package elgatopro300.cal_lights.manager;

import elgatopro300.cal_lights.animation.LightAnimation;

public class LightInstance {
    public final int id;
    public String name = "";
    public boolean isSpot = false;
    public boolean visible = true;
    
    // Position
    public float x, y, z;
    
    // Color (R, G, B floats 0-1)
    public float r = 1f;
    public float g = 1f;
    public float b = 1f;
    
    // Intensity (0..20)
    public float intensity = 1.0f;
    
    // Point light radius (0.1..64)
    public float radius = 6.0f;
    
    // Spot light properties
    public float dx = 0f;
    public float dy = -1f;
    public float dz = 0f; // pointing down by default

    // Spot cone: angle = outer cone full angle in degrees (1..179),
    //            soft  = penumbra width in degrees (0..60).
    // innerAngle is derived: max(1, angle - soft).
    // This matches the original IRL editor exactly.
    public float angle = 35.0f;     // outer cone full angle (degrees)
    public float soft  = 10.0f;     // penumbra/softness width (degrees)
    public float distance = 12.0f;  // spot range in blocks (0.1..128)

    // Spot light rotations (X, Y, Z in degrees, convenience for editor UI)
    public float rx = -90f;
    public float ry = 0f;
    public float rz = 0f;

    // Volumetric Fog properties
    // These map to the IRL Core API as:
    //   fogEnabled -> controls whether beamStrength is sent (beam or 0)
    //   beamStrength -> bm parameter (0..5)
    //   vlDensity -> dens parameter (0.005..0.5)
    //   anisotropy -> aniso parameter (-0.95..0.95)
    public boolean fogEnabled = true;
    public float beamStrength = 1.0f;    // volumetric beam strength (0..5)
    public float vlDensity = 0.05f;      // volumetric density (0.005..0.5)
    public float anisotropy = 0.4f;      // Henyey-Greenstein g (-0.95..0.95)

    // Shadow properties
    // bulbSize maps to the 'bulb' parameter in IRL Core (0..2)
    // This controls shadow softness (0 = hard / shader global, 2 = very soft)
    public boolean shadowEnabled = true;
    public float bulbSize = 0.0f;        // shadow softness / penumbra (0..2)

    // Exclusions
    public boolean entitiesOnly = false;
    public boolean blocksOnly = false;

    // Gobo properties
    public String goboName = "None";
    public float goboRotation = 0.0f;    // degrees in UI, converted to radians for API
    public float cookieScale = 1.0f;
    public boolean cookieInvert = false;
    
    // Coordinates specialized for shaders (replays/model blocks)
    public Float shaderX = null;
    public Float shaderY = null;
    public Float shaderZ = null;
    public Float shaderDx = null;
    public Float shaderDy = null;
    public Float shaderDz = null;

    public float getShaderX() { return shaderX != null ? shaderX : x; }
    public float getShaderY() { return shaderY != null ? shaderY : y; }
    public float getShaderZ() { return shaderZ != null ? shaderZ : z; }
    public float getShaderDx() { return shaderDx != null ? shaderDx : dx; }
    public float getShaderDy() { return shaderDy != null ? shaderDy : dy; }
    public float getShaderDz() { return shaderDz != null ? shaderDz : dz; }

    /** Derived outer angle in degrees (= angle). */
    public float getOuterAngleDeg() {
        return angle;
    }

    /** Derived inner angle in degrees = max(1, angle - soft), clamped to <= angle. */
    public float getInnerAngleDeg() {
        float inner = Math.max(1f, angle - soft);
        return Math.min(inner, angle);
    }

    public int counter = 2; // lifecycle ticks
    public boolean persistent = false; // if true, won't be deleted by tick() decrement

    // Animation
    /** Optional animation state. When non-null and enabled, overrides live values every tick. */
    public LightAnimation animation = null;

    public LightInstance(int id) {
        this.id = id;
    }

    public void mark() {
        this.counter = 2; // stays alive for 2 ticks (prevents deletion on frame gaps)
    }

    public void setPoint(float x, float y, float z, float r, float g, float b, float intensity, float radius) {
        this.isSpot = false;
        this.x = x; this.y = y; this.z = z;
        this.r = r; this.g = g; this.b = b;
        this.intensity = intensity;
        this.radius = radius;
        this.shaderX = null;
        this.shaderY = null;
        this.shaderZ = null;
        this.shaderDx = null;
        this.shaderDy = null;
        this.shaderDz = null;
    }

    public void setSpot(float x, float y, float z, float dx, float dy, float dz, float r, float g, float b, float intensity, float angle, float soft, float distance) {
        this.isSpot = true;
        this.x = x; this.y = y; this.z = z;
        this.dx = dx; this.dy = dy; this.dz = dz;
        this.r = r; this.g = g; this.b = b;
        this.intensity = intensity;
        this.angle = angle;
        this.soft = soft;
        this.distance = distance;
        this.shaderX = null;
        this.shaderY = null;
        this.shaderZ = null;
        this.shaderDx = null;
        this.shaderDy = null;
        this.shaderDz = null;

        // Synchronize rotation Euler angles from the loaded direction vector
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len > 0.0001f) {
            float ndx = dx / len;
            float ndy = dy / len;
            float ndz = dz / len;
            this.rx = (float) Math.toDegrees(Math.asin(ndy));
            this.ry = (float) Math.toDegrees(Math.atan2(-ndx, -ndz));
            if (this.ry < 0) this.ry += 360.0f;
        } else {
            this.rx = -90f;
            this.ry = 0f;
        }
        this.rz = 0f;
    }
}
