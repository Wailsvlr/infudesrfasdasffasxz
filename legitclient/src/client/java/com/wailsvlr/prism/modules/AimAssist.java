package com.wailsvlr.prism.modules;

import com.wailsvlr.prism.module.Category;
import com.wailsvlr.prism.module.Module;
import com.wailsvlr.prism.setting.NumberSetting;
import com.wailsvlr.prism.setting.BoolSetting;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public final class AimAssist extends Module {

    private final NumberSetting range;
    private final NumberSetting speed;
    private final NumberSetting fov;
    private final BoolSetting playersOnly;
    private final BoolSetting requireClick;
    private final BoolSetting invisibles;

    public AimAssist() {
        super("AimAssist", "Smoothly aims toward nearby targets", Category.COMBAT);
        range = add(new NumberSetting("Range", "Target detection range", 4.0, 2.0, 6.0, 0.5, " blocks"));
        speed = add(new NumberSetting("Speed", "Aim rotation speed", 4.0, 1.0, 10.0, 0.5, ""));
        fov = add(new NumberSetting("FOV", "Field of view for target detection", 90, 30, 360, 10, "°"));
        playersOnly = add(new BoolSetting("Players Only", "Only target players", true));
        requireClick = add(new BoolSetting("Click Only", "Only aim while attack key held", false));
        invisibles = add(new BoolSetting("Invisibles", "Target invisible entities", false));
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null || mc.screen != null) return;
        if (requireClick.isOn() && !mc.options.keyAttack.isDown()) return;

        LivingEntity best = null;
        double bestDist = range.get();

        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof LivingEntity le)) continue;
            if (le == mc.player) continue;
            if (!le.isAlive()) continue;
            if (playersOnly.isOn() && !(le instanceof Player)) continue;
            if (!invisibles.isOn() && le.isInvisible()) continue;

            double dist = mc.player.distanceTo(le);
            if (dist > bestDist) continue;

            float yawToTarget = getYawTo(le);
            float diff = wrapDegrees(yawToTarget - mc.player.getYRot());
            if (Math.abs(diff) > fov.get() / 2.0) continue;

            bestDist = dist;
            best = le;
        }

        if (best == null) return;

        float targetYaw = getYawTo(best);
        float targetPitch = getPitchTo(best);

        float yawDiff = wrapDegrees(targetYaw - mc.player.getYRot());
        float pitchDiff = targetPitch - mc.player.getXRot();

        float factor = (float) (speed.get() / 10.0);
        float jitter = 0.8f + (float) Math.random() * 0.4f;

        mc.player.setYRot(mc.player.getYRot() + yawDiff * factor * jitter);
        mc.player.setXRot(mc.player.getXRot() + pitchDiff * factor * jitter);
    }

    private float getYawTo(Entity entity) {
        double dx = entity.getX() - mc.player.getX();
        double dz = entity.getZ() - mc.player.getZ();
        return (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
    }

    private float getPitchTo(Entity entity) {
        double dx = entity.getX() - mc.player.getX();
        double dy = (entity.getY() + entity.getEyeHeight()) - (mc.player.getY() + mc.player.getEyeHeight());
        double dz = entity.getZ() - mc.player.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        return (float) -Math.toDegrees(Math.atan2(dy, dist));
    }

    private static float wrapDegrees(float deg) {
        deg %= 360;
        if (deg >= 180) deg -= 360;
        if (deg < -180) deg += 360;
        return deg;
    }

    @Override
    public String getInfo() { return String.format("%.1f", speed.get()); }
}
