package dev.donut.client.module.combat;

import dev.donut.client.event.EventBus;
import dev.donut.client.event.events.TickEvent;
import dev.donut.client.module.Category;
import dev.donut.client.module.Module;
import dev.donut.client.setting.BooleanSetting;
import dev.donut.client.setting.NumberSetting;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;

public class Triggerbot extends Module {
    private final NumberSetting delay = addSetting(new NumberSetting("Delay", 0.0, 0.0, 500.0, 10.0));
    private final BooleanSetting waitCooldown = addSetting(new BooleanSetting("Wait Cooldown", true));

    private long lastAttack = 0;

    public Triggerbot() {
        super("Triggerbot", "Attacks entity you look at", Category.COMBAT);
    }

    @EventBus.Subscribe
    public void onTick(TickEvent event) {
        if (nullCheck()) return;
        if (mc.currentScreen != null) return;
        if (mc.crosshairTarget == null) return;
        if (mc.crosshairTarget.getType() != HitResult.Type.ENTITY) return;

        Entity target = ((EntityHitResult) mc.crosshairTarget).getEntity();
        if (!(target instanceof PlayerEntity)) return;
        if (target == mc.player) return;

        if (waitCooldown.isEnabled() && mc.player.getAttackCooldownProgress(0.5f) < 1.0f) return;

        long now = System.currentTimeMillis();
        if (now - lastAttack < delay.getValue()) return;
        lastAttack = now;

        mc.interactionManager.attackEntity(mc.player, target);
        mc.player.swingHand(Hand.MAIN_HAND);
    }
}
