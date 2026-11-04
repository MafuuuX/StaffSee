package com.staffsee.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.staffsee.StaffSeeMod;
import com.staffsee.moderation.DurationParser;
import com.staffsee.permission.StaffPermissions;
import com.staffsee.storage.LogType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.BanListEntry;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.UserBanList;
import net.minecraft.server.players.UserBanListEntry;

import java.io.IOException;
import java.util.Collection;
import java.util.Date;

final class BanCommands {
	private static final SimpleCommandExceptionType IO_ERROR = new SimpleCommandExceptionType(Component.literal("StaffSee failed to write logs."));
	private static final String NO_REASON = "No reason given";

	private BanCommands() {
	}

	static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		StaffCommands.removeCommand(dispatcher, "ban");
		dispatcher.register(Commands.literal("ban")
			.requires(StaffPermissions::canModerate)
			.then(Commands.argument("player", GameProfileArgument.gameProfile())
				.executes(ctx -> ban(ctx, null, NO_REASON))
				.then(Commands.argument("reason", StringArgumentType.greedyString())
					.executes(ctx -> ban(ctx, null, StringArgumentType.getString(ctx, "reason"))))));

		dispatcher.register(Commands.literal("tempban")
			.requires(StaffPermissions::canModerate)
			.then(Commands.argument("player", GameProfileArgument.gameProfile())
				.then(Commands.argument("duration", StringArgumentType.word())
					.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(new String[]{"30m", "12h", "1d", "7d", "30d"}, builder))
					.executes(ctx -> ban(ctx, StringArgumentType.getString(ctx, "duration"), NO_REASON))
					.then(Commands.argument("reason", StringArgumentType.greedyString())
						.executes(ctx -> ban(ctx, StringArgumentType.getString(ctx, "duration"), StringArgumentType.getString(ctx, "reason")))))));

		// vanilla /pardon would skip the log, so it becomes the same as /unban
		StaffCommands.removeCommand(dispatcher, "pardon");
		for (String name : new String[]{"unban", "pardon"}) {
			dispatcher.register(Commands.literal(name)
				.requires(StaffPermissions::canModerate)
				.then(Commands.argument("player", StringArgumentType.word())
					.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(ctx.getSource().getServer().getPlayerList().getBans().getUserList(), builder))
					.executes(BanCommands::unban)));
		}
	}

	private static int ban(CommandContext<CommandSourceStack> context, String durationToken, String reason) throws CommandSyntaxException {
		CommandSourceStack source = context.getSource();
		Collection<NameAndId> targets = GameProfileArgument.getGameProfiles(context, "player");
		String staffName = source.getDisplayName().getString();
		StaffSeeMod mod = StaffSeeMod.getInstance();

		DurationParser.ParsedDuration duration = DurationParser.ParsedDuration.permanent();
		if (durationToken != null) {
			try {
				duration = DurationParser.parse(durationToken);
			} catch (IllegalArgumentException e) {
				throw new SimpleCommandExceptionType(Component.literal(e.getMessage())).create();
			}
			if (duration.isPermanent()) {
				throw new SimpleCommandExceptionType(Component.literal("Use /ban for permanent bans.")).create();
			}
		}

		long now = System.currentTimeMillis();
		Date expires = duration.isPermanent() ? null : new Date(duration.expiresAt(now));
		UserBanList bans = source.getServer().getPlayerList().getBans();
		int banned = 0;

		for (NameAndId target : targets) {
			if (source.getEntity() instanceof ServerPlayer self && self.getUUID().equals(target.id())) {
				source.sendFailure(Component.literal("You cannot ban yourself."));
				continue;
			}

			UserBanListEntry entry = new UserBanListEntry(target, new Date(now), staffName, expires, reason);
			bans.add(entry);
			try {
				mod.getLogWriter().append(target.name(), LogType.BAN, "by " + staffName + " (" + duration.label() + ")\nReason: " + reason);
			} catch (IOException e) {
				throw IO_ERROR.create();
			}

			ServerPlayer online = source.getServer().getPlayerList().getPlayer(target.id());
			if (online != null) {
				online.connection.disconnect(banScreen(entry));
			}

			String label = duration.label();
			source.sendSuccess(() -> Component.literal((expires == null ? "Banned " : "Temp-banned ") + target.name()
				+ " (" + label + "): " + reason), true);
			mod.sendPunishmentWebhook((expires == null ? "Banned" : "Temp-banned (" + label + ")"), target.name(), staffName, reason);
			banned++;
		}
		return banned;
	}

	private static int unban(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		CommandSourceStack source = context.getSource();
		String name = StringArgumentType.getString(context, "player");
		String staffName = source.getDisplayName().getString();
		UserBanList bans = source.getServer().getPlayerList().getBans();

		UserBanListEntry found = null;
		for (UserBanListEntry entry : bans.getEntries()) {
			if (entry.getUser() != null && entry.getUser().name().equalsIgnoreCase(name)) {
				found = entry;
				break;
			}
		}
		if (found == null) {
			source.sendFailure(Component.literal(name + " is not banned."));
			return 0;
		}

		NameAndId user = found.getUser();
		bans.remove(user);
		try {
			StaffSeeMod.getInstance().getLogWriter().append(user.name(), LogType.UNBAN, "by " + staffName + "\nAction: unbanned");
		} catch (IOException e) {
			throw IO_ERROR.create();
		}

		source.sendSuccess(() -> Component.literal("Unbanned " + user.name() + "."), true);
		StaffSeeMod.getInstance().sendPunishmentWebhook("Unbanned", user.name(), staffName, null);
		return 1;
	}

	// same screen vanilla shows when a banned player tries to join
	private static Component banScreen(UserBanListEntry entry) {
		MutableComponent message = Component.translatable("multiplayer.disconnect.banned.reason", entry.getReasonMessage());
		if (entry.getExpires() != null) {
			message.append(Component.translatable("multiplayer.disconnect.banned.expiration", BanListEntry.DATE_FORMAT.format(entry.getExpires())));
		}
		return message;
	}
}
