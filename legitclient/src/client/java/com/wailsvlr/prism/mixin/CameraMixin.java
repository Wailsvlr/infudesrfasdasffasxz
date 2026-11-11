package com.wailsvlr.prism.mixin;

import com.wailsvlr.prism.modules.Freecam;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Freecam: after vanilla places the camera on the player, move it to the freecam position and rotation. */
@Mixin(Camera.class)
public abstract class CameraMixin {
	@Shadow private boolean detached;

	@Shadow protected abstract void setRotation(float yRot, float xRot);

	@Shadow protected abstract void setPosition(Vec3 pos);

	@Inject(method = "setup", at = @At("TAIL"))
	private void prism$freecam(Level level, Entity entity, boolean detachedIn, boolean mirrored, float partialTick, CallbackInfo ci) {
		Freecam fc = Freecam.active();
		if (fc == null) return;
		// "detached" makes the game draw your own player, like third person
		this.detached = true;
		this.setRotation(fc.yaw(), fc.pitch());
		this.setPosition(fc.position(partialTick));
	}
}
