package net.feathermc.client.module.visual;

import net.feathermc.client.module.Module;

public class Animations extends Module {
    private final ModeSetting mode = addSetting(new ModeSetting("Mode", "1.7", "1.7", "Smooth", "Spin"));
    private final NumberSetting speed = addSetting(new NumberSetting("Speed", 1.0, 0.5, 3.0, 0.1));
    private final BooleanSetting oldSwing = addSetting(new BooleanSetting("Old Swing", true));

    public Animations() {
        super("Animations", "Custom attack and item animations", Category.VISUAL);
    }

    public String getMode() {
        return mode.getValue();
    }

    public double getSpeed() {
        return speed.getValue();
    }

    public boolean useOldSwing() {
        return oldSwing.getValue();
    }
}
