package elgatopro300.cal_lights.shaders;

import elgatopro300.cal_lights.manager.LightInstance;
import elgatopro300.cal_lights.manager.LightManager;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;

import org.lwjgl.opengl.GL43;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * VoxelShadowSSBO — binding 8
 *
 * Sube al GPU una grilla de vóxeles (bitfield) por cada luz CAL con
 * shadowEnabled=true. El shader usa DDA (Amanatides & Woo) para trazar
 * rayos a través de la grilla y detectar bloques sólidos entre el
 * fragmento y la fuente de luz.
 *
 * std430 binding 8:
 *   Header (16 bytes):
 *     int shadowLightCount
 *     int gridSize    (= 2*GRID_RADIUS+1 = 41)
 *     int pad0, pad1
 *   Por luz (hasta MAX_SHADOW_LIGHTS = 4):
 *     vec4 center_radius  → xyz=pos mundo, w=GRID_RADIUS (20)
 *     vec4 light_ref      → x=lightIdx, y=type(0=point/1=spot), zw=0
 *     uint voxels[2154]   → bitfield: 1=bloque sólido (8616 bytes)
 *     uint vpad[2]        → relleno a múltiplo de 16 (8 bytes)
 *
 *   Struct total = 32 + 8616 + 8 = 8656 bytes (= 541 × 16 ✓)
 *   Total buffer = 16 + 4 × 8656 = 34640 bytes ≈ 34 KB
 */
public class VoxelShadowSSBO {

    public static final int BINDING_POINT     = 8;
    public static final int MAX_SHADOW_LIGHTS = 4;

    // Dimensiones de la grilla (bloques). Debe coincidir con el shader GLSL.
    public static final int GRID_RADIUS = 20;
    public static final int GRID_SIZE   = 2 * GRID_RADIUS + 1;                 // 41
    public static final int GRID_VOXELS = GRID_SIZE * GRID_SIZE * GRID_SIZE;   // 68 921
    public static final int GRID_UINTS  = (GRID_VOXELS + 31) / 32;             // 2 154

    // Tamaños del buffer (bytes)
    private static final int HEADER_BYTES         = 16;
    private static final int GRID_VEC4S_BYTES     = 32;                        // 2 × vec4
    private static final int GRID_VOXELS_BYTES    = GRID_UINTS * 4;            // 8 616
    private static final int GRID_RAW_BYTES       = GRID_VEC4S_BYTES + GRID_VOXELS_BYTES; // 8 648
    private static final int GRID_ALIGNED_BYTES   = (GRID_RAW_BYTES + 15) & ~15;          // 8 656
    private static final int GRID_PAD_INTS        = (GRID_ALIGNED_BYTES - GRID_RAW_BYTES) / 4; // 2
    private static final int TOTAL_BYTES          = HEADER_BYTES + MAX_SHADOW_LIGHTS * GRID_ALIGNED_BYTES;

    private static int        ssboId = -1;
    private static ByteBuffer buffer = null;

    // Temporal reutilizable para evitar allocar 68 921 objetos BlockPos cada frame
    private static final int[] voxelBits = new int[GRID_UINTS];

    // -------------------------------------------------------------------------

    /** Inicializa el SSBO con ceros. Llamar una vez antes del primer frame. */
    public static void init() {
        if (ssboId != -1) return;
        ssboId = GL43.glGenBuffers();
        buffer = MemoryUtil.memAlloc(TOTAL_BYTES);
        // Llenar con ceros para que shadowLightCount = 0 desde el primer frame
        for (int i = 0; i < TOTAL_BYTES; i++) buffer.put(i, (byte) 0);
        GL43.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, ssboId);
        GL43.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, buffer, GL43.GL_DYNAMIC_DRAW);
        GL43.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, BINDING_POINT, ssboId);
        GL43.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, 0);
    }

    /**
     * Construye y sube el SSBO de vóxeles.
     * Debe llamarse cada frame desde el hilo de render (BEFORE_ENTITIES).
     */
    public static void upload() {
        init(); // no-op si ya está inicializado

        MinecraftClient mc    = MinecraftClient.getInstance();
        ClientWorld     world = mc.world;

        // Recopilar luces visibles en el mismo orden que LightSSBO
        List<LightInstance> pointLights = new ArrayList<>();
        for (LightInstance l : LightManager.INSTANCE.getPointLights()) {
            if (l.visible) pointLights.add(l);
        }

        List<LightInstance> spotLights = new ArrayList<>();
        for (LightInstance l : LightManager.INSTANCE.getSpotLights()) {
            if (l.visible) spotLights.add(l);
        }

        // Recopilar focos con sombra habilitada (hasta MAX_SHADOW_LIGHTS)
        // Guardamos: [ssboIndex, type(0=point/1=spot)]
        List<int[]> shadowEntries = new ArrayList<>();

        for (int i = 0; i < Math.min(pointLights.size(), 64) && shadowEntries.size() < MAX_SHADOW_LIGHTS; i++) {
            if (pointLights.get(i).shadowEnabled)
                shadowEntries.add(new int[]{i, 0});
        }
        for (int i = 0; i < Math.min(spotLights.size(), 64) && shadowEntries.size() < MAX_SHADOW_LIGHTS; i++) {
            if (spotLights.get(i).shadowEnabled)
                shadowEntries.add(new int[]{i, 1});
        }

        buffer.clear();

        // ── Header ────────────────────────────────────────────────────────────
        buffer.putInt(shadowEntries.size()); // shadowLightCount
        buffer.putInt(GRID_SIZE);            // gridSize (debug / validación)
        buffer.putInt(0);                    // pad0
        buffer.putInt(0);                    // pad1

        // ── Grillas ───────────────────────────────────────────────────────────
        for (int g = 0; g < MAX_SHADOW_LIGHTS; g++) {
            if (g < shadowEntries.size() && world != null) {
                int[]        entry     = shadowEntries.get(g);
                int          lightIdx  = entry[0];
                int          lightType = entry[1];
                LightInstance light    = (lightType == 0)
                                         ? pointLights.get(lightIdx)
                                         : spotLights.get(lightIdx);

                float cx = light.getShaderX();
                float cy = light.getShaderY();
                float cz = light.getShaderZ();

                // vec4 center_radius
                buffer.putFloat(cx);
                buffer.putFloat(cy);
                buffer.putFloat(cz);
                buffer.putFloat((float) GRID_RADIUS);

                // vec4 light_ref
                buffer.putFloat((float) lightIdx);
                buffer.putFloat((float) lightType);
                buffer.putFloat(0f);
                buffer.putFloat(0f);

                // Bitfield de vóxeles
                buildVoxelGrid(world, cx, cy, cz);
                for (int v : voxelBits) buffer.putInt(v);

                // Padding std430
                for (int p = 0; p < GRID_PAD_INTS; p++) buffer.putInt(0);

            } else {
                // Slot vacío: relleno con ceros
                for (int b = 0; b < GRID_ALIGNED_BYTES / 4; b++) buffer.putFloat(0f);
            }
        }

        buffer.flip();

        GL43.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, ssboId);
        GL43.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, buffer, GL43.GL_DYNAMIC_DRAW);
        GL43.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, BINDING_POINT, ssboId);
        GL43.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, 0);
    }

    /**
     * Rellena {@code voxelBits} con el bitfield de bloques sólidos centrado
     * en (cx, cy, cz) con radio GRID_RADIUS bloques.
     */
    private static void buildVoxelGrid(ClientWorld world, float cx, float cy, float cz) {
        // Limpiar el bitfield
        for (int i = 0; i < GRID_UINTS; i++) voxelBits[i] = 0;

        int startX = (int) Math.floor(cx) - GRID_RADIUS;
        int startY = (int) Math.floor(cy) - GRID_RADIUS;
        int startZ = (int) Math.floor(cz) - GRID_RADIUS;

        BlockPos.Mutable mpos = new BlockPos.Mutable();

        for (int dx = 0; dx < GRID_SIZE; dx++) {
            for (int dy = 0; dy < GRID_SIZE; dy++) {
                for (int dz = 0; dz < GRID_SIZE; dz++) {
                    mpos.set(startX + dx, startY + dy, startZ + dz);

                    // Verificar si el chunk está cargado para evitar falsos positivos
                    if (!world.isChunkLoaded(mpos.getX() >> 4, mpos.getZ() >> 4)) continue;

                    BlockState state = world.getBlockState(mpos);

                    if (isShadowCaster(world, mpos, state)) {
                        int idx = dx
                                + dy * GRID_SIZE
                                + dz * GRID_SIZE * GRID_SIZE;
                        voxelBits[idx >> 5] |= (1 << (idx & 31));
                    }
                }
            }
        }
    }

    /**
     * Decide si un bloque debe proyectar sombra vóxel.
     *
     * Cubre:
     *  • Bloques opacos completos (piedra, madera, tierra…)  → isOpaqueFullCube
     *  • Agua y lava                                          → fluidState no vacío
     *  • Cristal, vidrio teñido, hielo…                      → colisionShape lleno
     *  • Losas, escaleras, muros, vallas…                    → colisionShape no vacío
     */
    private static boolean isShadowCaster(ClientWorld world, BlockPos pos, BlockState state) {
        if (state.isAir()) return false;
        // Bloques opacos sólidos (el caso más común y rápido)
        if (state.isOpaqueFullCube(world, pos)) return true;
        // Fluidos: agua, lava (su colisionShape es vacío, los tratamos aparte)
        if (!state.getFluidState().isEmpty()) return true;
        // Bloques con colisión: cristal, vidrio teñido, losas, escaleras, muros…
        VoxelShape collision = state.getCollisionShape(world, pos);
        return !collision.isEmpty();
    }

    public static void delete() {
        if (ssboId != -1) {
            GL43.glDeleteBuffers(ssboId);
            ssboId = -1;
        }
        if (buffer != null) {
            MemoryUtil.memFree(buffer);
            buffer = null;
        }
    }
}
