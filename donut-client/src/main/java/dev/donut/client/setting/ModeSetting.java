package dev.donut.client.setting;

public class ModeSetting extends Setting<String> {
    private final String[] modes;

    public ModeSetting(String name, String defaultMode, String... modes) {
        super(name, defaultMode);
        this.modes = modes;
    }

    public String[] getModes() { return modes; }

    public boolean is(String mode) {
        return getValue().equalsIgnoreCase(mode);
    }

    public void cycle() {
        for (int i = 0; i < modes.length; i++) {
            if (modes[i].equalsIgnoreCase(getValue())) {
                setValue(modes[(i + 1) % modes.length]);
                return;
            }
        }
    }
}
