package elgatopro300.cal_lights.manager;

import elgatopro300.cal_lights.CALLightsClient;
import elgatopro300.cal_lights.animation.LightAnimation;

import elgatopro300.cal_lights.light.LightConfig;
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
            LightConfig.holdBake = LightConfig.holdBakeOnJoin;
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
                        light.beamStrength = dto.fogDispersion == 0.0f ? 1.0f : dto.fogDispersion;
                        light.vlDensity = dto.fogDensity == 0.0f ? 0.05f : dto.fogDensity;
                        light.anisotropy = dto.fogAnisotropy == 0.0f ? 0.4f : dto.fogAnisotropy;
                        light.shadowEnabled = dto.shadowEnabled;
                        light.bulbSize = dto.shadowSoftness;
                        light.entitiesOnly = dto.entitiesOnly;
                        light.blocksOnly = dto.blocksOnly;
                        light.goboName = dto.goboName == null ? "None" : dto.goboName;
                        light.goboRotation = dto.goboRotation;
                        light.cookieScale = dto.cookieScale == 0.0f ? 1.0f : dto.cookieScale;
                        light.cookieInvert = dto.cookieInvert;
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
                        light.beamStrength = dto.fogDispersion == 0.0f ? 1.0f : dto.fogDispersion;
                        light.vlDensity = dto.fogDensity == 0.0f ? 0.05f : dto.fogDensity;
                        light.anisotropy = dto.fogAnisotropy == 0.0f ? 0.4f : dto.fogAnisotropy;
                        light.shadowEnabled = dto.shadowEnabled;
                        light.bulbSize = dto.shadowSoftness;
                        light.entitiesOnly = dto.entitiesOnly;
                        light.blocksOnly = dto.blocksOnly;
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
        public double x, y, z;
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
        public boolean entitiesOnly;
        public boolean blocksOnly;
        public String goboName = "None";
        public float goboRotation;
        public float cookieScale = 1.0f;
        public boolean cookieInvert;
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
            this.innerAngle = light.angle;
            this.outerAngle = light.soft;
            this.distance = light.distance;
            this.visible = light.visible;
            this.fogEnabled = light.fogEnabled;
            this.fogDispersion = light.beamStrength;
            this.fogDensity = light.vlDensity;
            this.fogAnisotropy = light.anisotropy;
            this.rx = light.rx;
            this.ry = light.ry;
            this.rz = light.rz;
            this.shadowEnabled = light.shadowEnabled;
            this.shadowSoftness = light.bulbSize;
            this.entitiesOnly = light.entitiesOnly;
            this.blocksOnly = light.blocksOnly;
            this.goboName = light.goboName == null ? "None" : light.goboName;
            this.goboRotation = light.goboRotation;
            this.cookieScale = light.cookieScale;
            this.cookieInvert = light.cookieInvert;
            this.animation = light.animation;
        }
    }
}
