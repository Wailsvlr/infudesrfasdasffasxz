package net.feathermc.client.module.hud;

import net.feathermc.client.event.Subscribe;
import net.feathermc.client.event.events.TickEvent;
import net.minecraft.client.gui.GuiGraphics;

public class ComboCounter extends HudModule {
    private int combo;
    private int maxCombo;
    private float lastHealth = -1;
    private boolean wasSwinging;
    private final BooleanSetting showMax = addSetting(new BooleanSetting("Show Max Combo", true));
    private final NumberSetting displayTime = addSetting(new NumberSetting("Display Time (s)", 5.0, 1.0, 30.0, 1.0));
    private long lastComboTime;

    public ComboCounter() {
        super("Combo Counter", "Counts consecutive hits without taking damage", 5, 326);
    }

    @Subscribe
    public void onTick(TickEvent event) {
        if (mc.player == null) return;
        float health = mc.player.getHealth();

        if (lastHealth > 0 && health < lastHealth) {
            combo = 0;
        }

        if (mc.player.swinging && !wasSwinging && mc.crosshairPickEntity != null) {
            combo++;
            if (combo > maxCombo) maxCombo = combo;
            lastComboTime = System.currentTimeMillis();
        }

        wasSwinging = mc.player.swinging;
        lastHealth = health;
    }

    @Override
    public void render(GuiGraphics graphics, float tickDelta) {
        long elapsed = System.currentTimeMillis() - lastComboTime;
        if (combo == 0 && elapsed > displayTime.getValue() * 1000) return;
        String text = "Combo: " + combo;
        if (showMax.getValue()) text += " (Max: " + maxCombo + ")";
        drawText(graphics, text, getX(), getY());
    }
}
