package com.staffsee.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class TimeFormat {
	private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yy  HH:mm:ss");

	private TimeFormat() {
	}

	public static String now() {
		return String.format("[%s]", FORMATTER.format(LocalDateTime.now()));
	}
}
