package net.feathermc.client.mixin;

import net.feathermc.client.FeatherClient;
import net.feathermc.client.event.events.RenderHudEvent;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.DeltaTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public class MixinGui {
    @Inject(method = "render", at = @At("TAIL"))
    private void onRenderHud(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (FeatherClient.getInstance() != null) {
            FeatherClient.getInstance().getEventBus().post(
                    new RenderHudEvent(graphics, deltaTracker.getGameTimeDeltaPartialTick(true))
            );
        }
    }
}
