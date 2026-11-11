package dev.donut.client.module.utility;

import dev.donut.client.event.EventBus;
import dev.donut.client.event.events.TickEvent;
import dev.donut.client.module.Category;
import dev.donut.client.module.Module;
import dev.donut.client.setting.NumberSetting;

public class SpeedLeap extends Module {
    private final NumberSetting boost = addSetting(new NumberSetting("Boost", 1.5, 0.5, 5.0, 0.1));

    public SpeedLeap() {
        super("SpeedLeap", "Boosts speed on jump", Category.UTILITY);
    }

    @EventBus.Subscribe
    public void onTick(TickEvent event) {
        if (nullCheck()) return;
        if (mc.player.isOnGround() && mc.options.jumpKey.isPressed()) {
            double yaw = Math.toRadians(mc.player.getYaw());
            double speed = boost.getValue();
            mc.player.addVelocity(-Math.sin(yaw) * speed * 0.2, 0.0, Math.cos(yaw) * speed * 0.2);
        }
    }
}
