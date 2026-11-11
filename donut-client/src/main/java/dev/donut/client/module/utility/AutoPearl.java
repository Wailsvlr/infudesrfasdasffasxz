package dev.donut.client.module.utility;

import dev.donut.client.module.Category;
import dev.donut.client.module.Module;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;

public class AutoPearl extends Module {
    public AutoPearl() {
        super("AutoPearl", "Throws a pearl on enable", Category.UTILITY);
    }

    @Override
    public void onEnable() {
        super.onEnable();
        if (nullCheck()) { setEnabled(false); return; }

        int pearlSlot = -1;
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).getItem() == Items.ENDER_PEARL) {
                pearlSlot = i;
                break;
            }
        }
        if (pearlSlot == -1) { setEnabled(false); return; }

        int old = mc.player.getInventory().selectedSlot;
        mc.player.getInventory().selectedSlot = pearlSlot;
        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
        mc.player.getInventory().selectedSlot = old;
        setEnabled(false);
    }
}
