package com.staffsee.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.staffsee.StaffSeeMod;
import com.staffsee.moderation.StaffChat;
import com.staffsee.moderation.VanishManager;
import com.staffsee.permission.StaffPermissions;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.io.IOException;

final class StaffToolsCommands {
	private StaffToolsCommands() {
	}

	static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
		dispatcher.register(CommandManager.literal("vanish")
			.requires(StaffPermissions::canVanish)
			.executes(StaffToolsCommands::vanish));

		dispatcher.register(CommandManager.literal("sc")
			.requires(StaffPermissions::canStaffChat)
			.executes(StaffToolsCommands::toggleStaffChat)
			.then(CommandManager.argument("message", StringArgumentType.greedyString())
				.executes(StaffToolsCommands::staffChat)));
	}

	private static int vanish(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
		ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
		StaffSeeMod mod = StaffSeeMod.getInstance();
		VanishManager vanish = mod.getVanishManager();

		boolean nowVanished = !vanish.isVanished(player.getUuid());
		vanish.setVanished(player, nowVanished);
		try {
			vanish.save();
		} catch (IOException e) {
			StaffSeeMod.LOGGER.error("Failed to save vanish state.", e);
		}

		player.sendMessage(Text.literal(nowVanished ? "You are now vanished." : "You are visible again.")
			.formatted(nowVanished ? Formatting.GREEN : Formatting.YELLOW));
		mod.notifyStaff(Text.literal(player.getGameProfile().name() + (nowVanished ? " vanished." : " is visible again."))
			.formatted(Formatting.GRAY), player);
		return 1;
	}

	private static int toggleStaffChat(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
		ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
		boolean on = StaffSeeMod.getInstance().getStaffChat().toggle(player.getUuid());
		player.sendMessage(Text.literal(on ? "Staff chat on. Your messages now go to staff only." : "Staff chat off.")
			.formatted(Formatting.AQUA));
		return 1;
	}

	private static int staffChat(CommandContext<ServerCommandSource> context) {
		ServerCommandSource source = context.getSource();
		StaffSeeMod.getInstance().sendStaffChat(source.getDisplayName().getString(), StringArgumentType.getString(context, "message"));
		return 1;
	}
}
