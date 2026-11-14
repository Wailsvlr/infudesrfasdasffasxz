package net.feathermc.client.module.movement;

import net.feathermc.client.event.Subscribe;
import net.feathermc.client.event.events.TickEvent;
import net.feathermc.client.module.Module;
import org.lwjgl.glfw.GLFW;

public class ToggleSprint extends Module {
    private final ModeSetting mode = addSetting(new ModeSetting("Mode", "Toggle", "Toggle", "Hold"));

    public ToggleSprint() {
        super("Toggle Sprint", "Automatically sprint while moving forward", Category.MOVEMENT, GLFW.GLFW_KEY_V);
    }

    @Subscribe
    public void onTick(TickEvent event) {
        if (mc.player == null || mc.level == null) return;
        boolean forward = mc.player.input.forwardImpulse > 0;
        boolean canSprint = forward && !mc.player.isSprinting()
                && !mc.player.isShiftKeyDown()
                && !mc.player.isUsingItem()
                && mc.player.getFoodData().getFoodLevel() > 6;
        if (canSprint) {
            mc.player.setSprinting(true);
        }
    }

    public boolean isToggleMode() {
        return mode.getValue().equals("Toggle");
    }
}
