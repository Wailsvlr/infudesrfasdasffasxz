package dev.donut.client.mixin;

import dev.donut.client.DonutClient;
import dev.donut.client.event.events.RenderEvent;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class MixinGameRenderer {

    @Inject(method = "renderWorld", at = @At(value = "TAIL"))
    private void onWorldRender(RenderTickCounter tickCounter, CallbackInfo ci) {
        if (DonutClient.INSTANCE != null) {
            MatrixStack matrices = new MatrixStack();
            float tickDelta = tickCounter.getTickDelta(true);
            DonutClient.INSTANCE.getEventBus().post(new RenderEvent.World(matrices, tickDelta));
        }
    }
}
