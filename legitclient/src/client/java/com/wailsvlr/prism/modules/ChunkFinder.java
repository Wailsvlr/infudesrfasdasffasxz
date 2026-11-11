package com.wailsvlr.prism.modules;

import com.wailsvlr.prism.module.Category;
import com.wailsvlr.prism.module.Module;
import com.wailsvlr.prism.render.Highlight;
import com.wailsvlr.prism.render.HighlightStyle;
import com.wailsvlr.prism.setting.BoolSetting;
import com.wailsvlr.prism.setting.ColorSetting;
import com.wailsvlr.prism.setting.ModeSetting;
import com.wailsvlr.prism.setting.NumberSetting;
import com.wailsvlr.prism.setting.SectionSetting;
import com.wailsvlr.prism.util.BlockGroups;
import com.wailsvlr.prism.util.ChunkScanner;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Marks chunks that show signs of a player having been near for a long time, and estimates where they stood.
 *
 * Things only grow while a player is within the server's simulation distance (random ticks). So a chunk where they
 * grew a lot was loaded for a long time:
 * - Amethyst: a fresh geode has a few buds of mixed sizes; a geode that kept growing is full of clusters. The sign is
 *   the number of grown crystals per budding amethyst block.
 * - Kelp: kelp that reached the water surface in many places (natural kelp mostly stops short of it).
 * - Player blocks: blocks that never generate in the world (from the same block list BaseValue uses).
 *
 * Active chunks are grouped (a player loads everything within simulation distance around them), and the middle of
 * each group is marked as the probable spot. Sensitivity lowers or raises every threshold at once.
 */
public class ChunkFinder extends Module {
	public enum Kind { BUDDING, CLUSTER, LARGE_BUD, SMALL_BUD, KELP_HEAD, PLAYER_BLOCK }

	public enum Sign {
		AMETHYST("Amethyst"), KELP("Kelp"), PLAYER("Player blocks");

		public final String label;

		Sign(String label) { this.label = label; }
	}

	/** One chunk that looks active: why, and how strongly (1.0 = just at the threshold). */
	public record Active(long chunk, Sign sign, double strength, String detail) {
		public int x() { return ChunkPos.getX(chunk); }
		public int z() { return ChunkPos.getZ(chunk); }
	}

	/** A group of active chunks and the block where the player probably stood (its middle). */
	public record Spot(int x, int z, int chunks) { }

	private final BoolSetting amethyst, kelp, playerBlocks, showSpot, throughWalls, labels, hudCount;
	private final NumberSetting sensitivity, simDistance, range, chunksPerTick, opacity, customY;
	private final ColorSetting amethystColor, kelpColor, playerColor, spotColor;
	private final ModeSetting height;

	private final ChunkScanner<Kind> scanner;
	private volatile List<Active> active = List.of();
	private volatile List<Spot> spots = List.of();
	private long tick;

	public ChunkFinder() {
		super("ChunkFinder", "Marks chunks where a player spent a long time (amethyst geodes that kept growing, kelp grown to the surface, player-made blocks) and estimates where they stood", Category.RENDER);
		add(new SectionSetting("Signs"));
		amethyst = add(new BoolSetting("Amethyst Growth", "Geodes full of grown clusters: the chunk stayed loaded for a long time", true));
		kelp = add(new BoolSetting("Grown Kelp", "Many kelp plants that reached the water surface", true));
		playerBlocks = add(new BoolSetting("Player Blocks", "Blocks that never generate naturally (beds, crafted blocks, ...)", true));

		add(new SectionSetting("Detection"));
		sensitivity = add(new NumberSetting("Sensitivity", "Higher marks chunks on weaker signs (more finds, more false ones); lower only marks clear cases", 5, 1, 10, 1, ""));
		simDistance = add(new NumberSetting("Simulation Distance", "The server's simulation distance in chunks (blocks only grow this close to a player; often 5-10). Used to group active chunks and estimate where the player stood", 6, 2, 16, 1, " ch"));
		range = add(new NumberSetting("Range", "How many chunks around you to scan (the client only has the chunks inside your render distance)", 12, 2, 32, 1, " ch"));
		chunksPerTick = add(new NumberSetting("Chunks Per Tick", "How many chunks to scan each tick (higher finds faster, costs more FPS)", 3, 1, 16, 1, ""));

		add(new SectionSetting("Look"));
		amethystColor = add(new ColorSetting("Amethyst Color", "Colour of chunks with grown amethyst", 0xFFB26BFF)).visibleWhen(amethyst::isOn);
		kelpColor = add(new ColorSetting("Kelp Color", "Colour of chunks with grown kelp", 0xFF2EC4B6)).visibleWhen(kelp::isOn);
		playerColor = add(new ColorSetting("Player Color", "Colour of chunks with player blocks", 0xFFFF8A3D)).visibleWhen(playerBlocks::isOn);
		opacity = add(new NumberSetting("Opacity", "Opacity of the chunk squares", 26, 5, 80, 1, "%"));
		height = add(new ModeSetting("Height", "Where the squares are drawn: under your feet, at sea level, or a height you choose", "Feet", "Feet", "Sea Level", "Custom"));
		customY = add(new NumberSetting("Custom Y", "Height of the squares", 64, -64, 320, 1, "")).visibleWhen(() -> height.is("Custom"));
		throughWalls = add(new BoolSetting("Through Walls", "Draw the squares over the terrain like a map", true));
		labels = add(new BoolSetting("Labels", "Name the sign over each marked chunk", false));
		showSpot = add(new BoolSetting("Probable Spot", "Mark the middle of each group of active chunks with a column", true));
		spotColor = add(new ColorSetting("Spot Color", "Colour of the probable spot column", 0xFFFFD43B)).visibleWhen(showSpot::isOn);
		hudCount = add(new BoolSetting("HUD Counter", "Show active chunks and the nearest probable spot in the HUD", true));

		Map<Block, Kind> byBlock = new IdentityHashMap<>();
		byBlock.put(Blocks.BUDDING_AMETHYST, Kind.BUDDING);
		byBlock.put(Blocks.AMETHYST_CLUSTER, Kind.CLUSTER);
		byBlock.put(Blocks.LARGE_AMETHYST_BUD, Kind.LARGE_BUD);
		byBlock.put(Blocks.MEDIUM_AMETHYST_BUD, Kind.SMALL_BUD);
		byBlock.put(Blocks.SMALL_AMETHYST_BUD, Kind.SMALL_BUD);
		byBlock.put(Blocks.KELP, Kind.KELP_HEAD);
		Map<Block, Kind> lookup = byBlock;
		scanner = new ChunkScanner<>(st -> {
			Kind k = lookup.get(st.getBlock());
			if (k != null) return k;
			Set<String> g = BlockGroups.of(st.getBlock());
			return g != null && g.isEmpty() ? Kind.PLAYER_BLOCK : null;
		});
		scanner.setCap(k -> k == Kind.PLAYER_BLOCK, 400);
	}

	// ------------------------------------------------------------------ thresholds (sensitivity 1..10)
	/** Grown crystals per budding block a geode needs (fresh geodes are well under 0.5). */
	public double amethystThreshold() { return 1.3 - 0.1 * sensitivity.get(); }

	/** Kelp tops at the surface a chunk needs. */
	public int kelpThreshold() { return Math.max(1, 12 - sensitivity.getInt()); }

	/** Player-only blocks a chunk needs. */
	public int playerThreshold() { return Math.max(2, 22 - 2 * sensitivity.getInt()); }

	@Override
	protected void onEnable() { scanner.clear(); active = List.of(); spots = List.of(); }

	@Override
	protected void onDisable() { scanner.clear(); active = List.of(); spots = List.of(); }

	@Override
	public void onTick() {
		ClientLevel level = mc.level;
		if (level == null || mc.player == null) return;
		tick++;
		int r = Math.min(range.getInt(), mc.options.getEffectiveRenderDistance());
		boolean scanned = scanner.tick(level, mc.player.blockPosition(),
			new ChunkScanner.Options(r, level.getMinY(), level.getMaxY(), chunksPerTick.getInt(), 30 * 20L, false));
		if (scanned || tick % 20 == 0) evaluate(level);
	}

	/** Called for every block change on the client (see ClientLevelMixin). */
	public void onBlockChanged(BlockPos pos, BlockState oldState, BlockState newState) {
		if (isEnabled()) scanner.blockChanged(pos, oldState, newState, false);
	}

	/**
	 * Re-evaluates the scanned chunks against the thresholds, then groups the active ones. Geodes are judged as a
	 * whole (budding blocks within a few blocks of each other, across chunk borders), and every chunk a growing geode
	 * touches is marked; kelp and player blocks are counted per chunk.
	 */
	public void evaluate(ClientLevel level) {
		scanner.dropChanged(level);
		Map<Long, Active> best = new java.util.HashMap<>();
		double amThr = amethystThreshold();
		int kelpThr = kelpThreshold(), playerThr = playerThreshold();
		List<BlockPos> budding = new ArrayList<>();
		List<ChunkScanner.Found<Kind>> crystals = new ArrayList<>();
		for (ChunkScanner.ChunkResult<Kind> res : scanner.results()) {
			int kelpTops = 0, player = 0;
			for (ChunkScanner.Found<Kind> f : res.found()) {
				switch (f.kind()) {
					case BUDDING -> budding.add(f.pos());
					case CLUSTER, LARGE_BUD, SMALL_BUD -> crystals.add(f);
					case KELP_HEAD -> { if (level.getBlockState(f.pos().above()).getFluidState().isEmpty()) kelpTops++; }
					case PLAYER_BLOCK -> player++;
				}
			}
			if (playerBlocks.isOn() && player >= playerThr) offer(best, new Active(res.chunk(), Sign.PLAYER, player / (double) playerThr, player + " player blocks"));
			if (kelp.isOn() && kelpTops >= kelpThr) offer(best, new Active(res.chunk(), Sign.KELP, kelpTops / (double) kelpThr, kelpTops + " kelp at the surface"));
		}
		if (amethyst.isOn() && !budding.isEmpty()) {
			for (Geode g : geodes(budding, crystals)) {
				if (g.budding() < 4) continue;
				double ratio = g.grown() / g.budding();
				if (ratio < amThr) continue;
				String detail = String.format(java.util.Locale.ROOT, "%.2f grown per budding (%d budding)", ratio, g.budding());
				for (long chunk : g.chunks()) offer(best, new Active(chunk, Sign.AMETHYST, ratio / amThr, detail));
			}
		}
		List<Active> out = new ArrayList<>(best.values());
		active = out;
		spots = group(out, simDistance.getInt());
	}

	private static void offer(Map<Long, Active> best, Active a) {
		Active old = best.get(a.chunk());
		if (old == null || a.strength() > old.strength()) best.put(a.chunk(), a);
	}

	/** One geode: its budding blocks, its grown crystals (cluster 1, large bud 0.5) and the chunks it touches. */
	public record Geode(int budding, double grown, Set<Long> chunks) { }

	/**
	 * Groups budding amethyst into geodes (blocks in neighbouring 4-block cells belong together) and gives each
	 * crystal to the geode of the budding block it grows on.
	 */
	public static List<Geode> geodes(List<BlockPos> budding, List<ChunkScanner.Found<Kind>> crystals) {
		Map<Long, Integer> cellGroup = new java.util.HashMap<>();
		Map<Long, List<BlockPos>> cells = new java.util.HashMap<>();
		for (BlockPos p : budding) cells.computeIfAbsent(BlockPos.asLong(p.getX() >> 2, p.getY() >> 2, p.getZ() >> 2), k -> new ArrayList<>()).add(p);
		int groups = 0;
		for (long start : cells.keySet()) {
			if (cellGroup.containsKey(start)) continue;
			int id = groups++;
			java.util.ArrayDeque<Long> q = new java.util.ArrayDeque<>(List.of(start));
			cellGroup.put(start, id);
			while (!q.isEmpty()) {
				BlockPos c = BlockPos.of(q.poll());
				for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
					long n = BlockPos.asLong(c.getX() + dx, c.getY() + dy, c.getZ() + dz);
					if (cells.containsKey(n) && !cellGroup.containsKey(n)) {
						cellGroup.put(n, id);
						q.add(n);
					}
				}
			}
		}
		int[] count = new int[groups];
		double[] grown = new double[groups];
		List<Set<Long>> chunks = new ArrayList<>();
		for (int i = 0; i < groups; i++) chunks.add(new java.util.HashSet<>());
		Map<Long, Integer> byBlock = new java.util.HashMap<>();
		for (Map.Entry<Long, List<BlockPos>> e : cells.entrySet()) {
			int id = cellGroup.get(e.getKey());
			for (BlockPos p : e.getValue()) {
				count[id]++;
				chunks.get(id).add(ChunkPos.asLong(p.getX() >> 4, p.getZ() >> 4));
				byBlock.put(p.asLong(), id);
			}
		}
		for (ChunkScanner.Found<Kind> f : crystals) {
			double w = f.kind() == Kind.CLUSTER ? 1 : f.kind() == Kind.LARGE_BUD ? 0.5 : 0;
			if (w == 0) continue;
			for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
				Integer id = byBlock.get(f.pos().relative(d).asLong());
				if (id != null) {
					grown[id] += w;
					break;
				}
			}
		}
		List<Geode> out = new ArrayList<>();
		for (int i = 0; i < groups; i++) out.add(new Geode(count[i], grown[i], chunks.get(i)));
		return out;
	}

	/**
	 * Groups active chunks that a single player could have kept loaded together (within twice the simulation
	 * distance of each other) and returns each group's middle.
	 */
	static List<Spot> group(List<Active> list, int sim) {
		List<Spot> out = new ArrayList<>();
		boolean[] used = new boolean[list.size()];
		for (int i = 0; i < list.size(); i++) {
			if (used[i]) continue;
			List<Active> g = new ArrayList<>();
			List<Integer> queue = new ArrayList<>(List.of(i));
			used[i] = true;
			while (!queue.isEmpty()) {
				Active a = list.get(queue.remove(queue.size() - 1));
				g.add(a);
				for (int j = 0; j < list.size(); j++) {
					if (used[j]) continue;
					Active b = list.get(j);
					if (Math.max(Math.abs(a.x() - b.x()), Math.abs(a.z() - b.z())) <= 2 * sim) {
						used[j] = true;
						queue.add(j);
					}
				}
			}
			double sx = 0, sz = 0, w = 0;
			for (Active a : g) {
				sx += (a.x() * 16 + 8) * a.strength();
				sz += (a.z() * 16 + 8) * a.strength();
				w += a.strength();
			}
			out.add(new Spot((int) Math.round(sx / w), (int) Math.round(sz / w), g.size()));
		}
		return out;
	}

	public List<Active> active() { return active; }

	public List<Spot> spots() { return spots; }

	public boolean showHudCount() { return hudCount.isOn(); }

	/** The nearest probable spot, or null. */
	public Spot nearestSpot() {
		if (mc.player == null) return null;
		Spot best = null;
		double bd = Double.MAX_VALUE;
		for (Spot s : spots) {
			double d = Math.hypot(s.x() + 0.5 - mc.player.getX(), s.z() + 0.5 - mc.player.getZ());
			if (d < bd) {
				bd = d;
				best = s;
			}
		}
		return best;
	}

	public void collect(List<Highlight> out) {
		if (!isEnabled() || mc.player == null || mc.level == null) return;
		double y = switch (height.get()) {
			case "Sea Level" -> mc.level.getSeaLevel();
			case "Custom" -> customY.get();
			default -> Math.floor(mc.player.getY()) - 0.98;
		};
		float a = opacity.getFloat() / 100f;
		HighlightStyle st = new HighlightStyle("Fill", 0, a, 1, throughWalls.isOn(), false, 0, false, 1e9, false, 1, 0, false);
		for (Active c : active) {
			int rgb = switch (c.sign()) {
				case AMETHYST -> amethystColor.rgb();
				case KELP -> kelpColor.rgb();
				case PLAYER -> playerColor.rgb();
			};
			int x = c.x(), z = c.z();
			// the same thin square a little inside the chunk border that TrailFinder draws
			out.add(new Highlight(new AABB(x * 16 + 0.3, y, z * 16 + 0.3, x * 16 + 15.7, y + 0.04, z * 16 + 15.7), rgb, st,
				labels.isOn() ? c.sign().label + " " + c.detail() : null));
		}
		if (showSpot.isOn()) {
			HighlightStyle col = new HighlightStyle("Both", 0.9f, 0.25f, 2, true, false, 0, false, 1e9, true, 0.8f, 0, false);
			for (Spot s : spots) {
				out.add(new Highlight(new AABB(s.x() + 0.2, y, s.z() + 0.2, s.x() + 0.8, y + 24, s.z() + 0.8), spotColor.rgb(), col,
					labels.isOn() ? "Probable spot (" + s.chunks() + " chunks)" : null));
			}
		}
	}

	@Override
	public String getInfo() { return active.size() + " active"; }
}
