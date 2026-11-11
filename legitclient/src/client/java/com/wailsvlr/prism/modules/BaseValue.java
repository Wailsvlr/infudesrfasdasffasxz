package com.wailsvlr.prism.modules;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.wailsvlr.prism.PrismClient;
import com.wailsvlr.prism.mixin.TextDisplayAccessor;
import com.wailsvlr.prism.module.Category;
import com.wailsvlr.prism.module.Module;
import com.wailsvlr.prism.setting.BoolSetting;
import com.wailsvlr.prism.setting.SectionSetting;
import com.wailsvlr.prism.util.Money;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.phys.AABB;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What a base is worth, from what the client can actually see: valuable blocks (spawners with their stack size,
 * beacons, mineral blocks, shulkers...) and visible items (item frames, the ground, armour stands), priced with the
 * auction prices AuctionHelper remembered. Chests and shulkers do not send their contents until opened, so their
 * contents are only an estimate, learned from the containers you open in bases.
 */
public class BaseValue extends Module {
	private static final Pattern STACK = Pattern.compile("(\\d[\\d,]*)\\s*[xX×]|[xX×]\\s*(\\d[\\d,]*)");

	private final BoolSetting label, items, stacks, learn, skipVillages, box, hud;
	private final com.wailsvlr.prism.setting.NumberSetting minPoints;
	/** The base in front of you (looked at), or null. */
	private volatile BlockFinder.Base current;
	private int lostTicks;
	/** A base you look at that failed the check, and why (shown instead of a value). */
	private volatile String rejected;

	public record Value(double known, int unpriced, String summary, double estimate, int learnedFrom, Map<String, Double> parts) { }

	private final Map<BlockPos, Value> cache = new HashMap<>();
	private final Map<BlockPos, Long> cachedAt = new HashMap<>();
	// learned container contents: kind -> {total value, containers}
	private final Map<String, double[]> learned = new HashMap<>();
	private final Set<BlockPos> learnedHere = new HashSet<>();
	private BlockPos lastUsed;
	private long lastUsedAt;
	private int openTicks;

	public BaseValue() {
		super("BaseValue", "Look at a base: shows what it is worth (spawners, beacons, mineral blocks, shulkers, visible items) with your auction prices. Works without BlockFinder", Category.WORLD);
		add(new SectionSetting("Which Base"));
		minPoints = add(new com.wailsvlr.prism.setting.NumberSetting("Min Points", "Points of player-only blocks (shulker 8, ender chest 5, beacon 6, hopper 2, piston 2, sign 2-4...) that must sit close together (within 24 blocks) for a real base", 30, 10, 300, 5, ""));
		skipVillages = add(new BoolSetting("Skip Villages", "Never pick a village", true));
		box = add(new BoolSetting("Draw Box", "A box with the value around the base you look at", true));
		hud = add(new BoolSetting("HUD Line", "The value and what it is made of in the HUD", true));
		add(new SectionSetting("Value"));
		label = add(new BoolSetting("Show On Label", "Add \"~$8.4M\" to the base label and the base details", true));
		items = add(new BoolSetting("Visible Items", "Count items in item frames, on the ground and on armour stands", true));
		stacks = add(new BoolSetting("Spawner Stacks", "Read stack holograms above spawners (\"64x Skeleton Spawner\")", true));
		learn = add(new BoolSetting("Container Estimate", "Learn what chests and shulkers in bases hold when you open them, and estimate the rest (shown apart, marked ~?)", true));
		load();
	}

	private static AuctionHelper ah() { return PrismClient.modules().get(AuctionHelper.class); }

	// ---------------------------------------------------------------- prices

	private static String itemKey(Block b) { return BuiltInRegistries.ITEM.getKey(b.asItem()).toString(); }

	/** Unit price of a block, with fallbacks: a mineral block is 9 of its item, any shulker box is like a plain one. */
	private Double blockPrice(Block b) {
		Double p = ah().typical(itemKey(b));
		if (p != null) return p;
		Map<Block, String> nine = Map.of(Blocks.DIAMOND_BLOCK, "minecraft:diamond", Blocks.NETHERITE_BLOCK, "minecraft:netherite_ingot",
			Blocks.EMERALD_BLOCK, "minecraft:emerald", Blocks.GOLD_BLOCK, "minecraft:gold_ingot", Blocks.IRON_BLOCK, "minecraft:iron_ingot",
			Blocks.LAPIS_BLOCK, "minecraft:lapis_lazuli");
		if (nine.containsKey(b)) {
			Double unit = ah().typical(nine.get(b));
			return unit == null ? null : unit * 9;
		}
		if (b instanceof ShulkerBoxBlock) {
			Double plain = ah().typical("minecraft:shulker_box");
			if (plain != null) return plain;
			for (String k : ah().keys()) if (k.endsWith("shulker_box")) return ah().typical(k);
		}
		return null;
	}

	/** A spawner's price: a listing whose name has the mob in it ("Skeleton Spawner"), else any spawner listing. */
	private Double spawnerPrice(String mob) {
		String m = mob.toLowerCase(Locale.ROOT);
		for (String k : ah().keys()) if (k.startsWith("minecraft:spawner") && k.toLowerCase(Locale.ROOT).contains(m)) return ah().typical(k);
		return ah().typical("minecraft:spawner");
	}

	private Double itemPrice(ItemStack s) {
		return s.isEmpty() ? null : ah().typical(AutoSell.markOf(s));
	}

	// ---------------------------------------------------------------- one base

	public Value valueOf(BlockFinder.Base b) {
		long now = System.currentTimeMillis();
		Long at = cachedAt.get(b.center());
		Value v = cache.get(b.center());
		if (v != null && at != null && now - at < 1000) return v;
		v = compute(b);
		if (cache.size() > 64) { cache.clear(); cachedAt.clear(); }
		cache.put(b.center(), v);
		cachedAt.put(b.center(), now);
		return v;
	}

	private Value compute(BlockFinder.Base b) {
		double known = 0;
		int unpriced = 0;
		Map<String, Integer> counts = new LinkedHashMap<>();
		Map<String, Double> parts = new LinkedHashMap<>();
		// only the base itself: its core and a few blocks around it, not every build BlockFinder grouped with it
		AABB core = check(b).core();
		AABB area = core != null ? core.inflate(6) : b.box().inflate(4);
		for (BlockPos pos : b.valuables()) {
			if (!area.contains(pos.getCenter())) continue;
			Block block = mc.level.getBlockState(pos).getBlock();
			if (!BlockFinder.VALUABLE.contains(block)) continue;   // broken since the scan
			String name;
			int n = 1;
			Double price;
			if (block == Blocks.SPAWNER) {
				String mob = mob(pos);
				name = mob + " Spawner";
				if (stacks.isOn()) n = stackSize(pos);
				price = spawnerPrice(mob);
			} else {
				name = block instanceof ShulkerBoxBlock ? "Shulker Box" : block.getName().getString();
				price = blockPrice(block);
			}
			counts.merge(name, n, Integer::sum);
			if (price == null) { unpriced += n; continue; }
			known += price * n;
			parts.merge(name, price * n, Double::sum);
		}
		if (items.isOn()) {
			for (Entity e : mc.level.getEntities((Entity) null, area, e -> e instanceof ItemEntity || e instanceof ItemFrame || e instanceof ArmorStand)) {
				for (ItemStack s : stacksOf(e)) {
					Double p = itemPrice(s);
					if (p == null) continue;
					known += p * s.getCount();
					parts.merge("Items", p * s.getCount(), Double::sum);
				}
			}
		}
		// the contents of the chests and shulkers (unseen): average of the ones opened so far
		double estimate = 0;
		int from = 0;
		if (learn.isOn()) {
			double[] c = learned.get("chest"), sh = learned.get("shulker");
			int chests = 0, shulkers = 0;
			for (BlockPos p : b.playerPos()) {
				if (!area.contains(p.getCenter())) continue;
				Block blk = mc.level.getBlockState(p).getBlock();
				if (blk instanceof ShulkerBoxBlock) shulkers++;
				else if (blk == Blocks.CHEST || blk == Blocks.TRAPPED_CHEST || blk == Blocks.BARREL) chests++;
			}
			if (c != null && c[1] >= 3) { estimate += chests * c[0] / c[1]; from += (int) c[1]; }
			if (sh != null && sh[1] >= 3) { estimate += shulkers * sh[0] / sh[1]; from += (int) sh[1]; }
		}
		StringBuilder sum = new StringBuilder();
		parts.entrySet().stream().sorted((x, y) -> Double.compare(y.getValue(), x.getValue())).limit(5).forEach(e -> {
			if (sum.length() > 0) sum.append(", ");
			Integer n = counts.get(e.getKey());
			sum.append(n == null ? "" : n + " ").append(e.getKey()).append(" $").append(Money.format(e.getValue()));
		});
		return new Value(known, unpriced, sum.toString(), estimate, from, parts);
	}

	private static Iterable<ItemStack> stacksOf(Entity e) {
		if (e instanceof ItemEntity i) return java.util.List.of(i.getItem());
		if (e instanceof ItemFrame f) return java.util.List.of(f.getItem());
		java.util.List<ItemStack> out = new java.util.ArrayList<>();
		if (e instanceof ArmorStand a) for (EquipmentSlot slot : EquipmentSlot.values()) {
			ItemStack s = a.getItemBySlot(slot);
			if (!s.isEmpty()) out.add(s);
		}
		return out;
	}

	private String mob(BlockPos pos) {
		if (mc.level.getBlockEntity(pos) instanceof SpawnerBlockEntity s) {
			Entity display = s.getSpawner().getOrCreateDisplayEntity(mc.level, pos);
			if (display != null) return display.getType().getDescription().getString();
		}
		return "Empty";
	}

	/** The stack size from a hologram right above the spawner ("64x Skeleton", "Skeleton Spawner x12"), else 1. */
	private int stackSize(BlockPos pos) {
		AABB above = new AABB(pos).inflate(1.0, 0, 1.0).expandTowards(0, 3, 0);
		for (Entity e : mc.level.getEntities((Entity) null, above, e -> e.getType() == EntityType.TEXT_DISPLAY || e.hasCustomName())) {
			String text = e instanceof Display.TextDisplay ? e.getEntityData().get(TextDisplayAccessor.prism$textId()).getString()
				: e.getCustomName() == null ? "" : e.getCustomName().getString();
			Matcher m = STACK.matcher(text);
			if (m.find()) {
				try {
					return Math.max(1, Integer.parseInt((m.group(1) != null ? m.group(1) : m.group(2)).replace(",", "")));
				} catch (NumberFormatException ignored) { }
			}
		}
		return 1;
	}

	// ---------------------------------------------------------------- showing

	/** " - ~$8.4M" (plus "+~$6M?" for the estimated contents), or "" when off or nothing is priced. */
	public String labelFor(BlockFinder.Base b) {
		if (!isEnabled() || !label.isOn() || mc.level == null) return "";
		Value v = valueOf(b);
		if (v.known() <= 0 && v.estimate() <= 0) return v.unpriced() > 0 ? " - $?" : "";
		String out = " - ~$" + Money.format(v.known());
		if (v.estimate() > 0) out += " +~$" + Money.format(v.estimate()) + "?";
		return out;
	}

	/** Chat lines for the base details (BlockFinder.show). */
	public void explain(BlockFinder.Base b, Consumer<Component> line) {
		if (!isEnabled() || mc.level == null) return;
		Value v = valueOf(b);
		line.accept(Component.literal("Worth: ").withStyle(ChatFormatting.WHITE)
			.append(Component.literal("~$" + Money.format(v.known())).withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD))
			.append(Component.literal(v.summary().isEmpty() ? "" : "  (" + v.summary() + ")").withStyle(ChatFormatting.GRAY))
			.append(Component.literal(v.unpriced() > 0 ? "  " + v.unpriced() + " without an auction price" : "").withStyle(ChatFormatting.DARK_GRAY)));
		if (v.estimate() > 0) line.accept(Component.literal("Chests and shulkers might hold ").withStyle(ChatFormatting.WHITE)
			.append(Component.literal("~$" + Money.format(v.estimate())).withStyle(ChatFormatting.YELLOW))
			.append(Component.literal("  (estimate from " + v.learnedFrom() + " containers you opened; the real contents are only known when opened)").withStyle(ChatFormatting.DARK_GRAY)));
	}

	// ---------------------------------------------------------------- learning from opened containers

	/** The block the player last right-clicked (Fabric's use-block event). */
	public void onUseBlock(BlockPos pos) { lastUsed = pos.immutable(); lastUsedAt = System.currentTimeMillis(); }

	@Override
	protected void onDisable() {
		PrismClient.modules().get(BlockFinder.class).drive(false);
		current = null;
	}

	@Override
	public void onTick() {
		if (mc.level == null || mc.player == null) return;
		// the base search runs quietly in BlockFinder even while that module is off
		BlockFinder bf = PrismClient.modules().get(BlockFinder.class);
		bf.drive(true);
		bf.backgroundTick();
		BlockFinder.Base seen = pick(bf.bases());
		if (seen != null) {
			Check c = check(seen);
			if (c.real()) { current = seen; rejected = null; lostTicks = 0; }
			else { rejected = c.verdict(); if (++lostTicks > 40) current = null; }
		} else {
			if (++lostTicks > 40) { current = null; rejected = null; }
		}
		learnTick();
	}

	/** The base the crosshair points at (or stands in), else one within 10 degrees of it; nearest first. */
	private BlockFinder.Base pick(java.util.List<BlockFinder.Base> bases) {
		net.minecraft.world.phys.Vec3 eye = mc.player.getEyePosition(), look = mc.player.getViewVector(1f);
		BlockFinder.Base best = null;
		double bestD = Double.MAX_VALUE;
		for (BlockFinder.Base b : bases) {
			AABB area = b.box().inflate(3);
			double d;
			var hit = area.clip(eye, eye.add(look.scale(400)));
			if (area.contains(eye)) d = 0;
			else if (hit.isPresent()) d = hit.get().distanceTo(eye);
			else {
				net.minecraft.world.phys.Vec3 to = area.getCenter().subtract(eye);
				double cos = to.normalize().dot(look);
				if (cos < Math.cos(Math.toRadians(10)) || to.length() > 400) continue;
				d = 1000 + to.length();   // a direct hit always wins
			}
			if (d < bestD) { bestD = d; best = b; }
		}
		return best;
	}

	// ---------------------------------------------------------------- is it really a base?

	/**
	 * The verdict on a possible base, from the game's own worldgen data (BlockGroups): a block that never generates is
	 * player evidence; a block that generates only in some structures is evidence only when none of those structures is
	 * here (and only partly when the structure is one we cannot recognise). {@code real} needs a dense core (evidence
	 * worth Min Points within 24 blocks), decisive evidence (storage, redstone, utility or lived-in, not just building
	 * blocks that a mansion or a shipwreck also has), and is explained in {@code verdict} and {@code evidence}.
	 */
	public record Check(boolean real, int confidence, String verdict, String evidence, double dense, int kinds, String structure, String channels,
					   AABB core) { }

	private static final String[] KIND_NAMES = {"storage", "redstone", "utility", "lived-in", "light", "build"};
	private static final int STORAGE = 0, REDSTONE = 1, UTILITY = 2, LIVED = 3, LIGHT = 4, BUILD = 5;
	/** Structures recognised from their tell-tale blocks; the others (mansion, shipwreck, outpost...) cannot be ruled out. */
	private static final Set<String> DETECTABLE = Set.of("village", "trial_chambers", "ancient_city", "bastion", "mineshaft", "stronghold",
		"fortress", "dungeon", "trail_ruins", "desert_pyramid", "end_city", "ruined_portal");

	/** Weight and kind of a block that counts as evidence. Special blocks weigh more; plain building blocks 1 (capped per type). */
	private static double[] weigh(Block b) {
		String id = BuiltInRegistries.BLOCK.getKey(b).getPath();
		if (b instanceof ShulkerBoxBlock) return new double[]{8, STORAGE};
		if (b == Blocks.ENDER_CHEST) return new double[]{5, STORAGE};
		if (b == Blocks.HOPPER) return new double[]{2, STORAGE};
		if (b == Blocks.DROPPER || id.contains("copper_chest")) return new double[]{1.5, STORAGE};
		if (b == Blocks.CHEST || b == Blocks.TRAPPED_CHEST || b == Blocks.BARREL) return new double[]{0.5, BUILD};   // structures are full of them
		if (b == Blocks.PISTON || b == Blocks.STICKY_PISTON || b == Blocks.OBSERVER || b == Blocks.CRAFTER) return new double[]{2, REDSTONE};
		if (b == Blocks.REPEATER || b == Blocks.COMPARATOR || id.endsWith("_rail") || id.contains("copper_bulb") || id.contains("lightning_rod"))
			return new double[]{1.5, REDSTONE};
		if (b == Blocks.REDSTONE_LAMP || b == Blocks.DAYLIGHT_DETECTOR || b == Blocks.TARGET || b == Blocks.NOTE_BLOCK || b == Blocks.REDSTONE_BLOCK
			|| b == Blocks.SLIME_BLOCK || b == Blocks.HONEY_BLOCK || b == Blocks.LEVER || b == Blocks.REDSTONE_TORCH || b == Blocks.REDSTONE_WALL_TORCH)
			return new double[]{1, REDSTONE};
		if (b == Blocks.BEACON) return new double[]{6, UTILITY};
		if (b == Blocks.CONDUIT) return new double[]{5, UTILITY};
		if (b == Blocks.ENCHANTING_TABLE) return new double[]{4, UTILITY};
		if (id.contains("anvil") || b == Blocks.RESPAWN_ANCHOR || b == Blocks.LODESTONE || b == Blocks.JUKEBOX) return new double[]{3, UTILITY};
		if (b == Blocks.BREWING_STAND || b == Blocks.SMITHING_TABLE || b == Blocks.GRINDSTONE || b == Blocks.STONECUTTER || b == Blocks.LOOM
			|| b == Blocks.CARTOGRAPHY_TABLE || b == Blocks.FLETCHING_TABLE || b == Blocks.SMOKER || b == Blocks.BLAST_FURNACE || b == Blocks.CHISELED_BOOKSHELF)
			return new double[]{1.5, UTILITY};
		if (id.endsWith("_sign")) return new double[]{2, LIVED};
		if (id.endsWith("_banner") || id.contains("candle") || b == Blocks.CAKE || id.startsWith("potted_") || id.endsWith("_head") || id.endsWith("_skull")
			|| id.endsWith("_bed")) return new double[]{1, LIVED};
		if (id.endsWith("froglight") || id.contains("soul_torch") || id.contains("soul_lantern") || id.contains("copper_torch") || id.contains("copper_lantern")
			|| b == Blocks.SEA_LANTERN || b == Blocks.GLOWSTONE || b == Blocks.SHROOMLIGHT || b == Blocks.TORCH || b == Blocks.WALL_TORCH || b == Blocks.LANTERN)
			return new double[]{0.5, LIGHT};
		if (b == Blocks.NETHER_PORTAL || b == Blocks.END_PORTAL) return new double[]{0, BUILD};   // a portal is a waypoint, not a base
		return new double[]{1, BUILD};
	}

	private final Map<BlockPos, Check> checks = new HashMap<>();
	private final Map<BlockPos, Long> checkedAt = new HashMap<>();

	public Check check(BlockFinder.Base b) {
		long now = System.currentTimeMillis();
		Long at = checkedAt.get(b.center());
		Check c = checks.get(b.center());
		if (c != null && at != null && now - at < 3000) return c;
		long t0 = com.wailsvlr.prism.util.Perf.start();
		c = computeCheck(b);
		com.wailsvlr.prism.util.Perf.end("  BaseValue computeCheck (" + b.playerPos().size() + " blocks)", t0);
		if (checks.size() > 64) { checks.clear(); checkedAt.clear(); }
		checks.put(b.center(), c);
		checkedAt.put(b.center(), now);
		return c;
	}

	/** Which recognisable structures are here, from their tell-tale blocks, the dimension and chest minecarts. */
	private Map<String, String> structures(BlockFinder.Base b) {
		Map<String, Integer> marks = new HashMap<>();
		for (BlockPos p : b.markers()) {
			String m = BlockFinder.MARKERS.get(mc.level.getBlockState(p).getBlock());
			if (m != null) marks.merge(m, 1, Integer::sum);
		}
		int minecartChests = mc.level.getEntities((Entity) null, b.box().inflate(8), e -> e.getType() == EntityType.CHEST_MINECART).size();
		int web = marks.getOrDefault("cobweb", 0), rail = marks.getOrDefault("rail", 0);
		var dim = mc.level.dimension();
		Map<String, String> found = new LinkedHashMap<>();
		if (web >= 6 || minecartChests >= 1 || (rail >= 6 && web >= 1))
			found.put("mineshaft", "mineshaft (" + web + " cobwebs, " + rail + " rails" + (minecartChests > 0 ? ", " + minecartChests + " chest minecarts" : "") + ")");
		if (marks.getOrDefault("mossy cobblestone", 0) >= 4) found.put("dungeon", "dungeon (" + marks.get("mossy cobblestone") + " mossy cobblestone)");
		if (marks.containsKey("end portal frame") || marks.getOrDefault("infested block", 0) >= 2) found.put("stronghold", "stronghold");
		if (marks.containsKey("reinforced deepslate")) found.put("ancient_city", "ancient city");
		if (marks.containsKey("trial spawner") || marks.containsKey("vault")) found.put("trial_chambers", "trial chambers");
		if (marks.containsKey("gilded blackstone")) found.put("bastion", "bastion");
		if (dim == net.minecraft.world.level.Level.NETHER && marks.getOrDefault("nether brick fence", 0) >= 3) found.put("fortress", "nether fortress");
		if (marks.containsKey("suspicious gravel")) found.put("trail_ruins", "trail ruins");
		if (marks.containsKey("suspicious sand")) found.put("desert_pyramid", "desert pyramid");
		if (marks.containsKey("crying obsidian")) found.put("ruined_portal", "ruined portal");
		if (dim == net.minecraft.world.level.Level.END) found.put("end_city", "the End (end cities)");
		if (b.village() || marks.containsKey("bell")) found.put("village", "village");
		return found;
	}

	private Check computeCheck(BlockFinder.Base b) {
		Map<String, String> structures = structures(b);
		String structure = String.join(", ", structures.values());

		// 1. every collected block: does the game generate it here by itself? weight it accordingly
		int n = b.playerPos().size();
		double[] w = new double[n];
		int[] kind = new int[n];
		Block[] blocks = new Block[n];
		boolean[] text = new boolean[n];
		Map<Block, Double> perType = new IdentityHashMap<>();
		// what a block type counts for depends only on the type: worked out once per type, not once per block
		Map<Block, double[]> typeInfo = new IdentityHashMap<>();   // {weight x natural, kind, natural}
		for (int i = 0; i < n; i++) {
			BlockPos p = b.playerPos().get(i);
			Block blk = mc.level.getBlockState(p).getBlock();
			blocks[i] = blk;
			double[] info = typeInfo.computeIfAbsent(blk, t -> {
				Set<String> gen = com.wailsvlr.prism.util.BlockGroups.of(t);
				double nat;
				if (gen == null) nat = 0;                                    // generates everywhere: proves nothing
				else if (gen.isEmpty()) nat = 1;                             // never generates: a player put it there
				else if (gen.stream().anyMatch(structures::containsKey)) nat = 0;   // its structure is right here
				else if (DETECTABLE.containsAll(gen)) nat = 1;               // only in structures that are not here
				else nat = 0.35;                                             // might be a mansion, shipwreck, outpost...
				double[] wk = weigh(t);
				return new double[]{wk[0] * nat, wk[1], nat};
			});
			double natural = info[2];
			kind[i] = (int) info[1];
			w[i] = info[0];
			if (natural > 0 && blk instanceof net.minecraft.world.level.block.SignBlock
				&& mc.level.getBlockEntity(p) instanceof net.minecraft.world.level.block.entity.SignBlockEntity sign && signHasText(sign)) {
				w[i] = 4;
				text[i] = true;
			}
			if (kind[i] == BUILD || kind[i] == LIGHT) perType.merge(blk, w[i], Double::sum);
		}
		// plain building blocks count up to 12 per type: a long wall or a road of concrete is not a base by itself
		for (int i = 0; i < n; i++) {
			if (kind[i] != BUILD && kind[i] != LIGHT) continue;
			double total = perType.getOrDefault(blocks[i], 0.0);
			if (total > 12) w[i] *= 12 / total;
		}

		// 2. the densest spot, on a 4-block grid: the most weight within 12 blocks sideways and 16 up or down
		Map<Long, double[]> cells = new HashMap<>();   // cell -> {weight, x, y, z}
		for (int i = 0; i < n; i++) {
			if (w[i] <= 0) continue;
			BlockPos p = b.playerPos().get(i);
			long key = BlockPos.asLong(p.getX() >> 2, p.getY() >> 2, p.getZ() >> 2);
			cells.computeIfAbsent(key, k -> new double[]{0, p.getX() >> 2, p.getY() >> 2, p.getZ() >> 2})[0] += w[i];
		}
		double dense = 0;
		double[] best = null;
		if (!cells.isEmpty()) {
			int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, y1 = Integer.MIN_VALUE, z1 = Integer.MIN_VALUE;
			for (double[] c : cells.values()) {
				x0 = Math.min(x0, (int) c[1]); x1 = Math.max(x1, (int) c[1]);
				y0 = Math.min(y0, (int) c[2]); y1 = Math.max(y1, (int) c[2]);
				z0 = Math.min(z0, (int) c[3]); z1 = Math.max(z1, (int) c[3]);
			}
			int sx = x1 - x0 + 1, sy = y1 - y0 + 1, sz = z1 - z0 + 1;
			if ((long) (sx + 1) * (sy + 1) * (sz + 1) <= 4_000_000L) {
				// S[x][y][z] = sum of all cells with smaller-or-equal coordinates; a window sum is then 8 lookups
				int px = sx + 1, py = sy + 1, pz = sz + 1;
				double[] S = new double[px * py * pz];
				for (double[] c : cells.values())
					S[((((int) c[1] - x0 + 1) * py) + ((int) c[2] - y0 + 1)) * pz + ((int) c[3] - z0 + 1)] += c[0];
				for (int x = 1; x < px; x++)
					for (int y = 1; y < py; y++)
						for (int z = 1; z < pz; z++) {
							int i = (x * py + y) * pz + z;
							S[i] += S[((x - 1) * py + y) * pz + z] + S[(x * py + (y - 1)) * pz + z] + S[(x * py + y) * pz + (z - 1)]
								- S[((x - 1) * py + (y - 1)) * pz + z] - S[((x - 1) * py + y) * pz + (z - 1)] - S[(x * py + (y - 1)) * pz + (z - 1)]
								+ S[((x - 1) * py + (y - 1)) * pz + (z - 1)];
						}
				for (double[] a : cells.values()) {
					int ax = (int) a[1] - x0 + 1, ay = (int) a[2] - y0 + 1, az = (int) a[3] - z0 + 1;
					int xa = Math.max(0, ax - 4), xb = Math.min(sx, ax + 3), ya = Math.max(0, ay - 5), yb = Math.min(sy, ay + 4), za = Math.max(0, az - 4), zb = Math.min(sz, az + 3);
					double sum = S[(xb * py + yb) * pz + zb] - S[(xa * py + yb) * pz + zb] - S[(xb * py + ya) * pz + zb] - S[(xb * py + yb) * pz + za]
						+ S[(xa * py + ya) * pz + zb] + S[(xa * py + yb) * pz + za] + S[(xb * py + ya) * pz + za] - S[(xa * py + ya) * pz + za];
					if (sum > dense) { dense = sum; best = a; }
				}
			} else {
				// a huge, sparse spread: look the neighbours up instead (at most 7 x 9 x 7 per cell)
				for (double[] a : cells.values()) {
					double sum = 0;
					for (int dx = -3; dx <= 3; dx++) for (int dy = -4; dy <= 4; dy++) for (int dz = -3; dz <= 3; dz++) {
						double[] c = cells.get(BlockPos.asLong((int) a[1] + dx, (int) a[2] + dy, (int) a[3] + dz));
						if (c != null) sum += c[0];
					}
					if (sum > dense) { dense = sum; best = a; }
				}
			}
		}

		// 3. what is in that core
		double[] perKind = new double[KIND_NAMES.length];
		Map<String, Integer> names = new HashMap<>();
		Map<String, Double> nameWeight = new HashMap<>();
		int shulkers = 0, ender = 0, beacons = 0, signs = 0;
		AABB tight = null;   // just the base: around the evidence connected to the densest spot
		Set<Long> joined = new HashSet<>();
		if (best != null) {
			// the base is the heaviest group of touching 4-block cells (gaps under 4 blocks) in the densest window:
			// a separate build or a stray block 8+ blocks away does not stretch the box
			int bx = (int) best[1], by = (int) best[2], bz = (int) best[3];
			Set<Long> seenCells = new HashSet<>();
			double heaviest = -1;
			for (Map.Entry<Long, double[]> start : cells.entrySet()) {
				double[] s0 = start.getValue();
				if (Math.abs(s0[1] - bx) > 3 || Math.abs(s0[3] - bz) > 3 || Math.abs(s0[2] - by) > 4 || !seenCells.add(start.getKey())) continue;
				Set<Long> group = new HashSet<>();
				group.add(start.getKey());
				double weight = 0;
				java.util.ArrayDeque<double[]> queue = new java.util.ArrayDeque<>();
				queue.add(s0);
				while (!queue.isEmpty()) {
					double[] c = queue.poll();
					weight += c[0];
					for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
						long k = BlockPos.asLong((int) c[1] + dx, (int) c[2] + dy, (int) c[3] + dz);
						double[] nb = cells.get(k);
						if (nb == null || Math.abs(nb[1] - bx) > 3 || Math.abs(nb[3] - bz) > 3 || Math.abs(nb[2] - by) > 4) continue;
						if (group.add(k)) { seenCells.add(k); queue.add(nb); }
					}
				}
				if (weight > heaviest) { heaviest = weight; joined = group; }
			}
			int cx = (int) best[1] * 4, cy = (int) best[2] * 4, cz = (int) best[3] * 4;
			AABB core = new AABB(cx - 12, cy - 16, cz - 12, cx + 16, cy + 20, cz + 16);
			for (int i = 0; i < n; i++) {
				if (w[i] <= 0 || !core.contains(b.playerPos().get(i).getCenter())) continue;
				BlockPos at = b.playerPos().get(i);
				if (joined.contains(BlockPos.asLong(at.getX() >> 2, at.getY() >> 2, at.getZ() >> 2))) {
					AABB one = new AABB(at);
					tight = tight == null ? one : tight.minmax(one);
				}
				perKind[kind[i]] += w[i];
				Block blk = blocks[i];
				if (blk instanceof ShulkerBoxBlock) shulkers++;
				if (blk == Blocks.ENDER_CHEST) ender++;
				if (blk == Blocks.BEACON) beacons++;
				if (text[i]) signs++;
				String name = blk instanceof ShulkerBoxBlock ? "Shulker Box" : text[i] ? "sign with text" : blk.getName().getString();
				names.merge(name, 1, Integer::sum);
				nameWeight.merge(name, w[i], Double::sum);
			}
			// what players leave around: frames, stands, paintings, named, leashed or tamed animals, trading halls, pens
			int frames = 0, stands = 0, paintings = 0, pets = 0, villagers = 0, crystals = 0;
			Map<EntityType<?>, Integer> animals = new HashMap<>();
			for (Entity e : mc.level.getEntities((Entity) null, core, e -> true)) {
				if (e instanceof ItemFrame) frames++;
				else if (e instanceof ArmorStand) stands++;
				else if (e instanceof net.minecraft.world.entity.decoration.painting.Painting) paintings++;
				else if (e instanceof net.minecraft.world.entity.boss.enderdragon.EndCrystal) crystals++;
				else if (e instanceof net.minecraft.world.entity.npc.villager.Villager) villagers++;
				if (e instanceof net.minecraft.world.entity.Mob m && !(e instanceof ArmorStand)) {
					if (m.hasCustomName() || m.isLeashed() || (m instanceof net.minecraft.world.entity.TamableAnimal t && t.isTame())) pets++;
					if (m instanceof net.minecraft.world.entity.animal.Animal) animals.merge(m.getType(), 1, Integer::sum);
				}
			}
			int pens = (int) animals.values().stream().filter(c -> c >= 10).count();
			boolean hall = villagers >= 4 && !structures.containsKey("village");
			boolean crystalsCount = crystals > 0 && mc.level.dimension() != net.minecraft.world.level.Level.END;
			double ent = frames * 2 + stands * 2 + paintings + pets * 3 + pens * 4 + (hall ? 8 : 0) + (crystalsCount ? 3 * crystals : 0);
			perKind[LIVED] += ent;
			dense += ent;
			if (frames > 0) { names.put("item frame", frames); nameWeight.put("item frame", frames * 2.0); }
			if (stands > 0) { names.put("armor stand", stands); nameWeight.put("armor stand", stands * 2.0); }
			if (paintings > 0) { names.put("painting", paintings); nameWeight.put("painting", (double) paintings); }
			if (pets > 0) { names.put("named/tamed mob", pets); nameWeight.put("named/tamed mob", pets * 3.0); }
			if (pens > 0) { names.put("animal pen", pens); nameWeight.put("animal pen", pens * 4.0); }
			if (hall) { names.put("villager trading hall", 1); nameWeight.put("villager trading hall", 8.0); }
			if (crystalsCount) { names.put("end crystal", crystals); nameWeight.put("end crystal", 3.0 * crystals); }
		}
		int kinds = 0;
		for (int k = 0; k < BUILD; k++) if (perKind[k] >= 3) kinds++;
		double decisive = perKind[STORAGE] + perKind[REDSTONE] + perKind[UTILITY] + perKind[LIVED];
		boolean strong = shulkers >= 2 || (ender >= 1 && shulkers >= 1) || beacons >= 1 || signs >= 2;
		String evidence = names.entrySet().stream()
			.sorted((x, y) -> Double.compare(nameWeight.getOrDefault(y.getKey(), 0.0), nameWeight.getOrDefault(x.getKey(), 0.0))).limit(5)
			.map(e -> e.getValue() + " " + e.getKey()).reduce((x, y) -> x + ", " + y).orElse("nothing player-made");
		StringBuilder ch = new StringBuilder();
		for (int k = 0; k < KIND_NAMES.length; k++) if (perKind[k] >= 1) ch.append(ch.length() > 0 ? " " : "").append(KIND_NAMES[k]).append(' ').append(Math.round(perKind[k]));
		String channels = ch.toString();
		double need = minPoints.get();

		// 4. verdict
		String why = null;
		if (skipVillages.isOn() && structures.containsKey("village") && !strong && decisive < need) why = "village";
		else if (dense < need) why = structure.isEmpty()
			? "only " + Math.round(dense) + " points of player-made blocks close together (needs " + Math.round(need) + ")"
			: structure + ", only " + Math.round(dense) + " points of player-made blocks beyond it";
		else if (decisive < 6 && !strong) why = "only building blocks (" + evidence + "), no storage, redstone, utility or signs of living";
		else if (kinds < 2 && !strong) why = "only one kind of player activity (" + channels + ")";
		if (why != null) return new Check(false, 0, "Not a base: " + why, evidence, dense, kinds, structure, channels, tight);
		double c = 40 + 25 * Math.min(1, (dense - need) / (need * 3)) + 7 * Math.min(4, kinds - 1) + (strong ? 8 : 0) + Math.min(6, signs * 2)
			+ Math.min(6, decisive / 10) - (structure.isEmpty() ? 0 : 12);
		int conf = (int) Math.max(50, Math.min(99, Math.round(c)));
		return new Check(true, conf, "Real base " + conf + "%", evidence, dense, kinds, structure, channels, tight);
	}

	private static boolean signHasText(net.minecraft.world.level.block.entity.SignBlockEntity sign) {
		for (boolean front : new boolean[]{true, false})
			for (Component line : sign.getText(front).getMessages(false))
				if (!line.getString().isBlank()) return true;
		return false;
	}

	public BlockFinder.Base current() { return current; }

	/** Why the base you look at is not counted ("Not a base: mineshaft (12 cobwebs, 14 rails)"), or null. */
	public String rejected() { return rejected; }

	/** "~$8.4M +~$6M?" for a base ("$?" when nothing in it has an auction price yet). */
	public String worthText(BlockFinder.Base b) {
		Value v = valueOf(b);
		if (v.known() <= 0 && v.estimate() <= 0) return v.unpriced() > 0 ? "$? (" + v.unpriced() + " without an auction price)" : "nothing valuable seen";
		return "~$" + Money.format(v.known()) + (v.estimate() > 0 ? " +~$" + Money.format(v.estimate()) + "?" : "");
	}

	public boolean showHud() { return isEnabled() && hud.isOn() && mc.level != null && (current != null || rejected != null); }

	/** The HUD line: {title value, detail}. */
	public String[] hudLine() {
		BlockFinder.Base b = current;
		if (b == null) return new String[]{"no", rejected == null ? "" : rejected};
		Value v = valueOf(b);
		Check c = check(b);
		int dist = (int) Math.round(drawBox(b, c).getCenter().distanceTo(mc.player.position()));
		String detail = c.verdict() + "  " + dist + "m" + (b.status().isEmpty() ? "" : " - " + b.status())
			+ (v.summary().isEmpty() ? "" : "  " + v.summary()) + "  | " + c.evidence() + "  [" + c.channels() + "]"
			+ (c.structure().isEmpty() ? "" : "  (inside " + c.structure() + ")");
		return new String[]{worthText(b), detail};
	}

	/** The box drawn for a base: tight around its core (the base itself), else BlockFinder's whole area. */
	public AABB drawBox(BlockFinder.Base b, Check c) { return c.core() != null ? c.core().inflate(1) : b.box(); }

	/** The box and label around the base you look at. */
	public void collect(java.util.List<com.wailsvlr.prism.render.Highlight> out) {
		BlockFinder.Base b = current;
		if (!isEnabled() || !box.isOn() || b == null || mc.player == null) return;
		var st = new com.wailsvlr.prism.render.HighlightStyle("Outline", 0.95f, 0, 2f, true, false, 0, false, 1e9, true, 0.6f, 0.5, false);
		Check c = check(b);
		AABB area = drawBox(b, c);
		int dist = (int) Math.round(area.getCenter().distanceTo(mc.player.position()));
		out.add(new com.wailsvlr.prism.render.Highlight(area, 0xFFC21A, st, "Base: " + worthText(b) + " - " + c.verdict()
			+ (b.status().isEmpty() ? "" : " - " + b.status()) + " - " + dist + "m"));
	}

	private void learnTick() {
		if (!learn.isOn()) return;
		if (!(mc.screen instanceof AbstractContainerScreen<?> scr) || lastUsed == null) { openTicks = 0; return; }
		if (!(scr.getMenu() instanceof ChestMenu) && !(scr.getMenu() instanceof ShulkerBoxMenu)) return;
		// only a menu that opened right after clicking the block is that block's (not /sell or a server menu later)
		if (++openTicks == 1 && System.currentTimeMillis() - lastUsedAt > 1500) { lastUsed = null; return; }
		if (openTicks != 5) return;   // let the contents arrive
		Block block = mc.level.getBlockState(lastUsed).getBlock();
		String kind = block instanceof ShulkerBoxBlock ? "shulker" : block == Blocks.CHEST || block == Blocks.TRAPPED_CHEST || block == Blocks.BARREL ? "chest" : null;
		if (kind == null || learnedHere.contains(lastUsed)) return;
		BlockFinder bf = PrismClient.modules().get(BlockFinder.class);
		boolean inBase = false;
		for (BlockFinder.Base b : bf.bases()) if (b.box().inflate(2).contains(lastUsed.getCenter())) { inBase = true; break; }
		if (!inBase) return;   // only base containers teach what base containers hold
		double value = 0;
		int slots = 0;
		for (Slot slot : scr.getMenu().slots) {
			if (slot.container instanceof Inventory) continue;
			slots++;
			Double p = itemPrice(slot.getItem());
			if (p != null) value += p * slot.getItem().getCount();
		}
		int blocks = Math.max(1, slots / 27);   // a double chest is two chest blocks
		learnedHere.add(lastUsed);
		lastUsed = null;
		double[] t = learned.computeIfAbsent(kind, k -> new double[2]);
		t[0] += value;
		t[1] += blocks;
		cache.clear();
		saveLearned();
	}

	public double[] learned(String kind) { return learned.get(kind); }

	public static Path file() { return FabricLoader.getInstance().getConfigDir().resolve("prismclient-loot.json"); }

	private void load() {
		try {
			if (!Files.exists(file())) return;
			JsonObject o = JsonParser.parseString(Files.readString(file(), StandardCharsets.UTF_8)).getAsJsonObject();
			for (String k : o.keySet()) {
				var a = o.getAsJsonArray(k);
				learned.put(k, new double[]{a.get(0).getAsDouble(), a.get(1).getAsDouble()});
			}
		} catch (Exception e) {
			PrismClient.LOG.warn("Could not read {}: {}", file(), e.toString());
		}
	}

	private void saveLearned() {
		JsonObject o = new JsonObject();
		learned.forEach((k, v) -> { var a = new com.google.gson.JsonArray(); a.add(v[0]); a.add(v[1]); o.add(k, a); });
		try {
			Files.writeString(file(), o.toString(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			PrismClient.LOG.warn("Could not save {}: {}", file(), e.toString());
		}
	}
}
