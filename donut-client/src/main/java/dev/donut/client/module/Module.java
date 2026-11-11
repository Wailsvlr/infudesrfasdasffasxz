package dev.donut.client.module;

import dev.donut.client.DonutClient;
import dev.donut.client.setting.Setting;
import net.minecraft.client.MinecraftClient;

import java.util.ArrayList;
import java.util.List;

public abstract class Module {
    protected static final MinecraftClient mc = MinecraftClient.getInstance();

    private final String name;
    private final String description;
    private final Category category;
    private boolean enabled;
    private int keyBind;
    private final List<Setting<?>> settings = new ArrayList<>();

    public Module(String name, String description, Category category, int keyBind) {
        this.name = name;
        this.description = description;
        this.category = category;
        this.keyBind = keyBind;
    }

    public Module(String name, String description, Category category) {
        this(name, description, category, -1);
    }

    protected <T extends Setting<?>> T addSetting(T setting) {
        settings.add(setting);
        return setting;
    }

    public void toggle() {
        if (enabled) disable();
        else enable();
    }

    public void enable() {
        enabled = true;
        DonutClient.INSTANCE.getEventBus().register(this);
        onEnable();
    }

    public void disable() {
        enabled = false;
        DonutClient.INSTANCE.getEventBus().unregister(this);
        onDisable();
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

    public void setEnabled(boolean enabled) {
        if (enabled && !this.enabled) enable();
        else if (!enabled && this.enabled) disable();
    }

    protected boolean nullCheck() {
        return mc.player == null || mc.world == null;
    }
}
