package net.feathermc.client.mixin;

import net.feathermc.client.FeatherClient;
import net.feathermc.client.module.render.Fullbright;
import net.minecraft.client.renderer.LightTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LightTexture.class)
public class MixinLightTexture {
    @Inject(method = "getBrightness", at = @At("RETURN"), cancellable = true)
    private static void onGetBrightness(net.minecraft.world.level.dimension.DimensionType type, int lightLevel, CallbackInfoReturnable<Float> cir) {
        if (FeatherClient.getInstance() == null) return;
        Fullbright fb = FeatherClient.getInstance().getModuleManager().getModule(Fullbright.class);
        if (fb != null && fb.isEnabled()) {
            cir.setReturnValue(1.0f);
        }
    }
}
