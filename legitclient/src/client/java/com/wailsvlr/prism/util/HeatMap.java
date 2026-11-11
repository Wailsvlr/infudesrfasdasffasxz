package com.wailsvlr.prism.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.wailsvlr.prism.PrismClient;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What BaseHunter learned about the world, kept between sessions (config/prismclient-heatmap.json): for every
 * 1024x1024 area how often it was visited and how much player activity was seen there, and markers for every base
 * and trail found. Kept per server and dimension.
 */
public final class HeatMap {
	public static final int CELL = 1024;
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final HeatMap INSTANCE = new HeatMap();

	/** One 1024x1024 area. */
	public static final class Cell {
		public int visits;
		/** Sum over visits of the activity seen (old chunks, player-made blocks, portals, players). */
		public double activity;
		public long last;

		public double average() { return visits == 0 ? 0 : activity / visits; }
	}

	/** A base ("base", with its raid status) or a trail ("trail"). */
	public static final class Marker {
		public int x, y, z;
		public String type, status, label;
		public long time;
	}

	private static final class World {
		Map<String, Cell> cells = new HashMap<>();
		List<Marker> markers = new ArrayList<>();
	}

	private Map<String, World> worlds = new HashMap<>();
	private Marker target;
	private boolean loaded;

	public static HeatMap get() {
		INSTANCE.load();
		return INSTANCE;
	}

	private static Path file() { return FabricLoader.getInstance().getConfigDir().resolve("prismclient-heatmap.json"); }

	private void load() {
		if (loaded) return;
		loaded = true;
		try {
			if (!Files.exists(file())) return;
			Saved s = GSON.fromJson(Files.readString(file(), StandardCharsets.UTF_8), Saved.class);
			if (s != null && s.worlds != null) worlds = s.worlds;
		} catch (IOException | RuntimeException e) {
			PrismClient.LOG.warn("Could not read the heat map", e);
		}
	}

	private static final class Saved { Map<String, World> worlds; }

	public void save() {
		try {
			Saved s = new Saved();
			s.worlds = worlds;
			Files.writeString(file(), GSON.toJson(s), StandardCharsets.UTF_8);
		} catch (IOException e) {
			PrismClient.LOG.warn("Could not write the heat map", e);
		}
	}

	/** Server address + dimension of where you are now. */
	public static String worldKey() {
		Minecraft mc = Minecraft.getInstance();
		String server = mc.getCurrentServer() != null ? mc.getCurrentServer().ip : "singleplayer";
		return server + "|" + (mc.level == null ? "?" : mc.level.dimension().identifier().toString());
	}

	private World world() { return worlds.computeIfAbsent(worldKey(), k -> new World()); }

	public static String cellKey(int cx, int cz) { return cx + "," + cz; }

	public Map<String, Cell> cells() { return world().cells; }

	public List<Marker> markers() { return world().markers; }

	/** A landing: {@code activity} is what was seen around (x, z). */
	public void visit(int x, int z, double activity) {
		Cell c = world().cells.computeIfAbsent(cellKey(Math.floorDiv(x, CELL), Math.floorDiv(z, CELL)), k -> new Cell());
		c.visits++;
		c.activity += activity;
		c.last = System.currentTimeMillis();
		save();
	}

	/** Adds a marker, or updates the one of the same type within 48 blocks (a base seen again). */
	public void mark(String type, int x, int y, int z, String status, String label) {
		for (Marker m : world().markers) {
			if (m.type.equals(type) && Math.abs(m.x - x) <= 48 && Math.abs(m.z - z) <= 48) {
				m.status = status; m.label = label; m.time = System.currentTimeMillis(); m.y = y;
				save();
				return;
			}
		}
		Marker m = new Marker();
		m.type = type; m.x = x; m.y = y; m.z = z; m.status = status; m.label = label; m.time = System.currentTimeMillis();
		world().markers.add(m);
		save();
	}

	public Marker target() { return target; }

	public void setTarget(Marker m) { target = m; }

	/** Clears what was learned about the current server and dimension. */
	public void clearWorld() {
		worlds.remove(worldKey());
		target = null;
		save();
	}
}
