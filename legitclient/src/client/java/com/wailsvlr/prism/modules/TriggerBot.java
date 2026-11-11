package com.wailsvlr.prism.modules;

import com.wailsvlr.prism.module.Category;
import com.wailsvlr.prism.module.Module;
import com.wailsvlr.prism.setting.NumberSetting;
import com.wailsvlr.prism.setting.BoolSetting;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

public final class TriggerBot extends Module {

    private final NumberSetting minDelay;
    private final NumberSetting maxDelay;
    private final BoolSetting playersOnly;
    private final BoolSetting weaponOnly;

    private long nextAttack;

    public TriggerBot() {
        super("TriggerBot", "Attacks entities when crosshair is on them", Category.COMBAT);
        minDelay = add(new NumberSetting("Min Delay", "Minimum delay between attacks", 50, 0, 500, 10, " ms"));
        maxDelay = add(new NumberSetting("Max Delay", "Maximum delay between attacks", 100, 0, 500, 10, " ms"));
        playersOnly = add(new BoolSetting("Players Only", "Only attack players", false));
        weaponOnly = add(new BoolSetting("Weapon Only", "Only attack with weapons", true));
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null || mc.screen != null) return;

        Entity target = mc.crosshairPickEntity;
        if (target == null || !(target instanceof LivingEntity le) || !le.isAlive()) return;
        if (playersOnly.isOn() && !(le instanceof Player)) return;
        if (le == mc.player) return;

        if (weaponOnly.isOn() && mc.player.getMainHandItem().isEmpty()) return;
        if (mc.player.getAttackStrengthScale(0.5f) < 0.9f) return;

        long now = System.currentTimeMillis();
        if (now < nextAttack) return;

        mc.gameMode.attack(mc.player, le);
        mc.player.swing(InteractionHand.MAIN_HAND);

        double min = Math.min(minDelay.get(), maxDelay.get());
        double max = Math.max(minDelay.get(), maxDelay.get());
        nextAttack = now + (long) (min + Math.random() * (max - min));
    }

    @Override
    public String getInfo() { return minDelay.getInt() + "-" + maxDelay.getInt() + "ms"; }
}
