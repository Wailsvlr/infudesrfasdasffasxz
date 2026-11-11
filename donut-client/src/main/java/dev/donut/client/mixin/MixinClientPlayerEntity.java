package dev.donut.client.mixin;

import dev.donut.client.DonutClient;
import dev.donut.client.event.events.TickEvent;
import dev.donut.client.module.combat.KeepSprint;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerEntity.class)
public abstract class MixinClientPlayerEntity {

    @Inject(method = "tick", at = @At("HEAD"))
    private void onTick(CallbackInfo ci) {
        if (DonutClient.INSTANCE != null) {
            DonutClient.INSTANCE.getEventBus().post(TickEvent.INSTANCE);
        }
    }

    @Inject(method = "attack", at = @At("RETURN"))
    private void onAttack(CallbackInfo ci) {
        if (DonutClient.INSTANCE == null) return;
        KeepSprint ks = DonutClient.INSTANCE.getModules().get(KeepSprint.class);
        if (ks != null && ks.isEnabled()) {
            ClientPlayerEntity self = (ClientPlayerEntity)(Object)this;
            if (self.isSprinting()) {
                self.setSprinting(true);
                double slow = ks.getSlowdown();
                self.setVelocity(self.getVelocity().multiply(slow, 1.0, slow));
            }
        }
    }
}
