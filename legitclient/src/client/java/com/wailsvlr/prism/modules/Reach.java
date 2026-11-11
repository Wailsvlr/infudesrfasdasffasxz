package com.wailsvlr.prism.modules;

import com.wailsvlr.prism.module.Category;
import com.wailsvlr.prism.module.Module;
import com.wailsvlr.prism.setting.NumberSetting;

public final class Reach extends Module {
    private static Reach instance;
    private final NumberSetting distance;
    private final NumberSetting jitter;

    public Reach() {
        super("Reach", "Extends targeting distance within anticheat-safe range", Category.COMBAT);
        instance = this;
        distance = add(new NumberSetting("Distance", "Max targeting distance", 3.4, 3.0, 3.6, 0.05, " blocks"));
        jitter = add(new NumberSetting("Jitter", "Random variation per check", 0.05, 0.0, 0.15, 0.01, " blocks"));
    }

    public static double distance(double vanilla) {
        if (instance == null || !instance.isEnabled()) return vanilla;
        double base = instance.distance.get();
        double j = instance.jitter.get();
        double actual = base + (Math.random() * 2 - 1) * j;
        return Math.max(vanilla, Math.max(vanilla, actual));
    }

    @Override
    public String getInfo() { return distance.display(); }
}
