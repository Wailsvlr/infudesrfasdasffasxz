package com.wailsvlr.prism.modules;

import com.wailsvlr.prism.mixin.AbstractContainerScreenAccessor;
import com.wailsvlr.prism.module.Category;
import com.wailsvlr.prism.module.Module;
import com.wailsvlr.prism.setting.BoolSetting;
import com.wailsvlr.prism.setting.ModeSetting;
import com.wailsvlr.prism.setting.NumberSetting;
import com.wailsvlr.prism.setting.SectionSetting;
import com.wailsvlr.prism.setting.TextSetting;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Sells chosen items through the server's /sell menu: sends /sell, waits for the menu, shift-clicks every chosen stack
 * from the inventory into it and closes it (the server sells what is inside when the menu closes). Items are chosen by
 * marking them in any inventory (hover + mark key) or by typing ids. A marked custom item (one with its own name) only
 * matches that exact name, so a marked "Mystery Note" never sells plain paper.
 */
public class AutoSell extends Module {
	/** Entries of the marked list are separated by this; a custom item is "id=Name". */
	private static final String SEP = " | ";

	private final TextSetting command, title, items, marked;
	private final BoolSetting customItems, protectHotbar, protectEnchanted, autoFill, repeat, whenFull;
	private final NumberSetting delay, perTick, freeSlots;
	/** Last automatic (inventory full) sale, so a server that refuses /sell is not spammed. */
	private long lastAuto;
	private final ModeSetting finish;

	private enum State { IDLE, WAIT_MENU, FILLING, WAIT_REPEAT }

	private State state = State.IDLE;
	private int timer, round, moved, movedItems;
	private boolean byKey;
	/** Slots already clicked in this menu (a click the server refused is not retried forever). */
	private final Set<Integer> tried = new HashSet<>();
	/** The last sell menu handled on its own, so a menu typed open by the player is filled only once. */
	private AbstractContainerMenu handledMenu;
	private String lastResult = "";

	public AutoSell() {
		super("AutoSell", "Sells the items you marked through the server's /sell menu: one key sends /sell, puts them in and closes it", Category.MISC);
		add(new SectionSetting("What To Sell"));
		items = add(new TextSetting("Item List", "Item ids to sell besides the marked ones, separated by commas (e.g. golden_carrot, bone)",
			"", "e.g. golden_carrot, bone"));
		marked = add(new TextSetting("Marked Items", "Filled by the mark key (hover an item in your inventory and press it). A custom item is stored as id=Name",
			"", "hover an item + mark key"));
		customItems = add(new BoolSetting("All Custom Items", "Also sell every item with a custom name or model, marked or not. Careful: this includes renamed gear", false));
		protectEnchanted = add(new BoolSetting("Protect Enchanted", "Never sell enchanted items, even when they match", true));
		protectHotbar = add(new BoolSetting("Protect Hotbar", "Never take items from the hotbar", false));

		add(new SectionSetting("Sell Menu"));
		command = add(new TextSetting("Command", "The command that opens the sell menu, without the slash", "sell", "sell"));
		title = add(new TextSetting("Menu Title", "Text in the sell menu's title (not case sensitive)", "sell", "sell"));
		autoFill = add(new BoolSetting("Fill When Typed", "Also fill the menu when you open it yourself with /sell", true));
		finish = add(new ModeSetting("When Done", "Close: closes the menu so the server sells everything inside. Stay Open: leaves it open for you to check",
			"Close", "Close", "Stay Open"));
		repeat = add(new BoolSetting("Repeat If Full", "The menu was full and items are left: /sell again until everything is sold (up to 5 times)", true))
			.visibleWhen(() -> finish.is("Close"));

		add(new SectionSetting("Automatic"));
		whenFull = add(new BoolSetting("Sell When Full", "Module on: sells by itself when your inventory is full and holds something to sell (no key needed)", false));
		freeSlots = add(new NumberSetting("Free Slots Left", "Sell when this many empty slots or fewer are left", 0, 0, 9, 1, "")).visibleWhen(whenFull::isOn);

		add(new SectionSetting("Speed"));
		delay = add(new NumberSetting("Click Delay", "Ticks between clicks (1 tick = 50 ms). Raise it if the server kicks you for clicking too fast", 1, 1, 10, 1, " t"));
		perTick = add(new NumberSetting("Clicks Per Tick", "Stacks moved per click round", 1, 1, 4, 1, ""));
	}

	@Override
	public String getInfo() { return state == State.IDLE ? null : "selling"; }

	@Override
	protected void onDisable() { stop(); }

	// ---------------------------------------------------------------- what sells

	private static String id(ItemStack s) { return BuiltInRegistries.ITEM.getKey(s.getItem()).toString(); }

	private static boolean custom(ItemStack s) {
		return s.has(DataComponents.CUSTOM_NAME) || s.has(DataComponents.CUSTOM_MODEL_DATA);
	}

	/** The marked-list entry for a stack: the id, plus "=Name" for an item with its own name. */
	public static String markOf(ItemStack s) {
		String id = id(s);
		return s.has(DataComponents.CUSTOM_NAME) ? id + "=" + s.getHoverName().getString() : id;
	}

	// parsed lists, re-read only when the text changes (the frames ask for every slot on every frame)
	private String marksText, idsText;
	private List<String> marksCache = List.of(), idsCache = List.of();

	private List<String> marks() {
		if (!marked.get().equals(marksText)) {
			List<String> out = new ArrayList<>();
			for (String p : marked.get().split("\\s*\\|\\s*")) if (!p.isBlank()) out.add(p.trim());
			marksText = marked.get();
			marksCache = out;
		}
		return marksCache;
	}

	private List<String> typedIds() {
		if (!items.get().equals(idsText)) {
			idsText = items.get();
			idsCache = items.ids();
		}
		return idsCache;
	}

	public boolean isMarked(ItemStack s) { return !s.isEmpty() && marks().contains(markOf(s)); }

	/** Adds the stack to the marked list, or removes it when it is already there. Returns true when it is marked now. */
	public boolean toggleMark(ItemStack s) {
		if (s.isEmpty()) return false;
		LinkedHashSet<String> m = new LinkedHashSet<>(marks());
		String key = markOf(s);
		boolean now = m.add(key);
		if (!now) m.remove(key);
		marked.set(String.join(SEP, m));
		return now;
	}

	public boolean sells(ItemStack s) {
		if (s.isEmpty()) return false;
		if (protectEnchanted.isOn() && s.isEnchanted()) return false;
		if (isMarked(s)) return true;
		if (customItems.isOn() && custom(s)) return true;
		// typed ids match plain items only, so a custom item made from paper does not sell with "paper"
		if (s.has(DataComponents.CUSTOM_NAME)) return false;
		String id = id(s);
		for (String want : typedIds()) if (id.equals(want)) return true;
		return false;
	}

	// ---------------------------------------------------------------- the sell menu

	private boolean isSellMenu(Screen screen) {
		if (!(screen instanceof AbstractContainerScreen<?> s) || screen instanceof InventoryScreen) return false;
		if (mc.player == null || s.getMenu() == mc.player.inventoryMenu) return false;
		String want = title.get().trim().toLowerCase(Locale.ROOT);
		return !want.isEmpty() && s.getTitle().getString().toLowerCase(Locale.ROOT).contains(want);
	}

	private boolean playerSlot(Slot slot) {
		if (!(slot.container instanceof Inventory)) return false;
		int i = slot.getContainerSlot();
		if (i >= 36) return false;   // armour and offhand never sell
		return !(protectHotbar.isOn() && i < 9);
	}

	/** Matching stacks left in the inventory (in items). */
	private int left() {
		if (mc.player == null) return 0;
		int n = 0;
		Inventory inv = mc.player.getInventory();
		for (int i = 0; i < 36; i++) {
			if (protectHotbar.isOn() && i < 9) continue;
			ItemStack s = inv.getItem(i);
			if (sells(s)) n += s.getCount();
		}
		return n;
	}

	/** The sell key: /sell, fill, close (and again while items are left and the menu was full). */
	public void sellNow() {
		if (mc.player == null) return;
		if (state != State.IDLE) return;
		if (left() == 0) {
			say("nothing to sell: mark items first (hover one in your inventory and press the mark key)", ChatFormatting.GRAY);
			return;
		}
		byKey = true;
		money().beginSale();
		round = 0;
		moved = movedItems = 0;
		send();
	}

	private void send() {
		round++;
		tried.clear();
		state = State.WAIT_MENU;
		timer = 60;
		String cmd = command.get().trim();
		if (cmd.startsWith("/")) cmd = cmd.substring(1);
		mc.player.connection.sendCommand(cmd.isEmpty() ? "sell" : cmd);
	}

	private void stop() {
		state = State.IDLE;
		byKey = false;
		tried.clear();
	}

	@Override
	public void onTick() { }

	/** Runs every client tick whether the module is on or not (the key works either way; typed menus need it on). */
	public void tick() {
		if (mc.player == null) { stop(); return; }
		Screen screen = mc.screen;
		switch (state) {
			case IDLE -> {
				if (isEnabled() && whenFull.isOn() && screen == null && System.currentTimeMillis() - lastAuto > 20_000 && freeSlotCount() <= freeSlots.getInt() && left() > 0) {
					lastAuto = System.currentTimeMillis();
					say("inventory full, selling", ChatFormatting.GRAY);
					sellNow();
					return;
				}
				if (isEnabled() && autoFill.isOn() && isSellMenu(screen)) {
					AbstractContainerMenu menu = ((AbstractContainerScreen<?>) screen).getMenu();
					if (menu != handledMenu && left() > 0) {
						handledMenu = menu;
						byKey = false;
						money().beginSale();
						round = 1;
						moved = movedItems = 0;
						tried.clear();
						state = State.FILLING;
						timer = 2;   // let the menu's contents arrive
					}
				}
			}
			case WAIT_MENU -> {
				if (isSellMenu(screen)) {
					handledMenu = ((AbstractContainerScreen<?>) screen).getMenu();
					state = State.FILLING;
					timer = 2;
				} else if (--timer <= 0) {
					say("the sell menu did not open (is the command right? title must contain \"" + title.get() + "\")", ChatFormatting.RED);
					stop();
				}
			}
			case FILLING -> {
				if (!isSellMenu(screen)) { report(); stop(); return; }   // closed by the player or the server
				if (--timer > 0) return;
				timer = delay.getInt();
				AbstractContainerMenu menu = ((AbstractContainerScreen<?>) screen).getMenu();
				int clicks = 0;
				for (Slot slot : menu.slots) {
					if (clicks >= perTick.getInt()) return;
					if (!playerSlot(slot) || tried.contains(slot.index) || !sells(slot.getItem())) continue;
					if (!roomFor(menu, slot.getItem())) break;
					tried.add(slot.index);
					moved++;
					movedItems += slot.getItem().getCount();
					mc.gameMode.handleInventoryMouseClick(menu.containerId, slot.index, 0, ClickType.QUICK_MOVE, mc.player);
					clicks++;
				}
				if (clicks > 0) return;
				finishRound();
			}
			case WAIT_REPEAT -> {
				if (screen != null && !(screen instanceof InventoryScreen)) return;   // wait until the menu is really gone
				if (--timer <= 0) send();
			}
		}
	}

	/** True while the menu side has an empty slot or a stack this item can join. */
	private boolean roomFor(AbstractContainerMenu menu, ItemStack stack) {
		for (Slot s : menu.slots) {
			if (s.container instanceof Inventory) continue;
			ItemStack in = s.getItem();
			if (in.isEmpty() ? s.mayPlace(stack) : ItemStack.isSameItemSameComponents(in, stack) && in.getCount() < in.getMaxStackSize()) return true;
		}
		return false;
	}

	private void finishRound() {
		if (finish.is("Stay Open")) {
			report();
			stop();
			return;
		}
		mc.player.closeContainer();
		boolean more = left() > 0;
		if (more && byKey && repeat.isOn() && round < 5) {
			state = State.WAIT_REPEAT;
			timer = 15;
			return;
		}
		report();
		stop();
	}

	private void report() {
		if (moved == 0) return;
		int rest = left();
		money().endSale(movedItems);
		lastResult = movedItems + " items in " + moved + (moved == 1 ? " stack" : " stacks") + (round > 1 ? " (" + round + " rounds)" : "");
		say("sold " + lastResult + (rest > 0 ? ", " + rest + " left in your inventory" : ""), ChatFormatting.GREEN);
		moved = movedItems = 0;
	}

	private void say(String text, ChatFormatting color) {
		if (mc.player == null) return;
		mc.gui.getChat().addMessage(Component.literal("[Legit] ").withStyle(ChatFormatting.LIGHT_PURPLE)
			.append(Component.literal("AutoSell: " + text).withStyle(color)));
	}

	private static MoneyTracker money() { return com.wailsvlr.prism.PrismClient.modules().get(MoneyTracker.class); }

	private int freeSlotCount() {
		int n = 0;
		for (int i = 0; i < 36; i++) if (mc.player.getInventory().getItem(i).isEmpty()) n++;
		return n;
	}

	public boolean busy() { return state != State.IDLE; }
	public String lastResult() { return lastResult; }

	// ---------------------------------------------------------------- marking in inventories

	/** The mark key inside an inventory: marks or unmarks the hovered item. Returns true when it handled the key. */
	public boolean onMarkKey(Screen screen) {
		if (!(screen instanceof AbstractContainerScreen<?> s) || screen.getFocused() instanceof EditBox) return false;
		Slot slot = ((AbstractContainerScreenAccessor) s).prism$hoveredSlot();
		if (slot == null || !slot.hasItem()) return false;
		boolean now = toggleMark(slot.getItem());
		if (mc.player != null) mc.player.playSound(now ? SoundEvents.EXPERIENCE_ORB_PICKUP : SoundEvents.UI_BUTTON_CLICK.value(), 0.5f, now ? 1.6f : 0.8f);
		return true;
	}

	/** Green frame around a stack that will sell; called while the slot is drawn (menu-local coordinates). */
	public void renderMark(GuiGraphics g, Slot slot) {
		if (mc.player == null || !(slot.container instanceof Inventory) || !slot.hasItem()) return;
		if (slot.getContainerSlot() >= 36 || (protectHotbar.isOn() && slot.getContainerSlot() < 9) || !sells(slot.getItem())) return;
		int x = slot.x - 1, y = slot.y - 1, color = 0xFF55FF7A;
		g.fill(x, y, x + 18, y + 1, color);
		g.fill(x, y + 17, x + 18, y + 18, color);
		g.fill(x, y + 1, x + 1, y + 17, color);
		g.fill(x + 17, y + 1, x + 18, y + 17, color);
	}

	public TextSetting itemList() { return items; }
	public TextSetting markedList() { return marked; }
}
