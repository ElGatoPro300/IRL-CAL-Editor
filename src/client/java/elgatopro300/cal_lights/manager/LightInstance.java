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
    
    // Intensity
    public float intensity = 1.0f;
    
    // Point light radius
    public float radius = 10.0f;
    
    // Spot light properties
    public float dx = 0f;
    public float dy = -1f;
    public float dz = 0f; // pointing down by default
    public float innerAngle = 15.0f;
    public float outerAngle = 30.0f;
    public float distance = 15.0f;

    // Spot light rotations (X, Y, Z in degrees, convenience for editor UI)
    public float rx = -90f;
    public float ry = 0f;
    public float rz = 0f;

    // Volumetric Fog properties
    public boolean fogEnabled = true;
    public float fogDispersion = 1.0f;
    public float fogDensity = 1.0f;
    public float fogAnisotropy = 0.3f;

    // Shadow properties
    public boolean shadowEnabled = false;         // Enable shadow casting
    public float shadowSoftness = 1.0f;          // Penumbra blur (0 = hard, 5 = very soft)
    public float shadowIntensity = 0.8f;         // Shadow darkness (0 = no shadow, 1 = full black)

    // Rim lighting (Fresnel/contour glow on entity edges)
    public boolean rimEnabled = false;
    public float rimIntensity = 2.0f;   // Brightness multiplier for the rim glow
    public float rimPower = 3.0f;       // Sharpness: higher = tighter rim at silhouette edge
    public float rimHardness = 0.0f;    // 0 = smooth Fresnel, 1 = hard cel-shading line
    public float rimDirection = 0.0f;   // 0 = back (opposite), 1 = front (facing), 2 = both (uniform)
    
    // Outline (sharp white silhouette/geometric borders)
    public boolean outlineEnabled = false;
    public float outlineIntensity = 4.0f;
    public float outlineThickness = 3.0f;
    
    // Flare properties
    public boolean flareEnabled = false;
    public float flareSize = 1.0f;
    public float flareGlowSize = 1.5f;
    public float flareGlowIntensity = 1.0f;
    public float flareRayLength = 5.0f;
    public float flareRayThickness = 0.3f;
    public float flareRayLength2 = 5.0f;
    public float flareRayThickness2 = 0.3f;
    public float flareRayLength3 = 2.5f;
    public float flareRayThickness3 = 0.2f;
    public float flareRotation = 0.0f;
    public float flareStartAngle = 20.0f;
    public float flareEndAngle = 45.0f;

    // Gobo properties
    public String goboName = "None";
    public float goboRotation = 0.0f;
    
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

    public void setSpot(float x, float y, float z, float dx, float dy, float dz, float r, float g, float b, float intensity, float innerAngle, float outerAngle, float distance) {
        this.isSpot = true;
        this.x = x; this.y = y; this.z = z;
        this.dx = dx; this.dy = dy; this.dz = dz;
        this.r = r; this.g = g; this.b = b;
        this.intensity = intensity;
        this.innerAngle = innerAngle;
        this.outerAngle = outerAngle;
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
