package dev.donut.client.module.movement;

import dev.donut.client.event.EventBus;
import dev.donut.client.event.events.TickEvent;
import dev.donut.client.module.Category;
import dev.donut.client.module.Module;
import dev.donut.client.setting.NumberSetting;

public class Speed extends Module {
    private final NumberSetting speed = addSetting(new NumberSetting("Speed", 1.2, 0.1, 5.0, 0.1));

    public Speed() {
        super("Speed", "Move faster", Category.MOVEMENT);
    }

    @EventBus.Subscribe
    public void onTick(TickEvent event) {
        if (nullCheck()) return;
        if (!mc.player.isOnGround()) return;
        if (mc.player.input.movementForward == 0 && mc.player.input.movementSideways == 0) return;

        double rad = Math.toRadians(mc.player.getYaw());
        double forward = mc.player.input.movementForward;
        double strafe = mc.player.input.movementSideways;
        double motionX = -Math.sin(rad) * forward + Math.cos(rad) * strafe;
        double motionZ = Math.cos(rad) * forward + Math.sin(rad) * strafe;
        double dist = Math.sqrt(motionX * motionX + motionZ * motionZ);
        if (dist > 0) {
            motionX /= dist;
            motionZ /= dist;
        }
        double spd = speed.getValue() * 0.2873;
        mc.player.setVelocity(motionX * spd, mc.player.getVelocity().y, motionZ * spd);
    }
}
