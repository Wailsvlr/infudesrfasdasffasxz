package com.wailsvlr.prism.modules;

import com.wailsvlr.prism.module.Category;
import com.wailsvlr.prism.module.Module;
import com.wailsvlr.prism.setting.NumberSetting;
import com.wailsvlr.prism.setting.BoolSetting;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

public final class AutoClicker extends Module {

    private final NumberSetting minCps;
    private final NumberSetting maxCps;
    private final BoolSetting weaponOnly;
    private final BoolSetting playersOnly;

    private long nextClick;

    public AutoClicker() {
        super("AutoClicker", "Automatically attacks entities", Category.COMBAT);
        minCps = add(new NumberSetting("Min CPS", "Minimum clicks per second", 8, 1, 20, 1, " cps"));
        maxCps = add(new NumberSetting("Max CPS", "Maximum clicks per second", 12, 1, 20, 1, " cps"));
        weaponOnly = add(new BoolSetting("Weapon Only", "Only click while holding a weapon", true));
        playersOnly = add(new BoolSetting("Players Only", "Only target players", false));
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null || mc.screen != null) return;
        if (!mc.options.keyAttack.isDown()) return;

        Entity target = mc.crosshairPickEntity;
        if (target == null || !target.isAlive()) return;
        if (!(target instanceof LivingEntity)) return;
        if (playersOnly.isOn() && !(target instanceof Player)) return;

        if (weaponOnly.isOn()) {
            var stack = mc.player.getMainHandItem();
            if (stack.isEmpty()) return;
        }

        long now = System.currentTimeMillis();
        if (now < nextClick) return;

        mc.gameMode.attack(mc.player, target);
        mc.player.swing(InteractionHand.MAIN_HAND);

        double min = Math.min(minCps.get(), maxCps.get());
        double max = Math.max(minCps.get(), maxCps.get());
        double cps = min + Math.random() * (max - min);
        nextClick = now + (long) (1000.0 / cps);
    }

    @Override
    public String getInfo() {
        return minCps.getInt() + "-" + maxCps.getInt() + " CPS";
    }
}
