package net.feathermc.client.module.render;

import net.feathermc.client.event.Subscribe;
import net.feathermc.client.event.events.TickEvent;
import net.feathermc.client.module.Module;
import org.lwjgl.glfw.GLFW;

public class Freelook extends Module {
    private final BooleanSetting invertY = addSetting(new BooleanSetting("Invert Y", false));

    private float cameraYaw;
    private float cameraPitch;
    private float playerYaw;
    private float playerPitch;
    private boolean active;

    public Freelook() {
        super("Freelook", "Rotate camera independently of player direction", Category.RENDER, GLFW.GLFW_KEY_LEFT_ALT);
    }

    @Override
    protected void onEnable() {
        if (mc.player == null) return;
        active = true;
        playerYaw = mc.player.getYRot();
        playerPitch = mc.player.getXRot();
        cameraYaw = playerYaw;
        cameraPitch = playerPitch;
    }

    @Override
    protected void onDisable() {
        active = false;
        if (mc.player == null) return;
        mc.player.setYRot(playerYaw);
        mc.player.setXRot(playerPitch);
        mc.player.yRotO = playerYaw;
        mc.player.xRotO = playerPitch;
    }

    @Subscribe
    public void onTick(TickEvent event) {
        if (!active || mc.player == null) return;
        mc.player.setYRot(playerYaw);
        mc.player.setXRot(playerPitch);
    }

    public void applyCameraRotation(double deltaX, double deltaY) {
        if (!active) return;
        cameraYaw += (float) (deltaX * 0.15);
        float pitchDelta = (float) (deltaY * 0.15) * (invertY.getValue() ? -1 : 1);
        cameraPitch = Math.max(-90.0f, Math.min(90.0f, cameraPitch + pitchDelta));
    }

    public boolean isFreelooking() {
        return isEnabled() && active;
    }

    public float getCameraYaw() { return cameraYaw; }
    public float getCameraPitch() { return cameraPitch; }
}
