package com.staffsee.moderation;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.staffsee.mixin.ChunkMapAccessor;
import com.staffsee.mixin.TrackedEntityInvoker;
import com.staffsee.permission.StaffPermissions;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRemoveS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

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

	public boolean canSee(ServerPlayerEntity viewer, ServerPlayerEntity target) {
		if (viewer == target || !vanished.contains(target.getUuid())) {
			return true;
		}
		return StaffPermissions.canSeeVanished(viewer.getCommandSource());
	}

	public void setVanished(ServerPlayerEntity player, boolean vanish) {
		if (vanish ? !vanished.add(player.getUuid()) : !vanished.remove(player.getUuid())) {
			return;
		}

		MinecraftServer server = player.getEntityWorld().getServer();
		ServerWorld level = player.getEntityWorld();
		server.forcePlayerSampleUpdate();
		Object tracked = ((ChunkMapAccessor) level.getChunkManager().chunkLoadingManager).staffsee$getEntityMap().get(player.getId());

		for (ServerPlayerEntity viewer : server.getPlayerManager().getPlayerList()) {
			// on unvanish staff get the re-add too, someone may have lost staff rights in between
			if (viewer == player || vanish && StaffPermissions.canSeeVanished(viewer.getCommandSource())) {
				continue;
			}
			if (vanish) {
				viewer.networkHandler.sendPacket(new PlayerRemoveS2CPacket(List.of(player.getUuid())));
			} else {
				viewer.networkHandler.sendPacket(PlayerListS2CPacket.entryFromPlayer(List.of(player)));
			}
			if (tracked != null && viewer.getEntityWorld() == level) {
				((TrackedEntityInvoker) tracked).staffsee$updatePlayer(viewer);
			}
		}
	}

	// join/leave lines of vanished players only go to people who can see them
	public void sendToViewers(MinecraftServer server, ServerPlayerEntity player, Text message) {
		Text tagged = message.copy().append(Text.literal(" (vanished)").formatted(Formatting.GRAY));
		for (ServerPlayerEntity viewer : server.getPlayerManager().getPlayerList()) {
			if (viewer != player && canSee(viewer, player)) {
				viewer.sendMessage(tagged);
			}
		}
	}
}
