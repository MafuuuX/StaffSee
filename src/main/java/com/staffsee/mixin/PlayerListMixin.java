package com.staffsee.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.staffsee.StaffSeeMod;
import com.staffsee.moderation.VanishManager;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Collection;

@Mixin(PlayerManager.class)
public abstract class PlayerListMixin {
	@Shadow
	@Final
	private MinecraftServer server;

	@WrapOperation(method = "onPlayerConnect", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/PlayerManager;broadcast(Lnet/minecraft/text/Text;Z)V"))
	private void staffsee$quietJoin(PlayerManager playerList, Text message, boolean overlay, Operation<Void> original,
		ClientConnection connection, ServerPlayerEntity player, ConnectedClientData cookie) {
		VanishManager vanish = StaffSeeMod.getInstance().getVanishManager();
		if (vanish != null && vanish.isVanished(player.getUuid())) {
			vanish.sendToViewers(server, player, message);
			return;
		}
		original.call(playerList, message, overlay);
	}

	@WrapOperation(method = "onPlayerConnect", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/packet/s2c/play/PlayerListS2CPacket;entryFromPlayer(Ljava/util/Collection;)Lnet/minecraft/network/packet/s2c/play/PlayerListS2CPacket;", ordinal = 0))
	private PlayerListS2CPacket staffsee$hideVanishedFromTab(Collection<ServerPlayerEntity> players, Operation<PlayerListS2CPacket> original,
		ClientConnection connection, ServerPlayerEntity player, ConnectedClientData cookie) {
		VanishManager vanish = StaffSeeMod.getInstance().getVanishManager();
		if (vanish == null) {
			return original.call(players);
		}
		return original.call(players.stream().filter(other -> vanish.canSee(player, other)).toList());
	}

	@WrapOperation(method = "onPlayerConnect", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/PlayerManager;sendToAll(Lnet/minecraft/network/packet/Packet;)V"))
	private void staffsee$announceToViewers(PlayerManager playerList, Packet<?> packet, Operation<Void> original,
		ClientConnection connection, ServerPlayerEntity player, ConnectedClientData cookie) {
		VanishManager vanish = StaffSeeMod.getInstance().getVanishManager();
		if (vanish == null || !vanish.isVanished(player.getUuid())) {
			original.call(playerList, packet);
			return;
		}
		for (ServerPlayerEntity viewer : playerList.getPlayerList()) {
			if (vanish.canSee(viewer, player)) {
				viewer.networkHandler.sendPacket(packet);
			}
		}
	}
}
