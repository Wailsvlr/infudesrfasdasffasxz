package com.wailsvlr.prism.modules;

import com.wailsvlr.prism.module.Category;
import com.wailsvlr.prism.module.Module;
import com.wailsvlr.prism.setting.ModeSetting;
import com.wailsvlr.prism.setting.NumberSetting;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;

public final class Speed extends Module {

    private final ModeSetting mode;
    private final NumberSetting speed;

    public Speed() {
        super("Speed", "Increases movement speed", Category.MISC);
        mode = add(new ModeSetting("Mode", "Speed method", "Strafe", "Strafe", "OnGround"));
        speed = add(new NumberSetting("Speed", "Movement speed multiplier", 1.3, 1.0, 3.0, 0.1, "x"));
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null) return;
        if (mc.player.isFallFlying() || mc.player.isPassenger()) return;
        if (mc.player.isInWater() || mc.player.isInLava()) return;

        float forward = (mc.options.keyUp.isDown() ? 1f : 0f) - (mc.options.keyDown.isDown() ? 1f : 0f);
        float strafe = (mc.options.keyLeft.isDown() ? 1f : 0f) - (mc.options.keyRight.isDown() ? 1f : 0f);
        if (forward == 0 && strafe == 0) return;

        double mult = speed.get();
        if (mc.player.hasEffect(MobEffects.SPEED)) {
            int amp = mc.player.getEffect(MobEffects.SPEED).getAmplifier();
            mult *= 1.0 + (amp + 1) * 0.2;
        }

        if (mode.is("Strafe")) {
            float yaw = mc.player.getYRot();
            double rad = Math.toRadians(yaw);
            double vx = -Math.sin(rad) * forward + Math.cos(rad) * strafe;
            double vz = Math.cos(rad) * forward + Math.sin(rad) * strafe;
            double len = Math.sqrt(vx * vx + vz * vz);
            if (len > 0) {
                vx /= len;
                vz /= len;
            }
            double baseSpeed = 0.2873;
            Vec3 v = mc.player.getDeltaMovement();
            mc.player.setDeltaMovement(vx * baseSpeed * mult, v.y, vz * baseSpeed * mult);

        } else if (mode.is("OnGround")) {
            if (!mc.player.onGround()) return;
            Vec3 v = mc.player.getDeltaMovement();
            mc.player.setDeltaMovement(v.x * mult, v.y, v.z * mult);
        }
    }

    @Override
    public String getInfo() { return mode.get() + " " + String.format("%.1fx", speed.get()); }
}
