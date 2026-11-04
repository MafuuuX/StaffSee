package com.staffsee.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.staffsee.StaffSeeMod;
import com.staffsee.moderation.StaffChat;
import com.staffsee.moderation.VanishManager;
import com.staffsee.permission.StaffPermissions;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;

final class StaffToolsCommands {
	private StaffToolsCommands() {
	}

	static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("vanish")
			.requires(StaffPermissions::canVanish)
			.executes(StaffToolsCommands::vanish));

		dispatcher.register(Commands.literal("sc")
			.requires(StaffPermissions::canStaffChat)
			.executes(StaffToolsCommands::toggleStaffChat)
			.then(Commands.argument("message", StringArgumentType.greedyString())
				.executes(StaffToolsCommands::staffChat)));
	}

	private static int vanish(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		StaffSeeMod mod = StaffSeeMod.getInstance();
		VanishManager vanish = mod.getVanishManager();

		boolean nowVanished = !vanish.isVanished(player.getUUID());
		vanish.setVanished(player, nowVanished);
		try {
			vanish.save();
		} catch (IOException e) {
			StaffSeeMod.LOGGER.error("Failed to save vanish state.", e);
		}

		player.sendSystemMessage(Component.literal(nowVanished ? "You are now vanished." : "You are visible again.")
			.withStyle(nowVanished ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
		mod.notifyStaff(Component.literal(player.getGameProfile().name() + (nowVanished ? " vanished." : " is visible again."))
			.withStyle(ChatFormatting.GRAY), player);
		return 1;
	}

	private static int toggleStaffChat(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		boolean on = StaffSeeMod.getInstance().getStaffChat().toggle(player.getUUID());
		player.sendSystemMessage(Component.literal(on ? "Staff chat on. Your messages now go to staff only." : "Staff chat off.")
			.withStyle(ChatFormatting.AQUA));
		return 1;
	}

	private static int staffChat(CommandContext<CommandSourceStack> context) {
		CommandSourceStack source = context.getSource();
		StaffSeeMod.getInstance().sendStaffChat(source.getDisplayName().getString(), StringArgumentType.getString(context, "message"));
		return 1;
	}
}
