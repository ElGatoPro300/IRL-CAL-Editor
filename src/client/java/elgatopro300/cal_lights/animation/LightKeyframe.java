package elgatopro300.cal_lights.animation;

/**
 * A single keyframe in a light animation track.
 * Stores a time position (in milliseconds) and a float value.
 */
public class LightKeyframe {
    /** Time within the animation in milliseconds (0 to track.duration). */
    public float tick;

    /** The float value of the animated property at this keyframe. */
    public float value;

    /** Interpolation mode applied FROM this keyframe TO the next. */
    public LightInterpolation interp = LightInterpolation.LINEAR;

    /** Additional dimensions for 3D track properties (like transform, rotation, color). */
    public Float vecY;
    public Float vecZ;

    public LightKeyframe() {}

    public LightKeyframe(float tick, float value) {
        this.tick = tick;
        this.value = value;
    }

    public LightKeyframe(float tick, float value, LightInterpolation interp) {
        this.tick = tick;
        this.value = value;
        this.interp = interp;
    }

    public LightKeyframe(float tick, float value, Float vecY, Float vecZ) {
        this.tick = tick;
        this.value = value;
        this.vecY = vecY;
        this.vecZ = vecZ;
    }

    public LightKeyframe(float tick, float value, Float vecY, Float vecZ, LightInterpolation interp) {
        this.tick = tick;
        this.value = value;
        this.vecY = vecY;
        this.vecZ = vecZ;
        this.interp = interp;
    }
}
