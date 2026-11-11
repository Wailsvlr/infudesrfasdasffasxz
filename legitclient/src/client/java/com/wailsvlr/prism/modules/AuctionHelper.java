package com.wailsvlr.prism.modules;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.wailsvlr.prism.module.Category;
import com.wailsvlr.prism.module.Module;
import com.wailsvlr.prism.setting.BoolSetting;
import com.wailsvlr.prism.setting.SectionSetting;
import com.wailsvlr.prism.setting.TextSetting;
import com.wailsvlr.prism.util.Money;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Reads listing prices from the auction house menu (the lore line with "Price"), remembers the lowest price per item,
 * frames the cheapest listing of each item on the page, pings for watched items under your limit, and adds the
 * remembered price to every item tooltip so you know what your loot is worth.
 */
public class AuctionHelper extends Module {
	private static final int KEEP = 30;   // recent prices kept per item

	private final TextSetting title, priceWord, watch;
	private final BoolSetting cheapest, notify, tooltips;

	/** item key (id, or id=Name for a renamed item) -> recent unit prices, newest last: {unit price, time ms}. */
	private final Map<String, List<double[]>> prices = new HashMap<>();
	private boolean dirty;
	private AbstractContainerMenu lastMenu;
	private final Set<String> recorded = new HashSet<>(), pinged = new HashSet<>();
	/** Slot indices to frame in the open auction menu: cheapest per item (gold) and watched under the limit (green). */
	private volatile Set<Integer> cheapSlots = Set.of(), watchSlots = Set.of();
	private int pings;

	public AuctionHelper() {
		super("AuctionHelper", "Remembers auction house prices: frames the cheapest listing, pings for your watch list, and shows the price in item tooltips", Category.MISC);
		add(new SectionSetting("Auction Menu"));
		title = add(new TextSetting("Menu Title", "Text in the auction menu's title (not case sensitive)", "auction", "auction"));
		priceWord = add(new TextSetting("Price Word", "The lore line holding the price contains this word", "price", "price"));
		add(new SectionSetting("Show"));
		cheapest = add(new BoolSetting("Frame Cheapest", "Gold frame around the cheapest listing of each item on the page (per item, so stacks compare fairly)", true));
		watch = add(new TextSetting("Watch List", "item<=max price, separated by commas: a green frame and a ping when one is listed for that or less",
			"", "e.g. elytra<=5m, totem<=20k"));
		notify = add(new BoolSetting("Notify", "Chat message and sound for a watched item under its price", true));
		tooltips = add(new BoolSetting("Tooltip Prices", "Add \"AH: lowest $X each\" to item tooltips (everywhere, also your inventory)", true));
		load();
	}

	// ---------------------------------------------------------------- reading listings

	private boolean isAuctionMenu(Screen screen) {
		if (!(screen instanceof AbstractContainerScreen<?> s) || mc.player == null || s.getMenu() == mc.player.inventoryMenu) return false;
		String want = title.get().trim().toLowerCase(Locale.ROOT);
		return !want.isEmpty() && s.getTitle().getString().toLowerCase(Locale.ROOT).contains(want);
	}

	/** The listing price from the item's lore, or null when it has none. */
	public Double priceOf(ItemStack stack) {
		ItemLore lore = stack.get(DataComponents.LORE);
		if (lore == null) return null;
		String word = priceWord.get().trim().toLowerCase(Locale.ROOT);
		for (Component line : lore.lines()) {
			String text = line.getString();
			if (!word.isEmpty() && !text.toLowerCase(Locale.ROOT).contains(word)) continue;
			Double d = Money.parse(text.substring(word.isEmpty() ? 0 : text.toLowerCase(Locale.ROOT).indexOf(word) + word.length()));
			if (d != null && d > 0) return d;
		}
		return null;
	}

	private record Watch(String what, double max) { }

	private List<Watch> watches() {
		List<Watch> out = new ArrayList<>();
		for (String part : watch.get().split(",")) {
			String[] kv = part.split("<=|<");
			if (kv.length != 2) continue;
			Double max = Money.parse(kv[1]);
			String what = kv[0].trim().toLowerCase(Locale.ROOT).replace("minecraft:", "");
			if (max != null && !what.isEmpty()) out.add(new Watch(what, max));
		}
		return out;
	}

	private static boolean matches(Watch w, ItemStack s) {
		String id = BuiltInRegistries.ITEM.getKey(s.getItem()).getPath();
		return id.contains(w.what()) || s.getHoverName().getString().toLowerCase(Locale.ROOT).contains(w.what());
	}

	@Override
	public void onTick() {
		Screen screen = mc.screen;
		if (!isAuctionMenu(screen)) {
			if (lastMenu != null) { lastMenu = null; cheapSlots = watchSlots = Set.of(); if (dirty) savePrices(); }
			return;
		}
		AbstractContainerMenu menu = ((AbstractContainerScreen<?>) screen).getMenu();
		if (menu != lastMenu) { lastMenu = menu; recorded.clear(); }
		Map<String, Integer> best = new HashMap<>();
		Map<String, Double> bestUnit = new HashMap<>();
		Set<Integer> watched = new HashSet<>();
		List<Watch> ws = watches();
		long now = System.currentTimeMillis();
		for (Slot slot : menu.slots) {
			if (slot.container instanceof Inventory || !slot.hasItem()) continue;
			ItemStack s = slot.getItem();
			Double price = priceOf(s);
			if (price == null) continue;
			String key = AutoSell.markOf(s);
			double unit = price / s.getCount();
			// each listing is recorded once per menu (pages refresh the same slots)
			String tag = key + "@" + price + "x" + s.getCount() + "#" + slot.index;
			if (recorded.add(tag)) record(key, unit, now);
			if (!bestUnit.containsKey(key) || unit < bestUnit.get(key)) { bestUnit.put(key, unit); best.put(key, slot.index); }
			for (Watch w : ws) {
				if (!matches(w, s) || price > w.max()) continue;
				watched.add(slot.index);
				if (notify.isOn() && pinged.add(tag + "|" + s.getHoverName().getString())) ping(s, price);
			}
		}
		cheapSlots = cheapest.isOn() ? new HashSet<>(best.values()) : Set.of();
		watchSlots = watched;
	}

	private void record(String key, double unit, long now) {
		List<double[]> list = prices.computeIfAbsent(key, k -> new ArrayList<>());
		list.add(new double[]{unit, now});
		while (list.size() > KEEP) list.remove(0);
		dirty = true;
	}

	private void ping(ItemStack s, double price) {
		pings++;
		mc.gui.getChat().addMessage(Component.literal("[Legit] ").withStyle(ChatFormatting.LIGHT_PURPLE)
			.append(Component.literal("Auction: ").withStyle(ChatFormatting.WHITE))
			.append(Component.literal(s.getHoverName().getString() + (s.getCount() > 1 ? " ×" + s.getCount() : "")).withStyle(ChatFormatting.AQUA))
			.append(Component.literal(" for $" + Money.format(price)).withStyle(ChatFormatting.GREEN))
			.append(Component.literal(" (on your watch list)").withStyle(ChatFormatting.GRAY)));
		if (mc.player != null) mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 0.8f, 1.6f);
	}

	// ---------------------------------------------------------------- showing

	/** Lowest and median unit price remembered for this item, or null. */
	public double[] stats(ItemStack s) {
		List<double[]> list = prices.get(AutoSell.markOf(s));
		if (list == null || list.isEmpty()) return null;
		List<Double> units = new ArrayList<>();
		for (double[] p : list) units.add(p[0]);
		units.sort(Double::compare);
		return new double[]{units.get(0), units.get(units.size() / 2), units.size()};
	}

	/** The "AH:" tooltip line (Fabric's tooltip callback). */
	public void tooltip(ItemStack s, List<Component> lines) {
		if (!isEnabled() || !tooltips.isOn() || s.isEmpty()) return;
		double[] st = stats(s);
		if (st == null) return;
		String text = "AH: lowest $" + Money.format(st[0]) + " each";
		if (st[2] >= 3 && st[1] > st[0] * 1.05) text += ", usually $" + Money.format(st[1]);
		if (s.getCount() > 1) text += "  (stack ~$" + Money.format(st[0] * s.getCount()) + ")";
		lines.add(Component.literal(text).withStyle(ChatFormatting.GOLD));
	}

	/** Frames in the auction menu, drawn with the slot (menu-local coordinates). */
	public void renderMark(GuiGraphics g, Slot slot) {
		if (!isEnabled() || lastMenu == null || slot.container instanceof Inventory) return;
		int color = watchSlots.contains(slot.index) ? 0xFF55FF7A : cheapSlots.contains(slot.index) ? 0xFFFFC21A : 0;
		if (color == 0) return;
		int x = slot.x - 1, y = slot.y - 1;
		g.fill(x, y, x + 18, y + 1, color);
		g.fill(x, y + 17, x + 18, y + 18, color);
		g.fill(x, y + 1, x + 1, y + 17, color);
		g.fill(x + 17, y + 1, x + 18, y + 17, color);
	}

	/** Typical unit price of an item key: the median of the recent prices (the lowest while fewer than 3 are known), or null. */
	public Double typical(String key) {
		List<double[]> list = prices.get(key);
		if (list == null || list.isEmpty()) return null;
		List<Double> units = new ArrayList<>();
		for (double[] p : list) units.add(p[0]);
		units.sort(Double::compare);
		return units.size() < 3 ? units.get(0) : units.get(units.size() / 2);
	}

	/** Every item key with a remembered price. */
	public Set<String> keys() { return prices.keySet(); }

	/** Adds a price as if it was seen in the auction house (also used by tests). */
	public void remember(String key, double unit) { record(key, unit, System.currentTimeMillis()); }

	public Set<Integer> cheapSlots() { return cheapSlots; }
	public Set<Integer> watchSlots() { return watchSlots; }
	public int pings() { return pings; }

	// ---------------------------------------------------------------- saving

	public static Path file() { return FabricLoader.getInstance().getConfigDir().resolve("prismclient-prices.json"); }

	private void load() {
		try {
			if (!Files.exists(file())) return;
			JsonObject o = JsonParser.parseString(Files.readString(file(), StandardCharsets.UTF_8)).getAsJsonObject();
			for (String key : o.keySet()) {
				List<double[]> list = new ArrayList<>();
				for (var e : o.getAsJsonArray(key)) {
					JsonArray a = e.getAsJsonArray();
					list.add(new double[]{a.get(0).getAsDouble(), a.get(1).getAsDouble()});
				}
				prices.put(key, list);
			}
		} catch (Exception e) {
			com.wailsvlr.prism.PrismClient.LOG.warn("Could not read {}: {}", file(), e.toString());
		}
	}

	public void savePrices() {
		JsonObject o = new JsonObject();
		prices.forEach((key, list) -> {
			JsonArray arr = new JsonArray();
			for (double[] p : list) { JsonArray a = new JsonArray(); a.add(p[0]); a.add((long) p[1]); arr.add(a); }
			o.add(key, arr);
		});
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		try {
			Files.writeString(file(), gson.toJson(o), StandardCharsets.UTF_8);
			dirty = false;
		} catch (IOException e) {
			com.wailsvlr.prism.PrismClient.LOG.warn("Could not save {}: {}", file(), e.toString());
		}
	}
}
