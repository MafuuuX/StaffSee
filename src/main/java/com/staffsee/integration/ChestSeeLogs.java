package com.staffsee.integration;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Reads ChestSee's text logs directly so StaffSee doesn't need ChestSee on the classpath.
 */
public final class ChestSeeLogs {
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd.MM.yy  HH:mm:ss");
	private static final Pattern HEADER = Pattern.compile("^\\[(\\d\\d\\.\\d\\d\\.\\d\\d  \\d\\d:\\d\\d:\\d\\d)] (\\S+)$");
	private static final Pattern BLOCK_FILE = Pattern.compile("^(.+) (-?\\d+) (-?\\d+) (-?\\d+)\\.txt$");

	public record Hit(LocalDateTime when, String dimension, String container, String pos, List<String> lines) {
	}

	private ChestSeeLogs() {
	}

	public static boolean isAvailable() {
		return FabricLoader.getInstance().isModLoaded("chestsee");
	}

	public static List<Hit> search(Path chestLogRoot, String player) throws IOException {
		List<Hit> hits = new ArrayList<>();
		if (!Files.isDirectory(chestLogRoot)) {
			return hits;
		}

		List<Path> files;
		try (Stream<Path> walk = Files.walk(chestLogRoot, 2)) {
			files = walk.filter(f -> Files.isRegularFile(f) && f.getFileName().toString().endsWith(".txt")).toList();
		}

		for (Path file : files) {
			String dimension = file.getParent().equals(chestLogRoot) ? "" : file.getParent().getFileName().toString();
			String name = file.getFileName().toString();
			String container = name.substring(0, name.length() - 4);
			String pos = null;
			Matcher fileMatch = BLOCK_FILE.matcher(name);
			if (fileMatch.matches()) {
				container = fileMatch.group(1);
				pos = fileMatch.group(2) + " " + fileMatch.group(3) + " " + fileMatch.group(4);
			}

			String content = Files.readString(file, StandardCharsets.UTF_8).replace("\r", "");
			for (String block : content.split("\n\n")) {
				String[] lines = block.strip().split("\n");
				Matcher header = HEADER.matcher(lines[0]);
				if (lines.length < 2 || !header.matches() || !header.group(2).equalsIgnoreCase(player)) {
					continue;
				}
				try {
					LocalDateTime when = LocalDateTime.parse(header.group(1), TIME);
					hits.add(new Hit(when, dimension, container, pos, List.of(lines).subList(1, lines.length)));
				} catch (DateTimeParseException ignored) {
				}
			}
		}

		hits.sort(Comparator.comparing(Hit::when).reversed());
		return hits;
	}

	public static String dimensionId(String folder) {
		return switch (folder) {
			case "Overworld" -> "minecraft:overworld";
			case "The Nether" -> "minecraft:the_nether";
			case "The End" -> "minecraft:the_end";
			default -> null;
		};
	}
}
