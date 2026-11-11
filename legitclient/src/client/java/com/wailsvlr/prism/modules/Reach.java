package com.wailsvlr.prism.modules;

import com.wailsvlr.prism.module.Category;
import com.wailsvlr.prism.module.Module;
import com.wailsvlr.prism.setting.NumberSetting;

public final class Reach extends Module {
    private static Reach instance;
    private final NumberSetting distance;

    public Reach() {
        super("Reach", "Extends targeting distance (anticheat-safe)", Category.COMBAT);
        instance = this;
        distance = add(new NumberSetting("Distance", "Max targeting distance", 3.2, 3.0, 3.5, 0.05, " blocks"));
    }

    public static double distance(double vanilla) {
        if (instance == null || !instance.isEnabled()) return vanilla;
        return Math.max(vanilla, instance.distance.get());
    }

    @Override
    public String getInfo() { return distance.display(); }
}
