package com.wailsvlr.prism.modules;

import com.wailsvlr.prism.module.Category;
import com.wailsvlr.prism.module.Module;

public final class FastPlace extends Module {

    private static FastPlace instance;

    public FastPlace() {
        super("FastPlace", "Removes block placement delay", Category.MISC);
        instance = this;
    }

    public static boolean isActive() {
        return instance != null && instance.isEnabled();
    }

    @Override
    public String getInfo() { return null; }
}
