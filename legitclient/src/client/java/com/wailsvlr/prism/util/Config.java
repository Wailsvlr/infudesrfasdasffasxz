package com.wailsvlr.prism.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.wailsvlr.prism.PrismClient;
import com.wailsvlr.prism.module.Module;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Saves modules, settings, binds and GUI panel positions to {@code config/prismclient.json}. */
public final class Config {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private final Path file = FabricLoader.getInstance().getConfigDir().resolve("prismclient.json");
	/** Extra data saved alongside the modules (the ClickGUI stores panel positions here). */
	public JsonObject gui = new JsonObject();

	public void load() {
		if (!Files.exists(file)) return;
		try {
			JsonElement root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
			if (!root.isJsonObject()) return;
			JsonObject o = root.getAsJsonObject();
			JsonObject mods = o.has("modules") ? o.getAsJsonObject("modules") : new JsonObject();
			com.wailsvlr.prism.gui.Notifications.quiet = true;
			try {
				for (Module m : PrismClient.modules().all()) {
					if (mods.has(m.getName()) && mods.get(m.getName()).isJsonObject()) {
						try { m.load(mods.getAsJsonObject(m.getName())); }
						catch (RuntimeException e) { PrismClient.LOG.warn("Could not load settings of {}: {}", m.getName(), e.toString()); }
					}
				}
			} finally {
				com.wailsvlr.prism.gui.Notifications.quiet = false;
			}
			if (o.has("gui") && o.get("gui").isJsonObject()) gui = o.getAsJsonObject("gui");
		} catch (IOException | RuntimeException e) {
			PrismClient.LOG.warn("Could not read {}, using defaults: {}", file, e.toString());
		}
	}

	public void save() {
		JsonObject root = new JsonObject();
		JsonObject mods = new JsonObject();
		for (Module m : PrismClient.modules().all()) mods.add(m.getName(), m.save());
		root.add("modules", mods);
		root.add("gui", gui);
		try {
			Files.createDirectories(file.getParent());
			Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
			Files.writeString(tmp, GSON.toJson(root), StandardCharsets.UTF_8);
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException e) {
			PrismClient.LOG.warn("Could not save {}: {}", file, e.toString());
		}
	}
}
