package net.feathermc.client.module.movement;

import net.feathermc.client.event.Subscribe;
import net.feathermc.client.event.events.TickEvent;
import net.feathermc.client.module.Module;
import org.lwjgl.glfw.GLFW;

public class ToggleSneak extends Module {
    private final ModeSetting mode = addSetting(new ModeSetting("Mode", "Toggle", "Toggle", "Hold"));
    private boolean sneakState;

    public ToggleSneak() {
        super("Toggle Sneak", "Toggle sneaking without holding the key", Category.MOVEMENT, GLFW.GLFW_KEY_Z);
    }

    @Override
    protected void onEnable() {
        sneakState = true;
    }

    @Override
    protected void onDisable() {
        sneakState = false;
        if (mc.player != null) {
            mc.player.setShiftKeyDown(false);
        }
    }

    @Subscribe
    public void onTick(TickEvent event) {
        if (mc.player == null || mc.level == null) return;
        if (mc.screen != null) return;
        if (sneakState) {
            mc.player.setShiftKeyDown(true);
        }
    }

    public boolean isSneaking() {
        return isEnabled() && sneakState;
    }
}
