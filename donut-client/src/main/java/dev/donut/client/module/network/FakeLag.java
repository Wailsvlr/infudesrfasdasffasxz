package dev.donut.client.module.network;

import dev.donut.client.event.EventBus;
import dev.donut.client.event.events.PacketEvent;
import dev.donut.client.module.Category;
import dev.donut.client.module.Module;
import dev.donut.client.setting.NumberSetting;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class FakeLag extends Module {
    private final NumberSetting delay = addSetting(new NumberSetting("Delay", 200.0, 50.0, 2000.0, 10.0));
    private final Queue<DelayedPacket> packets = new ConcurrentLinkedQueue<>();

    public FakeLag() {
        super("FakeLag", "Delays outgoing packets", Category.NETWORK);
    }

    @EventBus.Subscribe
    public void onPacketSend(PacketEvent.Send event) {
        if (nullCheck()) return;
        if (event.getPacket() instanceof PlayerMoveC2SPacket) {
            packets.add(new DelayedPacket(event.getPacket(), System.currentTimeMillis()));
            event.setCancelled(true);
        }
    }

    @EventBus.Subscribe
    public void onTick(dev.donut.client.event.events.TickEvent event) {
        if (nullCheck()) return;
        long now = System.currentTimeMillis();
        while (!packets.isEmpty() && now - packets.peek().time >= (long)(double) delay.getValue()) {
            DelayedPacket dp = packets.poll();
            if (dp != null && dp.packet instanceof net.minecraft.network.packet.Packet<?> p) {
                mc.getNetworkHandler().getConnection().send(p);
            }
        }
    }

    @Override
    public void onDisable() {
        super.onDisable();
        if (mc.getNetworkHandler() != null) {
            while (!packets.isEmpty()) {
                DelayedPacket dp = packets.poll();
                if (dp != null && dp.packet instanceof net.minecraft.network.packet.Packet<?> p) {
                    mc.getNetworkHandler().getConnection().send(p);
                }
            }
        } else {
            packets.clear();
        }
    }

    private record DelayedPacket(Object packet, long time) {}
}
