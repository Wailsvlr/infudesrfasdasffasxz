package com.wailsvlr.prism.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.wailsvlr.prism.modules.Freecam;
import com.wailsvlr.prism.render.Projection;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Captures the exact view and projection matrices of each frame so labels line up with the blocks. */
@Mixin(LevelRenderer.class)
public class LevelRendererMixin {
	@Inject(method = "renderLevel", at = @At("HEAD"))
	private void prism$captureMatrices(GraphicsResourceAllocator allocator, DeltaTracker delta, boolean renderOutline, Camera camera,
									   Matrix4f view, Matrix4f projection, Matrix4f cullProjection, GpuBufferSlice fog, Vector4f fogColor,
									   boolean sky, CallbackInfo ci) {
		Projection.capture(view, projection, camera.position());
	}

	/** Freecam: cull terrain like a spectator, so flying into solid blocks does not hide the world. */
	@ModifyArg(method = "renderLevel", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/renderer/LevelRenderer;cullTerrain(Lnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/culling/Frustum;Z)V"), index = 2)
	private boolean prism$freecamSpectatorCulling(boolean spectator) {
		return spectator || Freecam.active() != null;
	}
}
