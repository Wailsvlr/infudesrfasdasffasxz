package com.wailsvlr.prism.gui;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import com.wailsvlr.prism.PrismClient;
import com.wailsvlr.prism.module.Category;
import com.wailsvlr.prism.module.Module;
import com.wailsvlr.prism.modules.ClickGuiModule;
import com.wailsvlr.prism.render.Draw;
import com.wailsvlr.prism.setting.BoolSetting;
import com.wailsvlr.prism.setting.ChipsSetting;
import com.wailsvlr.prism.setting.ColorSetting;
import com.wailsvlr.prism.setting.ModeSetting;
import com.wailsvlr.prism.setting.NumberSetting;
import com.wailsvlr.prism.setting.SectionSetting;
import com.wailsvlr.prism.setting.Setting;
import com.wailsvlr.prism.setting.TextSetting;
import com.wailsvlr.prism.util.ColorUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The ClickGUI: one floating panel per category. Left click toggles a module, right click opens its settings,
 * middle click binds a key. Headers can be dragged (positions are saved) and right-clicked to collapse.
 * Typing filters the modules.
 *
 * Layout and hit testing happen in the same pass: while drawing, every clickable area is recorded as a {@link Hit},
 * and the mouse handlers look the pointer up in that list, so what you see is exactly what you can click.
 */
public class ClickGuiScreen extends net.minecraft.client.gui.screens.Screen {
	// palette
	private static final int BG = 0x111111, HEADER = 0x181818, ROW_HOVER = 0x1F1F1F, SETTINGS_BG = 0x0C0C0C,
		TEXT = 0xF0F0F0, TEXT2 = 0xA8A8A8, TEXT3 = 0x666666, TRACK = 0x2E2E2E, KNOB = 0xF4F4F4;

	private static final float HEADER_H_CLASSIC = 20, MODULE_H_CLASSIC = 17, SETTING_H = 16, SLIDER_H = 23, SECTION_H = 14, SEG_S = 0.8f;
	private static final int[] SWATCHES = {0xFFFFFF, 0x8C8C8C, 0xEF4444, 0xF59E0B, 0xFACC15, 0x22C55E, 0x06B6D4, 0x3B82F6, 0xA855F7};

	private final ClickGuiModule theme;
	private final List<Panel> panels = new ArrayList<>();
	private final List<Hit> hits = new ArrayList<>();
	private final Map<Object, Float> anims = new HashMap<>();
	private Hit dragging;
	private Panel movingPanel;
	private float moveDX, moveDY;
	private Module binding;
	/** The text box being typed into, if any. */
	private TextSetting editing;
	private String search = "";
	private String hoverText;
	/** Tooltip shown next to the pointer after a short hover (Sidebar style). */
	private String tipText;
	private long tipSince;
	/** A short message (profile loaded / saved) shown for a moment at the bottom. */
	private String toast;
	private long toastUntil;
	private long openedAt, lastFrame;
	/** Closing animation: when it started (-1: not closing) and how far the menu still shows (1 .. 0). */
	private long closingAt = -1;
	private float closeK = 1f;
	private float globalAlpha;
	private float dt;

	public ClickGuiScreen() {
		super(Component.literal("LIMP ClickGUI"));
		this.theme = PrismClient.modules().get(ClickGuiModule.class);
	}

	// ------------------------------------------------------------------ model

	private final class Panel {
		final Category category;    // null for the special panels below
		final String special;       // "nav" (Sidebar category list), "profiles", or null for a category panel
		float x, y;
		boolean open = true;
		boolean shown = true;       // Sidebar style: the category list opens and closes panels
		float scroll, contentHeight, viewHeight;
		final Map<Module, Boolean> expanded = new HashMap<>();
		final Map<ColorSetting, Boolean> pickers = new HashMap<>();
		/** Chip groups: the colour last opened with a right click, and whether its picker is open. */
		final Map<ChipsSetting, ColorSetting> chipColor = new HashMap<>();
		final java.util.Set<ChipsSetting> chipOpen = new java.util.HashSet<>();
		/** Where the panel was drawn last frame (x, y, w, h); null until drawn. */
		float[] bounds;

		Panel(Category c, float x, float y) { this.category = c; this.special = null; this.x = x; this.y = y; }

		Panel(String special, float x, float y) { this.category = null; this.special = special; this.x = x; this.y = y; }
	}

	/** A clickable area recorded while drawing. */
	private static final class Hit {
		final float x, y, w, h;
		String tooltip;
		java.util.function.IntConsumer click;          // button
		java.util.function.BiConsumer<Double, Double> drag;   // mouse x, y (also called on press)
		java.util.function.DoubleConsumer scroll;
		Object owner;

		Hit(float x, float y, float w, float h) { this.x = x; this.y = y; this.w = w; this.h = h; }

		boolean contains(double mx, double my) { return mx >= x && mx < x + w && my >= y && my < y + h; }
	}

	private Hit hit(float x, float y, float w, float h) {
		Hit h2 = new Hit(x, y, w, h);
		hits.add(h2);
		return h2;
	}

	@Override
	protected void init() {
		if (!panels.isEmpty()) return;
		JsonObject saved = PrismClient.config().gui;
		if (!saved.has("layout3")) {
			// the panels got narrower and closer together: lay them out again once, at the new width
			for (String k : new String[]{"NAV", "PROFILES", "FRIENDS"}) saved.remove(k);
			for (Category c : Category.values()) saved.remove(c.name());
			theme.panelWidth.reset();
			saved.addProperty("layout3", true);
		}
		if (!saved.has("layout4")) {
			// the logo box moved to the top right and the panels spread out: lay them out again once
			for (String k : new String[]{"NAV", "PROFILES", "FRIENDS"}) saved.remove(k);
			for (Category c : Category.values()) saved.remove(c.name());
			saved.addProperty("layout4", true);
		}
		float w = theme.panelWidth.getFloat();
		float gap = theme.sidebar() ? SIDE_GAP : 12;
		// the Sidebar style's category list; the first time it appears the panels are laid out next to it
		boolean side = theme.sidebar();
		// Sidebar style: a small logo box in the top right (search, settings); its arrow unfolds the category list
		Panel nav = new Panel("nav", side ? Math.max(16, width - w - 16) : 16, side ? 12 : 34);
		nav.open = !side;
		boolean navSaved = saved.has("NAV") && saved.get("NAV").isJsonObject();
		if (navSaved) {
			JsonObject o = saved.getAsJsonObject("NAV");
			if (o.has("x")) nav.x = o.get("x").getAsFloat();
			if (o.has("y")) nav.y = o.get("y").getAsFloat();
			if (o.has("open") && side) nav.open = o.get("open").getAsBoolean();
		}
		nav.x = Math.max(0, Math.min(nav.x, width - w));
		nav.y = Math.max(0, Math.min(nav.y, height - headerH()));
		panels.add(nav);
		int i = 0;
		int cats = 0;
		for (Category c : Category.values()) if (!PrismClient.modules().in(c).isEmpty()) cats++;
		// first-time layout: as many columns as fit (left of the logo box), then a new row under the tallest panel
		float x0 = side ? 16 : 16 + w + gap;
		float limit = side ? Math.max(16, width - w - 16) - gap : width - 8;
		int cols = Math.max(1, (int) ((limit - x0 + gap) / (w + gap)));
		// all in one row: spread over the width, at a few different heights
		boolean spread = side && cats <= cols && cats > 1;
		float step = spread ? w + Math.max(gap, Math.min(40, (limit - x0 - cats * w) / (cats - 1))) : w + gap;
		float rowTop = 34, rowBottom = 34;
		java.util.Map<Panel, float[]> auto = new java.util.LinkedHashMap<>();
		for (Category c : Category.values()) {
			if (PrismClient.modules().in(c).isEmpty()) continue;   // no empty panels
			if (i > 0 && i % cols == 0) rowTop = rowBottom + gap + 2;
			float x = x0 + (i % cols) * step, y = rowTop + (spread ? STAGGER[i % STAGGER.length] : 0);
			rowBottom = Math.max(rowBottom, y + headerH() + PrismClient.modules().in(c).size() * moduleH() + 4);
			Panel p = new Panel(c, x, y);
			auto.put(p, new float[]{x, y});
			if (saved.has(c.name()) && saved.get(c.name()).isJsonObject()) {
				JsonObject o = saved.getAsJsonObject(c.name());
				if (navSaved || !theme.sidebar()) {
					if (o.has("x")) p.x = o.get("x").getAsFloat();
					if (o.has("y")) p.y = o.get("y").getAsFloat();
				}
				if (o.has("open")) p.open = o.get("open").getAsBoolean();
				if (o.has("shown")) p.shown = o.get("shown").getAsBoolean();
			}
			// keep panels on screen when the window got smaller
			p.x = Math.max(0, Math.min(p.x, width - 40));
			p.y = Math.max(0, Math.min(p.y, height - headerH()));
			panels.add(p);
			i++;
		}
		// a layout saved in a bigger window would run off this one: use the fitted layout instead
		if (auto.keySet().stream().anyMatch(p -> p.x + w > width + 1))
			auto.forEach((p, xy) -> { p.x = xy[0]; p.y = xy[1]; });
		// Sidebar style: the profiles panel, closed until opened from the category list
		// profiles: in the next free column when there is one, else under the category list; friends: under the list
		float under = side ? nav.y + navHeight(i) + 6 : 34 + navHeight(i) + 6;
		float sideX = side ? nav.x : 16;
		Panel prof = !side && i < cols && x0 + i * (w + gap) + w <= width - 8 ? new Panel("profiles", x0 + i * (w + gap), 34) : new Panel("profiles", sideX, under);
		prof.shown = false;
		if (saved.has("PROFILES") && saved.get("PROFILES").isJsonObject()) {
			JsonObject o = saved.getAsJsonObject("PROFILES");
			if (o.has("x")) prof.x = o.get("x").getAsFloat();
			if (o.has("y")) prof.y = o.get("y").getAsFloat();
			if (o.has("open")) prof.open = o.get("open").getAsBoolean();
			if (o.has("shown")) prof.shown = o.get("shown").getAsBoolean();
		}
		prof.y = Math.max(0, Math.min(prof.y, height - 110));
		panels.add(prof);
		Panel fr = new Panel("friends", sideX, under);
		fr.shown = false;
		if (saved.has("FRIENDS") && saved.get("FRIENDS").isJsonObject()) {
			JsonObject o = saved.getAsJsonObject("FRIENDS");
			if (o.has("x")) fr.x = o.get("x").getAsFloat();
			if (o.has("y")) fr.y = o.get("y").getAsFloat();
			if (o.has("open")) fr.open = o.get("open").getAsBoolean();
			if (o.has("shown")) fr.shown = o.get("shown").getAsBoolean();
		}
		fr.y = Math.max(0, Math.min(fr.y, height - 110));
		// pushed onto the profiles panel by a short window earlier: put it back underneath when there is room again
		if (Math.abs(fr.x - prof.x) < w && Math.abs(fr.y - prof.y) < 100 && prof.y + 220 <= height) fr.y = prof.y + 110;
		panels.add(fr);
		openedAt = System.nanoTime();
		lastFrame = openedAt;
	}

	@Override
	public boolean isPauseScreen() { return false; }

	/** Opens or closes a side panel; in a short window where it would sit on the other one, that one closes. */
	private void openOnly(Panel p, Panel other) {
		if (p == null) return;
		p.shown = !p.shown;
		if (p.shown && other != null && other.shown && Math.abs(p.x - other.x) < theme.panelWidth.getFloat() && Math.abs(p.y - other.y) < 100)
			other.shown = false;
	}

	/** For the tests: where a side panel ("profiles", "friends", "nav") is. */
	public float[] panelPosForTest(String special) {
		Panel q = special(special);
		return q == null ? null : new float[]{q.x, q.y};
	}

	/** For the tests: where a category's panel is. */
	public float[] catPosForTest(Category c) {
		for (Panel q : panels) if (q.category == c) return new float[]{q.x, q.y};
		return null;
	}

	/** For the tests: is the logo box unfolded into the category list, and what its search shows. */
	public boolean navOpenForTest() { Panel n = special("nav"); return n != null && n.open; }

	public List<String> navResultsForTest() { return navResults.stream().map(Module::getName).toList(); }

	/** For the tests: how many modules the search opened. */
	public int searchOpenedForTest() { return searchOpened.size(); }

	/** For the tests: the category of the module panel drawn last (on top). */
	public com.wailsvlr.prism.module.Category topPanelForTest() {
		for (int i = panels.size() - 1; i >= 0; i--) if (panels.get(i).category != null) return panels.get(i).category;
		return null;
	}

	/** For the tests: every module panel starts inside the window. */
	public boolean panelsFitForTest() {
		float w = theme.panelWidth.getFloat();
		return panels.stream().filter(p -> p.category != null).allMatch(p -> p.x >= 0 && p.x + w <= width && p.y + headerH() <= height);
	}

	/** Plays the closing animation first; the screen really closes when it ends (see render). */
	@Override
	public void onClose() {
		if (closingAt >= 0) return;
		closingAt = System.nanoTime();
		editing = null;
		binding = null;
	}

	@Override
	public void removed() {
		editing = null;
		JsonObject gui = PrismClient.config().gui;
		for (Panel p : panels) {
			JsonObject o = new JsonObject();
			o.addProperty("x", p.x);
			o.addProperty("y", p.y);
			o.addProperty("open", p.open);
			o.addProperty("shown", p.shown);
			gui.add(p.category == null ? p.special.toUpperCase(Locale.ROOT) : p.category.name(), o);
		}
		PrismClient.config().save();
	}

	// ------------------------------------------------------------------ helpers

	private float speed() { return theme.animationSpeed.getFloat(); }

	/** Eases a stored animation value towards {@code target}. */
	private float anim(Object key, float target, float rate) {
		float v = anims.getOrDefault(key, target);
		float k = 1f - (float) Math.exp(-dt * rate * speed());
		v += (target - v) * k;
		if (Math.abs(v - target) < 0.001f) v = target;
		anims.put(key, v);
		return v;
	}

	private int a(int rgb, float alpha) { return ColorUtil.argb(Math.round(255 * alpha * globalAlpha), rgb); }

	private int a(int rgb) { return a(rgb, 1f); }

	private float radius() { return theme.radius.getFloat() / 2f; }

	private boolean cards() { return theme.cards(); }

	private static boolean light(int rgb) {
		int r = rgb >> 16 & 255, gg = rgb >> 8 & 255, b = rgb & 255;
		return (0.299 * r + 0.587 * gg + 0.114 * b) / 255 > 0.6;
	}

	/** Text colour for something drawn on {@code bg}: near-black on light fills, white otherwise. */
	private static int on(int bg) { return light(bg) ? 0x111111 : 0xFFFFFF; }

	/** Switch knob: dark on a light (white) track, light otherwise. */
	private static int knob(int track) { return light(track) ? 0x151515 : KNOB; }

	private boolean blocks() { return theme.blocks(); }

	private boolean sidebar() { return theme.sidebar(); }

	private float headerH() { return cards() || blocks() ? 22 : sidebar() ? 20 : HEADER_H_CLASSIC; }

	private float moduleH() { return cards() ? 19 : sidebar() ? 20 : blocks() ? 18 : MODULE_H_CLASSIC; }

	/** Height of the Sidebar category list with this many categories. */
	private float navHeight(int cats) { return headerH() + 4 + cats * NAV_ROW + 4 + 14 + 3 * NAV_ROW + 4 + NAV_FOOT; }

	/** Sidebar style: one grey for header and body, a slightly darker head on the category list, a band behind MISC. */
	private static final int SIDE_BODY = 0x1A1A1A, SIDE_HEAD = 0x1A1A1A, SIDE_EDGE = 0x2C2C2C, NAV_HEAD = 0x151515, NAV_BAND = 0x141414,
		NAV_DIM = 0x8A8A8A, CHIP_BG = 0x2A2A2A, NAME_OFF = 0xC9C9C9, KEY_BG = 0x2C2C2C, KEY_TEXT = 0x9A9A9A, PROFILE_ROW = 0x222222;
	/** First-time layout: how far each panel starts below the top, so the row does not look like a ruler. */
	private static final float[] STAGGER = {28, 0, 6, 0, 16, 4};
	/** The logo box's search results (Sidebar style, folded). */
	private final List<Module> navResults = new ArrayList<>();
	/** Sidebar style: row height of the category list and the side panels, and the gap between panels. */
	private static final float NAV_ROW = 20, SIDE_GAP = 4, NAV_FOOT = 16;

	/** Sidebar style: the row being drawn and how many rows its panel has (for the colour along the panel). */
	private int rowIndex, rowCount;
	private float panelT0, panelT1;

	private static final int BLOCK_BODY = 0x2A2A2A;

	/** The module under the pointer this frame (Cards style shows its name at the top). */
	private Module hoverModule, lastHoverModule;

	private static final int CARD_BG = 0x0F0F0F, CARD_ROW = 0x1C1C1C, CARD_EDGE = 0x2A2A2A;

	/** The icon file for each category. */
	static String catIcon(Category c) {
		return switch (c) {
			case RENDER -> "render";
			case WORLD -> "world";
			case MISC -> "misc";
			case COMBAT -> "combat";
			case CLIENT -> "client";
		};
	}

	/** A small symbol for each category (drawn with the game's own font). */
	private static String icon(Category c) {
		return switch (c) {
			case RENDER -> "\u25C9";    // eye-like ring
			case WORLD -> "\u26CF";     // pick
			case MISC -> "\u2726";      // star
			case COMBAT -> "\u2694";    // swords
			case CLIENT -> "\u2699";    // gear
		};
	}

	/** A little gear: eight teeth round a ring with a hole of the given colour. */
	private void gear(GuiGraphics g, float cx, float cy, int col, int hole) {
		for (int i = 0; i < 8; i++) {
			double ang = i * Math.PI / 4;
			Draw.round(g, cx + (float) Math.cos(ang) * 3.4f - 0.9f, cy + (float) Math.sin(ang) * 3.4f - 0.9f, 1.8f, 1.8f, 0.5f, col);
		}
		Draw.circle(g, cx, cy, 2.9f, col);
		Draw.circle(g, cx, cy, 1.2f, hole);
	}

	private void click() {
		if (theme.sounds.isOn()) minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.6f, 0.25f));
	}

	private static float easeOutCubic(float t) { t = Math.max(0, Math.min(1, t)); return 1 - (float) Math.pow(1 - t, 3); }

	// ------------------------------------------------------------------ rendering

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partial) {
		if (theme.blur.isOn() && minecraft.options.getMenuBackgroundBlurriness() >= 1) g.blurBeforeThisStratum();
		float t = Math.min(easeOutCubic((System.nanoTime() - openedAt) / 1e9f * 4f * speed()), closeK);
		g.fill(0, 0, width, height, ColorUtil.argb(Math.round(255 * theme.dim.getFloat() / 100f * t), 0x060606));
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
		long now = System.nanoTime();
		Draw.smooth = theme.font.is("Inter");
		dt = Math.min(0.1f, (now - lastFrame) / 1e9f);
		lastFrame = now;
		closeK = 1f;
		if (closingAt >= 0) {
			float k = 1f - (now - closingAt) / 1e9f * 6f * speed();
			if (k <= 0) {
				closingAt = -1;
				super.onClose();
				return;
			}
			closeK = k * k;
		}
		float open = Math.min(easeOutCubic((now - openedAt) / 1e9f * 3.5f * speed()), closeK);
		globalAlpha = open;
		hits.clear();
		hoverText = null;
		lastHoverModule = hoverModule;
		hoverModule = null;

		searchExpand();
		renderTopBar(g, mouseX, mouseY, open);
		for (int i = 0; i < panels.size(); i++) {
			float stagger = Math.min(easeOutCubic(((now - openedAt) / 1e9f * 3.5f * speed()) - i * 0.08f), closeK);
			renderPanel(g, panels.get(i), mouseX, mouseY, stagger);
		}
		// description of whatever is under the pointer (topmost area that has one)
		for (int i = hits.size() - 1; i >= 0; i--) {
			Hit h = hits.get(i);
			if (h.tooltip != null && h.contains(mouseX, mouseY)) { hoverText = h.tooltip; break; }
		}
		renderFooter(g);
		if (sidebar()) renderTooltip(g, mouseX, mouseY);
	}

	/** Sidebar style: the hovered thing's description in a small box by the pointer, after ~0.35 s. */
	private void renderTooltip(GuiGraphics g, int mx, int my) {
		String t = binding == null && theme.descriptions.isOn() ? hoverText : null;
		if (t == null || t.isEmpty()) {
			tipText = null;
			return;
		}
		if (!t.equals(tipText)) {
			tipText = t;
			tipSince = System.currentTimeMillis();
		}
		long shown = System.currentTimeMillis() - tipSince;
		if (shown < 350) return;
		float fade = Math.min(1, (shown - 350) / 120f);
		// wrap to about 190 px
		List<String> lines = new ArrayList<>();
		StringBuilder line = new StringBuilder();
		for (String word : t.split(" ")) {
			if (line.length() > 0 && Draw.width(line + " " + word) > 190) {
				lines.add(line.toString());
				line = new StringBuilder();
			}
			if (line.length() > 0) line.append(' ');
			line.append(word);
		}
		if (line.length() > 0) lines.add(line.toString());
		float w = 0;
		for (String l : lines) w = Math.max(w, Draw.width(l));
		w += 12;
		float h = lines.size() * 10 + 7;
		float x = mx + 12, y = my + 8;
		if (x + w > width - 4) x = mx - 8 - w;
		if (y + h > height - 4) y = my - 6 - h;
		Draw.round(g, x, y, w, h, 3, a(0x0B0B0B, 0.96f * fade));
		Draw.roundOutline(g, x, y, w, h, 3, 0.75f, a(SIDE_EDGE, fade), a(0x0B0B0B, 0.96f * fade));
		for (int i = 0; i < lines.size(); i++) Draw.text(g, lines.get(i), x + 6, y + 4 + i * 10, a(TEXT, fade));
	}

	private void renderTopBar(GuiGraphics g, int mx, int my, float open) {
		if (sidebar()) return;
		float y = 8 - (1 - open) * 10;
		// logo
		String name = "LEGIT";
		float x = 12;
		for (int i = 0; i < name.length(); i++) {
			String ch = name.substring(i, i + 1);
			int col = theme.accentAt(i / (float) (name.length() - 1));
			g.pose().pushMatrix();
			g.pose().translate(x, y + 2);
			g.drawString(font, Draw.styled(Component.literal(ch).withStyle(ChatFormatting.BOLD)), 0, 0, a(col & 0xFFFFFF), false);
			g.pose().popMatrix();
			x += Draw.width(Component.literal(ch).withStyle(ChatFormatting.BOLD)) + 1;
		}
		Draw.text(g, "client", x + 3, y + 2, a(TEXT3));
		if (cards()) {
			if (lastHoverModule != null) {
				String n = lastHoverModule.getName() + (lastHoverModule.getKey() != GLFW.GLFW_KEY_UNKNOWN ? "  [" + keyName(lastHoverModule.getKey()) + "]" : "");
				float cw = Draw.width(n) + 22, ch = 14, cx = width / 2f - cw / 2, cy = y;
				Draw.round(g, cx, cy, cw, ch, 4, a(CARD_BG, 0.95f));
				Draw.roundOutline(g, cx, cy, cw, ch, 4, 0.75f, a(CARD_EDGE), a(CARD_BG, 0.95f));
				Draw.icon(g, "gear", cx + 4, cy + 3, 8, a(theme.accentAt(0) & 0xFFFFFF));
				Draw.text(g, n, cx + 15, cy + 3, a(TEXT));
			}
			return;
		}

		// search box
		float sw = 170, sh = 16, sx = width / 2f - sw / 2, sy = y;
		boolean active = !search.isEmpty();
		Draw.round(g, sx, sy, sw, sh, sh / 2, a(active ? 0x1A1A1A : 0x141414, 0.92f));
		if (active) {
			Draw.roundOutline(g, sx, sy, sw, sh, sh / 2, 1f, a(theme.accentAt(0) & 0xFFFFFF, 0.8f), a(0x1A1A1A, 0.92f));
		}
		Draw.text(g, "⌕", sx + 7, sy + 4, a(TEXT3));
		boolean caret = (System.currentTimeMillis() / 500) % 2 == 0;
		Draw.text(g, search.isEmpty() ? "Type to search modules" : search + (caret ? "_" : ""), sx + 18, sy + 4, a(search.isEmpty() ? TEXT3 : TEXT));
		Hit h = hit(sx, sy, sw, sh);
		h.tooltip = "Start typing anywhere to filter modules. Esc clears the search.";
		h.click = b -> { if (b == 1) search = ""; };
	}

	private void renderFooter(GuiGraphics g) {
		Notifications.draw(g);
		if (sidebar()) {
			String hint = binding != null ? "Press a key to bind " + binding.getName() + "  \u2022  Esc cancels  \u2022  Delete removes the bind"
				: toast != null && System.currentTimeMillis() < toastUntil ? toast : null;
			if (hint != null) {
				int tw = Draw.width(hint);
				float w = tw + 16, h = 14, x = width / 2f - w / 2, y = height - h - 8;
				Draw.round(g, x, y, w, h, 3, a(SIDE_BODY, 0.95f));
				Draw.roundOutline(g, x, y, w, h, 3, 0.75f, a(SIDE_EDGE), a(SIDE_BODY, 0.95f));
				Draw.text(g, hint, x + 8, y + 3, a(binding != null ? (theme.accentAt(0) & 0xFFFFFF) : TEXT2));
			}
			return;
		}
		if (cards()) {
			float sw = 200, sh = 17, sx = width / 2f - sw / 2, sy = height - sh - 6;
			boolean active = !search.isEmpty();
			Draw.round(g, sx, sy, sw, sh, 4, a(CARD_BG, 0.95f));
			Draw.roundOutline(g, sx, sy, sw, sh, 4, 0.75f, a(active ? theme.accentAt(0) & 0xFFFFFF : CARD_EDGE), a(CARD_BG, 0.95f));
			boolean caret = (System.currentTimeMillis() / 500) % 2 == 0;
			Draw.text(g, search.isEmpty() ? "Search..." : search + (caret ? "_" : ""), sx + 8, sy + 4.5f, a(search.isEmpty() ? TEXT3 : TEXT));
			Hit sh2 = hit(sx, sy, sw, sh);
			sh2.tooltip = "Start typing anywhere to filter modules. Esc or right click clears it.";
			sh2.click = b -> { if (b == 1) search = ""; };
			String hint = binding != null ? "Press a key to bind " + binding.getName() + "  \u2022  Esc cancels  \u2022  Delete removes the bind"
				: hoverText != null && theme.descriptions.isOn() ? hoverText : null;
			if (hint != null) {
				int tw = Draw.width(hint);
				Draw.text(g, hint, width / 2f - tw / 2f, sy - 12, a(binding != null ? (theme.accentAt(0) & 0xFFFFFF) : TEXT2));
			}
			return;
		}
		String text;
		if (binding != null) text = "Press a key to bind " + binding.getName() + "  •  Esc cancels  •  Delete removes the bind";
		else if (hoverText != null && theme.descriptions.isOn()) text = hoverText;
		else text = "Left click: toggle  •  Right click: settings  •  Middle click: bind  •  Drag headers to move";
		int tw = Draw.width(text);
		float w = tw + 20, h = 16, x = width / 2f - w / 2, y = height - h - 8;
		Draw.shadow(g, x, y, w, h, h / 2, 4, Math.round(90 * globalAlpha));
		Draw.round(g, x, y, w, h, h / 2, a(0x141414, 0.94f));
		Draw.text(g, text, x + 10, y + 4, a(binding != null ? (theme.accentAt(0) & 0xFFFFFF) : TEXT2));
	}

	private void renderPanel(GuiGraphics g, Panel p, int mx, int my, float appear) {
		if (appear <= 0) return;
		if (p.category == null) {
			if (sidebar() && "nav".equals(p.special)) renderNav(g, p, mx, my, appear);
			else if (sidebar() && p.shown && "profiles".equals(p.special)) renderProfiles(g, p, mx, my, appear);
			else if (sidebar() && p.shown && "friends".equals(p.special)) renderFriends(g, p, mx, my, appear);
			return;
		}
		if (sidebar() && !p.shown) return;
		float saveAlpha = globalAlpha;
		globalAlpha *= appear;
		float w = theme.panelWidth.getFloat();
		float x = p.x - (sidebar() ? (1 - appear) * 18 : 0), y = p.y - (sidebar() ? 0 : (1 - appear) * 14);
		float r = radius();
		float opacity = theme.panelOpacity.getFloat() / 100f;

		List<Module> mods = new ArrayList<>();
		for (Module m : PrismClient.modules().in(p.category)) {
			if (search.isEmpty() || m.getName().toLowerCase(Locale.ROOT).contains(search.toLowerCase(Locale.ROOT)) || settingHit(m) != null) mods.add(m);
		}
		if (!search.isEmpty() && mods.isEmpty()) globalAlpha *= 0.45f;

		float openAnim = anim(p, p.open ? 1f : 0f, 14f);
		// content height (modules + their expanded settings)
		float content = 0;
		for (Module m : mods) content += moduleH() + expandedHeight(p, m);
		p.contentHeight = content;
		float maxView = Math.max(60, height - y - headerH() - 40);
		p.viewHeight = Math.min(content, maxView);
		p.scroll = Math.max(0, Math.min(p.scroll, Math.max(0, content - p.viewHeight)));
		float bodyH = p.viewHeight * openAnim;
		float totalH = headerH() + bodyH + (openAnim > 0.01f ? 4 : 0);
		p.bounds = new float[]{x, y, w, totalH};

		if (sidebar()) {
			Draw.shadow(g, x, y, w, totalH, 3, 6, Math.round(120 * globalAlpha));
			Draw.round(g, x, y, w, totalH, 3, a(SIDE_BODY, opacity));
			Draw.roundCorners(g, x, y, w, headerH(), 3, a(SIDE_HEAD, opacity), true, true, openAnim < 0.05f, openAnim < 0.05f);
			Draw.icon(g, catIcon(p.category), x + 7, y + 5.5f, 9, a(TEXT));
			Draw.text(g, p.category.title(), x + 20, y + 6, a(TEXT));
			int onCount = 0, all = 0;
			for (Module m : PrismClient.modules().in(p.category)) if (m.isToggleable()) { all++; if (m.isEnabled()) onCount++; }
			if (onCount > 0) {
				String c = onCount + "/" + all;
				Draw.text(g, c, x + w - 18 - Draw.width(c), y + 6, a(TEXT3));
			}
			chevron(g, x + w - 10, y + headerH() / 2f, openAnim, a(TEXT2));
		} else if (blocks()) {
			int head = theme.accentAt(0) & 0xFFFFFF;
			Draw.shadow(g, x, y, w, totalH, 2, 5, Math.round(110 * globalAlpha));
			Draw.rect(g, x, y + headerH(), w, totalH - headerH(), a(BLOCK_BODY, 0.82f * opacity));
			Draw.rect(g, x, y, w, headerH(), a(head));
			Draw.rect(g, x, y + headerH() - 1, w, 1, a(ColorUtil.lerp(0xFF000000 | head, 0xFF000000, 0.25f) & 0xFFFFFF));
			int ht = on(head);
			Draw.icon(g, catIcon(p.category), x + 6, y + 6.5f, 9, a(ht));
			g.pose().pushMatrix();
			g.pose().translate(x + 20, y + 7);
			g.drawString(font, Draw.styled(Component.literal(p.category.title()).withStyle(ChatFormatting.BOLD)), 0, 0, a(ht), false);
			g.pose().popMatrix();
			Draw.rect(g, x + w - 13, y + headerH() / 2f - 0.75f, 7, 1.5f, a(ht));
			if (!p.open) Draw.rect(g, x + w - 10.25f, y + headerH() / 2f - 3.5f, 1.5f, 7, a(ht));
		} else if (cards()) {
			float cr = Math.max(r, 4);
			Draw.shadow(g, x, y, w, totalH, cr, 6, Math.round(120 * globalAlpha));
			Draw.round(g, x, y, w, totalH, cr, a(CARD_BG, opacity));
			Draw.roundOutline(g, x, y, w, totalH, cr, 0.75f, a(CARD_EDGE, opacity), a(CARD_BG, opacity));
			Draw.icon(g, catIcon(p.category), x + 8, y + 6.5f, 9, a(theme.accentAt(0) & 0xFFFFFF));
			g.pose().pushMatrix();
			g.pose().translate(x + 21, y + 7);
			g.drawString(font, Draw.styled(Component.literal(p.category.title())), 0, 0, a(TEXT), false);
			g.pose().popMatrix();
		} else {
		// shadow, body
		Draw.shadow(g, x, y, w, totalH, r, 6, Math.round(110 * globalAlpha));
		Draw.round(g, x, y, w, totalH, r, a(BG, opacity));

		// header
		Draw.roundCorners(g, x, y, w, headerH(), r, a(HEADER, Math.min(1, opacity + 0.05f)), true, true, openAnim < 0.05f, openAnim < 0.05f);
		Draw.hGradient(g, x + 1, y + headerH() - 1, w - 2, 1, a(theme.accentAt(0) & 0xFFFFFF, 0.9f), a(theme.accentAt(1) & 0xFFFFFF, 0.9f));
		g.pose().pushMatrix();
		g.pose().translate(x + 8, y + 6);
		g.drawString(font, Draw.styled(Component.literal(p.category.title()).withStyle(ChatFormatting.BOLD)), 0, 0, a(TEXT), false);
		g.pose().popMatrix();
		int enabledCount = 0;
		int toggleable = 0;
		for (Module m : PrismClient.modules().in(p.category)) if (m.isToggleable()) { toggleable++; if (m.isEnabled()) enabledCount++; }
		String count = enabledCount + "/" + toggleable;
		Draw.text(g, count, x + w - 20 - Draw.width(count), y + 6, a(TEXT3));
		chevron(g, x + w - 12, y + headerH() / 2f, openAnim, a(TEXT2));
		}
		Hit header = hit(x, y, w, headerH());
		header.tooltip = "Drag to move. Right click to collapse or expand.";
		header.click = b -> {
			if (b == 0) { movingPanel = p; }
			else if (b == 1) { p.open = !p.open; click(); }
		};
		if (blocks()) {
			Hit collapse = hit(x + w - 18, y, 18, headerH());
			collapse.tooltip = p.open ? "Collapse" : "Expand";
			collapse.click = b -> { p.open = !p.open; click(); };
		}

		if (openAnim < 0.01f) { globalAlpha = saveAlpha; return; }

		// body (scissored)
		float bodyTop = y + headerH() + 2;
		int sx0 = (int) Math.floor(x), sy0 = (int) Math.floor(bodyTop), sx1 = (int) Math.ceil(x + w), sy1 = (int) Math.ceil(bodyTop + bodyH);
		g.enableScissor(sx0, sy0, sx1, sy1);
		float cy = bodyTop - p.scroll;
		rowCount = mods.size();
		rowIndex = 0;
		int cats = 0, mine = 0;
		for (Panel q : panels) if (q.category != null) { if (q.category.ordinal() < p.category.ordinal()) mine++; cats++; }
		panelT0 = cats <= 1 ? 0 : mine / (float) cats;
		panelT1 = cats <= 1 ? 1 : (mine + 1) / (float) cats;
		for (Module m : mods) {
			cy = renderModule(g, p, m, x, cy, w, mx, my, bodyTop, bodyTop + bodyH);
			rowIndex++;
		}
		g.disableScissor();
		Hit scrollArea = hit(x, bodyTop, w, bodyH);
		scrollArea.scroll = d -> p.scroll = Math.max(0, Math.min(p.scroll - (float) d * 18, Math.max(0, p.contentHeight - p.viewHeight)));
		// remove the scroll area from click handling but keep it for wheel events
		hits.remove(scrollArea);
		hits.add(0, scrollArea);

		// scroll bar
		if (p.contentHeight > p.viewHeight + 1) {
			float frac = p.viewHeight / p.contentHeight;
			float barH = Math.max(14, bodyH * frac);
			float barY = bodyTop + (bodyH - barH) * (p.scroll / (p.contentHeight - p.viewHeight));
			Draw.round(g, x + w - 3.5f, barY, 2, barH, 1, a(TEXT3, 0.7f));
		}
		globalAlpha = saveAlpha;
	}

	/** A thin caret: "^" while open, "v" while collapsed (two short strokes, turned smoothly). */
	private void chevron(GuiGraphics g, float cx, float cy, float open, int col) {
		float turn = (float) Math.PI * open;
		g.pose().pushMatrix();
		g.pose().translate(cx, cy);
		g.pose().rotate(turn);
		for (int side = -1; side <= 1; side += 2) {
			g.pose().pushMatrix();
			g.pose().translate(side * 1.15f, 0);
			g.pose().rotate(side * (float) Math.toRadians(-45));
			Draw.rect(g, -1.65f, -0.35f, 3.3f, 0.7f, col);
			g.pose().popMatrix();
		}
		g.pose().popMatrix();
	}

	private float expandedHeight(Panel p, Module m) {
		float target = Boolean.TRUE.equals(p.expanded.get(m)) ? 1f : 0f;
		float v = anims.getOrDefault(m, target);
		return settingsHeight(p, m) * v;
	}

	/** Sections closed until opened (the long appearance and extra lists of the finders). */
	private static final java.util.Set<String> CLOSED_BY_DEFAULT = java.util.Set.of("Look", "Extras");

	private boolean sectionOpen(Module m, SectionSetting sec) {
		JsonObject gui = PrismClient.config().gui;
		String key = m.getName() + "/" + sec.getName();
		if (gui.has("sections") && gui.get("sections").isJsonObject() && gui.getAsJsonObject("sections").has(key))
			return gui.getAsJsonObject("sections").get(key).getAsBoolean();
		return !CLOSED_BY_DEFAULT.contains(sec.getName());
	}

	private void setSectionOpen(Module m, SectionSetting sec, boolean open) {
		JsonObject gui = PrismClient.config().gui;
		if (!gui.has("sections") || !gui.get("sections").isJsonObject()) gui.add("sections", new JsonObject());
		gui.getAsJsonObject("sections").addProperty(m.getName() + "/" + sec.getName(), open);
	}

	/** The settings drawn for a module: a section heading only when something under it is visible, nothing under a closed one. */
	private List<Setting<?>> shown(Module m) {
		List<Setting<?>> all = m.getSettings(), out = new ArrayList<>();
		String q = search.toLowerCase(Locale.ROOT);
		// searching for a setting (not the module's name): only the settings that match, in open sections
		boolean filter = !q.isEmpty() && !m.getName().toLowerCase(Locale.ROOT).contains(q);
		boolean open = true;
		for (int i = 0; i < all.size(); i++) {
			Setting<?> s = all.get(i);
			if (s instanceof SectionSetting sec) {
				boolean any = false;
				for (int j = i + 1; j < all.size() && !(all.get(j) instanceof SectionSetting); j++) {
					if (all.get(j).isVisible() && (!filter || matches(all.get(j), q))) { any = true; break; }
				}
				open = filter || sectionOpen(m, sec);
				if (any) out.add(s);
				continue;
			}
			if (open && s.isVisible() && (!filter || matches(s, q))) out.add(s);
		}
		return out;
	}

	private static boolean matches(Setting<?> s, String q) {
		if (s.getName().toLowerCase(Locale.ROOT).contains(q)) return true;
		if (s instanceof ChipsSetting cs) for (ChipsSetting.Chip c : cs.chips()) if (c.label().toLowerCase(Locale.ROOT).contains(q)) return true;
		return false;
	}

	/** The first setting of {@code m} the search finds (null: none, or the search is empty). */
	private Setting<?> settingHit(Module m) {
		if (search.isEmpty()) return null;
		String q = search.toLowerCase(Locale.ROOT);
		for (Setting<?> s : m.getSettings()) if (!(s instanceof SectionSetting) && s.isVisible() && matches(s, q)) return s;
		return null;
	}

	/** Modules the search opened (to close them again when the search is cleared). */
	private final List<Object[]> searchOpened = new ArrayList<>();

	private void searchExpand() {
		if (search.isEmpty()) {
			for (Object[] pm : searchOpened) ((Panel) pm[0]).expanded.put((Module) pm[1], false);
			searchOpened.clear();
			return;
		}
		String q = search.toLowerCase(Locale.ROOT);
		for (Panel p : panels) {
			if (p.category == null) continue;
			for (Module m : PrismClient.modules().in(p.category)) {
				if (m.getName().toLowerCase(Locale.ROOT).contains(q) || settingHit(m) == null || Boolean.TRUE.equals(p.expanded.get(m))) continue;
				p.expanded.put(m, true);
				searchOpened.add(new Object[]{p, m});
			}
		}
	}

	private float settingsHeight(Panel p, Module m) {
		float h = 4;
		for (Setting<?> s : shown(m)) h += settingHeight(p, s);
		h += SETTING_H; // keybind row
		h += SETTING_H; // reset row
		return h + 2;
	}

	private float settingHeight(Panel p, Setting<?> s) {
		if (s instanceof SectionSetting) return SECTION_H;
		if (s instanceof ChipsSetting cs) {
			float w = theme.panelWidth.getFloat() - 12;
			ColorSetting c = p.chipColor.get(cs);
			float pick = anims.getOrDefault(cs, p.chipOpen.contains(cs) ? 1f : 0f);
			return chipRowsHeight(cs, w) + 6 + (c == null ? 0 : (13 + pickerH(c)) * pick);
		}
		if (s instanceof NumberSetting) return SLIDER_H;
		if (s instanceof TextSetting) return SETTING_H + 14;
		if (s instanceof ColorSetting c) {
			float pick = anims.getOrDefault(c, Boolean.TRUE.equals(p.pickers.get(c)) ? 1f : 0f);
			return SETTING_H + pickerH(c) * pick;
		}
		if (s instanceof ModeSetting md && modeLayout(md, theme.panelWidth.getFloat() - 12) == 1) return SETTING_H + 14;
		return SETTING_H;
	}

	private static final float CHIP_H = 12, CHIP_GAP = 3;

	private static float chipW(ChipsSetting.Chip c) { return 14 + Draw.width(c.label()) * SEG_S + 6; }

	/** Each chip's x, y (relative) and width, flowing into rows of the given width. */
	private static float[][] chipLayout(ChipsSetting cs, float w) {
		List<ChipsSetting.Chip> chips = cs.chips();
		float[][] out = new float[chips.size()][];
		float cx = 0, cy = 0;
		for (int i = 0; i < chips.size(); i++) {
			float cw = Math.min(chipW(chips.get(i)), w);
			if (cx > 0 && cx + cw > w) { cx = 0; cy += CHIP_H + CHIP_GAP; }
			out[i] = new float[]{cx, cy, cw};
			cx += cw + CHIP_GAP;
		}
		// every full row is stretched to the panel's width, so the chips line up on both sides
		float lastY = out.length == 0 ? 0 : out[out.length - 1][1];
		for (int i = 0; i < out.length; ) {
			int j = i;
			while (j < out.length && out[j][1] == out[i][1]) j++;
			if (out[i][1] != lastY) {
				float used = out[j - 1][0] + out[j - 1][2], extra = (w - used) / (j - i);
				for (int k = i; k < j; k++) { out[k][0] += extra * (k - i); out[k][2] += extra; }
			}
			i = j;
		}
		return out;
	}

	private static float chipRowsHeight(ChipsSetting cs, float w) {
		float[][] l = chipLayout(cs, w);
		return l.length == 0 ? 0 : l[l.length - 1][1] + CHIP_H;
	}

	/** The open colour picker's height: square, hue bar, (opacity bar), swatches, hex line. */
	private static float pickerH(ColorSetting c) { return c.hasAlpha() ? 97 : 86; }

	/** How a mode setting is drawn: 0 = segments next to its name, 1 = segments on a line of their own, 2 = one pill that cycles. */
	private int modeLayout(ModeSetting md, float w) {
		float seg = segWidth(md);
		if (seg <= w - 8 - Draw.width(md.getName()) - 10) return 0;
		if (md.getModes().size() <= 4 && seg <= w - 8) return 1;
		return 2;
	}

	private static float segWidth(ModeSetting md) {
		float t = 2;
		for (String o : md.getModes()) t += Draw.width(o) * SEG_S + 8;
		return t;
	}

	/** A segmented control: one cell per mode, the chosen one under a light block that slides between them. */
	private void segmented(GuiGraphics g, ModeSetting md, float sx, float sy, float sw, float top, float bottom) {
		float sh = 11, k = sw / segWidth(md);
		List<String> modes = md.getModes();
		Draw.round(g, sx, sy, sw, sh, 3, a(0x2A2A2A));
		Draw.round(g, sx + 0.6f, sy + 0.6f, sw - 1.2f, sh - 1.2f, 2.6f, a(0x161616));
		float[] xs = new float[modes.size() + 1];
		xs[0] = sx + 1;
		for (int i = 0; i < modes.size(); i++) xs[i + 1] = xs[i] + (Draw.width(modes.get(i)) * SEG_S + 8) * k;
		int sel = md.index();
		float hx = anim(md.getName() + "#segx" + System.identityHashCode(md), xs[sel], 18f);
		float hw = anim(md.getName() + "#segw" + System.identityHashCode(md), xs[sel + 1] - xs[sel], 18f);
		Draw.round(g, hx + 0.5f, sy + 1.5f, hw - 1, sh - 3, 2.2f, a(0xEDEDED));
		for (int i = 0; i < modes.size(); i++) {
			String o = modes.get(i);
			float cw = xs[i + 1] - xs[i];
			float cx = xs[i] + (cw - Draw.width(o) * SEG_S) / 2f;
			boolean chosen = i == sel;
			Draw.textScaled(g, o, cx, sy + 2.4f, SEG_S, a(chosen ? 0x111111 : TEXT2), false);
			Hit cell = clippedHit(xs[i], sy, cw, sh, top, bottom);
			cell.tooltip = md.getDescription();
			cell.click = b -> { if (b == 0) { md.set(o); click(); } else if (b == 1) { md.cycle(-1); click(); } };
			cell.scroll = d -> md.cycle(d > 0 ? -1 : 1);
		}
	}

	private float renderModule(GuiGraphics g, Panel p, Module m, float x, float y, float w, int mx, int my, float clipTop, float clipBottom) {
		boolean visible = y + moduleH() > clipTop && y < clipBottom;
		boolean hover = visible && mx >= x && mx < x + w && my >= Math.max(y, clipTop) && my < Math.min(y + moduleH(), clipBottom);
		float en = anim(m.getName() + "#en", m.isEnabled() ? 1f : 0f, 12f);
		float hv = anim(m.getName() + "#hv", hover ? 1f : 0f, 18f);
		if (visible && cards()) {
			cardRow(g, p, m, x, y, w, mx, my, clipTop, clipBottom, hover, en, hv);
		} else if (visible && sidebar()) {
			sideRow(g, p, m, x, y, w, mx, my, clipTop, clipBottom, hover, en, hv);
		} else if (visible && blocks()) {
			blockRow(g, p, m, x, y, w, mx, my, clipTop, clipBottom, hover, en, hv);
		} else if (visible) {
			if (hv > 0) Draw.rect(g, x, y, w, moduleH(), a(ROW_HOVER, hv * 0.9f));
			if (en > 0) {
				Draw.hGradient(g, x, y, w * 0.85f, moduleH(), a(theme.accentAt(0) & 0xFFFFFF, 0.20f * en), a(theme.accentAt(1) & 0xFFFFFF, 0f));
				Draw.round(g, x + 2, y + 3, 2f, moduleH() - 6, 1f, a(theme.accentAt(0) & 0xFFFFFF, en));
			}
			int textCol = ColorUtil.lerp(0xFF000000 | TEXT2, 0xFF000000 | TEXT, en) & 0xFFFFFF;
			Draw.text(g, m.getName(), x + 9 + en * 2, y + 5, a(textCol));
			// bind tag and settings dots
			float rightX = x + w - 9;
			boolean expanded = Boolean.TRUE.equals(p.expanded.get(m));
			float ex = anim(m, expanded ? 1f : 0f, 14f);
			for (int i = 0; i < 3; i++) {
				float dy = (i - 1) * 2.6f * (1 - ex);
				float dx = (i - 1) * 2.6f * ex;
				Draw.circle(g, rightX + dx, y + moduleH() / 2f + dy, 0.9f, a(hover || expanded ? TEXT2 : TEXT3));
			}
			if (m.getKey() != GLFW.GLFW_KEY_UNKNOWN) {
				String key = keyName(m.getKey());
				float kw = Draw.width(key) + 6;
				Draw.round(g, rightX - 8 - kw, y + 3.5f, kw, 10, 3, a(0x282828, 0.9f));
				Draw.text(g, key, rightX - 5 - kw, y + 4.5f, a(TEXT2));
			}
			if (binding == m) {
				Draw.round(g, x + w - 70, y + 3.5f, 58, 10, 3, a(theme.accentAt(0) & 0xFFFFFF, 0.25f));
				Draw.text(g, "press a key", x + w - 66, y + 4.5f, a(TEXT));
			}
			if (hover) {
				Hit h = hit(x, Math.max(y, clipTop), w, Math.min(y + moduleH(), clipBottom) - Math.max(y, clipTop));
				h.tooltip = m.getDescription();
				h.click = b -> {
					if (b == 0 && m.isToggleable()) { m.toggle(); click(); }
					else if (b == 0 || b == 1) { p.expanded.put(m, !expanded); click(); }
					else if (b == 2) { binding = m; click(); }
				};
			}
		}
		y += moduleH();
		float ex = anims.getOrDefault(m, Boolean.TRUE.equals(p.expanded.get(m)) ? 1f : 0f);
		if (ex > 0.001f) {
			float full = settingsHeight(p, m);
			float shown = full * ex;
			if (!sidebar()) {
				Draw.rect(g, x + 3, y, w - 6, shown, a(SETTINGS_BG, 0.85f));
				Draw.rect(g, x + 3, y, 1, shown, a(theme.accentAt(0.5f) & 0xFFFFFF, 0.35f));
			}
			float top = Math.max(clipTop, y), bottom = Math.min(clipBottom, y + shown);
			float sy = y + 2;
			for (Setting<?> s : shown(m)) {
				float sh = settingHeight(p, s);
				if (sy + sh > top && sy < bottom) renderSetting(g, p, m, s, x + 6, sy, w - 12, mx, my, top, bottom);
				sy += sh;
			}
			if (sy + SETTING_H > top && sy < bottom) renderBindRow(g, m, x + 6, sy, w - 12, mx, my, top, bottom);
			sy += SETTING_H;
			if (sy + SETTING_H > top && sy < bottom) renderResetRow(g, m, x + 6, sy, w - 12, mx, my, top, bottom);
			y += shown;
		}
		return y;
	}

	/** Sidebar style: the window with the logo and one row per category; a row opens or closes that category's panel. */
	private void renderNav(GuiGraphics g, Panel nav, int mx, int my, float appear) {
		float save = globalAlpha;
		globalAlpha *= appear;
		float w = theme.panelWidth.getFloat(), x = nav.x - (1 - appear) * 18, y = nav.y, rowH = NAV_ROW;
		List<Panel> cats = new ArrayList<>();
		for (Panel p : panels) if (p.category != null) cats.add(p);
		cats.sort(java.util.Comparator.comparingInt(p -> p.category.ordinal()));   // panels are reordered when clicked; the list keeps its order
		// folded: just the logo box, with the search results under it
		float unfold = anim("nav#open", nav.open ? 1f : 0f, 14f);
		navResults.clear();
		if (!search.isEmpty() && !nav.open) {
			String q = search.toLowerCase(Locale.ROOT);
			for (Module m : PrismClient.modules().all()) {
				if (navResults.size() >= 8) break;
				if (m.getName().toLowerCase(Locale.ROOT).contains(q) || settingHit(m) != null) navResults.add(m);
			}
		}
		float results = navResults.isEmpty() ? 0 : 2 + navResults.size() * moduleH() + 2;
		float h = nav.open ? navHeight(cats.size()) : headerH() + results;
		if (unfold > 0.01f && unfold < 0.99f) h = headerH() + results + (navHeight(cats.size()) - headerH()) * unfold;
		nav.bounds = new float[]{x, y, w, h};
		Draw.shadow(g, x, y, w, h, 3, 6, Math.round(120 * globalAlpha));
		Draw.round(g, x, y, w, h, 3, a(SIDE_BODY, 0.97f));
		Draw.roundCorners(g, x, y, w, headerH(), 3, a(NAV_HEAD, 0.97f), true, true, h <= headerH() + 0.5f, h <= headerH() + 0.5f);
		if (search.isEmpty()) {
			String name = "LEGIT";
			Component logo = Draw.word(name, i -> theme.accentAt(i / (float) (name.length() - 1)));
			g.pose().pushMatrix();
			g.pose().translate(x + 8, y + 6);
			g.drawString(font, logo, 0, 0, a(0xFFFFFF), false);
			g.pose().popMatrix();
		} else {
			boolean caret = (System.currentTimeMillis() / 500) % 2 == 0;
			Draw.text(g, search + (caret ? "_" : ""), x + 8, y + 6, a(TEXT));
		}
		Draw.icon(g, "search", x + w - 43, y + 5.5f, 9, a(search.isEmpty() ? TEXT3 : theme.accentAt(0) & 0xFFFFFF));
		boolean gh = mx >= x + w - 30 && mx < x + w - 18 && my >= y && my < y + headerH();
		Draw.icon(g, "gear", x + w - 29, y + 5.5f, 9, a(gh ? TEXT : TEXT3));
		boolean ch = mx >= x + w - 17 && mx < x + w && my >= y && my < y + headerH();
		chevron(g, x + w - 10, y + headerH() / 2f, unfold, a(ch ? TEXT : TEXT2));
		Hit head = hit(x, y, w, headerH());
		head.tooltip = "Type anywhere to search. Drag to move. The arrow shows the categories.";
		head.click = b -> { if (b == 0) movingPanel = nav; else if (b == 1) search = ""; };
		Hit fold = hit(x + w - 17, y, 17, headerH());
		fold.tooltip = nav.open ? "Fold into the logo box" : "Show the categories, profiles and friends";
		fold.click = b -> { nav.open = !nav.open; click(); };
		Hit gearHit = hit(x + w - 30, y, 12, headerH());
		gearHit.tooltip = "The menu's own settings: style, colours, font";
		gearHit.click = b -> {
			for (Panel q : panels) {
				if (q.category == com.wailsvlr.prism.module.Category.CLIENT) {
					q.shown = true;
					q.open = true;
					Module gui = PrismClient.modules().get(ClickGuiModule.class);
					q.expanded.put(gui, !Boolean.TRUE.equals(q.expanded.get(gui)));
				}
			}
			click();
		};
		if (!navResults.isEmpty()) {
			float ry0 = y + headerH() + 2;
			for (Module m : navResults) { navResult(g, m, x, ry0, w, mx, my); ry0 += moduleH(); }
		}
		if (unfold < 0.01f) { globalAlpha = save; return; }
		g.enableScissor((int) Math.floor(x), (int) Math.floor(y + headerH()), (int) Math.ceil(x + w), (int) Math.ceil(y + h));
		float ry = y + headerH() + 4;
		for (Panel p : cats) {
			boolean hover = mx >= x && mx < x + w && my >= ry && my < ry + rowH;
			float hv = anim(p.category.name() + "#nav", hover ? 1f : 0f, 18f);
			if (hv > 0) Draw.rect(g, x, ry, w, rowH, a(0xFFFFFF, 0.05f * hv));
			// an open category is bright, a closed one dim (no bars)
			float on = anim(p.category.name() + "#navon", p.shown ? 1f : 0f, 14f);
			int col = ColorUtil.lerp(0xFF000000 | (hover ? 0xB0B0B0 : NAV_DIM), 0xFF000000 | (theme.accentAt(0) & 0xFFFFFF), on) & 0xFFFFFF;
			Draw.icon(g, catIcon(p.category), x + 9, ry + 5.5f, 9, a(col));
			Draw.text(g, p.category.title(), x + 23, ry + 6, a(ColorUtil.lerp(0xFF000000 | (hover ? 0xB0B0B0 : NAV_DIM), 0xFF000000 | TEXT, on) & 0xFFFFFF));
			chevronSide(g, x + w - 10, ry + rowH / 2f, -1, a(hover || p.shown ? TEXT2 : TEXT3));
			Hit hh = hit(x, ry, w, rowH);
			hh.tooltip = (p.shown ? "Close" : "Open") + " the " + p.category.title() + " panel";
			hh.click = b -> { p.shown = !p.shown; click(); };
			ry += rowH;
		}
		ry += 4;
		Draw.rect(g, x, ry, w, 14, a(NAV_BAND));
		Draw.textScaled(g, "MISC", x + 9, ry + 4.5f, 0.75f, a(TEXT3), false);
		ry += 14;
		Panel profPanel = special("profiles"), friendPanel = special("friends");
		String cur = com.wailsvlr.prism.util.Profiles.current();
		ry = navRow(g, x, ry, w, rowH, mx, my, "profiles", "Profiles", cur, profPanel != null && profPanel.shown,
			"Open or close the profiles: saved sets of modules and settings", () -> openOnly(profPanel, friendPanel));
		List<String> fl = com.wailsvlr.prism.util.Friends.list();
		int online = 0;
		for (String f : fl) if (com.wailsvlr.prism.util.Friends.online(f)) online++;
		ry = navRow(g, x, ry, w, rowH, mx, my, "friends", "Friends", fl.isEmpty() ? "" : online + "/" + fl.size(), friendPanel != null && friendPanel.shown,
			"Open or close the friends list (middle click a player in game to add them)", () -> openOnly(friendPanel, profPanel));
		ry = navRow(g, x, ry, w, rowH, mx, my, "hud", "Edit HUD", "", false,
			"Drag the module list, the counters and the info box where you want them", () -> minecraft.setScreen(new HudEditorScreen(this)));
		// footer: who you are on the left, the HUD editor and the menu's settings on the right
		ry += 4;
		Draw.rect(g, x + 6, ry, w - 12, 0.5f, a(SIDE_EDGE));
		float fy = ry + 3.5f;
		Draw.icon(g, "friends", x + 8, fy, 9, a(TEXT3));
		String me = minecraft.getUser().getName();
		while (me.length() > 3 && Draw.width(me) * 0.75f > w - 50) me = me.substring(0, me.length() - 1);
		Draw.textScaled(g, me, x + 20, fy + 2.5f, 0.75f, a(TEXT3), false);
		boolean hudH = mx >= x + w - 34 && mx < x + w - 21 && my >= ry && my < ry + NAV_FOOT;
		boolean setH = mx >= x + w - 19 && mx < x + w - 6 && my >= ry && my < ry + NAV_FOOT;
		Draw.icon(g, "hud", x + w - 32, fy, 9, a(hudH ? TEXT : TEXT3));
		Draw.icon(g, "gear", x + w - 17, fy, 9, a(setH ? TEXT : TEXT3));
		Hit hudHit = hit(x + w - 34, ry, 13, NAV_FOOT);
		hudHit.tooltip = "Edit HUD";
		hudHit.click = b -> { minecraft.setScreen(new HudEditorScreen(this)); click(); };
		Hit setHit = hit(x + w - 19, ry, 13, NAV_FOOT);
		setHit.tooltip = "The menu's own settings";
		setHit.click = gearHit.click;
		g.disableScissor();
		globalAlpha = save;
	}

	/** A search result under the folded logo box: click toggles it, right click opens its settings in its panel. */
	private void navResult(GuiGraphics g, Module m, float x, float y, float w, int mx, int my) {
		float h = moduleH(), cy = y + h / 2f;
		boolean hover = mx >= x && mx < x + w && my >= y && my < y + h;
		boolean filled = m.isEnabled() && m.isToggleable();
		int fill = theme.accentAt(0) & 0xFFFFFF;
		if (filled) Draw.rect(g, x, y, w, h, a(fill));
		if (hover) Draw.rect(g, x, y, w, h, a(0xFFFFFF, 0.06f));
		Draw.text(g, m.getName(), x + 8, cy - 4, a(filled ? on(fill) : NAME_OFF));
		float dx = x + w - 8;
		for (int k = -1; k <= 1; k++) Draw.circle(g, dx, cy + k * 3f, 0.9f, a(filled ? on(fill) : TEXT2));
		if (m.getKey() != GLFW.GLFW_KEY_UNKNOWN) keyChip(g, keyName(m.getKey()), dx - 8, cy, filled);
		Hit row = hit(x, y, w, h);
		row.tooltip = m.getDescription() + "  (right click: settings)";
		row.click = b -> {
			if (b == 0 && m.isToggleable()) m.toggle();
			else if (b == 0 || b == 1) {
				for (Panel q : panels) if (q.category == m.getCategory()) { q.shown = true; q.open = true; q.expanded.put(m, true); panels.remove(q); panels.add(q); break; }
			}
			click();
		};
	}

	/** The small key label on a row: dark grey without an edge (light on a filled row); right edge at {@code right}. */
	private void keyChip(GuiGraphics g, String key, float right, float cy, boolean filled) {
		float ks = 0.75f, kw = Draw.width(key) * ks + 7, kx = right - kw;
		Draw.round(g, kx, cy - 4.5f, kw, 9, 2f, a(filled ? 0xDADADA : KEY_BG));
		Draw.textScaled(g, key, kx + 3.5f, cy - 2.6f, ks, a(filled ? 0x5A5A5A : KEY_TEXT), false);
	}

	private Panel special(String name) {
		for (Panel q : panels) if (name.equals(q.special)) return q;
		return null;
	}

	/** One row of the category list's Misc section: icon, label, an optional chip on the right. */
	private float navRow(GuiGraphics g, float x, float ry, float w, float rowH, int mx, int my, String icon, String label, String chip,
						 boolean active, String tip, Runnable action) {
		boolean hover = mx >= x && mx < x + w && my >= ry && my < ry + rowH;
		float hv = anim(label + "#nav", hover ? 1f : 0f, 18f);
		if (hv > 0) Draw.rect(g, x, ry, w, rowH, a(0xFFFFFF, 0.05f * hv));
		int col = active ? TEXT : hover ? 0xB0B0B0 : NAV_DIM;
		Draw.icon(g, icon, x + 9, ry + 5.5f, 9, a(active ? theme.accentAt(0) & 0xFFFFFF : col));
		Draw.text(g, label, x + 23, ry + 6, a(col));
		if (chip != null && !chip.isEmpty()) {
			String c = chip;
			while (c.length() > 1 && Draw.width(c) * 0.8f > w - 84) c = c.substring(0, c.length() - 1);
			if (!c.equals(chip)) c += "\u2026";
			float cw = Draw.width(c) * 0.8f + 8;
			Draw.round(g, x + w - 18 - cw, ry + 4.5f, cw, 11, 2.5f, a(CHIP_BG));
			Draw.textScaled(g, c, x + w - 14 - cw, ry + 6.6f, 0.8f, a(TEXT2), false);
		}
		chevronSide(g, x + w - 10, ry + rowH / 2f, -1, a(hover || active ? TEXT2 : TEXT3));
		Hit hh = hit(x, ry, w, rowH);
		hh.tooltip = tip;
		hh.click = b -> { action.run(); click(); };
		return ry + rowH;
	}

	/** Sidebar style: the friends list. Add by typing a name; right click removes; the dot shows who is online now. */
	private void renderFriends(GuiGraphics g, Panel p, int mx, int my, float appear) {
		float save = globalAlpha;
		globalAlpha *= appear;
		float w = theme.panelWidth.getFloat(), x = p.x - (1 - appear) * 18, y = p.y, rowH = 22;
		List<String> names = new ArrayList<>(com.wailsvlr.prism.util.Friends.list());
		if (friendsOnline) names.removeIf(n -> !com.wailsvlr.prism.util.Friends.online(n));
		float openAnim = anim(p, p.open ? 1f : 0f, 14f);
		float body = (16 + 22 + names.size() * rowH + (names.isEmpty() ? 26 : 0) + 4) * openAnim;
		float h = headerH() + body;
		Draw.shadow(g, x, y, w, h, 3, 6, Math.round(120 * globalAlpha));
		Draw.round(g, x, y, w, h, 3, a(SIDE_BODY, 0.97f));
		Draw.roundCorners(g, x, y, w, headerH(), 3, a(SIDE_HEAD, 0.97f), true, true, openAnim < 0.05f, openAnim < 0.05f);
		Draw.icon(g, "friends", x + 7, y + 5.5f, 9, a(TEXT));
		Draw.text(g, "Friends", x + 20, y + 6, a(TEXT));
		boolean addOk = com.wailsvlr.prism.util.Friends.valid(search) && !com.wailsvlr.prism.util.Friends.isFriend(search);
		boolean addH = mx >= x + w - 31 && mx < x + w - 18 && my >= y && my < y + headerH();
		Draw.icon(g, "plus", x + w - 29, y + 6, 8, a(addH ? TEXT : TEXT3));
		chevron(g, x + w - 10, y + headerH() / 2f, openAnim, a(TEXT2));
		Hit head = hit(x, y, w, headerH());
		head.tooltip = "Drag to move. Right click to collapse.";
		head.click = b -> { if (b == 0) movingPanel = p; else if (b == 1) { p.open = !p.open; click(); } };
		Hit addHit = hit(x + w - 31, y, 13, headerH());
		addHit.tooltip = "Add the name you typed as a friend";
		addHit.click = b -> {
			if (addOk) { com.wailsvlr.prism.util.Friends.add(search); toast("Added " + search + " as a friend"); search = ""; }
			else toast("Type a player name first");
			click();
		};
		if (openAnim > 0.05f) {
			g.enableScissor((int) x, (int) (y + headerH()), (int) Math.ceil(x + w), (int) Math.ceil(y + h));
			// tabs: everyone / only the ones online now
			float ty = y + headerH() + 4, tw = (w - 12) / 2f;
			Draw.round(g, x + 6, ty, w - 12, 12, 2.5f, a(0x141414));
			float sel = anim("friendsTab", friendsOnline ? 1f : 0f, 18f);
			Draw.round(g, x + 6.5f + sel * tw, ty + 0.5f, tw - 1, 11, 2.2f, a(CHIP_BG));
			String[] tabs = {"ALL FRIENDS", "ONLINE"};
			for (int t = 0; t < 2; t++) {
				boolean chosen = (t == 1) == friendsOnline;
				float lw = Draw.width(tabs[t]) * 0.68f;
				Draw.textScaled(g, tabs[t], x + 6 + t * tw + (tw - lw) / 2f, ty + 3.6f, 0.68f, a(chosen ? 0xBDBDBD : 0x6A6A6A), false);
				Hit th = hit(x + 6 + t * tw, ty, tw, 12);
				boolean online = t == 1;
				th.tooltip = online ? "Only friends who are on this server now" : "Everyone on your friends list";
				th.click = b -> { friendsOnline = online; click(); };
			}
			float by = ty + 16;
			boolean ok = addOk;
			boolean bh = ok && mx >= x + 6 && mx < x + w - 6 && my >= by && my < by + 15;
			Draw.round(g, x + 6, by, w - 12, 15, 3, a(bh ? 0x2E2E2E : 0x232323));
			String label = search.isEmpty() ? "Type a name to add" : ok ? "+  Add " + search : "Not a player name";
			Draw.textScaled(g, label, x + w / 2f - Draw.width(label) * 0.85f / 2f, by + 4.2f, 0.85f, a(ok ? TEXT : TEXT3), false);
			Hit nb = hit(x + 6, by, w - 12, 15);
			nb.tooltip = "Type a player name anywhere in this menu, then click here to add them.";
			nb.click = b -> {
				if (!ok) return;
				String n = search;
				com.wailsvlr.prism.util.Friends.add(n);
				search = "";
				toast("Added " + n + " as a friend");
				click();
			};
			float ry = by + 19;
			if (names.isEmpty()) {
				Draw.text(g, friendsOnline ? "Nobody online" : "No friends yet", x + 8, ry + 2, a(TEXT3));
				Draw.textScaled(g, "middle click a player to add", x + 8, ry + 13, 0.75f, a(TEXT3), false);
			}
			for (String n : names) {
				boolean hover = mx >= x && mx < x + w && my >= ry && my < ry + rowH;
				boolean on = com.wailsvlr.prism.util.Friends.online(n);
				if (hover) Draw.rect(g, x, ry, w, rowH, a(0xFFFFFF, 0.05f));
				face(g, n, x + 8, ry + 5, 12, on);
				Draw.text(g, n, x + 25, ry + 3.5f, a(on ? TEXT : 0xB0B0B0));
				Draw.textScaled(g, on ? "Online" : "Offline", x + 25, ry + 13, 0.7f, a(on ? 0x9C9C9C : 0x5E5E5E), false);
				for (int i = -1; i <= 1; i++) Draw.circle(g, x + w - 8, ry + rowH / 2f + i * 3f, 0.9f, a(hover ? TEXT2 : TEXT3));
				Hit rh = hit(x, ry, w, rowH);
				rh.tooltip = "Right click: remove " + n + " from your friends";
				rh.click = b -> {
					if (b == 1) {
						com.wailsvlr.prism.util.Friends.remove(n);
						toast("Removed " + n);
						click();
					}
				};
				ry += rowH;
			}
			g.disableScissor();
		}
		globalAlpha = save;
	}

	/** Friends panel: showing only the ones online now. */
	private boolean friendsOnline;

	/** A player's face from their skin (the default skin for their name when they are not here), greyed out when offline. */
	private void face(GuiGraphics g, String name, float x, float y, float size, boolean online) {
		net.minecraft.world.entity.player.PlayerSkin skin = null;
		if (minecraft.getConnection() != null) {
			var info = minecraft.getConnection().getPlayerInfo(name);
			if (info != null) skin = info.getSkin();
		}
		if (skin == null) skin = net.minecraft.client.resources.DefaultPlayerSkin.get(
			java.util.UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
		net.minecraft.resources.Identifier tex = skin.body().texturePath();
		int tint = a(online ? 0xFFFFFF : 0x7A7A7A);
		Draw.round(g, x - 0.5f, y - 0.5f, size + 1, size + 1, 1.5f, a(0x000000, 0.5f));
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(size / 8f, size / 8f);
		g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, tex, 0, 0, 8f, 8f, 8, 8, 64, 64, tint);
		g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, tex, 0, 0, 40f, 8f, 8, 8, 64, 64, tint);
		g.pose().popMatrix();
	}

	/** Sidebar style: saved profiles. New saves the current setup; click loads; right click saves over; shift + right click deletes. */
	private void renderProfiles(GuiGraphics g, Panel p, int mx, int my, float appear) {
		float save = globalAlpha;
		globalAlpha *= appear;
		float w = theme.panelWidth.getFloat(), x = p.x - (1 - appear) * 18, y = p.y, rowH = 20;
		List<String> names = com.wailsvlr.prism.util.Profiles.list();
		String cur = com.wailsvlr.prism.util.Profiles.current();
		float openAnim = anim(p, p.open ? 1f : 0f, 14f);
		float body = (22 + names.size() * rowH + (names.isEmpty() ? 14 : 0) + 4) * openAnim;
		float h = headerH() + body;
		Draw.shadow(g, x, y, w, h, 3, 6, Math.round(120 * globalAlpha));
		Draw.round(g, x, y, w, h, 3, a(SIDE_BODY, 0.97f));
		Draw.roundCorners(g, x, y, w, headerH(), 3, a(SIDE_HEAD, 0.97f), true, true, openAnim < 0.05f, openAnim < 0.05f);
		Draw.icon(g, "profiles", x + 7, y + 5.5f, 9, a(TEXT));
		Draw.text(g, "Profiles", x + 20, y + 6, a(TEXT));
		boolean folderH = mx >= x + w - 32 && mx < x + w - 18 && my >= y && my < y + headerH();
		Draw.icon(g, "folder", x + w - 30, y + 6, 9, a(folderH ? TEXT : TEXT3));
		chevron(g, x + w - 10, y + headerH() / 2f, openAnim, a(TEXT2));
		Hit head = hit(x, y, w, headerH());
		head.tooltip = "Drag to move. Right click to collapse.";
		head.click = b -> { if (b == 0) movingPanel = p; else if (b == 1) { p.open = !p.open; click(); } };
		if (openAnim > 0.05f) {
			g.enableScissor((int) x, (int) (y + headerH()), (int) Math.ceil(x + w), (int) Math.ceil(y + h));
			float by = y + headerH() + 4;
			// "+ New" button: the name is what you typed (search text), or "Profile N"
			float bw = w - 12;
			boolean bh = mx >= x + 6 && mx < x + 6 + bw && my >= by && my < by + 15;
			Draw.round(g, x + 6, by, bw, 15, 3, a(bh ? 0x2E2E2E : CHIP_BG));
			String label = search.isEmpty() ? "CREATE NEW" : "SAVE AS " + search.toUpperCase(Locale.ROOT);
			while (label.length() > 4 && Draw.width(label) * 0.75f > bw - 22) label = label.substring(0, label.length() - 1);
			float lw = Draw.width(label) * 0.75f + 11;
			Draw.icon(g, "plus", x + 6 + (bw - lw) / 2f, by + 3.5f, 8, a(TEXT));
			Draw.textScaled(g, label, x + 6 + (bw - lw) / 2f + 11, by + 5, 0.75f, a(TEXT), false);
			Hit fb = hit(x + w - 32, y, 14, headerH());
			fb.tooltip = "Open the profiles folder (config/prismclient-profiles) to share or back up profiles";
			fb.click = b -> {
				try {
					java.nio.file.Files.createDirectories(com.wailsvlr.prism.util.Profiles.dir());
					net.minecraft.util.Util.getPlatform().openPath(com.wailsvlr.prism.util.Profiles.dir());
				} catch (Exception ignored) { }
				click();
			};
			Hit nb = hit(x + 6, by, bw, 15);
			nb.tooltip = "Saves every module, setting and key as a new profile. Type a name first to choose it.";
			nb.click = b -> {
				String name = com.wailsvlr.prism.util.Profiles.freeName(search.isEmpty() ? "Profile" : search);
				boolean ok = com.wailsvlr.prism.util.Profiles.save(name);
				search = "";
				toast(ok ? "Saved profile " + name : "Could not save the profile");
				click();
			};
			float ry = by + 19;
			if (names.isEmpty()) {
				Draw.text(g, "No profiles yet", x + 8, ry + 2, a(TEXT3));
			}
			for (String n : names) {
				boolean hover = mx >= x && mx < x + w && my >= ry && my < ry + rowH;
				boolean isCur = n.equals(cur);
				int fill = theme.accentAt(0) & 0xFFFFFF;
				// one box per profile, the loaded one filled
				Draw.round(g, x + 6, ry + 1.5f, w - 12, rowH - 3, 2f, a(isCur ? fill : hover ? 0x2A2A2A : PROFILE_ROW));
				Draw.text(g, n, x + 11, ry + 6, a(isCur ? on(fill) : hover ? TEXT : 0x9A9A9A));
				for (int i = -1; i <= 1; i++) Draw.circle(g, x + w - 12, ry + rowH / 2f + i * 3f, 0.9f, a(isCur ? on(fill) : TEXT3));
				Hit rh = hit(x, ry, w, rowH);
				rh.tooltip = "Left click: load  \u2022  Right click: save the current setup into it  \u2022  Shift + right click: delete";
				rh.click = b -> {
					if (b == 0) toast(com.wailsvlr.prism.util.Profiles.load(n) ? "Loaded " + n : "Could not load " + n);
					else if (b == 1 && minecraft.hasShiftDown()) toast(com.wailsvlr.prism.util.Profiles.delete(n) ? "Deleted " + n : "Could not delete " + n);
					else if (b == 1) toast(com.wailsvlr.prism.util.Profiles.save(n) ? "Saved into " + n : "Could not save " + n);
					click();
				};
				ry += rowH;
			}
			g.disableScissor();
		}
		globalAlpha = save;
	}

	private void toast(String text) {
		toast = text;
		toastUntil = System.currentTimeMillis() + 2200;
	}

	/** Sidebar style row: an enabled module fills its row with the accent (shifting along the panel), dots open the settings. */
	private void sideRow(GuiGraphics g, Panel p, Module m, float x, float y, float w, int mx, int my, float clipTop, float clipBottom,
						 boolean hover, float en, float hv) {
		float h = moduleH(), cy = y + h / 2f;
		boolean expanded = Boolean.TRUE.equals(p.expanded.get(m));
		int fill = theme.accentAt(panelT0 + (panelT1 - panelT0) * (rowCount <= 1 ? 0 : rowIndex / (float) (rowCount - 1))) & 0xFFFFFF;
		if (en > 0.002f && m.isToggleable()) Draw.rect(g, x, y, w * easeOutCubic(en), h, a(fill));
		if (hv > 0) Draw.rect(g, x, y, w, h, a(0xFFFFFF, 0.06f * hv));
		String n = binding == m ? "press a key..." : m.getName();
		boolean filled = en > 0.5f && m.isToggleable();
		Draw.text(g, n, x + 8, cy - 4, a(filled ? on(fill) : hover || expanded ? TEXT : NAME_OFF));
		float dx = x + w - 8;
		boolean dotsHover = hover && mx >= dx - 5 && mx < dx + 5;
		int dc = a(filled ? on(fill) : expanded || dotsHover ? 0xFFFFFF : TEXT2);
		for (int i = -1; i <= 1; i++) Draw.circle(g, dx, cy + i * 3f, 0.9f, dc);
		if (m.getKey() != GLFW.GLFW_KEY_UNKNOWN && binding != m) {
			keyChip(g, keyName(m.getKey()), dx - 8, cy, filled);
		}
		if (hover) {
			hoverModule = m;
			Hit row = clippedHit(x, y, w, h, clipTop, clipBottom);
			row.tooltip = m.getDescription();
			row.click = b -> {
				if (b == 0 && m.isToggleable()) { m.toggle(); click(); }
				else if (b == 0 || b == 1) { p.expanded.put(m, !expanded); click(); }
				else if (b == 2) { binding = m; click(); }
			};
			Hit dots = clippedHit(dx - 6, y, 12, h, clipTop, clipBottom);
			dots.tooltip = "Settings of " + m.getName() + " (middle click: bind a key)";
			dots.click = b -> {
				if (b == 2) binding = m;
				else p.expanded.put(m, !expanded);
				click();
			};
		}
	}

	/** Blocks style row: the name centred (accent coloured while on), a "+" on the right for the settings. */
	private void blockRow(GuiGraphics g, Panel p, Module m, float x, float y, float w, int mx, int my, float clipTop, float clipBottom,
						  boolean hover, float en, float hv) {
		float h = moduleH(), cy = y + h / 2f;
		boolean expanded = Boolean.TRUE.equals(p.expanded.get(m));
		if (hv > 0) Draw.rect(g, x, y, w, h, a(0xFFFFFF, 0.07f * hv));
		String n = binding == m ? "press a key..." : m.getName();
		int on = ColorUtil.lerp(0xFF000000 | TEXT, theme.accentAt(0), m.isToggleable() ? en : 0f) & 0xFFFFFF;
		Draw.text(g, n, x + w / 2f - Draw.width(n) / 2f, cy - 4, a(on));
		if (m.getKey() != GLFW.GLFW_KEY_UNKNOWN && binding != m) {
			String key = keyName(m.getKey());
			Draw.textScaled(g, key, x + 4, cy - 3, 0.75f, a(TEXT3), false);
		}
		float px = x + w - 10;
		boolean plusHover = hover && mx >= px - 5 && mx < px + 6;
		int pc = a(expanded ? theme.accentAt(0) & 0xFFFFFF : plusHover ? 0xFFFFFF : TEXT2);
		Draw.rect(g, px - 3, cy - 0.75f, 7, 1.5f, pc);
		if (!expanded) Draw.rect(g, px - 0.25f, cy - 3.5f, 1.5f, 7, pc);
		if (hover) {
			hoverModule = m;
			Hit row = clippedHit(x, y, w, h, clipTop, clipBottom);
			row.tooltip = m.getDescription();
			row.click = b -> {
				if (b == 0 && m.isToggleable()) { m.toggle(); click(); }
				else if (b == 0 || b == 1) { p.expanded.put(m, !expanded); click(); }
				else if (b == 2) { binding = m; click(); }
			};
			Hit plus = clippedHit(px - 6, y, 13, h, clipTop, clipBottom);
			plus.tooltip = "Settings of " + m.getName() + " (middle click: bind a key)";
			plus.click = b -> {
				if (b == 2) binding = m;
				else p.expanded.put(m, !expanded);
				click();
			};
		}
	}

	/** Cards style row: name on the left; key chip, a gear for the settings and a switch on the right. */
	private void cardRow(GuiGraphics g, Panel p, Module m, float x, float y, float w, int mx, int my, float clipTop, float clipBottom,
						 boolean hover, float en, float hv) {
		float h = moduleH(), rx = x + 4, rw = w - 8, cy = y + h / 2f;
		boolean expanded = Boolean.TRUE.equals(p.expanded.get(m));
		if (hv > 0) Draw.round(g, rx, y + 1, rw, h - 2, 4, a(CARD_ROW, hv));
		Draw.text(g, m.getName(), rx + 6, cy - 4, a(!m.isToggleable() || m.isEnabled() ? TEXT : TEXT2));
		float right = rx + rw - 5;
		if (m.isToggleable()) {
			float tw = 16, th = 8, tx = right - tw, ty = cy - th / 2;
			int trackCol = ColorUtil.lerp(0xFF000000 | TRACK, theme.accentAt(en), en) & 0xFFFFFF;
			Draw.round(g, tx, ty, tw, th, th / 2, a(trackCol));
			Draw.circle(g, tx + th / 2 + en * (tw - th), cy, th / 2 - 1.2f, a(knob(trackCol)));
			right = tx - 5;
		}
		float gx = right - 4;
		boolean gearHover = hover && mx >= gx - 6 && mx < gx + 6;
		gear(g, gx, cy, a(expanded ? theme.accentAt(0) & 0xFFFFFF : gearHover ? TEXT : TEXT3), a(hv > 0.5f ? CARD_ROW : CARD_BG));
		right = gx - 8;
		if (binding == m) {
			String t = "press a key";
			float kw = Draw.width(t) + 6;
			Draw.round(g, right - kw, cy - 5, kw, 10, 3, a(theme.accentAt(0) & 0xFFFFFF, 0.3f));
			Draw.text(g, t, right - kw + 3, cy - 4, a(TEXT));
		} else if (m.getKey() != GLFW.GLFW_KEY_UNKNOWN) {
			String key = keyName(m.getKey());
			float kw = Draw.width(key) + 6;
			Draw.round(g, right - kw, cy - 5, kw, 10, 3, a(0x262626, 0.95f));
			Draw.text(g, key, right - kw + 3, cy - 4, a(TEXT2));
		}
		if (hover) {
			hoverModule = m;
			Hit row = clippedHit(x, y, w, h, clipTop, clipBottom);
			row.tooltip = m.getDescription();
			row.click = b -> {
				if (b == 0 && m.isToggleable()) { m.toggle(); click(); }
				else if (b == 0 || b == 1) { p.expanded.put(m, !expanded); click(); }
				else if (b == 2) { binding = m; click(); }
			};
			Hit gearHit = clippedHit(gx - 6, y, 12, h, clipTop, clipBottom);
			gearHit.tooltip = "Settings of " + m.getName() + " (middle click: bind a key)";
			gearHit.click = b -> {
				if (b == 2) binding = m;
				else p.expanded.put(m, !expanded);
				click();
			};
		}
	}

	/** Only register a hit for the visible part of a row. */
	private Hit clippedHit(float x, float y, float w, float h, float top, float bottom) {
		float y0 = Math.max(y, top), y1 = Math.min(y + h, bottom);
		if (y1 <= y0) return new Hit(0, 0, 0, 0);
		return hit(x, y0, w, y1 - y0);
	}

	private void renderSetting(GuiGraphics g, Panel p, Module m, Setting<?> s, float x, float y, float w, int mx, int my, float top, float bottom) {
		boolean hover = mx >= x && mx < x + w && my >= Math.max(y, top) && my < Math.min(y + settingHeight(p, s), bottom);
		if (s instanceof SectionSetting sec) {
			boolean open = sectionOpen(m, sec);
			String t = s.getName().toUpperCase(Locale.ROOT);
			Draw.textScaled(g, t, x + 2, y + 5, 0.75f, a(hover ? TEXT : theme.accentAt(0.3f) & 0xFFFFFF, 0.95f), false);
			float tw = Draw.width(t) * 0.75f;
			Draw.rect(g, x + tw + 6, y + 8, w - tw - 20, 0.5f, a(TEXT3, 0.5f));
			chevron(g, x + w - 6, y + 8, anim(sec.getName() + "#sec" + System.identityHashCode(m), open ? 1f : 0f, 16f), a(hover ? TEXT : TEXT3));
			Hit h = clippedHit(x, y, w, SECTION_H, top, bottom);
			h.tooltip = open ? "Hide the " + s.getName().toLowerCase(Locale.ROOT) + " options" : "Show the " + s.getName().toLowerCase(Locale.ROOT) + " options";
			h.click = b -> { setSectionOpen(m, sec, !open); click(); };
			return;
		}
		if (s instanceof ChipsSetting cs) {
			renderChips(g, p, cs, x, y, w, mx, my, top, bottom);
			return;
		}
		if (hover && !(s instanceof ColorSetting && Boolean.TRUE.equals(p.pickers.get(s)) && my > y + SETTING_H))
			if (!(s instanceof ChipsSetting))
			Draw.round(g, x, y, w, Math.min(settingHeight(p, s), s instanceof NumberSetting ? SLIDER_H : SETTING_H), 3, a(0x1B1B1B, 0.9f));

		if (s instanceof BoolSetting b) {
			Draw.text(g, s.getName(), x + 4, y + 4, a(TEXT2));
			float on = anim(b, b.isOn() ? 1f : 0f, 16f);
			float tw = 16, th = 8, tx = x + w - tw - 4, ty = y + 4;
			int trackCol = ColorUtil.lerp(0xFF000000 | TRACK, theme.accentAt(on), on) & 0xFFFFFF;
			Draw.round(g, tx, ty, tw, th, th / 2, a(trackCol));
			Draw.circle(g, tx + th / 2 + on * (tw - th), ty + th / 2, th / 2 - 1.2f, a(knob(trackCol)));
			Hit h = clippedHit(x, y, w, SETTING_H, top, bottom);
			h.tooltip = s.getDescription();
			h.click = btn -> { if (btn == 0 || btn == 1) { b.toggle(); click(); } };
		} else if (s instanceof NumberSetting n) {
			boolean active = dragging != null && dragging.owner == n;
			Draw.text(g, s.getName(), x + 4, y + 3.5f, a(active ? TEXT : TEXT2));
			// the value in a small pill (light while you drag)
			String v = n.display();
			float vw = Draw.width(v) * SEG_S + 8, vx = x + w - 4 - vw;
			Draw.round(g, vx, y + 2.5f, vw, 10, 2.5f, a(active ? 0xEDEDED : 0x1E1E1E));
			Draw.textScaled(g, v, vx + 4, y + 3.9f, SEG_S, a(active ? 0x111111 : TEXT), false);
			float tx = x + 4, tw = w - 8, ty = y + 16, th = 2.5f;
			float f = anim(n, (float) n.fraction(), 22f);
			Draw.round(g, tx, ty, tw, th, th / 2, a(0x262626));
			if (f > 0) Draw.roundGradient(g, tx, ty, Math.max(th, tw * f), th, th / 2, a(theme.accentAt(0) & 0xFFFFFF), a(theme.accentAt(f) & 0xFFFFFF));
			float kr = active ? 4f : hover ? 3.6f : 3.2f;
			float kx = tx + tw * f, ky = ty + th / 2;
			Draw.circle(g, kx, ky, kr + 0.9f, a(0x000000, 0.45f));
			Draw.circle(g, kx, ky, kr, a(KNOB));
			Hit h = clippedHit(x, y, w, SLIDER_H, top, bottom);
			h.owner = n;
			h.tooltip = s.getDescription() + "  (Shift + wheel: fine steps)";
			h.drag = (mxx, myy) -> n.setFraction((mxx - tx) / tw);
			h.scroll = d -> n.set(n.get() + Math.signum(d) * n.getStep());
		} else if (s instanceof ModeSetting md) {
			Draw.text(g, s.getName(), x + 4, y + 4, a(TEXT2));
			int layout = modeLayout(md, w);
			Hit h = clippedHit(x, y, w, settingHeight(p, s), top, bottom);
			h.tooltip = s.getDescription() + "  (" + String.join(" / ", md.getModes()) + ")";
			h.click = btn -> { md.cycle(btn == 1 ? -1 : 1); click(); };
			h.scroll = d -> md.cycle(d > 0 ? -1 : 1);
			if (layout == 0) {
				float sw = segWidth(md);
				segmented(g, md, x + w - 4 - sw, y + 2.5f, sw, top, bottom);
			} else if (layout == 1) {
				segmented(g, md, x + 4, y + SETTING_H - 1, w - 8, top, bottom);
			} else {
				// too many choices for segments: one pill with the choice and thin arrows
				String v = md.get();
				float vw = Draw.width(v) * SEG_S;
				float bw = vw + 20, bx = x + w - 4 - bw;
				Draw.round(g, bx, y + 2.5f, bw, 11, 3, a(hover ? 0x262626 : 0x1C1C1C));
				chevronSide(g, bx + 5, y + 8, 1, a(TEXT3));
				Draw.textScaled(g, v, bx + 10, y + 4.4f, SEG_S, a(TEXT), false);
				chevronSide(g, bx + bw - 5, y + 8, -1, a(TEXT3));
				String pos = (md.index() + 1) + "/" + md.getModes().size();
				if (Draw.width(s.getName()) + Draw.width(pos) * 0.7f + bw + 16 <= w)
					Draw.textScaled(g, pos, bx - 4 - Draw.width(pos) * 0.7f, y + 6, 0.7f, a(TEXT3), false);
			}
		} else if (s instanceof TextSetting ts) {
			Draw.text(g, s.getName(), x + 4, y + 4, a(TEXT2));
			boolean active = editing == ts;
			float bx = x + 3, by = y + SETTING_H - 2, bw = w - 6, bh = 12;
			Draw.round(g, bx, by, bw, bh, 3, a(active ? (theme.accentAt(0) & 0xFFFFFF) : 0x303030, active ? 0.9f : 0.8f));
			Draw.round(g, bx + 0.75f, by + 0.75f, bw - 1.5f, bh - 1.5f, 2.5f, a(0x121212, 0.97f));
			String v = ts.get();
			boolean caret = active && (System.currentTimeMillis() / 500) % 2 == 0;
			String shown;
			int col;
			if (v.isEmpty() && !active) { shown = ts.getPlaceholder(); col = TEXT3; }
			else {
				shown = v;
				// while typing keep the end in view, otherwise cut the end off
				if (active) {
					while (!shown.isEmpty() && Draw.width(shown + "_") > bw - 8) shown = shown.substring(1);
					shown += caret ? "_" : "";
				} else if (Draw.width(shown) > bw - 8) {
					while (!shown.isEmpty() && Draw.width(shown + "...") > bw - 8) shown = shown.substring(0, shown.length() - 1);
					shown += "...";
				}
				col = TEXT;
			}
			Draw.text(g, shown, bx + 4, by + 2.5f, a(col));
			Hit h = clippedHit(x, y, w, SETTING_H + 14, top, bottom);
			h.tooltip = s.getDescription() + "  (click to type, Enter to save, right click to clear)";
			h.click = btn -> {
				if (btn == 1) { ts.set(""); editing = null; }
				else editing = ts;
				click();
			};
		} else if (s instanceof ColorSetting c) {
			boolean opened = Boolean.TRUE.equals(p.pickers.get(c));
			Draw.text(g, s.getName(), x + 4, y + 4, a(opened ? TEXT : TEXT2));
			int rgb = c.rgb();
			String hex = c.isRainbow() ? "Rainbow" : c.hex();
			// the code only when it fits next to the name (narrow panels show the swatch alone)
			if (Draw.width(s.getName()) + Draw.width(hex) * SEG_S + 32 <= w)
				Draw.textScaled(g, hex, x + w - 21 - Draw.width(hex) * SEG_S, y + 4.5f, SEG_S, a(TEXT3), false);
			Draw.round(g, x + w - 17, y + 2.5f, 13, 11, 3, a(opened ? 0xEDEDED : 0x3A3A3A));
			Draw.round(g, x + w - 16, y + 3.5f, 11, 9, 2.4f, a(0x111111));
			Draw.round(g, x + w - 16, y + 3.5f, 11, 9, 2.4f, a(rgb, Math.max(0.15f, c.alpha() / 255f)));
			Hit h = clippedHit(x, y, w, SETTING_H, top, bottom);
			h.tooltip = s.getDescription() + "  (click to edit)";
			h.click = btn -> { p.pickers.put(c, !opened); click(); };
			float pick = anim(c, opened ? 1f : 0f, 14f);
			if (pick > 0.01f) renderPicker(g, c, x, y + SETTING_H, w, pickerH(c) * pick, top, bottom);
		}
	}

	/** A chip group: one chip per target (light when shown), its colour dot, and the colour picker under the chips. */
	private void renderChips(GuiGraphics g, Panel p, ChipsSetting cs, float x, float y, float w, int mx, int my, float top, float bottom) {
		List<ChipsSetting.Chip> chips = cs.chips();
		float[][] lay = chipLayout(cs, w);
		boolean pickerOpen = p.chipOpen.contains(cs);
		ColorSetting last = p.chipColor.get(cs);
		for (int i = 0; i < chips.size(); i++) {
			ChipsSetting.Chip c = chips.get(i);
			float cx = x + lay[i][0], cy = y + 3 + lay[i][1], cw = lay[i][2];
			boolean on = c.on().isOn();
			boolean hover = mx >= cx && mx < cx + cw && my >= Math.max(cy, top) && my < Math.min(cy + CHIP_H, bottom);
			boolean editing = pickerOpen && last == c.color();
			float en = anim(c.on(), on ? 1f : 0f, 16f);
			int fill = ColorUtil.lerp(0xFF000000 | (hover ? 0x222222 : 0x171717), 0xFFEDEDED, en) & 0xFFFFFF;
			int edge = editing ? 0xFFFFFF : ColorUtil.lerp(0xFF000000 | (hover ? 0x3A3A3A : 0x2C2C2C), 0xFFEDEDED, en) & 0xFFFFFF;
			Draw.round(g, cx, cy, cw, CHIP_H, 3, a(edge));
			Draw.round(g, cx + (editing ? 1 : 0.6f), cy + (editing ? 1 : 0.6f), cw - (editing ? 2 : 1.2f), CHIP_H - (editing ? 2 : 1.2f), 2.5f, a(fill));
			// the colour dot (several colours when the targets use their own, like dyed shulkers)
			float dx = cx + 7, dy = cy + CHIP_H / 2f;
			Draw.circle(g, dx, dy, 3f, a(on ? 0x111111 : 0x0A0A0A, 0.8f));
			if (c.color() == null) Draw.circle(g, dx, dy, 2.2f, a(TEXT2, on ? 1f : 0.45f));
			else if (c.colorActive()) Draw.circle(g, dx, dy, 2.2f, a(c.color().rgb(), on ? 1f : 0.45f));
			else for (int q = 0; q < 4; q++) {
				float ang = (float) (q * Math.PI / 2 + Math.PI / 4);
				Draw.circle(g, dx + (float) Math.cos(ang) * 1.1f, dy + (float) Math.sin(ang) * 1.1f, 1.2f,
					a(ColorUtil.hsbToRgb(q / 4f, 0.7f, 1f), on ? 1f : 0.45f));
			}
			int tc = ColorUtil.lerp(0xFF000000 | (hover ? TEXT : TEXT2), 0xFF111111, en) & 0xFFFFFF;
			String label = c.label();
			float room = cw - 19.5f;
			if (Draw.width(label) * SEG_S > room) {
				while (!label.isEmpty() && Draw.width(label + "...") * SEG_S > room) label = label.substring(0, label.length() - 1);
				label += "...";
			}
			Draw.textScaled(g, label, cx + 13, cy + 2.9f, SEG_S, a(tc), false);
			Hit h = clippedHit(cx, cy, cw, CHIP_H, top, bottom);
			h.tooltip = c.on().getDescription() + (c.colorActive() ? "  (right click: colour)" : "");
			h.click = b -> {
				if (b == 0) c.on().toggle();
				else if (b == 1 && c.colorActive()) {
					if (pickerOpen && last == c.color()) p.chipOpen.remove(cs);
					else { p.chipColor.put(cs, c.color()); p.chipOpen.add(cs); }
				}
				click();
			};
		}
		float pick = anim(cs, pickerOpen ? 1f : 0f, 14f);
		if (pick > 0.01f && last != null) {
			float py = y + 3 + chipRowsHeight(cs, w) + 4;
			Draw.textScaled(g, last.getName(), x + 4, py + 2, SEG_S, a(TEXT2), false);
			Hit close = clippedHit(x + w - 14, py, 14, 11, top, bottom);
			close.tooltip = "Close the colour";
			close.click = b -> { p.chipOpen.remove(cs); click(); };
			boolean ch = mx >= x + w - 14 && mx < x + w && my >= py && my < py + 11;
			Draw.rect(g, x + w - 9.5f, py + 5, 5, 0.8f, a(ch ? TEXT : TEXT3));
			renderPicker(g, last, x, py + 9, w, pickerH(last) * pick, top, bottom);
		}
	}

	/** A thin < or > drawn with two short lines (the font's angle quotes look heavy in Inter). */
	private void chevronSide(GuiGraphics g, float cx, float cy, int dir, int col) {
		for (int i = 0; i < 4; i++) {
			float t = i / 3f;
			Draw.rect(g, cx + dir * (1.5f - t * 1.5f) - 0.4f, cy - 2 + t * 2 - 0.4f, 0.9f, 0.9f, col);
			Draw.rect(g, cx + dir * (1.5f - t * 1.5f) - 0.4f, cy + 2 - t * 2 - 0.4f, 0.9f, 0.9f, col);
		}
	}

	private void renderPicker(GuiGraphics g, ColorSetting c, float x, float y, float w, float h, float top, float bottom) {
		float pad = 4, sx = x + pad, sw = w - pad * 2, limit = y + h + 0.5f;
		float sy = y + 3, svH = 40;
		// saturation (left to right) / brightness (top to bottom): columns of vertical gradients
		if (sy + svH <= limit) {
			Draw.svSquare(g, sx, sy, sw, svH, c.hue(), Math.round(255 * globalAlpha));
			int edge = a(0x000000, 0.55f);
			Draw.rect(g, sx, sy, sw, 0.5f, edge);
			Draw.rect(g, sx, sy + svH - 0.5f, sw, 0.5f, edge);
			Draw.rect(g, sx, sy, 0.5f, svH, edge);
			Draw.rect(g, sx + sw - 0.5f, sy, 0.5f, svH, edge);
			float px = sx + sw * c.saturation(), py = sy + svH * (1 - c.brightness());
			Draw.circle(g, px, py, 4f, a(0x000000, 0.4f));
			Draw.circle(g, px, py, 3.4f, a(0xFFFFFF));
			Draw.circle(g, px, py, 2.4f, a(ColorUtil.hsbToRgb(c.hue(), c.saturation(), c.brightness())));
			Hit sv = clippedHit(sx, sy, sw, svH, top, bottom);
			sv.tooltip = "Saturation and brightness";
			sv.drag = (mx, my) -> { c.setRainbow(false); c.setHsb(c.hue(), clamp01((mx - sx) / sw), 1 - clamp01((my - sy) / svH)); };
		}
		// hue bar, rounded (both ends are red, so the caps are plain red circles)
		float by = sy + svH + 5, bh = 6, r = bh / 2;
		if (by + bh <= limit) {
			Draw.circle(g, sx + r, by + r, r, a(0xFF0000));
			Draw.circle(g, sx + sw - r, by + r, r, a(0xFF0000));
			int seg = 12;
			float hx0 = sx + r, hw = sw - 2 * r;
			for (int i = 0; i < seg; i++)
				Draw.hGradient(g, hx0 + hw * i / seg, by, hw / seg + 0.3f, bh, a(ColorUtil.hsbToRgb(i / (float) seg, 1, 1)), a(ColorUtil.hsbToRgb((i + 1) / (float) seg, 1, 1)));
			float kx = sx + r + hw * c.hue();
			Draw.circle(g, kx, by + r, 4.2f, a(0x000000, 0.4f));
			Draw.circle(g, kx, by + r, 3.6f, a(0xFFFFFF));
			Draw.circle(g, kx, by + r, 2.6f, a(ColorUtil.hsbToRgb(c.hue(), 1, 1)));
			Hit hue = clippedHit(sx, by - 2, sw, bh + 4, top, bottom);
			hue.tooltip = "Hue";
			hue.drag = (mx, my) -> { c.setRainbow(false); c.setHsb(clamp01((mx - hx0) / hw) * 0.9999f, c.saturation(), c.brightness()); };
		}
		by += bh + 5;
		// opacity bar over a checkerboard
		if (c.hasAlpha()) {
			if (by + bh <= limit) {
				float hx0 = sx + r, hw = sw - 2 * r;
				Draw.round(g, sx, by, sw, bh, r, a(0x3A3A3A));
				for (float cx = sx + r; cx < sx + sw - r; cx += 3)
					for (int row = 0; row < 2; row++)
						if (((int) ((cx - sx) / 3) + row) % 2 == 0) Draw.rect(g, cx, by + row * 3, Math.min(3, sx + sw - r - cx), 3, a(0x1E1E1E));
				int rgb = ColorUtil.hsbToRgb(c.hue(), c.saturation(), c.brightness());
				Draw.hGradient(g, hx0, by, hw, bh, a(rgb, 0f), a(rgb));
				Draw.circle(g, sx + sw - r, by + r, r, a(rgb));
				float kx = hx0 + hw * c.alpha() / 255f;
				Draw.circle(g, kx, by + r, 4.2f, a(0x000000, 0.4f));
				Draw.circle(g, kx, by + r, 3.6f, a(0xFFFFFF));
				Draw.circle(g, kx, by + r, 2.6f, a(rgb, Math.max(0.2f, c.alpha() / 255f)));
				Hit al = clippedHit(sx, by - 2, sw, bh + 4, top, bottom);
				al.tooltip = "Opacity";
				al.drag = (mx, my) -> c.setAlpha(Math.round(clamp01((mx - hx0) / hw) * 255));
			}
			by += bh + 5;
		}
		// quick colours, then a rainbow swatch
		int n = SWATCHES.length + 1;
		float gap = 2, size = Math.min(9, (sw - gap * (n - 1)) / n);
		if (by + size <= limit) {
			for (int i = 0; i < n; i++) {
				float qx = sx + i * (size + gap);
				boolean rainbow = i == SWATCHES.length;
				boolean chosen = rainbow ? c.isRainbow() : !c.isRainbow() && (c.rgb() & 0xFFFFFF) == SWATCHES[i];
				if (chosen) Draw.round(g, qx - 1, by - 1, size + 2, size + 2, 3, a(0xFFFFFF));
				Draw.round(g, qx, by, size, size, 2.2f, a(0x111111));
				if (rainbow) {
					float hue0 = (System.currentTimeMillis() % 4000L) / 4000f;
					for (int k = 0; k < 6; k++)
						Draw.rect(g, qx + 1 + (size - 2) * k / 6f, by + 1, (size - 2) / 6f + 0.2f, size - 2, a(ColorUtil.hsbToRgb((hue0 + k / 6f) % 1f, 0.8f, 1f)));
				} else Draw.round(g, qx + 0.6f, by + 0.6f, size - 1.2f, size - 1.2f, 1.8f, a(SWATCHES[i]));
				Hit q = clippedHit(qx, by, size, size, top, bottom);
				int pick = rainbow ? -1 : SWATCHES[i];
				q.tooltip = rainbow ? "Rainbow: cycle through all colours" : String.format("#%06X", pick);
				q.click = b -> {
					if (pick < 0) c.setRainbow(!c.isRainbow());
					else { c.setRainbow(false); c.setArgb((c.alpha() << 24) | pick); }
					click();
				};
			}
		}
		by += size + 5;
		// the hex code: click copies it, right click pastes one
		if (by + 9 <= limit) {
			String hex = c.hex();
			Draw.textScaled(g, hex, sx, by + 1, SEG_S, a(TEXT2), false);
			if (c.hasAlpha()) {
				String al = Math.round(c.alpha() / 2.55f) + "%";
				Draw.textScaled(g, al, sx + sw - Draw.width(al) * SEG_S, by + 1, SEG_S, a(TEXT2), false);
			}
			Hit hx = clippedHit(sx, by, sw - 24, 9, top, bottom);
			hx.tooltip = "Click: copy " + hex + "  \u2022  Right click: paste a #RRGGBB code";
			hx.click = b -> {
				if (b == 0) { minecraft.keyboardHandler.setClipboard(hex); toast("Copied " + hex); }
				else if (b == 1) {
					String t = minecraft.keyboardHandler.getClipboard().trim();
					if (t.startsWith("#")) t = t.substring(1);
					if (t.matches("[0-9a-fA-F]{6}")) {
						c.setRainbow(false);
						c.setArgb((c.alpha() << 24) | Integer.parseInt(t, 16));
						toast("Pasted #" + t.toUpperCase(Locale.ROOT));
					} else toast("The clipboard has no #RRGGBB code");
				}
				click();
			};
		}
	}

	private static float clamp01(double v) { return (float) Math.max(0, Math.min(1, v)); }

	private void renderBindRow(GuiGraphics g, Module m, float x, float y, float w, int mx, int my, float top, float bottom) {
		Draw.text(g, "Keybind", x + 4, y + 4, a(TEXT2));
		boolean waiting = binding == m;
		String v = waiting ? "press a key" : m.getKey() == GLFW.GLFW_KEY_UNKNOWN ? "None" : keyName(m.getKey());
		float vw = Draw.width(v) * SEG_S + 8, vx = x + w - 4 - vw;
		Draw.round(g, vx, y + 2.5f, vw, 10.5f, 2.5f, a(waiting ? 0xEDEDED : 0x3A3A3A));
		Draw.round(g, vx + 0.6f, y + 3.1f, vw - 1.2f, 9.3f, 2f, a(waiting ? 0xEDEDED : 0x141414));
		Draw.textScaled(g, v, vx + 4, y + 4.2f, SEG_S, a(waiting ? 0x111111 : m.getKey() == GLFW.GLFW_KEY_UNKNOWN ? TEXT3 : TEXT), false);
		Hit h = clippedHit(x, y, w, SETTING_H, top, bottom);
		h.tooltip = "Key that toggles " + m.getName() + ". Click, then press a key (Delete removes it). Right click clears it.";
		h.click = b -> { binding = b == 1 ? null : m; if (b == 1) m.setKey(GLFW.GLFW_KEY_UNKNOWN); click(); };
	}

	private void renderResetRow(GuiGraphics g, Module m, float x, float y, float w, int mx, int my, float top, float bottom) {
		String t = "Reset to defaults";
		boolean hover = mx >= x && mx < x + w && my >= y && my < y + SETTING_H;
		float tw = Draw.width(t) * SEG_S;
		Draw.textScaled(g, t, x + w - 4 - tw, y + 5, SEG_S, a(hover ? TEXT : TEXT3), false);
		if (hover) Draw.rect(g, x + w - 4 - tw, y + 12, tw, 0.5f, a(TEXT, 0.6f));
		Hit h = clippedHit(x, y, w, SETTING_H, top, bottom);
		h.tooltip = "Reset every option of " + m.getName();
		h.click = b -> { for (Setting<?> s : m.getSettings()) s.reset(); click(); toast("Reset " + m.getName()); };
	}

	public static String keyName(int key) {
		String n = InputConstants.Type.KEYSYM.getOrCreate(key).getDisplayName().getString();
		return n.length() > 10 ? n.substring(0, 10) : n;
	}

	// ------------------------------------------------------------------ input

	private Hit topHit(double mx, double my, boolean wantScroll) {
		for (int i = hits.size() - 1; i >= 0; i--) {
			Hit h = hits.get(i);
			if (!h.contains(mx, my)) continue;
			if (wantScroll ? h.scroll != null : (h.click != null || h.drag != null)) return h;
		}
		return null;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
		if (closingAt >= 0) return true;
		if (binding != null) {
			binding = null;   // clicking anywhere cancels binding
			return true;
		}
		editing = null;   // clicking anywhere else finishes typing (the box itself turns it back on)
		// the panel you work in comes to the front, so a long open panel is not covered by the one below it
		for (int i = panels.size() - 1; i >= 0; i--) {
			Panel q = panels.get(i);
			float[] b = q.bounds;
			if (q.category == null || b == null || (sidebar() && !q.shown)) continue;
			if (e.x() >= b[0] && e.x() < b[0] + b[2] && e.y() >= b[1] && e.y() < b[1] + b[3]) {
				if (i != panels.size() - 1) { panels.remove(i); panels.add(q); }
				break;
			}
		}
		Hit h = topHit(e.x(), e.y(), false);
		if (h == null) return super.mouseClicked(e, doubleClick);
		if (h.drag != null && e.button() == 0) {
			dragging = h;
			h.drag.accept(e.x(), e.y());
			return true;
		}
		if (h.click != null) {
			h.click.accept(e.button());
			if (movingPanel != null) { moveDX = (float) e.x() - movingPanel.x; moveDY = (float) e.y() - movingPanel.y; }
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
		if (movingPanel != null) {
			movingPanel.x = Math.max(0, Math.min((float) e.x() - moveDX, width - 30));
			movingPanel.y = Math.max(0, Math.min((float) e.y() - moveDY, height - headerH()));
			return true;
		}
		if (dragging != null && dragging.drag != null) {
			dragging.drag.accept(e.x(), e.y());
			return true;
		}
		return super.mouseDragged(e, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent e) {
		movingPanel = null;
		dragging = null;
		return super.mouseReleased(e);
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double sx, double sy) {
		// Shift + wheel adjusts the slider / mode under the pointer; the plain wheel scrolls the panel
		if (minecraft.hasShiftDown()) {
			for (int i = hits.size() - 1; i >= 0; i--) {
				Hit h = hits.get(i);
				if (h.scroll != null && (h.click != null || h.drag != null) && h.contains(mx, my)) { h.scroll.accept(sy); return true; }
			}
		}
		for (Hit h : hits) {
			if (h.scroll != null && h.click == null && h.drag == null && h.contains(mx, my)) { h.scroll.accept(sy); return true; }
		}
		return super.mouseScrolled(mx, my, sx, sy);
	}

	@Override
	public boolean keyPressed(KeyEvent e) {
		int key = e.key();
		if (editing != null) {
			String v = editing.get();
			if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER || key == GLFW.GLFW_KEY_ESCAPE) editing = null;
			else if (key == GLFW.GLFW_KEY_BACKSPACE && !v.isEmpty()) editing.set(minecraft.hasControlDown() ? "" : v.substring(0, v.length() - 1));
			else if (key == GLFW.GLFW_KEY_V && minecraft.hasControlDown()) editing.set((v + minecraft.keyboardHandler.getClipboard()).replaceAll("[\r\n]+", ",").trim());
			return true;   // every other key belongs to the text box, so the GUI key does not close the menu
		}
		if (binding != null) {
			if (key == GLFW.GLFW_KEY_ESCAPE) binding = null;
			else if (key == GLFW.GLFW_KEY_DELETE || key == GLFW.GLFW_KEY_BACKSPACE) { binding.setKey(GLFW.GLFW_KEY_UNKNOWN); binding = null; }
			else { binding.setKey(key); binding = null; }
			click();
			return true;
		}
		if (key == GLFW.GLFW_KEY_ESCAPE && !search.isEmpty()) { search = ""; return true; }
		if (key == GLFW.GLFW_KEY_BACKSPACE && !search.isEmpty()) { search = search.substring(0, search.length() - 1); return true; }
		if (PrismClient.guiKey().matches(e) && search.isEmpty()) { onClose(); return true; }
		return super.keyPressed(e);
	}

	@Override
	public boolean charTyped(CharacterEvent e) {
		if (binding != null) return true;
		int cp = e.codepoint();
		if (editing != null) {
			if (cp >= 32 && editing.get().length() < 400) editing.set(editing.get() + new String(Character.toChars(cp)));
			return true;
		}
		if (Character.isLetterOrDigit(cp) || cp == ' ') {
			if (search.length() < 24) search += new String(Character.toChars(cp));
			return true;
		}
		return super.charTyped(e);
	}

	// ------------------------------------------------------------------ for tests

	/** Expands a module's settings in its panel (used by the automated screenshots). */
	public void expandForTest(Module m, ColorSetting openPicker) {
		for (Panel p : panels) {
			if (p.category == m.getCategory()) {
				p.expanded.put(m, true);
				anims.put(m, 1f);
				if (openPicker != null) { p.pickers.put(openPicker, true); anims.put(openPicker, 1f); }
			}
		}
	}

	public void setSearchForTest(String s) { search = s; }

	public void editForTest(TextSetting s) { editing = s; }
}
