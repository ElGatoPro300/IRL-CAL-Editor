package elgatopro300.cal_lights.animation;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleUnaryOperator;

/**
 * A single animation track that controls one float property of a LightInstance.
 *
 * <p>Properties supported (by string name):
 * <ul>
 *   <li>"r", "g", "b"        — RGB color components (0.0-1.0)</li>
 *   <li>"intensity"          — light intensity (0.0-100.0)</li>
 *   <li>"x", "y", "z"       — world position</li>
 *   <li>"rx", "ry", "rz"    — spot light rotation in degrees</li>
 *   <li>"radius"             — point light radius</li>
 *   <li>"fogDensity", "fogDispersion", "fogAnisotropy" — volumetric fog</li>
 * </ul>
 */
public class LightAnimationTrack {

    /** The LightInstance property this track animates. */
    public String property = "r";

    /** Duration of the full animation cycle in milliseconds. */
    public float duration = 2000f;

    /** Fade-in time in ms (start of the animation). Not yet used for rendering but stored. */
    public float fadeIn = 0f;

    /** Fade-out time in ms (end of the animation). Not yet used for rendering but stored. */
    public float fadeOut = 0f;

    /** Whether the animation loops forever. */
    public boolean looping = true;

    /** Optional time offset in ms, allows staggering multiple tracks. */
    public float offset = 0f;

    /** The keyframes in time order. Automatically sorted after edits. */
    public List<LightKeyframe> keyframes = new ArrayList<>();

    public LightAnimationTrack() {}

    public LightAnimationTrack(String property, float duration) {
        this.property = property;
        this.duration = duration;
    }

    /**
     * Evaluates the animated value at a given world time (in ms).
     * Returns the base value if there are no keyframes.
     */
    public float evaluate(long worldTimeMs, float baseValue) {
        if (keyframes == null || keyframes.isEmpty()) {
            return baseValue;
        }

        // Compute local time within the animation cycle
        float localTime = (worldTimeMs + offset) % Math.max(duration, 1f);
        if (!looping && (worldTimeMs + offset) > duration) {
            // Past the end and not looping — clamp to last keyframe
            return keyframes.get(keyframes.size() - 1).value;
        }

        // Sort keyframes by tick just in case
        keyframes.sort((a, b) -> Float.compare(a.tick, b.tick));

        // Find surrounding keyframes
        LightKeyframe prev = null;
        LightKeyframe next = null;

        for (int i = 0; i < keyframes.size(); i++) {
            LightKeyframe kf = keyframes.get(i);
            if (kf.tick <= localTime) {
                prev = kf;
            } else {
                next = kf;
                break;
            }
        }

        if (prev == null) {
            // Before first keyframe — wrap from last (looping) or use first
            if (looping && !keyframes.isEmpty()) {
                prev = keyframes.get(keyframes.size() - 1);
                next = keyframes.get(0);
                // Adjust timing: prev is at end of cycle, next is at beginning
                float prevTick = prev.tick;
                float nextTick = next.tick + duration;
                float t = (localTime + duration - prevTick) / Math.max(nextTick - prevTick, 0.001f);
                return interpolate(prev, next, t);
            }
            return keyframes.get(0).value;
        }

        if (next == null) {
            // After last keyframe — wrap to first (looping) or clamp
            if (looping && keyframes.size() > 1) {
                next = keyframes.get(0);
                float prevTick = prev.tick;
                float nextTick = next.tick + duration;
                float t = (localTime - prevTick) / Math.max(nextTick - prevTick, 0.001f);
                return interpolate(prev, next, t);
            }
            return prev.value;
        }

        // Normal interpolation between prev and next
        float t = (localTime - prev.tick) / Math.max(next.tick - prev.tick, 0.001f);
        t = Math.max(0f, Math.min(1f, t));
        return interpolate(prev, next, t);
    }

    private float interpolate(LightKeyframe from, LightKeyframe to, float t) {
        if (from.interp == LightInterpolation.STEP) {
            return t < 1.0f ? from.value : to.value;
        }
        float easeT = (float) calculateEasing(from.interp, t);
        return from.value + (to.value - from.value) * easeT;
    }

    /**
     * Evaluates a 3D property (like transform, rotation, color) at a given world time (in ms).
     * Returns the base 3-element vector if there are no keyframes.
     */
    public float[] evaluateVector(long worldTimeMs, float[] baseValue) {
        if (keyframes == null || keyframes.isEmpty()) {
            return baseValue;
        }

        // Compute local time within the animation cycle
        float localTime = (worldTimeMs + offset) % Math.max(duration, 1f);
        if (!looping && (worldTimeMs + offset) > duration) {
            // Past the end and not looping — clamp to last keyframe
            LightKeyframe last = keyframes.get(keyframes.size() - 1);
            return new float[]{ last.value, last.vecY != null ? last.vecY : baseValue[1], last.vecZ != null ? last.vecZ : baseValue[2] };
        }

        // Sort keyframes by tick just in case
        keyframes.sort((a, b) -> Float.compare(a.tick, b.tick));

        // Find surrounding keyframes
        LightKeyframe prev = null;
        LightKeyframe next = null;

        for (int i = 0; i < keyframes.size(); i++) {
            LightKeyframe kf = keyframes.get(i);
            if (kf.tick <= localTime) {
                prev = kf;
            } else {
                next = kf;
                break;
            }
        }

        if (prev == null) {
            // Before first keyframe — wrap from last (looping) or use first
            if (looping && !keyframes.isEmpty()) {
                prev = keyframes.get(keyframes.size() - 1);
                next = keyframes.get(0);
                // Adjust timing
                float prevTick = prev.tick;
                float nextTick = next.tick + duration;
                float t = (localTime + duration - prevTick) / Math.max(nextTick - prevTick, 0.001f);
                return interpolateVector(prev, next, t, baseValue);
            }
            LightKeyframe first = keyframes.get(0);
            return new float[]{ first.value, first.vecY != null ? first.vecY : baseValue[1], first.vecZ != null ? first.vecZ : baseValue[2] };
        }

        if (next == null) {
            // After last keyframe — wrap to first (looping) or clamp
            if (looping && keyframes.size() > 1) {
                next = keyframes.get(0);
                float prevTick = prev.tick;
                float nextTick = next.tick + duration;
                float t = (localTime - prevTick) / Math.max(nextTick - prevTick, 0.001f);
                return interpolateVector(prev, next, t, baseValue);
            }
            return new float[]{ prev.value, prev.vecY != null ? prev.vecY : baseValue[1], prev.vecZ != null ? prev.vecZ : baseValue[2] };
        }

        // Normal interpolation between prev and next
        float t = (localTime - prev.tick) / Math.max(next.tick - prev.tick, 0.001f);
        t = Math.max(0f, Math.min(1f, t));
        return interpolateVector(prev, next, t, baseValue);
    }

    private float[] interpolateVector(LightKeyframe from, LightKeyframe to, float t, float[] baseValue) {
        float fromY = from.vecY != null ? from.vecY : baseValue[1];
        float fromZ = from.vecZ != null ? from.vecZ : baseValue[2];
        
        float toY = to.vecY != null ? to.vecY : baseValue[1];
        float toZ = to.vecZ != null ? to.vecZ : baseValue[2];
        
        if (from.interp == LightInterpolation.STEP) {
            return t < 1.0f 
                ? new float[]{ from.value, fromY, fromZ } 
                : new float[]{ to.value, toY, toZ };
        }
        
        float easeT = (float) calculateEasing(from.interp, t);
        
        float x = from.value + (to.value - from.value) * easeT;
        float y = fromY + (toY - fromY) * easeT;
        float z = fromZ + (toZ - fromZ) * easeT;
        
        return new float[]{ x, y, z };
    }

    public static double calculateEasing(LightInterpolation mode, double x) {
        if (mode == null) return x;
        switch (mode) {
            case STEP:
                return x < 1.0 ? 0.0 : 1.0;
            case CONSTANT:
                return 0.0;
            case LINEAR:
                return x;
            
            case SINE_IN:
                return sine(x);
            case SINE_OUT:
                return easeOut(x, LightAnimationTrack::sine);
            case SINE_INOUT:
                return easeInOut(x, LightAnimationTrack::sine);
                
            case CIRCLE_IN:
                return circle(x);
            case CIRCLE_OUT:
                return easeOut(x, LightAnimationTrack::circle);
            case CIRCLE_INOUT:
                return easeInOut(x, LightAnimationTrack::circle);
                
            case QUAD_IN:
                return quad(x);
            case QUAD_OUT:
                return easeOut(x, LightAnimationTrack::quad);
            case QUAD_INOUT:
                return easeInOut(x, LightAnimationTrack::quad);
                
            case CUBIC_IN:
                return cubic(x);
            case CUBIC_OUT:
                return easeOut(x, LightAnimationTrack::cubic);
            case CUBIC_INOUT:
                return easeInOut(x, LightAnimationTrack::cubic);
                
            case QUART_IN:
                return quart(x);
            case QUART_OUT:
                return easeOut(x, LightAnimationTrack::quart);
            case QUART_INOUT:
                return easeInOut(x, LightAnimationTrack::quart);
                
            case QUINT_IN:
                return quint(x);
            case QUINT_OUT:
                return easeOut(x, LightAnimationTrack::quint);
            case QUINT_INOUT:
                return easeInOut(x, LightAnimationTrack::quint);
                
            case EXP_IN:
                return exp(x);
            case EXP_OUT:
                return easeOut(x, LightAnimationTrack::exp);
            case EXP_INOUT:
                return easeInOut(x, LightAnimationTrack::exp);
                
            case BACK_IN:
                return back(x);
            case BACK_OUT:
                return easeOut(x, LightAnimationTrack::back);
            case BACK_INOUT:
                return easeInOut(x, LightAnimationTrack::back);
                
            case ELASTIC_IN:
                return elastic(x);
            case ELASTIC_OUT:
                return easeOut(x, LightAnimationTrack::elastic);
            case ELASTIC_INOUT:
                return easeInOut(x, LightAnimationTrack::elastic);
                
            case BOUNCE_IN:
                return bounce(x);
            case BOUNCE_OUT:
                return easeOut(x, LightAnimationTrack::bounce);
            case BOUNCE_INOUT:
                return easeInOut(x, LightAnimationTrack::bounce);
            
            default:
                return x;
        }
    }

    private static double sine(double x) {
        return 1.0 - Math.sin(((1.0 - x) * Math.PI) / 2.0);
    }
    
    private static double circle(double x) {
        return 1.0 - Math.sqrt(1.0 - x * x);
    }
    
    private static double quad(double x) {
        return x * x;
    }
    
    private static double cubic(double x) {
        return x * x * x;
    }
    
    private static double quart(double x) {
        return x * x * x * x;
    }
    
    private static double quint(double x) {
        return x * x * x * x * x;
    }
    
    private static double exp(double x) {
        return (Math.pow(2.0, 10.0 * (x - 1.0)) - 0.001) / 0.999;
    }
    
    private static double back(double x) {
        double c1 = 1.70158;
        double c3 = c1 + 1.0;
        return c3 * x * x * x - c1 * x * x;
    }
    
    private static double elastic(double x) {
        return elasticIn((x + 0.025) * 0.975);
    }
    
    private static double elasticIn(double x) {
        double amp = 10.0;
        double c4 = (2.0 * Math.PI) / 3.0;
        return -Math.pow(2.0, amp * x - amp) * Math.sin((x * 10.0 - 10.75) * c4);
    }
    
    private static double bounce(double x) {
        return 1.0 - bounceIn(1.0 - x);
    }
    
    private static double bounceIn(double x) {
        double n = 4.5;
        double lambda = 5.0;
        return 1.0 - Math.abs(Math.cos(n * Math.PI * x) * Math.exp(-lambda * x));
    }

    private static double easeOut(double x, DoubleUnaryOperator base) {
        return 1.0 - base.applyAsDouble(1.0 - x);
    }
    
    private static double easeInOut(double x, DoubleUnaryOperator base) {
        if (x < 0.5) {
            return base.applyAsDouble(x * 2.0) / 2.0;
        }
        double newX = (x - 0.5) * 2.0;
        double newY = 1.0 - base.applyAsDouble(1.0 - newX);
        return newY / 2.0 + 0.5;
    }
}
