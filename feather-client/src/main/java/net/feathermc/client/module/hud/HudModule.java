package net.feathermc.client.module.hud;

import net.feathermc.client.module.Module;
import net.minecraft.client.gui.GuiGraphics;

public abstract class HudModule extends Module {
    protected int x, y;
    private final NumberSetting posX;
    private final NumberSetting posY;
    protected final ColorSetting textColor;
    protected final BooleanSetting background;

    public HudModule(String name, String description, int defaultX, int defaultY) {
        super(name, description, Category.HUD);
        this.x = defaultX;
        this.y = defaultY;
        posX = addSetting(new NumberSetting("X", defaultX, 0, 3840, 1));
        posY = addSetting(new NumberSetting("Y", defaultY, 0, 2160, 1));
        textColor = addSetting(new ColorSetting("Text Color", 0xFFFFFFFF));
        background = addSetting(new BooleanSetting("Background", true));
    }

    public abstract void render(GuiGraphics graphics, float tickDelta);

    protected void drawText(GuiGraphics graphics, String text, int x, int y) {
        if (background.getValue()) {
            int width = mc.font.width(text) + 4;
            graphics.fill(x - 2, y - 2, x + width, y + 10, 0x80000000);
        }
        graphics.drawString(mc.font, text, x, y, textColor.getValue(), false);
    }

    protected int getX() { return posX.getValue().intValue(); }
    protected int getY() { return posY.getValue().intValue(); }
}
