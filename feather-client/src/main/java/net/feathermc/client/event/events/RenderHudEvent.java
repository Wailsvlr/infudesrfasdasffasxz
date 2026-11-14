package net.feathermc.client.event.events;

import net.minecraft.client.gui.GuiGraphics;

public class RenderHudEvent {
    public final GuiGraphics graphics;
    public final float tickDelta;

    public RenderHudEvent(GuiGraphics graphics, float tickDelta) {
        this.graphics = graphics;
        this.tickDelta = tickDelta;
    }
}
