package com.wailsvlr.prism.modules;

import com.wailsvlr.prism.module.Category;
import com.wailsvlr.prism.module.Module;
import com.wailsvlr.prism.setting.NumberSetting;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;

import java.util.ArrayDeque;
import java.util.Deque;

public final class Backtrack extends Module {

    private static Backtrack instance;
    private final NumberSetting delay;
    private final Deque<DelayedPacket> queue = new ArrayDeque<>();

    public Backtrack() {
        super("Backtrack", "Delays incoming entity movement packets", Category.COMBAT);
        instance = this;
        delay = add(new NumberSetting("Delay", "Packet delay in milliseconds", 200, 50, 1000, 50, " ms"));
    }

    public static boolean shouldDelay(Packet<?> packet) {
        if (instance == null || !instance.isEnabled()) return false;
        return packet instanceof ClientboundMoveEntityPacket || packet instanceof ClientboundTeleportEntityPacket;
    }

    public static void enqueue(Packet<?> packet) {
        if (instance != null) {
            instance.queue.add(new DelayedPacket(packet, System.currentTimeMillis()));
        }
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.player.connection == null) return;

        long now = System.currentTimeMillis();
        long delayMs = delay.get().longValue();

        while (!queue.isEmpty() && now - queue.peek().time >= delayMs) {
            DelayedPacket dp = queue.poll();
            if (dp != null) {
                // Re-handle the packet by letting the connection process it
                // This is a simplified version - full implementation would re-inject into the handler
            }
        }
    }

    @Override
    protected void onDisable() {
        queue.clear();
    }

    @Override
    public String getInfo() { return delay.getInt() + "ms"; }

    private record DelayedPacket(Packet<?> packet, long time) {}
}
