package elgatopro300.cal_lights.light;

import org.qualet.irl.light.shadow.ShadowConfig;

public final class LightConfig {
    public static final ShadowConfig SHADOW = ShadowConfig.builder()
        .shadowQuality(LightConfig::shadowQuality)
        .shadowCache(LightConfig::shadowCache)
        .shadowBakeBudget(LightConfig::shadowBakeBudget)
        .shadowBlocks(LightConfig::shadowBlocks)
        .shadowBlockRadius(LightConfig::shadowBlockRadius)
        .build();

    public static boolean showGuides = false;
    public static volatile boolean holdBake = false;
    public static boolean holdBakeOnJoin = false;
    public static int shadowQuality = 1;
    public static boolean shadowCache = true;
    public static boolean shadowBlocks = true;
    public static int shadowBlockRadius = 24;
    public static int shadowBakeBudget = 4;
    public static boolean shadowsLive = true;
    public static float shadowSoftness = 0.10f;

    // --- Auto block-lights ---
    public static boolean autoLights = false;
    public static boolean autoLightCulling = true;
    public static boolean autoLightShadows = false;
    public static float autoLightIntensity = 1.0f;
    public static float autoLightReach = 1.0f;
    public static int autoLightRadius = 48;
    public static int autoLightMax = 200;

    // --- Global volumetrics (live) ---
    public static float vlIntensity = 1.0f;
    public static int vlSteps = 48;
    public static float vlMaxDist = 96.0f;
    public static boolean vlShadows = true;
    public static int vlShadowStride = 2;
    public static float vlTipBoost = 1.5f;
    public static float vlTipRadius = 1.5f;
    public static boolean vlNoise = true;
    public static float vlNoiseAmount = 0.6f;
    public static float vlNoiseScale = 2.0f;
    public static float vlNoiseSpeed = 0.25f;
    public static float vlNoiseMorph = 0.0f;
    public static int vlNoiseStride = 2;
    public static boolean vlBlueNoise = true;
    public static boolean vlDitherTemporal = true;
    public static boolean vlClusterCull = true;
    public static boolean vlShadowHiz = true;

    // --- Outline (live) ---
    public static boolean outline = true;
    public static int outlineTarget = 1;
    public static float outlineStrength = 0.65f;
    public static int outlinePixelSize = 6;
    public static float outlineFresnelPower = 2.2f;
    public static float outlineBack = 1.0f;
    public static boolean outlineFront = false;
    public static float outlineFrontStrength = 0.3f;
    public static boolean outlineGlow = false;
    public static float outlineGlowStrength = 0.12f;

    private LightConfig() {}

    public static boolean showGuides() {
        return showGuides;
    }

    public static boolean shadowCache() {
        return shadowCache;
    }

    public static boolean shadowBlocks() {
        return shadowBlocks;
    }

    public static int shadowQuality() {
        return shadowQuality;
    }

    public static int shadowBlockRadius() {
        return shadowBlockRadius;
    }

    public static int shadowBakeBudget() {
        return shadowBakeBudget;
    }

    public static boolean autoLights() {
        return autoLights;
    }

    public static boolean autoLightCulling() {
        return autoLightCulling;
    }

    public static boolean autoLightShadows() {
        return autoLightShadows;
    }

    public static float autoLightIntensity() {
        return autoLightIntensity;
    }

    public static float autoLightReach() {
        return autoLightReach;
    }

    public static int autoLightRadius() {
        return autoLightRadius;
    }

    public static int autoLightMax() {
        return autoLightMax;
    }

    public static float vlIntensity() {
        return vlIntensity;
    }

    public static int vlSteps() {
        return vlSteps;
    }

    public static float vlMaxDist() {
        return vlMaxDist;
    }

    public static boolean vlShadows() {
        return vlShadows;
    }

    public static int vlShadowStride() {
        return vlShadowStride;
    }

    public static float vlTipBoost() {
        return vlTipBoost;
    }

    public static float vlTipRadius() {
        return vlTipRadius;
    }

    public static boolean vlNoise() {
        return vlNoise;
    }

    public static float vlNoiseAmount() {
        return vlNoiseAmount;
    }

    public static float vlNoiseScale() {
        return vlNoiseScale;
    }

    public static float vlNoiseSpeed() {
        return vlNoiseSpeed;
    }

    public static float vlNoiseMorph() {
        return vlNoiseMorph;
    }

    public static int vlNoiseStride() {
        return vlNoiseStride;
    }

    public static boolean vlBlueNoise() {
        return vlBlueNoise;
    }

    public static boolean vlDitherTemporal() {
        return vlDitherTemporal;
    }

    public static boolean vlClusterCull() {
        return vlClusterCull;
    }

    public static boolean vlShadowHiz() {
        return vlShadowHiz;
    }
}
