package org.qualet.irl.light.shadow;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Entity shadow caster for CAL: world entities only, drawn through the vanilla
 * {@link EntityRenderDispatcher}. Installed into the shared {@link ShadowEngine}
 * at client init.
 */
public final class RedactorEntityCasterSource implements ShadowCasterSource {
    private static final double COLLECT_DIST = 72.0;
    private static final double COLLECT_DIST_SQ = COLLECT_DIST * COLLECT_DIST;
    private static final int FULL_LIGHT = LightmapTextureManager.pack(15, 15);

    @Override
    public void collect(ClientWorld world, Vec3d camPos, float tickDelta, OccluderSink sink) {
        double camX = camPos.x;
        double camY = camPos.y;
        double camZ = camPos.z;

        for (Entity entity : world.getEntities()) {
            if (!(entity instanceof LivingEntity) && !(entity instanceof ItemEntity)) {
                continue;
            }

            double ex = MathHelper.lerp(tickDelta, entity.lastRenderX, entity.getX());
            double ey = MathHelper.lerp(tickDelta, entity.lastRenderY, entity.getY());
            double ez = MathHelper.lerp(tickDelta, entity.lastRenderZ, entity.getZ());
            double dx = ex - camX;
            double dy = ey - camY;
            double dz = ez - camZ;
            if (dx * dx + dy * dy + dz * dz > COLLECT_DIST_SQ) {
                continue;
            }

            sink.emitFromBox(entity, CasterType.ENTITY, false, ex, ey, ez, entity.getBoundingBox(), 1f, 0L);
        }
    }

    @Override
    public void emitOccluder(Object caster, int type, float tickDelta, OccluderBatch batch) {
        ImmediateOccluderBatch b = (ImmediateOccluderBatch) batch;
        drawEntity((Entity) caster, b.matrices(), b.immediate(), tickDelta);
    }

    private static void drawEntity(Entity entity, MatrixStack matrices, VertexConsumerProvider.Immediate immediate, float tickDelta) {
        double cx = MathHelper.lerp(tickDelta, entity.lastRenderX, entity.getX());
        double cy = MathHelper.lerp(tickDelta, entity.lastRenderY, entity.getY());
        double cz = MathHelper.lerp(tickDelta, entity.lastRenderZ, entity.getZ());
        float yaw = entity.getYaw(tickDelta);

        EntityRenderDispatcher dispatcher = MinecraftClient.getInstance().getEntityRenderDispatcher();
        if (dispatcher != null)
        {
            dispatcher.render(entity, cx, cy, cz, tickDelta, matrices, immediate, FULL_LIGHT);
        }
    }
}
