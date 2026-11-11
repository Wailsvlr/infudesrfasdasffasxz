package dev.donut.client.module.render;

import dev.donut.client.event.EventBus;
import dev.donut.client.event.events.TickEvent;
import dev.donut.client.module.Category;
import dev.donut.client.module.Module;

public class FullBright extends Module {
    private double oldGamma = -1;

    public FullBright() {
        super("FullBright", "Maximum brightness", Category.RENDER);
    }

    @Override
    protected void onEnable() {
        oldGamma = mc.options.getGamma().getValue();
        mc.options.getGamma().setValue(16.0);
    }

    @Override
    protected void onDisable() {
        if (oldGamma >= 0) {
            mc.options.getGamma().setValue(oldGamma);
        }
    }

    @EventBus.Subscribe
    public void onTick(TickEvent event) {
        if (nullCheck()) return;
        mc.options.getGamma().setValue(16.0);
    }
}
