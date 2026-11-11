package com.wailsvlr.prism.modules;

import com.wailsvlr.prism.module.Category;
import com.wailsvlr.prism.module.Module;
import com.wailsvlr.prism.setting.NumberSetting;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Client-side backtrack: stores recent positions of the target you're looking at
 * and extends their hitbox backward so you can hit them at a previous location.
 * Does not intercept packets, so it won't desync or trigger anticheat packet checks.
 */
public final class Backtrack extends Module {

    private static Backtrack instance;
    private final NumberSetting delay;
    private final Deque<Vec3> positionHistory = new ArrayDeque<>();
    private Entity trackedEntity;
    private int tickCounter;

    public Backtrack() {
        super("Backtrack", "Extends target hitbox using position history", Category.COMBAT);
        instance = this;
        delay = add(new NumberSetting("Delay", "History length in ticks", 5, 1, 10, 1, " ticks"));
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null) return;

        Entity crosshair = mc.crosshairPickEntity;
        if (crosshair instanceof LivingEntity le && le.isAlive() && le instanceof Player) {
            if (crosshair != trackedEntity) {
                trackedEntity = crosshair;
                positionHistory.clear();
            }
            positionHistory.addLast(crosshair.position());
            int maxHistory = delay.getInt();
            while (positionHistory.size() > maxHistory) {
                positionHistory.removeFirst();
            }
        } else {
            trackedEntity = null;
            positionHistory.clear();
        }
    }

    public static Vec3 getBacktrackPos() {
        if (instance == null || !instance.isEnabled()) return null;
        if (instance.positionHistory.isEmpty()) return null;
        return instance.positionHistory.peekFirst();
    }

    public static Entity getTrackedEntity() {
        if (instance == null || !instance.isEnabled()) return null;
        return instance.trackedEntity;
    }

    @Override
    protected void onDisable() {
        positionHistory.clear();
        trackedEntity = null;
    }

    @Override
    public String getInfo() { return delay.getInt() + "t"; }
}
