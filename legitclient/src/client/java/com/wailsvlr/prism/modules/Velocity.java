package com.wailsvlr.prism.modules;

import com.wailsvlr.prism.module.Category;
import com.wailsvlr.prism.module.Module;
import com.wailsvlr.prism.setting.ModeSetting;
import com.wailsvlr.prism.setting.NumberSetting;
import net.minecraft.world.phys.Vec3;

public final class Velocity extends Module {
    private static Velocity instance;
    private final ModeSetting mode;
    private final NumberSetting horizontal, vertical;

    private boolean hit;
    private int ticksSinceHit;

    public Velocity() {
        super("Velocity", "Reduces knockback with anticheat bypass", Category.COMBAT);
        instance = this;
        mode = add(new ModeSetting("Mode", "Bypass technique", "Reverse", "Percentage", "Reverse", "Smooth"));
        horizontal = add(new NumberSetting("Horizontal", "Horizontal knockback retained", 80, 0, 100, 5, "%"));
        vertical = add(new NumberSetting("Vertical", "Vertical knockback retained", 100, 0, 100, 5, "%"));
    }

    public static Vec3 scale(Vec3 motion) {
        if (instance == null || !instance.isEnabled()) return motion;

        double hPct = instance.horizontal.get() / 100.0;
        double vPct = instance.vertical.get() / 100.0;

        if (instance.mode.is("Percentage")) {
            double jitter = 0.97 + Math.random() * 0.06;
            return new Vec3(
                    motion.x * hPct * jitter,
                    motion.y * vPct,
                    motion.z * hPct * jitter
            );
        }

        instance.hit = true;
        instance.ticksSinceHit = 0;
        return motion;
    }

    @Override
    public void onTick() {
        if (!hit || mc.player == null) return;
        ticksSinceHit++;

        double hPct = horizontal.get() / 100.0;
        double vPct = vertical.get() / 100.0;
        Vec3 current = mc.player.getDeltaMovement();

        if (mode.is("Reverse") && ticksSinceHit == 1) {
            double jitter = 0.97 + Math.random() * 0.06;
            mc.player.setDeltaMovement(
                    current.x * hPct * jitter,
                    current.y * vPct,
                    current.z * hPct * jitter
            );
            hit = false;
        } else if (mode.is("Smooth") && ticksSinceHit <= 3) {
            double factor = 1.0 - ((1.0 - hPct) * ticksSinceHit / 3.0);
            double jitter = 0.98 + Math.random() * 0.04;
            mc.player.setDeltaMovement(
                    current.x * factor * jitter,
                    current.y * (ticksSinceHit == 1 ? vPct : 1.0),
                    current.z * factor * jitter
            );
            if (ticksSinceHit >= 3) hit = false;
        }

        if (ticksSinceHit > 5) hit = false;
    }

    @Override
    protected void onDisable() {
        hit = false;
    }

    @Override
    public String getInfo() { return mode.get() + " " + horizontal.display() + "/" + vertical.display(); }
}
