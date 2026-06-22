package elgatopro300.cal_lights.manager;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public class LightManager {
    public static final LightManager INSTANCE = new LightManager();

    private final Map<Integer, LightInstance> pointLights = new LinkedHashMap<>();
    private final Map<Integer, LightInstance> spotLights = new LinkedHashMap<>();

    public LightInstance updatePoint(int id, float x, float y, float z, float r, float g, float b, float intensity, float radius) {
        LightInstance inst = pointLights.computeIfAbsent(id, k -> new LightInstance(id));
        inst.mark();
        inst.setPoint(x, y, z, r, g, b, intensity, radius);
        return inst;
    }

    public LightInstance updateSpot(int id, float x, float y, float z, float dx, float dy, float dz, float r, float g, float b, float intensity, float innerAngle, float outerAngle, float distance) {
        LightInstance inst = spotLights.computeIfAbsent(id, k -> new LightInstance(id));
        inst.mark();
        inst.setSpot(x, y, z, dx, dy, dz, r, g, b, intensity, innerAngle, outerAngle, distance);
        return inst;
    }

    public void removePoint(int id) {
        pointLights.remove(id);
    }

    public void removeSpot(int id) {
        spotLights.remove(id);
    }

    public void convertLightType(int id, boolean makeSpot) {
        if (makeSpot) {
            LightInstance inst = pointLights.remove(id);
            if (inst != null) {
                inst.isSpot = true;
                spotLights.put(id, inst);
            }
        } else {
            LightInstance inst = spotLights.remove(id);
            if (inst != null) {
                inst.isSpot = false;
                pointLights.put(id, inst);
            }
        }
    }

    public void tick() {
        long now = System.currentTimeMillis();

        pointLights.entrySet().removeIf(entry -> {
            LightInstance light = entry.getValue();
            if (light.persistent) {
                if (light.animation != null && light.animation.enabled) {
                    light.animation.apply(light, now);
                }
                return false;
            }
            light.counter--;
            return light.counter <= 0;
        });
        spotLights.entrySet().removeIf(entry -> {
            LightInstance light = entry.getValue();
            if (light.persistent) {
                if (light.animation != null && light.animation.enabled) {
                    light.animation.apply(light, now);
                }
                return false;
            }
            light.counter--;
            return light.counter <= 0;
        });
    }

    public Collection<LightInstance> getPointLights() {
        return pointLights.values();
    }

    public Collection<LightInstance> getSpotLights() {
        return spotLights.values();
    }

    public void clear() {
        pointLights.clear();
        spotLights.clear();
    }
}
