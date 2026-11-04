package com.staffsee.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.staffsee.StaffSeeMod;
import com.staffsee.moderation.VanishManager;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ServerPlayNetworkHandler.class)
public abstract class ServerGamePacketListenerImplMixin {
	@Shadow
	public ServerPlayerEntity player;

	@WrapOperation(method = "cleanUp", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/PlayerManager;broadcast(Lnet/minecraft/text/Text;Z)V"))
	private void staffsee$quietLeave(PlayerManager playerList, Text message, boolean overlay, Operation<Void> original) {
		StaffSeeMod mod = StaffSeeMod.getInstance();
		VanishManager vanish = mod.getVanishManager();
		if (vanish != null && vanish.isVanished(player.getUuid())) {
			vanish.sendToViewers(mod.getServer(), player, message);
			return;
		}
		original.call(playerList, message, overlay);
	}
}
