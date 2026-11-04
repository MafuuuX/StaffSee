package com.staffsee.mixin;

import com.staffsee.StaffSeeMod;
import com.staffsee.moderation.VanishManager;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerMixin {
	@Inject(method = "canBeSpectated", at = @At("HEAD"), cancellable = true)
	private void staffsee$hideVanished(ServerPlayerEntity viewer, CallbackInfoReturnable<Boolean> cir) {
		VanishManager vanish = StaffSeeMod.getInstance().getVanishManager();
		if (vanish != null && !vanish.canSee(viewer, (ServerPlayerEntity) (Object) this)) {
			cir.setReturnValue(false);
		}
	}
}
