package net.feathermc.client.module.hud;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.PlayerInfo;

public class PingDisplay extends HudModule {
    public PingDisplay() {
        super("Ping Display", "Shows latency to the server", 5, 79);
    }

    @Override
    public void render(GuiGraphics graphics, float tickDelta) {
        if (mc.player == null || mc.getConnection() == null) return;
        PlayerInfo info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
        int ping = info != null ? info.getLatency() : -1;
        String text = ping >= 0 ? "Ping: " + ping + "ms" : "Ping: N/A";
        drawText(graphics, text, getX(), getY());
    }
}
