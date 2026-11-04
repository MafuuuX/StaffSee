package com.staffsee.storage;

import com.staffsee.util.TimeFormat;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class LogWriter {
	private final Path staffLogRoot;

	public LogWriter(Path staffLogRoot) {
		this.staffLogRoot = staffLogRoot;
	}

	public void ensureRoot() throws IOException {
		Files.createDirectories(staffLogRoot);
	}

	public void append(String playerName, LogType type, String body) throws IOException {
		String safeName = sanitizePlayerName(playerName);
		Path playerDir = staffLogRoot.resolve(safeName);
		Files.createDirectories(playerDir);

		Path logFile = playerDir.resolve(type.getFileName());
		String entry = TimeFormat.now() + "\n" + body + "\n\n";

		try (FileWriter writer = new FileWriter(logFile.toFile(), StandardCharsets.UTF_8, true)) {
			writer.write(entry);
		}
	}

	public List<String> readRecentWarns(String playerName, int limit) throws IOException {
		String safeName = sanitizePlayerName(playerName);
		Path logFile = staffLogRoot.resolve(safeName).resolve(LogType.WARN.getFileName());
		if (!Files.exists(logFile)) {
			return Collections.emptyList();
		}

		String content = Files.readString(logFile, StandardCharsets.UTF_8).trim();
		if (content.isEmpty()) {
			return Collections.emptyList();
		}

		String[] entries = content.split("\n\n");
		List<String> result = new ArrayList<>();
		int start = Math.max(0, entries.length - limit);
		for (int i = start; i < entries.length; i++) {
			result.add(entries[i].trim());
		}
		return result;
	}

	public static String sanitizePlayerName(String playerName) {
		StringBuilder safe = new StringBuilder();
		for (char c : playerName.toCharArray()) {
			if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_') {
				safe.append(c);
			}
		}
		if (safe.isEmpty()) {
			return "unknown";
		}
		return safe.toString();
	}

	public Path getStaffLogRoot() {
		return staffLogRoot;
	}

	public File getCountsFile() {
		return staffLogRoot.resolve("_counts.json").toFile();
	}

	public File getMutesFile() {
		return staffLogRoot.resolve("_mutes.json").toFile();
	}

	public File getFreezesFile() {
		return staffLogRoot.resolve("_freezes.json").toFile();
	}

	public File getVanishFile() {
		return staffLogRoot.resolve("_vanish.json").toFile();
	}

	public File getReportsFile() {
		return staffLogRoot.resolve("_reports.json").toFile();
	}

	public Path getReportsArchiveDir() {
		return staffLogRoot.resolve("_reports");
	}

	public Path getChatLogDir() {
		return staffLogRoot.resolve("_chat");
	}
}
