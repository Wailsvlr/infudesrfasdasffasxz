package com.wailsvlr.prism.mixin;

import com.wailsvlr.prism.modules.Timer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.client.DeltaTracker$Timer")
public class TimerMixin {
    @Shadow private float deltaTicks;
    @Shadow private float realtimeDeltaTicks;

    @Inject(method = "advanceTime", at = @At("RETURN"), cancellable = true)
    private void prism$timer(long time, boolean paused, CallbackInfoReturnable<Integer> cir) {
        float mult = Timer.getMultiplier();
        if (mult != 1.0f) {
            this.deltaTicks *= mult;
            this.realtimeDeltaTicks *= mult;
            int ticks = Math.max(1, Math.round(cir.getReturnValue() * mult));
            cir.setReturnValue(ticks);
        }
    }
}
