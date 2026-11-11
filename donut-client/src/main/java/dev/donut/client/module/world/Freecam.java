package dev.donut.client.module.world;

import dev.donut.client.event.EventBus;
import dev.donut.client.event.events.TickEvent;
import dev.donut.client.module.Category;
import dev.donut.client.module.Module;
import dev.donut.client.setting.NumberSetting;

public class Freecam extends Module {
    private final NumberSetting speed = addSetting(new NumberSetting("Speed", 1.0, 0.1, 5.0, 0.1));
    private double startX, startY, startZ;

    public Freecam() {
        super("Freecam", "Fly camera freely", Category.WORLD);
    }

    @Override
    public void onEnable() {
        super.onEnable();
        if (nullCheck()) return;
        startX = mc.player.getX();
        startY = mc.player.getY();
        startZ = mc.player.getZ();
    }

    @Override
    public void onDisable() {
        super.onDisable();
        if (nullCheck()) return;
        mc.player.setPosition(startX, startY, startZ);
    }

    @EventBus.Subscribe
    public void onTick(TickEvent event) {
        if (nullCheck()) return;
        mc.player.setNoGravity(true);
        mc.player.setVelocity(0, 0, 0);

        double s = speed.getValue();
        float yaw = (float) Math.toRadians(mc.player.getYaw());
        float pitch = (float) Math.toRadians(mc.player.getPitch());

        if (mc.options.forwardKey.isPressed()) {
            mc.player.setPosition(
                    mc.player.getX() - Math.sin(yaw) * s,
                    mc.player.getY() - Math.sin(pitch) * s,
                    mc.player.getZ() + Math.cos(yaw) * s
            );
        }
        if (mc.options.backKey.isPressed()) {
            mc.player.setPosition(
                    mc.player.getX() + Math.sin(yaw) * s,
                    mc.player.getY() + Math.sin(pitch) * s,
                    mc.player.getZ() - Math.cos(yaw) * s
            );
        }
        if (mc.options.jumpKey.isPressed()) {
            mc.player.setPosition(mc.player.getX(), mc.player.getY() + s, mc.player.getZ());
        }
        if (mc.options.sneakKey.isPressed()) {
            mc.player.setPosition(mc.player.getX(), mc.player.getY() - s, mc.player.getZ());
        }
    }
}
