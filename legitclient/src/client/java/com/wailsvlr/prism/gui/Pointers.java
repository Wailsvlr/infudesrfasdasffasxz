package com.wailsvlr.prism.gui;

import com.wailsvlr.prism.PrismClient;
import com.wailsvlr.prism.module.Module;
import com.wailsvlr.prism.modules.ClickGuiModule;
import com.wailsvlr.prism.modules.DeathPoint;
import com.wailsvlr.prism.modules.FinderModule;
import com.wailsvlr.prism.render.Draw;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Arrows on a ring around the crosshair, each pointing (left/right/behind, ignoring height) at a module's nearest
 * target, with its distance: the finders' "Pointer" option and DeathPoint.
 */
public final class Pointers {
	/** Where to point, what to call it and in which colour. */
	public record Target(Vec3 pos, String name, int rgb) { }

	private static final float RADIUS = 32;

	private Pointers() { }

	/** Everything that wants an arrow right now. */
	public static List<Target> targets() {
		List<Target> out = new ArrayList<>();
		for (Module m : PrismClient.modules().all()) {
			Target t = m instanceof FinderModule f ? f.pointerTarget() : m instanceof DeathPoint d ? d.pointerTarget() : null;
			if (t != null) out.add(t);
		}
		return out;
	}

	public static void render(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.options.hideGui || mc.player == null || mc.screen instanceof ClickGuiScreen) return;
		List<Target> list = targets();
		if (list.isEmpty()) return;
		Draw.smooth = PrismClient.modules().get(ClickGuiModule.class).font.is("Inter");
		float cx = g.guiWidth() / 2f, cy = g.guiHeight() / 2f;
		Vec3 me = mc.player.getEyePosition(delta.getGameTimeDeltaPartialTick(true));
		float yaw = mc.player.getViewYRot(delta.getGameTimeDeltaPartialTick(true));
		for (Target t : list) {
			double dx = t.pos().x - me.x, dz = t.pos().z - me.z;
			double dist = t.pos().distanceTo(me);
			// the yaw that would face the target, relative to where you look: 0 = ahead, 90 = to the right
			double rel = Math.toDegrees(Math.atan2(-dx, dz)) - yaw;
			rel = ((rel % 360) + 540) % 360 - 180;
			float a = (float) Math.toRadians(rel);
			float ax = cx + (float) Math.sin(a) * RADIUS, ay = cy - (float) Math.cos(a) * RADIUS;
			// fainter when it is straight ahead (you are already looking at it)
			float alpha = (float) Math.min(1, 0.35 + Math.abs(rel) / 40);
			int col = Math.round(255 * alpha) << 24 | (t.rgb() & 0xFFFFFF);
			g.pose().pushMatrix();
			g.pose().translate(ax, ay);
			g.pose().rotate(a);
			Draw.icon(g, "arrow", -6f, -6f, 12, Math.round(150 * alpha) << 24);
			Draw.icon(g, "arrow", -5f, -5f, 10, col);
			g.pose().popMatrix();
			String d = dist >= 1000 ? String.format(java.util.Locale.ROOT, "%.1fk", dist / 1000) : Math.round(dist) + "m";
			float lx = cx + (float) Math.sin(a) * (RADIUS + 13), ly = cy - (float) Math.cos(a) * (RADIUS + 13);
			float tw = Draw.width(d) * 0.75f;
			Draw.textScaled(g, d, lx - tw / 2f, ly - 3, 0.75f, Math.round(255 * alpha) << 24 | 0xF0F0F0, true);
		}
	}
}
