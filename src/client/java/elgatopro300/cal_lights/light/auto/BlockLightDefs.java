package elgatopro300.cal_lights.light.auto;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.RedstoneWireBlock;

import java.util.IdentityHashMap;
import java.util.Map;

public final class BlockLightDefs {
    public static final class Def {
        public final float r, g, b;
        public final float intensity;
        public final float radius;
        public final boolean shadows;

        Def(float r, float g, float b, float intensity, float radius, boolean shadows) {
            this.r = r;
            this.g = g;
            this.b = b;
            this.intensity = intensity;
            this.radius = radius;
            this.shadows = shadows;
        }
    }

    private static final Map<Block, Def> DEFS = new IdentityHashMap<>();
    private static final Def[] REDSTONE_BY_POWER = new Def[16];

    private BlockLightDefs() {}

    private static void put(Block block, float r, float g, float b, float intensity, float radius) {
        DEFS.put(block, new Def(r, g, b, intensity, radius, true));
    }

    public static Def get(Block block) {
        return DEFS.get(block);
    }

    public static boolean has(Block block) {
        return DEFS.containsKey(block);
    }

    public static Def resolve(BlockState state) {
        if (state.getLuminance() > 0) {
            return DEFS.get(state.getBlock());
        }
        if (state.isOf(Blocks.REDSTONE_WIRE)) {
            int power = state.get(RedstoneWireBlock.POWER);
            return power > 0 ? REDSTONE_BY_POWER[power] : null;
        }
        return null;
    }

    public static boolean paletteCandidate(BlockState state) {
        return state.getLuminance() > 0 || state.isOf(Blocks.REDSTONE_WIRE);
    }

    static {
        // --- torch family ---------------------------------------------------
        put(Blocks.TORCH,                 1.00f, 0.72f, 0.36f, 1.2f, 10f);
        put(Blocks.WALL_TORCH,            1.00f, 0.72f, 0.36f, 1.2f, 10f);
        put(Blocks.SOUL_TORCH,            0.35f, 0.78f, 1.00f, 1.0f,  9f);
        put(Blocks.SOUL_WALL_TORCH,       0.35f, 0.78f, 1.00f, 1.0f,  9f);
        put(Blocks.REDSTONE_TORCH,        1.00f, 0.15f, 0.10f, 0.7f,  6f);
        put(Blocks.REDSTONE_WALL_TORCH,   1.00f, 0.15f, 0.10f, 0.7f,  6f);

        // --- lanterns -------------------------------------------------------
        put(Blocks.LANTERN,               1.00f, 0.78f, 0.50f, 1.3f, 12f);
        put(Blocks.SOUL_LANTERN,          0.40f, 0.80f, 1.00f, 1.1f, 11f);

        // --- full-block emitters --------------------------------------------
        put(Blocks.GLOWSTONE,             1.00f, 0.86f, 0.60f, 1.5f, 14f);
        put(Blocks.SEA_LANTERN,           0.80f, 0.95f, 1.00f, 1.4f, 13f);
        put(Blocks.SHROOMLIGHT,           1.00f, 0.55f, 0.25f, 1.3f, 13f);
        put(Blocks.JACK_O_LANTERN,        1.00f, 0.70f, 0.30f, 1.3f, 13f);
        put(Blocks.REDSTONE_LAMP,         1.00f, 0.76f, 0.42f, 1.3f, 13f);
        put(Blocks.MAGMA_BLOCK,           1.00f, 0.45f, 0.15f, 0.7f,  6f);
        put(Blocks.OCHRE_FROGLIGHT,       1.00f, 0.85f, 0.50f, 1.5f, 15f);
        put(Blocks.VERDANT_FROGLIGHT,     0.80f, 1.00f, 0.70f, 1.5f, 15f);
        put(Blocks.PEARLESCENT_FROGLIGHT, 1.00f, 0.85f, 0.95f, 1.5f, 15f);

        // --- shaped / decorative emitters -----------------------------------
        put(Blocks.END_ROD,               0.95f, 0.95f, 1.00f, 1.2f, 12f);
        put(Blocks.CAMPFIRE,              1.00f, 0.60f, 0.25f, 1.4f, 13f);
        put(Blocks.SOUL_CAMPFIRE,         0.40f, 0.80f, 1.00f, 1.1f, 11f);
        put(Blocks.FIRE,                  1.00f, 0.60f, 0.25f, 1.3f, 12f);
        put(Blocks.SOUL_FIRE,             0.40f, 0.80f, 1.00f, 1.1f, 11f);
        put(Blocks.LAVA,                  1.00f, 0.42f, 0.12f, 1.4f, 12f);
        put(Blocks.BEACON,                0.90f, 0.97f, 1.00f, 1.6f, 16f);
        put(Blocks.CONDUIT,               0.60f, 0.90f, 1.00f, 1.4f, 14f);
        put(Blocks.GLOW_LICHEN,           0.45f, 0.85f, 0.55f, 0.7f,  7f);
        put(Blocks.CRYING_OBSIDIAN,       0.60f, 0.30f, 0.95f, 1.0f, 10f);
        put(Blocks.RESPAWN_ANCHOR,        1.00f, 0.30f, 0.90f, 1.3f, 13f);
        put(Blocks.ENDER_CHEST,           0.25f, 0.75f, 0.72f, 0.7f,  7f);
        put(Blocks.SEA_PICKLE,            0.55f, 0.90f, 0.55f, 0.6f,  6f);

        // --- lit machines ---------------------------------------------------
        put(Blocks.FURNACE,               1.00f, 0.70f, 0.35f, 1.0f, 11f);
        put(Blocks.SMOKER,                1.00f, 0.70f, 0.35f, 1.0f, 11f);
        put(Blocks.BLAST_FURNACE,         1.00f, 0.70f, 0.35f, 1.0f, 11f);

        // --- portals / end --------------------------------------------------
        put(Blocks.NETHER_PORTAL,         0.65f, 0.30f, 0.95f, 1.1f, 11f);
        put(Blocks.END_PORTAL,            0.40f, 0.55f, 0.95f, 1.4f, 14f);
        put(Blocks.END_GATEWAY,           0.40f, 0.55f, 0.95f, 1.4f, 14f);

        // --- candles --------------------------------------------------------
        float cr = 1.00f, cg = 0.70f, cb = 0.35f, ci = 0.8f, crad = 8f;
        put(Blocks.CANDLE,            cr, cg, cb, ci, crad);
        put(Blocks.WHITE_CANDLE,      cr, cg, cb, ci, crad);
        put(Blocks.ORANGE_CANDLE,     cr, cg, cb, ci, crad);
        put(Blocks.MAGENTA_CANDLE,    cr, cg, cb, ci, crad);
        put(Blocks.LIGHT_BLUE_CANDLE, cr, cg, cb, ci, crad);
        put(Blocks.YELLOW_CANDLE,     cr, cg, cb, ci, crad);
        put(Blocks.LIME_CANDLE,       cr, cg, cb, ci, crad);
        put(Blocks.PINK_CANDLE,       cr, cg, cb, ci, crad);
        put(Blocks.GRAY_CANDLE,       cr, cg, cb, ci, crad);
        put(Blocks.LIGHT_GRAY_CANDLE, cr, cg, cb, ci, crad);
        put(Blocks.CYAN_CANDLE,       cr, cg, cb, ci, crad);
        put(Blocks.PURPLE_CANDLE,     cr, cg, cb, ci, crad);
        put(Blocks.BLUE_CANDLE,       cr, cg, cb, ci, crad);
        put(Blocks.BROWN_CANDLE,      cr, cg, cb, ci, crad);
        put(Blocks.GREEN_CANDLE,      cr, cg, cb, ci, crad);
        put(Blocks.RED_CANDLE,        cr, cg, cb, ci, crad);
        put(Blocks.BLACK_CANDLE,      cr, cg, cb, ci, crad);

        for (int p = 1; p <= 15; p++) {
            float t = p / 15f;
            REDSTONE_BY_POWER[p] = new Def(1.0f, 0.06f, 0.0f,
                0.06f + 0.12f * t, 2.0f + 2.5f * t, false);
        }
    }
}
