package com.staffsee.moderation;

import com.staffsee.permission.StaffPermissions;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

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
		Component line = Component.literal("[Staff] ").withStyle(ChatFormatting.AQUA)
			.append(Component.literal(senderName).withStyle(ChatFormatting.YELLOW))
			.append(Component.literal(": " + message).withStyle(ChatFormatting.WHITE));
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (StaffPermissions.canStaffChat(player.createCommandSourceStack())) {
				player.sendSystemMessage(line);
			}
		}
		server.sendSystemMessage(line);
	}
}
