package com.wailsvlr.prism.mixin;

import com.wailsvlr.prism.modules.Freecam;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Freecam and Zoom (26.1.2 build): no first-person hand in front of the free camera or while zoomed in. */
@Mixin(GameRenderer.class)
public class GameRendererMixin {
	@Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
	private void prism$freecamHideHand(CameraRenderState camera, float partialTick, Matrix4fc modelView, CallbackInfo ci) {
		if (Freecam.active() != null || com.wailsvlr.prism.modules.Zoom.hidesHand()) ci.cancel();
	}
}
