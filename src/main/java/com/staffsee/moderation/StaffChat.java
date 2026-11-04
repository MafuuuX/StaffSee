package com.staffsee.moderation;

import com.staffsee.permission.StaffPermissions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class StaffChat {
	private final Set<UUID> toggled = new HashSet<>();

	public boolean toggle(UUID playerId) {
		if (toggled.remove(playerId)) {
			return false;
		}
		toggled.add(playerId);
		return true;
	}

	public boolean isToggled(UUID playerId) {
		return toggled.contains(playerId);
	}

	public void remove(UUID playerId) {
		toggled.remove(playerId);
	}

	public void send(MinecraftServer server, String senderName, String message) {
		Text line = Text.literal("[Staff] ").formatted(Formatting.AQUA)
			.append(Text.literal(senderName).formatted(Formatting.YELLOW))
			.append(Text.literal(": " + message).formatted(Formatting.WHITE));
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			if (StaffPermissions.canStaffChat(player.getCommandSource())) {
				player.sendMessage(line);
			}
		}
		server.sendMessage(line);
	}
}
