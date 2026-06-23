package elgatopro300.cal_lights.light;

public final class LightConfig {

    public static int shadowQuality = 1;
    public static boolean shadowCache = true;
    public static boolean shadowBlocks = true;
    public static int shadowBlockRadius = 24;
    public static int shadowBakeBudget = 4;

    // --- Auto block-lights ---
    public static boolean autoLights = false;
    public static boolean autoLightShadows = false;
    public static float autoLightIntensity = 1.0f;
    public static float autoLightReach = 1.0f;
    public static int autoLightRadius = 48;
    public static int autoLightMax = 200;

    private LightConfig() {}


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
}
