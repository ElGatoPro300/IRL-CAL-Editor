package elgatopro300.cal_lights.manager;

import net.minecraft.client.Minecraft;

import com.mojang.blaze3d.platform.NativeImage;

import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
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

    private static final int RES = 256;
    private static final int MAX_LAYERS = 16;

    private int glTextureId = 0;
    private boolean initialized = false;
    private final List<String> goboNames = new ArrayList<>();
    private final Map<String, Integer> goboNameToIndex = new HashMap<>();

    public void init() {
        scanAndBuild();
    }

    public void scanAndBuild() {
        delete();
        goboNames.clear();
        goboNameToIndex.clear();

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

        // Scan config/cal_lights/gobos/ and config/irl-redactor/cookies/
        Path runDir = Minecraft.getInstance().gameDirectory.toPath();
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

        uploadAll(layersData);
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
    }

    private void uploadAll(List<byte[]> layersData) {
        int numLayers = layersData.size();
        if (numLayers == 0) return;

        // Save GL state: PBO, unpack alignment, row length, skip
        int prevTex = GL11.glGetInteger(GL30.GL_TEXTURE_BINDING_2D_ARRAY);
        int prevPbo = GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING);
        int prevAlign = GL11.glGetInteger(GL11.GL_UNPACK_ALIGNMENT);
        int prevRowLen = GL11.glGetInteger(GL11.GL_UNPACK_ROW_LENGTH);
        int prevSkipRows = GL11.glGetInteger(GL11.GL_UNPACK_SKIP_ROWS);
        int prevSkipPixels = GL11.glGetInteger(GL11.GL_UNPACK_SKIP_PIXELS);
        int prevImgHeight = GL11.glGetInteger(GL12.GL_UNPACK_IMAGE_HEIGHT);
        int prevSkipImages = GL11.glGetInteger(GL12.GL_UNPACK_SKIP_IMAGES);

        GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0);
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
        GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0);
        GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0);
        GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0);
        GL11.glPixelStorei(GL12.GL_UNPACK_IMAGE_HEIGHT, 0);
        GL11.glPixelStorei(GL12.GL_UNPACK_SKIP_IMAGES, 0);

        glTextureId = GL11.glGenTextures();
        GL11.glBindTexture(GL30.GL_TEXTURE_2D_ARRAY, glTextureId);

        GL30.glTexImage3D(GL30.GL_TEXTURE_2D_ARRAY, 0, GL30.GL_R8, RES, RES, numLayers, 0, GL11.GL_RED, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);

        GL11.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY, GL11.GL_TEXTURE_WRAP_S, GL13.GL_CLAMP_TO_BORDER);
        GL11.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY, GL11.GL_TEXTURE_WRAP_T, GL13.GL_CLAMP_TO_BORDER);
        try (var stack = MemoryStack.stackPush()) {
            FloatBuffer border = stack.floats(0f, 0f, 0f, 0f);
            GL11.glTexParameterfv(GL30.GL_TEXTURE_2D_ARRAY, GL11.GL_TEXTURE_BORDER_COLOR, border);
        }

        ByteBuffer buf = MemoryUtil.memAlloc(RES * RES);
        for (int i = 0; i < numLayers; i++) {
            buf.clear();
            byte[] src = layersData.get(i);
            // Convert RGBA (4 bytes/pixel) to R (1 byte/pixel, take red channel)
            for (int j = 0; j < RES * RES; j++) {
                buf.put(src[j * 4]);
            }
            buf.flip();
            GL12.glTexSubImage3D(GL30.GL_TEXTURE_2D_ARRAY, 0, 0, 0, i, RES, RES, 1, GL11.GL_RED, GL11.GL_UNSIGNED_BYTE, buf);
        }
        MemoryUtil.memFree(buf);

        GL11.glBindTexture(GL30.GL_TEXTURE_2D_ARRAY, prevTex);
        GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, prevPbo);
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, prevAlign);
        GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, prevRowLen);
        GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, prevSkipRows);
        GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, prevSkipPixels);
        GL11.glPixelStorei(GL12.GL_UNPACK_IMAGE_HEIGHT, prevImgHeight);
        GL11.glPixelStorei(GL12.GL_UNPACK_SKIP_IMAGES, prevSkipImages);

        initialized = true;
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
        if (glTextureId != 0) {
            int prev = GL11.glGetInteger(GL30.GL_TEXTURE_BINDING_2D_ARRAY);
            GL11.glBindTexture(GL30.GL_TEXTURE_2D_ARRAY, glTextureId);
            GL11.glBindTexture(GL30.GL_TEXTURE_2D_ARRAY, prev);
        }
    }

    public int getTextureArrayId() {
        return glTextureId;
    }

    private void delete() {
        if (glTextureId != 0) {
            GL11.glDeleteTextures(glTextureId);
            glTextureId = 0;
            initialized = false;
        }
    }

    private byte[] generateWindowGobo() {
        byte[] data = new byte[RES * RES * 4];
        int border = 8;
        int centerThickness = 6;
        int centerStart = RES / 2 - centerThickness / 2;
        int centerEnd = RES / 2 + centerThickness / 2;

        for (int y = 0; y < RES; y++) {
            for (int x = 0; x < RES; x++) {
                int idx = (y * RES + x) * 4;
                boolean isBorder = x < border || x >= RES - border || y < border || y >= RES - border;
                boolean isCross = (x >= centerStart && x < centerEnd) || (y >= centerStart && y < centerEnd);

                if (isBorder || isCross) {
                    data[idx] = 0;
                    data[idx + 1] = 0;
                    data[idx + 2] = 0;
                    data[idx + 3] = (byte) 255;
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
        byte[] data = new byte[RES * RES * 4];
        for (int y = 0; y < RES; y++) {
            boolean isSolid = (y / 16) % 2 == 0;
            for (int x = 0; x < RES; x++) {
                int idx = (y * RES + x) * 4;
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
        byte[] data = new byte[RES * RES * 4];
        float cx = 127.5f;
        float cy = 127.5f;
        float rInner = 100.0f;
        float rOuter = 120.0f;

        for (int y = 0; y < RES; y++) {
            for (int x = 0; x < RES; x++) {
                int idx = (y * RES + x) * 4;
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
        float[][] grid = new float[16][16];
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                grid[y][x] = ThreadLocalRandom.current().nextFloat();
            }
        }

        byte[] data = new byte[RES * RES * 4];
        for (int y = 0; y < RES; y++) {
            float gy = (float) y / RES * 15f;
            int yLow = (int) Math.floor(gy);
            int yHigh = Math.min(yLow + 1, 15);
            float yWeight = gy - yLow;

            for (int x = 0; x < RES; x++) {
                float gx = (float) x / RES * 15f;
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
                int idx = (y * RES + x) * 4;
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

            byte[] srcData = new byte[w * h * 4];
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int pixel = img.getPixel(x, y);
                    int idx = (y * w + x) * 4;
                    srcData[idx] = (byte) ((pixel >> 16) & 0xFF);
                    srcData[idx + 1] = (byte) ((pixel >> 8) & 0xFF);
                    srcData[idx + 2] = (byte) (pixel & 0xFF);
                    srcData[idx + 3] = (byte) ((pixel >> 24) & 0xFF);
                }
            }
            img.close();

            if (w == RES && h == RES) {
                return srcData;
            }

            byte[] destData = new byte[RES * RES * 4];
            for (int y = 0; y < RES; y++) {
                float srcY = (float) y / RES * h;
                int yLow = (int) Math.floor(srcY);
                int yHigh = Math.min(yLow + 1, h - 1);
                float yWeight = srcY - yLow;

                for (int x = 0; x < RES; x++) {
                    float srcX = (float) x / RES * w;
                    int xLow = (int) Math.floor(srcX);
                    int xHigh = Math.min(xLow + 1, w - 1);
                    float xWeight = srcX - xLow;

                    int idx = (y * RES + x) * 4;

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
}
