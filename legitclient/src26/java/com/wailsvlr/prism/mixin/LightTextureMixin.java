package com.wailsvlr.prism.mixin;

import com.wailsvlr.prism.PrismClient;
import com.wailsvlr.prism.modules.Fullbright;
import net.minecraft.client.renderer.Lightmap;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fullbright (26.1.2 build): the lightmap is drawn from a render state; Fullbright raises its night vision, gamma and
 * ambient colour just before it is uploaded, and keeps it updating while it is on.
 */
@Mixin(Lightmap.class)
public abstract class LightTextureMixin {
	@Inject(method = "render", at = @At("HEAD"))
	private void prism$fullbright(LightmapRenderState state, CallbackInfo ci) {
		Fullbright fb = PrismClient.modules().get(Fullbright.class);
		if (!fb.isEnabled() && fb.amount() <= 0) return;
		state.needsUpdate = true;
		state.nightVisionEffectIntensity = fb.nightVision(state.nightVisionEffectIntensity);
		state.brightness = fb.gamma(state.brightness);
		state.ambientColor = fb.ambientColor(state.ambientColor);
		state.skyFactor = fb.ambient(state.skyFactor);
	}
}
