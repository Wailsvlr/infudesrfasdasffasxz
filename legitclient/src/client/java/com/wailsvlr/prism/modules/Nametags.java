package com.wailsvlr.prism.modules;

import com.wailsvlr.prism.module.Category;
import com.wailsvlr.prism.module.Module;
import com.wailsvlr.prism.setting.BoolSetting;
import com.wailsvlr.prism.setting.NumberSetting;

public final class Nametags extends Module {

    private static Nametags instance;
    private final NumberSetting scale;
    private final BoolSetting health;
    private final BoolSetting distance;
    private final BoolSetting armor;

    public Nametags() {
        super("Nametags", "Enhanced nametag rendering", Category.RENDER);
        instance = this;
        scale = add(new NumberSetting("Scale", "Nametag size multiplier", 1.5, 0.5, 4.0, 0.1, "x"));
        health = add(new BoolSetting("Health", "Show health next to name", true));
        distance = add(new BoolSetting("Distance", "Show distance", true));
        armor = add(new BoolSetting("Armor", "Show armor value", false));
    }

    public static boolean isActive() {
        return instance != null && instance.isEnabled();
    }

    public static float getScale() {
        return instance != null ? instance.scale.getFloat() : 1.0f;
    }

    public static boolean showHealth() {
        return instance != null && instance.health.isOn();
    }

    public static boolean showDistance() {
        return instance != null && instance.distance.isOn();
    }

    public static boolean showArmor() {
        return instance != null && instance.armor.isOn();
    }

    @Override
    public String getInfo() { return null; }
}
