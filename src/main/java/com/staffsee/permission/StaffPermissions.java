package com.staffsee.permission;

import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.commands.CommandSourceStack;

public final class StaffPermissions {
	private StaffPermissions() {
	}

	public static boolean canModerate(CommandSourceStack source) {
		return Permissions.check(source, "staffsee.moderate", 2);
	}

	public static boolean canReport(CommandSourceStack source) {
		return Permissions.check(source, "staffsee.report", true);
	}
}
