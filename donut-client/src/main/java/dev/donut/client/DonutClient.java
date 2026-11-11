package dev.donut.client;

import dev.donut.client.event.EventBus;
import dev.donut.client.gui.hud.HUDRenderer;
import dev.donut.client.module.ModuleManager;
import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DonutClient implements ClientModInitializer {
    public static final String NAME = "Donut Client";
    public static final String VERSION = "1.0.0";
    public static final Logger LOGGER = LoggerFactory.getLogger(NAME);
    public static DonutClient INSTANCE;

    private EventBus eventBus;
    private ModuleManager moduleManager;
    private HUDRenderer hudRenderer;

    @Override
    public void onInitializeClient() {
        INSTANCE = this;
        LOGGER.info("{} v{} initializing...", NAME, VERSION);

        eventBus = new EventBus();
        moduleManager = new ModuleManager();
        hudRenderer = new HUDRenderer();

        moduleManager.init();
        eventBus.register(hudRenderer);

        LOGGER.info("{} loaded — {} modules", NAME, moduleManager.getModules().size());
    }

    public EventBus getEventBus() { return eventBus; }
    public ModuleManager getModules() { return moduleManager; }
    public HUDRenderer getHud() { return hudRenderer; }
}
