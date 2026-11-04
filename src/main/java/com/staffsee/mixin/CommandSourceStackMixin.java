package com.staffsee.mixin;

import com.staffsee.StaffSeeMod;
import com.staffsee.moderation.VanishManager;
import com.staffsee.permission.StaffPermissions;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;

@Mixin(CommandSourceStack.class)
public abstract class CommandSourceStackMixin {
	@Shadow
	@Final
	private MinecraftServer server;

	// name suggestions in commands
	@Inject(method = "getOnlinePlayerNames", at = @At("RETURN"), cancellable = true)
	private void staffsee$hideVanishedNames(CallbackInfoReturnable<Collection<String>> cir) {
		VanishManager vanish = StaffSeeMod.getInstance().getVanishManager();
		CommandSourceStack source = (CommandSourceStack) (Object) this;
		if (vanish == null || !vanish.anyVanished() || StaffPermissions.canSeeVanished(source)) {
			return;
		}
		Collection<String> names = cir.getReturnValue();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player != source.getEntity() && vanish.isVanished(player.getUUID())) {
				names.remove(player.getGameProfile().name());
			}
		}
		cir.setReturnValue(names);
	}
}
