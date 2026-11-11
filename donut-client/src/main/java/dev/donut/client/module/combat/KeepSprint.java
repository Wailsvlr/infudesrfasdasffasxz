package dev.donut.client.module.combat;

import dev.donut.client.module.Category;
import dev.donut.client.module.Module;
import dev.donut.client.setting.NumberSetting;

public class KeepSprint extends Module {
    private final NumberSetting slowdown = addSetting(new NumberSetting("Slowdown", 1.0, 0.0, 1.0, 0.05));

    public KeepSprint() {
        super("KeepSprint", "Keeps sprinting after attacking", Category.COMBAT);
    }

    public double getSlowdown() { return slowdown.getValue(); }
}
