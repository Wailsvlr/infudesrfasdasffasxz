package com.wailsvlr.prism.mixin;

import com.wailsvlr.prism.modules.FastBreak;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public class PlayerDigSpeedMixin {
    @Inject(method = "getDigSpeed", at = @At("RETURN"), cancellable = true)
    private void prism$fastBreak(BlockState state, BlockPos pos, CallbackInfoReturnable<Float> cir) {
        if (FastBreak.isActive() && (Object) this == Minecraft.getInstance().player) {
            cir.setReturnValue(cir.getReturnValue() * FastBreak.getMultiplier());
        }
    }
}
