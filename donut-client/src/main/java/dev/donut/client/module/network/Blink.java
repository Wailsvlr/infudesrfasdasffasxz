package dev.donut.client.module.network;

import dev.donut.client.event.EventBus;
import dev.donut.client.event.events.PacketEvent;
import dev.donut.client.module.Category;
import dev.donut.client.module.Module;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class Blink extends Module {
    private final Queue<Object> packets = new ConcurrentLinkedQueue<>();

    public Blink() {
        super("Blink", "Holds movement packets", Category.NETWORK);
    }

    @EventBus.Subscribe
    public void onPacketSend(PacketEvent.Send event) {
        if (nullCheck()) return;
        if (event.getPacket() instanceof PlayerMoveC2SPacket) {
            packets.add(event.getPacket());
            event.setCancelled(true);
        }
    }

    @Override
    public void onDisable() {
        super.onDisable();
        if (mc.getNetworkHandler() == null) { packets.clear(); return; }
        while (!packets.isEmpty()) {
            Object p = packets.poll();
            if (p instanceof net.minecraft.network.packet.Packet<?> packet) {
                mc.getNetworkHandler().getConnection().send((net.minecraft.network.packet.Packet<?>) packet);
            }
        }
    }
}
