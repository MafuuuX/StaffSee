package com.staffsee;

import com.staffsee.command.StaffCommands;
import com.staffsee.config.StaffSeeConfig;
import com.staffsee.moderation.FreezeManager;
import com.staffsee.moderation.MuteManager;
import com.staffsee.moderation.ReportManager;
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
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.storage.LevelResource;
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

			Path staffLogRoot = server.getWorldPath(LevelResource.ROOT).resolve("StaffLog");
			this.logWriter = new LogWriter(staffLogRoot);
			this.chatLogWriter = new ChatLogWriter(logWriter.getChatLogDir());
			this.muteManager = new MuteManager(logWriter.getMutesFile());
			this.freezeManager = new FreezeManager(logWriter.getFreezesFile());
			this.reportManager = new ReportManager(logWriter.getReportsFile(), logWriter.getReportsArchiveDir());
			this.warnCounter = new WarnCounter(logWriter.getCountsFile());

			try {
				logWriter.ensureRoot();
				muteManager.load();
				freezeManager.load();
				reportManager.load();
				warnCounter.load();
			} catch (IOException e) {
				LOGGER.error("Failed to initialize StaffSee storage.", e);
			}
		});

		ServerLifecycleEvents.SERVER_STOPPING.register(server -> persistState());

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			tickCounter++;
			if (freezeManager != null) {
				for (ServerPlayer player : server.getPlayerList().getPlayers()) {
					if (freezeManager.isFrozen(player.getUUID())) {
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
			if (!(sender instanceof ServerPlayer player)) {
				return true;
			}
			if (muteManager == null || config == null) {
				return true;
			}
			if (freezeManager != null && freezeManager.isFrozen(player.getUUID())) {
				player.sendSystemMessage(Component.literal(config.freezeMessage));
				return false;
			}
			if (muteManager.isMuted(player.getUUID())) {
				player.sendSystemMessage(Component.literal(config.muteMessage));
				return false;
			}
			if (config.logChat && chatLogWriter != null) {
				try {
					chatLogWriter.append(player.getGameProfile().name(), message.decoratedContent().getString());
				} catch (IOException e) {
					LOGGER.error("Failed to write chat log.", e);
				}
			}
			return true;
		});

		registerFreezeHandlers();
	}

	private void registerFreezeHandlers() {
		AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
			if (player instanceof ServerPlayer serverPlayer && isFrozen(serverPlayer)) {
				sendFreezeMessage(serverPlayer);
				return InteractionResult.FAIL;
			}
			return InteractionResult.PASS;
		});

		UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
			if (player instanceof ServerPlayer serverPlayer && isFrozen(serverPlayer)) {
				sendFreezeMessage(serverPlayer);
				return InteractionResult.FAIL;
			}
			return InteractionResult.PASS;
		});

		UseItemCallback.EVENT.register((player, world, hand) -> {
			if (player instanceof ServerPlayer serverPlayer && isFrozen(serverPlayer)) {
				sendFreezeMessage(serverPlayer);
				return InteractionResult.FAIL;
			}
			return InteractionResult.PASS;
		});

		PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
			if (player instanceof ServerPlayer serverPlayer && isFrozen(serverPlayer)) {
				sendFreezeMessage(serverPlayer);
				return false;
			}
			return true;
		});
	}

	private boolean isFrozen(ServerPlayer player) {
		return freezeManager != null && freezeManager.isFrozen(player.getUUID());
	}

	private void sendFreezeMessage(ServerPlayer player) {
		if (config != null) {
			player.sendSystemMessage(Component.literal(config.freezeMessage));
		}
	}

	public void notifyStaff(Component message) {
		if (server == null) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (StaffPermissions.canModerate(player.createCommandSourceStack())) {
				player.sendSystemMessage(message);
			}
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

	public WarnCounter getWarnCounter() {
		return warnCounter;
	}

	public MinecraftServer getServer() {
		return server;
	}
}
