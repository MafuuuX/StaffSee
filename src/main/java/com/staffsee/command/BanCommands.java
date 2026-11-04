package com.staffsee.command;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.staffsee.StaffSeeMod;
import com.staffsee.mixin.StoredUserEntryAccessor;
import com.staffsee.moderation.DurationParser;
import com.staffsee.permission.StaffPermissions;
import com.staffsee.storage.LogType;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.GameProfileArgumentType;
import net.minecraft.server.BanEntry;
import net.minecraft.server.BannedPlayerEntry;
import net.minecraft.server.BannedPlayerList;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

import java.io.IOException;
import java.util.Collection;
import java.util.Date;

final class BanCommands {
	private static final SimpleCommandExceptionType IO_ERROR = new SimpleCommandExceptionType(Text.literal("StaffSee failed to write logs."));
	private static final String NO_REASON = "No reason given";

	private BanCommands() {
	}

	static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
		StaffCommands.removeCommand(dispatcher, "ban");
		dispatcher.register(CommandManager.literal("ban")
			.requires(StaffPermissions::canModerate)
			.then(CommandManager.argument("player", GameProfileArgumentType.gameProfile())
				.executes(ctx -> ban(ctx, null, NO_REASON))
				.then(CommandManager.argument("reason", StringArgumentType.greedyString())
					.executes(ctx -> ban(ctx, null, StringArgumentType.getString(ctx, "reason"))))));

		dispatcher.register(CommandManager.literal("tempban")
			.requires(StaffPermissions::canModerate)
			.then(CommandManager.argument("player", GameProfileArgumentType.gameProfile())
				.then(CommandManager.argument("duration", StringArgumentType.word())
					.suggests((ctx, builder) -> CommandSource.suggestMatching(new String[]{"30m", "12h", "1d", "7d", "30d"}, builder))
					.executes(ctx -> ban(ctx, StringArgumentType.getString(ctx, "duration"), NO_REASON))
					.then(CommandManager.argument("reason", StringArgumentType.greedyString())
						.executes(ctx -> ban(ctx, StringArgumentType.getString(ctx, "duration"), StringArgumentType.getString(ctx, "reason")))))));

		// vanilla /pardon would skip the log, so it becomes the same as /unban
		StaffCommands.removeCommand(dispatcher, "pardon");
		for (String name : new String[]{"unban", "pardon"}) {
			dispatcher.register(CommandManager.literal(name)
				.requires(StaffPermissions::canModerate)
				.then(CommandManager.argument("player", StringArgumentType.word())
					.suggests((ctx, builder) -> CommandSource.suggestMatching(ctx.getSource().getServer().getPlayerManager().getUserBanList().getNames(), builder))
					.executes(BanCommands::unban)));
		}
	}

	private static int ban(CommandContext<ServerCommandSource> context, String durationToken, String reason) throws CommandSyntaxException {
		ServerCommandSource source = context.getSource();
		Collection<GameProfile> targets = GameProfileArgumentType.getProfileArgument(context, "player");
		String staffName = source.getDisplayName().getString();
		StaffSeeMod mod = StaffSeeMod.getInstance();

		DurationParser.ParsedDuration duration = DurationParser.ParsedDuration.permanent();
		if (durationToken != null) {
			try {
				duration = DurationParser.parse(durationToken);
			} catch (IllegalArgumentException e) {
				throw new SimpleCommandExceptionType(Text.literal(e.getMessage())).create();
			}
			if (duration.isPermanent()) {
				throw new SimpleCommandExceptionType(Text.literal("Use /ban for permanent bans.")).create();
			}
		}

		long now = System.currentTimeMillis();
		Date expires = duration.isPermanent() ? null : new Date(duration.expiresAt(now));
		BannedPlayerList bans = source.getServer().getPlayerManager().getUserBanList();
		int banned = 0;

		for (GameProfile target : targets) {
			if (source.getEntity() instanceof ServerPlayerEntity self && self.getUuid().equals(target.getId())) {
				source.sendError(Text.literal("You cannot ban yourself."));
				continue;
			}

			BannedPlayerEntry entry = new BannedPlayerEntry(target, new Date(now), staffName, expires, reason);
			bans.add(entry);
			try {
				mod.getLogWriter().append(target.getName(), LogType.BAN, "by " + staffName + " (" + duration.label() + ")\nReason: " + reason);
			} catch (IOException e) {
				throw IO_ERROR.create();
			}

			ServerPlayerEntity online = source.getServer().getPlayerManager().getPlayer(target.getId());
			if (online != null) {
				online.networkHandler.disconnect(banScreen(entry));
			}

			String label = duration.label();
			source.sendFeedback(() -> Text.literal((expires == null ? "Banned " : "Temp-banned ") + target.getName()
				+ " (" + label + "): " + reason), true);
			mod.sendPunishmentWebhook((expires == null ? "Banned" : "Temp-banned (" + label + ")"), target.getName(), staffName, reason);
			banned++;
		}
		return banned;
	}

	private static int unban(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
		ServerCommandSource source = context.getSource();
		String name = StringArgumentType.getString(context, "player");
		String staffName = source.getDisplayName().getString();
		BannedPlayerList bans = source.getServer().getPlayerManager().getUserBanList();

		GameProfile found = null;
		for (BannedPlayerEntry entry : bans.values()) {
			GameProfile profile = (GameProfile) ((StoredUserEntryAccessor) entry).staffsee$getUser();
			if (profile != null && profile.getName().equalsIgnoreCase(name)) {
				found = profile;
				break;
			}
		}
		if (found == null) {
			source.sendError(Text.literal(name + " is not banned."));
			return 0;
		}

		GameProfile user = found;
		bans.remove(user);
		try {
			StaffSeeMod.getInstance().getLogWriter().append(user.getName(), LogType.UNBAN, "by " + staffName + "\nAction: unbanned");
		} catch (IOException e) {
			throw IO_ERROR.create();
		}

		source.sendFeedback(() -> Text.literal("Unbanned " + user.getName() + "."), true);
		StaffSeeMod.getInstance().sendPunishmentWebhook("Unbanned", user.getName(), staffName, null);
		return 1;
	}

	// same screen vanilla shows when a banned player tries to join
	private static Text banScreen(BannedPlayerEntry entry) {
		MutableText message = Text.translatable("multiplayer.disconnect.banned.reason", entry.getReason());
		if (entry.getExpiryDate() != null) {
			message.append(Text.translatable("multiplayer.disconnect.banned.expiration", BanEntry.DATE_FORMAT.format(entry.getExpiryDate())));
		}
		return message;
	}
}
