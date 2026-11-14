package net.feathermc.client.module.visual;

import net.feathermc.client.module.Module;

public class BossBar extends Module {
    private final BooleanSetting hide = addSetting(new BooleanSetting("Hide", false));
    private final NumberSetting scale = addSetting(new NumberSetting("Scale", 1.0, 0.5, 2.0, 0.1));
    private final ModeSetting position = addSetting(new ModeSetting("Position", "Top", "Top", "Bottom"));

    public BossBar() {
        super("Boss Bar", "Boss bar display customization", Category.VISUAL);
    }

    public boolean shouldHide() {
        return hide.getValue();
    }

    public double getScale() {
        return scale.getValue();
    }

    public String getPosition() {
        return position.getValue();
    }
}
