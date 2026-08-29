package elgatopro300.cal_lights.graphics;

import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;

public final class CALLayers {
    private static final BlendFunction BLEND = BlendFunction.TRANSLUCENT;

    private static final RenderPipeline POSITION_COLOR_LINES = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("cal", "pipeline/draw_position_color_lines"))
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.DrawMode.DEBUG_LINES)
            .withBlend(BLEND)
            .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
            .withCull(false)
            .build()
    );

    private static final RenderPipeline POSITION_COLOR_TRIS = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("cal", "pipeline/draw_position_color"))
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.DrawMode.TRIANGLES)
            .withBlend(BLEND)
            .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
            .withCull(false)
            .build()
    );

    private static final RenderPipeline POSITION_COLOR_TRIS_NO_DEPTH = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("cal", "pipeline/draw_position_color_no_depth"))
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.DrawMode.TRIANGLES)
            .withBlend(BLEND)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withCull(false)
            .build()
    );

    private static final RenderPipeline POSITION_TEX_COLOR_QUADS_NO_DEPTH = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("cal", "pipeline/draw_position_tex_color_no_depth"))
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.DrawMode.QUADS)
            .withBlend(BLEND)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withCull(false)
            .build()
    );

    private static RenderType positionColorLinesLayer;
    private static RenderType positionColorLayer;
    private static RenderType positionColorNoDepthLayer;
    private static RenderType positionTexColorNoDepthLayer;

    public static RenderType getPositionColorLinesLayer() {
        if (positionColorLinesLayer == null) {
            positionColorLinesLayer = RenderType.create("cal_draw_position_color_lines",
                RenderSetup.builder(POSITION_COLOR_LINES).sortOnUpload().createRenderSetup());
        }
        return positionColorLinesLayer;
    }

    public static RenderType getPositionColorLayer() {
        if (positionColorLayer == null) {
            positionColorLayer = RenderType.create("cal_draw_position_color",
                RenderSetup.builder(POSITION_COLOR_TRIS).sortOnUpload().createRenderSetup());
        }
        return positionColorLayer;
    }

    public static RenderType getPositionColorNoDepthLayer() {
        if (positionColorNoDepthLayer == null) {
            positionColorNoDepthLayer = RenderType.create("cal_draw_position_color_no_depth",
                RenderSetup.builder(POSITION_COLOR_TRIS_NO_DEPTH).sortOnUpload().createRenderSetup());
        }
        return positionColorNoDepthLayer;
    }

    public static RenderType getPositionTexColorNoDepthLayer(Identifier texture) {
        RenderSetup setup = RenderSetup.builder(POSITION_TEX_COLOR_QUADS_NO_DEPTH)
            .withTexture("Sampler0", texture)
            .sortOnUpload()
            .createRenderSetup();
        return RenderType.create("cal_billboard_" + texture.getPath(), setup);
    }

    public static void flush(BufferBuilder builder, RenderType layer) {
        MeshData built = builder.build();
        if (built != null) {
            layer.draw(built);
        }
    }

    public static void flushLines(BufferBuilder builder) {
        flush(builder, getPositionColorLinesLayer());
    }

    public static void flushTrianglesNoDepth(BufferBuilder builder) {
        flush(builder, getPositionColorNoDepthLayer());
    }

    private CALLayers() {}
}
