package com.wailsvlr.prism.mixin;

import com.wailsvlr.prism.PrismClient;
import com.wailsvlr.prism.modules.Fullbright;
import net.minecraft.client.renderer.LightTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Fullbright: the lightmap parameters are written in order (ambient light, sky factor, block flicker, night vision,
 * darkness, darken world, gamma, sky colour, ambient colour); Fullbright raises some of them.
 */
@Mixin(LightTexture.class)
public abstract class LightTextureMixin {
	@ModifyArg(method = "updateLightTexture", index = 0,
		at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/buffers/Std140Builder;putFloat(F)Lcom/mojang/blaze3d/buffers/Std140Builder;", ordinal = 0))
	private float prism$ambient(float vanilla) {
		return PrismClient.modules().get(Fullbright.class).ambient(vanilla);
	}

	@ModifyArg(method = "updateLightTexture", index = 0,
		at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/buffers/Std140Builder;putVec3(Lorg/joml/Vector3fc;)Lcom/mojang/blaze3d/buffers/Std140Builder;", ordinal = 1))
	private org.joml.Vector3fc prism$ambientColor(org.joml.Vector3fc vanilla) {
		return PrismClient.modules().get(Fullbright.class).ambientColor(vanilla);
	}

	@ModifyArg(method = "updateLightTexture", index = 0,
		at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/buffers/Std140Builder;putFloat(F)Lcom/mojang/blaze3d/buffers/Std140Builder;", ordinal = 3))
	private float prism$nightVision(float vanilla) {
		return PrismClient.modules().get(Fullbright.class).nightVision(vanilla);
	}

	@ModifyArg(method = "updateLightTexture", index = 0,
		at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/buffers/Std140Builder;putFloat(F)Lcom/mojang/blaze3d/buffers/Std140Builder;", ordinal = 6))
	private float prism$gamma(float vanilla) {
		return PrismClient.modules().get(Fullbright.class).gamma(vanilla);
	}
}
