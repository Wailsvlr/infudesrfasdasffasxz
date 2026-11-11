package com.wailsvlr.prism.mixin;

import com.wailsvlr.prism.modules.Freecam;
import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Freecam (26.1.2 build), and Zoom. Freecam: right after vanilla puts the camera on the player (alignWithEntity, before the cull
 * frustum is built from the camera position), move it to the freecam position and rotation.
 */
@Mixin(Camera.class)
public abstract class CameraMixin {
	@Shadow private boolean detached;

	@Shadow protected abstract void setRotation(float yRot, float xRot);

	@Shadow protected abstract void setPosition(Vec3 pos);

	@Inject(method = "alignWithEntity", at = @At("TAIL"))
	private void prism$freecam(float partialTick, CallbackInfo ci) {
		Freecam fc = Freecam.active();
		if (fc == null) return;
		// "detached" makes the game draw your own player, like third person
		this.detached = true;
		this.setRotation(fc.yaw(), fc.pitch());
		this.setPosition(fc.position(partialTick));
	}

	/** Zoom (26.1.2 build): the world's field of view is worked out here now (GameRenderer.getFov in 1.21.11). */
	@Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)
	private void prism$zoom(float partialTick, org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Float> cir) {
		float k = com.wailsvlr.prism.modules.Zoom.factor();
		if (k != 1f) cir.setReturnValue(cir.getReturnValue() / k);
	}
}
