package elgatopro300.cal_lights.graphics;

import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;

import com.mojang.blaze3d.systems.RenderSystem;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import java.io.IOException;
import java.io.InputStream;

public class CLTexture {
    private final NativeImageBackedTexture mcTexture;
    public final int id;
    public final int width;
    public final int height;
    private final boolean nearest;

    public CLTexture(InputStream png, boolean nearest) {
        if (png == null) {
            throw new IllegalArgumentException("PNG input stream cannot be null");
        }
        RenderSystem.assertOnRenderThread();
        try {
            NativeImage image = NativeImage.read(png);
            this.width = image.getWidth();
            this.height = image.getHeight();
            this.mcTexture = new NativeImageBackedTexture(image);
            this.id = this.mcTexture.getGlId();
            this.nearest = nearest;
        } catch (IOException e) {
            throw new RuntimeException("Failed to read texture stream", e);
        }
    }

    public CLTexture(InputStream png) {
        this(png, false);
    }

    public void bind() {
        RenderSystem.setShaderTexture(0, this.id);
        int filter = this.nearest ? GL11.GL_NEAREST : GL11.GL_LINEAR;
        RenderSystem.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, filter);
        RenderSystem.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, filter);
        RenderSystem.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        RenderSystem.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
    }

    public void delete() {
        RenderSystem.assertOnRenderThread();
        this.mcTexture.close();
    }
}
