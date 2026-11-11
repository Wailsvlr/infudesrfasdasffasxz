package com.wailsvlr.prism.gui;

import com.wailsvlr.prism.PrismClient;
import com.wailsvlr.prism.render.Draw;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.Map;

/**
 * Edit HUD: every HUD element (module list, finder counters, info box, watermark) gets a frame; drag a frame to move
 * the element, right click to put it back in its default place. Places are saved in the config and kept as fractions
 * of the screen, anchored to the nearer side, so they stay put when the window size changes.
 */
public class HudEditorScreen extends Screen {
	private final Screen parent;
	private String dragging;
	private float grabX, grabY;

	public HudEditorScreen(Screen parent) {
		super(Component.literal("Edit HUD"));
		this.parent = parent;
	}

	@Override
	public boolean isPauseScreen() { return false; }

	@Override
	public void renderBackground(GuiGraphics g, int mx, int my, float partial) {
		g.fill(0, 0, width, height, 0x40000000);
	}

	@Override
	public void render(GuiGraphics g, int mx, int my, float partial) {
		super.render(g, mx, my, partial);
		String hint = "Drag a box to move it  \u2022  Right click: back to its place  \u2022  Esc: done";
		Draw.round(g, width / 2f - Draw.width(hint) / 2f - 8, height - 26, Draw.width(hint) + 16, 15, 3, 0xF0141414);
		Draw.text(g, hint, width / 2f - Draw.width(hint) / 2f, height - 22, 0xFFF0F0F0);
		for (Map.Entry<String, float[]> e : PrismHud.BOUNDS.entrySet()) {
			float[] b = e.getValue();
			boolean hover = mx >= b[0] - 2 && mx < b[0] + b[2] + 2 && my >= b[1] - 2 && my < b[1] + b[3] + 2;
			int col = e.getKey().equals(dragging) ? 0xFFFFFFFF : hover ? 0xD0FFFFFF : 0x80FFFFFF;
			Draw.rect(g, b[0] - 2, b[1] - 2, b[2] + 4, 1, col);
			Draw.rect(g, b[0] - 2, b[1] + b[3] + 1, b[2] + 4, 1, col);
			Draw.rect(g, b[0] - 2, b[1] - 2, 1, b[3] + 4, col);
			Draw.rect(g, b[0] + b[2] + 1, b[1] - 2, 1, b[3] + 4, col);
			if (hover || e.getKey().equals(dragging)) Draw.rect(g, b[0] - 2, b[1] - 2, b[2] + 4, b[3] + 4, 0x18FFFFFF);
			String name = switch (e.getKey()) {
				case "list" -> "Module list";
				case "stats" -> "Finder counters";
				case "info" -> "Info";
				case "watermark" -> "Watermark";
				default -> e.getKey();
			};
			float ty = b[1] - 12 < 2 ? b[1] + b[3] + 4 : b[1] - 12;
			Draw.textShadow(g, name, b[0], ty, 0xFFFFFFFF);
		}
	}

	private String at(double mx, double my) {
		String found = null;
		for (Map.Entry<String, float[]> e : PrismHud.BOUNDS.entrySet()) {
			float[] b = e.getValue();
			if (mx >= b[0] - 2 && mx < b[0] + b[2] + 2 && my >= b[1] - 2 && my < b[1] + b[3] + 2) found = e.getKey();
		}
		return found;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
		String id = at(e.x(), e.y());
		if (id == null) return super.mouseClicked(e, doubleClick);
		if (e.button() == 1) {
			PrismHud.resetPlace(id);
			PrismClient.config().save();
			return true;
		}
		float[] b = PrismHud.BOUNDS.get(id);
		dragging = id;
		grabX = (float) e.x() - b[0];
		grabY = (float) e.y() - b[1];
		return true;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
		if (dragging == null) return super.mouseDragged(e, dx, dy);
		float[] b = PrismHud.BOUNDS.get(dragging);
		if (b == null) return true;
		float left = Math.max(0, Math.min((float) e.x() - grabX, width - b[2]));
		float top = Math.max(0, Math.min((float) e.y() - grabY, height - b[3]));
		PrismHud.setPlace(dragging, left, top, b[2], b[3], width, height);
		return true;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent e) {
		if (dragging != null) PrismClient.config().save();
		dragging = null;
		return super.mouseReleased(e);
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}
}
