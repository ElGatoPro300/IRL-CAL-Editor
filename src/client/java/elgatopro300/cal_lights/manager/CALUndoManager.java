package elgatopro300.cal_lights.manager;

import elgatopro300.cal_lights.gizmo.LightGizmo;
import elgatopro300.cal_lights.ui.CALEditorScreen;
import elgatopro300.cal_lights.ui.panels.CALEditorPanel;

import net.minecraft.client.MinecraftClient;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class CALUndoManager {
    private static final Gson GSON = new Gson();
    private static final int MAX_HISTORY = 50;

    private static final List<String> undoStack = new ArrayList<>();
    private static final List<String> redoStack = new ArrayList<>();

    public static class CALUndoState {
        public int selectedLightId;
        public List<LightSaveManager.LightInstanceDto> lights;

        public CALUndoState(int selectedLightId, List<LightSaveManager.LightInstanceDto> lights) {
            this.selectedLightId = selectedLightId;
            this.lights = lights;
        }
    }

    public static void pushState() {
        try {
            List<LightSaveManager.LightInstanceDto> dtos = new ArrayList<>();
            for (LightInstance light : LightManager.INSTANCE.getPointLights()) {
                if (light.persistent) {
                    dtos.add(new LightSaveManager.LightInstanceDto(light));
                }
            }
            for (LightInstance light : LightManager.INSTANCE.getSpotLights()) {
                if (light.persistent) {
                    dtos.add(new LightSaveManager.LightInstanceDto(light));
                }
            }
            int selectedId = -1;
            LightInstance selected = LightGizmo.INSTANCE.getSelectedLight();
            if (selected != null) {
                selectedId = selected.id;
            }
            CALUndoState state = new CALUndoState(selectedId, dtos);
            String json = GSON.toJson(state);
            undoStack.add(json);
            if (undoStack.size() > MAX_HISTORY) {
                undoStack.remove(0);
            }
            redoStack.clear();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void undo() {
        if (undoStack.isEmpty()) return;
        try {
            List<LightSaveManager.LightInstanceDto> dtos = new ArrayList<>();
            for (LightInstance light : LightManager.INSTANCE.getPointLights()) {
                if (light.persistent) {
                    dtos.add(new LightSaveManager.LightInstanceDto(light));
                }
            }
            for (LightInstance light : LightManager.INSTANCE.getSpotLights()) {
                if (light.persistent) {
                    dtos.add(new LightSaveManager.LightInstanceDto(light));
                }
            }
            int selectedId = -1;
            LightInstance selected = LightGizmo.INSTANCE.getSelectedLight();
            if (selected != null) {
                selectedId = selected.id;
            }
            CALUndoState currentState = new CALUndoState(selectedId, dtos);
            redoStack.add(GSON.toJson(currentState));

            String json = undoStack.remove(undoStack.size() - 1);
            restoreState(json);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void redo() {
        if (redoStack.isEmpty()) return;
        try {
            List<LightSaveManager.LightInstanceDto> dtos = new ArrayList<>();
            for (LightInstance light : LightManager.INSTANCE.getPointLights()) {
                if (light.persistent) {
                    dtos.add(new LightSaveManager.LightInstanceDto(light));
                }
            }
            for (LightInstance light : LightManager.INSTANCE.getSpotLights()) {
                if (light.persistent) {
                    dtos.add(new LightSaveManager.LightInstanceDto(light));
                }
            }
            int selectedId = -1;
            LightInstance selected = LightGizmo.INSTANCE.getSelectedLight();
            if (selected != null) {
                selectedId = selected.id;
            }
            CALUndoState currentState = new CALUndoState(selectedId, dtos);
            undoStack.add(GSON.toJson(currentState));

            String json = redoStack.remove(redoStack.size() - 1);
            restoreState(json);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void restoreState(String json) {
        LightManager.INSTANCE.clear();
        Type stateType = new TypeToken<CALUndoState>(){}.getType();
        CALUndoState state = GSON.fromJson(json, stateType);
        if (state != null && state.lights != null) {
            for (LightSaveManager.LightInstanceDto dto : state.lights) {
                if (dto.isSpot) {
                    LightInstance light = LightManager.INSTANCE.updateSpot(
                        dto.id, dto.x, dto.y, dto.z, dto.dx, dto.dy, dto.dz,
                        dto.r, dto.g, dto.b, dto.intensity, dto.innerAngle, dto.outerAngle, dto.distance
                    );
                    light.persistent = true;
                    light.visible = dto.visible;
                    light.fogEnabled = dto.fogEnabled;
                    light.name = dto.name == null ? "" : dto.name;
                    light.beamStrength = dto.fogDispersion;
                    light.vlDensity = dto.fogDensity;
                    light.anisotropy = dto.fogAnisotropy;
                    light.shadowEnabled = dto.shadowEnabled;
                    light.bulbSize = dto.shadowSoftness;
                    light.entitiesOnly = dto.entitiesOnly;
                    light.blocksOnly = dto.blocksOnly;
                    light.goboName = dto.goboName == null ? "None" : dto.goboName;
                    light.goboRotation = dto.goboRotation;
                    light.cookieScale = dto.cookieScale == 0.0f ? 1.0f : dto.cookieScale;
                    light.cookieInvert = dto.cookieInvert;
                    light.rx = dto.rx;
                    light.ry = dto.ry;
                    light.rz = dto.rz;
                    light.animation = dto.animation;
                } else {
                    LightInstance light = LightManager.INSTANCE.updatePoint(
                        dto.id, dto.x, dto.y, dto.z, dto.r, dto.g, dto.b, dto.intensity, dto.radius
                    );
                    light.persistent = true;
                    light.visible = dto.visible;
                    light.fogEnabled = dto.fogEnabled;
                    light.name = dto.name == null ? "" : dto.name;
                    light.beamStrength = dto.fogDispersion;
                    light.vlDensity = dto.fogDensity;
                    light.anisotropy = dto.fogAnisotropy;
                    light.shadowEnabled = dto.shadowEnabled;
                    light.bulbSize = dto.shadowSoftness;
                    light.entitiesOnly = dto.entitiesOnly;
                    light.blocksOnly = dto.blocksOnly;
                    light.animation = dto.animation;
                }
            }

            // Restore selection
            int selId = state.selectedLightId;
            LightInstance selected = LightManager.INSTANCE.getPointLights().stream().filter(l -> l.id == selId).findFirst().orElse(
                LightManager.INSTANCE.getSpotLights().stream().filter(l -> l.id == selId).findFirst().orElse(null)
            );
            LightGizmo.INSTANCE.setSelectedLight(selected);
        }

        // Rebuild UI editor settings
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.currentScreen instanceof CALEditorScreen screen) {
            if (screen.getRoot() instanceof CALEditorPanel p) {
                p.rebuildSettings();
                p.resize(p.x, p.y, p.w, p.h);
            }
        }
    }
}
