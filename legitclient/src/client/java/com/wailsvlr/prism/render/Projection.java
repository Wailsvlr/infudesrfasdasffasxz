package com.wailsvlr.prism.render;

import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/** Keeps the camera matrices of the last frame so the HUD can place labels over world positions. */
public final class Projection {
	private static final Matrix4f viewProj = new Matrix4f();
	private static Vec3 camera = Vec3.ZERO;
	private static boolean valid;

	private Projection() { }

	/** Called at the start of world rendering each frame with the matrices the world is drawn with. */
	public static synchronized void capture(Matrix4f view, Matrix4f projection, Vec3 cameraPos) {
		viewProj.set(projection).mul(view);
		camera = cameraPos;
		valid = true;
	}

	/**
	 * Projects a world position to GUI coordinates. Returns {x, y} or null when it is behind the camera.
	 */
	public static synchronized float[] toScreen(Vec3 pos, int guiWidth, int guiHeight) {
		if (!valid) return null;
		Vector4f v = new Vector4f((float) (pos.x - camera.x), (float) (pos.y - camera.y), (float) (pos.z - camera.z), 1f);
		viewProj.transform(v);
		if (v.w <= 0.05f) return null;
		float nx = v.x / v.w, ny = v.y / v.w;
		if (nx < -1.2f || nx > 1.2f || ny < -1.2f || ny > 1.2f) return null;
		return new float[]{(nx * 0.5f + 0.5f) * guiWidth, (1f - (ny * 0.5f + 0.5f)) * guiHeight};
	}
}
