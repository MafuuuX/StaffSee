package com.staffsee.moderation;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DurationParser {
	private static final Pattern DURATION_PATTERN = Pattern.compile("^(\\d+)([mhd])$", Pattern.CASE_INSENSITIVE);

	private DurationParser() {
	}

	public static ParsedDuration parse(String input) {
		if (input == null || input.isBlank()) {
			return ParsedDuration.permanent();
		}

		String normalized = input.trim().toLowerCase(Locale.ROOT);
		if (normalized.equals("perm") || normalized.equals("permanent")) {
			return ParsedDuration.permanent();
		}

		Matcher matcher = DURATION_PATTERN.matcher(normalized);
		if (!matcher.matches()) {
			throw new IllegalArgumentException("Invalid duration. Use 30m, 2h, 1d, or perm.");
		}

		int amount = Integer.parseInt(matcher.group(1));
		if (amount <= 0) {
			throw new IllegalArgumentException("Duration must be greater than zero.");
		}

		String unit = matcher.group(2);
		long millis = switch (unit) {
			case "m" -> amount * 60_000L;
			case "h" -> amount * 3_600_000L;
			case "d" -> amount * 86_400_000L;
			default -> throw new IllegalArgumentException("Invalid duration unit.");
		};

		return ParsedDuration.timed(millis, normalized);
	}

	public record ParsedDuration(boolean isPermanent, long durationMillis, String label) {
		public static ParsedDuration permanent() {
			return new ParsedDuration(true, -1L, "perm");
		}

		public static ParsedDuration timed(long durationMillis, String label) {
			return new ParsedDuration(false, durationMillis, label);
		}

		public long expiresAt(long now) {
			return isPermanent ? -1L : now + durationMillis;
		}
	}
}
