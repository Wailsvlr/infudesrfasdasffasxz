package com.wailsvlr.prism.modules;

import com.wailsvlr.prism.module.Category;
import com.wailsvlr.prism.module.Module;
import com.wailsvlr.prism.setting.BoolSetting;
import com.wailsvlr.prism.setting.NumberSetting;

public final class Chams extends Module {

    private static Chams instance;
    private final BoolSetting players;
    private final BoolSetting mobs;
    private final NumberSetting alpha;

    public Chams() {
        super("Chams", "See entities through walls", Category.RENDER);
        instance = this;
        players = add(new BoolSetting("Players", "Show players through walls", true));
        mobs = add(new BoolSetting("Mobs", "Show mobs through walls", false));
        alpha = add(new NumberSetting("Alpha", "Entity visibility through walls", 0.5, 0.1, 1.0, 0.1, ""));
    }

    public static boolean isActive() {
        return instance != null && instance.isEnabled();
    }

    public static boolean showPlayers() {
        return instance != null && instance.players.isOn();
    }

    public static boolean showMobs() {
        return instance != null && instance.mobs.isOn();
    }

    public static float getAlpha() {
        return instance != null ? instance.alpha.getFloat() : 0.5f;
    }

    @Override
    public String getInfo() { return null; }
}
