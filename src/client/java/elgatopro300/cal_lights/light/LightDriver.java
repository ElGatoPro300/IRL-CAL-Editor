package elgatopro300.cal_lights.light;

import elgatopro300.cal_lights.light.auto.AutoLightManager;
import elgatopro300.cal_lights.light.cookie.CookieArray;
import elgatopro300.cal_lights.manager.LightInstance;
import elgatopro300.cal_lights.manager.LightManager;

import org.qualet.irl.light.FramePipeline;
import org.qualet.irl.light.LightBuffer;
import org.qualet.irl.light.LightMath;
import org.qualet.irl.light.LightRegistry;
import org.qualet.irl.light.VlGlobalsBuffer;
import org.qualet.irl.light.shadow.PointDepthAtlas;
import org.qualet.irl.light.shadow.ShadowBaker;

import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Vec3d;

import java.util.List;

/**
 * Feeds CAL's managed lights (manual + auto) into {@link LightRegistry} each
 * frame. Called by {@link FramePipeline} before the shared
 * {@link ShadowBaker} runs.
 */
public final class LightDriver {
    private static final int AUTO_SHADOW_RAMP_STEP = 2;
    private static int autoShadowRamp;

    private LightDriver() {}

    public static void resetAutoShadowRamp() {
        autoShadowRamp = 0;
    }

    public static void collect(ClientWorld world, Vec3d cameraPos, float tickDelta) {
        // Track the global-VL knobs each frame: they land in the globals UBO
        // (binding 7) on the SSBO upload, so UBO-era shader patches read every
        // VL number and flag live without a recompile.
        VlGlobalsBuffer.set(
            LightConfig.vlIntensity(),
            LightConfig.vlMaxDist(),
            LightConfig.vlTipBoost(),
            LightConfig.vlTipRadius(),
            LightConfig.vlNoiseAmount(),
            LightConfig.vlNoiseScale(),
            LightConfig.vlNoiseSpeed(),
            LightConfig.vlNoiseMorph(),
            LightConfig.vlSteps(),
            LightConfig.vlShadowStride(),
            LightConfig.vlNoiseStride(),
            (LightConfig.vlShadows() ? 1 : 0) | (LightConfig.vlNoise() ? 2 : 0)
                | (LightConfig.vlBlueNoise() ? 4 : 0) | (LightConfig.vlDitherTemporal() ? 8 : 0)
                | (LightConfig.vlClusterCull() ? 16 : 0) | (LightConfig.vlShadowHiz() ? 32 : 0)
                | 64);   // bit6 = depth-aware bilateral VL upsample, always on (no UI knob)

        // Outline knobs -> globals UBO (also live)
        VlGlobalsBuffer.setOutline(
            LightConfig.outline, LightConfig.outlineTarget, LightConfig.outlineStrength,
            LightConfig.outlineFresnelPower, LightConfig.outlineBack, LightConfig.outlineFront,
            LightConfig.outlineFrontStrength, LightConfig.outlineGlow, LightConfig.outlineGlowStrength,
            LightConfig.outlinePixelSize);

        // Live shadow knobs (master enable + default penumbra width) -> globals UBO
        VlGlobalsBuffer.setShadow(LightConfig.shadowsLive, LightConfig.shadowSoftness);

        if (world == null || cameraPos == null) {
            return;
        }

        int manualShadowPoints = 0;

        for (LightInstance l : LightManager.INSTANCE.getPointLights()) {
            if (!l.visible) {
                continue;
            }
            emitPoint(l);
            if (l.shadowEnabled) {
                manualShadowPoints++;
            }
        }

        for (LightInstance l : LightManager.INSTANCE.getSpotLights()) {
            if (l.visible) {
                emitSpot(l);
            }
        }

        if (LightConfig.autoLights()) {
            int headroom = Math.max(0, LightBuffer.MAX_LIGHTS - LightRegistry.getCount());
            int feedMax = Math.min(LightConfig.autoLightMax(), headroom);

            autoShadowRamp = Math.min(PointDepthAtlas.blockCount(), autoShadowRamp + AUTO_SHADOW_RAMP_STEP);
            int shadowBudget = LightConfig.autoLightShadows()
                ? Math.min(autoShadowRamp, Math.max(0, PointDepthAtlas.blockCount() - manualShadowPoints))
                : 0;

            List<PlacedLight> autos = AutoLightManager.nearest(cameraPos, feedMax);
            int granted = 0;
            for (PlacedLight l : autos) {
                if (l == null) {
                    continue;
                }
                boolean wantShadow = l.autoShadowEligible && granted < shadowBudget;
                l.shadows = wantShadow;
                if (wantShadow) {
                    granted++;
                }
                emitAutoPoint(l);
            }
        } else {
            autoShadowRamp = 0;
        }
    }

    private static void emitPoint(LightInstance l) {
        float beamStrength = l.fogEnabled ? l.beamStrength : 0.0f;
        LightRegistry.registerPoint(
            l.x, l.y, l.z,
            l.r, l.g, l.b,
            l.intensity, l.radius,
            l.entitiesOnly, l.blocksOnly,
            l.anisotropy, l.vlDensity, beamStrength, l.bulbSize,
            l.shadowEnabled && !LightConfig.holdBake, (long) l.id
        );
    }

    private static void emitSpot(LightInstance l) {
        float[] dir = LightMath.normalizeDir(l.dx, l.dy, l.dz, 0f, -1f, 0f, new float[3]);
        LightMath.Cone cone = LightMath.cone(l.getOuterAngleDeg(), l.getInnerAngleDeg());

        int cookieLayer = CookieArray.resolve(l.goboName);
        float cookieRot = (float) Math.toRadians(l.goboRotation);
        float cookieFlags = l.cookieInvert ? 1.0f : 0.0f;
        float beamStrength = l.fogEnabled ? l.beamStrength : 0.0f;

        LightRegistry.registerSpot(
            l.x, l.y, l.z,
            dir[0], dir[1], dir[2],
            l.r, l.g, l.b,
            l.intensity, l.distance, cone.cosOuter(), cone.cosInner(),
            l.entitiesOnly, l.blocksOnly,
            l.anisotropy, l.vlDensity, beamStrength, l.bulbSize,
            l.shadowEnabled && !LightConfig.holdBake,
            (float) cookieLayer, cookieRot, l.cookieScale, cookieFlags,
            (long) l.id
        );
    }

    private static void emitAutoPoint(PlacedLight l) {
        LightRegistry.registerPoint(
            l.x, l.y, l.z,
            l.r, l.g, l.b,
            l.intensity, l.radius,
            l.entitiesOnly, l.blocksOnly,
            l.anisotropy, l.vlDensity, l.beamStrength, l.bulbSize,
            l.shadows && !LightConfig.holdBake, l.id
        );
    }
}
