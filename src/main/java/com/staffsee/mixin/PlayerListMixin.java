package com.staffsee.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.staffsee.StaffSeeMod;
import com.staffsee.moderation.VanishManager;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Collection;

@Mixin(PlayerList.class)
public abstract class PlayerListMixin {
	@Shadow
	@Final
	private MinecraftServer server;

	@WrapOperation(method = "placeNewPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/players/PlayerList;broadcastSystemMessage(Lnet/minecraft/network/chat/Component;Z)V"))
	private void staffsee$quietJoin(PlayerList playerList, Component message, boolean overlay, Operation<Void> original,
		Connection connection, ServerPlayer player, CommonListenerCookie cookie) {
		VanishManager vanish = StaffSeeMod.getInstance().getVanishManager();
		if (vanish != null && vanish.isVanished(player.getUUID())) {
			vanish.sendToViewers(server, player, message);
			return;
		}
		original.call(playerList, message, overlay);
	}

	@WrapOperation(method = "placeNewPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/game/ClientboundPlayerInfoUpdatePacket;createPlayerInitializing(Ljava/util/Collection;)Lnet/minecraft/network/protocol/game/ClientboundPlayerInfoUpdatePacket;", ordinal = 0))
	private ClientboundPlayerInfoUpdatePacket staffsee$hideVanishedFromTab(Collection<ServerPlayer> players, Operation<ClientboundPlayerInfoUpdatePacket> original,
		Connection connection, ServerPlayer player, CommonListenerCookie cookie) {
		VanishManager vanish = StaffSeeMod.getInstance().getVanishManager();
		if (vanish == null) {
			return original.call(players);
		}
		return original.call(players.stream().filter(other -> vanish.canSee(player, other)).toList());
	}

	@WrapOperation(method = "placeNewPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/players/PlayerList;broadcastAll(Lnet/minecraft/network/protocol/Packet;)V"))
	private void staffsee$announceToViewers(PlayerList playerList, Packet<?> packet, Operation<Void> original,
		Connection connection, ServerPlayer player, CommonListenerCookie cookie) {
		VanishManager vanish = StaffSeeMod.getInstance().getVanishManager();
		if (vanish == null || !vanish.isVanished(player.getUUID())) {
			original.call(playerList, packet);
			return;
		}
		for (ServerPlayer viewer : playerList.getPlayers()) {
			if (vanish.canSee(viewer, player)) {
				viewer.connection.send(packet);
			}
		}
	}
}
