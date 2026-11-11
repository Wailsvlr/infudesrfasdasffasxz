package com.wailsvlr.prism.modules;

import com.wailsvlr.prism.module.Category;
import com.wailsvlr.prism.module.Module;
import com.wailsvlr.prism.setting.ModeSetting;
import net.minecraft.world.phys.Vec3;

public final class AntiVoid extends Module {

    private final ModeSetting mode;
    private Vec3 safePos;

    public AntiVoid() {
        super("AntiVoid", "Prevents falling into the void", Category.MISC);
        mode = add(new ModeSetting("Mode", "Void prevention method", "Jump", "Jump", "Save"));
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null) return;

        int minY = mc.level.getMinY();

        if (mc.player.onGround() && mc.player.getY() > minY + 3) {
            safePos = mc.player.position();
        }

        if (mc.player.getY() < minY) {
            if (mode.is("Jump")) {
                Vec3 v = mc.player.getDeltaMovement();
                mc.player.setDeltaMovement(v.x, 0.42, v.z);
            } else if (mode.is("Save") && safePos != null) {
                mc.player.setPos(safePos.x, safePos.y, safePos.z);
                mc.player.setDeltaMovement(Vec3.ZERO);
            }
        }
    }

    @Override
    protected void onDisable() { safePos = null; }

    @Override
    public String getInfo() { return mode.get(); }
}
