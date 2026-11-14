package net.feathermc.client.module.visual;

import net.feathermc.client.module.Module;

public class HitColor extends Module {
    private final ColorSetting color = addSetting(new ColorSetting("Color", 0xFFFF0000));

    public HitColor() {
        super("Hit Color", "Custom damage tint color on entities", Category.VISUAL);
    }

    public int getColor() {
        return color.getValue();
    }

    public float getRed() {
        return ((color.getValue() >> 16) & 0xFF) / 255f;
    }

    public float getGreen() {
        return ((color.getValue() >> 8) & 0xFF) / 255f;
    }

    public float getBlue() {
        return (color.getValue() & 0xFF) / 255f;
    }

    public float getAlpha() {
        return ((color.getValue() >> 24) & 0xFF) / 255f;
    }
}
