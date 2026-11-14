package net.feathermc.client.gui;

import net.feathermc.client.FeatherClient;
import net.feathermc.client.event.Subscribe;
import net.feathermc.client.event.events.RenderHudEvent;
import net.feathermc.client.module.Module;
import net.feathermc.client.module.hud.HudModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

public class FeatherHudRenderer {
    public FeatherHudRenderer() {
        FeatherClient.getInstance().getEventBus().register(this);
    }

    @Subscribe(priority = 100)
    public void onRenderHud(RenderHudEvent event) {
        if (Minecraft.getInstance().options.hideGui) return;
        for (Module module : FeatherClient.getInstance().getModuleManager().getModules()) {
            if (module instanceof HudModule hud && module.isEnabled()) {
                hud.render(event.graphics, event.tickDelta);
            }
        }
    }
}
