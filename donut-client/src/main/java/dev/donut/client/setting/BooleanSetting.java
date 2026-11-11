package dev.donut.client.setting;

public class BooleanSetting extends Setting<Boolean> {
    public BooleanSetting(String name, boolean value) {
        super(name, value);
    }

    public boolean isEnabled() { return getValue(); }
    public void toggle() { setValue(!getValue()); }
}
