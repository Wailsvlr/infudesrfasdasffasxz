package dev.donut.client.module.combat;

import dev.donut.client.event.EventBus;
import dev.donut.client.event.events.TickEvent;
import dev.donut.client.module.Category;
import dev.donut.client.module.Module;
import dev.donut.client.setting.NumberSetting;
import dev.donut.client.util.PlayerUtil;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.util.Hand;

public class ShieldBreaker extends Module {
    private final NumberSetting range = addSetting(new NumberSetting("Range", 3.5, 1.0, 6.0, 0.1));

    public ShieldBreaker() {
        super("ShieldBreaker", "Breaks enemy shields with axe swap", Category.COMBAT);
    }

    @EventBus.Subscribe
    public void onTick(TickEvent event) {
        if (nullCheck()) return;

        PlayerEntity target = PlayerUtil.getClosestPlayer(range.getValue());
        if (target == null) return;
        if (!target.isBlocking()) return;

        int axeSlot = findAxeSlot();
        if (axeSlot == -1) return;

        int oldSlot = mc.player.getInventory().selectedSlot;
        mc.player.getInventory().selectedSlot = axeSlot;
        mc.interactionManager.attackEntity(mc.player, target);
        mc.player.swingHand(Hand.MAIN_HAND);
        mc.player.getInventory().selectedSlot = oldSlot;
    }

    private int findAxeSlot() {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).getItem() instanceof AxeItem) return i;
        }
        return -1;
    }
}
