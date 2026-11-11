package com.wailsvlr.prism.mixin;

import com.wailsvlr.prism.modules.Blink;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Connection.class)
public class ConnectionMixin {
    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;)V",
            at = @At("HEAD"), cancellable = true)
    private void prism$blink(Packet<?> packet, CallbackInfo ci) {
        if (Blink.shouldCapture(packet)) {
            Blink.capture(packet);
            ci.cancel();
        }
    }
}
