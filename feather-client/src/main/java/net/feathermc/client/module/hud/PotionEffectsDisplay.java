package net.feathermc.client.module.hud;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.world.effect.MobEffectInstance;

public class PotionEffectsDisplay extends HudModule {
    private final BooleanSetting showAmplifier = addSetting(new BooleanSetting("Show Amplifier", true));

    public PotionEffectsDisplay() {
        super("Potion Effects", "Shows active status effects and duration", 5, 245);
    }

    @Override
    public void render(GuiGraphics graphics, float tickDelta) {
        if (mc.player == null) return;
        int x = getX();
        int y = getY();

        for (MobEffectInstance effect : mc.player.getActiveEffects()) {
            String name = I18n.get(effect.getEffect().value().getDescriptionId());
            if (showAmplifier.getValue() && effect.getAmplifier() > 0) {
                name += " " + toRoman(effect.getAmplifier() + 1);
            }
            String duration = formatDuration(effect.getDuration());
            int color = effect.getEffect().value().getColor();
            graphics.drawString(mc.font, name + " " + duration, x, y, color | 0xFF000000, false);
            y += 12;
        }
    }

    private String formatDuration(int ticks) {
        if (ticks < 0) return "∞";
        int secs = ticks / 20;
        int m = secs / 60;
        int s = secs % 60;
        return String.format("%d:%02d", m, s);
    }

    private String toRoman(int num) {
        return switch (num) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> String.valueOf(num);
        };
    }
}
