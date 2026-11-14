package net.feathermc.client.module.hud;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

public class ArmorStatusDisplay extends HudModule {
    private final BooleanSetting showDurability = addSetting(new BooleanSetting("Show Durability", true));
    private final ModeSetting orientation = addSetting(new ModeSetting("Orientation", "Vertical", "Vertical", "Horizontal"));

    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    public ArmorStatusDisplay() {
        super("Armor Status", "Shows equipped armor and durability", 5, 170);
    }

    @Override
    public void render(GuiGraphics graphics, float tickDelta) {
        if (mc.player == null) return;
        int x = getX();
        int y = getY();
        boolean vertical = orientation.getValue().equals("Vertical");

        for (EquipmentSlot slot : ARMOR_SLOTS) {
            ItemStack stack = mc.player.getItemBySlot(slot);
            if (!stack.isEmpty()) {
                graphics.renderItem(stack, x, y - 4);
                if (showDurability.getValue() && stack.isDamageableItem()) {
                    int max = stack.getMaxDamage();
                    int remaining = max - stack.getDamageValue();
                    int percent = (int) ((float) remaining / max * 100);
                    int color = percent > 50 ? 0xFF55FF55 : percent > 25 ? 0xFFFFFF55 : 0xFFFF5555;
                    graphics.drawString(mc.font, remaining + "/" + max, x + 18, y, color, false);
                }
            }
            if (vertical) y += 18;
            else x += 70;
        }
    }
}
