package net.feathermc.client.config;

import com.google.gson.*;
import net.feathermc.client.FeatherClient;
import net.feathermc.client.module.Module;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.nio.file.*;

public class ConfigManager {
    private static final Path CONFIG_DIR = FabricLoader.getInstance().getConfigDir().resolve("feather");
    private static final Path CONFIG_FILE = CONFIG_DIR.resolve("modules.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public void load() {
        if (!Files.exists(CONFIG_FILE)) return;
        try {
            JsonObject root = JsonParser.parseString(Files.readString(CONFIG_FILE)).getAsJsonObject();
            for (Module module : FeatherClient.getInstance().getModuleManager().getModules()) {
                if (!root.has(module.getName())) continue;
                JsonObject obj = root.getAsJsonObject(module.getName());
                if (obj.has("enabled") && obj.get("enabled").getAsBoolean()) {
                    module.setEnabled(true);
                }
                if (obj.has("keyBind")) {
                    module.setKeyBind(obj.get("keyBind").getAsInt());
                }
                if (obj.has("settings")) {
                    JsonObject settings = obj.getAsJsonObject("settings");
                    for (Module.Setting<?> setting : module.getSettings()) {
                        if (!settings.has(setting.getName())) continue;
                        JsonElement val = settings.get(setting.getName());
                        if (setting instanceof Module.BooleanSetting bs) {
                            bs.setValue(val.getAsBoolean());
                        } else if (setting instanceof Module.NumberSetting ns) {
                            ns.setValue(val.getAsDouble());
                        } else if (setting instanceof Module.ColorSetting cs) {
                            cs.setValue(val.getAsInt());
                        } else if (setting instanceof Module.ModeSetting ms) {
                            ms.setValue(val.getAsString());
                        }
                    }
                }
            }
            FeatherClient.LOGGER.info("Config loaded");
        } catch (Exception e) {
            FeatherClient.LOGGER.error("Failed to load config", e);
        }
    }

    public void save() {
        try {
            Files.createDirectories(CONFIG_DIR);
            JsonObject root = new JsonObject();
            for (Module module : FeatherClient.getInstance().getModuleManager().getModules()) {
                JsonObject obj = new JsonObject();
                obj.addProperty("enabled", module.isEnabled());
                obj.addProperty("keyBind", module.getKeyBind());
                JsonObject settings = new JsonObject();
                for (Module.Setting<?> setting : module.getSettings()) {
                    if (setting.getValue() instanceof Boolean b) settings.addProperty(setting.getName(), b);
                    else if (setting.getValue() instanceof Number n) settings.addProperty(setting.getName(), n);
                    else if (setting.getValue() instanceof String s) settings.addProperty(setting.getName(), s);
                }
                obj.add("settings", settings);
                root.add(module.getName(), obj);
            }
            Files.writeString(CONFIG_FILE, GSON.toJson(root));
        } catch (Exception e) {
            FeatherClient.LOGGER.error("Failed to save config", e);
        }
    }
}
