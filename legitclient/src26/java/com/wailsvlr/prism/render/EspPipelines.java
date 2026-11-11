package com.wailsvlr.prism.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.rendertype.PrismRenderTypes;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

/**
 * Render types for the block highlights (26.1.2 build). Each exists in a "through walls" variant (no depth test) and a
 * depth-tested variant; neither writes depth, so highlights never hide each other or the world. 26.1 made vanilla's
 * line and debug-fill snippets private, so they are rebuilt here with the same shaders, uniforms and formats.
 */
public final class EspPipelines {
	private EspPipelines() { }

	private static DepthStencilState depth(boolean throughWalls) {
		return new DepthStencilState(throughWalls ? CompareOp.ALWAYS_PASS : CompareOp.LESS_THAN_OR_EQUAL, false);
	}

	private static RenderPipeline lines(String name, boolean throughWalls) {
		return RenderPipeline.builder()
			.withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
			.withUniform("Projection", UniformType.UNIFORM_BUFFER)
			.withUniform("Fog", UniformType.UNIFORM_BUFFER)
			.withUniform("Globals", UniformType.UNIFORM_BUFFER)
			.withVertexShader("core/rendertype_lines")
			.withFragmentShader("core/rendertype_lines")
			.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
			.withCull(false)
			.withVertexFormat(DefaultVertexFormat.POSITION_COLOR_NORMAL_LINE_WIDTH, VertexFormat.Mode.LINES)
			.withDepthStencilState(depth(throughWalls))
			.withLocation(Identifier.fromNamespaceAndPath("prismclient", "pipeline/" + name))
			.build();
	}

	private static RenderPipeline fill(String name, boolean throughWalls) {
		return RenderPipeline.builder()
			.withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
			.withUniform("Projection", UniformType.UNIFORM_BUFFER)
			.withVertexShader("core/position_color")
			.withFragmentShader("core/position_color")
			.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
			.withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
			.withCull(false)
			.withDepthStencilState(depth(throughWalls))
			.withLocation(Identifier.fromNamespaceAndPath("prismclient", "pipeline/" + name))
			.build();
	}

	public static final RenderType LINES_SEE_THROUGH = PrismRenderTypes.create("prismclient_lines_xray",
		RenderSetup.builder(lines("esp_lines_xray", true)).bufferSize(RenderType.SMALL_BUFFER_SIZE).createRenderSetup());
	public static final RenderType LINES_DEPTH = PrismRenderTypes.create("prismclient_lines",
		RenderSetup.builder(lines("esp_lines", false)).bufferSize(RenderType.SMALL_BUFFER_SIZE).createRenderSetup());
	public static final RenderType FILL_SEE_THROUGH = PrismRenderTypes.create("prismclient_fill_xray",
		RenderSetup.builder(fill("esp_fill_xray", true)).bufferSize(RenderType.SMALL_BUFFER_SIZE).sortOnUpload().createRenderSetup());
	public static final RenderType FILL_DEPTH = PrismRenderTypes.create("prismclient_fill",
		RenderSetup.builder(fill("esp_fill", false)).bufferSize(RenderType.SMALL_BUFFER_SIZE).sortOnUpload().createRenderSetup());

	public static RenderType lines(boolean throughWalls) { return throughWalls ? LINES_SEE_THROUGH : LINES_DEPTH; }
	public static RenderType fill(boolean throughWalls) { return throughWalls ? FILL_SEE_THROUGH : FILL_DEPTH; }
}
