package elgatopro300.cal_lights.animation;

import elgatopro300.cal_lights.manager.LightInstance;
import elgatopro300.cal_lights.manager.LightManager;

import java.util.ArrayList;
import java.util.List;

/**
 * A container for all animation tracks on a single {@link LightInstance}.
 *
 * <p>When {@link #enabled} is true, {@link #apply(LightInstance, long)} will
 * overwrite the light's properties every tick.  The original/base values are
 * read from {@code baseR}, {@code baseG}, etc., which are captured the moment
 * the animation is first applied so we have a stable reference.
 */
public class LightAnimation {

    /** Whether this animation is currently active. */
    public boolean enabled = false;

    /** The list of property tracks. */
    public List<LightAnimationTrack> tracks = new ArrayList<>();

    // Playback state
    public transient boolean isPlaying = false;
    public transient long playbackTimeMs = 0;
    private transient long lastTickTimeMs = -1;

    // ----------------------------------------------------------------
    // Base values — captured from the LightInstance when first applied
    // so that tracks without keyframes fall back to the original value.
    // ----------------------------------------------------------------
    private transient boolean baseCaptured = false;
    private transient float baseR, baseG, baseB;
    private transient float baseIntensity;
    private transient float baseX, baseY, baseZ;
    private transient float baseRx, baseRy, baseRz;
    private transient float baseRadius;
    private transient float baseInnerAngle, baseOuterAngle, baseDistance;
    private transient float baseFogEnabled;
    private transient float baseFogDispersion, baseFogDensity, baseFogAnisotropy;
    private transient float baseShadowEnabled;
    private transient float baseShadowSoftness;

    public LightAnimation() {}

    /** Captures base values from the light for fallback. */
    private void captureBase(LightInstance light) {
        baseR            = light.r;
        baseG            = light.g;
        baseB            = light.b;
        baseIntensity    = light.intensity;
        baseX            = light.x;
        baseY            = light.y;
        baseZ            = light.z;
        baseRx           = light.rx;
        baseRy           = light.ry;
        baseRz           = light.rz;
        baseRadius       = light.radius;
        baseInnerAngle   = light.angle;
        baseOuterAngle   = light.soft;
        baseDistance     = light.distance;
        baseFogEnabled    = light.fogEnabled ? 1f : 0f;
        baseFogDispersion = light.beamStrength;
        baseFogDensity   = light.vlDensity;
        baseFogAnisotropy = light.anisotropy;
        baseShadowEnabled = light.shadowEnabled ? 1f : 0f;
        baseShadowSoftness = light.bulbSize;
        baseCaptured = true;
    }

    /**
     * Applies all enabled animation tracks to the light at the given world time.
     * Call this every tick from {@link LightManager#tick()}.
     */
    public void apply(LightInstance light, long worldTimeMs) {
        if (!enabled || tracks == null || tracks.isEmpty()) return;

        if (!baseCaptured) {
            captureBase(light);
        }

        if (isPlaying) {
            if (lastTickTimeMs == -1) {
                lastTickTimeMs = worldTimeMs;
            }
            long delta = worldTimeMs - lastTickTimeMs;
            if (delta > 0) {
                playbackTimeMs += delta;
            }
            lastTickTimeMs = worldTimeMs;
        } else {
            lastTickTimeMs = worldTimeMs;
        }

        // Compute each track and write to the light
        for (LightAnimationTrack track : tracks) {
            if (track == null) continue;

            if (track.property.equals("transform")) {
                float[] base = new float[]{ baseX, baseY, baseZ };
                float[] val = track.evaluateVector(playbackTimeMs, base);
                light.x = val[0];
                light.y = val[1];
                light.z = val[2];
            } else if (track.property.equals("rotation")) {
                if (light.isSpot) {
                    float[] base = new float[]{ baseRx, baseRy, baseRz };
                    float[] val = track.evaluateVector(playbackTimeMs, base);
                    light.rx = val[0];
                    light.ry = val[1];
                    light.rz = val[2];
                    updateSpotDir(light);
                }
            } else if (track.property.equals("color")) {
                float[] base = new float[]{ baseR, baseG, baseB };
                float[] val = track.evaluateVector(playbackTimeMs, base);
                light.r = clamp01(val[0]);
                light.g = clamp01(val[1]);
                light.b = clamp01(val[2]);
            } else {
                if (track.keyframes == null || track.keyframes.isEmpty()) continue;
                float val = track.evaluate(playbackTimeMs, getBaseValue(track.property));

                switch (track.property) {
                    case "intensity"       -> light.intensity       = Math.max(0f, val);
                    case "radius"          -> light.radius          = Math.max(0.1f, val);
                    case "angle"           -> light.angle           = Math.max(1f, val);
                    case "soft"            -> light.soft            = Math.max(0f, val);
                    case "distance"        -> light.distance        = Math.max(0f, val);
                    case "fogEnabled"      -> light.fogEnabled      = val >= 0.5f;
                    case "beamStrength"    -> light.beamStrength    = Math.max(0f, val);
                    case "vlDensity"       -> light.vlDensity       = Math.max(0f, val);
                    case "anisotropy"      -> light.anisotropy      = val;
                    case "shadowEnabled"   -> light.shadowEnabled   = val >= 0.5f;
                    case "bulbSize"        -> light.bulbSize        = Math.max(0f, val);
                }
            }
        }
    }

    /** Resets the base capture so it will re-capture next apply(). Useful after manual edits. */
    public void resetBase() {
        baseCaptured = false;
    }

    private float getBaseValue(String property) {
        return switch (property) {
            case "r"               -> baseR;
            case "g"               -> baseG;
            case "b"               -> baseB;
            case "intensity"       -> baseIntensity;
            case "x"               -> baseX;
            case "y"               -> baseY;
            case "z"               -> baseZ;
            case "rx"              -> baseRx;
            case "ry"              -> baseRy;
            case "rz"              -> baseRz;
            case "radius"          -> baseRadius;
            case "angle"           -> baseInnerAngle;
            case "soft"            -> baseOuterAngle;
            case "distance"        -> baseDistance;
            case "fogEnabled"      -> baseFogEnabled;
            case "beamStrength"    -> baseFogDispersion;
            case "vlDensity"       -> baseFogDensity;
            case "anisotropy"      -> baseFogAnisotropy;
            case "shadowEnabled"   -> baseShadowEnabled;
            case "bulbSize"        -> baseShadowSoftness;
            default                -> 0f;
        };
    }

    /** Recalculate spot light direction from rx/ry/rz Euler angles. */
    private void updateSpotDir(LightInstance light) {
        if (!light.isSpot) return;
        float radX = (float) Math.toRadians(light.rx);
        float radY = (float) Math.toRadians(light.ry);
        float radZ = (float) Math.toRadians(light.rz);
        // Rotate (0, 0, -1) by XYZ Euler
        float cosX = (float) Math.cos(radX), sinX = (float) Math.sin(radX);
        float cosY = (float) Math.cos(radY), sinY = (float) Math.sin(radY);
        float cosZ = (float) Math.cos(radZ), sinZ = (float) Math.sin(radZ);
        float y1 = -(-1f) * sinX, z1 = (-1f) * cosX, x1 = 0f;
        float x2 = x1 * cosY + z1 * sinY, y2 = y1, z2 = -x1 * sinY + z1 * cosY;
        light.dx = x2 * cosZ - y2 * sinZ;
        light.dy = x2 * sinZ + y2 * cosZ;
        light.dz = z2;
    }

    /** Synchronizes animation tracks to the fixed ones for the current light type. */
    public void synchronizeTracks(boolean isSpot) {
        String[] fixedProps;
        if (isSpot) {
            fixedProps = new String[]{ "transform", "rotation", "color", "intensity", "angle", "soft", "distance", "fogEnabled", "beamStrength", "vlDensity", "anisotropy", "shadowEnabled", "bulbSize" };
        } else {
            fixedProps = new String[]{ "transform", "color", "intensity", "radius", "fogEnabled", "beamStrength", "vlDensity", "anisotropy", "shadowEnabled", "bulbSize" };
        }

        List<LightAnimationTrack> newTracks = new ArrayList<>();
        for (String prop : fixedProps) {
            LightAnimationTrack existing = null;
            if (this.tracks != null) {
                for (LightAnimationTrack t : this.tracks) {
                    if (t != null && t.property != null && t.property.equals(prop)) {
                        existing = t;
                        break;
                    }
                }
            }
            if (existing == null) {
                existing = new LightAnimationTrack(prop, 2000f);
            }
            newTracks.add(existing);
        }
        this.tracks = newTracks;
    }

    private float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }
}
