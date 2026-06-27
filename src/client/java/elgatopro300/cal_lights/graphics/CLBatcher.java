package elgatopro300.cal_lights.graphics;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;

import org.joml.Matrix4f;

public class CLBatcher {
    private final GuiGraphics ctx;

    public CLBatcher(GuiGraphics ctx) {
        this.ctx = ctx;
    }

    public GuiGraphics getCtx() {
        return this.ctx;
    }

    public void box(float x1, float y1, float x2, float y2, int color) {
        this.ctx.fill((int) x1, (int) y1, (int) x2, (int) y2, color);
    }

    public void box(float x, float y, float w, float h, int color1, int color2, int color3, int color4) {
        // Most callers use this for vertical or horizontal gradients.
        if (color1 == color2 && color3 == color4 && (color1 != color3)) {
            this.ctx.fillGradient((int) x, (int) y, (int) (x + w), (int) (y + h), color1, color3);
        } else if (color1 == color3 && color2 == color4 && (color1 != color2)) {
            // Horizontal gradient: fillGradient only does vertical, so approximate
            // with a few vertical strips. The UI uses only subtle horizontal fades,
            // so 8 strips is visually fine.
            int strips = 8;
            float stripW = w / strips;
            for (int i = 0; i < strips; i++) {
                float t0 = i / (float) strips;
                float t1 = (i + 1) / (float) strips;
                int c0 = lerpColor(color1, color2, t0);
                int c1 = lerpColor(color1, color2, t1);
                int sx = (int) (x + t0 * w);
                int ex = (int) (x + t1 * w);
                this.ctx.fillGradient(sx, (int) y, ex, (int) (y + h), c0, c1);
            }
        } else {
            this.ctx.fill((int) x, (int) y, (int) (x + w), (int) (y + h), averageColor(color1, color2, color3, color4));
        }
    }

    public void gradientV(float x1, float y1, float x2, float y2, int topColor, int bottomColor) {
        this.ctx.fillGradient((int) x1, (int) y1, (int) x2, (int) y2, topColor, bottomColor);
    }

    public void gradientH(float x1, float y1, float x2, float y2, int leftColor, int rightColor) {
        int strips = 8;
        float x1f = x1, x2f = x2, y1f = y1, y2f = y2;
        float w = x2f - x1f;
        for (int i = 0; i < strips; i++) {
            float t0 = i / (float) strips;
            float t1 = (i + 1) / (float) strips;
            int c0 = lerpColor(leftColor, rightColor, t0);
            int c1 = lerpColor(leftColor, rightColor, t1);
            int sx = (int) (x1f + t0 * w);
            int ex = (int) (x1f + t1 * w);
            this.ctx.fillGradient(sx, (int) y1f, ex, (int) y2f, c0, c1);
        }
    }

    public void outline(float x1, float y1, float x2, float y2, int color, int border) {
        this.ctx.fill((int) x1, (int) y1, (int) (x1 + border), (int) y2, color);
        this.ctx.fill((int) (x2 - border), (int) y1, (int) x2, (int) y2, color);
        this.ctx.fill((int) (x1 + border), (int) y1, (int) (x2 - border), (int) (y1 + border), color);
        this.ctx.fill((int) (x1 + border), (int) (y2 - border), (int) (x2 - border), (int) y2, color);
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
        if (texture == null) {
            return;
        }
        int ix = (int) x;
        int iy = (int) y;
        this.ctx.blit(RenderPipelines.GUI_TEXTURED, texture.identifier, ix, iy, (float) texX, (float) texY, texW, texH, texture.width, texture.height, color);
    }

    public void text(String s, float x, float y, int color) {
        this.ctx.drawString(Minecraft.getInstance().font, s, (int) x, (int) y, color, false);
    }

    public void textShadow(String s, float x, float y, int color) {
        this.ctx.drawString(Minecraft.getInstance().font, s, (int) x, (int) y, color, true);
    }

    public void clip(int x, int y, int w, int h) {
        this.ctx.enableScissor(x, y, x + w, y + h);
    }

    public void unclip() {
        this.ctx.disableScissor();
    }

    private static int lerpColor(int a, int b, float t) {
        int aa = (a >>> 24) & 0xFF;
        int ar = (a >>> 16) & 0xFF;
        int ag = (a >>> 8) & 0xFF;
        int ab = a & 0xFF;
        int ba = (b >>> 24) & 0xFF;
        int br = (b >>> 16) & 0xFF;
        int bg = (b >>> 8) & 0xFF;
        int bb = b & 0xFF;
        int ca = (int) (aa + (ba - aa) * t);
        int cr = (int) (ar + (br - ar) * t);
        int cg = (int) (ag + (bg - ag) * t);
        int cb = (int) (ab + (bb - ab) * t);
        return (ca << 24) | (cr << 16) | (cg << 8) | cb;
    }

    private static int averageColor(int c1, int c2, int c3, int c4) {
        int a = (((c1 >>> 24) & 0xFF) + ((c2 >>> 24) & 0xFF) + ((c3 >>> 24) & 0xFF) + ((c4 >>> 24) & 0xFF)) / 4;
        int r = (((c1 >>> 16) & 0xFF) + ((c2 >>> 16) & 0xFF) + ((c3 >>> 16) & 0xFF) + ((c4 >>> 16) & 0xFF)) / 4;
        int g = (((c1 >>> 8) & 0xFF) + ((c2 >>> 8) & 0xFF) + ((c3 >>> 8) & 0xFF) + ((c4 >>> 8) & 0xFF)) / 4;
        int b = ((c1 & 0xFF) + (c2 & 0xFF) + (c3 & 0xFF) + (c4 & 0xFF)) / 4;
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
