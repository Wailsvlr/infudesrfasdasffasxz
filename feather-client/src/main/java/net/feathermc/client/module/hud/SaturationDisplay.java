package net.feathermc.client.module.hud;

import net.minecraft.client.gui.GuiGraphics;

public class SaturationDisplay extends HudModule {
    private final BooleanSetting showHunger = addSetting(new BooleanSetting("Show Hunger", true));

    public SaturationDisplay() {
        super("Saturation", "Shows hunger and saturation values", 5, 352);
    }

    @Override
    public void render(GuiGraphics graphics, float tickDelta) {
        if (mc.player == null) return;
        float saturation = mc.player.getFoodData().getSaturationLevel();
        int hunger = mc.player.getFoodData().getFoodLevel();
        String text;
        if (showHunger.getValue()) {
            text = String.format("Hunger: %d | Sat: %.1f", hunger, saturation);
        } else {
            text = String.format("Saturation: %.1f", saturation);
        }
        drawText(graphics, text, getX(), getY());
    }
}
