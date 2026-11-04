package com.staffsee.moderation;

import java.util.HashMap;
import java.util.Map;

public final class ReportStore {
	public int nextId = 1;
	public Map<String, Report> reports = new HashMap<>();

	public ReportStore() {
	}
}
