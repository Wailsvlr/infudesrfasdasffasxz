package com.wailsvlr.prism.modules;

import com.wailsvlr.prism.module.Category;
import com.wailsvlr.prism.module.Module;
import com.wailsvlr.prism.setting.ModeSetting;

public final class Sprint extends Module {

    private final ModeSetting mode;

    public Sprint() {
        super("Sprint", "Automatically sprints", Category.MISC);
        mode = add(new ModeSetting("Mode", "Sprint behavior", "Legit", "Legit", "Rage"));
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null) return;
        if (mc.player.isFallFlying() || mc.player.isPassenger()) return;
        if (mc.player.getFoodData().getFoodLevel() <= 6) return;

        if (mode.is("Legit")) {
            if (mc.player.input.forwardImpulse > 0 && !mc.player.horizontalCollision
                    && !mc.player.isCrouching() && !mc.player.isUsingItem()) {
                mc.player.setSprinting(true);
            }
        } else {
            if (mc.player.input.forwardImpulse != 0 || mc.player.input.leftImpulse != 0) {
                mc.player.setSprinting(true);
            }
        }
    }

    @Override
    public String getInfo() { return mode.get(); }
}
