package dev.donut.client.module.movement;

import dev.donut.client.event.EventBus;
import dev.donut.client.event.events.TickEvent;
import dev.donut.client.module.Category;
import dev.donut.client.module.Module;

public class SafeWalk extends Module {
    public SafeWalk() {
        super("SafeWalk", "Prevents walking off edges", Category.MOVEMENT);
    }

    @EventBus.Subscribe
    public void onTick(TickEvent event) {
        if (nullCheck()) return;
        mc.player.setClipAtLedge(true);
    }

    @Override
    protected void onDisable() {
        if (mc.player != null) mc.player.setClipAtLedge(false);
    }
}
