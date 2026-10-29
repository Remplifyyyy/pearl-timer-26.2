package com.remplify.pearltimer.mixin;

import com.remplify.pearltimer.PearlTimerClient;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundCooldownPacket;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Learns the server's pearl cooldown length from the cooldown packet it sends when you throw a pearl. */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
	@Unique
	private static final Identifier PEARLTIMER$ENDER_PEARL = Identifier.withDefaultNamespace("ender_pearl");

	// TAIL only runs on the game thread (the network-thread call bails out early).
	@Inject(method = "handleItemCooldown", at = @At("TAIL"))
	private void pearltimer$onCooldown(ClientboundCooldownPacket packet, CallbackInfo ci) {
		if (PEARLTIMER$ENDER_PEARL.equals(packet.cooldownGroup())) {
			PearlTimerClient.onOwnPearlCooldown(packet.duration());
		}
	}
}
