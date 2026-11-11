package dev.donut.client.mixin;

import dev.donut.client.DonutClient;
import dev.donut.client.event.events.RenderEvent;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public abstract class MixinInGameHud {

    @Inject(method = "render", at = @At("TAIL"))
    private void onHudRender(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (DonutClient.INSTANCE != null) {
            float tickDelta = tickCounter.getTickDelta(true);
            DonutClient.INSTANCE.getEventBus().post(new RenderEvent.Hud(context, tickDelta));
        }
    }
}
