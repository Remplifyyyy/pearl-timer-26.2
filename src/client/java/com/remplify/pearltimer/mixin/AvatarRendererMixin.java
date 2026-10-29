package com.remplify.pearltimer.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.remplify.pearltimer.PearlTimerClient;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Draws the pearl countdown one line above the player's name tag. */
@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin {
	/** Height of one name tag line in blocks (same spacing vanilla uses between the name and the score line). */
	@Unique
	private static final float PEARLTIMER$LINE = 9.0F * 1.15F * 0.025F;

	@Inject(
			method = "submitNameDisplay(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
			at = @At("TAIL")
	)
	private void pearltimer$submitTimer(AvatarRenderState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo ci) {
		if (state.distanceToCameraSq > 4096.0) return; // 64 blocks, same as vanilla name tags

		Component label = PearlTimerClient.getLabel(state.id);
		if (label == null) return;

		Vec3 anchor = (state.nameTag != null && state.nameTagAttachment != null)
				? state.nameTagAttachment
				: new Vec3(0.0, state.boundingBoxHeight, 0.0);

		int lines = 0;
		if (state.nameTag != null) lines++;
		if (state.scoreText != null) lines++;

		int offset = state.showExtraEars ? -10 : 0;

		pose.pushPose();
		pose.translate(0.0F, lines * PEARLTIMER$LINE, 0.0F);
		collector.submitNameTag(pose, anchor, offset, label, !state.isDiscrete, state.lightCoords, camera);
		pose.popPose();
	}
}
