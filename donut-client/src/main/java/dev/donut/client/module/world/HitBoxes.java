package dev.donut.client.module.world;

import dev.donut.client.module.Category;
import dev.donut.client.module.Module;
import dev.donut.client.setting.NumberSetting;

public class HitBoxes extends Module {
    private final NumberSetting size = addSetting(new NumberSetting("Size", 0.4, 0.0, 2.0, 0.1));

    public HitBoxes() {
        super("HitBoxes", "Expands entity hitboxes", Category.WORLD);
    }

    public double getSize() {
        return size.getValue();
    }
}
