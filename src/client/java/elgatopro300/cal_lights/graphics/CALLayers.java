package elgatopro300.cal_lights.graphics;

import elgatopro300.cal_lights.client.mixin.RenderPipelinesInvoker;
import elgatopro300.cal_lights.client.mixin.RenderTypeInvoker;

import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

import com.mojang.blaze3d.IndexType;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class CALLayers {
    private static final ByteBufferBuilder SHARED_BUFFER = new ByteBufferBuilder(2 * 1024 * 1024);
    private static final Map<Identifier, RenderType> BILLBOARD_LAYERS = new ConcurrentHashMap<>();

    private static final RenderPipeline POSITION_COLOR_LINES = RenderPipelinesInvoker.invokeRegister(
        RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("cal", "pipeline/lines_no_depth"))
            .withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
            .withVertexShader("core/position_color")
            .withFragmentShader("core/position_color")
            .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.DEBUG_LINES)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
            .withCull(false)
            .build()
    );

    private static final RenderPipeline POSITION_COLOR_TRIS = RenderPipelinesInvoker.invokeRegister(
        RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("cal", "pipeline/tris_no_depth"))
            .withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
            .withVertexShader("core/position_color")
            .withFragmentShader("core/position_color")
            .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
            .withCull(false)
            .build()
    );

    private static final RenderPipeline POSITION_TEX_COLOR_BILLBOARD = RenderPipelinesInvoker.invokeRegister(
        RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("cal", "pipeline/billboard_no_depth"))
            .withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
            .withVertexShader("core/position_tex_color")
            .withFragmentShader("core/position_tex_color")
            .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
            .withCull(false)
            .build()
    );

    private static RenderType linesLayer;
    private static RenderType trianglesLayer;

    public static RenderType getLinesLayer() {
        if (linesLayer == null) {
            linesLayer = RenderTypeInvoker.invokeCreate("cal_lines_no_depth",
                RenderSetup.builder(POSITION_COLOR_LINES).sortOnUpload().createRenderSetup());
        }
        return linesLayer;
    }

    public static RenderType getTrianglesLayer() {
        if (trianglesLayer == null) {
            trianglesLayer = RenderTypeInvoker.invokeCreate("cal_tris_no_depth",
                RenderSetup.builder(POSITION_COLOR_TRIS).sortOnUpload().createRenderSetup());
        }
        return trianglesLayer;
    }

    public static RenderType getBillboardLayer(Identifier texture) {
        return BILLBOARD_LAYERS.computeIfAbsent(texture, tex -> {
            RenderSetup setup = RenderSetup.builder(POSITION_TEX_COLOR_BILLBOARD)
                .withTexture("Sampler0", tex)
                .sortOnUpload()
                .createRenderSetup();
            return RenderTypeInvoker.invokeCreate("cal_billboard_" + tex.getPath(), setup);
        });
    }

    public static BufferBuilder begin(PrimitiveTopology topology, VertexFormat format) {
        return new BufferBuilder(SHARED_BUFFER, topology, format);
    }

    public static BufferBuilder beginLines() {
        return begin(PrimitiveTopology.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
    }

    public static BufferBuilder beginTriangles() {
        return begin(PrimitiveTopology.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
    }

    public static BufferBuilder beginQuads(VertexFormat format) {
        return begin(PrimitiveTopology.QUADS, format);
    }

    public static void flush(BufferBuilder builder, RenderType layer) {
        MeshData mesh = builder.build();
        if (mesh == null) return;
        try (mesh) {
            MeshData.DrawState state = mesh.drawState();
            int vertexCount = state.vertexCount();
            if (vertexCount == 0) return;

            GpuBuffer vertexBuffer = RenderSystem.getDevice().createBuffer(
                () -> "cal_dynamic_vbo",
                GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_MAP_WRITE,
                mesh.vertexBuffer()
            );
            if (vertexBuffer == null) return;

            try (vertexBuffer) {
                GpuBuffer indexBuffer = null;
                IndexType indexType;
                int indexCount = state.indexCount();

                if (mesh.indexBuffer() != null && indexCount > 0) {
                    indexBuffer = RenderSystem.getDevice().createBuffer(
                        () -> "cal_dynamic_ibo",
                        GpuBuffer.USAGE_INDEX | GpuBuffer.USAGE_MAP_WRITE,
                        mesh.indexBuffer()
                    );
                    indexType = state.indexType();
                } else {
                    RenderSystem.AutoStorageIndexBuffer autoIdx = RenderSystem.getSequentialBuffer(state.primitiveTopology());
                    indexBuffer = autoIdx.getBuffer(indexCount > 0 ? indexCount : vertexCount);
                    indexType = autoIdx.type();
                    if (indexCount <= 0) {
                        indexCount = vertexCount;
                    }
                }

                PreparedRenderType prepared = layer.prepare();
                prepared.drawFromBuffer(vertexBuffer, indexBuffer, indexType, 0, 0, indexCount);

                if (mesh.indexBuffer() != null && indexBuffer != null) {
                    indexBuffer.close();
                }
            }
        }
    }

    public static void flushLines(BufferBuilder builder) {
        flush(builder, getLinesLayer());
    }

    public static void flushTriangles(BufferBuilder builder) {
        flush(builder, getTrianglesLayer());
    }

    private CALLayers() {}
}
