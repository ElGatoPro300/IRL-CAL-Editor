package elgatopro300.cal_lights.manager;

import elgatopro300.cal_lights.CALLightsClient;
import elgatopro300.cal_lights.animation.LightAnimation;

import net.minecraft.client.MinecraftClient;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class LightSaveManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static String lastWorldId = null;

    public static void tick(MinecraftClient client) {
        if (client.world == null) {
            if (lastWorldId != null) {
                // Player left the world, save the last world's lights
                saveLights(lastWorldId);
                lastWorldId = null;
                // Clear lights on exit
                LightManager.INSTANCE.clear();
            }
            return;
        }

        String currentId = getCurrentWorldId(client);
        if (currentId == null) return;

        if (!currentId.equals(lastWorldId)) {
            if (lastWorldId != null) {
                // Save old world lights
                saveLights(lastWorldId);
            }
            // Load new world lights
            lastWorldId = currentId;
            loadLights(currentId);
        }
    }

    private static String getCurrentWorldId(MinecraftClient client) {
        if (client.isInSingleplayer() && client.getServer() != null) {
            return "singleplayer_" + client.getServer().getSaveProperties().getLevelName();
        } else if (client.getCurrentServerEntry() != null) {
            return "multiplayer_" + client.getCurrentServerEntry().address.replace(":", "_");
        } else if (client.world != null) {
            return "world_" + client.world.getRegistryKey().getValue().getPath();
        }
        return null;
    }

    private static File getSaveFile(String worldId) {
        File dir = new File(MinecraftClient.getInstance().runDirectory, "config/cal_lights/saved_lights");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        // Sanitize filename
        String safeId = worldId.replaceAll("[^a-zA-Z0-9_.-]", "_");
        return new File(dir, safeId + ".json");
    }

    public static void saveLights(String worldId) {
        if (worldId == null) return;
        File file = getSaveFile(worldId);
        try {
            List<LightInstanceDto> list = new ArrayList<>();
            // Collect both Point and Spot lights that are persistent
            for (LightInstance light : LightManager.INSTANCE.getPointLights()) {
                if (light.persistent) {
                    list.add(new LightInstanceDto(light));
                }
            }
            for (LightInstance light : LightManager.INSTANCE.getSpotLights()) {
                if (light.persistent) {
                    list.add(new LightInstanceDto(light));
                }
            }

            try (FileWriter writer = new FileWriter(file)) {
                GSON.toJson(list, writer);
            }
            CALLightsClient.LOGGER.info("Saved " + list.size() + " persistent lights for world: " + worldId);
        } catch (Exception e) {
            CALLightsClient.LOGGER.error("Failed to save lights for world: " + worldId, e);
        }
    }

    public static void loadLights(String worldId) {
        if (worldId == null) return;
        // First clear current lights
        LightManager.INSTANCE.clear();

        File file = getSaveFile(worldId);
        if (!file.exists()) {
            CALLightsClient.LOGGER.info("No saved lights found for world: " + worldId);
            return;
        }

        try (FileReader reader = new FileReader(file)) {
            Type listType = new TypeToken<ArrayList<LightInstanceDto>>(){}.getType();
            List<LightInstanceDto> dtos = GSON.fromJson(reader, listType);
            if (dtos != null) {
                for (LightInstanceDto dto : dtos) {
                    if (dto.isSpot) {
                        LightInstance light = LightManager.INSTANCE.updateSpot(
                            dto.id, dto.x, dto.y, dto.z, dto.dx, dto.dy, dto.dz,
                            dto.r, dto.g, dto.b, dto.intensity, dto.innerAngle, dto.outerAngle, dto.distance
                        );
                        light.persistent = true;
                        light.visible = dto.visible;
                        light.fogEnabled = dto.fogEnabled;
                        light.name = dto.name == null ? "" : dto.name;
                        light.fogDispersion = dto.fogDispersion == 0.0f ? 1.0f : dto.fogDispersion;
                        light.fogDensity = dto.fogDensity == 0.0f ? 1.0f : dto.fogDensity;
                        light.fogAnisotropy = dto.fogAnisotropy == 0.0f ? 0.3f : dto.fogAnisotropy;
                        light.shadowEnabled = dto.shadowEnabled;
                        light.shadowSoftness = dto.shadowSoftness == 0.0f ? 1.0f : dto.shadowSoftness;
                        light.shadowIntensity = dto.shadowIntensity == 0.0f ? 0.8f : dto.shadowIntensity;
                        light.flareEnabled = dto.flareEnabled;
                        light.flareSize = dto.flareSize == 0.0f ? 1.0f : dto.flareSize;
                        light.flareGlowSize = dto.flareGlowSize != 0.0f ? dto.flareGlowSize : (dto.flareSize == 0.0f ? 1.5f : dto.flareSize);
                        light.flareGlowIntensity = dto.flareGlowIntensity == 0.0f ? 1.0f : dto.flareGlowIntensity;
                        light.flareRayLength = dto.flareRayLength == 0.0f ? 5.0f : dto.flareRayLength;
                        light.flareRayThickness = dto.flareRayThickness == 0.0f ? 0.3f : dto.flareRayThickness;
                        light.flareRayLength2 = dto.flareRayLength2 == 0.0f ? 5.0f : dto.flareRayLength2;
                        light.flareRayThickness2 = dto.flareRayThickness2 == 0.0f ? 0.3f : dto.flareRayThickness2;
                        light.flareRayLength3 = dto.flareRayLength3 == 0.0f ? 2.5f : dto.flareRayLength3;
                        light.flareRayThickness3 = dto.flareRayThickness3 == 0.0f ? 0.2f : dto.flareRayThickness3;
                        light.flareRotation = dto.flareRotation;
                        light.flareStartAngle = dto.flareStartAngle == 0.0f ? 20.0f : dto.flareStartAngle;
                        light.flareEndAngle = dto.flareEndAngle == 0.0f ? 45.0f : dto.flareEndAngle;
                        light.goboName = dto.goboName == null ? "None" : dto.goboName;
                        light.goboRotation = dto.goboRotation;
                        light.rimEnabled = dto.rimEnabled;
                        light.rimIntensity = dto.rimIntensity == 0.0f ? 2.0f : dto.rimIntensity;
                        light.rimPower = dto.rimPower == 0.0f ? 3.0f : dto.rimPower;
                        light.rimHardness = dto.rimHardness;
                        light.rimDirection = dto.rimDirection;
                        light.outlineEnabled = dto.outlineEnabled;
                        light.outlineIntensity = dto.outlineIntensity == 0.0f ? 4.0f : dto.outlineIntensity;
                        light.outlineThickness = dto.outlineThickness == 0.0f ? 3.0f : dto.outlineThickness;
                        // Restore rotation values if present in DTO
                        if (dto.rx != 0.0f || dto.ry != 0.0f || dto.rz != 0.0f) {
                            light.rx = dto.rx;
                            light.ry = dto.ry;
                            light.rz = dto.rz;
                        }
                        // Restore animation
                        if (dto.animation != null) {
                            light.animation = dto.animation;
                        }
                    } else {
                        LightInstance light = LightManager.INSTANCE.updatePoint(
                            dto.id, dto.x, dto.y, dto.z, dto.r, dto.g, dto.b, dto.intensity, dto.radius
                        );
                        light.persistent = true;
                        light.visible = dto.visible;
                        light.fogEnabled = dto.fogEnabled;
                        light.name = dto.name == null ? "" : dto.name;
                        light.fogDispersion = dto.fogDispersion == 0.0f ? 1.0f : dto.fogDispersion;
                        light.fogDensity = dto.fogDensity == 0.0f ? 1.0f : dto.fogDensity;
                        light.fogAnisotropy = dto.fogAnisotropy == 0.0f ? 0.3f : dto.fogAnisotropy;
                        light.shadowEnabled = dto.shadowEnabled;
                        light.shadowSoftness = dto.shadowSoftness == 0.0f ? 1.0f : dto.shadowSoftness;
                        light.shadowIntensity = dto.shadowIntensity == 0.0f ? 0.8f : dto.shadowIntensity;
                        light.flareEnabled = dto.flareEnabled;
                        light.flareSize = dto.flareSize == 0.0f ? 1.0f : dto.flareSize;
                        light.flareGlowSize = dto.flareGlowSize != 0.0f ? dto.flareGlowSize : (dto.flareSize == 0.0f ? 1.5f : dto.flareSize);
                        light.flareGlowIntensity = dto.flareGlowIntensity == 0.0f ? 1.0f : dto.flareGlowIntensity;
                        light.flareRayLength = dto.flareRayLength == 0.0f ? 5.0f : dto.flareRayLength;
                        light.flareRayThickness = dto.flareRayThickness == 0.0f ? 0.3f : dto.flareRayThickness;
                        light.flareRayLength2 = dto.flareRayLength2 == 0.0f ? 5.0f : dto.flareRayLength2;
                        light.flareRayThickness2 = dto.flareRayThickness2 == 0.0f ? 0.3f : dto.flareRayThickness2;
                        light.flareRayLength3 = dto.flareRayLength3 == 0.0f ? 2.5f : dto.flareRayLength3;
                        light.flareRayThickness3 = dto.flareRayThickness3 == 0.0f ? 0.2f : dto.flareRayThickness3;
                        light.flareRotation = dto.flareRotation;
                        light.flareStartAngle = dto.flareStartAngle == 0.0f ? 20.0f : dto.flareStartAngle;
                        light.flareEndAngle = dto.flareEndAngle == 0.0f ? 45.0f : dto.flareEndAngle;
                        light.rimEnabled = dto.rimEnabled;
                        light.rimIntensity = dto.rimIntensity == 0.0f ? 2.0f : dto.rimIntensity;
                        light.rimPower = dto.rimPower == 0.0f ? 3.0f : dto.rimPower;
                        light.rimHardness = dto.rimHardness;
                        light.rimDirection = dto.rimDirection;
                        light.outlineEnabled = dto.outlineEnabled;
                        light.outlineIntensity = dto.outlineIntensity == 0.0f ? 4.0f : dto.outlineIntensity;
                        light.outlineThickness = dto.outlineThickness == 0.0f ? 3.0f : dto.outlineThickness;
                        // Restore animation
                        if (dto.animation != null) {
                            light.animation = dto.animation;
                        }
                    }
                }
                CALLightsClient.LOGGER.info("Loaded " + dtos.size() + " persistent lights for world: " + worldId);
            }
        } catch (Exception e) {
            CALLightsClient.LOGGER.error("Failed to load lights for world: " + worldId, e);
        }
    }

    public static void forceSaveCurrent() {
        if (lastWorldId != null) {
            saveLights(lastWorldId);
        }
    }

    public static class LightInstanceDto {
        public int id;
        public String name = "";
        public boolean isSpot;
        public float x, y, z;
        public float r, g, b;
        public float intensity;
        public float radius;
        public float dx, dy, dz;
        public float innerAngle, outerAngle, distance;
        public boolean visible;
        public boolean fogEnabled;
        public float fogDispersion;
        public float fogDensity;
        public float fogAnisotropy;
        public float rx, ry, rz;
        public boolean shadowEnabled;
        public float shadowSoftness;
        public float shadowIntensity;
        public boolean flareEnabled;
        public float flareSize;
        public float flareGlowSize;
        public float flareGlowIntensity;
        public float flareRayLength;
        public float flareRayThickness;
        public float flareRayLength2;
        public float flareRayThickness2;
        public float flareRayLength3;
        public float flareRayThickness3;
        public float flareRotation;
        public float flareStartAngle;
        public float flareEndAngle;
        public String goboName = "None";
        public float goboRotation;
        public boolean rimEnabled;
        public float rimIntensity;
        public float rimPower;
        public float rimHardness;
        public float rimDirection;
        public boolean outlineEnabled;
        public float outlineIntensity;
        public float outlineThickness;
        /** Serialized animation (nullable). Gson handles nested deserialization. */
        public LightAnimation animation;

        public LightInstanceDto() {}

        public LightInstanceDto(LightInstance light) {
            this.id = light.id;
            this.name = light.name == null ? "" : light.name;
            this.isSpot = light.isSpot;
            this.x = light.x;
            this.y = light.y;
            this.z = light.z;
            this.r = light.r;
            this.g = light.g;
            this.b = light.b;
            this.intensity = light.intensity;
            this.radius = light.radius;
            this.dx = light.dx;
            this.dy = light.dy;
            this.dz = light.dz;
            this.innerAngle = light.innerAngle;
            this.outerAngle = light.outerAngle;
            this.distance = light.distance;
            this.visible = light.visible;
            this.fogEnabled = light.fogEnabled;
            this.fogDispersion = light.fogDispersion;
            this.fogDensity = light.fogDensity;
            this.fogAnisotropy = light.fogAnisotropy;
            this.rx = light.rx;
            this.ry = light.ry;
            this.rz = light.rz;
            this.shadowEnabled = light.shadowEnabled;
            this.shadowSoftness = light.shadowSoftness;
            this.shadowIntensity = light.shadowIntensity;
            this.flareEnabled = light.flareEnabled;
            this.flareSize = light.flareSize;
            this.flareGlowSize = light.flareGlowSize;
            this.flareGlowIntensity = light.flareGlowIntensity;
            this.flareRayLength = light.flareRayLength;
            this.flareRayThickness = light.flareRayThickness;
            this.flareRayLength2 = light.flareRayLength2;
            this.flareRayThickness2 = light.flareRayThickness2;
            this.flareRayLength3 = light.flareRayLength3;
            this.flareRayThickness3 = light.flareRayThickness3;
            this.flareRotation = light.flareRotation;
            this.flareStartAngle = light.flareStartAngle;
            this.flareEndAngle = light.flareEndAngle;
            this.goboName = light.goboName == null ? "None" : light.goboName;
            this.goboRotation = light.goboRotation;
            this.rimEnabled = light.rimEnabled;
            this.rimIntensity = light.rimIntensity;
            this.rimPower = light.rimPower;
            this.rimHardness = light.rimHardness;
            this.rimDirection = light.rimDirection;
            this.outlineEnabled = light.outlineEnabled;
            this.outlineIntensity = light.outlineIntensity;
            this.outlineThickness = light.outlineThickness;
            this.animation = light.animation;
        }
    }
}
