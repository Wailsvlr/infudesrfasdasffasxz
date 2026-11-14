package net.feathermc.client.module.render;

import net.feathermc.client.module.Module;
import org.lwjgl.glfw.GLFW;

public class Fullbright extends Module {
    private final NumberSetting brightness = addSetting(new NumberSetting("Brightness", 16.0, 1.0, 16.0, 0.5));
    private double originalGamma;

    public Fullbright() {
        super("Fullbright", "Maximum brightness — see in the dark", Category.RENDER, GLFW.GLFW_KEY_H);
    }

    @Override
    protected void onEnable() {
        if (mc.options == null) return;
        originalGamma = mc.options.gamma().get();
        mc.options.gamma().set(brightness.getValue());
    }

    @Override
    protected void onDisable() {
        if (mc.options == null) return;
        mc.options.gamma().set(originalGamma);
    }

    public double getBrightness() {
        return brightness.getValue();
    }
}
