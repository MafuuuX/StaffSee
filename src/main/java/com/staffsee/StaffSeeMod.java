package com.staffsee;

import com.staffsee.command.StaffCommands;
import com.staffsee.config.StaffSeeConfig;
import com.staffsee.integration.DiscordWebhook;
import com.staffsee.moderation.FreezeManager;
import com.staffsee.moderation.MuteManager;
import com.staffsee.moderation.ReportManager;
import com.staffsee.moderation.StaffChat;
import com.staffsee.moderation.VanishManager;
import com.staffsee.permission.StaffPermissions;
import com.staffsee.storage.ChatLogWriter;
import com.staffsee.storage.LogWriter;
import com.staffsee.storage.WarnCounter;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.WorldSavePath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

public class StaffSeeMod implements ModInitializer {
	public static final String MOD_ID = "staffsee";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static StaffSeeMod instance;

	private StaffSeeConfig config;
	private LogWriter logWriter;
	private ChatLogWriter chatLogWriter;
	private MuteManager muteManager;
	private FreezeManager freezeManager;
	private ReportManager reportManager;
	private WarnCounter warnCounter;
	private VanishManager vanishManager;
	private final StaffChat staffChat = new StaffChat();
	private MinecraftServer server;
	private int tickCounter;

	public static StaffSeeMod getInstance() {
		return instance;
	}

	@Override
	public void onInitialize() {
		instance = this;

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
			StaffCommands.register(dispatcher)
		);

		ServerLifecycleEvents.SERVER_STARTING.register(server -> {
			this.server = server;
			File configFile = new File("config/staffsee.json");
			try {
				this.config = StaffSeeConfig.load(configFile);
			} catch (IOException e) {
				LOGGER.error("Failed to load StaffSee config, using defaults.", e);
				this.config = new StaffSeeConfig();
			}

			Path staffLogRoot = server.getSavePath(WorldSavePath.ROOT).resolve("StaffLog");
			this.logWriter = new LogWriter(staffLogRoot);
			this.chatLogWriter = new ChatLogWriter(logWriter.getChatLogDir());
			this.muteManager = new MuteManager(logWriter.getMutesFile());
			this.freezeManager = new FreezeManager(logWriter.getFreezesFile());
			this.reportManager = new ReportManager(logWriter.getReportsFile(), logWriter.getReportsArchiveDir());
			this.warnCounter = new WarnCounter(logWriter.getCountsFile());
			this.vanishManager = new VanishManager(logWriter.getVanishFile());

			try {
				logWriter.ensureRoot();
				muteManager.load();
				freezeManager.load();
				reportManager.load();
				warnCounter.load();
				vanishManager.load();
			} catch (IOException e) {
				LOGGER.error("Failed to initialize StaffSee storage.", e);
			}
		});

		ServerLifecycleEvents.SERVER_STOPPING.register(server -> persistState());

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			tickCounter++;
			if (freezeManager != null) {
				for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
					if (freezeManager.isFrozen(player.getUuid())) {
						freezeManager.enforceFrozen(player);
					}
				}
			}
			if (tickCounter % 20 != 0) {
				return;
			}
			if (muteManager != null) {
				muteManager.purgeExpired();
			}
		});

		ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, params) -> {
			if (muteManager == null || config == null) {
				return true;
			}
			if (staffChat.isToggled(sender.getUuid()) && StaffPermissions.canStaffChat(sender.getCommandSource())) {
				sendStaffChat(sender.getGameProfile().getName(), message.getSignedContent());
				return false;
			}
			if (freezeManager != null && freezeManager.isFrozen(sender.getUuid())) {
				sender.sendMessage(Text.literal(config.freezeMessage), false);
				return false;
			}
			if (muteManager.isMuted(sender.getUuid())) {
				sender.sendMessage(Text.literal(config.muteMessage), false);
				return false;
			}
			if (config.logChat && chatLogWriter != null) {
				try {
					chatLogWriter.append(sender.getGameProfile().getName(), message.getSignedContent());
				} catch (IOException e) {
					LOGGER.error("Failed to write chat log.", e);
				}
			}
			return true;
		});

		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> staffChat.remove(handler.player.getUuid()));

		registerFreezeHandlers();
	}

	private void registerFreezeHandlers() {
		AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
			if (player instanceof ServerPlayerEntity serverPlayer && isFrozen(serverPlayer)) {
				sendFreezeMessage(serverPlayer);
				return ActionResult.FAIL;
			}
			return ActionResult.PASS;
		});

		UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
			if (player instanceof ServerPlayerEntity serverPlayer && isFrozen(serverPlayer)) {
				sendFreezeMessage(serverPlayer);
				return ActionResult.FAIL;
			}
			return ActionResult.PASS;
		});

		UseItemCallback.EVENT.register((player, world, hand) -> {
			if (player instanceof ServerPlayerEntity serverPlayer && isFrozen(serverPlayer)) {
				sendFreezeMessage(serverPlayer);
				return ActionResult.FAIL;
			}
			return ActionResult.PASS;
		});

		PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
			if (player instanceof ServerPlayerEntity serverPlayer && isFrozen(serverPlayer)) {
				sendFreezeMessage(serverPlayer);
				return false;
			}
			return true;
		});
	}

	private boolean isFrozen(ServerPlayerEntity player) {
		return freezeManager != null && freezeManager.isFrozen(player.getUuid());
	}

	private void sendFreezeMessage(ServerPlayerEntity player) {
		if (config != null) {
			player.sendMessage(Text.literal(config.freezeMessage), false);
		}
	}

	public void notifyStaff(Text message) {
		notifyStaff(message, null);
	}

	public void notifyStaff(Text message, ServerPlayerEntity except) {
		if (server == null) {
			return;
		}
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			if (player != except && StaffPermissions.canModerate(player.getCommandSource())) {
				player.sendMessage(message, false);
			}
		}
	}

	public void sendStaffChat(String senderName, String message) {
		staffChat.send(server, senderName, message);
		if (config != null && config.logChat && chatLogWriter != null) {
			try {
				chatLogWriter.append(senderName, "[Staff] " + message);
			} catch (IOException e) {
				LOGGER.error("Failed to write chat log.", e);
			}
		}
	}

	public void sendReportWebhook(int id, String reporter, String target, String reason) {
		if (config != null && config.discordReports) {
			DiscordWebhook.send(config.discordWebhookUrl, "**Report #" + id + "** "
				+ DiscordWebhook.escape(reporter) + " reported **" + DiscordWebhook.escape(target) + "**: " + DiscordWebhook.escape(reason));
		}
	}

	public void sendPunishmentWebhook(String action, String target, String staffName, String reason) {
		if (config != null && config.discordPunishments) {
			String text = "**" + action + "** " + DiscordWebhook.escape(target) + " by " + DiscordWebhook.escape(staffName);
			if (reason != null) {
				text += ": " + DiscordWebhook.escape(reason);
			}
			DiscordWebhook.send(config.discordWebhookUrl, text);
		}
	}

	public void persistState() {
		if (muteManager == null || warnCounter == null) {
			return;
		}
		try {
			muteManager.save();
			if (freezeManager != null) {
				freezeManager.save();
			}
			if (reportManager != null) {
				reportManager.save();
			}
			warnCounter.save();
		} catch (IOException e) {
			LOGGER.error("Failed to save StaffSee state.", e);
		}
	}

	public StaffSeeConfig getConfig() {
		return config;
	}

	public LogWriter getLogWriter() {
		return logWriter;
	}

	public ChatLogWriter getChatLogWriter() {
		return chatLogWriter;
	}

	public MuteManager getMuteManager() {
		return muteManager;
	}

	public FreezeManager getFreezeManager() {
		return freezeManager;
	}

	public ReportManager getReportManager() {
		return reportManager;
	}

	public VanishManager getVanishManager() {
		return vanishManager;
	}

	public StaffChat getStaffChat() {
		return staffChat;
	}

	public WarnCounter getWarnCounter() {
		return warnCounter;
	}

	public MinecraftServer getServer() {
		return server;
	}
}
