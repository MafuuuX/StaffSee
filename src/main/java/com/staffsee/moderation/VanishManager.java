package com.staffsee.moderation;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.staffsee.mixin.ChunkMapAccessor;
import com.staffsee.mixin.TrackedEntityInvoker;
import com.staffsee.permission.StaffPermissions;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class VanishManager {
	private static final Gson GSON = new Gson();
	private static final Type SET_TYPE = new TypeToken<Set<UUID>>() {}.getType();

	private final File vanishFile;
	private final Set<UUID> vanished = new HashSet<>();

	public VanishManager(File vanishFile) {
		this.vanishFile = vanishFile;
	}

	public void load() throws IOException {
		vanished.clear();
		if (!vanishFile.exists()) {
			return;
		}
		try (FileReader reader = new FileReader(vanishFile)) {
			Set<UUID> loaded = GSON.fromJson(reader, SET_TYPE);
			if (loaded != null) {
				vanished.addAll(loaded);
			}
		}
	}

	public void save() throws IOException {
		try (FileWriter writer = new FileWriter(vanishFile)) {
			GSON.toJson(vanished, writer);
		}
	}

	public boolean anyVanished() {
		return !vanished.isEmpty();
	}

	public boolean isVanished(UUID playerId) {
		return vanished.contains(playerId);
	}

	public boolean canSee(ServerPlayer viewer, ServerPlayer target) {
		if (viewer == target || !vanished.contains(target.getUUID())) {
			return true;
		}
		return StaffPermissions.canSeeVanished(viewer.createCommandSourceStack());
	}

	public void setVanished(ServerPlayer player, boolean vanish) {
		if (vanish ? !vanished.add(player.getUUID()) : !vanished.remove(player.getUUID())) {
			return;
		}

		MinecraftServer server = player.level().getServer();
		ServerLevel level = player.level();
		server.invalidateStatus();
		Object tracked = ((ChunkMapAccessor) level.getChunkSource().chunkMap).staffsee$getEntityMap().get(player.getId());

		for (ServerPlayer viewer : server.getPlayerList().getPlayers()) {
			// on unvanish staff get the re-add too, someone may have lost staff rights in between
			if (viewer == player || vanish && StaffPermissions.canSeeVanished(viewer.createCommandSourceStack())) {
				continue;
			}
			if (vanish) {
				viewer.connection.send(new ClientboundPlayerInfoRemovePacket(List.of(player.getUUID())));
			} else {
				viewer.connection.send(ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of(player)));
			}
			if (tracked != null && viewer.level() == level) {
				((TrackedEntityInvoker) tracked).staffsee$updatePlayer(viewer);
			}
		}
	}

	// join/leave lines of vanished players only go to people who can see them
	public void sendToViewers(MinecraftServer server, ServerPlayer player, Component message) {
		Component tagged = message.copy().append(Component.literal(" (vanished)").withStyle(ChatFormatting.GRAY));
		for (ServerPlayer viewer : server.getPlayerList().getPlayers()) {
			if (viewer != player && canSee(viewer, player)) {
				viewer.sendSystemMessage(tagged);
			}
		}
	}
}
