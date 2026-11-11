package com.wailsvlr.prism.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

/**
 * Render types for the block highlights. Each exists in a "through walls" variant (no depth test) and a depth-tested
 * variant; neither writes depth, so highlights never hide each other or the world.
 */
public final class EspPipelines {
	private EspPipelines() { }

	private static RenderPipeline lines(String name, boolean throughWalls) {
		RenderPipeline.Builder b = RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
			.withLocation(Identifier.fromNamespaceAndPath("prismclient", "pipeline/" + name))
			.withDepthWrite(false);
		if (throughWalls) b.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST);
		return b.build();
	}

	private static RenderPipeline fill(String name, boolean throughWalls) {
		RenderPipeline.Builder b = RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
			.withLocation(Identifier.fromNamespaceAndPath("prismclient", "pipeline/" + name))
			.withCull(false)
			.withDepthWrite(false);
		if (throughWalls) b.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST);
		return b.build();
	}

	public static final RenderType LINES_SEE_THROUGH = RenderType.create("prismclient_lines_xray",
		RenderSetup.builder(lines("esp_lines_xray", true)).bufferSize(RenderType.SMALL_BUFFER_SIZE).createRenderSetup());
	public static final RenderType LINES_DEPTH = RenderType.create("prismclient_lines",
		RenderSetup.builder(lines("esp_lines", false)).bufferSize(RenderType.SMALL_BUFFER_SIZE).createRenderSetup());
	public static final RenderType FILL_SEE_THROUGH = RenderType.create("prismclient_fill_xray",
		RenderSetup.builder(fill("esp_fill_xray", true)).bufferSize(RenderType.SMALL_BUFFER_SIZE).sortOnUpload().createRenderSetup());
	public static final RenderType FILL_DEPTH = RenderType.create("prismclient_fill",
		RenderSetup.builder(fill("esp_fill", false)).bufferSize(RenderType.SMALL_BUFFER_SIZE).sortOnUpload().createRenderSetup());

	public static RenderType lines(boolean throughWalls) { return throughWalls ? LINES_SEE_THROUGH : LINES_DEPTH; }
	public static RenderType fill(boolean throughWalls) { return throughWalls ? FILL_SEE_THROUGH : FILL_DEPTH; }
}
