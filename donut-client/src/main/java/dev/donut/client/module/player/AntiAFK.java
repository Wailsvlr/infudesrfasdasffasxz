package dev.donut.client.module.player;

import dev.donut.client.event.EventBus;
import dev.donut.client.event.events.TickEvent;
import dev.donut.client.module.Category;
import dev.donut.client.module.Module;

public class AntiAFK extends Module {
    private int timer = 0;

    public AntiAFK() {
        super("AntiAFK", "Prevents AFK kick", Category.PLAYER);
    }

    @EventBus.Subscribe
    public void onTick(TickEvent event) {
        if (nullCheck()) return;
        timer++;
        if (timer > 40) {
            mc.player.setYaw(mc.player.getYaw() + 1);
            timer = 0;
        }
    }
}
