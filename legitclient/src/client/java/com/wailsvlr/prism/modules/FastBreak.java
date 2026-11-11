package com.wailsvlr.prism.modules;

import com.wailsvlr.prism.module.Category;
import com.wailsvlr.prism.module.Module;
import com.wailsvlr.prism.setting.NumberSetting;

public final class FastBreak extends Module {

    private static FastBreak instance;
    private final NumberSetting multiplier;

    public FastBreak() {
        super("FastBreak", "Speeds up block breaking", Category.MISC);
        instance = this;
        multiplier = add(new NumberSetting("Speed", "Mining speed multiplier", 1.5, 1.0, 3.0, 0.1, "x"));
    }

    public static boolean isActive() {
        return instance != null && instance.isEnabled();
    }

    public static float getMultiplier() {
        if (instance == null || !instance.isEnabled()) return 1.0f;
        return instance.multiplier.getFloat();
    }

    @Override
    public String getInfo() { return String.format("%.1fx", multiplier.get()); }
}
