package com.staffsee.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.staffsee.StaffSeeMod;
import com.staffsee.moderation.VanishManager;
import com.staffsee.permission.StaffPermissions;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.commands.ListPlayersCommand;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

@Mixin(ListPlayersCommand.class)
public abstract class ListPlayersCommandMixin {
	@WrapOperation(method = "format", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/players/PlayerList;getPlayers()Ljava/util/List;"))
	private static List<ServerPlayer> staffsee$hideVanishedFromList(PlayerList playerList, Operation<List<ServerPlayer>> original,
		@Local(argsOnly = true) CommandSourceStack source) {
		List<ServerPlayer> players = original.call(playerList);
		VanishManager vanish = StaffSeeMod.getInstance().getVanishManager();
		if (vanish == null || StaffPermissions.canSeeVanished(source)) {
			return players;
		}
		return players.stream().filter(player -> player == source.getEntity() || !vanish.isVanished(player.getUUID())).toList();
	}
}
