package com.wailsvlr.prism.modules;

import com.wailsvlr.prism.module.Category;
import com.wailsvlr.prism.module.Module;
import com.wailsvlr.prism.setting.BoolSetting;

public final class NoSlow extends Module {

    private static NoSlow instance;
    private final BoolSetting items;
    private final BoolSetting sneaking;
    private final BoolSetting webs;

    public NoSlow() {
        super("NoSlow", "Prevents movement slowdowns", Category.MISC);
        instance = this;
        items = add(new BoolSetting("Items", "Prevent item use slowdown", true));
        sneaking = add(new BoolSetting("Sneaking", "Prevent sneak slowdown", false));
        webs = add(new BoolSetting("Webs", "Reduce web slowdown", false));
    }

    public static boolean shouldCancelItemSlow() {
        return instance != null && instance.isEnabled() && instance.items.isOn();
    }

    public static boolean shouldCancelSneakSlow() {
        return instance != null && instance.isEnabled() && instance.sneaking.isOn();
    }

    public static boolean shouldCancelWebSlow() {
        return instance != null && instance.isEnabled() && instance.webs.isOn();
    }

    @Override
    public String getInfo() { return null; }
}
