package dev.donut.client.mixin;

import dev.donut.client.DonutClient;
import dev.donut.client.event.events.MovementEvent;
import net.minecraft.client.input.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public abstract class MixinKeyboardInput {

    @Inject(method = "tick", at = @At("TAIL"))
    private void onTick(boolean slowDown, float f, CallbackInfo ci) {
        if (DonutClient.INSTANCE != null) {
            KeyboardInput input = (KeyboardInput)(Object)this;
            MovementEvent.Input event = new MovementEvent.Input(
                input.movementForward, input.movementSideways,
                input.jumping, input.sneaking
            );
            DonutClient.INSTANCE.getEventBus().post(event);
        }
    }
}
