package com.staffsee.storage;

public enum LogType {
	WARN("warns.txt"),
	MUTE("mutes.txt"),
	KICK("kicks.txt"),
	UNMUTE("mutes.txt"),
	FREEZE("freezes.txt"),
	UNFREEZE("freezes.txt"),
	BAN("bans.txt"),
	UNBAN("bans.txt");

	private final String fileName;

	LogType(String fileName) {
		this.fileName = fileName;
	}

	public String getFileName() {
		return fileName;
	}
}
