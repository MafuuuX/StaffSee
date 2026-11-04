package com.staffsee.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.staffsee.StaffSeeMod;
import com.staffsee.config.StaffSeeConfig;
import com.staffsee.mixin.CommandNodeAccessor;
import com.staffsee.moderation.DurationParser;
import com.staffsee.moderation.FreezeManager;
import com.staffsee.moderation.MuteEntry;
import com.staffsee.moderation.MuteManager;
import com.staffsee.moderation.Report;
import com.staffsee.moderation.ReportManager;
import com.staffsee.permission.StaffPermissions;
import com.staffsee.storage.LogType;
import com.staffsee.storage.LogWriter;
import com.staffsee.storage.WarnCounter;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.util.List;
import java.util.regex.Pattern;

public final class StaffCommands {
	private static final SimpleCommandExceptionType IO_ERROR = new SimpleCommandExceptionType(Component.literal("StaffSee failed to write logs."));
	private static final Pattern DURATION_TOKEN = Pattern.compile("^\\d+[mhd]$|^perm(anent)?$", Pattern.CASE_INSENSITIVE);

	private StaffCommands() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("warn")
			.requires(StaffPermissions::canModerate)
			.then(Commands.argument("player", EntityArgument.player())
				.then(Commands.argument("reason", StringArgumentType.greedyString())
					.executes(StaffCommands::warn))));

		dispatcher.register(Commands.literal("warns")
			.requires(StaffPermissions::canModerate)
			.then(Commands.argument("player", EntityArgument.player())
				.executes(StaffCommands::warns)));

		dispatcher.register(Commands.literal("mute")
			.requires(StaffPermissions::canModerate)
			.then(Commands.argument("player", EntityArgument.player())
				.then(Commands.argument("reason", StringArgumentType.greedyString())
					.executes(StaffCommands::mute))));

		dispatcher.register(Commands.literal("unmute")
			.requires(StaffPermissions::canModerate)
			.then(Commands.argument("player", EntityArgument.player())
				.executes(StaffCommands::unmute)));

		removeCommand(dispatcher, "kick");
		dispatcher.register(Commands.literal("kick")
			.requires(StaffPermissions::canModerate)
			.then(Commands.argument("player", EntityArgument.player())
				.executes(StaffCommands::kickWithoutReason)
				.then(Commands.argument("reason", StringArgumentType.greedyString())
					.executes(StaffCommands::kick))));

		dispatcher.register(Commands.literal("freeze")
			.requires(StaffPermissions::canModerate)
			.then(Commands.argument("player", EntityArgument.player())
				.executes(StaffCommands::freezeWithoutReason)
				.then(Commands.argument("reason", StringArgumentType.greedyString())
					.executes(StaffCommands::freeze))));

		dispatcher.register(Commands.literal("unfreeze")
			.requires(StaffPermissions::canModerate)
			.then(Commands.argument("player", EntityArgument.player())
				.executes(StaffCommands::unfreeze)));

		dispatcher.register(Commands.literal("report")
			.requires(StaffPermissions::canReport)
			.then(Commands.argument("player", EntityArgument.player())
				.then(Commands.argument("reason", StringArgumentType.greedyString())
					.executes(StaffCommands::report))));

		dispatcher.register(Commands.literal("reports")
			.requires(StaffPermissions::canModerate)
			.executes(StaffCommands::listReports)
			.then(Commands.literal("resolve")
				.then(Commands.argument("id", IntegerArgumentType.integer(1))
					.executes(StaffCommands::resolveReport))));

		BanCommands.register(dispatcher);
		HistoryCommand.register(dispatcher);
		StaffToolsCommands.register(dispatcher);
	}

	private static int warn(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer target = EntityArgument.getPlayer(context, "player");
		String reason = StringArgumentType.getString(context, "reason");
		CommandSourceStack source = context.getSource();
		String staffName = source.getDisplayName().getString();

		StaffSeeMod mod = StaffSeeMod.getInstance();
		LogWriter logWriter = mod.getLogWriter();
		WarnCounter warnCounter = mod.getWarnCounter();
		StaffSeeConfig config = mod.getConfig();

		try {
			logWriter.append(target.getGameProfile().name(), LogType.WARN, "by " + staffName + "\nReason: " + reason);
			warnCounter.increment(target.getUUID());
			warnCounter.save();
		} catch (IOException e) {
			throw IO_ERROR.create();
		}

		Component warnMessage = Component.literal("You have been warned: " + reason);
		target.sendSystemMessage(warnMessage);
		source.sendSuccess(() -> Component.literal("Warned " + target.getGameProfile().name() + " (" + countLabel(warnCounter, target) + "): " + reason), true);

		mod.sendPunishmentWebhook("Warned", target.getGameProfile().name(), staffName, reason);

		if (config.warnBroadcast) {
			source.getServer().getPlayerList().broadcastSystemMessage(
				Component.literal(target.getGameProfile().name() + " was warned by " + staffName + ": " + reason),
				false
			);
		}

		if (config.autoKickAfterWarns > 0 && warnCounter.getCount(target.getUUID()) >= config.autoKickAfterWarns) {
			return autoKick(target, source, staffName, mod);
		}

		return 1;
	}

	private static int warns(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer target = EntityArgument.getPlayer(context, "player");
		LogWriter logWriter = StaffSeeMod.getInstance().getLogWriter();

		List<String> entries;
		try {
			entries = logWriter.readRecentWarns(target.getGameProfile().name(), 10);
		} catch (IOException e) {
			throw IO_ERROR.create();
		}

		CommandSourceStack source = context.getSource();
		if (entries.isEmpty()) {
			source.sendSuccess(() -> Component.literal("No warnings for " + target.getGameProfile().name() + "."), false);
			return 1;
		}

		source.sendSuccess(() -> Component.literal("Warnings for " + target.getGameProfile().name() + ":"), false);
		for (String entry : entries) {
			source.sendSuccess(() -> Component.literal(entry.replace("\n", " | ")), false);
		}
		return entries.size();
	}

	private static int mute(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer target = EntityArgument.getPlayer(context, "player");
		String rawInput = StringArgumentType.getString(context, "reason").trim();
		CommandSourceStack source = context.getSource();
		String staffName = source.getDisplayName().getString();

		String durationToken = "perm";
		String reason = rawInput;
		String[] parts = rawInput.split("\\s+", 2);
		if (DURATION_TOKEN.matcher(parts[0]).matches()) {
			durationToken = parts[0];
			reason = parts.length > 1 ? parts[1] : "No reason given";
		}

		DurationParser.ParsedDuration parsed;
		try {
			parsed = DurationParser.parse(durationToken);
		} catch (IllegalArgumentException e) {
			throw new SimpleCommandExceptionType(Component.literal(e.getMessage())).create();
		}

		long now = System.currentTimeMillis();
		MuteEntry entry = parsed.isPermanent()
			? MuteEntry.permanent(target.getGameProfile().name(), staffName, reason)
			: MuteEntry.timed(target.getGameProfile().name(), staffName, reason, parsed.expiresAt(now), parsed.label());

		StaffSeeMod mod = StaffSeeMod.getInstance();
		mod.getMuteManager().setMute(target.getUUID(), entry);

		try {
			mod.getLogWriter().append(
				target.getGameProfile().name(),
				LogType.MUTE,
				"by " + staffName + " (" + parsed.label() + ")\nReason: " + reason
			);
			mod.getMuteManager().save();
		} catch (IOException e) {
			throw IO_ERROR.create();
		}

		target.sendSystemMessage(Component.literal("You have been muted (" + parsed.label() + "): " + reason));
		String muteLabel = parsed.label();
		String muteReason = reason;
		source.sendSuccess(() -> Component.literal("Muted " + target.getGameProfile().name() + " (" + muteLabel + "): " + muteReason), true);
		mod.sendPunishmentWebhook("Muted (" + muteLabel + ")", target.getGameProfile().name(), staffName, reason);
		return 1;
	}

	private static int unmute(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer target = EntityArgument.getPlayer(context, "player");
		CommandSourceStack source = context.getSource();
		String staffName = source.getDisplayName().getString();
		StaffSeeMod mod = StaffSeeMod.getInstance();

		if (!mod.getMuteManager().removeMute(target.getUUID())) {
			source.sendFailure(Component.literal(target.getGameProfile().name() + " is not muted."));
			return 0;
		}

		try {
			mod.getLogWriter().append(
				target.getGameProfile().name(),
				LogType.UNMUTE,
				"by " + staffName + "\nAction: unmuted"
			);
			mod.getMuteManager().save();
		} catch (IOException e) {
			throw IO_ERROR.create();
		}

		target.sendSystemMessage(Component.literal("You have been unmuted."));
		source.sendSuccess(() -> Component.literal("Unmuted " + target.getGameProfile().name() + "."), true);
		mod.sendPunishmentWebhook("Unmuted", target.getGameProfile().name(), staffName, null);
		return 1;
	}

	private static int kickWithoutReason(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		return kickPlayer(context, "No reason given");
	}

	private static int kick(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		String reason = StringArgumentType.getString(context, "reason");
		return kickPlayer(context, reason);
	}

	private static int kickPlayer(CommandContext<CommandSourceStack> context, String reason) throws CommandSyntaxException {
		ServerPlayer target = EntityArgument.getPlayer(context, "player");
		CommandSourceStack source = context.getSource();
		String staffName = source.getDisplayName().getString();
		StaffSeeMod mod = StaffSeeMod.getInstance();

		try {
			mod.getLogWriter().append(
				target.getGameProfile().name(),
				LogType.KICK,
				"by " + staffName + "\nReason: " + reason
			);
		} catch (IOException e) {
			throw IO_ERROR.create();
		}

		Component kickMessage = Component.literal("Kicked by " + staffName + ": " + reason);
		target.connection.disconnect(kickMessage);
		source.sendSuccess(() -> Component.literal("Kicked " + target.getGameProfile().name() + ": " + reason), true);
		mod.sendPunishmentWebhook("Kicked", target.getGameProfile().name(), staffName, reason);
		return 1;
	}

	private static int freezeWithoutReason(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		return freezePlayer(context, "No reason given");
	}

	private static int freeze(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		String reason = StringArgumentType.getString(context, "reason");
		return freezePlayer(context, reason);
	}

	private static int freezePlayer(CommandContext<CommandSourceStack> context, String reason) throws CommandSyntaxException {
		ServerPlayer target = EntityArgument.getPlayer(context, "player");
		CommandSourceStack source = context.getSource();
		String staffName = source.getDisplayName().getString();
		StaffSeeMod mod = StaffSeeMod.getInstance();
		FreezeManager freezeManager = mod.getFreezeManager();

		if (freezeManager.isFrozen(target.getUUID())) {
			source.sendFailure(Component.literal(target.getGameProfile().name() + " is already frozen."));
			return 0;
		}

		freezeManager.freeze(target, staffName, reason);
		try {
			mod.getLogWriter().append(
				target.getGameProfile().name(),
				LogType.FREEZE,
				"by " + staffName + "\nReason: " + reason
			);
			freezeManager.save();
		} catch (IOException e) {
			throw IO_ERROR.create();
		}

		target.sendSystemMessage(Component.literal(mod.getConfig().freezeMessage));
		source.sendSuccess(() -> Component.literal("Froze " + target.getGameProfile().name() + ": " + reason), true);
		mod.sendPunishmentWebhook("Frozen", target.getGameProfile().name(), staffName, reason);
		return 1;
	}

	private static int unfreeze(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer target = EntityArgument.getPlayer(context, "player");
		CommandSourceStack source = context.getSource();
		String staffName = source.getDisplayName().getString();
		StaffSeeMod mod = StaffSeeMod.getInstance();
		FreezeManager freezeManager = mod.getFreezeManager();

		if (!freezeManager.unfreeze(target.getUUID())) {
			source.sendFailure(Component.literal(target.getGameProfile().name() + " is not frozen."));
			return 0;
		}

		try {
			mod.getLogWriter().append(
				target.getGameProfile().name(),
				LogType.UNFREEZE,
				"by " + staffName + "\nAction: unfrozen"
			);
			freezeManager.save();
		} catch (IOException e) {
			throw IO_ERROR.create();
		}

		target.sendSystemMessage(Component.literal("You have been unfrozen."));
		source.sendSuccess(() -> Component.literal("Unfroze " + target.getGameProfile().name() + "."), true);
		return 1;
	}

	private static int report(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer target = EntityArgument.getPlayer(context, "player");
		String reason = StringArgumentType.getString(context, "reason");
		CommandSourceStack source = context.getSource();

		if (!(source.getEntity() instanceof ServerPlayer reporter)) {
			throw new SimpleCommandExceptionType(Component.literal("Only players can submit reports.")).create();
		}

		if (reporter.getUUID().equals(target.getUUID())) {
			source.sendFailure(Component.literal("You cannot report yourself."));
			return 0;
		}

		StaffSeeMod mod = StaffSeeMod.getInstance();
		StaffSeeConfig config = mod.getConfig();
		ReportManager reportManager = mod.getReportManager();

		if (reportManager.isOnCooldown(reporter.getUUID(), config.reportCooldownSeconds)) {
			int wait = reportManager.secondsUntilNextReport(reporter.getUUID(), config.reportCooldownSeconds);
			source.sendFailure(Component.literal("Please wait " + wait + "s before submitting another report."));
			return 0;
		}

		Report report;
		try {
			report = reportManager.createReport(
				reporter.getGameProfile().name(),
				reporter.getUUID(),
				target.getGameProfile().name(),
				target.getUUID(),
				reason
			);
		} catch (IOException e) {
			throw IO_ERROR.create();
		}

		Component staffMessage = Component.literal("[Report #" + report.id + "] " + reporter.getGameProfile().name()
			+ " reported " + target.getGameProfile().name() + ": " + reason);
		mod.notifyStaff(staffMessage);
		mod.sendReportWebhook(report.id, reporter.getGameProfile().name(), target.getGameProfile().name(), reason);
		source.sendSuccess(() -> Component.literal("Report #" + report.id + " submitted. Staff has been notified."), false);
		return 1;
	}

	private static int listReports(CommandContext<CommandSourceStack> context) {
		StaffSeeMod mod = StaffSeeMod.getInstance();
		List<Report> reports = mod.getReportManager().listOpenReports(10);
		CommandSourceStack source = context.getSource();

		if (reports.isEmpty()) {
			source.sendSuccess(() -> Component.literal("No open reports."), false);
			return 1;
		}

		source.sendSuccess(() -> Component.literal("Open reports:"), false);
		for (Report report : reports) {
			source.sendSuccess(() -> Component.literal("#" + report.id + " " + report.reporterName + " -> " + report.targetName + ": " + report.reason), false);
		}
		return reports.size();
	}

	private static int resolveReport(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		int id = IntegerArgumentType.getInteger(context, "id");
		CommandSourceStack source = context.getSource();
		String staffName = source.getDisplayName().getString();
		StaffSeeMod mod = StaffSeeMod.getInstance();

		boolean resolved;
		try {
			resolved = mod.getReportManager().resolveReport(id, staffName, mod.getLogWriter());
		} catch (IOException e) {
			throw IO_ERROR.create();
		}

		if (!resolved) {
			source.sendFailure(Component.literal("Report #" + id + " was not found."));
			return 0;
		}

		source.sendSuccess(() -> Component.literal("Resolved report #" + id + "."), true);
		return 1;
	}

	private static int autoKick(ServerPlayer target, CommandSourceStack source, String staffName, StaffSeeMod mod) throws CommandSyntaxException {
		String reason = "Too many warnings";
		try {
			mod.getLogWriter().append(
				target.getGameProfile().name(),
				LogType.KICK,
				"by " + staffName + " (auto)\nReason: " + reason
			);
		} catch (IOException e) {
			throw IO_ERROR.create();
		}

		target.connection.disconnect(Component.literal(reason));
		source.sendSuccess(() -> Component.literal("Auto-kicked " + target.getGameProfile().name() + " after reaching warning limit."), true);
		mod.sendPunishmentWebhook("Auto-kicked", target.getGameProfile().name(), staffName, reason);
		return 1;
	}

	@SuppressWarnings("unchecked")
	static void removeCommand(CommandDispatcher<CommandSourceStack> dispatcher, String name) {
		CommandNodeAccessor<CommandSourceStack> root = (CommandNodeAccessor<CommandSourceStack>) dispatcher.getRoot();
		root.staffsee$getChildren().remove(name);
		root.staffsee$getLiterals().remove(name);
	}

	private static String countLabel(WarnCounter warnCounter, ServerPlayer target) {
		return warnCounter.getCount(target.getUUID()) + " warning(s)";
	}
}
