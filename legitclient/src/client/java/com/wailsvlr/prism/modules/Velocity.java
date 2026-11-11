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
        mode = add(new ModeSetting("Mode", "Bypass technique", "Jump", "Jump", "Reduce", "Cancel"));
        horizontal = add(new NumberSetting("Horizontal", "Horizontal KB % (Reduce)", 85, 0, 100, 5, "%"));
        vertical = add(new NumberSetting("Vertical", "Vertical KB % (Reduce)", 100, 0, 100, 5, "%"));
    }

    public static Vec3 scale(Vec3 motion) {
        if (instance == null || !instance.isEnabled()) return motion;

        String m = instance.mode.get();

        if (m.equals("Jump")) {
            instance.hit = true;
            instance.ticksSinceHit = 0;
            return motion;
        }

        if (m.equals("Cancel")) {
            return Vec3.ZERO;
        }

        double hPct = instance.horizontal.get() / 100.0;
        double vPct = instance.vertical.get() / 100.0;
        double jitter = 0.97 + Math.random() * 0.06;
        return new Vec3(
                motion.x * hPct * jitter,
                motion.y * vPct,
                motion.z * hPct * jitter
        );
    }

    @Override
    public void onTick() {
        if (!hit || mc.player == null) return;
        ticksSinceHit++;

        if (mode.is("Jump") && ticksSinceHit == 1) {
            mc.player.setSprinting(true);
            if (mc.player.onGround()) {
                Vec3 v = mc.player.getDeltaMovement();
                mc.player.setDeltaMovement(v.x, 0.42, v.z);
            }
            hit = false;
        }

        if (ticksSinceHit > 5) hit = false;
    }

    @Override
    protected void onDisable() { hit = false; }

    @Override
    public String getInfo() {
        if (mode.is("Jump")) return "Jump";
        if (mode.is("Cancel")) return "Cancel";
        return horizontal.display() + "/" + vertical.display();
    }
}
