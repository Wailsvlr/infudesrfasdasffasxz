package net.feathermc.client.mixin;

import net.feathermc.client.FeatherClient;
import net.feathermc.client.event.events.MouseScrollEvent;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MixinMouseHandler {
    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void onScroll(long window, double xOffset, double yOffset, CallbackInfo ci) {
        if (FeatherClient.getInstance() != null) {
            MouseScrollEvent event = new MouseScrollEvent(yOffset);
            FeatherClient.getInstance().getEventBus().post(event);
            if (event.isCancelled()) {
                ci.cancel();
            }
        }
    }
}
