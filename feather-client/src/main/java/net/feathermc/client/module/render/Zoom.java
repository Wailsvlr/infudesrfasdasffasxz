package net.feathermc.client.module.render;

import net.feathermc.client.event.Subscribe;
import net.feathermc.client.event.events.MouseScrollEvent;
import net.feathermc.client.event.events.TickEvent;
import net.feathermc.client.module.Module;
import org.lwjgl.glfw.GLFW;

public class Zoom extends Module {
    private final NumberSetting zoomLevel = addSetting(new NumberSetting("Zoom Level", 4.0, 1.0, 10.0, 0.5));
    private final BooleanSetting smoothZoom = addSetting(new BooleanSetting("Smooth Zoom", true));
    private final BooleanSetting scrollAdjust = addSetting(new BooleanSetting("Scroll Adjust", true));
    private final BooleanSetting smoothCamera = addSetting(new BooleanSetting("Cinematic Camera", false));

    private double originalFov;
    private double currentFov;
    private boolean wasSmooth;

    public Zoom() {
        super("Zoom", "OptiFine-style camera zoom", Category.RENDER, GLFW.GLFW_KEY_C);
    }

    @Override
    protected void onEnable() {
        if (mc.options == null) return;
        originalFov = mc.options.fov().get();
        currentFov = originalFov;
        wasSmooth = mc.options.smoothCamera;
        if (smoothCamera.getValue()) {
            mc.options.smoothCamera = true;
        }
    }

    @Override
    protected void onDisable() {
        if (mc.options == null) return;
        mc.options.fov().set((int) originalFov);
        mc.options.smoothCamera = wasSmooth;
    }

    @Subscribe
    public void onTick(TickEvent event) {
        if (mc.options == null) return;
        double targetFov = originalFov / zoomLevel.getValue();
        if (smoothZoom.getValue()) {
            currentFov = currentFov + (targetFov - currentFov) * 0.3;
        } else {
            currentFov = targetFov;
        }
    }

    @Subscribe
    public void onScroll(MouseScrollEvent event) {
        if (!scrollAdjust.getValue()) return;
        double newLevel = zoomLevel.getValue() + event.delta * 0.5;
        newLevel = Math.max(zoomLevel.getMin(), Math.min(zoomLevel.getMax(), newLevel));
        zoomLevel.setValue(newLevel);
        event.cancel();
    }

    public double getOriginalFov() {
        return originalFov;
    }

    public double getCurrentFov() {
        return currentFov;
    }
}
