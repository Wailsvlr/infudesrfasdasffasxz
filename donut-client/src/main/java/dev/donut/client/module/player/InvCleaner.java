package dev.donut.client.module.player;

import dev.donut.client.event.EventBus;
import dev.donut.client.event.events.TickEvent;
import dev.donut.client.module.Category;
import dev.donut.client.module.Module;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;

public class InvCleaner extends Module {
    private int timer = 0;

    public InvCleaner() {
        super("InvCleaner", "Drops junk items", Category.PLAYER);
    }

    @EventBus.Subscribe
    public void onTick(TickEvent event) {
        if (nullCheck()) return;
        if (mc.currentScreen != null) return;
        timer++;
        if (timer < 5) return;
        timer = 0;

        for (int i = 9; i < 45; i++) {
            ItemStack stack = mc.player.currentScreenHandler.getSlot(i).getStack();
            if (stack.isEmpty()) continue;
            String name = stack.getItem().toString().toLowerCase();
            if (name.contains("dirt") || name.contains("gravel") || name.contains("cobblestone")
                    || name.contains("rotten_flesh") || name.contains("poisonous_potato")) {
                mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, i, 1, SlotActionType.THROW, mc.player);
                return;
            }
        }
    }
}
