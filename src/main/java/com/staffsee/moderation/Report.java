package com.staffsee.moderation;

public final class Report {
	public int id;
	public String reporterName;
	public String reporterUuid;
	public String targetName;
	public String targetUuid;
	public String reason;
	public long timestamp;

	public Report() {
	}

	public Report(int id, String reporterName, String reporterUuid, String targetName, String targetUuid, String reason, long timestamp) {
		this.id = id;
		this.reporterName = reporterName;
		this.reporterUuid = reporterUuid;
		this.targetName = targetName;
		this.targetUuid = targetUuid;
		this.reason = reason;
		this.timestamp = timestamp;
	}
}
