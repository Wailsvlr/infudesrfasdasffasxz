package dev.donut.client.module.combat;

import dev.donut.client.event.EventBus;
import dev.donut.client.event.events.TickEvent;
import dev.donut.client.module.Category;
import dev.donut.client.module.Module;
import dev.donut.client.setting.BooleanSetting;
import dev.donut.client.setting.ModeSetting;
import dev.donut.client.setting.NumberSetting;
import dev.donut.client.util.PlayerUtil;
import dev.donut.client.util.RotationUtil;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;

public class KillAura extends Module {
    private final NumberSetting range = addSetting(new NumberSetting("Range", 3.8, 1.0, 6.0, 0.1));
    private final NumberSetting rotSpeed = addSetting(new NumberSetting("Rotation Speed", 0.8, 0.1, 1.0, 0.05));
    private final ModeSetting targetMode = addSetting(new ModeSetting("Target", "Closest", "Closest", "LowestHP"));
    private final ModeSetting rotationMode = addSetting(new ModeSetting("Rotations", "Smooth", "Smooth", "Instant", "None"));
    private final BooleanSetting autoBlock = addSetting(new BooleanSetting("AutoBlock", false));
    private final BooleanSetting waitForCooldown = addSetting(new BooleanSetting("Wait for Cooldown", true));

    public KillAura() {
        super("KillAura", "Automatically attacks nearby players", Category.COMBAT, 66);
    }

    @EventBus.Subscribe
    public void onTick(TickEvent event) {
        if (nullCheck()) return;
        if (mc.currentScreen != null) return;

        LivingEntity target = findTarget();
        if (target == null) return;

        if (!rotationMode.is("None")) {
            float[] rotations = RotationUtil.getRotations(target);
            if (rotationMode.is("Instant")) {
                mc.player.setYaw(rotations[0]);
                mc.player.setPitch(rotations[1]);
            } else {
                float[] current = {mc.player.getYaw(), mc.player.getPitch()};
                float[] smooth = RotationUtil.smoothRotations(current, rotations, rotSpeed.getFloat());
                mc.player.setYaw(smooth[0]);
                mc.player.setPitch(smooth[1]);
            }
        }

        if (waitForCooldown.isEnabled() && mc.player.getAttackCooldownProgress(0.5f) < 1.0f) return;

        mc.interactionManager.attackEntity(mc.player, target);
        mc.player.swingHand(Hand.MAIN_HAND);

        if (autoBlock.isEnabled()) {
            mc.interactionManager.interactEntity(mc.player, (LivingEntity) target, Hand.OFF_HAND);
        }
    }

    private LivingEntity findTarget() {
        if (targetMode.is("LowestHP")) {
            return PlayerUtil.getPlayers(range.getValue()).stream()
                    .min((a, b) -> Float.compare(a.getHealth(), b.getHealth()))
                    .orElse(null);
        }
        return PlayerUtil.getClosestPlayer(range.getValue());
    }
}
