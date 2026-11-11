package com.wailsvlr.prism.modules;

import com.wailsvlr.prism.render.Highlight;
import com.wailsvlr.prism.render.HighlightStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.CrafterScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * The Printer's setup guide: what a schematic needs besides its blocks, read from the .litematic itself.
 * <ul>
 * <li>crafters: which slots are locked and the items that start the pattern (checked when you open the crafter),</li>
 * <li>entities: minecarts, boats, frames... (checked by looking for them in the world),</li>
 * <li>blocks the Printer cannot place: water, lava, kelp, seagrass,</li>
 * <li>blocks that need a click: levers, repeaters, comparators, note blocks, doors (Fix States does these when it can),</li>
 * <li>containers saved with items in them (dispensers full of bone meal...), as a hint.</li>
 * </ul>
 */
public final class PrinterGuide {
	private static final Minecraft mc = Minecraft.getInstance();

	/** An entity the schematic has: where it goes and what it is. */
	public record Spot(EntityType<?> type, Vec3 pos, String name) { }

	/** Required locked slots per crafter, and the items that start each crafter's pattern (slot -> item). */
	private final Map<BlockPos, Set<Integer>> crafterLocked = new HashMap<>();
	private final Map<BlockPos, Map<Integer, Item>> crafterItems = new HashMap<>();
	/** Other containers saved with items: what was in them. */
	private final Map<BlockPos, Map<Item, Integer>> saved = new LinkedHashMap<>();
	/** What block each saved container is in the schematic (the world may not have it yet). */
	private volatile Map<BlockPos, String> savedNames = Map.of();
	private final List<Spot> spots = new ArrayList<>();
	/** Crafters that were opened and found set as the schematic wants. */
	private final Set<BlockPos> crafterOk = new HashSet<>();

	// what is still to do, worked out once a second
	private volatile List<Spot> spotsMissing = List.of();
	private volatile List<BlockPos> handNear = List.of(), clickNear = List.of();
	private volatile Map<String, Integer> handCounts = Map.of(), clickCounts = Map.of();
	private long lastScan = -100;
	private BlockPos openCrafter;
	/** "Set slots": grid slots still to toggle, one every APPLY_GAP ticks (the same packet as clicking the slot). */
	private final java.util.ArrayDeque<Integer> applyQueue = new java.util.ArrayDeque<>();
	private int applyWait;
	private static final int APPLY_GAP = 2;

	public void clear() {
		crafterLocked.clear();
		crafterItems.clear();
		saved.clear();
		spots.clear();
		crafterOk.clear();
		spotsMissing = List.of();
		handNear = List.of();
		clickNear = List.of();
		handCounts = Map.of();
		clickCounts = Map.of();
		lastScan = -100;
	}

	public boolean isEmpty() {
		return crafterLocked.isEmpty() && saved.isEmpty() && spots.isEmpty() && handCounts.isEmpty() && clickCounts.isEmpty();
	}

	// ---------------------------------------------------------------- reading the schematic

	private static Item item(String id) {
		Identifier key = Identifier.tryParse(id);
		return key == null ? Items.AIR : BuiltInRegistries.ITEM.getOptional(key).orElse(Items.AIR);
	}

	/** Reads the block entities and entities of every region (same corner rules as {@link Printer#read}). */
	public void load(CompoundTag root, BlockPos origin) {
		clear();
		CompoundTag regions = root.getCompoundOrEmpty("Regions");
		for (String key : regions.keySet()) {
			CompoundTag r = regions.getCompoundOrEmpty(key);
			CompoundTag pos = r.getCompoundOrEmpty("Position"), size = r.getCompoundOrEmpty("Size");
			int px = pos.getIntOr("x", 0), py = pos.getIntOr("y", 0), pz = pos.getIntOr("z", 0);
			int sx = size.getIntOr("x", 0), sy = size.getIntOr("y", 0), sz = size.getIntOr("z", 0);
			int mx = sx < 0 ? px + sx + 1 : px, my = sy < 0 ? py + sy + 1 : py, mz = sz < 0 ? pz + sz + 1 : pz;
			// block entities: positions from the region's lowest corner
			for (Tag t : r.getListOrEmpty("TileEntities")) {
				if (!(t instanceof CompoundTag te)) continue;
				BlockPos at = origin.offset(mx + te.getIntOr("x", 0), my + te.getIntOr("y", 0), mz + te.getIntOr("z", 0));
				ListTag items = te.getListOrEmpty("Items");
				boolean crafter = te.getStringOr("id", "").endsWith("crafter") || te.contains("disabled_slots");
				if (crafter) {
					Set<Integer> locked = new HashSet<>();
					for (int s : te.getIntArray("disabled_slots").orElse(new int[0])) locked.add(s);
					crafterLocked.put(at, locked);
					Map<Integer, Item> pattern = new TreeMap<>();
					for (Tag it : items) if (it instanceof CompoundTag c) {
						Item i = item(c.getStringOr("id", ""));
						if (i != Items.AIR) pattern.put((int) c.getByteOr("Slot", (byte) 0), i);
					}
					crafterItems.put(at, pattern);
				} else if (!items.isEmpty()) {
					Map<Item, Integer> what = new LinkedHashMap<>();
					for (Tag it : items) if (it instanceof CompoundTag c) {
						Item i = item(c.getStringOr("id", ""));
						if (i != Items.AIR) what.merge(i, Math.max(1, c.getIntOr("count", c.getByteOr("Count", (byte) 1))), Integer::sum);
					}
					if (!what.isEmpty()) saved.put(at, what);
				}
			}
			// entities: positions from the region's own corner
			for (Tag t : r.getListOrEmpty("Entities")) {
				if (!(t instanceof CompoundTag e)) continue;
				String id = e.getStringOr("id", "");
				if (id.endsWith(":item") || id.endsWith("experience_orb") || id.endsWith("arrow") || id.endsWith("falling_block")) continue;
				Identifier key2 = Identifier.tryParse(id);
				if (key2 == null) continue;
				var type = BuiltInRegistries.ENTITY_TYPE.getOptional(key2);
				if (type.isEmpty()) continue;
				ListTag p = e.getListOrEmpty("Pos");
				Vec3 at = new Vec3(origin.getX() + px + p.getDoubleOr(0, 0), origin.getY() + py + p.getDoubleOr(1, 0), origin.getZ() + pz + p.getDoubleOr(2, 0));
				spots.add(new Spot(type.get(), at, type.get().getDescription().getString()));
			}
		}
	}

	// ---------------------------------------------------------------- what is still to do

	private static boolean handPlaced(BlockState want) {
		return !want.getFluidState().isEmpty() && (want.getBlock() instanceof net.minecraft.world.level.block.LiquidBlock || want.is(Blocks.BUBBLE_COLUMN)
			|| want.is(Blocks.KELP) || want.is(Blocks.KELP_PLANT) || want.is(Blocks.SEAGRASS) || want.is(Blocks.TALL_SEAGRASS));
	}

	private static String handName(BlockState want) {
		if (want.is(Blocks.KELP) || want.is(Blocks.KELP_PLANT)) return "kelp";
		if (want.is(Blocks.SEAGRASS) || want.is(Blocks.TALL_SEAGRASS)) return "seagrass";
		if (want.is(Blocks.LAVA)) return "lava";
		return "water";
	}

	private static boolean clickable(BlockState want) {
		return want.is(Blocks.LEVER) || want.is(Blocks.REPEATER) || want.is(Blocks.COMPARATOR) || want.is(Blocks.NOTE_BLOCK)
			|| want.is(Blocks.DAYLIGHT_DETECTOR) || want.hasProperty(BlockStateProperties.OPEN);
	}

	/** Once a second: what still needs doing by hand. */
	public void tick(Map<BlockPos, BlockState> blocks, long gameTime) {
		if (mc.level == null || mc.player == null || gameTime - lastScan < 20) return;
		lastScan = gameTime;
		Vec3 eye = mc.player.getEyePosition();
		Map<String, Integer> hand = new TreeMap<>(), click = new TreeMap<>();
		List<BlockPos> hn = new ArrayList<>(), cn = new ArrayList<>();
		for (var e : blocks.entrySet()) {
			BlockState want = e.getValue();
			if (want.isAir()) continue;
			BlockPos p = e.getKey();
			if (!mc.level.isLoaded(p)) continue;
			BlockState have = mc.level.getBlockState(p);
			if (handPlaced(want)) {
				boolean ok;
				if (want.is(Blocks.KELP) || want.is(Blocks.KELP_PLANT)) ok = have.is(Blocks.KELP) || have.is(Blocks.KELP_PLANT);
				else if (want.is(Blocks.SEAGRASS) || want.is(Blocks.TALL_SEAGRASS)) ok = have.is(Blocks.SEAGRASS) || have.is(Blocks.TALL_SEAGRASS);
				// water and lava (a bubble column is water too): the same liquid, and a source where the schematic has one
				else ok = have.getFluidState().getType().isSame(want.getFluidState().getType()) && (have.getFluidState().isSource() || !want.getFluidState().isSource());
				if (!ok) {
					hand.merge(handName(want), 1, Integer::sum);
					if (hn.size() < 400 && Vec3.atCenterOf(p).distanceToSqr(eye) < 40 * 40) hn.add(p);
				}
			} else if (have.getBlock() == want.getBlock() && clickable(want) && !Printer.matches(have, want)) {
				click.merge(want.getBlock().getName().getString().toLowerCase(java.util.Locale.ROOT), 1, Integer::sum);
				if (cn.size() < 100 && Vec3.atCenterOf(p).distanceToSqr(eye) < 40 * 40) cn.add(p);
			}
		}
		handCounts = hand;
		clickCounts = click;
		Map<BlockPos, String> names = new HashMap<>();
		for (BlockPos p : saved.keySet()) {
			BlockState want = blocks.get(p);
			if (want != null) names.put(p, want.getBlock().getName().getString().toLowerCase(java.util.Locale.ROOT));
		}
		savedNames = names;
		handNear = hn;
		clickNear = cn;
		List<Spot> missing = new ArrayList<>();
		for (Spot s : spots) {
			if (!mc.level.isLoaded(BlockPos.containing(s.pos()))) { missing.add(s); continue; }
			// the same kind of entity right at the spot (two minecarts a block apart must not tick each other off)
			List<Entity> there = mc.level.getEntities((Entity) null, new AABB(s.pos(), s.pos()).inflate(1.5),
				en -> en.getType() == s.type() && en.position().distanceTo(s.pos()) < 0.6);
			if (there.isEmpty()) missing.add(s);
		}
		spotsMissing = missing;
	}

	/** While a crafter's screen is open: is it set as the schematic wants? */
	public void screenTick() {
		if (!(mc.screen instanceof CrafterScreen cs)) { openCrafter = null; applyQueue.clear(); return; }
		if (openCrafter == null && mc.hitResult instanceof BlockHitResult bh && bh.getType() == HitResult.Type.BLOCK && crafterLocked.containsKey(bh.getBlockPos()))
			openCrafter = bh.getBlockPos().immutable();
		if (openCrafter == null) return;
		Set<Integer> locked = crafterLocked.get(openCrafter);
		boolean ok = true;
		for (int i = 0; i < 9; i++) if (cs.getMenu().isSlotDisabled(i) != locked.contains(i)) { ok = false; break; }
		if (ok) crafterOk.add(openCrafter); else crafterOk.remove(openCrafter);
		applyStep(cs, locked);
	}

	/** The crafter screen's "Set slots" button (added in PrismClient on screen init; shown while the open crafter is set wrong). */
	public void addButton(CrafterScreen cs) {
		var acc = (com.wailsvlr.prism.mixin.AbstractContainerScreenAccessor) cs;
		net.minecraft.client.gui.components.Button b = net.minecraft.client.gui.components.Button.builder(
				net.minecraft.network.chat.Component.literal("Set slots"), btn -> startApply())
			.bounds(acc.prism$leftPos() + 98, acc.prism$topPos() + 53, 70, 16)
			.tooltip(net.minecraft.client.gui.components.Tooltip.create(net.minecraft.network.chat.Component.literal(
				"Legit: locks the red slots and opens the green ones for you, like clicking them one by one")))
			.build();
		b.visible = false;
		net.fabricmc.fabric.api.client.screen.v1.Screens.getButtons(cs).add(b);
		net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.afterTick(cs).register(s -> {
			b.visible = openCrafter != null && !crafterOk.contains(openCrafter);
			b.active = applyQueue.isEmpty();
		});
	}

	private void startApply() {
		if (openCrafter == null || !(mc.screen instanceof CrafterScreen cs)) return;
		Set<Integer> locked = crafterLocked.get(openCrafter);
		applyQueue.clear();
		List<Integer> full = new ArrayList<>();
		for (int i = 0; i < 9; i++) {
			boolean want = locked.contains(i);
			if (cs.getMenu().isSlotDisabled(i) == want) continue;
			// the game only locks an empty slot
			if (want && cs.getMenu().getSlot(i).hasItem()) { full.add(i + 1); continue; }
			applyQueue.add(i);
		}
		applyWait = 0;
		if (!full.isEmpty()) com.wailsvlr.prism.gui.Notifications.post("crafter-full", "warning", "Crafter", "Take the items out of slot " + full.toString().replaceAll("[\\[\\]]", "") + " to lock it");
	}

	private void applyStep(CrafterScreen cs, Set<Integer> locked) {
		if (applyQueue.isEmpty() || mc.gameMode == null || mc.player == null) return;
		if (applyWait-- > 0) return;
		applyWait = APPLY_GAP - 1;
		if (!cs.getMenu().getCarried().isEmpty()) { applyQueue.clear(); return; }   // holding an item: the game would not lock
		int i = applyQueue.poll();
		boolean enable = !locked.contains(i);
		if (cs.getMenu().isSlotDisabled(i) != enable) return;                        // already right
		if (!enable && cs.getMenu().getSlot(i).hasItem()) return;
		cs.getMenu().setSlotState(i, enable);
		mc.gameMode.handleSlotStateChanged(i, cs.getMenu().containerId, enable);
		mc.player.playSound(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 0.4F, enable ? 1.0F : 0.75F);
	}

	/** For the 26.1.2 probe: what the button does. */
	public void applyForTest() { startApply(); }

	/** For the tests: the button's place in GUI coordinates (centre), or null when not shown. */
	public int[] applyButtonForTest() {
		if (!(mc.screen instanceof CrafterScreen cs)) return null;
		for (var b : net.fabricmc.fabric.api.client.screen.v1.Screens.getButtons(cs))
			if (b.visible && b.getMessage().getString().equals("Set slots")) return new int[]{b.getX() + b.getWidth() / 2, b.getY() + b.getHeight() / 2};
		return null;
	}

	/** Crafter screen: red frame = lock this slot, green frame = open it, a faded item = what goes there. */
	public void renderSlot(GuiGraphics g, Slot slot) {
		if (openCrafter == null || !(mc.screen instanceof CrafterScreen cs) || slot.container instanceof Inventory) return;
		int i = slot.index;   // the menu's own order: 0-8 is the grid (the result slot is container slot 0 too)
		if (i < 0 || i > 8) return;
		Set<Integer> locked = crafterLocked.get(openCrafter);
		if (locked == null) return;
		boolean disabled = cs.getMenu().isSlotDisabled(i), want = locked.contains(i);
		int x = slot.x, y = slot.y;
		if (want != disabled) {
			// inside the slot, so neighbouring frames do not cover each other
			int c = want ? 0xFFE53935 : 0xFF2EBD4E;
			g.fill(x, y, x + 16, y + 2, c);
			g.fill(x, y + 14, x + 16, y + 16, c);
			g.fill(x, y + 2, x + 2, y + 14, c);
			g.fill(x + 14, y + 2, x + 16, y + 14, c);
		}
		Item pattern = crafterItems.getOrDefault(openCrafter, Map.of()).get(i);
		if (pattern != null && !want && !slot.hasItem()) {
			g.renderItem(new ItemStack(pattern), slot.x, slot.y);
			g.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, 0xA88B8B8B);
		}
		if (i == 8) {
			// in the free space right of the grid, under the result slot
			boolean ok = crafterOk.contains(openCrafter);
			g.pose().pushMatrix();
			g.pose().translate(slot.x + 26, slot.y + 2);
			g.pose().scale(0.75f, 0.75f);
			if (ok) g.drawString(mc.font, "Set right", 0, 0, 0xFF2E7D32, false);
			// set wrong: the "Set slots" button takes this spot (its tooltip explains the colours)
			g.pose().popMatrix();
		}
	}

	/** For the tests. */
	public java.util.Map<BlockPos, Set<Integer>> crafters() { return crafterLocked; }
	public boolean crafterOk(BlockPos p) { return crafterOk.contains(p); }
	public List<Spot> spots() { return spots; }
	public List<Spot> spotsMissing() { return spotsMissing; }

	// ---------------------------------------------------------------- showing it

	/** The crafter you look at, for the HUD tip: {locked slots, pattern items, set?} or null. */
	public Object[] lookedAtCrafter() {
		if (!(mc.hitResult instanceof BlockHitResult bh) || bh.getType() != HitResult.Type.BLOCK) return null;
		Set<Integer> locked = crafterLocked.get(bh.getBlockPos());
		if (locked == null) return null;
		return new Object[]{locked, crafterItems.getOrDefault(bh.getBlockPos(), Map.of()), crafterOk.contains(bh.getBlockPos())};
	}

	private static String counts(Map<String, Integer> m) {
		StringBuilder sb = new StringBuilder();
		for (var e : m.entrySet()) {
			if (sb.length() > 0) sb.append(", ");
			sb.append(e.getValue()).append(' ').append(e.getKey());
		}
		return sb.toString();
	}

	/** The checklist rows for the printer panel: {icon, text, done ("1") or not ("0")}. */
	public List<String[]> rows() {
		List<String[]> out = new ArrayList<>();
		if (!crafterLocked.isEmpty()) {
			int ok = 0;
			for (BlockPos p : crafterLocked.keySet()) if (crafterOk.contains(p)) ok++;
			boolean done = ok == crafterLocked.size();
			out.add(new String[]{done ? "check" : "warning", "Crafters set: " + ok + "/" + crafterLocked.size() + (done ? "" : " (open each one)"), done ? "1" : "0"});
		}
		if (!spots.isEmpty()) {
			Map<String, Integer> miss = new TreeMap<>();
			for (Spot s : spotsMissing) miss.merge(s.name().toLowerCase(java.util.Locale.ROOT), 1, Integer::sum);
			int placed = spots.size() - spotsMissing.size();
			out.add(new String[]{miss.isEmpty() ? "check" : "pin", "Place by hand: " + placed + "/" + spots.size() + (miss.isEmpty() ? "" : " - " + counts(miss)), miss.isEmpty() ? "1" : "0"});
		}
		if (!handCounts.isEmpty()) out.add(new String[]{"pin", "Fill by hand: " + counts(handCounts), "0"});
		if (!clickCounts.isEmpty()) out.add(new String[]{"warning", "Needs a click: " + counts(clickCounts), "0"});
		if (!saved.isEmpty()) {
			// "7 dispenser (bone meal)": containers that were saved with items, by block and first item
			Map<String, Integer> kinds = new TreeMap<>();
			for (var e : saved.entrySet()) {
				String block = savedNames.getOrDefault(e.getKey(), "container");
				Item first = e.getValue().keySet().iterator().next();
				kinds.merge(block + " (" + new ItemStack(first).getHoverName().getString().toLowerCase(java.util.Locale.ROOT) + ")", 1, Integer::sum);
			}
			out.add(new String[]{"cube", "Saved with items: " + counts(kinds), "1"});
		}
		return out;
	}

	public void collect(List<Highlight> out) {
		if (mc.player == null) return;
		Vec3 eye = mc.player.getEyePosition();
		HighlightStyle hand = new HighlightStyle("Both", 0.9f, 0.22f, 1.5f, true, false, 0, true, 64, false, 1, -0.05, false);
		HighlightStyle mark = new HighlightStyle("Both", 1f, 0.15f, 2f, true, false, 0, true, 64, true, 1.2f, 0.02, false);
		for (Spot s : spotsMissing) {
			if (s.pos().distanceToSqr(eye) > 48 * 48) continue;
			out.add(new Highlight(new AABB(s.pos(), s.pos()).inflate(0.4), 0xFFB020, mark, s.name()));
		}
		for (BlockPos p : crafterLocked.keySet()) {
			if (crafterOk.contains(p)) continue;
			double d2 = Vec3.atCenterOf(p).distanceToSqr(eye);
			if (d2 > 32 * 32) continue;
			out.add(new Highlight(new AABB(p), 0xB57BFF, mark, d2 < 12 * 12 ? "Crafter: set slots" : null));
		}
		for (BlockPos p : handNear) out.add(new Highlight(new AABB(p), 0x60A5FA, hand, null));
		for (BlockPos p : clickNear) out.add(new Highlight(new AABB(p), 0xFACC15, mark, "click"));
	}
}
