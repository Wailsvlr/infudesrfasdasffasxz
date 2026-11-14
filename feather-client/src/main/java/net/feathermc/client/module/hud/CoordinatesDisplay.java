package net.feathermc.client.module.hud;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;

public class CoordinatesDisplay extends HudModule {
    private final NumberSetting decimals = addSetting(new NumberSetting("Decimals", 1, 0, 4, 1));
    private final BooleanSetting showDirection = addSetting(new BooleanSetting("Show Direction", true));
    private final BooleanSetting showBiome = addSetting(new BooleanSetting("Show Biome", false));

    public CoordinatesDisplay() {
        super("Coordinates", "Shows player coordinates", 5, 31);
    }

    @Override
    public void render(GuiGraphics graphics, float tickDelta) {
        Player player = mc.player;
        if (player == null) return;
        int d = decimals.getValue().intValue();
        String fmt = "%." + d + "f";
        int y = getY();
        drawText(graphics, "X: " + String.format(fmt, player.getX()), getX(), y);
        y += 12;
        drawText(graphics, "Y: " + String.format(fmt, player.getY()), getX(), y);
        y += 12;
        drawText(graphics, "Z: " + String.format(fmt, player.getZ()), getX(), y);
        if (showDirection.getValue()) {
            y += 12;
            drawText(graphics, "Facing: " + getCardinal(player.getYRot()), getX(), y);
        }
    }

    private String getCardinal(float yaw) {
        yaw = ((yaw % 360) + 360) % 360;
        if (yaw >= 315 || yaw < 45) return "South (+Z)";
        if (yaw >= 45 && yaw < 135) return "West (-X)";
        if (yaw >= 135 && yaw < 225) return "North (-Z)";
        return "East (+X)";
    }
}
