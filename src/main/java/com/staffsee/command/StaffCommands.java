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
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.io.IOException;
import java.util.List;
import java.util.regex.Pattern;

public final class StaffCommands {
	private static final SimpleCommandExceptionType IO_ERROR = new SimpleCommandExceptionType(Text.literal("StaffSee failed to write logs."));
	private static final Pattern DURATION_TOKEN = Pattern.compile("^\\d+[mhd]$|^perm(anent)?$", Pattern.CASE_INSENSITIVE);

	private StaffCommands() {
	}

	public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
		dispatcher.register(CommandManager.literal("warn")
			.requires(StaffPermissions::canModerate)
			.then(CommandManager.argument("player", EntityArgumentType.player())
				.then(CommandManager.argument("reason", StringArgumentType.greedyString())
					.executes(StaffCommands::warn))));

		dispatcher.register(CommandManager.literal("warns")
			.requires(StaffPermissions::canModerate)
			.then(CommandManager.argument("player", EntityArgumentType.player())
				.executes(StaffCommands::warns)));

		dispatcher.register(CommandManager.literal("mute")
			.requires(StaffPermissions::canModerate)
			.then(CommandManager.argument("player", EntityArgumentType.player())
				.then(CommandManager.argument("reason", StringArgumentType.greedyString())
					.executes(StaffCommands::mute))));

		dispatcher.register(CommandManager.literal("unmute")
			.requires(StaffPermissions::canModerate)
			.then(CommandManager.argument("player", EntityArgumentType.player())
				.executes(StaffCommands::unmute)));

		removeCommand(dispatcher, "kick");
		dispatcher.register(CommandManager.literal("kick")
			.requires(StaffPermissions::canModerate)
			.then(CommandManager.argument("player", EntityArgumentType.player())
				.executes(StaffCommands::kickWithoutReason)
				.then(CommandManager.argument("reason", StringArgumentType.greedyString())
					.executes(StaffCommands::kick))));

		dispatcher.register(CommandManager.literal("freeze")
			.requires(StaffPermissions::canModerate)
			.then(CommandManager.argument("player", EntityArgumentType.player())
				.executes(StaffCommands::freezeWithoutReason)
				.then(CommandManager.argument("reason", StringArgumentType.greedyString())
					.executes(StaffCommands::freeze))));

		dispatcher.register(CommandManager.literal("unfreeze")
			.requires(StaffPermissions::canModerate)
			.then(CommandManager.argument("player", EntityArgumentType.player())
				.executes(StaffCommands::unfreeze)));

		dispatcher.register(CommandManager.literal("report")
			.requires(StaffPermissions::canReport)
			.then(CommandManager.argument("player", EntityArgumentType.player())
				.then(CommandManager.argument("reason", StringArgumentType.greedyString())
					.executes(StaffCommands::report))));

		dispatcher.register(CommandManager.literal("reports")
			.requires(StaffPermissions::canModerate)
			.executes(StaffCommands::listReports)
			.then(CommandManager.literal("resolve")
				.then(CommandManager.argument("id", IntegerArgumentType.integer(1))
					.executes(StaffCommands::resolveReport))));
	}

	private static int warn(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
		ServerPlayerEntity target = EntityArgumentType.getPlayer(context, "player");
		String reason = StringArgumentType.getString(context, "reason");
		ServerCommandSource source = context.getSource();
		String staffName = source.getName();

		StaffSeeMod mod = StaffSeeMod.getInstance();
		LogWriter logWriter = mod.getLogWriter();
		WarnCounter warnCounter = mod.getWarnCounter();
		StaffSeeConfig config = mod.getConfig();

		try {
			logWriter.append(target.getGameProfile().name(), LogType.WARN, "by " + staffName + "\nReason: " + reason);
			warnCounter.increment(target.getUuid());
			warnCounter.save();
		} catch (IOException e) {
			throw IO_ERROR.create();
		}

		target.sendMessage(Text.literal("You have been warned: " + reason), false);
		source.sendFeedback(() -> Text.literal("Warned " + target.getGameProfile().name() + " (" + countLabel(warnCounter, target) + "): " + reason), true);

		if (config.warnBroadcast) {
			source.getServer().getPlayerManager().broadcast(
				Text.literal(target.getGameProfile().name() + " was warned by " + staffName + ": " + reason),
				false
			);
		}

		if (config.autoKickAfterWarns > 0 && warnCounter.getCount(target.getUuid()) >= config.autoKickAfterWarns) {
			return autoKick(target, source, staffName, mod);
		}

		return 1;
	}

	private static int warns(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
		ServerPlayerEntity target = EntityArgumentType.getPlayer(context, "player");
		LogWriter logWriter = StaffSeeMod.getInstance().getLogWriter();

		List<String> entries;
		try {
			entries = logWriter.readRecentWarns(target.getGameProfile().name(), 10);
		} catch (IOException e) {
			throw IO_ERROR.create();
		}

		ServerCommandSource source = context.getSource();
		if (entries.isEmpty()) {
			source.sendFeedback(() -> Text.literal("No warnings for " + target.getGameProfile().name() + "."), false);
			return 1;
		}

		source.sendFeedback(() -> Text.literal("Warnings for " + target.getGameProfile().name() + ":"), false);
		for (String entry : entries) {
			source.sendFeedback(() -> Text.literal(entry.replace("\n", " | ")), false);
		}
		return entries.size();
	}

	private static int mute(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
		ServerPlayerEntity target = EntityArgumentType.getPlayer(context, "player");
		String rawInput = StringArgumentType.getString(context, "reason").trim();
		ServerCommandSource source = context.getSource();
		String staffName = source.getName();

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
			throw new SimpleCommandExceptionType(Text.literal(e.getMessage())).create();
		}

		long now = System.currentTimeMillis();
		MuteEntry entry = parsed.isPermanent()
			? MuteEntry.permanent(target.getGameProfile().name(), staffName, reason)
			: MuteEntry.timed(target.getGameProfile().name(), staffName, reason, parsed.expiresAt(now), parsed.label());

		StaffSeeMod mod = StaffSeeMod.getInstance();
		mod.getMuteManager().setMute(target.getUuid(), entry);

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

		target.sendMessage(Text.literal("You have been muted (" + parsed.label() + "): " + reason), false);
		String muteLabel = parsed.label();
		String muteReason = reason;
		source.sendFeedback(() -> Text.literal("Muted " + target.getGameProfile().name() + " (" + muteLabel + "): " + muteReason), true);
		return 1;
	}

	private static int unmute(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
		ServerPlayerEntity target = EntityArgumentType.getPlayer(context, "player");
		ServerCommandSource source = context.getSource();
		String staffName = source.getName();
		StaffSeeMod mod = StaffSeeMod.getInstance();

		if (!mod.getMuteManager().removeMute(target.getUuid())) {
			source.sendError(Text.literal(target.getGameProfile().name() + " is not muted."));
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

		target.sendMessage(Text.literal("You have been unmuted."), false);
		source.sendFeedback(() -> Text.literal("Unmuted " + target.getGameProfile().name() + "."), true);
		return 1;
	}

	private static int kickWithoutReason(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
		return kickPlayer(context, "No reason given");
	}

	private static int kick(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
		String reason = StringArgumentType.getString(context, "reason");
		return kickPlayer(context, reason);
	}

	private static int kickPlayer(CommandContext<ServerCommandSource> context, String reason) throws CommandSyntaxException {
		ServerPlayerEntity target = EntityArgumentType.getPlayer(context, "player");
		ServerCommandSource source = context.getSource();
		String staffName = source.getName();
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

		Text kickMessage = Text.literal("Kicked by " + staffName + ": " + reason);
		target.networkHandler.disconnect(kickMessage);
		source.sendFeedback(() -> Text.literal("Kicked " + target.getGameProfile().name() + ": " + reason), true);
		return 1;
	}

	private static int freezeWithoutReason(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
		return freezePlayer(context, "No reason given");
	}

	private static int freeze(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
		String reason = StringArgumentType.getString(context, "reason");
		return freezePlayer(context, reason);
	}

	private static int freezePlayer(CommandContext<ServerCommandSource> context, String reason) throws CommandSyntaxException {
		ServerPlayerEntity target = EntityArgumentType.getPlayer(context, "player");
		ServerCommandSource source = context.getSource();
		String staffName = source.getName();
		StaffSeeMod mod = StaffSeeMod.getInstance();
		FreezeManager freezeManager = mod.getFreezeManager();

		if (freezeManager.isFrozen(target.getUuid())) {
			source.sendError(Text.literal(target.getGameProfile().name() + " is already frozen."));
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

		target.sendMessage(Text.literal(mod.getConfig().freezeMessage), false);
		source.sendFeedback(() -> Text.literal("Froze " + target.getGameProfile().name() + ": " + reason), true);
		return 1;
	}

	private static int unfreeze(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
		ServerPlayerEntity target = EntityArgumentType.getPlayer(context, "player");
		ServerCommandSource source = context.getSource();
		String staffName = source.getName();
		StaffSeeMod mod = StaffSeeMod.getInstance();
		FreezeManager freezeManager = mod.getFreezeManager();

		if (!freezeManager.unfreeze(target.getUuid())) {
			source.sendError(Text.literal(target.getGameProfile().name() + " is not frozen."));
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

		target.sendMessage(Text.literal("You have been unfrozen."), false);
		source.sendFeedback(() -> Text.literal("Unfroze " + target.getGameProfile().name() + "."), true);
		return 1;
	}

	private static int report(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
		ServerPlayerEntity target = EntityArgumentType.getPlayer(context, "player");
		String reason = StringArgumentType.getString(context, "reason");
		ServerCommandSource source = context.getSource();

		if (!(source.getEntity() instanceof ServerPlayerEntity reporter)) {
			throw new SimpleCommandExceptionType(Text.literal("Only players can submit reports.")).create();
		}

		if (reporter.getUuid().equals(target.getUuid())) {
			source.sendError(Text.literal("You cannot report yourself."));
			return 0;
		}

		StaffSeeMod mod = StaffSeeMod.getInstance();
		StaffSeeConfig config = mod.getConfig();
		ReportManager reportManager = mod.getReportManager();

		if (reportManager.isOnCooldown(reporter.getUuid(), config.reportCooldownSeconds)) {
			int wait = reportManager.secondsUntilNextReport(reporter.getUuid(), config.reportCooldownSeconds);
			source.sendError(Text.literal("Please wait " + wait + "s before submitting another report."));
			return 0;
		}

		Report report;
		try {
			report = reportManager.createReport(
				reporter.getGameProfile().name(),
				reporter.getUuid(),
				target.getGameProfile().name(),
				target.getUuid(),
				reason
			);
		} catch (IOException e) {
			throw IO_ERROR.create();
		}

		Text staffMessage = Text.literal("[Report #" + report.id + "] " + reporter.getGameProfile().name()
			+ " reported " + target.getGameProfile().name() + ": " + reason);
		mod.notifyStaff(staffMessage);
		source.sendFeedback(() -> Text.literal("Report #" + report.id + " submitted. Staff has been notified."), false);
		return 1;
	}

	private static int listReports(CommandContext<ServerCommandSource> context) {
		StaffSeeMod mod = StaffSeeMod.getInstance();
		List<Report> reports = mod.getReportManager().listOpenReports(10);
		ServerCommandSource source = context.getSource();

		if (reports.isEmpty()) {
			source.sendFeedback(() -> Text.literal("No open reports."), false);
			return 1;
		}

		source.sendFeedback(() -> Text.literal("Open reports:"), false);
		for (Report report : reports) {
			source.sendFeedback(() -> Text.literal("#" + report.id + " " + report.reporterName + " -> " + report.targetName + ": " + report.reason), false);
		}
		return reports.size();
	}

	private static int resolveReport(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
		int id = IntegerArgumentType.getInteger(context, "id");
		ServerCommandSource source = context.getSource();
		String staffName = source.getName();
		StaffSeeMod mod = StaffSeeMod.getInstance();

		boolean resolved;
		try {
			resolved = mod.getReportManager().resolveReport(id, staffName, mod.getLogWriter());
		} catch (IOException e) {
			throw IO_ERROR.create();
		}

		if (!resolved) {
			source.sendError(Text.literal("Report #" + id + " was not found."));
			return 0;
		}

		source.sendFeedback(() -> Text.literal("Resolved report #" + id + "."), true);
		return 1;
	}

	private static int autoKick(ServerPlayerEntity target, ServerCommandSource source, String staffName, StaffSeeMod mod) throws CommandSyntaxException {
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

		target.networkHandler.disconnect(Text.literal(reason));
		source.sendFeedback(() -> Text.literal("Auto-kicked " + target.getGameProfile().name() + " after reaching warning limit."), true);
		return 1;
	}

	@SuppressWarnings("unchecked")
	private static void removeCommand(CommandDispatcher<ServerCommandSource> dispatcher, String name) {
		CommandNodeAccessor<ServerCommandSource> root = (CommandNodeAccessor<ServerCommandSource>) dispatcher.getRoot();
		root.staffsee$getChildren().remove(name);
		root.staffsee$getLiterals().remove(name);
	}

	private static String countLabel(WarnCounter warnCounter, ServerPlayerEntity target) {
		return warnCounter.getCount(target.getUuid()) + " warning(s)";
	}
}
