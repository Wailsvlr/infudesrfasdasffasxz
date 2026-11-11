package com.wailsvlr.prism.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.wailsvlr.prism.PrismClient;
import com.wailsvlr.prism.module.Module;
import com.wailsvlr.prism.modules.ClickGuiModule;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Named setting profiles in {@code config/prismclient-profiles/<name>.json}: every module's on/off state, settings and
 * key, like the main config. The ClickGUI's own look is left out, so loading a profile never changes the menu
 * under the pointer. The name of the last loaded or saved profile is kept in the main config ("profile").
 */
public final class Profiles {
	private Profiles() { }

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public static Path dir() {
		return FabricLoader.getInstance().getConfigDir().resolve("prismclient-profiles");
	}

	/** A file-safe name: letters, digits, space and _ - ( ) . only, at most 32 characters. */
	public static String clean(String name) {
		String s = name.replaceAll("[^A-Za-z0-9 _().-]", "_").trim();
		if (s.length() > 32) s = s.substring(0, 32).trim();
		return s.isEmpty() ? "Profile" : s;
	}

	public static List<String> list() {
		List<String> out = new ArrayList<>();
		if (!Files.isDirectory(dir())) return out;
		try (Stream<Path> files = Files.list(dir())) {
			files.map(p -> p.getFileName().toString()).filter(n -> n.endsWith(".json"))
				.map(n -> n.substring(0, n.length() - 5)).sorted(String.CASE_INSENSITIVE_ORDER).forEach(out::add);
		} catch (IOException e) {
			PrismClient.LOG.warn("Could not list profiles: {}", e.toString());
		}
		return out;
	}

	/** A name not taken yet: "Profile 1", "Profile 2"... */
	public static String freeName(String base) {
		List<String> have = list().stream().map(n -> n.toLowerCase(Locale.ROOT)).toList();
		String b = clean(base);
		if (!have.contains(b.toLowerCase(Locale.ROOT))) return b;
		for (int i = 2; ; i++) if (!have.contains((b + " " + i).toLowerCase(Locale.ROOT))) return b + " " + i;
	}

	public static String current() {
		JsonObject gui = PrismClient.config().gui;
		return gui.has("profile") && gui.get("profile").isJsonPrimitive() ? gui.get("profile").getAsString() : "";
	}

	private static void setCurrent(String name) {
		PrismClient.config().gui.addProperty("profile", name);
		PrismClient.config().save();
	}

	public static boolean save(String name) {
		name = clean(name);
		JsonObject mods = new JsonObject();
		for (Module m : PrismClient.modules().all()) if (!(m instanceof ClickGuiModule)) mods.add(m.getName(), m.save());
		JsonObject root = new JsonObject();
		root.add("modules", mods);
		try {
			Files.createDirectories(dir());
			Files.writeString(dir().resolve(name + ".json"), GSON.toJson(root), StandardCharsets.UTF_8);
			setCurrent(name);
			return true;
		} catch (IOException e) {
			PrismClient.LOG.warn("Could not save profile {}: {}", name, e.toString());
			return false;
		}
	}

	public static boolean load(String name) {
		Path f = dir().resolve(clean(name) + ".json");
		try {
			JsonElement root = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8));
			JsonObject mods = root.getAsJsonObject().getAsJsonObject("modules");
			com.wailsvlr.prism.gui.Notifications.quiet = true;
			try {
				for (Module m : PrismClient.modules().all()) {
					if (m instanceof ClickGuiModule || mods == null || !mods.has(m.getName()) || !mods.get(m.getName()).isJsonObject()) continue;
					try { m.load(mods.getAsJsonObject(m.getName())); }
					catch (RuntimeException e) { PrismClient.LOG.warn("Profile {}: could not load {}: {}", name, m.getName(), e.toString()); }
				}
			} finally {
				com.wailsvlr.prism.gui.Notifications.quiet = false;
			}
			setCurrent(clean(name));
			return true;
		} catch (IOException | RuntimeException e) {
			PrismClient.LOG.warn("Could not load profile {}: {}", name, e.toString());
			return false;
		}
	}

	public static boolean delete(String name) {
		try {
			boolean ok = Files.deleteIfExists(dir().resolve(clean(name) + ".json"));
			if (ok && current().equals(clean(name))) setCurrent("");
			return ok;
		} catch (IOException e) {
			return false;
		}
	}
}
