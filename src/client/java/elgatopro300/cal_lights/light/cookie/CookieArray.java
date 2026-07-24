package elgatopro300.cal_lights.light.cookie;

import elgatopro300.cal_lights.CALLightsClient;

import org.qualet.irl.light.CookieArrayBase;

import net.fabricmc.loader.api.FabricLoader;

import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

/**
 * Grayscale gobo/cookie mask array for spot lights, backed by
 * {@link CookieArrayBase} (R8, CLAMP_TO_BORDER, guarded PBO/UNPACK upload).
 *
 * <p>Built-in procedural masks are uploaded on {@link #init()}; user images load
 * lazily from {@code config/cal_lights/gobos/} and {@code config/irl-redactor/cookies/}.</p>
 */
public final class CookieArray extends CookieArrayBase {
    public static final int RES = CookieArrayBase.RES;
    /** Four built-in masks + up to sixteen custom files. */
    public static final int MAX_LAYERS = 20;

    /**
     * Optional bridge from irlite when both mods are installed. Routes all cookie
     * lookups to the shared {@code irl_cookieArray} texture.
     */
    public interface Host {
        void init();

        int resolveName(String name);

        int textureId();

        List<String> catalog();

        void reload();
    }

    private static final CookieArray INSTANCE = new CookieArray();
    private static final String[] BUILTINS = {"Window", "Blinds", "Circle", "Noise"};

    private static Host host;

    private final Map<String, Integer> nameToLayer = new HashMap<>();
    private final Map<String, Path> nameToPath = new HashMap<>();
    private final List<String> catalog = new ArrayList<>();
    private int nextLayer = 0;
    private boolean builtinsReady;

    private CookieArray() {
        super(MAX_LAYERS);
    }

    public static Path dir() {
        return FabricLoader.getInstance().getConfigDir().resolve("cal_lights").resolve("gobos");
    }

    public static Path cookiesDir() {
        return FabricLoader.getInstance().getConfigDir().resolve("irl-redactor").resolve("cookies");
    }

    public static void installHost(Host bridge) {
        host = bridge;
    }

    public static void init() {
        if (host != null) {
            host.init();
            return;
        }
        INSTANCE.initBuiltins();
        INSTANCE.scanCatalog();
    }

    public static int getGlTextureId() {
        if (host != null) {
            return host.textureId();
        }
        return INSTANCE.textureId();
    }

    public static List<String> available() {
        if (host != null) {
            return host.catalog();
        }
        INSTANCE.ensureCatalog();
        return List.copyOf(INSTANCE.catalog);
    }

    public static int resolve(String name) {
        if (host != null) {
            return host.resolveName(name);
        }
        return INSTANCE.resolve0(name);
    }

    public static void reload() {
        if (host != null) {
            host.reload();
            return;
        }
        INSTANCE.reload0();
    }

    public static void delete() {
        if (host != null) {
            host.reload();
            return;
        }
        INSTANCE.deleteTexture();
    }

    private void initBuiltins() {
        if (builtinsReady) {
            return;
        }
        for (String name : BUILTINS) {
            ByteBuffer pixels = generateBuiltin(name);
            int layer = nextLayer++;
            uploadLayer(pixels, layer);
            MemoryUtil.memFree(pixels);
            nameToLayer.put(name, layer);
        }
        builtinsReady = true;
    }

    private void ensureCatalog() {
        if (catalog.isEmpty()) {
            scanCatalog();
        }
    }

    private void scanCatalog() {
        catalog.clear();
        nameToPath.clear();
        for (String builtin : BUILTINS) {
            catalog.add(builtin);
        }
        scanDir(dir());
        scanDir(cookiesDir());
    }

    private void scanDir(Path folder) {
        try {
            if (!Files.isDirectory(folder)) {
                Files.createDirectories(folder);
                return;
            }
            try (Stream<Path> stream = Files.list(folder)) {
                stream.filter(Files::isRegularFile)
                    .map(p -> p.getFileName().toString())
                    .filter(CookieArray::isImage)
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .forEach(fileName -> {
                        String key = displayName(fileName);
                        if (!nameToPath.containsKey(key)) {
                            nameToPath.put(key, folder.resolve(fileName));
                            if (!catalog.contains(key)) {
                                catalog.add(key);
                            }
                        }
                    });
            }
        } catch (IOException e) {
            CALLightsClient.LOGGER.warn("Cookie folder list failed: {}", folder, e);
        }
    }

    private int resolve0(String name) {
        if (name == null || name.isEmpty() || "None".equalsIgnoreCase(name)) {
            return -1;
        }

        Integer cached = nameToLayer.get(name);
        if (cached != null) {
            return cached;
        }

        String key = displayName(name);
        cached = nameToLayer.get(key);
        if (cached != null) {
            return cached;
        }

        if (nextLayer >= MAX_LAYERS) {
            return -1;
        }

        Path path = nameToPath.get(key);
        if (path == null) {
            ensureCatalog();
            path = nameToPath.get(key);
        }
        if (path == null) {
            return -1;
        }

        int layer = load(path, key);
        nameToLayer.put(key, layer);
        if (!key.equals(name)) {
            nameToLayer.put(name, layer);
        }
        return layer;
    }

    private int load(Path path, String key) {
        byte[] raw;
        try {
            raw = Files.readAllBytes(path);
        } catch (IOException e) {
            CALLightsClient.LOGGER.warn("Cookie read failed: {}", path, e);
            return -1;
        }

        ByteBuffer pixels = CookieArrayBase.decode(raw);
        if (pixels == null) {
            CALLightsClient.LOGGER.warn("Cookie decode failed: {} ({})", key, STBImage.stbi_failure_reason());
            return -1;
        }
        try {
            int layer = nextLayer++;
            uploadLayer(pixels, layer);
            CALLightsClient.LOGGER.debug("Cookie loaded '{}' -> layer {}", key, layer);
            return layer;
        } finally {
            MemoryUtil.memFree(pixels);
        }
    }

    private void reload0() {
        nameToLayer.clear();
        nextLayer = 0;
        builtinsReady = false;
        deleteTexture();
        initBuiltins();
        scanCatalog();
    }

    private static boolean isImage(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")
            || lower.endsWith(".tga") || lower.endsWith(".bmp");
    }

    private static String displayName(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    private static ByteBuffer generateBuiltin(String name) {
        byte[] gray = switch (name) {
            case "Window" -> windowMask();
            case "Blinds" -> blindsMask();
            case "Circle" -> circleMask();
            case "Noise" -> noiseMask();
            default -> throw new IllegalArgumentException("Unknown builtin: " + name);
        };
        ByteBuffer buf = MemoryUtil.memAlloc(gray.length);
        buf.put(gray).flip();
        return buf;
    }

    private static byte[] windowMask() {
        byte[] data = new byte[RES * RES];
        int border = RES / 32;
        int centerThickness = RES / 42;
        int centerStart = RES / 2 - centerThickness / 2;
        int centerEnd = RES / 2 + centerThickness / 2;

        for (int y = 0; y < RES; y++) {
            for (int x = 0; x < RES; x++) {
                boolean isBorder = x < border || x >= RES - border || y < border || y >= RES - border;
                boolean isCross = (x >= centerStart && x < centerEnd) || (y >= centerStart && y < centerEnd);
                data[y * RES + x] = (isBorder || isCross) ? 0 : (byte) 255;
            }
        }
        return data;
    }

    private static byte[] blindsMask() {
        byte[] data = new byte[RES * RES];
        int band = RES / 16;
        for (int y = 0; y < RES; y++) {
            boolean solid = (y / band) % 2 == 0;
            byte val = solid ? (byte) 255 : 0;
            for (int x = 0; x < RES; x++) {
                data[y * RES + x] = val;
            }
        }
        return data;
    }

    private static byte[] circleMask() {
        byte[] data = new byte[RES * RES];
        float cx = (RES - 1) * 0.5f;
        float cy = (RES - 1) * 0.5f;
        float rInner = RES * 0.39f;
        float rOuter = RES * 0.47f;

        for (int y = 0; y < RES; y++) {
            for (int x = 0; x < RES; x++) {
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
                data[y * RES + x] = val;
            }
        }
        return data;
    }

    private static byte[] noiseMask() {
        int grid = 32;
        float[][] gridVals = new float[grid][grid];
        for (int y = 0; y < grid; y++) {
            for (int x = 0; x < grid; x++) {
                gridVals[y][x] = ThreadLocalRandom.current().nextFloat();
            }
        }

        byte[] data = new byte[RES * RES];
        for (int y = 0; y < RES; y++) {
            float gy = (float) y / RES * (grid - 1);
            int yLow = (int) Math.floor(gy);
            int yHigh = Math.min(yLow + 1, grid - 1);
            float yWeight = gy - yLow;

            for (int x = 0; x < RES; x++) {
                float gx = (float) x / RES * (grid - 1);
                int xLow = (int) Math.floor(gx);
                int xHigh = Math.min(xLow + 1, grid - 1);
                float xWeight = gx - xLow;

                float v1 = gridVals[yLow][xLow];
                float v2 = gridVals[yLow][xHigh];
                float v3 = gridVals[yHigh][xLow];
                float v4 = gridVals[yHigh][xHigh];

                float val = (1f - xWeight) * (1f - yWeight) * v1
                    + xWeight * (1f - yWeight) * v2
                    + (1f - xWeight) * yWeight * v3
                    + xWeight * yWeight * v4;
                data[y * RES + x] = (byte) (val * 255f);
            }
        }
        return data;
    }
}
