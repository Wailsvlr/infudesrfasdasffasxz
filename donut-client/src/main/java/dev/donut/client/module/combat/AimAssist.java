package dev.donut.client.module.combat;

import dev.donut.client.event.EventBus;
import dev.donut.client.event.events.TickEvent;
import dev.donut.client.module.Category;
import dev.donut.client.module.Module;
import dev.donut.client.setting.NumberSetting;
import dev.donut.client.util.PlayerUtil;
import dev.donut.client.util.RotationUtil;
import net.minecraft.entity.player.PlayerEntity;

public class AimAssist extends Module {
    private final NumberSetting range = addSetting(new NumberSetting("Range", 4.5, 1.0, 8.0, 0.1));
    private final NumberSetting speed = addSetting(new NumberSetting("Speed", 0.5, 0.1, 1.0, 0.05));
    private final NumberSetting fov = addSetting(new NumberSetting("FOV", 90.0, 10.0, 360.0, 5.0));

    public AimAssist() {
        super("AimAssist", "Smoothly aims toward players", Category.COMBAT);
    }

    @EventBus.Subscribe
    public void onTick(TickEvent event) {
        if (nullCheck()) return;
        if (mc.currentScreen != null) return;

        PlayerEntity target = PlayerUtil.getClosestPlayer(range.getValue());
        if (target == null) return;

        float[] needed = RotationUtil.getRotations(target);
        float yawDiff = RotationUtil.wrapDegrees(needed[0] - mc.player.getYaw());
        if (Math.abs(yawDiff) > fov.getValue() / 2.0) return;

        float[] current = {mc.player.getYaw(), mc.player.getPitch()};
        float[] smooth = RotationUtil.smoothRotations(current, needed, speed.getFloat());
        mc.player.setYaw(smooth[0]);
        mc.player.setPitch(smooth[1]);
    }
}
