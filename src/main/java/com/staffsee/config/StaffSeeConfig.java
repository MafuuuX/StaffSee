package com.staffsee.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public final class StaffSeeConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public int autoKickAfterWarns = 0;
	public String muteMessage = "You are muted.";
	public boolean warnBroadcast = false;
	public String freezeMessage = "You are frozen by staff.";
	public boolean logChat = true;
	public int reportCooldownSeconds = 60;
	public String discordWebhookUrl = "";
	public boolean discordReports = true;
	public boolean discordPunishments = false;

	public static StaffSeeConfig load(File configFile) throws IOException {
		if (!configFile.exists()) {
			StaffSeeConfig config = new StaffSeeConfig();
			config.save(configFile);
			return config;
		}

		StaffSeeConfig loaded;
		try (FileReader reader = new FileReader(configFile)) {
			loaded = GSON.fromJson(reader, StaffSeeConfig.class);
		}
		if (loaded == null) {
			loaded = new StaffSeeConfig();
		}
		// rewrite so options added in an update show up in the file
		loaded.save(configFile);
		return loaded;
	}

	public void save(File configFile) throws IOException {
		File parent = configFile.getParentFile();
		if (parent != null && !parent.exists()) {
			parent.mkdirs();
		}

		try (FileWriter writer = new FileWriter(configFile)) {
			GSON.toJson(this, writer);
		}
	}
}
