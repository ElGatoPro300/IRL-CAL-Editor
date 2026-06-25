package elgatopro300.cal_lights.manager;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;

import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class GoboManager {
    public static final GoboManager INSTANCE = new GoboManager();

    private static final int TEXTURE_SIZE = 256;
    private int textureArrayId = -1;
    private final List<String> goboNames = new ArrayList<>();
    private final Map<String, Integer> goboNameToIndex = new HashMap<>();

    public void init() {
        scanAndBuild();
    }

    public void scanAndBuild() {
        // Clear previous state
        if (textureArrayId != -1) {
            GL11.glDeleteTextures(textureArrayId);
            textureArrayId = -1;
        }
        goboNames.clear();
        goboNameToIndex.clear();

        // 1. Build default gobos in memory
        List<byte[]> layersData = new ArrayList<>();
        
        // Default 0: Window
        layersData.add(generateWindowGobo());
        goboNames.add("Window");
        goboNameToIndex.put("Window", 0);

        // Default 1: Blinds
        layersData.add(generateBlindsGobo());
        goboNames.add("Blinds");
        goboNameToIndex.put("Blinds", 1);

        // Default 2: Circle
        layersData.add(generateCircleGobo());
        goboNames.add("Circle");
        goboNameToIndex.put("Circle", 2);

        // Default 3: Noise
        layersData.add(generateNoiseGobo());
        goboNames.add("Noise");
        goboNameToIndex.put("Noise", 3);

        // 2. Scan config/cal_lights/gobos/ and config/irl-redactor/cookies/ directories
        Path runDir = MinecraftClient.getInstance().runDirectory.toPath();
        Path gobosDir = runDir.resolve("config").resolve("cal_lights").resolve("gobos");
        Path cookiesDir = runDir.resolve("config").resolve("irl-redactor").resolve("cookies");

        try {
            if (!Files.exists(gobosDir)) {
                Files.createDirectories(gobosDir);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        scanDirectory(gobosDir, layersData);
        scanDirectory(cookiesDir, layersData);
    }

    private void scanDirectory(Path dir, List<byte[]> layersData) {
        if (!Files.exists(dir)) return;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path entry : stream) {
                if (Files.isDirectory(entry)) continue;
                String name = entry.getFileName().toString();
                String lower = name.toLowerCase();
                if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
                    String goboName = name;
                    int lastDot = name.lastIndexOf('.');
                    if (lastDot > 0) {
                        goboName = name.substring(0, lastDot);
                    }
                    if (!goboNameToIndex.containsKey(goboName)) {
                        byte[] data = loadAndResizePng(entry);
                        if (data != null) {
                            int nextIndex = layersData.size();
                            layersData.add(data);
                            goboNames.add(goboName);
                            goboNameToIndex.put(goboName, nextIndex);
                        }
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        // 3. Upload to OpenGL 2D Texture Array
        int numLayers = layersData.size();
        int originalTextureUnit = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL13.glActiveTexture(GL13.GL_TEXTURE20);
        
        textureArrayId = GL11.glGenTextures();
        GL11.glBindTexture(GL30.GL_TEXTURE_2D_ARRAY, textureArrayId);

        GL11.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);

        // Allocate storage for texture array layers
        GL30.glTexImage3D(GL30.GL_TEXTURE_2D_ARRAY, 0, GL11.GL_RGBA8, TEXTURE_SIZE, TEXTURE_SIZE, numLayers, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);

        // Upload layer by layer
        ByteBuffer buffer = MemoryUtil.memAlloc(TEXTURE_SIZE * TEXTURE_SIZE * 4);
        for (int i = 0; i < numLayers; i++) {
            buffer.clear();
            buffer.put(layersData.get(i));
            buffer.flip();
            GL30.glTexSubImage3D(GL30.GL_TEXTURE_2D_ARRAY, 0, 0, 0, i, TEXTURE_SIZE, TEXTURE_SIZE, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buffer);
        }
        MemoryUtil.memFree(buffer);

        GL11.glBindTexture(GL30.GL_TEXTURE_2D_ARRAY, 0);
        GL13.glActiveTexture(originalTextureUnit);
    }

    public int getGoboIndex(String name) {
        if (name == null || name.equalsIgnoreCase("None")) {
            return -1;
        }
        return goboNameToIndex.getOrDefault(name, -1);
    }

    public List<String> getGoboNames() {
        return goboNames;
    }

    public void bind() {
        if (textureArrayId != -1) {
            int originalTextureUnit = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
            GL13.glActiveTexture(GL13.GL_TEXTURE20);
            GL11.glBindTexture(GL30.GL_TEXTURE_2D_ARRAY, textureArrayId);
            GL13.glActiveTexture(originalTextureUnit); // Restore active unit
        }
    }

    // Default Gobo Generation Helpers

    private byte[] generateWindowGobo() {
        byte[] data = new byte[TEXTURE_SIZE * TEXTURE_SIZE * 4];
        int border = 8;
        int centerThickness = 6;
        int centerStart = TEXTURE_SIZE / 2 - centerThickness / 2;
        int centerEnd = TEXTURE_SIZE / 2 + centerThickness / 2;

        for (int y = 0; y < TEXTURE_SIZE; y++) {
            for (int x = 0; x < TEXTURE_SIZE; x++) {
                int idx = (y * TEXTURE_SIZE + x) * 4;
                boolean isBorder = x < border || x >= TEXTURE_SIZE - border || y < border || y >= TEXTURE_SIZE - border;
                boolean isCross = (x >= centerStart && x < centerEnd) || (y >= centerStart && y < centerEnd);

                if (isBorder || isCross) {
                    data[idx] = 0;     // R
                    data[idx + 1] = 0; // G
                    data[idx + 2] = 0; // B
                    data[idx + 3] = (byte) 255; // A
                } else {
                    data[idx] = (byte) 255;
                    data[idx + 1] = (byte) 255;
                    data[idx + 2] = (byte) 255;
                    data[idx + 3] = (byte) 255;
                }
            }
        }
        return data;
    }

    private byte[] generateBlindsGobo() {
        byte[] data = new byte[TEXTURE_SIZE * TEXTURE_SIZE * 4];
        for (int y = 0; y < TEXTURE_SIZE; y++) {
            boolean isSolid = (y / 16) % 2 == 0;
            for (int x = 0; x < TEXTURE_SIZE; x++) {
                int idx = (y * TEXTURE_SIZE + x) * 4;
                byte val = isSolid ? (byte) 255 : 0;
                data[idx] = val;
                data[idx + 1] = val;
                data[idx + 2] = val;
                data[idx + 3] = (byte) 255;
            }
        }
        return data;
    }

    private byte[] generateCircleGobo() {
        byte[] data = new byte[TEXTURE_SIZE * TEXTURE_SIZE * 4];
        float cx = 127.5f;
        float cy = 127.5f;
        float rInner = 100.0f;
        float rOuter = 120.0f;

        for (int y = 0; y < TEXTURE_SIZE; y++) {
            for (int x = 0; x < TEXTURE_SIZE; x++) {
                int idx = (y * TEXTURE_SIZE + x) * 4;
                float dx = x - cx;
                float dy = y - cy;
                float dist = (float) Math.sqrt(dx * dx + dy * dy);

                byte val;
                if (dist < rInner) {
                    val = (byte) 255;
                } else if (dist > rOuter) {
                    val = 0;
                } else {
                    float t = (dist - rInner) / (rOuter - rInner);
                    val = (byte) ((1.0f - t) * 255.0f);
                }

                data[idx] = val;
                data[idx + 1] = val;
                data[idx + 2] = val;
                data[idx + 3] = (byte) 255;
            }
        }
        return data;
    }

    private byte[] generateNoiseGobo() {
        // 1. Generate 16x16 random grid
        float[][] grid = new float[16][16];
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                grid[y][x] = ThreadLocalRandom.current().nextFloat();
            }
        }

        // 2. Bilinearly upscale to 256x256
        byte[] data = new byte[TEXTURE_SIZE * TEXTURE_SIZE * 4];
        for (int y = 0; y < TEXTURE_SIZE; y++) {
            float gy = (float) y / TEXTURE_SIZE * 15f;
            int yLow = (int) Math.floor(gy);
            int yHigh = Math.min(yLow + 1, 15);
            float yWeight = gy - yLow;

            for (int x = 0; x < TEXTURE_SIZE; x++) {
                float gx = (float) x / TEXTURE_SIZE * 15f;
                int xLow = (int) Math.floor(gx);
                int xHigh = Math.min(xLow + 1, 15);
                float xWeight = gx - xLow;

                float v1 = grid[yLow][xLow];
                float v2 = grid[yLow][xHigh];
                float v3 = grid[yHigh][xLow];
                float v4 = grid[yHigh][xHigh];

                float val = (1f - xWeight) * (1f - yWeight) * v1
                          + xWeight * (1f - yWeight) * v2
                          + (1f - xWeight) * yWeight * v3
                          + xWeight * yWeight * v4;

                byte bVal = (byte) (val * 255f);
                int idx = (y * TEXTURE_SIZE + x) * 4;
                data[idx] = bVal;
                data[idx + 1] = bVal;
                data[idx + 2] = bVal;
                data[idx + 3] = (byte) 255;
            }
        }
        return data;
    }

    private byte[] loadAndResizePng(Path path) {
        try (InputStream is = Files.newInputStream(path)) {
            NativeImage img = NativeImage.read(is);
            int w = img.getWidth();
            int h = img.getHeight();

            // Extract RGBA raw bytes
            byte[] srcData = new byte[w * h * 4];
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int pixel = img.getColorArgb(x, y);
                    int idx = (y * w + x) * 4;
                    // NativeImage.getColorArgb returns ARGB
                    srcData[idx] = (byte) ((pixel >> 16) & 0xFF);     // R
                    srcData[idx + 1] = (byte) ((pixel >> 8) & 0xFF);  // G
                    srcData[idx + 2] = (byte) (pixel & 0xFF);         // B
                    srcData[idx + 3] = (byte) ((pixel >> 24) & 0xFF); // A
                }
            }
            img.close();

            if (w == TEXTURE_SIZE && h == TEXTURE_SIZE) {
                return srcData;
            }

            // Bilinear resize to 256x256
            byte[] destData = new byte[TEXTURE_SIZE * TEXTURE_SIZE * 4];
            for (int y = 0; y < TEXTURE_SIZE; y++) {
                float srcY = (float) y / TEXTURE_SIZE * h;
                int yLow = (int) Math.floor(srcY);
                int yHigh = Math.min(yLow + 1, h - 1);
                float yWeight = srcY - yLow;

                for (int x = 0; x < TEXTURE_SIZE; x++) {
                    float srcX = (float) x / TEXTURE_SIZE * w;
                    int xLow = (int) Math.floor(srcX);
                    int xHigh = Math.min(xLow + 1, w - 1);
                    float xWeight = srcX - xLow;

                    int idx = (y * TEXTURE_SIZE + x) * 4;

                    for (int c = 0; c < 4; c++) {
                        float v1 = srcData[(yLow * w + xLow) * 4 + c] & 0xFF;
                        float v2 = srcData[(yLow * w + xHigh) * 4 + c] & 0xFF;
                        float v3 = srcData[(yHigh * w + xLow) * 4 + c] & 0xFF;
                        float v4 = srcData[(yHigh * w + xHigh) * 4 + c] & 0xFF;

                        float val = (1f - xWeight) * (1f - yWeight) * v1
                                  + xWeight * (1f - yWeight) * v2
                                  + (1f - xWeight) * yWeight * v3
                                  + xWeight * yWeight * v4;

                        destData[idx + c] = (byte) val;
                    }
                }
            }
            return destData;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public int getTextureArrayId() {
        return textureArrayId == -1 ? 0 : textureArrayId;
    }
}
