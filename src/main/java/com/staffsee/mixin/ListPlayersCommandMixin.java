package com.staffsee.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.staffsee.StaffSeeMod;
import com.staffsee.moderation.VanishManager;
import com.staffsee.permission.StaffPermissions;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.command.ListCommand;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

@Mixin(ListCommand.class)
public abstract class ListPlayersCommandMixin {
	@WrapOperation(method = "execute", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/PlayerManager;getPlayerList()Ljava/util/List;"))
	private static List<ServerPlayerEntity> staffsee$hideVanishedFromList(PlayerManager playerList, Operation<List<ServerPlayerEntity>> original,
		@Local(argsOnly = true) ServerCommandSource source) {
		List<ServerPlayerEntity> players = original.call(playerList);
		VanishManager vanish = StaffSeeMod.getInstance().getVanishManager();
		if (vanish == null || StaffPermissions.canSeeVanished(source)) {
			return players;
		}
		return players.stream().filter(player -> player == source.getEntity() || !vanish.isVanished(player.getUuid())).toList();
	}
}
