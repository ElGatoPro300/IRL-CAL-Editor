package org.qualet.irl.light.shadow;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

public final class RedactorEntityCasterSource implements ShadowCasterSource
{
    private static final double COLLECT_DIST = 72.0;
    private static final double COLLECT_DIST_SQ = COLLECT_DIST * COLLECT_DIST;

    private final Int2ObjectOpenHashMap<Captured> entityGeom = new Int2ObjectOpenHashMap<>();

    private float[] rebase = new float[0];

    private static final class Captured
    {
        final float[] tris;
        final double ax, ay, az;

        Captured(float[] tris, double ax, double ay, double az)
        {
            this.tris = tris;
            this.ax = ax;
            this.ay = ay;
            this.az = az;
        }
    }

    @Override
    public void collect(ClientLevel world, Vec3 camPos, float tickDelta, OccluderSink sink)
    {
        entityGeom.clear();

        double camX = camPos.x, camY = camPos.y, camZ = camPos.z;

        for (Entity entity : world.entitiesForRendering())
        {
            if (!(entity instanceof LivingEntity) && !(entity instanceof ItemEntity))
            {
                continue;
            }

            double ex = Mth.lerp(tickDelta, entity.xOld, entity.getX());
            double ey = Mth.lerp(tickDelta, entity.yOld, entity.getY());
            double ez = Mth.lerp(tickDelta, entity.zOld, entity.getZ());
            double dx = ex - camX, dy = ey - camY, dz = ez - camZ;
            if (dx * dx + dy * dy + dz * dz > COLLECT_DIST_SQ)
            {
                continue;
            }

            sink.emitFromBox(entity, CasterType.ENTITY, false, ex, ey, ez, entity.getBoundingBox(), 1f, 0L);
        }
    }

    @Override
    public void emitOccluder(Object caster, int type, float tickDelta, OccluderBatch batch)
    {
        if (!(caster instanceof Entity))
        {
            return;
        }
        Entity entity = (Entity) caster;

        double pax = ShadowRenderer.currentOriginX();
        double pay = ShadowRenderer.currentOriginY();
        double paz = ShadowRenderer.currentOriginZ();

        Captured cached = entityGeom.get(entity.getId());
        if (cached == null)
        {
            float[] tris = OccluderGeometryCapturer.captureEntityTris(entity, tickDelta);
            cached = new Captured(tris, pax, pay, paz);
            entityGeom.put(entity.getId(), cached);
        }
        if (cached.tris.length == 0)
        {
            return;
        }

        if (pax == cached.ax && pay == cached.ay && paz == cached.az)
        {
            ((RawOccluderBatch) batch).append(cached.tris);
            return;
        }
        float dx = (float) (cached.ax - pax);
        float dy = (float) (cached.ay - pay);
        float dz = (float) (cached.az - paz);
        float[] src = cached.tris;
        int n = src.length;
        if (rebase.length < n)
        {
            rebase = new float[n];
        }
        float[] dst = rebase;
        for (int i = 0; i + 3 <= n; i += 3)
        {
            dst[i]     = src[i]     + dx;
            dst[i + 1] = src[i + 1] + dy;
            dst[i + 2] = src[i + 2] + dz;
        }
        ((RawOccluderBatch) batch).append(dst, 0, n);
    }
}
