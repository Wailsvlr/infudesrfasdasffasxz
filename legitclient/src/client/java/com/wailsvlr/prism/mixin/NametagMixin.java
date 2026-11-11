package com.wailsvlr.prism.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.wailsvlr.prism.modules.Nametags;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public class NametagMixin {
    @Inject(method = "submitNameTag", at = @At("HEAD"))
    private void prism$scaleNametag(EntityRenderState state, PoseStack poseStack,
                                     SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo ci) {
        if (!Nametags.isActive()) return;
        float scale = Nametags.getScale();
        poseStack.pushPose();
        poseStack.scale(scale, scale, scale);
    }

    @Inject(method = "submitNameTag", at = @At("RETURN"))
    private void prism$restoreNametag(EntityRenderState state, PoseStack poseStack,
                                       SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo ci) {
        if (!Nametags.isActive()) return;
        poseStack.popPose();
    }
}
