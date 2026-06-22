package elgatopro300.cal_lights.shaders;

import elgatopro300.cal_lights.manager.GoboManager;
import elgatopro300.cal_lights.manager.LightInstance;
import elgatopro300.cal_lights.manager.LightManager;

import org.lwjgl.opengl.GL43;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class LightSSBO {
    private static int ssboId = -1;
    private static final int BINDING_POINT = 7;
    private static final int MAX_LIGHTS = 64;

    // Standard std430 layout:
    // struct PointLight {
    //     vec4 pos_radius;    // xyz: world pos, w: radius
    //     vec4 col_intensity; // rgb: color, w: intensity
    //     vec4 fog_params;    // x: fogEnabled, y: fogDensity, z: fogDispersion, w: fogAnisotropy
    //     vec4 shadow_params; // x: shadowEnabled, y: shadowSoftness, z: shadowIntensity, w: unused
    //     vec4 rim_params;    // x: rimEnabled, y: rimIntensity, z: rimPower, w: unused
    // };  --> 20 floats per point light
    // struct SpotLight {
    //     vec4 pos_distance;  // xyz: world pos, w: distance limit
    //     vec4 dir_inner;     // xyz: direction, w: inner angle
    //     vec4 col_intensity; // rgb: color, w: intensity
    //     vec4 outer_padding; // x: outer angle, y: fogEnabled, z: fogDensity, w: fogAnisotropy
    //     vec4 fog_params2;   // x: fogDispersion, y: goboIndex, z: goboRotation, w: unused
    //     vec4 shadow_params; // x: shadowEnabled, y: shadowSoftness, z: shadowIntensity, w: unused
    //     vec4 rim_params;    // x: rimEnabled, y: rimIntensity, z: rimPower, w: unused
    // };  --> 28 floats per spot light
    // layout(std430, binding = 7) buffer LightData {
    //     int pointCount;
    //     int spotCount;
    //     int pad1;
    //     int pad2;
    //     PointLight points[64];
    //     SpotLight spots[64];
    // };

    // Header: 4 floats
    // Points: 64 * 20 = 1280 floats
    // Spots:  64 * 28 = 1792 floats
    // Total:  4 + 1280 + 1792 = 3076 floats
    private static final int FLOATS_PER_POINT = 20;
    private static final int FLOATS_PER_SPOT  = 28;
    
    public static void upload() {
        if (ssboId == -1) {
            ssboId = GL43.glGenBuffers();
        }

        long now = System.currentTimeMillis();

        List<LightInstance> pointLights = new ArrayList<>();
        for (LightInstance l : LightManager.INSTANCE.getPointLights()) {
            if (l.visible) {
                if (l.animation != null && l.animation.enabled) {
                    l.animation.apply(l, now);
                }
                pointLights.add(l);
            }
        }

        List<LightInstance> spotLights = new ArrayList<>();
        for (LightInstance l : LightManager.INSTANCE.getSpotLights()) {
            if (l.visible) {
                if (l.animation != null && l.animation.enabled) {
                    l.animation.apply(l, now);
                }
                spotLights.add(l);
            }
        }

        int pointCount = Math.min(pointLights.size(), MAX_LIGHTS);
        int spotCount = Math.min(spotLights.size(), MAX_LIGHTS);

        int totalFloats = 4 + (MAX_LIGHTS * FLOATS_PER_POINT) + (MAX_LIGHTS * FLOATS_PER_SPOT);
        FloatBuffer buffer = MemoryUtil.memAllocFloat(totalFloats);


        // 1. Header
        buffer.put(Float.intBitsToFloat(pointCount));
        buffer.put(Float.intBitsToFloat(spotCount));
        buffer.put(0.0f);
        buffer.put(0.0f);

        // 2. Point Lights (16 floats per light: 4 vec4s)
        int pIndex = 0;
        for (LightInstance light : pointLights) {
            if (pIndex >= MAX_LIGHTS) break;
            // vec4 pos_radius
            buffer.put(light.getShaderX()).put(light.getShaderY()).put(light.getShaderZ()).put(light.radius);
            // vec4 col_intensity
            buffer.put(light.r).put(light.g).put(light.b).put(light.intensity);
            // vec4 fog_params
            buffer.put(light.fogEnabled ? 1.0f : 0.0f)
                  .put(light.fogDensity)
                  .put(light.fogDispersion)
                  .put(packOutline(light.outlineEnabled, light.outlineIntensity, light.outlineThickness));
            // vec4 shadow_params
            buffer.put(light.shadowEnabled ? 1.0f : 0.0f)
                  .put(light.shadowSoftness)
                  .put(light.shadowIntensity)
                  .put(light.rimDirection);
            // vec4 rim_params
            buffer.put(light.rimEnabled ? 1.0f : 0.0f)
                  .put(light.rimIntensity)
                  .put(light.rimPower)
                  .put(light.rimHardness);
            pIndex++;
        }
        // Pad remaining points (FLOATS_PER_POINT floats each)
        for (int i = pIndex; i < MAX_LIGHTS; i++) {
            for (int j = 0; j < FLOATS_PER_POINT; j++) buffer.put(0f);
        }

        // 3. Spot Lights (24 floats per light: 6 vec4s)
        int sIndex = 0;
        for (LightInstance light : spotLights) {
            if (sIndex >= MAX_LIGHTS) break;
            // vec4 pos_distance
            buffer.put(light.getShaderX()).put(light.getShaderY()).put(light.getShaderZ()).put(light.distance);
            // vec4 dir_inner
            buffer.put(light.getShaderDx()).put(light.getShaderDy()).put(light.getShaderDz()).put(light.innerAngle);
            // vec4 col_intensity
            buffer.put(light.r).put(light.g).put(light.b).put(light.intensity);
            // vec4 outer_padding
            buffer.put(light.outerAngle)
                  .put(light.fogEnabled ? 1.0f : 0.0f)
                  .put(light.fogDensity)
                  .put(light.fogAnisotropy);
            // vec4 fog_params2
            float goboIndex = (float) GoboManager.INSTANCE.getGoboIndex(light.goboName);
            buffer.put(light.fogDispersion).put(goboIndex).put(light.goboRotation).put(packOutline(light.outlineEnabled, light.outlineIntensity, light.outlineThickness));
            // vec4 shadow_params
            buffer.put(light.shadowEnabled ? 1.0f : 0.0f)
                  .put(light.shadowSoftness)
                  .put(light.shadowIntensity)
                  .put(light.rimDirection);
            // vec4 rim_params
            buffer.put(light.rimEnabled ? 1.0f : 0.0f)
                  .put(light.rimIntensity)
                  .put(light.rimPower)
                  .put(light.rimHardness);
            sIndex++;
        }
        // Pad remaining spots (FLOATS_PER_SPOT floats each)
        for (int i = sIndex; i < MAX_LIGHTS; i++) {
            for (int j = 0; j < FLOATS_PER_SPOT; j++) buffer.put(0f);
        }

        buffer.flip();

        GL43.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, ssboId);
        GL43.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, buffer, GL43.GL_DYNAMIC_DRAW);
        GL43.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, BINDING_POINT, ssboId);
        GL43.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, 0);

        MemoryUtil.memFree(buffer);
    }

    public static void delete() {
        if (ssboId != -1) {
            GL43.glDeleteBuffers(ssboId);
            ssboId = -1;
        }
    }

    public static float packOutline(boolean enabled, float intensity, float thickness) {
        int enabledBit = enabled ? 1 : 0;
        int intensityVal = Math.max(0, Math.min(255, Math.round((intensity / 20.0f) * 255.0f)));
        int thicknessVal = Math.max(0, Math.min(255, Math.round((thickness / 16.0f) * 255.0f)));
        int mantissa = enabledBit | (intensityVal << 1) | (thicknessVal << 9);
        int packed = 0x3F800000 | (mantissa & 0x7FFFFF);
        return Float.intBitsToFloat(packed);
    }
}
