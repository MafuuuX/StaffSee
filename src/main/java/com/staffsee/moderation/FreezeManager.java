package com.staffsee.moderation;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.server.level.ServerPlayer;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class FreezeManager {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Type MAP_TYPE = new TypeToken<Map<String, FreezeEntry>>() {}.getType();

	private final File freezesFile;
	private final Map<UUID, FreezeEntry> activeFreezes = new HashMap<>();

	public FreezeManager(File freezesFile) {
		this.freezesFile = freezesFile;
	}

	public void load() throws IOException {
		activeFreezes.clear();
		if (!freezesFile.exists()) {
			return;
		}

		try (FileReader reader = new FileReader(freezesFile)) {
			Map<String, FreezeEntry> loaded = GSON.fromJson(reader, MAP_TYPE);
			if (loaded == null) {
				return;
			}
			for (Map.Entry<String, FreezeEntry> entry : loaded.entrySet()) {
				activeFreezes.put(UUID.fromString(entry.getKey()), entry.getValue());
			}
		}
	}

	public void save() throws IOException {
		File parent = freezesFile.getParentFile();
		if (parent != null && !parent.exists()) {
			parent.mkdirs();
		}

		Map<String, FreezeEntry> serialized = new HashMap<>();
		for (Map.Entry<UUID, FreezeEntry> entry : activeFreezes.entrySet()) {
			serialized.put(entry.getKey().toString(), entry.getValue());
		}

		try (FileWriter writer = new FileWriter(freezesFile)) {
			GSON.toJson(serialized, writer);
		}
	}

	public void freeze(ServerPlayer player, String frozenBy, String reason) {
		String dimension = player.level().dimension().identifier().toString();
		FreezeEntry entry = new FreezeEntry(
			player.getGameProfile().name(),
			frozenBy,
			reason,
			player.getX(),
			player.getY(),
			player.getZ(),
			player.getYRot(),
			player.getXRot(),
			dimension
		);
		activeFreezes.put(player.getUUID(), entry);
	}

	public boolean unfreeze(UUID playerId) {
		return activeFreezes.remove(playerId) != null;
	}

	public boolean isFrozen(UUID playerId) {
		return activeFreezes.containsKey(playerId);
	}

	public Optional<FreezeEntry> getFreeze(UUID playerId) {
		return Optional.ofNullable(activeFreezes.get(playerId));
	}

	public void enforceFrozen(ServerPlayer player) {
		FreezeEntry entry = activeFreezes.get(player.getUUID());
		if (entry == null) {
			return;
		}

		String currentDimension = player.level().dimension().identifier().toString();
		if (!currentDimension.equals(entry.dimension)) {
			return;
		}

		player.teleportTo(entry.x, entry.y, entry.z);
		player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
		player.resetFallDistance();
		player.setYRot(entry.yaw);
		player.setXRot(entry.pitch);
	}
}
