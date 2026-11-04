package com.staffsee.integration;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.staffsee.StaffSeeMod;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public final class DiscordWebhook {
	private static final HttpClient CLIENT = HttpClient.newBuilder()
		.version(HttpClient.Version.HTTP_1_1)
		.connectTimeout(Duration.ofSeconds(10))
		.build();

	private DiscordWebhook() {
	}

	public static void send(String url, String message) {
		if (url == null || url.isBlank()) {
			return;
		}

		URI uri;
		try {
			uri = URI.create(url.trim());
		} catch (IllegalArgumentException e) {
			StaffSeeMod.LOGGER.warn("discordWebhookUrl is not a valid URL.");
			return;
		}

		JsonObject body = new JsonObject();
		body.addProperty("username", "StaffSee");
		body.addProperty("content", message.length() > 1900 ? message.substring(0, 1900) + "..." : message);
		// never ping @everyone or roles because a player typed it into a report
		JsonObject mentions = new JsonObject();
		mentions.add("parse", new JsonArray());
		body.add("allowed_mentions", mentions);

		HttpRequest request = HttpRequest.newBuilder(uri)
			.timeout(Duration.ofSeconds(10))
			.header("Content-Type", "application/json")
			.POST(HttpRequest.BodyPublishers.ofString(body.toString()))
			.build();

		CLIENT.sendAsync(request, HttpResponse.BodyHandlers.discarding()).whenComplete((response, error) -> {
			if (error != null) {
				StaffSeeMod.LOGGER.warn("Discord webhook failed: {}", error.getMessage());
			} else if (response.statusCode() >= 300) {
				StaffSeeMod.LOGGER.warn("Discord webhook returned HTTP {}", response.statusCode());
			}
		});
	}

	public static String escape(String text) {
		return text.replaceAll("([*_~`|>\\\\])", "\\\\$1");
	}
}
