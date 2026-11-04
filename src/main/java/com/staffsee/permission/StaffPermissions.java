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
}
