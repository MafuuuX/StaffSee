package com.staffsee.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.staffsee.StaffSeeMod;
import com.staffsee.moderation.VanishManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {
	// player count and sample in the server list
	@WrapOperation(method = "buildPlayerStatus", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/players/PlayerList;getPlayers()Ljava/util/List;"))
	private List<ServerPlayer> staffsee$hideVanishedFromStatus(PlayerList playerList, Operation<List<ServerPlayer>> original) {
		List<ServerPlayer> players = original.call(playerList);
		VanishManager vanish = StaffSeeMod.getInstance().getVanishManager();
		if (vanish == null) {
			return players;
		}
		return players.stream().filter(player -> !vanish.isVanished(player.getUUID())).toList();
	}
}
