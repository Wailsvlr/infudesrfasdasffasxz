package dev.donut.client.module.player;

import dev.donut.client.event.EventBus;
import dev.donut.client.event.events.TickEvent;
import dev.donut.client.module.Category;
import dev.donut.client.module.Module;
import dev.donut.client.setting.NumberSetting;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;

public class ChestStealer extends Module {
    private final NumberSetting delay = addSetting(new NumberSetting("Delay", 50.0, 0.0, 500.0, 10.0));
    private long lastSteal = 0;

    public ChestStealer() {
        super("ChestStealer", "Steals items from chests", Category.PLAYER);
    }

    @EventBus.Subscribe
    public void onTick(TickEvent event) {
        if (nullCheck()) return;
        if (!(mc.currentScreen instanceof GenericContainerScreen screen)) return;

        long now = System.currentTimeMillis();
        if (now - lastSteal < delay.getValue()) return;

        int rows = screen.getScreenHandler().getRows();
        for (int i = 0; i < rows * 9; i++) {
            ItemStack stack = screen.getScreenHandler().getSlot(i).getStack();
            if (!stack.isEmpty()) {
                mc.interactionManager.clickSlot(screen.getScreenHandler().syncId, i, 0, SlotActionType.QUICK_MOVE, mc.player);
                lastSteal = now;
                return;
            }
        }
    }
}
