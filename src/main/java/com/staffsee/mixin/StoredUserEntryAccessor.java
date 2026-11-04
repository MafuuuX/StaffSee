package com.staffsee.mixin;

import net.minecraft.server.ServerConfigEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

// getUser() is package-private before 1.21.9
@Mixin(ServerConfigEntry.class)
public interface StoredUserEntryAccessor {
	@Invoker("getKey")
	Object staffsee$getUser();
}
