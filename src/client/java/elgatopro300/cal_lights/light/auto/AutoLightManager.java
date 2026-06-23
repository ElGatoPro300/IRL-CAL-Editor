package elgatopro300.cal_lights.light.auto;

import elgatopro300.cal_lights.light.IrisShadersState;
import elgatopro300.cal_lights.light.LightConfig;
import elgatopro300.cal_lights.light.PlacedLight;

import net.minecraft.block.BlockState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectIterator;

public final class AutoLightManager {
    private static final Predicate<BlockState> EMISSIVE = BlockLightDefs::paletteCandidate;

    private static final int CHUNKS_PER_TICK = 12;
    private static final int SECTIONS_PER_TICK = 16;

    private static final Long2ObjectOpenHashMap<PlacedLight> byPos = new Long2ObjectOpenHashMap<>();
    private static final LongOpenHashSet seenThisPass = new LongOpenHashSet();
    private static final List<PlacedLight> feed = new ArrayList<>();

    // --- rolling-pass cursor / parameters ---
    private static boolean passActive;
    private static double passCx, passCy, passCz;
    private static float passR2;
    private static int passMinX, passMaxX, passMinY, passMaxY, passMinZ, passMaxZ;
    private static int passMinChunkX, passMinChunkZ, passSpanX, passSpanZ;
    private static int passChunkIdx;

    private AutoLightManager() {}

    public static int count() {
        return byPos.size();
    }

    public static void clear() {
        byPos.clear();
        seenThisPass.clear();
        feed.clear();
        passActive = false;
        passChunkIdx = 0;
    }

    public static void tick(ClientWorld world, double centerX, double centerY, double centerZ) {
        if (!LightConfig.autoLights() || world == null) {
            if (!byPos.isEmpty() || passActive) {
                clear();
            }
            return;
        }
        if (IrisShadersState.shadersDisabled()) {
            return;
        }

        if (!passActive) {
            startPass(centerX, centerY, centerZ);
        }
        stepPass(world);
    }

    private static void startPass(double centerX, double centerY, double centerZ) {
        int radius = Math.max(1, LightConfig.autoLightRadius());
        passCx = centerX;
        passCy = centerY;
        passCz = centerZ;
        passR2 = (float) radius * radius;

        passMinX = (int) Math.floor(centerX - radius);
        passMaxX = (int) Math.floor(centerX + radius);
        passMinY = (int) Math.floor(centerY - radius);
        passMaxY = (int) Math.floor(centerY + radius);
        passMinZ = (int) Math.floor(centerZ - radius);
        passMaxZ = (int) Math.floor(centerZ + radius);

        passMinChunkX = passMinX >> 4;
        passMinChunkZ = passMinZ >> 4;
        passSpanX = (passMaxX >> 4) - passMinChunkX + 1;
        passSpanZ = (passMaxZ >> 4) - passMinChunkZ + 1;

        passChunkIdx = 0;
        seenThisPass.clear();
        passActive = true;
    }

    private static void stepPass(ClientWorld world) {
        int total = passSpanX * passSpanZ;
        int chunksThisTick = 0;
        int sectionsThisTick = 0;

        while (passChunkIdx < total
            && chunksThisTick < CHUNKS_PER_TICK
            && sectionsThisTick < SECTIONS_PER_TICK) {
            int idx = passChunkIdx++;
            chunksThisTick++;

            int chunkX = passMinChunkX + (idx % passSpanX);
            int chunkZ = passMinChunkZ + (idx / passSpanX);
            WorldChunk chunk = world.getChunkManager().getWorldChunk(chunkX, chunkZ, false);
            if (chunk == null) {
                continue;
            }
            sectionsThisTick += scanChunk(chunk, chunkX, chunkZ);
        }

        if (passChunkIdx >= total) {
            if (!byPos.isEmpty()) {
                ObjectIterator<Long2ObjectMap.Entry<PlacedLight>> it = byPos.long2ObjectEntrySet().iterator();
                while (it.hasNext()) {
                    if (!seenThisPass.contains(it.next().getLongKey())) {
                        it.remove();
                    }
                }
            }
            passActive = false;
        }
    }

    private static int scanChunk(WorldChunk chunk, int chunkX, int chunkZ) {
        ChunkSection[] sections = chunk.getSectionArray();
        int bottomY = chunk.getBottomY();
        int baseX = chunkX << 4;
        int baseZ = chunkZ << 4;
        int fullScanned = 0;

        for (int s = 0; s < sections.length; s++) {
            ChunkSection sec = sections[s];
            if (sec == null || sec.isEmpty()) {
                continue;
            }

            int secMinY = bottomY + (s << 4);
            if (secMinY + 15 < passMinY || secMinY > passMaxY) {
                continue;
            }
            if (!sec.hasAny(EMISSIVE)) {
                continue;
            }
            fullScanned++;

            int ly0 = Math.max(0, passMinY - secMinY);
            int ly1 = Math.min(15, passMaxY - secMinY);
            for (int ly = ly0; ly <= ly1; ly++) {
                int wy = secMinY + ly;
                double dy = (wy + 0.5) - passCy;
                double dy2 = dy * dy;

                for (int lx = 0; lx < 16; lx++) {
                    int wx = baseX + lx;
                    if (wx < passMinX || wx > passMaxX) {
                        continue;
                    }
                    double dx = (wx + 0.5) - passCx;
                    double dxy2 = dx * dx + dy2;
                    if (dxy2 > passR2) {
                        continue;
                    }

                    for (int lz = 0; lz < 16; lz++) {
                        int wz = baseZ + lz;
                        if (wz < passMinZ || wz > passMaxZ) {
                            continue;
                        }
                        double dz = (wz + 0.5) - passCz;
                        if (dxy2 + dz * dz > passR2) {
                            continue;
                        }

                        BlockState state = sec.getBlockState(lx, ly, lz);
                        BlockLightDefs.Def def = BlockLightDefs.resolve(state);
                        if (def == null) {
                            continue;
                        }

                        long key = BlockPos.asLong(wx, wy, wz);
                        seenThisPass.add(key);
                        upsert(key, wx, wy, wz, def);
                    }
                }
            }
        }
        return fullScanned;
    }

    public static List<PlacedLight> nearest(Vec3d cameraPos, int max) {
        feed.clear();
        if (byPos.isEmpty() || cameraPos == null || max <= 0) {
            return feed;
        }

        for (PlacedLight l : byPos.values()) {
            feed.add(l);
        }

        final double cx = cameraPos.x, cy = cameraPos.y, cz = cameraPos.z;
        feed.sort((a, b) -> Double.compare(dist2(a, cx, cy, cz), dist2(b, cx, cy, cz)));

        if (feed.size() > max) {
            feed.subList(max, feed.size()).clear();
        }
        return feed;
    }

    private static double dist2(PlacedLight l, double cx, double cy, double cz) {
        double dx = l.x - cx, dy = l.y - cy, dz = l.z - cz;
        return dx * dx + dy * dy + dz * dz;
    }

    private static void upsert(long key, int wx, int wy, int wz, BlockLightDefs.Def def) {
        PlacedLight l = byPos.get(key);
        if (l == null) {
            l = PlacedLight.point();
            l.name = "auto";
            byPos.put(key, l);
        }
        l.x = wx + 0.5;
        l.y = wy + 0.5;
        l.z = wz + 0.5;
        l.r = def.r;
        l.g = def.g;
        l.b = def.b;
        l.intensity = def.intensity * LightConfig.autoLightIntensity();
        l.radius = def.radius * LightConfig.autoLightReach();
        l.autoShadowEligible = def.shadows;
    }
}
