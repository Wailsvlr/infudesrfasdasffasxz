package net.feathermc.client.mixin;

import net.feathermc.client.FeatherClient;
import net.feathermc.client.event.events.TickEvent;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MixinMinecraft {
    @Inject(method = "tick", at = @At("TAIL"))
    private void onTick(CallbackInfo ci) {
        if (FeatherClient.getInstance() != null) {
            FeatherClient.getInstance().getEventBus().post(TickEvent.INSTANCE);
        }
    }
}
