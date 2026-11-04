package com.staffsee.moderation;

public final class FreezeEntry {
	public String playerName;
	public String frozenBy;
	public String reason;
	public double x;
	public double y;
	public double z;
	public float yaw;
	public float pitch;
	public String dimension;

	public FreezeEntry() {
	}

	public FreezeEntry(String playerName, String frozenBy, String reason, double x, double y, double z, float yaw, float pitch, String dimension) {
		this.playerName = playerName;
		this.frozenBy = frozenBy;
		this.reason = reason;
		this.x = x;
		this.y = y;
		this.z = z;
		this.yaw = yaw;
		this.pitch = pitch;
		this.dimension = dimension;
	}
}
