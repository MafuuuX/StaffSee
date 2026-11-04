package com.staffsee.command;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.staffsee.StaffSeeMod;
import com.staffsee.integration.ChestSeeLogs;
import com.staffsee.mixin.StoredUserEntryAccessor;
import com.staffsee.permission.StaffPermissions;
import com.staffsee.storage.HistoryReader;
import net.minecraft.command.CommandSource;
import net.minecraft.server.BanEntry;
import net.minecraft.server.BannedPlayerEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.WorldSavePath;

import java.io.IOException;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
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

	static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
		RequiredArgumentBuilder<ServerCommandSource, String> player = CommandManager.argument("player", StringArgumentType.word())
			.suggests((ctx, builder) -> CommandSource.suggestMatching(ctx.getSource().getPlayerNames(), builder))
			.executes(ctx -> history(ctx, 1))
			.then(CommandManager.argument("page", IntegerArgumentType.integer(1))
				.executes(ctx -> history(ctx, IntegerArgumentType.getInteger(ctx, "page"))));

		if (ChestSeeLogs.isAvailable()) {
			player.then(CommandManager.literal("chests")
				.executes(ctx -> chests(ctx, 1))
				.then(CommandManager.argument("page", IntegerArgumentType.integer(1))
					.executes(ctx -> chests(ctx, IntegerArgumentType.getInteger(ctx, "page")))));
		}

		dispatcher.register(CommandManager.literal("history")
			.requires(StaffPermissions::canModerate)
			.then(player));
	}

	private static int history(CommandContext<ServerCommandSource> context, int page) {
		ServerCommandSource source = context.getSource();
		String name = StringArgumentType.getString(context, "player");
		StaffSeeMod mod = StaffSeeMod.getInstance();

		List<HistoryReader.Entry> entries = HistoryReader.read(mod.getLogWriter(), mod.getReportManager().listOpenReports(Integer.MAX_VALUE), name);
		if (entries.isEmpty()) {
			source.sendFeedback(() -> Text.literal("No history for " + name + ".").formatted(Formatting.GRAY), false);
			return 0;
		}

		int pages = (entries.size() + PAGE_SIZE - 1) / PAGE_SIZE;
		int shown = Math.min(page, pages);
		List<Text> out = new ArrayList<>();
		out.add(Text.literal("History of " + name + " (" + entries.size() + " entries)").formatted(Formatting.GOLD));

		BannedPlayerEntry ban = findBan(source.getServer(), name);
		if (ban != null) {
			String until = ban.getExpiryDate() == null ? "permanently" : "until " + BanEntry.DATE_FORMAT.format(ban.getExpiryDate());
			out.add(Text.literal("Currently banned " + until + ": " + ban.getReason()).formatted(Formatting.RED));
		}

		for (HistoryReader.Entry entry : entries.subList((shown - 1) * PAGE_SIZE, Math.min(entries.size(), shown * PAGE_SIZE))) {
			MutableText line = Text.literal(TIME.format(entry.when()) + " ").formatted(Formatting.GRAY)
				.append(Text.literal(entry.type()).formatted(typeColor(entry.type())))
				.append(Text.literal(" " + entry.details()).formatted(Formatting.WHITE));
			out.add(line);
		}
		out.add(pager("/history " + name + " ", shown, pages));
		send(source, out);
		return entries.size();
	}

	private static int chests(CommandContext<ServerCommandSource> context, int page) {
		ServerCommandSource source = context.getSource();
		String name = StringArgumentType.getString(context, "player");
		MinecraftServer server = source.getServer();
		Path root = server.getSavePath(WorldSavePath.ROOT).resolve("ChestLog");

		// rcon replies as soon as the command returns, so console lookups can't wait for the search thread
		if (source.getPlayer() == null) {
			try {
				showChests(source, name, ChestSeeLogs.search(root, name), page);
			} catch (IOException e) {
				StaffSeeMod.LOGGER.warn("Could not read ChestSee logs", e);
				source.sendError(Text.literal("Could not read the ChestSee logs."));
			}
			return 1;
		}

		CHEST_SEARCH.execute(() -> {
			List<ChestSeeLogs.Hit> hits;
			try {
				hits = ChestSeeLogs.search(root, name);
			} catch (IOException e) {
				StaffSeeMod.LOGGER.warn("Could not read ChestSee logs", e);
				server.execute(() -> source.sendError(Text.literal("Could not read the ChestSee logs.")));
				return;
			}
			server.execute(() -> showChests(source, name, hits, page));
		});
		return 1;
	}

	private static void showChests(ServerCommandSource source, String name, List<ChestSeeLogs.Hit> hits, int page) {
		if (hits.isEmpty()) {
			source.sendFeedback(() -> Text.literal("No container activity for " + name + ".").formatted(Formatting.GRAY), false);
			return;
		}

		int pages = (hits.size() + PAGE_SIZE - 1) / PAGE_SIZE;
		int shown = Math.min(page, pages);
		List<Text> out = new ArrayList<>();
		out.add(Text.literal("Container activity of " + name + " (" + hits.size() + " entries)").formatted(Formatting.GOLD));

		for (ChestSeeLogs.Hit hit : hits.subList((shown - 1) * PAGE_SIZE, Math.min(hits.size(), shown * PAGE_SIZE))) {
			String where = hit.container() + (hit.pos() != null ? " " + hit.pos() : "") + (hit.dimension().isEmpty() ? "" : " (" + hit.dimension() + ")");
			MutableText location = Text.literal(where).formatted(Formatting.AQUA);
			String dimension = ChestSeeLogs.dimensionId(hit.dimension());
			if (hit.pos() != null && dimension != null) {
				String command = "/chestsee lookup " + hit.pos() + " 1 " + dimension;
				location = location.styled(style -> style.withClickEvent(new ClickEvent.RunCommand(command))
					.withHoverEvent(new HoverEvent.ShowText(Text.literal("Show this container's log"))));
			}

			MutableText line = Text.literal(TIME.format(hit.when()) + " ").formatted(Formatting.GRAY)
				.append(location)
				.append(Text.literal(" ").append(describe(hit.lines())));
			out.add(line);
		}
		out.add(pager("/history " + name + " chests ", shown, pages));
		send(source, out);
	}

	// "Items Added: [2 minecraft:dirt]" / "Items Removed: [...]" -> "+2 minecraft:dirt -..."
	private static Text describe(List<String> lines) {
		MutableText text = Text.empty();
		for (String line : lines) {
			String list = null;
			Formatting color = Formatting.WHITE;
			String sign = "";
			if (line.startsWith("Items Added: ")) {
				list = line.substring("Items Added: ".length());
				color = Formatting.GREEN;
				sign = "+";
			} else if (line.startsWith("Items Removed: ")) {
				list = line.substring("Items Removed: ".length());
				color = Formatting.RED;
				sign = "-";
			}

			String shown = list == null ? line : list.replaceAll("^\\[|\\]$", "");
			if (shown.isEmpty()) {
				continue;
			}
			if (!text.getSiblings().isEmpty()) {
				text.append(Text.literal(" "));
			}
			text.append(Text.literal(sign + shown).formatted(color));
		}
		return text;
	}

	private static void send(ServerCommandSource source, List<Text> lines) {
		if (source.getPlayer() != null) {
			for (Text line : lines) {
				source.sendFeedback(() -> line, false);
			}
			return;
		}
		// one message for the console, rcon would glue separate ones together
		MutableText all = Text.empty();
		for (int i = 0; i < lines.size(); i++) {
			if (i > 0) {
				all.append("\n");
			}
			all.append(lines.get(i));
		}
		source.sendFeedback(() -> all, false);
	}

	private static BannedPlayerEntry findBan(MinecraftServer server, String name) {
		for (BannedPlayerEntry entry : server.getPlayerManager().getUserBanList().values()) {
			GameProfile profile = (GameProfile) ((StoredUserEntryAccessor) entry).staffsee$getUser();
			if (profile != null && profile.getName().equalsIgnoreCase(name) && (entry.getExpiryDate() == null || entry.getExpiryDate().after(new Date()))) {
				return entry;
			}
		}
		return null;
	}

	private static Text pager(String command, int page, int pages) {
		MutableText text = Text.empty();
		if (page > 1) {
			text.append(button("[< Prev] ", command + (page - 1)));
		}
		text.append(Text.literal("Page " + page + "/" + pages).formatted(Formatting.GRAY));
		if (page < pages) {
			text.append(button(" [Next >]", command + (page + 1)));
		}
		return text;
	}

	private static MutableText button(String label, String command) {
		return Text.literal(label).styled(style -> style.withColor(Formatting.AQUA)
			.withClickEvent(new ClickEvent.RunCommand(command)));
	}

	private static Formatting typeColor(String type) {
		return switch (type) {
			case "BAN", "TEMPBAN" -> Formatting.DARK_RED;
			case "KICK", "MUTE" -> Formatting.RED;
			case "WARN", "FREEZE" -> Formatting.YELLOW;
			case "REPORT" -> Formatting.LIGHT_PURPLE;
			default -> Formatting.GREEN;
		};
	}
}
