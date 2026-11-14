package net.feathermc.client.mixin;

import net.feathermc.client.FeatherClient;
import net.feathermc.client.module.render.Zoom;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.DeltaTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public class MixinGameRenderer {
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void onGetFov(DeltaTracker deltaTracker, boolean useFovSetting, CallbackInfoReturnable<Float> cir) {
        if (FeatherClient.getInstance() == null) return;
        Zoom zoom = FeatherClient.getInstance().getModuleManager().getModule(Zoom.class);
        if (zoom != null && zoom.isEnabled()) {
            cir.setReturnValue((float) zoom.getCurrentFov());
        }
    }
}
