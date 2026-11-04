package com.staffsee.permission;

import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.server.command.ServerCommandSource;

public final class StaffPermissions {
	private StaffPermissions() {
	}

	public static boolean canModerate(ServerCommandSource source) {
		return Permissions.check(source, "staffsee.moderate", 2);
	}

	public static boolean canReport(ServerCommandSource source) {
		return Permissions.check(source, "staffsee.report", true);
	}

	// the following nodes default to whatever staffsee.moderate says
	public static boolean canVanish(ServerCommandSource source) {
		return Permissions.check(source, "staffsee.vanish", canModerate(source));
	}

	public static boolean canSeeVanished(ServerCommandSource source) {
		return Permissions.check(source, "staffsee.vanish.see", canModerate(source));
	}

	public static boolean canStaffChat(ServerCommandSource source) {
		return Permissions.check(source, "staffsee.staffchat", canModerate(source));
	}
}
