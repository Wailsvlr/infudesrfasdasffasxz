package dev.donut.client.module.network;

import dev.donut.client.event.EventBus;
import dev.donut.client.event.events.PacketEvent;
import dev.donut.client.module.Category;
import dev.donut.client.module.Module;
import dev.donut.client.setting.NumberSetting;
import net.minecraft.network.packet.s2c.play.EntityPositionS2CPacket;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class BackTrack extends Module {
    private final NumberSetting delay = addSetting(new NumberSetting("Delay", 200.0, 50.0, 1000.0, 10.0));
    private final Queue<DelayedPacket> packets = new ConcurrentLinkedQueue<>();

    public BackTrack() {
        super("BackTrack", "Delays entity position updates", Category.NETWORK);
    }

    @EventBus.Subscribe
    public void onPacketReceive(PacketEvent.Receive event) {
        if (nullCheck()) return;
        if (event.getPacket() instanceof EntityPositionS2CPacket) {
            event.setCancelled(true);
            packets.add(new DelayedPacket(event.getPacket(), System.currentTimeMillis()));
        }
    }

    @EventBus.Subscribe
    public void onTick(dev.donut.client.event.events.TickEvent event) {
        if (nullCheck()) return;
        long now = System.currentTimeMillis();
        while (!packets.isEmpty() && now - packets.peek().time >= (long)(double) delay.getValue()) {
            DelayedPacket dp = packets.poll();
            if (dp != null) {
                mc.getNetworkHandler().onEntityPosition((EntityPositionS2CPacket) dp.packet);
            }
        }
    }

    @Override
    public void onDisable() {
        super.onDisable();
        packets.clear();
    }

    private record DelayedPacket(Object packet, long time) {}
}
