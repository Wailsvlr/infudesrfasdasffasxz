package dev.donut.client.module.utility;

import dev.donut.client.event.EventBus;
import dev.donut.client.event.events.AttackEvent;
import dev.donut.client.module.Category;
import dev.donut.client.module.Module;
import net.minecraft.item.SwordItem;

public class HitSwap extends Module {
    public HitSwap() {
        super("HitSwap", "Swaps to sword on hit", Category.UTILITY);
    }

    @EventBus.Subscribe
    public void onAttack(AttackEvent event) {
        if (nullCheck()) return;
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).getItem() instanceof SwordItem) {
                mc.player.getInventory().selectedSlot = i;
                return;
            }
        }
    }
}
