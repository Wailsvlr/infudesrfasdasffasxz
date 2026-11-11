package dev.donut.client.mixin;

import dev.donut.client.DonutClient;
import dev.donut.client.event.events.PacketEvent;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientConnection.class)
public abstract class MixinClientConnection {

    @Inject(method = "send(Lnet/minecraft/network/packet/Packet;)V", at = @At("HEAD"), cancellable = true)
    private void onSend(Packet<?> packet, CallbackInfo ci) {
        if (DonutClient.INSTANCE == null) return;
        PacketEvent.Send event = new PacketEvent.Send(packet);
        DonutClient.INSTANCE.getEventBus().post(event);
        if (event.isCancelled()) ci.cancel();
    }

    @Inject(method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/packet/Packet;)V", at = @At("HEAD"), cancellable = true)
    private void onReceive(ChannelHandlerContext ctx, Packet<?> packet, CallbackInfo ci) {
        if (DonutClient.INSTANCE == null) return;
        PacketEvent.Receive event = new PacketEvent.Receive(packet);
        DonutClient.INSTANCE.getEventBus().post(event);
        if (event.isCancelled()) ci.cancel();
    }
}
