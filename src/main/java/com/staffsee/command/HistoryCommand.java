package com.staffsee.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.staffsee.StaffSeeMod;
import com.staffsee.integration.ChestSeeLogs;
import com.staffsee.permission.StaffPermissions;
import com.staffsee.storage.HistoryReader;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.BanListEntry;
import net.minecraft.server.players.UserBanListEntry;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class HistoryCommand {
	private static final int PAGE_SIZE = 8;
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd.MM.yy HH:mm");
	private static final ExecutorService CHEST_SEARCH = Executors.newSingleThreadExecutor(r -> {
		Thread thread = new Thread(r, "StaffSee chest history");
		thread.setDaemon(true);
		return thread;
	});

	private HistoryCommand() {
	}

	static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		RequiredArgumentBuilder<CommandSourceStack, String> player = Commands.argument("player", StringArgumentType.word())
			.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(ctx.getSource().getOnlinePlayerNames(), builder))
			.executes(ctx -> history(ctx, 1))
			.then(Commands.argument("page", IntegerArgumentType.integer(1))
				.executes(ctx -> history(ctx, IntegerArgumentType.getInteger(ctx, "page"))));

		if (ChestSeeLogs.isAvailable()) {
			player.then(Commands.literal("chests")
				.executes(ctx -> chests(ctx, 1))
				.then(Commands.argument("page", IntegerArgumentType.integer(1))
					.executes(ctx -> chests(ctx, IntegerArgumentType.getInteger(ctx, "page")))));
		}

		dispatcher.register(Commands.literal("history")
			.requires(StaffPermissions::canModerate)
			.then(player));
	}

	private static int history(CommandContext<CommandSourceStack> context, int page) {
		CommandSourceStack source = context.getSource();
		String name = StringArgumentType.getString(context, "player");
		StaffSeeMod mod = StaffSeeMod.getInstance();

		List<HistoryReader.Entry> entries = HistoryReader.read(mod.getLogWriter(), mod.getReportManager().listOpenReports(Integer.MAX_VALUE), name);
		if (entries.isEmpty()) {
			source.sendSuccess(() -> Component.literal("No history for " + name + ".").withStyle(ChatFormatting.GRAY), false);
			return 0;
		}

		int pages = (entries.size() + PAGE_SIZE - 1) / PAGE_SIZE;
		int shown = Math.min(page, pages);
		List<Component> out = new ArrayList<>();
		out.add(Component.literal("History of " + name + " (" + entries.size() + " entries)").withStyle(ChatFormatting.GOLD));

		UserBanListEntry ban = findBan(source.getServer(), name);
		if (ban != null) {
			String until = ban.getExpires() == null ? "permanently" : "until " + BanListEntry.DATE_FORMAT.format(ban.getExpires());
			out.add(Component.literal("Currently banned " + until + ": " + ban.getReason()).withStyle(ChatFormatting.RED));
		}

		for (HistoryReader.Entry entry : entries.subList((shown - 1) * PAGE_SIZE, Math.min(entries.size(), shown * PAGE_SIZE))) {
			MutableComponent line = Component.literal(TIME.format(entry.when()) + " ").withStyle(ChatFormatting.GRAY)
				.append(Component.literal(entry.type()).withStyle(typeColor(entry.type())))
				.append(Component.literal(" " + entry.details()).withStyle(ChatFormatting.WHITE));
			out.add(line);
		}
		out.add(pager("/history " + name + " ", shown, pages));
		send(source, out);
		return entries.size();
	}

	private static int chests(CommandContext<CommandSourceStack> context, int page) {
		CommandSourceStack source = context.getSource();
		String name = StringArgumentType.getString(context, "player");
		MinecraftServer server = source.getServer();
		Path root = server.getWorldPath(LevelResource.ROOT).resolve("ChestLog");

		// rcon replies as soon as the command returns, so console lookups can't wait for the search thread
		if (source.getPlayer() == null) {
			try {
				showChests(source, name, ChestSeeLogs.search(root, name), page);
			} catch (IOException e) {
				StaffSeeMod.LOGGER.warn("Could not read ChestSee logs", e);
				source.sendFailure(Component.literal("Could not read the ChestSee logs."));
			}
			return 1;
		}

		CHEST_SEARCH.execute(() -> {
			List<ChestSeeLogs.Hit> hits;
			try {
				hits = ChestSeeLogs.search(root, name);
			} catch (IOException e) {
				StaffSeeMod.LOGGER.warn("Could not read ChestSee logs", e);
				server.execute(() -> source.sendFailure(Component.literal("Could not read the ChestSee logs.")));
				return;
			}
			server.execute(() -> showChests(source, name, hits, page));
		});
		return 1;
	}

	private static void showChests(CommandSourceStack source, String name, List<ChestSeeLogs.Hit> hits, int page) {
		if (hits.isEmpty()) {
			source.sendSuccess(() -> Component.literal("No container activity for " + name + ".").withStyle(ChatFormatting.GRAY), false);
			return;
		}

		int pages = (hits.size() + PAGE_SIZE - 1) / PAGE_SIZE;
		int shown = Math.min(page, pages);
		List<Component> out = new ArrayList<>();
		out.add(Component.literal("Container activity of " + name + " (" + hits.size() + " entries)").withStyle(ChatFormatting.GOLD));

		for (ChestSeeLogs.Hit hit : hits.subList((shown - 1) * PAGE_SIZE, Math.min(hits.size(), shown * PAGE_SIZE))) {
			String where = hit.container() + (hit.pos() != null ? " " + hit.pos() : "") + (hit.dimension().isEmpty() ? "" : " (" + hit.dimension() + ")");
			MutableComponent location = Component.literal(where).withStyle(ChatFormatting.AQUA);
			String dimension = ChestSeeLogs.dimensionId(hit.dimension());
			if (hit.pos() != null && dimension != null) {
				String command = "/chestsee lookup " + hit.pos() + " 1 " + dimension;
				location = location.withStyle(style -> style.withClickEvent(new ClickEvent.RunCommand(command))
					.withHoverEvent(new HoverEvent.ShowText(Component.literal("Show this container's log"))));
			}

			MutableComponent line = Component.literal(TIME.format(hit.when()) + " ").withStyle(ChatFormatting.GRAY)
				.append(location)
				.append(Component.literal(" ").append(describe(hit.lines())));
			out.add(line);
		}
		out.add(pager("/history " + name + " chests ", shown, pages));
		send(source, out);
	}

	// "Items Added: [2 minecraft:dirt]" / "Items Removed: [...]" -> "+2 minecraft:dirt -..."
	private static Component describe(List<String> lines) {
		MutableComponent text = Component.empty();
		for (String line : lines) {
			String list = null;
			ChatFormatting color = ChatFormatting.WHITE;
			String sign = "";
			if (line.startsWith("Items Added: ")) {
				list = line.substring("Items Added: ".length());
				color = ChatFormatting.GREEN;
				sign = "+";
			} else if (line.startsWith("Items Removed: ")) {
				list = line.substring("Items Removed: ".length());
				color = ChatFormatting.RED;
				sign = "-";
			}

			String shown = list == null ? line : list.replaceAll("^\\[|\\]$", "");
			if (shown.isEmpty()) {
				continue;
			}
			if (!text.getSiblings().isEmpty()) {
				text.append(Component.literal(" "));
			}
			text.append(Component.literal(sign + shown).withStyle(color));
		}
		return text;
	}

	private static void send(CommandSourceStack source, List<Component> lines) {
		if (source.getPlayer() != null) {
			for (Component line : lines) {
				source.sendSuccess(() -> line, false);
			}
			return;
		}
		// one message for the console, rcon would glue separate ones together
		MutableComponent all = Component.empty();
		for (int i = 0; i < lines.size(); i++) {
			if (i > 0) {
				all.append("\n");
			}
			all.append(lines.get(i));
		}
		source.sendSuccess(() -> all, false);
	}

	private static UserBanListEntry findBan(MinecraftServer server, String name) {
		for (UserBanListEntry entry : server.getPlayerList().getBans().getEntries()) {
			if (entry.getUser() != null && entry.getUser().name().equalsIgnoreCase(name) && !entry.hasExpired()) {
				return entry;
			}
		}
		return null;
	}

	private static Component pager(String command, int page, int pages) {
		MutableComponent text = Component.empty();
		if (page > 1) {
			text.append(button("[< Prev] ", command + (page - 1)));
		}
		text.append(Component.literal("Page " + page + "/" + pages).withStyle(ChatFormatting.GRAY));
		if (page < pages) {
			text.append(button(" [Next >]", command + (page + 1)));
		}
		return text;
	}

	private static MutableComponent button(String label, String command) {
		return Component.literal(label).withStyle(style -> style.withColor(ChatFormatting.AQUA)
			.withClickEvent(new ClickEvent.RunCommand(command)));
	}

	private static ChatFormatting typeColor(String type) {
		return switch (type) {
			case "BAN", "TEMPBAN" -> ChatFormatting.DARK_RED;
			case "KICK", "MUTE" -> ChatFormatting.RED;
			case "WARN", "FREEZE" -> ChatFormatting.YELLOW;
			case "REPORT" -> ChatFormatting.LIGHT_PURPLE;
			default -> ChatFormatting.GREEN;
		};
	}
}
