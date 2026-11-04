package com.staffsee.mixin;

import com.staffsee.StaffSeeMod;
import com.staffsee.moderation.VanishManager;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {
	@Inject(method = "broadcastToPlayer", at = @At("HEAD"), cancellable = true)
	private void staffsee$hideVanished(ServerPlayer viewer, CallbackInfoReturnable<Boolean> cir) {
		VanishManager vanish = StaffSeeMod.getInstance().getVanishManager();
		if (vanish != null && !vanish.canSee(viewer, (ServerPlayer) (Object) this)) {
			cir.setReturnValue(false);
		}
	}
}
