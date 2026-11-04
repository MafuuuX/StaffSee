package com.staffsee.moderation;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.staffsee.storage.LogWriter;
import com.staffsee.util.TimeFormat;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class ReportManager {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final DateTimeFormatter ARCHIVE_DATE = DateTimeFormatter.ISO_LOCAL_DATE;

	private final File reportsFile;
	private final Path reportsArchiveDir;
	private final Map<UUID, Long> lastReportAt = new HashMap<>();
	private ReportStore store = new ReportStore();

	public ReportManager(File reportsFile, Path reportsArchiveDir) {
		this.reportsFile = reportsFile;
		this.reportsArchiveDir = reportsArchiveDir;
	}

	public void load() throws IOException {
		store = new ReportStore();
		if (!reportsFile.exists()) {
			return;
		}

		try (FileReader reader = new FileReader(reportsFile)) {
			ReportStore loaded = GSON.fromJson(reader, ReportStore.class);
			if (loaded != null) {
				store = loaded;
				if (store.reports == null) {
					store.reports = new HashMap<>();
				}
			}
		}
	}

	public void save() throws IOException {
		File parent = reportsFile.getParentFile();
		if (parent != null && !parent.exists()) {
			parent.mkdirs();
		}

		try (FileWriter writer = new FileWriter(reportsFile)) {
			GSON.toJson(store, writer);
		}
	}

	public boolean isOnCooldown(UUID reporterId, int cooldownSeconds) {
		if (cooldownSeconds <= 0) {
			return false;
		}
		Long last = lastReportAt.get(reporterId);
		if (last == null) {
			return false;
		}
		return System.currentTimeMillis() - last < cooldownSeconds * 1000L;
	}

	public int secondsUntilNextReport(UUID reporterId, int cooldownSeconds) {
		Long last = lastReportAt.get(reporterId);
		if (last == null || cooldownSeconds <= 0) {
			return 0;
		}
		long remaining = cooldownSeconds * 1000L - (System.currentTimeMillis() - last);
		return remaining > 0 ? (int) Math.ceil(remaining / 1000.0) : 0;
	}

	public Report createReport(String reporterName, UUID reporterId, String targetName, UUID targetId, String reason) throws IOException {
		Report report = new Report(
			store.nextId++,
			reporterName,
			reporterId.toString(),
			targetName,
			targetId.toString(),
			reason,
			System.currentTimeMillis()
		);
		store.reports.put(String.valueOf(report.id), report);
		lastReportAt.put(reporterId, System.currentTimeMillis());
		save();
		return report;
	}

	public List<Report> listOpenReports(int limit) {
		List<Report> open = new ArrayList<>(store.reports.values());
		open.sort(Comparator.comparingInt(report -> report.id));
		if (open.size() <= limit) {
			return open;
		}
		return open.subList(0, limit);
	}

	public Optional<Report> getReport(int id) {
		return Optional.ofNullable(store.reports.get(String.valueOf(id)));
	}

	public boolean resolveReport(int id, String resolvedBy, LogWriter logWriter) throws IOException {
		Report report = store.reports.remove(String.valueOf(id));
		if (report == null) {
			return false;
		}

		Files.createDirectories(reportsArchiveDir);
		String date = ARCHIVE_DATE.format(LocalDate.now());
		Path archiveFile = reportsArchiveDir.resolve(date + ".txt");
		String entry = TimeFormat.now() + " #" + report.id + " resolved by " + resolvedBy + "\n"
			+ "Reporter: " + report.reporterName + " -> Target: " + report.targetName + "\n"
			+ "Reason: " + report.reason + "\n\n";

		try (FileWriter writer = new FileWriter(archiveFile.toFile(), StandardCharsets.UTF_8, true)) {
			writer.write(entry);
		}

		save();
		return true;
	}
}
