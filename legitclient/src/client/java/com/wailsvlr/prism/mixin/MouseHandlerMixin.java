package com.wailsvlr.prism.mixin;

import com.wailsvlr.prism.modules.Zoom;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Zoom: a slower mouse while zoomed in, and the wheel changes the zoom while the key is held. */
@Mixin(MouseHandler.class)
public class MouseHandlerMixin {
	@Shadow private double accumulatedDX;
	@Shadow private double accumulatedDY;

	@Inject(method = "turnPlayer", at = @At("HEAD"))
	private void prism$zoomSlowMouse(double movementTime, CallbackInfo ci) {
		float k = Zoom.factor();
		if (k > 1.01f && Zoom.slowsMouse()) {
			accumulatedDX /= k;
			accumulatedDY /= k;
		}
	}

	@Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
	private void prism$zoomWheel(long window, double xOffset, double yOffset, CallbackInfo ci) {
		if (Zoom.scroll(yOffset)) ci.cancel();
	}
}
