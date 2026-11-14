package net.feathermc.client.module.hud;

import net.minecraft.client.gui.GuiGraphics;

public class DirectionDisplay extends HudModule {
    private final BooleanSetting showDegrees = addSetting(new BooleanSetting("Show Degrees", true));

    public DirectionDisplay() {
        super("Direction", "Shows cardinal direction and yaw", 5, 300);
    }

    @Override
    public void render(GuiGraphics graphics, float tickDelta) {
        if (mc.player == null) return;
        float yaw = ((mc.player.getYRot() % 360) + 360) % 360;
        String cardinal = getDirection(yaw);
        String text = showDegrees.getValue()
                ? cardinal + " (" + String.format("%.1f", yaw) + "°)"
                : cardinal;
        drawText(graphics, text, getX(), getY());
    }

    private String getDirection(float yaw) {
        String[] dirs = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
        int index = Math.round(yaw / 45f) & 7;
        return dirs[index];
    }
}
