package net.minecraft.client.renderer.rendertype;

/**
 * 26.1 made RenderType.create package-private; this tiny class lives in the same package (the game and Prism share
 * one class loader) only to reach it. Prism Client 26.1.2 build only.
 */
public final class PrismRenderTypes {
	private PrismRenderTypes() { }

	public static RenderType create(String name, RenderSetup setup) {
		return RenderType.create(name, setup);
	}
}
