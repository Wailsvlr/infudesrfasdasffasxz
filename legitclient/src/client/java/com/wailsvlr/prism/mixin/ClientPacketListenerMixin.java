package com.wailsvlr.prism.mixin;

import com.wailsvlr.prism.modules.Velocity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {
	@Inject(method = "handleSetEntityMotion", at = @At("TAIL"))
	private void prism$velocity(ClientboundSetEntityMotionPacket packet, CallbackInfo ci) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null && packet.getId() == mc.player.getId()) mc.player.setDeltaMovement(Velocity.scale(packet.getMovement()));
	}
}
