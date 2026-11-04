package com.staffsee.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.staffsee.StaffSeeMod;
import com.staffsee.moderation.VanishManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {
	// player count and sample in the server list
	@WrapOperation(method = "createMetadataPlayers", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/PlayerManager;getPlayerList()Ljava/util/List;"))
	private List<ServerPlayerEntity> staffsee$hideVanishedFromStatus(PlayerManager playerList, Operation<List<ServerPlayerEntity>> original) {
		List<ServerPlayerEntity> players = original.call(playerList);
		VanishManager vanish = StaffSeeMod.getInstance().getVanishManager();
		if (vanish == null) {
			return players;
		}
		return players.stream().filter(player -> !vanish.isVanished(player.getUuid())).toList();
	}
}
