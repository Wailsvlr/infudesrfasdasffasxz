package dev.donut.client.module.player;

import dev.donut.client.event.EventBus;
import dev.donut.client.event.events.TickEvent;
import dev.donut.client.module.Category;
import dev.donut.client.module.Module;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;

public class AutoArmor extends Module {
    public AutoArmor() {
        super("AutoArmor", "Equips best armor", Category.PLAYER);
    }

    @EventBus.Subscribe
    public void onTick(TickEvent event) {
        if (nullCheck()) return;
        if (mc.currentScreen != null) return;

        for (int i = 9; i < 45; i++) {
            ItemStack stack = mc.player.currentScreenHandler.getSlot(i).getStack();
            if (stack.isEmpty()) continue;
            if (!(stack.getItem() instanceof ArmorItem armor)) continue;

            int armorSlot = 8 - armor.getSlotType().getEntitySlotId();
            ItemStack current = mc.player.currentScreenHandler.getSlot(armorSlot).getStack();
            if (!current.isEmpty()) continue;

            mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, i, 0, SlotActionType.PICKUP, mc.player);
            mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, armorSlot, 0, SlotActionType.PICKUP, mc.player);
        }
    }
}
