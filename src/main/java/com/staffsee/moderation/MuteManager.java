package com.staffsee.moderation;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class MuteManager {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Type MAP_TYPE = new TypeToken<Map<String, MuteEntry>>() {}.getType();

	private final File mutesFile;
	private final Map<UUID, MuteEntry> activeMutes = new HashMap<>();

	public MuteManager(File mutesFile) {
		this.mutesFile = mutesFile;
	}

	public void load() throws IOException {
		activeMutes.clear();
		if (!mutesFile.exists()) {
			return;
		}

		try (FileReader reader = new FileReader(mutesFile)) {
			Map<String, MuteEntry> loaded = GSON.fromJson(reader, MAP_TYPE);
			if (loaded == null) {
				return;
			}
			long now = System.currentTimeMillis();
			for (Map.Entry<String, MuteEntry> entry : loaded.entrySet()) {
				MuteEntry muteEntry = entry.getValue();
				if (!muteEntry.isExpired(now)) {
					activeMutes.put(UUID.fromString(entry.getKey()), muteEntry);
				}
			}
		}
	}

	public void save() throws IOException {
		File parent = mutesFile.getParentFile();
		if (parent != null && !parent.exists()) {
			parent.mkdirs();
		}

		Map<String, MuteEntry> serialized = new HashMap<>();
		for (Map.Entry<UUID, MuteEntry> entry : activeMutes.entrySet()) {
			serialized.put(entry.getKey().toString(), entry.getValue());
		}

		try (FileWriter writer = new FileWriter(mutesFile)) {
			GSON.toJson(serialized, writer);
		}
	}

	public void setMute(UUID playerId, MuteEntry entry) {
		activeMutes.put(playerId, entry);
	}

	public boolean removeMute(UUID playerId) {
		return activeMutes.remove(playerId) != null;
	}

	public boolean isMuted(UUID playerId) {
		MuteEntry entry = activeMutes.get(playerId);
		if (entry == null) {
			return false;
		}

		if (entry.isExpired(System.currentTimeMillis())) {
			activeMutes.remove(playerId);
			return false;
		}

		return true;
	}

	public Optional<MuteEntry> getMute(UUID playerId) {
		if (!isMuted(playerId)) {
			return Optional.empty();
		}
		return Optional.ofNullable(activeMutes.get(playerId));
	}

	public void purgeExpired() {
		long now = System.currentTimeMillis();
		Iterator<Map.Entry<UUID, MuteEntry>> iterator = activeMutes.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<UUID, MuteEntry> entry = iterator.next();
			if (entry.getValue().isExpired(now)) {
				iterator.remove();
			}
		}
	}
}
