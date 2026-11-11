package com.wailsvlr.prism.mixin;

import com.wailsvlr.prism.modules.Freecam;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Freecam: no first-person hand floating in front of the free camera. */
@Mixin(GameRenderer.class)
public class GameRendererMixin {
	@Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
	private void prism$freecamHideHand(float partialTick, boolean sleeping, Matrix4f projection, CallbackInfo ci) {
		if (Freecam.active() != null || com.wailsvlr.prism.modules.Zoom.hidesHand()) ci.cancel();
	}

	/** Zoom: a narrower field of view for the world (the hand keeps its own). */
	@Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
	private void prism$zoom(net.minecraft.client.Camera camera, float partialTick, boolean useFovSetting,
							org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Float> cir) {
		if (!useFovSetting) return;
		float k = com.wailsvlr.prism.modules.Zoom.factor();
		if (k != 1f) cir.setReturnValue(cir.getReturnValue() / k);
	}
}
