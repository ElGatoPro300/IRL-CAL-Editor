package elgatopro300.cal_lights.graphics;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;

import org.joml.Matrix4f;

import com.mojang.blaze3d.systems.RenderSystem;

public class CLBatcher {
    private final DrawContext ctx;

    public CLBatcher(DrawContext ctx) {
        this.ctx = ctx;
    }

    public DrawContext getCtx() {
        return this.ctx;
    }

    public void box(float x1, float y1, float x2, float y2, int color) {
        this.box(x1, y1, x2 - x1, y2 - y1, color, color, color, color);
    }

    public void box(float x, float y, float w, float h, int color1, int color2, int color3, int color4) {
        Matrix4f matrix4f = this.ctx.getMatrices().peek().getPositionMatrix();
        BufferBuilder builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

        builder.vertex(matrix4f, x, y, 0).color(color1);
        builder.vertex(matrix4f, x, y + h, 0).color(color3);
        builder.vertex(matrix4f, x + w, y + h, 0).color(color4);
        builder.vertex(matrix4f, x + w, y, 0).color(color2);

        RenderSystem.enableBlend();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        BufferRenderer.drawWithGlobalProgram(builder.end());
    }

    public void gradientV(float x1, float y1, float x2, float y2, int topColor, int bottomColor) {
        this.box(x1, y1, x2 - x1, y2 - y1, topColor, topColor, bottomColor, bottomColor);
    }

    public void gradientH(float x1, float y1, float x2, float y2, int leftColor, int rightColor) {
        this.box(x1, y1, x2 - x1, y2 - y1, leftColor, rightColor, leftColor, rightColor);
    }

    public void outline(float x1, float y1, float x2, float y2, int color, int border) {
        this.box(x1, y1, x1 + border, y2, color);
        this.box(x2 - border, y1, x2, y2, color);
        this.box(x1 + border, y1, x2 - border, y1 + border, color);
        this.box(x1 + border, y2 - border, x2 - border, y2, color);
    }

    public void icon(CLIcon icon, float x, float y, int color) {
        if (icon == null) return;
        if (icon.staticTexture != null && icon.tintTexture != null) {
            this.drawSingleLayer(icon.tintTexture, icon.x, icon.y, icon.w, icon.h, x, y, color);
            this.drawSingleLayer(icon.staticTexture, icon.x, icon.y, icon.w, icon.h, x, y, 0xFFFFFFFF);
        } else if (icon.texture != null) {
            this.drawSingleLayer(icon.texture, icon.x, icon.y, icon.w, icon.h, x, y, color);
        }
    }

    private void drawSingleLayer(CLTexture texture, int texX, int texY, int texW, int texH, float x, float y, int color) {
        texture.bind();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        Matrix4f matrix = this.ctx.getMatrices().peek().getPositionMatrix();
        BufferBuilder builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_TEXTURE_COLOR);

        float u1 = texX / (float) texture.width;
        float v1 = texY / (float) texture.height;
        float u2 = (texX + texW) / (float) texture.width;
        float v2 = (texY + texH) / (float) texture.height;
        float x2 = x + texW;
        float y2 = y + texH;

        builder.vertex(matrix, x, y2, 0).texture(u1, v2).color(color);
        builder.vertex(matrix, x2, y2, 0).texture(u2, v2).color(color);
        builder.vertex(matrix, x2, y, 0).texture(u2, v1).color(color);
        builder.vertex(matrix, x, y2, 0).texture(u1, v2).color(color);
        builder.vertex(matrix, x2, y, 0).texture(u2, v1).color(color);
        builder.vertex(matrix, x, y, 0).texture(u1, v1).color(color);

        RenderSystem.enableBlend();
        RenderSystem.setShader(GameRenderer::getPositionTexColorProgram);
        BufferRenderer.drawWithGlobalProgram(builder.end());
    }

    public void text(String s, float x, float y, int color) {
        this.ctx.drawText(MinecraftClient.getInstance().textRenderer, s, (int) x, (int) y, color, false);
    }

    public void textShadow(String s, float x, float y, int color) {
        this.ctx.drawText(MinecraftClient.getInstance().textRenderer, s, (int) x, (int) y, color, true);
    }

    public void clip(int x, int y, int w, int h) {
        this.ctx.enableScissor(x, y, x + w, y + h);
    }

    public void unclip() {
        this.ctx.disableScissor();
    }
}
