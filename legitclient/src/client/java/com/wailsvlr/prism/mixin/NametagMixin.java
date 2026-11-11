package com.wailsvlr.prism.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.wailsvlr.prism.modules.Nametags;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.invocation.InvokeInfo;

@Mixin(EntityRenderer.class)
public class NametagMixin {
    @Inject(method = "renderNameTag", at = @At("HEAD"))
    private void prism$scaleNametag(Entity entity, Component displayName, PoseStack poseStack,
                                     MultiBufferSource buffer, int packedLight, float partialTick, CallbackInfo ci) {
        if (!Nametags.isActive()) return;
        if (!(entity instanceof Player) && !(entity instanceof LivingEntity)) return;
        if (entity == Minecraft.getInstance().player) return;

        float scale = Nametags.getScale();
        poseStack.pushPose();
        poseStack.scale(scale, scale, scale);
    }

    @Inject(method = "renderNameTag", at = @At("RETURN"))
    private void prism$restoreNametag(Entity entity, Component displayName, PoseStack poseStack,
                                       MultiBufferSource buffer, int packedLight, float partialTick, CallbackInfo ci) {
        if (!Nametags.isActive()) return;
        if (!(entity instanceof Player) && !(entity instanceof LivingEntity)) return;
        if (entity == Minecraft.getInstance().player) return;

        poseStack.popPose();
    }
}
