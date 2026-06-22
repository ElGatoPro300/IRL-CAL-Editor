package elgatopro300.cal_lights.light;

public class PlacedLight {
    public enum Type {
        POINT, SPOT
    }

    private static long NEXT_ID = 1L;

    public final long id;
    public Type type = Type.POINT;
    public String name = "Fuente";
    public double x, y, z;
    public float dirX = 0f, dirY = -1f, dirZ = 0f;
    public float r = 1f, g = 1f, b = 1f, a = 1f;

    public float intensity = 1f;
    public float radius = 6f;
    public float range = 12f;
    public float outerAngleDeg = 35f;
    public float innerAngleDeg = 25f;
    public float beamStrength = 1f;
    public float anisotropy = 0.4f;
    public float vlDensity = 0.05f;
    public float bulbSize = 0f;
    public boolean entitiesOnly = false;
    public boolean blocksOnly = false;
    public boolean shadows = true;

    public String cookie = "";
    public float cookieRotation = 0f;
    public float cookieScale = 1f;
    public boolean cookieInvert = false;
    public boolean autoShadowEligible = true;

    public PlacedLight() {
        this.id = NEXT_ID++;
    }

    public static PlacedLight point() {
        PlacedLight l = new PlacedLight();
        l.type = Type.POINT;
        return l;
    }

    public static PlacedLight spot() {
        PlacedLight l = new PlacedLight();
        l.type = Type.SPOT;
        return l;
    }

    public static PlacedLight copyOf(PlacedLight s) {
        PlacedLight l = new PlacedLight();
        l.type = s.type;
        l.name = s.name;
        l.x = s.x; l.y = s.y; l.z = s.z;
        l.dirX = s.dirX; l.dirY = s.dirY; l.dirZ = s.dirZ;
        l.r = s.r; l.g = s.g; l.b = s.b; l.a = s.a;
        l.intensity = s.intensity;
        l.radius = s.radius;
        l.range = s.range;
        l.outerAngleDeg = s.outerAngleDeg;
        l.innerAngleDeg = s.innerAngleDeg;
        l.beamStrength = s.beamStrength;
        l.anisotropy = s.anisotropy;
        l.vlDensity = s.vlDensity;
        l.bulbSize = s.bulbSize;
        l.entitiesOnly = s.entitiesOnly;
        l.blocksOnly = s.blocksOnly;
        l.shadows = s.shadows;
        l.cookie = s.cookie;
        l.cookieRotation = s.cookieRotation;
        l.cookieScale = s.cookieScale;
        l.cookieInvert = s.cookieInvert;
        return l;
    }
}
