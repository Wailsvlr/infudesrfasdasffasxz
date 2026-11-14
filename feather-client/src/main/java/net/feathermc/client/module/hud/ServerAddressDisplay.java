package net.feathermc.client.module.hud;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ServerData;

public class ServerAddressDisplay extends HudModule {
    public ServerAddressDisplay() {
        super("Server Address", "Shows current server IP", 5, 365);
    }

    @Override
    public void render(GuiGraphics graphics, float tickDelta) {
        ServerData server = mc.getCurrentServer();
        String text = server != null ? "Server: " + server.ip : "Singleplayer";
        drawText(graphics, text, getX(), getY());
    }
}
