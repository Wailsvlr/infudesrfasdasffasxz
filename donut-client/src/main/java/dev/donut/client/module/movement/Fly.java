package dev.donut.client.module.movement;

import dev.donut.client.event.EventBus;
import dev.donut.client.event.events.TickEvent;
import dev.donut.client.module.Category;
import dev.donut.client.module.Module;
import dev.donut.client.setting.NumberSetting;

public class Fly extends Module {
    private final NumberSetting speed = addSetting(new NumberSetting("Speed", 1.0, 0.1, 10.0, 0.1));

    public Fly() {
        super("Fly", "Creative-style flight", Category.MOVEMENT);
    }

    @EventBus.Subscribe
    public void onTick(TickEvent event) {
        if (nullCheck()) return;
        mc.player.getAbilities().flying = true;
        mc.player.getAbilities().setFlySpeed((float)(speed.getValue() * 0.05f));
    }

    @Override
    protected void onDisable() {
        if (mc.player != null) {
            mc.player.getAbilities().flying = false;
            mc.player.getAbilities().setFlySpeed(0.05f);
        }
    }
}
