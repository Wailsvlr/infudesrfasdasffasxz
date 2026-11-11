package com.wailsvlr.prism.modules;

import com.wailsvlr.prism.module.Category;
import com.wailsvlr.prism.module.Module;
import com.wailsvlr.prism.setting.ModeSetting;
import com.wailsvlr.prism.setting.NumberSetting;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.world.phys.Vec3;

public final class Velocity extends Module {
    private static Velocity instance;
    private final ModeSetting mode;
    private final NumberSetting horizontal, vertical;
    private int ticksSinceHit;
    private boolean wasHit;

    public Velocity() {
        super("Velocity", "Reduces knockback with anticheat bypass", Category.COMBAT);
        instance = this;
        mode = add(new ModeSetting("Mode", "Bypass technique", "Grim", "Grim", "Reduce", "Cancel"));
        horizontal = add(new NumberSetting("Horizontal", "Horizontal KB %", 0, 0, 100, 5, "%"));
        vertical = add(new NumberSetting("Vertical", "Vertical KB %", 0, 0, 100, 5, "%"));
    }

    /**
     * Called from ClientPacketListenerMixin at TAIL of handleSetEntityMotion.
     * Scales the knockback velocity according to settings.
     */
    public static Vec3 scale(Vec3 motion) {
        if (instance == null || !instance.isEnabled()) return motion;

        String m = instance.mode.get();

        if (m.equals("Cancel")) {
            return Vec3.ZERO;
        }

        if (m.equals("Grim")) {
            // Grim bypass: reduce to near-zero, then sprint-reset on next tick
            instance.wasHit = true;
            instance.ticksSinceHit = 0;
            double hPct = instance.horizontal.get() / 100.0;
            double vPct = instance.vertical.get() / 100.0;
            return new Vec3(motion.x * hPct, motion.y * vPct, motion.z * hPct);
        }

        // Reduce mode
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
        if (mc.player == null || mc.player.connection == null) return;
        if (!wasHit) return;

        ticksSinceHit++;

        if (mode.is("Grim") && ticksSinceHit == 1) {
            // Grim bypass: sprint-reset trick - stop sprint then immediately re-sprint
            // This makes the server recalculate knockback with sprint reduction
            mc.player.connection.send(new ServerboundPlayerCommandPacket(
                    mc.player, ServerboundPlayerCommandPacket.Action.STOP_SPRINTING));
            mc.player.connection.send(new ServerboundPlayerCommandPacket(
                    mc.player, ServerboundPlayerCommandPacket.Action.START_SPRINTING));
            wasHit = false;
        }

        if (ticksSinceHit > 5) wasHit = false;
    }

    @Override
    protected void onDisable() { wasHit = false; }

    @Override
    public String getInfo() {
        if (mode.is("Grim")) return "Grim " + horizontal.display() + "/" + vertical.display();
        if (mode.is("Cancel")) return "Cancel";
        return horizontal.display() + "/" + vertical.display();
    }
}
