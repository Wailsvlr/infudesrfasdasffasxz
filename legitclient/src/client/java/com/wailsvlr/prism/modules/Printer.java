package com.wailsvlr.prism.modules;

import com.wailsvlr.prism.module.Category;
import com.wailsvlr.prism.module.Module;
import com.wailsvlr.prism.render.Highlight;
import com.wailsvlr.prism.render.HighlightStyle;
import com.wailsvlr.prism.setting.BoolSetting;
import com.wailsvlr.prism.setting.ModeSetting;
import com.wailsvlr.prism.setting.NumberSetting;
import com.wailsvlr.prism.setting.SectionSetting;
import com.wailsvlr.prism.setting.TextSetting;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ComparatorMode;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Printer: builds a Litematica schematic (.litematic from the game folder's schematics/) by itself. Every tick it
 * looks at the schematic blocks within reach, takes the right item from the inventory and places it so that it ends
 * up with the schematic's orientation: for every candidate (which neighbour face to click, where on it, which way to
 * look) it asks the block's own placement code what state that would give, and uses the first candidate that matches
 * (facing, axis, half, slab type, attach face, sign rotation...). Settings the block keeps after placement are fixed
 * by right-clicking: repeater delay, comparator mode, note block pitch, lever, doors, trapdoors and gates, daylight
 * detector. Missing blocks are drawn cyan, wrong ones red. Redstone parts go last so they have something to stand on.
 */
public class Printer extends Module {
	private final TextSetting file, origin;
	private final NumberSetting range, perTick, delay;
	private final ModeSetting mode, order;
	private final BoolSetting hudPanel, alerts;
	/** One line of the material list: how many of an item the unbuilt part still needs, and how many you carry. */
	public record Material(Item item, int need, int have) {
		public int short_() { return Math.max(0, need - have); }
	}
	private volatile List<Material> materials = List.of();
	private boolean finishedSaid;
	/** In Layers order: the height being built now (Integer.MIN_VALUE when not in use). */
	private volatile int layerY = Integer.MIN_VALUE;
	private final BoolSetting fixStates, redstoneLast, showMissing, showWrong, swapFromInventory, airPlace, legitAim, lookAtBlock;
	/** A placement waiting one tick so the server has the new look direction before the click. */
	private Plan pending;
	private BlockPos pendingPos;
	private static Printer instance;
	private BoolSetting easyPlace;
	private BoolSetting guideOn;
	/** What the schematic needs besides blocks (crafters, entities, water...). */
	private final PrinterGuide guide = new PrinterGuide();
	/** Easy Place: the spot you right-clicked, placed over the next ticks (look, then click). */
	private BlockPos easyTarget;
	private long easySince;
	/** The right click that placed something is still held: it must not go on to open the chest or furnace it just placed. */
	private boolean easyHeld;
	/** Placements sent to the server, checked a moment later: did the block stay? */
	private final Map<BlockPos, Long> sent = new HashMap<>();
	/** Spots where the server took back an air placement: only neighbour clicks there from now on. */
	private final java.util.Set<BlockPos> noAir = new java.util.HashSet<>();
	private final java.util.Set<Item> warned = new java.util.HashSet<>();
	/** Extra ticks between placements, raised when the server refuses, lowered after a run of accepted ones. */
	private int extraDelay, streak, refused;
	private String reason = "";
	/**
	 * The plan has to be clicked while sneaking (against a chest, hopper, door...; or so a chest does not join its
	 * neighbour). Sneaking lowers the eyes, so the printer crouches first and plans the aim again from there.
	 */
	private boolean planSneak, sneakHeld, standingUp;
	private final Map<BlockPos, Integer> flips = new HashMap<>();
	/** Read by KeyboardInputMixin: the player's input says "sneaking" while this is set. */
	public static volatile boolean forceSneak;
	private long lastSneakNeed;
	/** Spots the server took back recently: tried again only after a short wait, other blocks go first. */
	private final Map<BlockPos, Long> retryAfter = new HashMap<>();

	/** Schematic blocks in world coordinates (air included, so the printer knows what should stay empty). */
	private final Map<BlockPos, BlockState> blocks = new HashMap<>();
	private String loadedFile = "", loadedOrigin = "";
	private int cooldown, placed, total, missing, wrong;
	private final Map<BlockPos, Integer> clicked = new HashMap<>();

	private static final List<Property<?>> ORIENTATION = List.of(
		BlockStateProperties.FACING, BlockStateProperties.HORIZONTAL_FACING, BlockStateProperties.FACING_HOPPER,
		BlockStateProperties.AXIS, BlockStateProperties.HORIZONTAL_AXIS, BlockStateProperties.HALF,
		BlockStateProperties.SLAB_TYPE, BlockStateProperties.ATTACH_FACE, BlockStateProperties.ROTATION_16,
		BlockStateProperties.ORIENTATION, BlockStateProperties.VERTICAL_DIRECTION, BlockStateProperties.RAIL_SHAPE_STRAIGHT,
		BlockStateProperties.DOOR_HINGE, BlockStateProperties.BED_PART, BlockStateProperties.CHEST_TYPE);

	private static final Set<Block> REDSTONE = Set.of(Blocks.REDSTONE_WIRE, Blocks.REPEATER, Blocks.COMPARATOR, Blocks.REDSTONE_TORCH,
		Blocks.REDSTONE_WALL_TORCH, Blocks.OBSERVER, Blocks.PISTON, Blocks.STICKY_PISTON, Blocks.LEVER, Blocks.STONE_BUTTON,
		Blocks.OAK_BUTTON, Blocks.DISPENSER, Blocks.DROPPER, Blocks.HOPPER, Blocks.POWERED_RAIL, Blocks.DETECTOR_RAIL,
		Blocks.ACTIVATOR_RAIL, Blocks.RAIL, Blocks.REDSTONE_LAMP, Blocks.TARGET, Blocks.DAYLIGHT_DETECTOR, Blocks.TRIPWIRE_HOOK,
		Blocks.NOTE_BLOCK, Blocks.CRAFTER);

	/**
	 * A placement the server took back (the spot is empty again ~10 ticks later) slows the printer down and stops air
	 * placing at that spot; a run of 8 accepted ones speeds it up again. Singleplayer never refuses, so it stays fast.
	 */
	private void verify() {
		long now = mc.level.getGameTime();
		var it = sent.entrySet().iterator();
		while (it.hasNext()) {
			var e = it.next();
			if (now - e.getValue() < 10) continue;
			it.remove();
			BlockState want = blocks.get(e.getKey());
			if (want == null) continue;
			if (mc.level.getBlockState(e.getKey()).getBlock() == want.getBlock()) {
				if (++streak >= 4 && extraDelay > 0) {
					extraDelay--;
					streak = 0;
				}
			} else {
				streak = 0;
				refused++;
				noAir.add(e.getKey());
				extraDelay = Math.min(6, extraDelay + 1);
				retryAfter.put(e.getKey(), now + 40);
				reason = "server refused, slowing down";
				if (refused == 1 || refused % 10 == 0) say("The server took back a placement (" + refused + " so far): slowing down, no air placing there.", ChatFormatting.YELLOW);
			}
		}
	}

	@Override
	protected void onDisable() {
		forceSneak = false;
		sneakHeld = false;
		pending = null;
	}

	public Printer() {
		super("Printer", "Builds a .litematic schematic by itself: places the blocks within reach with the right direction "
			+ "and sets repeaters, comparators, levers and note blocks", Category.WORLD);
		instance = this;
		add(new SectionSetting("Schematic"));
		file = add(new TextSetting("File", "File name in the game folder's schematics/ folder (e.g. farm.litematic)", "", "farm.litematic"));
		origin = add(new TextSetting("Origin", "Where the schematic's origin goes: x y z. Empty: your feet when the module is turned on",
			"", "x y z (empty = your feet)"));
		add(new SectionSetting("Placing"));
		mode = add(new ModeSetting("Mode", "Auto: Legit on servers, Free in singleplayer. Legit: like a player (looks at each block, clicks only "
			+ "faces it can see, never in the air; for anti-cheat servers). Free: any angle, air placing, fastest (singleplayer)", "Auto", "Auto", "Legit", "Free"));
		range = add(new NumberSetting("Range", "How far away blocks are placed", 4.5, 2, 5.5, 0.5, " blocks"));
		perTick = add(new NumberSetting("Blocks Per Tick", "Blocks placed each tick (servers may kick for too many)", 1, 1, 4, 1, ""));
		delay = add(new NumberSetting("Delay", "Extra ticks to wait between placements (0 = as fast as possible)", 0, 0, 10, 1, " ticks"));
		order = add(new ModeSetting("Build Order", "Nearest: whatever is in reach, low to high. Layers: finish each height before "
			+ "starting the next one (walk along as it builds)", "Nearest", "Nearest", "Layers"));
		swapFromInventory = add(new BoolSetting("Use Inventory", "Move items from the inventory to the hotbar when they are not in it", true));
		lookAtBlock = add(new BoolSetting("Look At Block", "In Legit mode: really turn your view to each block, place it, go to the next, like placing by hand", true));
		legitAim = add(new BoolSetting("Legit Aim", "In Legit mode: look exactly at the face that is clicked, only faces you can see", true));
		easyPlace = add(new BoolSetting("Easy Place", "Do not place by itself: right-click a missing block of the schematic (or the face of the "
			+ "block next to it) and the right block goes there, turned the right way. Hold right click to keep placing. You decide every block", false));
		airPlace = add(new BoolSetting("Air Place", "Click the target spot itself when no neighbour gives the right direction (logs, trapdoors). Vanilla allows it; anti-cheats do not. Always on in Free mode", false));
		redstoneLast = add(new BoolSetting("Redstone Last", "Place redstone parts after the other blocks in reach", true));
		fixStates = add(new BoolSetting("Fix States", "Right-click repeaters, comparators, levers, note blocks, doors until they match", true));
		add(new SectionSetting("Display"));
		showMissing = add(new BoolSetting("Show Missing", "Cyan boxes where a block is still missing", true));
		guideOn = add(new BoolSetting("Setup Guide", "What the schematic needs besides its blocks: crafter slots and starting items, minecarts, boats "
			+ "and frames to place, water and kelp to add, levers to flip. Shown in the panel and the world; open a crafter to see which slots to lock", true));
		hudPanel = add(new BoolSetting("HUD Panel", "Progress and the materials still needed (counted against your inventory) in the HUD", true));
		alerts = add(new BoolSetting("Alerts", "A card in the corner and a sound when the build is finished or a block runs out", true));
		showWrong = add(new BoolSetting("Show Wrong", "Red boxes where a different block or direction is in the way", true));
	}

	/** Legit placing: on a server in Auto mode, or when chosen. */
	private boolean legit() {
		return mode.is("Legit") || mode.is("Auto") && !mc.isLocalServer();
	}

	private boolean useLegitAim() {
		return legit() && legitAim.get();
	}

	private boolean turnView() {
		return legit() && lookAtBlock.get();
	}

	private boolean useAirPlace() {
		return !legit() && (airPlace.get() || !mode.is("Legit"));
	}

	@Override
	public String getInfo() {
		if (total == 0) return null;
		return placed + "/" + total + (reason.isEmpty() ? "" : " - " + reason) + (extraDelay > 0 ? " (slowed +" + extraDelay + ")" : "");
	}

	public int missing() { return missing; }
	public int placed() { return placed; }
	public String reason() { return reason; }
	public List<Material> materials() { return materials; }
	public int layer() { return layerY; }
	public boolean showHud() { return isEnabled() && hudPanel.isOn() && total > 0; }
	public int wrong() { return wrong; }
	public int total() { return total; }

	@Override
	protected void onEnable() {
		if (mc.player != null && origin.get().isBlank()) {
			BlockPos p = mc.player.blockPosition();
			origin.set(p.getX() + " " + p.getY() + " " + p.getZ());
		}
		loadedFile = "";
		clicked.clear();
		sent.clear();
		noAir.clear();
		warned.clear();
		extraDelay = streak = refused = 0;
		retryAfter.clear();
		flips.clear();
		standingUp = false;
		reason = "";
	}

	private void say(String s, ChatFormatting color) {
		if (mc.player != null) mc.gui.getChat().addMessage(Component.literal("[Printer] ").withStyle(ChatFormatting.LIGHT_PURPLE).append(Component.literal(s).withStyle(color)));
	}

	/** Loads the file again when the file name or origin changed. */
	private boolean ensureLoaded() {
		if (file.get().equals(loadedFile) && origin.get().equals(loadedOrigin)) return !blocks.isEmpty();
		loadedFile = file.get();
		loadedOrigin = origin.get();
		blocks.clear();
		if (loadedFile.isBlank()) {
			say("Set a schematic file name in the Printer settings.", ChatFormatting.YELLOW);
			return false;
		}
		BlockPos o;
		try {
			String[] p = loadedOrigin.trim().split("[\\s,]+");
			o = new BlockPos(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]));
		} catch (Exception e) {
			say("Origin must be three numbers: x y z", ChatFormatting.RED);
			return false;
		}
		String name = loadedFile.endsWith(".litematic") ? loadedFile : loadedFile + ".litematic";
		Path path = mc.gameDirectory.toPath().resolve("schematics").resolve(name);
		try {
			if (!Files.exists(path)) {
				say("Not found: schematics/" + name, ChatFormatting.RED);
				return false;
			}
			CompoundTag root = NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
			read(root, o, blocks);
			guide.load(root, o);
			say("Loaded " + name + ": " + blocks.values().stream().filter(s -> !s.isAir()).count() + " blocks at " + o.toShortString(), ChatFormatting.GREEN);
		} catch (Exception e) {
			say("Could not read " + name + ": " + e.getMessage(), ChatFormatting.RED);
			blocks.clear();
			guide.clear();
		}
		return !blocks.isEmpty();
	}

	/** Litematica format: Regions -> Position, Size (may be negative), BlockStatePalette, BlockStates (packed longs). */
	public static void read(CompoundTag root, BlockPos origin, Map<BlockPos, BlockState> out) {
		var lookup = mc.level != null ? mc.level.holderLookup(Registries.BLOCK) : net.minecraft.core.registries.BuiltInRegistries.BLOCK;
		CompoundTag regions = root.getCompoundOrEmpty("Regions");
		for (String key : regions.keySet()) {
			CompoundTag r = regions.getCompoundOrEmpty(key);
			CompoundTag pos = r.getCompoundOrEmpty("Position"), size = r.getCompoundOrEmpty("Size");
			int px = pos.getIntOr("x", 0), py = pos.getIntOr("y", 0), pz = pos.getIntOr("z", 0);
			int sx = size.getIntOr("x", 0), sy = size.getIntOr("y", 0), sz = size.getIntOr("z", 0);
			int ax = Math.abs(sx), ay = Math.abs(sy), az = Math.abs(sz);
			// the region's minimum corner relative to the schematic origin
			int mx = sx < 0 ? px + sx + 1 : px, my = sy < 0 ? py + sy + 1 : py, mz = sz < 0 ? pz + sz + 1 : pz;
			List<BlockState> palette = new ArrayList<>();
			for (Tag t : r.getListOrEmpty("BlockStatePalette")) {
				palette.add(t instanceof CompoundTag c ? NbtUtils.readBlockState(lookup, c) : Blocks.AIR.defaultBlockState());
			}
			long[] data = r.getLongArray("BlockStates").orElse(new long[0]);
			int bits = Math.max(2, 32 - Integer.numberOfLeadingZeros(Math.max(1, palette.size() - 1)));
			long mask = (1L << bits) - 1;
			long volume = (long) ax * ay * az;
			for (long i = 0; i < volume; i++) {
				long start = i * bits;
				int si = (int) (start >> 6), ei = (int) (((i + 1) * bits - 1) >> 6);
				int off = (int) (start & 63);
				if (ei >= data.length) break;
				long v = si == ei ? (data[si] >>> off) & mask : ((data[si] >>> off) | (data[ei] << (64 - off))) & mask;
				BlockState st = v < palette.size() ? palette.get((int) v) : Blocks.AIR.defaultBlockState();
				int x = (int) (i % ax), z = (int) ((i / ax) % az), y = (int) (i / ((long) ax * az));
				out.put(origin.offset(mx + x, my + y, mz + z), st);
			}
		}
	}

	// ------------------------------------------------------------------ the tick
	@Override
	public void onTick() {
		if (guideOn.isOn()) guide.screenTick();   // a crafter screen may be open
		if (mc.player == null || mc.level == null || mc.gameMode == null || mc.screen != null) return;
		if (!ensureLoaded()) return;
		Vec3 eye = mc.player.getEyePosition();
		double r = range.get();
		List<BlockPos> todo = new ArrayList<>();
		int ok = 0, all = 0, miss = 0, bad = 0, lowest = Integer.MAX_VALUE;
		Map<Item, Integer> need = new HashMap<>();
		for (var e : blocks.entrySet()) {
			BlockState want = e.getValue();
			if (want.isAir()) continue;
			all++;
			BlockState have = mc.level.getBlockState(e.getKey());
			if (matches(have, want)) {
				ok++;
				continue;
			}
			if (have.getBlock() == want.getBlock() || have.canBeReplaced()) {
				miss++;
				if (have.getBlock() != want.getBlock()) {
					// still to be placed: it needs an item, and it counts for the lowest unfinished layer
					Item it = itemFor(want);
					if (it != net.minecraft.world.item.Items.AIR) need.merge(it, 1, Integer::sum);   // water, kelp...: in the setup guide instead
					if (mc.level.isLoaded(e.getKey())) lowest = Math.min(lowest, e.getKey().getY());
				}
			} else bad++;
			if (Vec3.atCenterOf(e.getKey()).distanceTo(eye) <= r + 0.8) todo.add(e.getKey());
		}
		placed = ok;
		total = all;
		missing = miss;
		wrong = bad;
		materials = materialList(need);
		if (guideOn.isOn()) guide.tick(blocks, mc.level.getGameTime());
		for (Material m : materials) if (m.have() > 0 && swapFromInventory.get()) warned.remove(m.item());
		layerY = order.is("Layers") && lowest != Integer.MAX_VALUE ? lowest : Integer.MIN_VALUE;
		if (layerY != Integer.MIN_VALUE) {
			int ly = layerY;
			todo.removeIf(p -> p.getY() > ly);
		}
		if (todo.isEmpty() && miss + bad > 0) reason = layerY != Integer.MIN_VALUE ? "layer Y " + layerY + ": walk closer" : "nothing in reach";
		if (all > 0 && ok == all) {
			if (!finishedSaid) {
				finishedSaid = true;
				reason = "";
				if (alerts.isOn()) {
					com.wailsvlr.prism.gui.Notifications.post("printer-done", "check", "Printer", "Finished: all " + all + " blocks are in place");
					mc.player.playSound(net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP, 0.5f, 1.4f);
				}
			}
		} else finishedSaid = false;
		verify();
		if (pending != null) {
			// second half of a placement: the look direction was sent last tick
			BlockState want = blocks.get(pendingPos);
			BlockState now = mc.level.getBlockState(pendingPos);
			if (want != null && (now.canBeReplaced() || now.getBlock() == want.getBlock())) {
				Item item = itemFor(want);
				if (holdItem(item) && mc.player.getMainHandItem().is(item)) {
					use(pending);
					sent.put(pendingPos, mc.level.getGameTime());
				}
			}
			pending = null;
			cooldown = delay.getInt() + extraDelay;
			if (cooldown > 0) return;
			// no wait: plan the next block now, so its look goes out with the next tick's movement packet
		}
		long tick = mc.level.getGameTime();
		if (sneakHeld && tick - lastSneakNeed > 10) {
			// nothing needed sneaking for a while: stand up, and plan from standing eyes from the next tick
			forceSneak = false;
			sneakHeld = false;
			return;
		}
		if (sneakHeld && !mc.player.isCrouching()) return;     // still going down
		if (standingUp) {
			if (mc.player.isCrouching()) return;                 // still getting up
			standingUp = false;
		}
		if (!mc.options.keyUse.isDown()) easyHeld = false;
		if (easyPlace.isOn()) {
			// nothing by itself: only the spot you right-clicked
			easyTick(tick);
			return;
		}
		if (cooldown > 0) {
			cooldown--;
			return;
		}
		retryAfter.values().removeIf(t -> t <= tick);
		boolean rl = redstoneLast.get();
		todo.sort(Comparator.<BlockPos>comparingInt(p -> rl && REDSTONE.contains(blocks.get(p).getBlock()) ? 1 : 0)
			.thenComparingInt(BlockPos::getY).thenComparingDouble(p -> -Vec3.atCenterOf(p).distanceToSqr(eye)));
		int done = 0;
		if (!todo.isEmpty()) reason = "";
		for (BlockPos p : todo) {
			if (done >= perTick.getInt()) break;
			if (retryAfter.containsKey(p)) continue;
			BlockState want = blocks.get(p), have = mc.level.getBlockState(p);
			if (have.getBlock() == want.getBlock()) {
				if (fixStates.get() && fix(p, have, want)) done++;
				else if (want.hasProperty(BlockStateProperties.SLAB_TYPE) && want.getValue(BlockStateProperties.SLAB_TYPE) == SlabType.DOUBLE && place(p, want)) break;
			} else if (have.canBeReplaced() && place(p, want)) {
				break;    // one placement at a time: it finishes next tick
			}
		}
		if (done > 0) cooldown = delay.getInt() + extraDelay;
	}

	/** What the unbuilt part needs against what you carry; the items you are short of first. */
	private List<Material> materialList(Map<Item, Integer> need) {
		if (need.isEmpty()) return List.of();
		var inv = mc.player.getInventory();
		boolean creative = mc.player.getAbilities().instabuild;
		Map<Item, Integer> have = new HashMap<>();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack st = inv.getItem(i);
			if (!st.isEmpty() && need.containsKey(st.getItem())) have.merge(st.getItem(), st.getCount(), Integer::sum);
		}
		List<Material> out = new ArrayList<>();
		for (var e : need.entrySet()) {
			int n = e.getValue();
			out.add(new Material(e.getKey(), n, creative ? n : have.getOrDefault(e.getKey(), 0)));
		}
		out.sort(Comparator.comparingInt((Material m) -> -m.short_()).thenComparingInt(m -> -m.need()));
		return out;
	}

	static boolean matches(BlockState have, BlockState want) {
		if (have.getBlock() != want.getBlock()) return false;
		for (Property<?> prop : ORIENTATION) {
			if (want.hasProperty(prop) && have.hasProperty(prop) && !have.getValue(prop).equals(want.getValue(prop))) return false;
		}
		for (Property<?> prop : List.of(BlockStateProperties.DELAY, BlockStateProperties.MODE_COMPARATOR, BlockStateProperties.NOTE,
			BlockStateProperties.INVERTED)) {
			if (want.hasProperty(prop) && !have.getValue(prop).equals(want.getValue(prop))) return false;
		}
		if (want.is(Blocks.LEVER) && have.getValue(BlockStateProperties.POWERED) != want.getValue(BlockStateProperties.POWERED)) return false;
		if (want.hasProperty(BlockStateProperties.OPEN) && !want.is(Blocks.IRON_DOOR) && !want.is(Blocks.IRON_TRAPDOOR)
			&& have.getValue(BlockStateProperties.OPEN) != want.getValue(BlockStateProperties.OPEN)) return false;
		// doors, trapdoors and gates that open by hand (iron ones only open by redstone, so they are not checked)
		if (want.hasProperty(BlockStateProperties.OPEN) && !want.is(Blocks.IRON_DOOR) && !want.is(Blocks.IRON_TRAPDOOR)
			&& have.getValue(BlockStateProperties.OPEN) != want.getValue(BlockStateProperties.OPEN)) return false;
		return true;
	}

	// ------------------------------------------------------------------ placing
	private Item itemFor(BlockState want) {
		Item it = want.getBlock().asItem();
		if (want.is(Blocks.REDSTONE_WALL_TORCH)) it = Blocks.REDSTONE_TORCH.asItem();
		else if (want.is(Blocks.WALL_TORCH)) it = Blocks.TORCH.asItem();
		else if (want.is(Blocks.SOUL_WALL_TORCH)) it = Blocks.SOUL_TORCH.asItem();
		return it;
	}

	/** Puts the item in hand (hotbar first, then from the inventory into the selected slot). */
	private boolean holdItem(Item item) {
		var inv = mc.player.getInventory();
		if (mc.player.getMainHandItem().is(item)) return true;
		for (int i = 0; i < 9; i++) if (inv.getItem(i).is(item)) {
			inv.setSelectedSlot(i);
			return true;
		}
		if (mc.player.getAbilities().instabuild) {
			// creative: take it from nowhere into the selected slot, as the creative inventory does
			ItemStack s = new ItemStack(item);
			int slot = inv.getSelectedSlot();
			inv.setItem(slot, s);
			mc.gameMode.handleCreativeModeItemAdd(s, 36 + slot);
			return true;
		}
		if (!swapFromInventory.get()) return false;
		for (int i = 9; i < 36; i++) if (inv.getItem(i).is(item)) {
			mc.gameMode.handleInventoryMouseClick(mc.player.inventoryMenu.containerId, i, inv.getSelectedSlot(), ClickType.SWAP, mc.player);
			return true;
		}
		return false;
	}

	record Plan(BlockHitResult hit, float yaw, float pitch) {}

	/** Tries every face, hit height and look direction with the block's own placement code; first match wins. */
	private Plan plan(BlockPos pos, BlockState want, ItemStack stack) {
		float[] yaws = {0, 90, 180, 270};
		float[] pitches = {0, 60, -60, 90, -90};
		if (want.hasProperty(BlockStateProperties.ROTATION_16)) {
			yaws = new float[16];
			for (int i = 0; i < 16; i++) yaws[i] = i * 22.5F;
		}
		Vec3 eye = mc.player.getEyePosition();
		float oy = mc.player.getYRot(), op = mc.player.getXRot();
		try {
			for (Direction d : Direction.values()) {
				BlockPos n = pos.relative(d);
				BlockState ns = mc.level.getBlockState(n);
				if (ns.canBeReplaced() && !(want.getBlock() == ns.getBlock())) continue;
				if (reacts(ns)) continue;    // clicking it would open or toggle it instead of placing
				Direction face = d.getOpposite();
				double[] ys = d.getAxis().isHorizontal() ? new double[] {0.25, 0.75} : new double[] {0.5};
				for (double yf : ys) {
					Vec3 at = new Vec3(pos.getX() + 0.5 + d.getStepX() * 0.5, pos.getY() + (d == Direction.UP ? 1 : d == Direction.DOWN ? 0 : yf), pos.getZ() + 0.5 + d.getStepZ() * 0.5);
					if (at.distanceTo(eye) > range.get() + 0.5) continue;
					BlockHitResult hit = new BlockHitResult(at, face, n, false);
					for (float yaw : yaws) for (float pitch : pitches) {
						mc.player.setYRot(yaw);
						mc.player.setXRot(pitch);
						BlockPlaceContext ctx = new BlockPlaceContext(mc.player, InteractionHand.MAIN_HAND, stack, hit);
						if (!ctx.getClickedPos().equals(pos)) continue;
						BlockState result = want.getBlock().getStateForPlacement(ctx);
						if (result != null && result.getBlock() == want.getBlock() && acceptable(result, want, pos) && result.canSurvive(mc.level, pos)) {
							return new Plan(hit, yaw, pitch);
						}
					}
				}
			}
			if (useAirPlace() && !noAir.contains(pos) && mc.level.getBlockState(pos).canBeReplaced()) {
				for (Direction face : Direction.values()) {
					double[] ys = face.getAxis().isHorizontal() ? new double[] {0.25, 0.75} : new double[] {0.5};
					for (double yf : ys) {
						Vec3 at = new Vec3(pos.getX() + 0.5 + face.getStepX() * 0.5, pos.getY() + (face == Direction.UP ? 1 : face == Direction.DOWN ? 0 : yf), pos.getZ() + 0.5 + face.getStepZ() * 0.5);
						if (at.distanceTo(eye) > range.get() + 0.5) continue;
						BlockHitResult hit = new BlockHitResult(at, face, pos, false);
						for (float yaw : yaws) for (float pitch : pitches) {
							mc.player.setYRot(yaw);
							mc.player.setXRot(pitch);
							BlockPlaceContext ctx = new BlockPlaceContext(mc.player, InteractionHand.MAIN_HAND, stack, hit);
							if (!ctx.getClickedPos().equals(pos)) continue;
							BlockState result = want.getBlock().getStateForPlacement(ctx);
							if (result != null && result.getBlock() == want.getBlock() && acceptable(result, want, pos) && result.canSurvive(mc.level, pos)) {
								return new Plan(hit, yaw, pitch);
							}
						}
					}
				}
			}
		} finally {
			mc.player.setYRot(oy);
			mc.player.setXRot(op);
		}
		return null;
	}

	/**
	 * Which side of the block the player has to stand on for its direction (furnaces, chests, stairs... take it from
	 * where the player looks): tried with the four look directions, ignoring what is in the way.
	 */
	private String sideHint(BlockPos pos, BlockState want, ItemStack stack) {
		String name = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(want.getBlock()).getPath();
		float oy = mc.player.getYRot(), op = mc.player.getXRot();
		try {
			// every neighbour face that could be clicked, with every look direction, ignoring what is in the way
			for (Direction d : Direction.values()) {
				BlockPos n = pos.relative(d);
				BlockState ns = mc.level.getBlockState(n);
				if (ns.canBeReplaced()) continue;
				Direction face = d.getOpposite();
				BlockHitResult hit = new BlockHitResult(facePoint(outline(ns, n), face, 0.5, 0.5), face, n, false);
				Direction lookOk = null;
				int looks = 0;
				for (Direction look : Direction.Plane.HORIZONTAL) {
					mc.player.setYRot(look.toYRot());
					mc.player.setXRot(30);
					for (boolean sneak : new boolean[] {false, true}) {
						BlockPlaceContext ctx = new BlockPlaceContext(mc.player, InteractionHand.MAIN_HAND, stack, hit) {
							@Override
							public boolean isSecondaryUseActive() {
								return sneak;
							}
						};
						BlockState r = want.getBlock().getStateForPlacement(ctx);
						if (r != null && acceptable(r, want, pos)) {
							looks++;
							lookOk = look;
							break;
						}
					}
				}
				if (looks == 0) continue;
				// the direction comes from the look (a furnace faces the player): stand on the opposite side
				if (looks < 4) return "stand " + lookOk.getOpposite().getName() + " of the " + name;
				// it comes from the clicked face (a hopper points into what it was placed against): see that face
				if (face.getAxis().isHorizontal()) return "stand " + face.getName() + " of the " + name + " (to click the " +
					net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(ns.getBlock()).getPath() + ")";
				return face == Direction.UP ? "look down onto the " + name + "'s spot from above" : "look up at the " + name + "'s spot from below";
			}
		} finally {
			mc.player.setYRot(oy);
			mc.player.setXRot(op);
		}
		return "nothing to place the " + name + " against yet";
	}

	/** Blocks a right click opens or toggles (containers, doors, levers, repeaters...): never clicked to place against. */
	private static boolean reacts(BlockState s) {
		Block b = s.getBlock();
		return s.hasBlockEntity() || b instanceof net.minecraft.world.level.block.DoorBlock || b instanceof net.minecraft.world.level.block.TrapDoorBlock
			|| b instanceof net.minecraft.world.level.block.FenceGateBlock || b instanceof net.minecraft.world.level.block.LeverBlock
			|| b instanceof net.minecraft.world.level.block.ButtonBlock || b instanceof net.minecraft.world.level.block.DiodeBlock
			|| b instanceof net.minecraft.world.level.block.NoteBlock || b instanceof net.minecraft.world.level.block.CraftingTableBlock
			|| b instanceof net.minecraft.world.level.block.AnvilBlock || b instanceof net.minecraft.world.level.block.CakeBlock
			|| b instanceof net.minecraft.world.level.block.RedStoneWireBlock || b instanceof net.minecraft.world.level.block.GrindstoneBlock
			|| b instanceof net.minecraft.world.level.block.StonecutterBlock || b instanceof net.minecraft.world.level.block.LoomBlock
			|| b instanceof net.minecraft.world.level.block.CartographyTableBlock || b instanceof net.minecraft.world.level.block.SmithingTableBlock;
	}

	/**
	 * Anti-cheat friendly plan. For points spread over the real outline of every neighbour face (a chest top is lower
	 * than a full block), aim straight at the point from the eyes; the ray must really hit that face first; the block's
	 * own placement code, with that aim and that sneak state, must give the schematic's direction. Pass 0 is the
	 * player's current stance; pass 1 the other one (crouched or standing), planned from that stance's eye height.
	 * {@link #planSneak} tells which stance the plan needs.
	 */
	private Plan planLegit(BlockPos pos, BlockState want, ItemStack stack) {
		boolean crouched = mc.player.isShiftKeyDown();
		boolean flying = mc.player.getAbilities().flying;
		float oy = mc.player.getYRot(), op = mc.player.getXRot();
		double[] f = {0.15, 0.5, 0.85};
		try {
			for (int pass = 0; pass < 2; pass++) {
				boolean sneak = pass == 0 ? crouched : !crouched;
				if (pass == 1 && sneak && flying) continue;          // no crouching while flying
				Vec3 eye = pass == 0 ? mc.player.getEyePosition() : mc.player.position().add(0,
					mc.player.getDimensions(sneak ? net.minecraft.world.entity.Pose.CROUCHING : net.minecraft.world.entity.Pose.STANDING).eyeHeight(), 0);
				for (Direction d : Direction.values()) {
					BlockPos n = pos.relative(d);
					BlockState ns = mc.level.getBlockState(n);
					if (ns.canBeReplaced() && want.getBlock() != ns.getBlock()) continue;
					if (reacts(ns) && !sneak) continue;               // a plain click would open it
					Direction face = d.getOpposite();
					AABB box = outline(ns, n);
					for (double a : f) for (double b : f) {
						Vec3 at = facePoint(box, face, a, b);
						Vec3 dir = at.subtract(eye);
						if (dir.length() > range.get() + 0.5) continue;
						BlockHitResult ray = mc.level.clip(new net.minecraft.world.level.ClipContext(eye, eye.add(dir.normalize().scale(range.get() + 1)),
							net.minecraft.world.level.ClipContext.Block.OUTLINE, net.minecraft.world.level.ClipContext.Fluid.NONE, mc.player));
						if (ray.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK || !ray.getBlockPos().equals(n) || ray.getDirection() != face) continue;
						float yaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
						float pitch = (float) -Math.toDegrees(Math.atan2(dir.y, Math.sqrt(dir.x * dir.x + dir.z * dir.z)));
						mc.player.setYRot(yaw);
						mc.player.setXRot(pitch);
						BlockHitResult hit = new BlockHitResult(ray.getLocation(), face, n, false);
						BlockPlaceContext ctx = new BlockPlaceContext(mc.player, InteractionHand.MAIN_HAND, stack, hit) {
							@Override
							public boolean isSecondaryUseActive() {
								return sneak;     // what the block sees when the click comes: sneaking or not
							}
						};
						if (!ctx.getClickedPos().equals(pos)) continue;
						BlockState result = want.getBlock().getStateForPlacement(ctx);
						if (result != null && result.getBlock() == want.getBlock() && acceptable(result, want, pos) && result.canSurvive(mc.level, pos)) {
							planSneak = sneak;
							return new Plan(hit, yaw, pitch);
						}
					}
				}
				BlockState here = mc.level.getBlockState(pos);
				if (here.getBlock() == want.getBlock() && want.hasProperty(BlockStateProperties.SLAB_TYPE)) {
					// a single slab is already here: click the slab itself to make it double
					AABB box = outline(here, pos);
					for (Direction face : Direction.values()) for (double a : f) for (double b : f) {
						Vec3 at = facePoint(box, face, a, b), dir = at.subtract(eye);
						if (dir.length() > range.get() + 0.5) continue;
						BlockHitResult ray = mc.level.clip(new net.minecraft.world.level.ClipContext(eye, eye.add(dir.normalize().scale(range.get() + 1)),
							net.minecraft.world.level.ClipContext.Block.OUTLINE, net.minecraft.world.level.ClipContext.Fluid.NONE, mc.player));
						if (ray.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK || !ray.getBlockPos().equals(pos) || ray.getDirection() != face) continue;
						float yaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
						float pitch = (float) -Math.toDegrees(Math.atan2(dir.y, Math.sqrt(dir.x * dir.x + dir.z * dir.z)));
						BlockHitResult hit = new BlockHitResult(ray.getLocation(), face, pos, false);
						BlockPlaceContext ctx = new BlockPlaceContext(mc.player, InteractionHand.MAIN_HAND, stack, hit);
						if (!ctx.getClickedPos().equals(pos) || !ctx.canPlace()) continue;
						BlockState result = want.getBlock().getStateForPlacement(ctx);
						if (result != null && result.equals(want.getBlock().withPropertiesOf(want).setValue(BlockStateProperties.WATERLOGGED, result.getValue(BlockStateProperties.WATERLOGGED)))) {
							planSneak = sneak;
							return new Plan(hit, yaw, pitch);
						}
					}
				}
			}
		} finally {
			mc.player.setYRot(oy);
			mc.player.setXRot(op);
		}
		return null;
	}

	/** The block's outline in world coordinates (a chest is 14/16 high, a slab half), or the full cube. */
	private static AABB outline(BlockState s, BlockPos p) {
		var shape = s.getShape(mc.level, p);
		return shape.isEmpty() ? new AABB(p) : shape.bounds().move(p);
	}

	/** A point on the given face of a box, at fractions a, b across it. */
	private static Vec3 facePoint(AABB box, Direction face, double a, double b) {
		double x = box.minX + (box.maxX - box.minX) * a, y = box.minY + (box.maxY - box.minY) * a, z = box.minZ + (box.maxZ - box.minZ) * b;
		return switch (face) {
			case UP -> new Vec3(x, box.maxY, z);
			case DOWN -> new Vec3(x, box.minY, z);
			case EAST -> new Vec3(box.maxX, y, z);
			case WEST -> new Vec3(box.minX, y, z);
			case SOUTH -> new Vec3(x, y, box.maxZ);
			case NORTH -> new Vec3(x, y, box.minZ);
		};
	}

	/**
	 * The orientation matches; a chest half may be placed single only while its partner is not there yet (with the
	 * partner present it has to join it).
	 */
	private boolean acceptable(BlockState result, BlockState want, BlockPos pos) {
		if (!orientationEquals(result, want)) return false;
		if (want.hasProperty(BlockStateProperties.CHEST_TYPE) && want.getValue(BlockStateProperties.CHEST_TYPE) != net.minecraft.world.level.block.state.properties.ChestType.SINGLE
			&& result.getValue(BlockStateProperties.CHEST_TYPE) == net.minecraft.world.level.block.state.properties.ChestType.SINGLE) {
			BlockPos partner = pos.relative(net.minecraft.world.level.block.ChestBlock.getConnectedDirection(want));
			return !mc.level.getBlockState(partner).is(want.getBlock());
		}
		return true;
	}

	/** Why a block is not placed: the item, and for each neighbour face what the ray and the placement give. */
	public String explain(BlockPos pos) {
		BlockState want = blocks.get(pos);
		if (want == null) return "not in the schematic";
		StringBuilder sb = new StringBuilder(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(want.getBlock()).getPath());
		sb.append(" have=").append(mc.level.getBlockState(pos).getBlock()).append(" sneaking=").append(mc.player.isShiftKeyDown())
			.append(" crouching=").append(mc.player.isCrouching()).append(" retry=").append(retryAfter.containsKey(pos));
		Item item = itemFor(want);
		sb.append(" item=").append(item).append(" inInv=").append(mc.player.getInventory().countItem(item));
		Vec3 eye = mc.player.getEyePosition();
		ItemStack stack = new ItemStack(item);
		for (Direction d : Direction.values()) {
			BlockPos n = pos.relative(d);
			BlockState ns = mc.level.getBlockState(n);
			sb.append("\n    ").append(d).append(": ").append(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(ns.getBlock()).getPath());
			if (ns.canBeReplaced() && want.getBlock() != ns.getBlock()) {
				sb.append(" (replaceable, no face)");
				continue;
			}
			Direction face = d.getOpposite();
			int seen = 0, rays = 0;
			String result = "-";
			for (double a : new double[] {0.15, 0.5, 0.85}) for (double b : new double[] {0.15, 0.5, 0.85}) {
				double x = pos.getX() + 0.5 + d.getStepX() * 0.5, y = pos.getY() + 0.5 + d.getStepY() * 0.5, z = pos.getZ() + 0.5 + d.getStepZ() * 0.5;
				if (d.getAxis() == Direction.Axis.X) { y = pos.getY() + a; z = pos.getZ() + b; }
				else if (d.getAxis() == Direction.Axis.Y) { x = pos.getX() + a; z = pos.getZ() + b; }
				else { x = pos.getX() + a; y = pos.getY() + b; }
				Vec3 dir = new Vec3(x, y, z).subtract(eye);
				if (dir.length() > range.get() + 0.5) continue;
				rays++;
				BlockHitResult ray = mc.level.clip(new net.minecraft.world.level.ClipContext(eye, eye.add(dir.normalize().scale(range.get() + 1)),
					net.minecraft.world.level.ClipContext.Block.OUTLINE, net.minecraft.world.level.ClipContext.Fluid.NONE, mc.player));
				if (ray.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK && ray.getBlockPos().equals(n) && ray.getDirection() == face) {
					seen++;
					if (result.equals("-")) {
						float oy = mc.player.getYRot(), op = mc.player.getXRot();
						mc.player.setYRot((float) Math.toDegrees(Math.atan2(-dir.x, dir.z)));
						mc.player.setXRot((float) -Math.toDegrees(Math.atan2(dir.y, Math.sqrt(dir.x * dir.x + dir.z * dir.z))));
						BlockPlaceContext ctx = new BlockPlaceContext(mc.player, InteractionHand.MAIN_HAND, stack, new BlockHitResult(ray.getLocation(), face, n, false));
						BlockState r = want.getBlock().getStateForPlacement(ctx);
						result = "clicked=" + ctx.getClickedPos().toShortString() + " -> " + (r == null ? "null" : net.minecraft.commands.arguments.blocks.BlockStateParser.serialize(r))
							+ (r != null && r.canSurvive(mc.level, pos) ? "" : " (cannot survive)");
						mc.player.setYRot(oy);
						mc.player.setXRot(op);
					}
				} else if (rays == 1) {
					sb.append(" firstRay=").append(ray.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK ? ray.getBlockPos().toShortString() + "/" + ray.getDirection() : "miss");
				}
			}
			sb.append(" reacts=").append(reacts(ns)).append(" inReach=").append(rays).append(" visible=").append(seen).append(' ').append(result);
		}
		return sb.toString();
	}

	private static boolean orientationEquals(BlockState a, BlockState b) {
		for (Property<?> prop : ORIENTATION) {
			if (b.hasProperty(prop) && a.hasProperty(prop) && !a.getValue(prop).equals(b.getValue(prop))) {
				// a single slab becomes double by the second click: the first one only has to be the same block
				if (prop == BlockStateProperties.SLAB_TYPE && b.getValue(BlockStateProperties.SLAB_TYPE) == SlabType.DOUBLE) continue;
				// a chest half placed before its partner is a single chest; it joins when the partner comes
				if (prop == BlockStateProperties.CHEST_TYPE && a.getValue(BlockStateProperties.CHEST_TYPE) == net.minecraft.world.level.block.state.properties.ChestType.SINGLE) continue;
				return false;
			}
		}
		return true;
	}

	private boolean place(BlockPos pos, BlockState want) {
		if (mc.player.getBoundingBox().intersects(new AABB(pos)) && want.isCollisionShapeFullBlock(mc.level, pos)) {
			reason = "you stand in it";
			return false;
		}
		Item item = itemFor(want);
		if (!(item instanceof BlockItem) && !want.is(Blocks.REDSTONE_WIRE)) return false;
		if (!holdItem(item)) {
			reason = "no " + new ItemStack(item).getHoverName().getString();
			if (warned.add(item)) {
				say("Not in your inventory: " + net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).getPath(), ChatFormatting.YELLOW);
				if (alerts.isOn()) {
					com.wailsvlr.prism.gui.Notifications.post("printer-out-" + item, "warning", "Printer",
						"Out of " + new ItemStack(item).getHoverName().getString());
					mc.player.playSound(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BASS.value(), 0.6f, 0.8f);
				}
			}
			return false;
		}
		ItemStack stack = mc.player.getMainHandItem();
		if (!stack.is(item)) return false;
		Plan plan;
		if (want.is(Blocks.REDSTONE_WIRE)) {
			BlockPos below = pos.below();
			if (mc.level.getBlockState(below).canBeReplaced()) return false;
			plan = new Plan(new BlockHitResult(Vec3.atCenterOf(below).add(0, 0.5, 0), Direction.UP, below, false), mc.player.getYRot(), 90);
		} else {
			planSneak = false;
			plan = useLegitAim() ? planLegit(pos, want, stack) : plan(pos, want, stack);
		}
		if (plan == null) {
			if (reason.isEmpty()) reason = sideHint(pos, want, stack);
			return false;
		}
		if (useLegitAim()) {
			long now = mc.level.getGameTime();
			if (planSneak) lastSneakNeed = now;
			if (planSneak != mc.player.isShiftKeyDown()) {
				// change stance first; next tick the aim is planned again from the new eye height
				forceSneak = planSneak;
				sneakHeld = planSneak;
				standingUp = !planSneak;
				reason = "";
				// a block that keeps asking for the other stance waits a little; the others go first
				if (flips.merge(pos, 1, Integer::sum) > 4) {
					flips.remove(pos);
					retryAfter.put(pos, now + 60);
				}
				return true;
			}
		}
		if (turnView()) {
			// turn the real view: the client's own movement packet carries the look before the click next tick
			mc.player.setYRot(plan.yaw());
			mc.player.setXRot(plan.pitch());
		} else {
			mc.player.connection.send(new ServerboundMovePlayerPacket.Rot(plan.yaw(), plan.pitch(), mc.player.onGround(), mc.player.horizontalCollision));
		}
		pending = plan;
		pendingPos = pos;
		return true;
	}

	/** Looks the planned way (server and client), clicks, and looks back. */
	private void use(Plan plan) {
		float oy = mc.player.getYRot(), op = mc.player.getXRot();
		if (turnView()) {
			mc.player.setYRot(plan.yaw());
			mc.player.setXRot(plan.pitch());
			mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, plan.hit());
			mc.player.swing(InteractionHand.MAIN_HAND);
			return;      // the view stays on the block, like a player who just placed it
		}
		mc.player.connection.send(new ServerboundMovePlayerPacket.Rot(plan.yaw(), plan.pitch(), mc.player.onGround(), mc.player.horizontalCollision));
		mc.player.setYRot(plan.yaw());
		mc.player.setXRot(plan.pitch());
		mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, plan.hit());
		mc.player.swing(InteractionHand.MAIN_HAND);
		mc.player.setYRot(oy);
		mc.player.setXRot(op);
	}

	/** Right-click fixes for blocks that keep a setting. One click per call. */
	private boolean fix(BlockPos pos, BlockState have, BlockState want) {
		boolean click = false;
		if (want.hasProperty(BlockStateProperties.DELAY)) click = !have.getValue(BlockStateProperties.DELAY).equals(want.getValue(BlockStateProperties.DELAY));
		else if (want.hasProperty(BlockStateProperties.MODE_COMPARATOR))
			click = have.getValue(BlockStateProperties.MODE_COMPARATOR) != want.getValue(BlockStateProperties.MODE_COMPARATOR);
		else if (want.hasProperty(BlockStateProperties.NOTE)) click = !have.getValue(BlockStateProperties.NOTE).equals(want.getValue(BlockStateProperties.NOTE));
		else if (want.is(Blocks.LEVER)) click = have.getValue(BlockStateProperties.POWERED) != want.getValue(BlockStateProperties.POWERED);
		else if (want.hasProperty(BlockStateProperties.INVERTED)) click = have.getValue(BlockStateProperties.INVERTED) != want.getValue(BlockStateProperties.INVERTED);
		else if (want.hasProperty(BlockStateProperties.OPEN) && !want.is(Blocks.IRON_DOOR) && !want.is(Blocks.IRON_TRAPDOOR))
			click = have.getValue(BlockStateProperties.OPEN) != want.getValue(BlockStateProperties.OPEN);
		if (!click) return false;
		// do not click the same block again before the server answered the last click
		long now = mc.level.getGameTime();
		Integer last = clicked.get(pos);
		if (last != null && now - last < 6) return false;
		clicked.put(pos, (int) now);
		Vec3 at = Vec3.atCenterOf(pos).add(0, 0.4, 0);
		if (at.distanceTo(mc.player.getEyePosition()) > range.get() + 0.5) return false;
		// an empty-handed click (a block item in hand could place against it when sneaking; we never sneak)
		mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, new BlockHitResult(at, Direction.UP, pos, false));
		mc.player.swing(InteractionHand.MAIN_HAND);
		return true;
	}

	// ------------------------------------------------------------------ display
	/** Called on a right click (MinecraftMixin): true when it was for Easy Place, so the game's own click is skipped. */
	public static boolean easyPlaceClick() {
		Printer p = instance;
		return p != null && p.isEnabled() && p.easyPlace.isOn() && p.easyClick();
	}

	private boolean easyClick() {
		if (mc.player == null || mc.level == null || mc.screen != null || blocks.isEmpty()) return false;
		if (!mc.options.keyUse.isDown()) easyHeld = false;
		BlockPos t = aimedSpot();
		if (t == null) return easyHeld;                 // not aiming into the schematic: a normal right click (unless still held from a placement)
		easyHeld = true;
		if (easyTarget == null && pending == null) {
			easyTarget = t;
			easySince = mc.level.getGameTime();
			reason = "";
		}
		return true;                                    // busy with the last one: the click is still ours
	}

	/** Places the right-clicked spot: plan the look this tick, the click goes out next tick. */
	private void easyTick(long now) {
		if (easyTarget == null || pending != null) return;
		BlockState want = blocks.get(easyTarget), have = mc.level.getBlockState(easyTarget);
		if (want == null || have.getBlock() == want.getBlock() || !have.canBeReplaced() || now - easySince > 20) {
			easyTarget = null;
			return;
		}
		boolean started = place(easyTarget, want);
		if (!started) {
			if (reason.isEmpty()) reason = "cannot place that one from here";
			easyTarget = null;
		} else if (pending != null) easyTarget = null;   // looking at it now; the click follows next tick
		// started without a plan: changing stance first, the same spot is tried again next tick
	}

	/**
	 * The schematic spot you are aiming at: the first missing block along your view within reach, or the missing
	 * block on the face of the real block you look at. Null when you are not aiming into the schematic.
	 */
	private BlockPos aimedSpot() {
		if (mc.player == null || mc.level == null || blocks.isEmpty()) return null;
		Vec3 eye = mc.player.getEyePosition(), dir = mc.player.getViewVector(1f);
		double reach = mc.player.blockInteractionRange();
		// the crosshair is on a chest, crafter or furnace: it opens as usual, even with missing blocks in between (crouch to place)
		if (!mc.player.isShiftKeyDown() && mc.hitResult instanceof BlockHitResult hr0 && hr0.getType() == HitResult.Type.BLOCK
			&& reacts(mc.level.getBlockState(hr0.getBlockPos()))) return null;
		BlockPos last = null;
		for (double d = 0; d <= reach; d += 0.05) {
			BlockPos bp = BlockPos.containing(eye.add(dir.scale(d)));
			if (bp.equals(last)) continue;
			last = bp;
			BlockState want = blocks.get(bp), have = mc.level.getBlockState(bp);
			if (want != null && !want.isAir() && have.getBlock() != want.getBlock() && have.canBeReplaced()) return bp;
			if (!have.canBeReplaced()) {
				// a chest, crafter or furnace opens as usual (crouch to place against it instead)
				if (reacts(have) && !mc.player.isShiftKeyDown()) return null;
				if (mc.hitResult instanceof BlockHitResult hr && hr.getType() == HitResult.Type.BLOCK && hr.getBlockPos().equals(bp)) {
					BlockPos side = bp.relative(hr.getDirection());
					BlockState w2 = blocks.get(side), h2 = mc.level.getBlockState(side);
					if (w2 != null && !w2.isAir() && h2.getBlock() != w2.getBlock() && h2.canBeReplaced()) return side;
				}
				return null;
			}
		}
		return null;
	}

	public boolean easyPlaceOn() { return isEnabled() && easyPlace.isOn(); }

	/** The setup guide object itself (the crafter screen's button lives on it whether or not the guide shows now). */
	public PrinterGuide guide0() { return guide; }

	/** The setup guide, when the Printer is on with a schematic loaded and the guide switched on (else null). */
	public PrinterGuide guide() { return isEnabled() && guideOn.isOn() && !blocks.isEmpty() ? guide : null; }

	/** Crafter screens: the guide's slot frames (AbstractContainerScreenMixin). */
	public void renderGuideSlot(net.minecraft.client.gui.GuiGraphics g, net.minecraft.world.inventory.Slot slot) {
		PrinterGuide gd = guide();
		if (gd != null) gd.renderSlot(g, slot);
	}

	/** For the tests: the spot Easy Place would fill now. */
	public BlockPos aimedForTest() { return aimedSpot(); }

	public void collect(List<Highlight> out) {
		if (!isEnabled() || mc.level == null || blocks.isEmpty() || mc.player == null) return;
		if (guideOn.isOn()) guide.collect(out);
		HighlightStyle style = new HighlightStyle("Both", 0.9F, 0.18F, 1.5F, false, false, 0, true, 48, false, 1, -0.02, false);
		Vec3 eye = mc.player.getEyePosition();
		if (easyPlace.isOn()) {
			BlockPos aim = aimedSpot();
			if (aim != null) {
				HighlightStyle aimStyle = new HighlightStyle("Both", 1f, 0.12f, 2.5F, true, false, 0, false, 48, false, 1, 0.01, false);
				out.add(new Highlight(new AABB(aim), 0xFFFFFF, aimStyle, blocks.get(aim).getBlock().getName().getString()));
			}
		}
		int n = 0;
		for (var e : blocks.entrySet()) {
			if (e.getValue().isAir() || n > 2000) continue;
			if (Vec3.atCenterOf(e.getKey()).distanceToSqr(eye) > 48 * 48) continue;
			BlockState have = mc.level.getBlockState(e.getKey());
			if (matches(have, e.getValue())) continue;
			boolean isWrong = !(have.getBlock() == e.getValue().getBlock() || have.canBeReplaced());
			if (isWrong && !showWrong.get() || !isWrong && !showMissing.get()) continue;
			out.add(new Highlight(new AABB(e.getKey()), isWrong ? 0xFF4040 : 0x40E0FF, style, null));
			n++;
		}
	}
}
