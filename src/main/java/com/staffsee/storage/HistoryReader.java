package com.staffsee.storage;

import com.staffsee.StaffSeeMod;
import com.staffsee.moderation.Report;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class HistoryReader {
	private static final DateTimeFormatter LOG_TIME = DateTimeFormatter.ofPattern("dd.MM.yy  HH:mm:ss");
	private static final Pattern LOG_HEADER = Pattern.compile("^\\[(\\d\\d\\.\\d\\d\\.\\d\\d  \\d\\d:\\d\\d:\\d\\d)]");
	private static final Pattern ARCHIVED_REPORT = Pattern.compile(
		"^\\[(.+?)] #(\\d+) resolved by (.+)\\nReporter: (.+) -> Target: (.+)\\nReason: (.*)$", Pattern.DOTALL);

	public record Entry(LocalDateTime when, String type, String details) {
	}

	private HistoryReader() {
	}

	public static List<Entry> read(LogWriter logWriter, List<Report> openReports, String playerName) {
		List<Entry> entries = new ArrayList<>();
		Path playerDir = findPlayerDir(logWriter.getStaffLogRoot(), playerName);
		if (playerDir != null) {
			for (LogType type : List.of(LogType.WARN, LogType.MUTE, LogType.KICK, LogType.FREEZE, LogType.BAN)) {
				readLogFile(playerDir.resolve(type.getFileName()), type, entries);
			}
		}

		for (Report report : openReports) {
			if (report.targetName.equalsIgnoreCase(playerName)) {
				LocalDateTime when = LocalDateTime.ofInstant(Instant.ofEpochMilli(report.timestamp), ZoneId.systemDefault());
				entries.add(new Entry(when, "REPORT", "#" + report.id + " (open) by " + report.reporterName + " | Reason: " + report.reason));
			}
		}
		readArchivedReports(logWriter.getReportsArchiveDir(), playerName, entries);

		entries.sort(Comparator.comparing(Entry::when).reversed());
		return entries;
	}

	private static Path findPlayerDir(Path root, String playerName) {
		String safeName = LogWriter.sanitizePlayerName(playerName);
		Path exact = root.resolve(safeName);
		if (Files.isDirectory(exact)) {
			return exact;
		}
		if (!Files.isDirectory(root)) {
			return null;
		}
		// folders are named after whatever capitalization the player had at the time
		try (Stream<Path> dirs = Files.list(root)) {
			return dirs.filter(Files::isDirectory)
				.filter(dir -> dir.getFileName().toString().equalsIgnoreCase(safeName))
				.findFirst()
				.orElse(null);
		} catch (IOException e) {
			return null;
		}
	}

	private static void readLogFile(Path file, LogType fileType, List<Entry> out) {
		for (String block : readBlocks(file)) {
			String[] lines = block.split("\n", 2);
			Matcher header = LOG_HEADER.matcher(lines[0]);
			if (!header.find()) {
				continue;
			}
			LocalDateTime when = parseTime(header.group(1));
			if (when == null) {
				continue;
			}
			String body = lines.length > 1 ? lines[1] : "";
			out.add(new Entry(when, typeLabel(fileType, body), body.replace("\n", " | ")));
		}
	}

	private static String typeLabel(LogType fileType, String body) {
		if (body.contains("Action: unmuted")) {
			return "UNMUTE";
		}
		if (body.contains("Action: unfrozen")) {
			return "UNFREEZE";
		}
		if (body.contains("Action: unbanned")) {
			return "UNBAN";
		}
		return switch (fileType) {
			case MUTE -> "MUTE";
			case KICK -> "KICK";
			case FREEZE -> "FREEZE";
			case BAN -> body.contains("(perm)") ? "BAN" : "TEMPBAN";
			default -> "WARN";
		};
	}

	private static void readArchivedReports(Path archiveDir, String playerName, List<Entry> out) {
		if (!Files.isDirectory(archiveDir)) {
			return;
		}
		List<Path> files;
		try (Stream<Path> list = Files.list(archiveDir)) {
			files = list.filter(f -> f.getFileName().toString().endsWith(".txt")).toList();
		} catch (IOException e) {
			return;
		}
		for (Path file : files) {
			for (String block : readBlocks(file)) {
				Matcher m = ARCHIVED_REPORT.matcher(block);
				if (!m.matches() || !m.group(5).trim().equalsIgnoreCase(playerName)) {
					continue;
				}
				LocalDateTime when = parseTime(m.group(1));
				if (when != null) {
					out.add(new Entry(when, "REPORT", "#" + m.group(2) + " (resolved by " + m.group(3) + ") by "
						+ m.group(4) + " | Reason: " + m.group(6)));
				}
			}
		}
	}

	private static List<String> readBlocks(Path file) {
		if (!Files.isRegularFile(file)) {
			return List.of();
		}
		try {
			String content = Files.readString(file, StandardCharsets.UTF_8).replace("\r", "").trim();
			if (content.isEmpty()) {
				return List.of();
			}
			return List.of(content.split("\n\n"));
		} catch (IOException e) {
			StaffSeeMod.LOGGER.warn("Could not read {}", file, e);
			return List.of();
		}
	}

	private static LocalDateTime parseTime(String text) {
		try {
			return LocalDateTime.parse(text, LOG_TIME);
		} catch (DateTimeParseException e) {
			return null;
		}
	}
}
