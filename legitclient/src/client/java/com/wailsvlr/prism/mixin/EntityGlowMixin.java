package com.wailsvlr.prism.mixin;

import com.wailsvlr.prism.modules.Chams;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public class EntityGlowMixin {
    @Inject(method = "isCurrentlyGlowing", at = @At("RETURN"), cancellable = true)
    private void prism$chams(CallbackInfoReturnable<Boolean> cir) {
        if (!Chams.isActive()) return;
        Entity self = (Entity) (Object) this;
        if (self == Minecraft.getInstance().player) return;
        if (!(self instanceof LivingEntity le) || !le.isAlive()) return;

        if (self instanceof Player && Chams.showPlayers()) cir.setReturnValue(true);
        else if (self instanceof Monster && Chams.showMobs()) cir.setReturnValue(true);
    }
}
