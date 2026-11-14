package net.feathermc.client.module.render;

import net.feathermc.client.event.Subscribe;
import net.feathermc.client.event.events.TickEvent;
import net.feathermc.client.module.Module;

public class MotionBlur extends Module {
    private final NumberSetting amount = addSetting(new NumberSetting("Amount", 5.0, 1.0, 10.0, 1.0));

    private float prevYaw, prevPitch;
    private float smoothYaw, smoothPitch;

    public MotionBlur() {
        super("Motion Blur", "Smooth camera motion blur effect", Category.RENDER);
    }

    @Override
    protected void onEnable() {
        if (mc.player != null) {
            prevYaw = mc.player.getYRot();
            prevPitch = mc.player.getXRot();
            smoothYaw = prevYaw;
            smoothPitch = prevPitch;
        }
    }

    @Subscribe
    public void onTick(TickEvent event) {
        if (mc.player == null) return;
        float currentYaw = mc.player.getYRot();
        float currentPitch = mc.player.getXRot();
        float factor = (float) (1.0 - (amount.getValue() / 12.0));
        smoothYaw = smoothYaw + (currentYaw - smoothYaw) * factor;
        smoothPitch = smoothPitch + (currentPitch - smoothPitch) * factor;
        prevYaw = currentYaw;
        prevPitch = currentPitch;
    }

    public float getBlurFactor() {
        return (float) (amount.getValue() / 10.0);
    }

    public float getSmoothYaw() { return smoothYaw; }
    public float getSmoothPitch() { return smoothPitch; }
}
