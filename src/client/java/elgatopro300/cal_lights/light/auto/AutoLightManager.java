package elgatopro300.cal_lights.light.auto;

import elgatopro300.cal_lights.light.LightConfig;
import elgatopro300.cal_lights.light.PlacedLight;

import org.qualet.irl.light.iris.IrisShadersState;

import net.minecraft.block.Block;
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
    private static final int MERGE_MIN_SAME = 3;
    private static final double FEED_RESORT_DIST2 = 4.0;
    private static final int[] FX = {1, -1, 0, 0, 0, 0};
    private static final int[] FY = {0, 0, 1, -1, 0, 0};
    private static final int[] FZ = {0, 0, 0, 0, 1, -1};

    private static final Long2ObjectOpenHashMap<PlacedLight> byPos = new Long2ObjectOpenHashMap<>();
    private static final LongOpenHashSet seenThisPass = new LongOpenHashSet();
    private static final LongOpenHashSet claimedCellsThisPass = new LongOpenHashSet();
    private static final List<PlacedLight> feed = new ArrayList<>();
    private static final BlockPos.Mutable NB = new BlockPos.Mutable();

    private static int setGeneration;
    private static int feedGeneration = Integer.MIN_VALUE;
    private static int feedMax = -1;
    private static int lastActiveCount;
    private static double feedCamX, feedCamY, feedCamZ;

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

    public static int activeCount() {
        return lastActiveCount;
    }

    public static void clear() {
        byPos.clear();
        seenThisPass.clear();
        claimedCellsThisPass.clear();
        feed.clear();
        passActive = false;
        passChunkIdx = 0;
        setGeneration++;
        feedGeneration = Integer.MIN_VALUE;
        feedMax = -1;
        lastActiveCount = 0;
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
        claimedCellsThisPass.clear();
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
            sectionsThisTick += scanChunk(world, chunk, chunkX, chunkZ);
        }

        if (passChunkIdx >= total) {
            if (!byPos.isEmpty()) {
                ObjectIterator<Long2ObjectMap.Entry<PlacedLight>> it = byPos.long2ObjectEntrySet().iterator();
                while (it.hasNext()) {
                    if (!seenThisPass.contains(it.next().getLongKey())) {
                        it.remove();
                        setGeneration++;
                    }
                }
            }
            passActive = false;
        }
    }

    private static int scanChunk(ClientWorld world, WorldChunk chunk, int chunkX, int chunkZ) {
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

                        if (def.mergeCells > 1) {
                            long swept = neighborSweep(world, sec, state.getBlock(), lx, ly, lz, wx, wy, wz);
                            if (LightConfig.autoLightCulling() && (swept & 1L) == 0L) {
                                continue;
                            }
                            if ((int) (swept >>> 1) >= MERGE_MIN_SAME) {
                                long cellKey = BlockPos.asLong(
                                    Math.floorDiv(wx, def.mergeCells),
                                    Math.floorDiv(wy, def.mergeCells),
                                    Math.floorDiv(wz, def.mergeCells));
                                if (!claimedCellsThisPass.add(cellKey)) {
                                    continue;
                                }
                            }
                        } else if (LightConfig.autoLightCulling()
                            && !isExposed(world, sec, lx, ly, lz, wx, wy, wz)) {
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

    private static boolean isExposed(ClientWorld world, ChunkSection sec,
                                     int lx, int ly, int lz,
                                     int wx, int wy, int wz) {
        return isOpen(world, sec, lx + 1, ly, lz, wx + 1, wy, wz)
            || isOpen(world, sec, lx - 1, ly, lz, wx - 1, wy, wz)
            || isOpen(world, sec, lx, ly + 1, lz, wx, wy + 1, wz)
            || isOpen(world, sec, lx, ly - 1, lz, wx, wy - 1, wz)
            || isOpen(world, sec, lx, ly, lz + 1, wx, wy, wz + 1)
            || isOpen(world, sec, lx, ly, lz - 1, wx, wy, wz - 1);
    }

    private static boolean isOpen(ClientWorld world, ChunkSection sec,
                                  int lx, int ly, int lz,
                                  int wx, int wy, int wz) {
        NB.set(wx, wy, wz);
        BlockState n = inSection(lx, ly, lz) ? sec.getBlockState(lx, ly, lz) : world.getBlockState(NB);
        return isOpenState(world, n, NB);
    }

    private static boolean inSection(int lx, int ly, int lz) {
        return (lx | ly | lz) >= 0 && lx < 16 && ly < 16 && lz < 16;
    }

    private static boolean isOpenState(ClientWorld world, BlockState n, BlockPos pos) {
        if (BlockLightDefs.resolve(n) != null) {
            return false;
        }
        return !n.isOpaqueFullCube(world, pos);
    }

    private static long neighborSweep(ClientWorld world, ChunkSection sec, Block self,
                                      int lx, int ly, int lz, int wx, int wy, int wz) {
        boolean exposed = false;
        int same = 0;
        for (int f = 0; f < 6; f++) {
            int nlx = lx + FX[f];
            int nly = ly + FY[f];
            int nlz = lz + FZ[f];
            NB.set(wx + FX[f], wy + FY[f], wz + FZ[f]);
            BlockState n = inSection(nlx, nly, nlz) ? sec.getBlockState(nlx, nly, nlz) : world.getBlockState(NB);
            if (n.getBlock() == self) {
                same++;
            }
            if (!exposed && isOpenState(world, n, NB)) {
                exposed = true;
            }
        }
        return (exposed ? 1L : 0L) | ((long) same << 1);
    }

    public static List<PlacedLight> nearest(Vec3d cameraPos, int max) {
        if (byPos.isEmpty() || cameraPos == null || max <= 0) {
            feed.clear();
            feedGeneration = setGeneration;
            feedMax = max;
            lastActiveCount = 0;
            return feed;
        }

        final double cx = cameraPos.x;
        final double cy = cameraPos.y;
        final double cz = cameraPos.z;
        double dcx = cx - feedCamX;
        double dcy = cy - feedCamY;
        double dcz = cz - feedCamZ;
        boolean camMoved = dcx * dcx + dcy * dcy + dcz * dcz > FEED_RESORT_DIST2;

        if (setGeneration == feedGeneration && max == feedMax && !camMoved) {
            lastActiveCount = feed.size();
            return feed;
        }

        feed.clear();
        for (PlacedLight l : byPos.values()) {
            feed.add(l);
        }
        feed.sort((a, b) -> Double.compare(dist2(a, cx, cy, cz), dist2(b, cx, cy, cz)));
        if (feed.size() > max) {
            feed.subList(max, feed.size()).clear();
        }

        feedGeneration = setGeneration;
        feedMax = max;
        feedCamX = cx;
        feedCamY = cy;
        feedCamZ = cz;
        lastActiveCount = feed.size();
        return feed;
    }

    private static double dist2(PlacedLight l, double cx, double cy, double cz) {
        double dx = l.x - cx;
        double dy = l.y - cy;
        double dz = l.z - cz;
        return dx * dx + dy * dy + dz * dz;
    }

    private static void upsert(long key, int wx, int wy, int wz, BlockLightDefs.Def def) {
        PlacedLight l = byPos.get(key);
        if (l == null) {
            l = PlacedLight.point();
            l.name = "auto";
            byPos.put(key, l);
            setGeneration++;
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
