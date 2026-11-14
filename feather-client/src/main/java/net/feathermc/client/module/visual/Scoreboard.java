package net.feathermc.client.module.visual;

import net.feathermc.client.module.Module;

public class Scoreboard extends Module {
    private final BooleanSetting noNumbers = addSetting(new BooleanSetting("No Numbers", true));
    private final NumberSetting scale = addSetting(new NumberSetting("Scale", 1.0, 0.5, 2.0, 0.1));

    public Scoreboard() {
        super("Scoreboard", "Scoreboard display customization", Category.VISUAL);
    }

    public boolean hideNumbers() {
        return noNumbers.getValue();
    }

    public double getScale() {
        return scale.getValue();
    }
}
