package elgatopro300.cal_lights.light;

public final class SettingsPresets {
    private SettingsPresets() {}

    public static final String[] QUALITY_LABELS = {"Performance", "Balanced", "Quality", "Ultra", "Custom"};
    public static final String[] STYLE_LABELS = {"Clean", "Dusty", "Smoky", "Custom"};

    private static class Quality {
        final int steps;
        final float maxDist;
        final int shadowStride;
        final int noiseStride;
        final boolean vlShadows;
        final int shadowQuality;
        final boolean blockShadows;

        Quality(int steps, float maxDist, int shadowStride, int noiseStride, boolean vlShadows, int shadowQuality, boolean blockShadows) {
            this.steps = steps;
            this.maxDist = maxDist;
            this.shadowStride = shadowStride;
            this.noiseStride = noiseStride;
            this.vlShadows = vlShadows;
            this.shadowQuality = shadowQuality;
            this.blockShadows = blockShadows;
        }
    }

    private static final Quality[] QUALITY = {
        new Quality(16, 48f, 4, 4, false, 0, false),   // Performance
        new Quality(48, 96f, 2, 2, true, 1, true),     // Balanced
        new Quality(56, 128f, 1, 2, true, 2, true),    // Quality
        new Quality(64, 192f, 1, 1, true, 2, true)     // Ultra
    };

    private static class Style {
        final boolean noise;
        final float amount;
        final float scale;
        final float speed;
        final float tipBoost;
        final float tipRadius;

        Style(boolean noise, float amount, float scale, float speed, float tipBoost, float tipRadius) {
            this.noise = noise;
            this.amount = amount;
            this.scale = scale;
            this.speed = speed;
            this.tipBoost = tipBoost;
            this.tipRadius = tipRadius;
        }
    }

    private static final Style[] STYLE = {
        new Style(false, 0.6f, 2.0f, 0.25f, 1.0f, 1.5f), // Clean
        new Style(true, 0.6f, 2.0f, 0.25f, 1.5f, 1.5f),  // Dusty
        new Style(true, 1.0f, 0.5f, 3.0f, 2.0f, 2.0f)    // Smoky
    };

    public static int quality() {
        for (int i = 0; i < QUALITY.length; i++) {
            Quality q = QUALITY[i];
            if (LightConfig.vlSteps == q.steps
                && LightConfig.vlMaxDist == q.maxDist
                && LightConfig.vlShadowStride == q.shadowStride
                && LightConfig.vlNoiseStride == q.noiseStride
                && LightConfig.vlShadows == q.vlShadows
                && LightConfig.shadowQuality == q.shadowQuality
                && LightConfig.shadowBlocks == q.blockShadows) {
                return i;
            }
        }
        return QUALITY.length; // Custom
    }

    public static void applyQuality(int i) {
        if (i < 0 || i >= QUALITY.length) {
            return;
        }
        Quality q = QUALITY[i];
        LightConfig.vlSteps = q.steps;
        LightConfig.vlMaxDist = q.maxDist;
        LightConfig.vlShadowStride = q.shadowStride;
        LightConfig.vlNoiseStride = q.noiseStride;
        LightConfig.vlShadows = q.vlShadows;
        LightConfig.shadowQuality = q.shadowQuality;
        LightConfig.shadowBlocks = q.blockShadows;
    }

    public static int style() {
        for (int i = 0; i < STYLE.length; i++) {
            Style s = STYLE[i];
            if (LightConfig.vlNoise != s.noise) {
                continue;
            }
            boolean shapeOk = !LightConfig.vlNoise
                || (LightConfig.vlNoiseAmount == s.amount
                    && LightConfig.vlNoiseScale == s.scale
                    && LightConfig.vlNoiseSpeed == s.speed);
            if (shapeOk
                && LightConfig.vlTipBoost == s.tipBoost
                && LightConfig.vlTipRadius == s.tipRadius) {
                return i;
            }
        }
        return STYLE.length; // Custom
    }

    public static void applyStyle(int i) {
        if (i < 0 || i >= STYLE.length) {
            return;
        }
        Style s = STYLE[i];
        LightConfig.vlNoise = s.noise;
        if (s.noise) {
            LightConfig.vlNoiseAmount = s.amount;
            LightConfig.vlNoiseScale = s.scale;
            LightConfig.vlNoiseSpeed = s.speed;
        }
        LightConfig.vlTipBoost = s.tipBoost;
        LightConfig.vlTipRadius = s.tipRadius;
    }
}
