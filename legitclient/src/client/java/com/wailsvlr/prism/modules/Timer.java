package com.wailsvlr.prism.modules;

import com.wailsvlr.prism.module.Category;
import com.wailsvlr.prism.module.Module;
import com.wailsvlr.prism.setting.NumberSetting;

public final class Timer extends Module {

    private static Timer instance;
    private final NumberSetting speed;

    public Timer() {
        super("Timer", "Changes game tick speed", Category.MISC);
        instance = this;
        speed = add(new NumberSetting("Speed", "Tick speed multiplier", 1.5, 0.1, 5.0, 0.1, "x"));
    }

    public static float getMultiplier() {
        if (instance == null || !instance.isEnabled()) return 1.0f;
        return instance.speed.getFloat();
    }

    @Override
    public String getInfo() { return String.format("%.1fx", speed.get()); }
}
