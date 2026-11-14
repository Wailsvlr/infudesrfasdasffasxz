package net.feathermc.client.module.hud;

import net.minecraft.client.gui.GuiGraphics;

public class FPSDisplay extends HudModule {
    public FPSDisplay() {
        super("FPS Display", "Shows current frames per second", 5, 5);
    }

    @Override
    public void render(GuiGraphics graphics, float tickDelta) {
        drawText(graphics, "FPS: " + mc.getFps(), getX(), getY());
    }
}
