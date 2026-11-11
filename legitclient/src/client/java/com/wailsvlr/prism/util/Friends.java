package com.wailsvlr.prism.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.wailsvlr.prism.PrismClient;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Friends by player name, kept in {@code config/prismclient-friends.json}. Names compare without case. Added from the
 * Friends panel or by middle-clicking a player; PlayerFinder draws friends in their own colour.
 */
public final class Friends {
	private Friends() { }

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final List<String> NAMES = new ArrayList<>();
	private static boolean loaded;

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("prismclient-friends.json");
	}

	private static void ensure() {
		if (loaded) return;
		loaded = true;
		try {
			if (!Files.exists(file())) return;
			JsonElement root = JsonParser.parseString(Files.readString(file(), StandardCharsets.UTF_8));
			for (JsonElement e : root.getAsJsonObject().getAsJsonArray("friends")) NAMES.add(e.getAsString());
		} catch (IOException | RuntimeException e) {
			PrismClient.LOG.warn("Could not read the friends list: {}", e.toString());
		}
	}

	private static void save() {
		JsonArray a = new JsonArray();
		for (String n : NAMES) a.add(n);
		JsonObject root = new JsonObject();
		root.add("friends", a);
		try {
			Files.createDirectories(file().getParent());
			Files.writeString(file(), GSON.toJson(root), StandardCharsets.UTF_8);
		} catch (IOException e) {
			PrismClient.LOG.warn("Could not save the friends list: {}", e.toString());
		}
	}

	public static List<String> list() {
		ensure();
		return List.copyOf(NAMES);
	}

	public static boolean isFriend(String name) {
		ensure();
		for (String n : NAMES) if (n.equalsIgnoreCase(name)) return true;
		return false;
	}

	/** A valid Minecraft name: 1-16 letters, digits or underscores. */
	public static boolean valid(String name) {
		return name.matches("[A-Za-z0-9_]{1,16}");
	}

	public static boolean add(String name) {
		ensure();
		if (!valid(name) || isFriend(name)) return false;
		NAMES.add(name);
		NAMES.sort(String.CASE_INSENSITIVE_ORDER);
		save();
		return true;
	}

	public static boolean remove(String name) {
		ensure();
		boolean ok = NAMES.removeIf(n -> n.equalsIgnoreCase(name));
		if (ok) save();
		return ok;
	}

	/** Adds or removes; returns true when the player is a friend afterwards. */
	public static boolean toggle(String name) {
		if (isFriend(name)) {
			remove(name);
			return false;
		}
		add(name);
		return true;
	}

	/** Is this name on the server's player list right now? */
	public static boolean online(String name) {
		var conn = Minecraft.getInstance().getConnection();
		if (conn == null) return false;
		for (var info : conn.getOnlinePlayers()) if (info.getProfile().name().toLowerCase(Locale.ROOT).equals(name.toLowerCase(Locale.ROOT))) return true;
		return false;
	}
}
