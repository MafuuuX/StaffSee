package com.staffsee.storage;

import com.staffsee.util.TimeFormat;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public final class ChatLogWriter {
	private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.ISO_LOCAL_DATE;

	private final Path chatDir;

	public ChatLogWriter(Path chatDir) {
		this.chatDir = chatDir;
	}

	public void append(String playerName, String message) throws IOException {
		Files.createDirectories(chatDir);
		String safeName = LogWriter.sanitizePlayerName(playerName);
		String sanitizedMessage = message.replace('\n', ' ').replace('\r', ' ');
		String line = TimeFormat.now() + " " + safeName + ": " + sanitizedMessage + "\n";
		Path logFile = chatDir.resolve(FILE_DATE.format(LocalDate.now()) + ".txt");

		try (FileWriter writer = new FileWriter(logFile.toFile(), StandardCharsets.UTF_8, true)) {
			writer.write(line);
		}
	}
}
