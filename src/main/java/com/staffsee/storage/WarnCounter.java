package com.staffsee.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class WarnCounter {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Type MAP_TYPE = new TypeToken<Map<String, Integer>>() {}.getType();

	private final File countsFile;
	private final Map<UUID, Integer> counts = new HashMap<>();

	public WarnCounter(File countsFile) {
		this.countsFile = countsFile;
	}

	public void load() throws IOException {
		counts.clear();
		if (!countsFile.exists()) {
			return;
		}

		try (FileReader reader = new FileReader(countsFile)) {
			Map<String, Integer> loaded = GSON.fromJson(reader, MAP_TYPE);
			if (loaded == null) {
				return;
			}
			for (Map.Entry<String, Integer> entry : loaded.entrySet()) {
				counts.put(UUID.fromString(entry.getKey()), entry.getValue());
			}
		}
	}

	public void save() throws IOException {
		File parent = countsFile.getParentFile();
		if (parent != null && !parent.exists()) {
			parent.mkdirs();
		}

		Map<String, Integer> serialized = new HashMap<>();
		for (Map.Entry<UUID, Integer> entry : counts.entrySet()) {
			serialized.put(entry.getKey().toString(), entry.getValue());
		}

		try (FileWriter writer = new FileWriter(countsFile)) {
			GSON.toJson(serialized, writer);
		}
	}

	public int increment(UUID playerId) {
		int next = counts.getOrDefault(playerId, 0) + 1;
		counts.put(playerId, next);
		return next;
	}

	public int getCount(UUID playerId) {
		return counts.getOrDefault(playerId, 0);
	}
}
