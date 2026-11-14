package net.feathermc.client.module;

import net.feathermc.client.FeatherClient;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public abstract class Module {
    private final String name;
    private final String description;
    private final Category category;
    private boolean enabled;
    private int keyBind;
    private final List<Setting<?>> settings = new ArrayList<>();

    protected static final Minecraft mc = Minecraft.getInstance();

    public Module(String name, String description, Category category, int keyBind) {
        this.name = name;
        this.description = description;
        this.category = category;
        this.keyBind = keyBind;
    }

    public Module(String name, String description, Category category) {
        this(name, description, category, GLFW.GLFW_KEY_UNKNOWN);
    }

    public void toggle() {
        setEnabled(!enabled);
    }

    public void setEnabled(boolean enabled) {
        if (this.enabled == enabled) return;
        this.enabled = enabled;
        if (enabled) {
            onEnable();
            FeatherClient.getInstance().getEventBus().register(this);
        } else {
            FeatherClient.getInstance().getEventBus().unregister(this);
            onDisable();
        }
    }

    protected void onEnable() {}
    protected void onDisable() {}

    public String getName() { return name; }
    public String getDescription() { return description; }
    public Category getCategory() { return category; }
    public boolean isEnabled() { return enabled; }
    public int getKeyBind() { return keyBind; }
    public void setKeyBind(int keyBind) { this.keyBind = keyBind; }
    public List<Setting<?>> getSettings() { return settings; }

    protected <T extends Setting<?>> T addSetting(T setting) {
        settings.add(setting);
        return setting;
    }

    public enum Category {
        HUD("HUD"),
        RENDER("Render"),
        MOVEMENT("Movement"),
        VISUAL("Visual"),
        MISC("Misc");

        private final String displayName;
        Category(String displayName) { this.displayName = displayName; }
        public String getDisplayName() { return displayName; }
    }

    public static class Setting<T> {
        private final String name;
        private T value;
        private final T defaultValue;

        public Setting(String name, T defaultValue) {
            this.name = name;
            this.value = defaultValue;
            this.defaultValue = defaultValue;
        }

        public String getName() { return name; }
        public T getValue() { return value; }
        public void setValue(T value) { this.value = value; }
        public T getDefaultValue() { return defaultValue; }
    }

    public static class BooleanSetting extends Setting<Boolean> {
        public BooleanSetting(String name, boolean defaultValue) {
            super(name, defaultValue);
        }
    }

    public static class NumberSetting extends Setting<Double> {
        private final double min, max, step;

        public NumberSetting(String name, double defaultValue, double min, double max, double step) {
            super(name, defaultValue);
            this.min = min;
            this.max = max;
            this.step = step;
        }

        public double getMin() { return min; }
        public double getMax() { return max; }
        public double getStep() { return step; }
    }

    public static class ColorSetting extends Setting<Integer> {
        public ColorSetting(String name, int defaultValue) {
            super(name, defaultValue);
        }
    }

    public static class ModeSetting extends Setting<String> {
        private final String[] modes;

        public ModeSetting(String name, String defaultValue, String... modes) {
            super(name, defaultValue);
            this.modes = modes;
        }

        public String[] getModes() { return modes; }

        public void cycle() {
            for (int i = 0; i < modes.length; i++) {
                if (modes[i].equals(getValue())) {
                    setValue(modes[(i + 1) % modes.length]);
                    return;
                }
            }
        }
    }
}
