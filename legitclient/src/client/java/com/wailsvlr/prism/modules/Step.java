package com.wailsvlr.prism.modules;

import com.wailsvlr.prism.module.Category;
import com.wailsvlr.prism.module.Module;
import com.wailsvlr.prism.setting.NumberSetting;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

public final class Step extends Module {

    private final NumberSetting height;
    private double originalStep = 0.6;

    public Step() {
        super("Step", "Step up blocks instantly", Category.MISC);
        height = add(new NumberSetting("Height", "Maximum step height", 1.0, 0.6, 2.5, 0.1, " blocks"));
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        AttributeInstance attr = mc.player.getAttribute(Attributes.STEP_HEIGHT);
        if (attr != null && attr.getBaseValue() != height.get()) {
            attr.setBaseValue(height.get());
        }
    }

    @Override
    protected void onEnable() {
        if (mc.player != null) {
            AttributeInstance attr = mc.player.getAttribute(Attributes.STEP_HEIGHT);
            if (attr != null) originalStep = attr.getBaseValue();
        }
    }

    @Override
    protected void onDisable() {
        if (mc.player != null) {
            AttributeInstance attr = mc.player.getAttribute(Attributes.STEP_HEIGHT);
            if (attr != null) attr.setBaseValue(originalStep);
        }
    }

    @Override
    public String getInfo() { return String.format("%.1f", height.get()); }
}
