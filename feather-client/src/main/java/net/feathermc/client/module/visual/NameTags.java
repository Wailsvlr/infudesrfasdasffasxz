package net.feathermc.client.module.visual;

import net.feathermc.client.module.Module;

public class NameTags extends Module {
    private final NumberSetting scale = addSetting(new NumberSetting("Scale", 2.0, 1.0, 5.0, 0.5));
    private final BooleanSetting showHealth = addSetting(new BooleanSetting("Show Health", true));
    private final BooleanSetting showArmor = addSetting(new BooleanSetting("Show Armor", true));

    public NameTags() {
        super("Name Tags", "Enhanced name tags with health and armor", Category.VISUAL);
    }

    public double getScale() {
        return scale.getValue();
    }

    public boolean shouldShowHealth() {
        return showHealth.getValue();
    }

    public boolean shouldShowArmor() {
        return showArmor.getValue();
    }
}
