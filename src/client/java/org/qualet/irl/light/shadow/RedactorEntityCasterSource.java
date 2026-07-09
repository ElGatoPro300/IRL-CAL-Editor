package org.qualet.irl.light.shadow;

import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

public final class RedactorEntityCasterSource implements ShadowCasterSource
{
    /** Max distance (from the camera) at which an entity is considered a caster. */
    private static final double COLLECT_DIST = 72.0;
    private static final double COLLECT_DIST_SQ = COLLECT_DIST * COLLECT_DIST;

    private final Int2ObjectOpenHashMap<float[]> entityGeom = new Int2ObjectOpenHashMap<>();

    @Override
    public void collect(ClientWorld world, Vec3d camPos, float tickDelta, OccluderSink sink)
    {
        // New bake: drop the previous bake's captured geometry so a moved / re-posed
        // entity is re-captured this bake (keyed by entity id, reused across passes).
        entityGeom.clear();

        double camX = camPos.x, camY = camPos.y, camZ = camPos.z;

        // --- world entities (real model capture path) ---
        for (Entity entity : world.getEntities())
        {
            if (!(entity instanceof LivingEntity) && !(entity instanceof ItemEntity))
            {
                continue;
            }

            double ex = MathHelper.lerp(tickDelta, entity.lastRenderX, entity.getX());
            double ey = MathHelper.lerp(tickDelta, entity.lastRenderY, entity.getY());
            double ez = MathHelper.lerp(tickDelta, entity.lastRenderZ, entity.getZ());
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

        float[] tris = entityGeom.get(entity.getId());
        if (tris == null)
        {
            tris = OccluderGeometryCapturer.captureEntityTris(entity, tickDelta);
            entityGeom.put(entity.getId(), tris);
        }
        if (tris.length == 0)
        {
            return;
        }
        ((RawOccluderBatch) batch).append(tris);
    }
}
