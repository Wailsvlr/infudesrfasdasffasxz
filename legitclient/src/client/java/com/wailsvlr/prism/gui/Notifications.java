package com.wailsvlr.prism.gui;

import com.wailsvlr.prism.PrismClient;
import com.wailsvlr.prism.module.Module;
import com.wailsvlr.prism.modules.ClickGuiModule;
import com.wailsvlr.prism.modules.HudModule;
import com.wailsvlr.prism.render.Draw;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;

/**
 * Small cards in the bottom-right corner: a module switched on or off (by key or in the menu), or a message from a
 * module (a finder spotted something, the printer finished). They slide in, count down with a thin bar and slide out.
 * Loading the config or a profile is {@link #quiet} so it does not flood the corner.
 */
public final class Notifications {
	private static final long TOGGLE_LIFE = 2200, MESSAGE_LIFE = 4000, SLIDE = 220;
	private static final int MAX = 5;
	/** Set while settings are being loaded: those toggles are not news. */
	public static volatile boolean quiet;

	/** {@code on} is null for a message card (no switch drawn). */
	private record Card(Object key, String icon, String title, String text, Boolean on, long born, long life) { }

	private static final List<Card> CARDS = new ArrayList<>();

	private Notifications() { }

	/** Called from {@link Module#setEnabled}. */
	public static void toggled(Module m) {
		Minecraft mc = Minecraft.getInstance();
		if (quiet || mc == null || mc.player == null || !m.isToggleable()) return;
		HudModule hud = PrismClient.modules().get(HudModule.class);
		if (hud == null || m == hud || !hud.notifications.isOn()) return;
		add(new Card(m, ClickGuiScreen.catIcon(m.getCategory()), m.getName(), m.isEnabled() ? "Enabled" : "Disabled", m.isEnabled(),
			System.currentTimeMillis(), TOGGLE_LIFE));
	}

	/**
	 * A message card. {@code key} replaces an older card with the same key (so a repeated message does not stack);
	 * {@code icon} is one of the GUI icon names (textures/gui/icons).
	 */
	public static void post(Object key, String icon, String title, String text) {
		Minecraft mc = Minecraft.getInstance();
		if (quiet || mc == null || mc.player == null) return;
		add(new Card(key, icon, title, text, null, System.currentTimeMillis(), MESSAGE_LIFE));
	}

	private static void add(Card c) {
		synchronized (CARDS) {
			CARDS.removeIf(o -> o.key.equals(c.key));
			CARDS.add(c);
			while (CARDS.size() > MAX) CARDS.remove(0);
		}
	}

	/** The HUD element (hidden under the menu, which draws them itself on top of its blur). */
	public static void render(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.options.hideGui || mc.screen instanceof ClickGuiScreen) return;
		draw(g);
	}

	static void draw(GuiGraphics g) {
		long now = System.currentTimeMillis();
		List<Card> live;
		synchronized (CARDS) {
			CARDS.removeIf(c -> now - c.born > c.life);
			if (CARDS.isEmpty()) return;
			live = new ArrayList<>(CARDS);
		}
		ClickGuiModule theme = PrismClient.modules().get(ClickGuiModule.class);
		Draw.smooth = theme.font.is("Inter");
		int sw = g.guiWidth(), sh = g.guiHeight();
		float ch = 26, gap = 4;
		float y = sh - 8;
		for (int i = live.size() - 1; i >= 0; i--) {
			Card c = live.get(i);
			long age = now - c.born;
			float in = Math.min(1, age / (float) SLIDE), out = Math.min(1, Math.max(0, (c.life - age) / (float) SLIDE));
			float k = easeOut(Math.min(in, out));
			// toggles keep one width; messages grow to fit (up to half the screen)
			float textW = Math.max(Draw.width(c.title), Draw.width(c.text));
			float cw = c.on != null ? 132 : Math.min(sw / 2f, Math.max(132, textW + 32));
			y -= (ch + gap) * k;
			float x = sw - 8 - cw + (1 - k) * (cw + 12);
			int alpha = Math.round(235 * k);
			Draw.round(g, x, y, cw, ch, 4, alpha << 24 | 0x111111);
			if (c.on != null) {
				// the switch on the right
				float tx = x + cw - 26, ty = y + ch / 2f - 4.5f;
				Draw.round(g, tx, ty, 18, 9, 4.5f, (Math.round(255 * k) << 24) | (c.on ? 0xEDEDED : 0x2E2E2E));
				Draw.circle(g, c.on ? tx + 13.5f : tx + 4.5f, ty + 4.5f, 3.4f, (Math.round(255 * k) << 24) | (c.on ? 0x111111 : 0x8A8A8A));
			}
			int ta = Math.max(4, Math.round(255 * k)) << 24;
			Draw.icon(g, c.icon, x + 7, y + 8.5f, 9, ta | 0xBDBDBD);
			String text = c.text;
			float room = cw - 29;
			if (Draw.width(text) > room) {
				while (!text.isEmpty() && Draw.width(text + "...") > room) text = text.substring(0, text.length() - 1);
				text += "...";
			}
			Draw.text(g, c.title, x + 21, y + 4, ta | 0xF2F2F2);
			Draw.text(g, text, x + 21, y + 14, ta | (c.on == null || c.on ? 0xA8A8A8 : 0x6E6E6E));
			// time left
			float left = 1 - age / (float) c.life;
			Draw.rect(g, x + 4, y + ch - 1.5f, (cw - 8) * left, 1, (Math.round(120 * k) << 24) | 0xFFFFFF);
		}
	}

	/** For the tests: cards on screen right now. */
	public static int count() {
		long now = System.currentTimeMillis();
		synchronized (CARDS) { CARDS.removeIf(c -> now - c.born > c.life); return CARDS.size(); }
	}

	/** For the tests: the text of every card on screen ("title: text"). */
	public static List<String> texts() {
		synchronized (CARDS) { return CARDS.stream().map(c -> c.title + ": " + c.text).toList(); }
	}

	private static float easeOut(float t) { float u = 1 - t; return 1 - u * u * u; }
}
