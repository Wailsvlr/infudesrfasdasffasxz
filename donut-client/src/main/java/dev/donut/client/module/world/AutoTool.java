package dev.donut.client.module.world;

import dev.donut.client.event.EventBus;
import dev.donut.client.event.events.TickEvent;
import dev.donut.client.module.Category;
import dev.donut.client.module.Module;
import net.minecraft.block.BlockState;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;

public class AutoTool extends Module {
    public AutoTool() {
        super("AutoTool", "Switches to best tool", Category.WORLD);
    }

    @EventBus.Subscribe
    public void onTick(TickEvent event) {
        if (nullCheck()) return;
        if (!mc.options.attackKey.isPressed()) return;
        if (mc.crosshairTarget == null || mc.crosshairTarget.getType() != HitResult.Type.BLOCK) return;

        BlockHitResult hit = (BlockHitResult) mc.crosshairTarget;
        BlockState state = mc.world.getBlockState(hit.getBlockPos());

        float bestSpeed = 1.0f;
        int bestSlot = mc.player.getInventory().selectedSlot;

        for (int i = 0; i < 9; i++) {
            float speed = mc.player.getInventory().getStack(i).getMiningSpeedMultiplier(state);
            if (speed > bestSpeed) {
                bestSpeed = speed;
                bestSlot = i;
            }
        }
        mc.player.getInventory().selectedSlot = bestSlot;
    }
}
