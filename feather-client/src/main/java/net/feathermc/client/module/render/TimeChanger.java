package net.feathermc.client.module.render;

import net.feathermc.client.module.Module;

public class TimeChanger extends Module {
    private final NumberSetting time = addSetting(new NumberSetting("Time", 6000.0, 0.0, 24000.0, 100.0));
    private final BooleanSetting cycle = addSetting(new BooleanSetting("Cycle", false));
    private final NumberSetting cycleSpeed = addSetting(new NumberSetting("Cycle Speed", 1.0, 0.1, 10.0, 0.1));

    private double currentTime;

    public TimeChanger() {
        super("Time Changer", "Override world time for rendering", Category.RENDER);
    }

    @Override
    protected void onEnable() {
        currentTime = time.getValue();
    }

    public long getWorldTime() {
        if (cycle.getValue()) {
            currentTime += cycleSpeed.getValue() * 10;
            if (currentTime >= 24000) currentTime -= 24000;
            return (long) currentTime;
        }
        return time.getValue().longValue();
    }
}
