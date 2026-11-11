package com.wailsvlr.prism.modules;

import com.wailsvlr.prism.module.Category;
import com.wailsvlr.prism.module.Module;
import com.wailsvlr.prism.setting.NumberSetting;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * W-Tap: resets sprint via packet right before the hit lands, then re-sprints.
 * Server sees a fresh sprint → full sprint knockback bonus applied to the target.
 * Uses packet-level sprint control so vanilla movement code can't override it.
 */
public final class WTap extends Module {

    private final NumberSetting chance;
    private boolean resetNextTick;
    private int cooldownTicks;

    public WTap() {
        super("WTap", "Re-sprints between hits for extra knockback", Category.COMBAT);
        chance = add(new NumberSetting("Chance", "Chance to W-tap per hit", 100, 30, 100, 5, "%"));
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.player.connection == null) return;

        if (cooldownTicks > 0) {
            cooldownTicks--;
        }

        // Re-sprint one tick after the reset
        if (resetNextTick) {
            mc.player.connection.send(new ServerboundPlayerCommandPacket(
                    mc.player, ServerboundPlayerCommandPacket.Action.START_SPRINTING));
            mc.player.setSprinting(true);
            resetNextTick = false;
            return;
        }

        // Detect attack: attack key down, crosshair on living entity, attack charged, sprinting
        Entity target = mc.crosshairPickEntity;
        if (target instanceof LivingEntity le && le.isAlive()
                && mc.options.keyAttack.isDown()
                && mc.player.isSprinting()
                && mc.player.getAttackStrengthScale(0.5f) >= 0.9f
                && cooldownTicks <= 0) {

            if (Math.random() * 100 < chance.get()) {
                // Stop sprint via packet - server immediately registers sprint loss
                mc.player.connection.send(new ServerboundPlayerCommandPacket(
                        mc.player, ServerboundPlayerCommandPacket.Action.STOP_SPRINTING));
                mc.player.setSprinting(false);
                resetNextTick = true;
                cooldownTicks = 5; // don't W-tap every single tick
            }
        }
    }

    @Override
    protected void onDisable() {
        resetNextTick = false;
        cooldownTicks = 0;
    }

    @Override
    public String getInfo() { return chance.getInt() + "%"; }
}
