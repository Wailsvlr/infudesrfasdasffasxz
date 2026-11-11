package com.wailsvlr.prism.modules;

import com.wailsvlr.prism.module.Category;
import com.wailsvlr.prism.module.Module;
import com.wailsvlr.prism.setting.NumberSetting;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public final class WTap extends Module {

    private final NumberSetting chance;

    private boolean sprinting;
    private int ticksSinceAttack;
    private boolean waitingToResprint;

    public WTap() {
        super("WTap", "Re-sprints between hits for extra knockback", Category.COMBAT);
        chance = add(new NumberSetting("Chance", "Chance to W-tap per hit", 100, 30, 100, 5, "%"));
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null) return;

        if (waitingToResprint) {
            ticksSinceAttack++;
            if (ticksSinceAttack >= 1) {
                mc.player.setSprinting(true);
                waitingToResprint = false;
            }
            return;
        }

        Entity target = mc.crosshairPickEntity;
        if (target instanceof LivingEntity le && le.isAlive()
                && mc.options.keyAttack.isDown()
                && mc.player.isSprinting()
                && mc.player.getAttackStrengthScale(0.5f) >= 0.9f) {

            if (Math.random() * 100 < chance.get()) {
                mc.player.setSprinting(false);
                waitingToResprint = true;
                ticksSinceAttack = 0;
            }
        }
    }

    @Override
    protected void onDisable() {
        waitingToResprint = false;
    }

    @Override
    public String getInfo() { return chance.getInt() + "%"; }
}
