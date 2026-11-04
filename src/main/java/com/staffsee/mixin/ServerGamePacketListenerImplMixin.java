package com.staffsee.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.staffsee.StaffSeeMod;
import com.staffsee.moderation.VanishManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin {
	@Shadow
	public ServerPlayer player;

	@WrapOperation(method = "removePlayerFromWorld", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/players/PlayerList;broadcastSystemMessage(Lnet/minecraft/network/chat/Component;Z)V"))
	private void staffsee$quietLeave(PlayerList playerList, Component message, boolean overlay, Operation<Void> original) {
		StaffSeeMod mod = StaffSeeMod.getInstance();
		VanishManager vanish = mod.getVanishManager();
		if (vanish != null && vanish.isVanished(player.getUUID())) {
			vanish.sendToViewers(mod.getServer(), player, message);
			return;
		}
		original.call(playerList, message, overlay);
	}
}
