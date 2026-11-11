package com.wailsvlr.prism.modules;

import com.wailsvlr.prism.module.Category;
import com.wailsvlr.prism.module.Module;
import com.wailsvlr.prism.setting.NumberSetting;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class ChestStealer extends Module {

    private final NumberSetting delay;
    private int tickCounter;

    public ChestStealer() {
        super("ChestStealer", "Automatically takes items from containers", Category.MISC);
        delay = add(new NumberSetting("Delay", "Ticks between each item steal", 1, 0, 10, 1, " ticks"));
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        if (!(mc.screen instanceof AbstractContainerScreen<?> screen)) return;

        AbstractContainerMenu menu = screen.getMenu();
        int containerSlots = menu.slots.size() - 36;
        if (containerSlots <= 0) return;

        tickCounter++;
        if (tickCounter < delay.getInt()) return;
        tickCounter = 0;

        for (int i = 0; i < containerSlots; i++) {
            Slot slot = menu.getSlot(i);
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) continue;

            mc.gameMode.handleInventoryMouseClick(menu.containerId, i, 0, ClickType.QUICK_MOVE, mc.player);
            if (delay.getInt() > 0) return;
        }
    }

    @Override
    protected void onDisable() { tickCounter = 0; }

    @Override
    public String getInfo() { return delay.getInt() + "t"; }
}
