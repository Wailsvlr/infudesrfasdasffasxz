package net.feathermc.client;

import net.fabricmc.api.ClientModInitializer;
import net.feathermc.client.config.ConfigManager;
import net.feathermc.client.event.EventBus;
import net.feathermc.client.module.ModuleManager;
import net.feathermc.client.gui.FeatherHudRenderer;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FeatherClient implements ClientModInitializer {
    public static final String MOD_ID = "featherclient";
    public static final String NAME = "Feather Client";
    public static final Logger LOGGER = LoggerFactory.getLogger(NAME);

    private static FeatherClient instance;
    private EventBus eventBus;
    private ConfigManager configManager;
    private ModuleManager moduleManager;
    private FeatherHudRenderer hudRenderer;

    @Override
    public void onInitializeClient() {
        instance = this;
        LOGGER.info("Initializing {} v1.0.0", NAME);

        eventBus = new EventBus();
        configManager = new ConfigManager();
        moduleManager = new ModuleManager();
        hudRenderer = new FeatherHudRenderer();

        moduleManager.registerAll();
        configManager.load();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> configManager.save()));

        LOGGER.info("{} initialized — {} modules loaded", NAME, moduleManager.getModules().size());
    }

    public static FeatherClient getInstance() {
        return instance;
    }

    public static Minecraft mc() {
        return Minecraft.getInstance();
    }

    public EventBus getEventBus() {
        return eventBus;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public ModuleManager getModuleManager() {
        return moduleManager;
    }

    public FeatherHudRenderer getHudRenderer() {
        return hudRenderer;
    }
}
