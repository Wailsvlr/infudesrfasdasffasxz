package net.feathermc.client.module.hud;

import net.minecraft.client.gui.GuiGraphics;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class ClockDisplay extends HudModule {
    private final ModeSetting mode = addSetting(new ModeSetting("Mode", "Real Time", "Real Time", "In-Game Time"));
    private final BooleanSetting use24h = addSetting(new BooleanSetting("24-Hour Format", true));

    public ClockDisplay() {
        super("Clock", "Shows real-world or in-game time", 5, 339);
    }

    @Override
    public void render(GuiGraphics graphics, float tickDelta) {
        String text;
        if (mode.getValue().equals("In-Game Time") && mc.level != null) {
            long dayTime = mc.level.getDayTime() % 24000;
            int hours = (int) ((dayTime / 1000 + 6) % 24);
            int minutes = (int) ((dayTime % 1000) * 60 / 1000);
            if (use24h.getValue()) {
                text = String.format("%02d:%02d", hours, minutes);
            } else {
                String ampm = hours >= 12 ? "PM" : "AM";
                int h12 = hours % 12;
                if (h12 == 0) h12 = 12;
                text = String.format("%d:%02d %s", h12, minutes, ampm);
            }
        } else {
            DateTimeFormatter fmt = use24h.getValue()
                    ? DateTimeFormatter.ofPattern("HH:mm:ss")
                    : DateTimeFormatter.ofPattern("h:mm:ss a");
            text = LocalTime.now().format(fmt);
        }
        drawText(graphics, text, getX(), getY());
    }
}
