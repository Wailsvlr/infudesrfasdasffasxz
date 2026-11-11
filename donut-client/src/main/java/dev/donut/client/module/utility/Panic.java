package dev.donut.client.module.utility;

import dev.donut.client.DonutClient;
import dev.donut.client.module.Category;
import dev.donut.client.module.Module;

public class Panic extends Module {
    public Panic() {
        super("Panic", "Disables all modules", Category.UTILITY);
    }

    @Override
    public void onEnable() {
        super.onEnable();
        for (Module m : DonutClient.INSTANCE.getModules().getModules()) {
            if (m != this && m.isEnabled()) {
                m.setEnabled(false);
            }
        }
        setEnabled(false);
    }
}
