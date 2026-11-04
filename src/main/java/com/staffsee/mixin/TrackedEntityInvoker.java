package com.staffsee.mixin;

import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(targets = "net.minecraft.server.world.ServerChunkLoadingManager$EntityTracker")
public interface TrackedEntityInvoker {
	@Invoker("updateTrackedStatus")
	void staffsee$updatePlayer(ServerPlayerEntity player);
}
