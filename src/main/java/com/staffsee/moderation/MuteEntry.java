package com.staffsee.moderation;

import java.util.UUID;

public final class MuteEntry {
	public String playerName;
	public String mutedBy;
	public String reason;
	public long expiresAt;
	public String durationLabel;

	public MuteEntry() {
	}

	public MuteEntry(String playerName, String mutedBy, String reason, long expiresAt, String durationLabel) {
		this.playerName = playerName;
		this.mutedBy = mutedBy;
		this.reason = reason;
		this.expiresAt = expiresAt;
		this.durationLabel = durationLabel;
	}

	public boolean isExpired(long now) {
		return expiresAt != -1L && now >= expiresAt;
	}

	public boolean isPermanent() {
		return expiresAt == -1L;
	}

	public static MuteEntry permanent(String playerName, String mutedBy, String reason) {
		return new MuteEntry(playerName, mutedBy, reason, -1L, "perm");
	}

	public static MuteEntry timed(String playerName, String mutedBy, String reason, long expiresAt, String durationLabel) {
		return new MuteEntry(playerName, mutedBy, reason, expiresAt, durationLabel);
	}

	public UUID playerIdFromMapKey(String key) {
		return UUID.fromString(key);
	}
}
