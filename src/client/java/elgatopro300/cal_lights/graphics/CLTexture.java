package elgatopro300.cal_lights.graphics;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Wrapper around a dynamically-loaded PNG texture. In 1.21.11 raw GL texture
 * ids are no longer exposed by MC's texture abstraction, so every texture is
 * registered with the TextureManager under a synthetic Identifier and rendered
 * through MC's RenderPipeline/RenderLayer paths.
 */
public class CLTexture {
    private static final AtomicInteger COUNTER = new AtomicInteger();

    private final NativeImageBackedTexture mcTexture;
    public final Identifier identifier;
    public final int width;
    public final int height;

    public CLTexture(InputStream png, boolean nearest) {
        if (png == null) {
            throw new IllegalArgumentException("PNG input stream cannot be null");
        }
        try {
            NativeImage image = NativeImage.read(png);
            this.width = image.getWidth();
            this.height = image.getHeight();
            String name = "cal_texture_" + COUNTER.getAndIncrement();
            this.mcTexture = new NativeImageBackedTexture(() -> name, image);
            this.identifier = Identifier.of("cal", name);
            MinecraftClient.getInstance().getTextureManager().registerTexture(this.identifier, this.mcTexture);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read texture stream", e);
        }
    }

    public CLTexture(InputStream png) {
        this(png, false);
    }

    /**
     * Returns the underlying MC texture object. Useful when a caller needs the
     * GPU texture view directly.
     */
    public NativeImageBackedTexture getMcTexture() {
        return this.mcTexture;
    }

    public void delete() {
        this.mcTexture.close();
    }
}
