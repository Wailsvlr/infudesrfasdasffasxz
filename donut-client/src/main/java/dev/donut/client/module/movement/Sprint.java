package dev.donut.client.module.movement;

import dev.donut.client.event.EventBus;
import dev.donut.client.event.events.TickEvent;
import dev.donut.client.module.Category;
import dev.donut.client.module.Module;

public class Sprint extends Module {
    public Sprint() {
        super("Sprint", "Always sprint", Category.MOVEMENT);
    }

    @EventBus.Subscribe
    public void onTick(TickEvent event) {
        if (nullCheck()) return;
        if (mc.player.input.movementForward > 0 && !mc.player.isSneaking()) {
            mc.player.setSprinting(true);
        }
    }
}
