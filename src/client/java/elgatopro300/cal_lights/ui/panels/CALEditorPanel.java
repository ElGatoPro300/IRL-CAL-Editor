package elgatopro300.cal_lights.ui.panels;

import elgatopro300.cal_lights.CALLightsClient;
import elgatopro300.cal_lights.animation.LightAnimation;
import elgatopro300.cal_lights.gizmo.LightGizmo;
import elgatopro300.cal_lights.graphics.CalLightsIcons;
import elgatopro300.cal_lights.light.LightConfig;
import elgatopro300.cal_lights.light.auto.AutoLightManager;
import elgatopro300.cal_lights.manager.CALUndoManager;
import elgatopro300.cal_lights.manager.LightInstance;
import elgatopro300.cal_lights.manager.LightManager;
import elgatopro300.cal_lights.manager.LightSaveManager;
import elgatopro300.cal_lights.ui.CALKeys;
import elgatopro300.cal_lights.ui.CLUIContext;
import elgatopro300.cal_lights.ui.CLUIElement;
import elgatopro300.cal_lights.ui.CalSettings;
import elgatopro300.cal_lights.ui.IKey;
import elgatopro300.cal_lights.ui.elements.CLUITrackpad;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import com.google.gson.Gson;

import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class CALEditorPanel extends CLUIElement {
    public static LightSaveManager.LightInstanceDto copiedLight = null;
    public static boolean showLeftSidebar = true;
    public static boolean showRightSidebar = true;
    public static boolean showTimeline = false;
    public static int leftSidebarW = 180;
    public static int rightSidebarW = 200;
    public static int timelineHeight = 140;
    private boolean draggingLeftBorder = false;
    private boolean draggingRightBorder = false;
    private boolean draggingTimelineBorder = false;
    private int activeMenuDropdown = 0; // 0 = none, 1 = Files, 2 = Windows

    // Animated panel dimensions for premium smooth slide transitions
    public boolean closing = false;
    private float currentTopMenuY = -999f;
    private float currentStatusBarY = -999f;
    private float currentTimelineH = -1f;
    private float currentRightPanelW = -1f;
    private float currentLeftPanelW = -1f;

    // Animated popup scales for zoom animations
    private float settingsPopupScale = 0.0f;
    private float patcherPopupScale = 0.0f;

    // Settings Popup State
    public boolean showSettingsPopup = false;
    public boolean showPatcherPopup = false;
    private boolean draggingGizmoSlider = false;
    private int selectedSettingsCategory = 0; // 0 = General, 1 = Interfaz, 2 = Teclas, 3 = Motor
    private boolean showLanguageDropdown = false;
    public static int activeRebindingKeyIndex = -1;

    // Patcher Panel
    private CALShaderPatcherPanel patcherPanel = null;

    // Motor Settings Trackpads
    private final CLUITrackpad trackShadowBlockRadius;
    private final CLUITrackpad trackAutoLightIntensity;
    private final CLUITrackpad trackAutoLightReach;
    private final CLUITrackpad trackAutoLightRadius;
    private final CLUITrackpad trackAutoLightMax;

    public static final IKey[] KEY_NAMES = {
        CALKeys.KEY_PLAY_PAUSE, CALKeys.KEY_ADD_KEYFRAME, CALKeys.KEY_DELETE_KEYFRAME,
        CALKeys.KEY_UNDO, CALKeys.KEY_REDO, CALKeys.KEY_SELECT_ALL,
        CALKeys.KEY_COPY, CALKeys.KEY_PASTE, CALKeys.KEY_CUT,
        CALKeys.KEY_TOGGLE_PANELS, CALKeys.KEY_QUICK_LIGHT, CALKeys.KEY_OPEN_CLOSE
    };

    public static int getSettingKey(int idx) {
        return switch (idx) {
            case 0 -> CalSettings.INSTANCE.keyPlayPause;
            case 1 -> CalSettings.INSTANCE.keyAddKeyframe;
            case 2 -> CalSettings.INSTANCE.keyDeleteKeyframe;
            default -> 0;
        };
    }

    public static void setSettingKey(int idx, int keyCode) {
        switch (idx) {
            case 0 -> CalSettings.INSTANCE.keyPlayPause = keyCode;
            case 1 -> CalSettings.INSTANCE.keyAddKeyframe = keyCode;
            case 2 -> CalSettings.INSTANCE.keyDeleteKeyframe = keyCode;
        }
        CalSettings.INSTANCE.save();
    }

    // Right-Click Context Popup State
    private boolean showRightClickPopup = false;
    private int rightClickPopupX = 0;
    private int rightClickPopupY = 0;
    private Vec3d rightClickWorldPos = null;
    private boolean isRightClickingLight = false;

    // Interaction diagnostics metrics
    public static double lastScrollX = 0;
    public static double lastScrollY = 1.0;
    public static int cameraSpeedIndex = 20;
    private int leftPanelScrollY = 0;

    public static double getCameraSpeed() {
        if (cameraSpeedIndex <= 10) return cameraSpeedIndex / 100.0;
        else if (cameraSpeedIndex <= 20) return (cameraSpeedIndex - 10) / 10.0;
        else if (cameraSpeedIndex <= 30) return (cameraSpeedIndex - 20) / 1.0;
        return (cameraSpeedIndex - 30) * 10.0;
    }

    public static final List<Integer> currentlyPressedKeys = new ArrayList<>();

    private final List<LightInstance> cachedLights = new ArrayList<>();
    private CLUIElement activeSettingsPanel = null;
    private AnimationEditorPanel timelinePanel = null;
    private LightInstance lastSelected = null;

    private final int topMenuH = 20;
    private final int tabsH = 20;
    private final int toolbarH = 20;
    private final int statusBarH = 16;

    // Search and filter
    private String searchQuery = "";
    public boolean searchFocused = false;

    public CALEditorPanel() {
        this.trackShadowBlockRadius = new CLUITrackpad(CALKeys.SHADOW_BLOCK_RADIUS.get(), LightConfig.shadowBlockRadius, 4f, 96f, val -> {
            LightConfig.shadowBlockRadius = Math.round(val);
        }).setArrowStep(1f);
        this.trackAutoLightIntensity = new CLUITrackpad(CALKeys.AUTO_LIGHT_INTENSITY.get(), LightConfig.autoLightIntensity, 0.0f, 5.0f, val -> {
            LightConfig.autoLightIntensity = val;
        }).setArrowStep(0.05f);
        this.trackAutoLightReach = new CLUITrackpad(CALKeys.AUTO_LIGHT_REACH.get(), LightConfig.autoLightReach, 0.25f, 3.0f, val -> {
            LightConfig.autoLightReach = val;
        }).setArrowStep(0.05f);
        this.trackAutoLightRadius = new CLUITrackpad(CALKeys.AUTO_LIGHT_RADIUS.get(), LightConfig.autoLightRadius, 8f, 96f, val -> {
            LightConfig.autoLightRadius = Math.round(val);
        }).setArrowStep(1f);
        this.trackAutoLightMax = new CLUITrackpad(CALKeys.AUTO_LIGHT_MAX.get(), LightConfig.autoLightMax, 0f, 2000f, val -> {
            LightConfig.autoLightMax = Math.round(val);
        }).setArrowStep(10f);

        this.patcherPanel = new CALShaderPatcherPanel(() -> {
            showPatcherPopup = false;
        });

        rebuildSettings();
    }

    public void rebuildSettings() {
        if (showTimeline && showRightSidebar) {
            showTimeline = false;
        }
        CLUITrackpad.activeEditingTrackpad = null;
        int leftPanelW = showLeftSidebar ? leftSidebarW : 0;
        int rightPanelW = showRightSidebar ? rightSidebarW : 0;
        LightInstance selected = LightGizmo.INSTANCE.getSelectedLight();
        if (selected == null) {
            activeSettingsPanel = null;
            timelinePanel = null;
        } else {
            activeSettingsPanel = new LightInspectorPanel(selected, () -> {});
            timelinePanel = new AnimationEditorPanel(selected, () -> {});
        }
        
        int timelineH = (showTimeline && selected != null) ? timelineHeight : 0;
        if (activeSettingsPanel != null) {
            activeSettingsPanel.resize(x + w - rightPanelW, y + topMenuH + tabsH, rightPanelW, h - topMenuH - tabsH - statusBarH);
        }
        if (timelinePanel != null) {
            timelinePanel.resize(x + leftPanelW, y + h - statusBarH - timelineH, w - leftPanelW - rightPanelW, timelineH);
        }
        lastSelected = selected;
    }

    @Override
    public void resize(int px, int py, int pw, int ph) {
        super.resize(px, py, pw, ph);
        int leftPanelW = showLeftSidebar ? leftSidebarW : 0;
        int rightPanelW = showRightSidebar ? rightSidebarW : 0;
        LightInstance selected = LightGizmo.INSTANCE.getSelectedLight();
        int timelineH = (showTimeline && selected != null) ? timelineHeight : 0;
        if (activeSettingsPanel != null) {
            activeSettingsPanel.resize(px + pw - rightPanelW, py + topMenuH + tabsH, rightPanelW, ph - topMenuH - tabsH - statusBarH);
        }
        if (timelinePanel != null) {
            timelinePanel.resize(px + leftPanelW, py + ph - statusBarH - timelineH, pw - leftPanelW - rightPanelW, timelineH);
        }
    }

    @Override
    public void render(CLUIContext ctx) {
        LightInstance selected = LightGizmo.INSTANCE.getSelectedLight();

        float targetLeftPanelW = closing ? 0 : (showLeftSidebar ? leftSidebarW : 0);
        float targetRightPanelW = closing ? 0 : (showRightSidebar ? rightSidebarW : 0);
        float targetTimelineH = closing ? 0 : ((showTimeline && selected != null) ? timelineHeight : 0);

        float targetTopMenuY = closing ? -topMenuH : 0;
        float targetStatusBarY = closing ? h : (h - statusBarH);

        float targetSettingsScale = showSettingsPopup ? 1.0f : 0.0f;
        float targetPatcherScale = showPatcherPopup ? 1.0f : 0.0f;

        if (CalSettings.INSTANCE.simplifyAnimations) {
            settingsPopupScale = targetSettingsScale;
            patcherPopupScale = targetPatcherScale;
        } else {
            settingsPopupScale += (targetSettingsScale - settingsPopupScale) * 0.25f;
            patcherPopupScale += (targetPatcherScale - patcherPopupScale) * 0.25f;
        }

        if (currentLeftPanelW < 0) {
            currentLeftPanelW = 0f;
        }
        if (currentRightPanelW < 0) {
            currentRightPanelW = 0f;
        }
        if (currentTimelineH < 0) {
            currentTimelineH = 0f;
        }
        if (currentTopMenuY < -900f) {
            currentTopMenuY = -topMenuH;
        }
        if (currentStatusBarY < -900f) {
            currentStatusBarY = h;
        }

        if (CalSettings.INSTANCE.simplifyAnimations) {
            currentLeftPanelW = targetLeftPanelW;
            currentRightPanelW = targetRightPanelW;
            currentTimelineH = targetTimelineH;
            currentTopMenuY = targetTopMenuY;
            currentStatusBarY = targetStatusBarY;
        } else {
            float diffL = targetLeftPanelW - currentLeftPanelW;
            float diffW = targetRightPanelW - currentRightPanelW;
            float diffH = targetTimelineH - currentTimelineH;
            float diffTop = targetTopMenuY - currentTopMenuY;
            float diffBot = targetStatusBarY - currentStatusBarY;
            
            if (Math.abs(diffL) > 0.1f) {
                currentLeftPanelW += diffL * 0.2f;
            } else {
                currentLeftPanelW = targetLeftPanelW;
            }

            if (Math.abs(diffW) > 0.1f) {
                currentRightPanelW += diffW * 0.2f;
            } else {
                currentRightPanelW = targetRightPanelW;
            }

            if (Math.abs(diffH) > 0.1f) {
                currentTimelineH += diffH * 0.2f;
            } else {
                currentTimelineH = targetTimelineH;
            }

            if (Math.abs(diffTop) > 0.1f) {
                currentTopMenuY += diffTop * 0.2f;
            } else {
                currentTopMenuY = targetTopMenuY;
            }

            if (Math.abs(diffBot) > 0.1f) {
                currentStatusBarY += diffBot * 0.2f;
            } else {
                currentStatusBarY = targetStatusBarY;
            }
        }

        int animLeftPanelW = (int) currentLeftPanelW;
        int animRightPanelW = (int) currentRightPanelW;
        int animTimelineH = (int) currentTimelineH;

        int topY = (int) currentTopMenuY;
        int botY = (int) currentStatusBarY;
        int contentH = botY - animTimelineH;

        // Hover diagnostics for border dragging
        boolean hoverLeftBorder = showLeftSidebar && ctx.mouseX >= animLeftPanelW - 3 && ctx.mouseX <= animLeftPanelW + 3 && ctx.mouseY >= topY + topMenuH && ctx.mouseY < botY;
        boolean hoverRightBorder = showRightSidebar && ctx.mouseX >= w - animRightPanelW - 3 && ctx.mouseX <= w - animRightPanelW + 3 && ctx.mouseY >= topY + topMenuH && ctx.mouseY < botY;

        if (selected != lastSelected) {
            rebuildSettings();
        }

        // Dynamically resize active children
        if (activeSettingsPanel != null && animRightPanelW > 0) {
            activeSettingsPanel.resize(x + w - animRightPanelW, y + topY + topMenuH + tabsH, animRightPanelW, botY - topY - topMenuH - tabsH);
        }
        if (timelinePanel != null && animTimelineH > 0) {
            timelinePanel.resize(x + animLeftPanelW, y + botY - animTimelineH, w - animLeftPanelW - animRightPanelW, animTimelineH);
        }

        // Draw solid backgrounds over side panels and top menu (Frame the 3D world!)
        // 1. Top menu bar background
        ctx.batcher.box(0, topY, w, topY + topMenuH, 0xFF121216);
        ctx.batcher.outline(0, topY, w, topY + topMenuH, 0xFF1C1C24, 1);

        // 2. Left Panel background
        if (animLeftPanelW > 0) {
            ctx.batcher.box(0, topY + topMenuH, animLeftPanelW, botY, 0xFF141418);
            ctx.batcher.outline(0, topY + topMenuH, animLeftPanelW, botY, 0xFF22222A, 1);
        }

        // 3. Right Panel background
        if (animRightPanelW > 0) {
            ctx.batcher.box(w - animRightPanelW, topY + topMenuH, w, botY, 0xFF141418);
            ctx.batcher.outline(w - animRightPanelW, topY + topMenuH, w, botY, 0xFF22222A, 1);
        }



        // --- RENDER TOP MENU ITEMS ---
        ctx.batcher.text(CALKeys.TITLE.get(), 8, topY + 6, 0xFFFFAA00);

        // Files Tab
        boolean hoverFiles = ctx.mouseX >= 110 && ctx.mouseX < 165 && ctx.mouseY >= 0 && ctx.mouseY < topMenuH;
        int filesCol = (activeMenuDropdown == 1) ? 0xFFFFAA00 : (hoverFiles ? 0xFFFFFFFF : 0xFFCCCCCC);
        ctx.batcher.text(CALKeys.FILES.get(), 115, topY + 6, filesCol);

        // Windows Tab
        boolean hoverWindows = ctx.mouseX >= 165 && ctx.mouseX < 240 && ctx.mouseY >= 0 && ctx.mouseY < topMenuH;
        int windowsCol = (activeMenuDropdown == 2) ? 0xFFFFAA00 : (hoverWindows ? 0xFFFFFFFF : 0xFFCCCCCC);
        ctx.batcher.text(CALKeys.WINDOWS.get(), 170, topY + 6, windowsCol);

        // --- RENDER TAB HEADERS ---
        // Solid background and outline for the central tab row space so the light icons button is beautifully covered
        ctx.batcher.box(animLeftPanelW, topY + topMenuH, w - animRightPanelW, topY + topMenuH + tabsH, 0xFF121216);
        ctx.batcher.outline(animLeftPanelW, topY + topMenuH, w - animRightPanelW, topY + topMenuH + tabsH, 0xFF1C1C24, 1);
        // Tab Left (Esquema)
        if (animLeftPanelW > 0) {
            ctx.batcher.box(0, topY + topMenuH, animLeftPanelW, topY + topMenuH + tabsH, 0xFF1C1C24);
            ctx.batcher.text(CALKeys.SCHEME.get(), 10, topY + topMenuH + 5, 0xFFFFAA00);
            ctx.batcher.outline(0, topY + topMenuH, animLeftPanelW, topY + topMenuH + tabsH, 0xFF2A2A35, 1);
        }

        // Tab Right (Inspector)
        if (animRightPanelW > 0) {
            ctx.batcher.box(w - animRightPanelW, topY + topMenuH, w, topY + topMenuH + tabsH, 0xFF1C1C24);
            ctx.batcher.icon(CalLightsIcons.GEAR, w - animRightPanelW + 8, topY + topMenuH + 2, 0xFFFFAA00);
            ctx.batcher.text(CALKeys.INSPECTOR.get(), w - animRightPanelW + 26, topY + topMenuH + 5, 0xFFFFAA00);
            ctx.batcher.outline(w - animRightPanelW, topY + topMenuH, w, topY + topMenuH + tabsH, 0xFF2A2A35, 1);
        }

        // --- RENDER VIEWPORT FRAME ---
        ctx.batcher.outline(animLeftPanelW, topY + topMenuH + tabsH, w - animRightPanelW, contentH, 0xFF22222A, 1);

        // --- VIEWPORT TOOLBAR CONTENT ---
        // Light Icons Toggle Button (Moved up into the Tab row!)
        boolean hoverIcons = ctx.mouseX >= animLeftPanelW + 10 && ctx.mouseX < animLeftPanelW + 110 && ctx.mouseY >= topY + topMenuH + 2 && ctx.mouseY < topY + topMenuH + 18;
        int iconsBg = LightGizmo.renderLightIcons ? (hoverIcons ? 0xFF2E7D32 : 0xFF1B5E20) : (hoverIcons ? 0xFF424242 : 0xFF212121);
        ctx.batcher.box(animLeftPanelW + 10, topY + topMenuH + 2, animLeftPanelW + 110, topY + topMenuH + 18, iconsBg);
        ctx.batcher.outline(animLeftPanelW + 10, topY + topMenuH + 2, animLeftPanelW + 110, topY + topMenuH + 18, LightGizmo.renderLightIcons ? 0xFF81C784 : 0xFF555555, 1);
        ctx.batcher.icon(LightGizmo.renderLightIcons ? CalLightsIcons.VISIBLE : CalLightsIcons.INVISIBLE, animLeftPanelW + 14, topY + topMenuH + 2, 0xFFFFFFFF);
        ctx.batcher.text(CALKeys.LIGHT_ICONS.get(), animLeftPanelW + 30, topY + topMenuH + 5, 0xFFFFFFFF);

        // Alternating Viewport Panel Toggle Button
        boolean hoverToggle = ctx.mouseX >= animLeftPanelW + 115 && ctx.mouseX < animLeftPanelW + 225 && ctx.mouseY >= topY + topMenuH + 2 && ctx.mouseY < topY + topMenuH + 18;
        int toggleBg = hoverToggle ? 0xFF3A3A4A : 0xFF1E1E24;
        ctx.batcher.box(animLeftPanelW + 115, topY + topMenuH + 2, animLeftPanelW + 225, topY + topMenuH + 18, toggleBg);
        ctx.batcher.outline(animLeftPanelW + 115, topY + topMenuH + 2, animLeftPanelW + 225, topY + topMenuH + 18, hoverToggle ? 0xFFFFAA00 : 0xFF3E3E4D, 1);
        boolean showTime = showTimeline;
        ctx.batcher.icon(showTime ? CalLightsIcons.GEAR : CalLightsIcons.FILM, animLeftPanelW + 119, topY + topMenuH + 2, 0xFFFFAA00);
        ctx.batcher.text(showTime ? CALKeys.INSPECTOR.get() : CALKeys.TIMELINE.get(), animLeftPanelW + 137, topY + topMenuH + 5, 0xFFFFFFFF);

        // --- RENDER LEFT SIDEBAR (ESQUEMA / LIST) ---
        if (animLeftPanelW > 0) {
            // Search Box
            int searchY = topY + topMenuH + tabsH + 6;
            int searchBg = searchFocused ? 0xFF0D0D11 : 0xFF1A1A22;
            ctx.batcher.box(8, searchY, animLeftPanelW - 8, searchY + 16, searchBg);
            ctx.batcher.outline(8, searchY, animLeftPanelW - 8, searchY + 16, searchFocused ? 0xFFFFAA00 : 0xFF3E3E4D, 1);
            if (searchQuery.isEmpty()) {
                ctx.batcher.text(CALKeys.SEARCH.get(), 14, searchY + 4, 0xFF666677);
            } else {
                ctx.batcher.text(searchQuery, 14, searchY + 4, 0xFFFFFFFF);
                boolean hoverClear = ctx.mouseX >= animLeftPanelW - 20 && ctx.mouseX < animLeftPanelW - 8 && ctx.mouseY >= searchY + 2 && ctx.mouseY < searchY + 14;
                ctx.batcher.text("x", animLeftPanelW - 18, searchY + 3, hoverClear ? 0xFFEF5350 : 0xFF888899);
            }

            // Add Point / Spot Quick Buttons
            int addY = searchY + 22;
            int btnW = (animLeftPanelW - 22) / 2;
            boolean hoverP = ctx.mouseX >= 8 && ctx.mouseX < 8 + btnW && ctx.mouseY >= addY && ctx.mouseY < addY + 16;
            ctx.batcher.box(8, addY, 8 + btnW, addY + 16, hoverP ? 0xFF2E7D32 : 0xFF1B5E20);
            ctx.batcher.outline(8, addY, 8 + btnW, addY + 16, 0xFF81C784, 1);
            int pointTextX = 8 + (btnW - MinecraftClient.getInstance().textRenderer.getWidth(CALKeys.ADD_POINT_SHORT.get())) / 2;
            ctx.batcher.text(CALKeys.ADD_POINT_SHORT.get(), pointTextX, addY + 4, 0xFFFFFFFF);

            boolean hoverS = ctx.mouseX >= 8 + btnW + 6 && ctx.mouseX < animLeftPanelW - 8 && ctx.mouseY >= addY && ctx.mouseY < addY + 16;
            ctx.batcher.box(8 + btnW + 6, addY, animLeftPanelW - 8, addY + 16, hoverS ? 0xFF1565C0 : 0xFF0D47A1);
            ctx.batcher.outline(8 + btnW + 6, addY, animLeftPanelW - 8, addY + 16, 0xFF64B5F6, 1);
            int spotTextX = 8 + btnW + 6 + (btnW - MinecraftClient.getInstance().textRenderer.getWidth(CALKeys.ADD_SPOT_SHORT.get())) / 2;
            ctx.batcher.text(CALKeys.ADD_SPOT_SHORT.get(), spotTextX, addY + 4, 0xFFFFFFFF);

            // Populate Lights List
            cachedLights.clear();
            for (LightInstance l : LightManager.INSTANCE.getPointLights()) {
                boolean matches = searchQuery.isEmpty() || 
                                  String.valueOf(l.id).contains(searchQuery) || 
                                  (l.name != null && l.name.toLowerCase().contains(searchQuery.toLowerCase()));
                if (matches) {
                    cachedLights.add(l);
                }
            }
            for (LightInstance l : LightManager.INSTANCE.getSpotLights()) {
                boolean matches = searchQuery.isEmpty() || 
                                  String.valueOf(l.id).contains(searchQuery) || 
                                  (l.name != null && l.name.toLowerCase().contains(searchQuery.toLowerCase()));
                if (matches) {
                    cachedLights.add(l);
                }
            }

            // List Viewport Area
            int listStartY = addY + 22;
            int itemH = 20;
            int visibleHeight = botY - listStartY - 10;
            int totalListHeight = cachedLights.size() * (itemH + 2);
            int maxScroll = Math.min(0, visibleHeight - totalListHeight);
            if (leftPanelScrollY < maxScroll) leftPanelScrollY = maxScroll;

            ctx.batcher.clip(2, listStartY, animLeftPanelW - 4, visibleHeight);
            for (int i = 0; i < cachedLights.size(); i++) {
                LightInstance light = cachedLights.get(i);
                int itemY = listStartY + i * (itemH + 2) + leftPanelScrollY;

                boolean isCurrent = (light == selected);
                boolean hoverItem = ctx.mouseX >= 8 && ctx.mouseX < animLeftPanelW - 8 && ctx.mouseY >= itemY && ctx.mouseY < itemY + itemH && ctx.mouseY >= listStartY && ctx.mouseY < listStartY + visibleHeight;

                // Box bg
                int bg = isCurrent ? 0xFF2B2B36 : (hoverItem ? 0xFF1F1F26 : 0xFF18181F);
                ctx.batcher.box(8, itemY, animLeftPanelW - 8, itemY + itemH, bg);
                ctx.batcher.outline(8, itemY, animLeftPanelW - 8, itemY + itemH, isCurrent ? 0xFFFFAA00 : 0xFF2D2D38, 1);

                // Light Icon
                int lightCol = 0xFF000000 | ((int) (light.r * 255) << 16) | ((int) (light.g * 255) << 8) | (int) (light.b * 255);
                ctx.batcher.icon(light.isSpot ? CalLightsIcons.SPOT_LIGHT : CalLightsIcons.POINT_LIGHT, 12, itemY + 2, lightCol);

                // Label
                String label = (light.name == null || light.name.isEmpty()) ? String.valueOf(light.id) : light.name;
                ctx.batcher.text(label, 32, itemY + 6, 0xFFE0E0E0);

                // Visibility Icon (Eyeball)
                boolean hoverEye = ctx.mouseX >= animLeftPanelW - 42 && ctx.mouseX < animLeftPanelW - 28 && ctx.mouseY >= itemY + 3 && ctx.mouseY < itemY + 17 && ctx.mouseY >= listStartY && ctx.mouseY < listStartY + visibleHeight;
                int eyeCol = light.visible ? (hoverEye ? 0xFFFFAA00 : 0xFFE0E0E0) : 0xFF555555;
                ctx.batcher.icon(light.visible ? CalLightsIcons.VISIBLE : CalLightsIcons.INVISIBLE, animLeftPanelW - 42, itemY + 2, eyeCol);

                // Delete Icon (Trash)
                boolean hoverTrash = ctx.mouseX >= animLeftPanelW - 24 && ctx.mouseX < animLeftPanelW - 10 && ctx.mouseY >= itemY + 3 && ctx.mouseY < itemY + 17 && ctx.mouseY >= listStartY && ctx.mouseY < listStartY + visibleHeight;
                int trashCol = hoverTrash ? 0xFFEF5350 : 0xFF9E9E9E;
                ctx.batcher.icon(CalLightsIcons.TRASH, animLeftPanelW - 24, itemY + 2, trashCol);
            }
            ctx.batcher.unclip();

            // Draw scrollbar if needed
            if (totalListHeight > visibleHeight) {
                int scrollbarX = animLeftPanelW - 6;
                int scrollbarY = listStartY;
                int scrollbarH = visibleHeight;
                float ratio = (float) visibleHeight / totalListHeight;
                int thumbH = Math.max(15, (int) (scrollbarH * ratio));
                float scrollPercent = (float) -leftPanelScrollY / (totalListHeight - visibleHeight);
                int thumbY = scrollbarY + (int) (scrollPercent * (scrollbarH - thumbH));
                
                // Track
                ctx.batcher.box(scrollbarX, scrollbarY, scrollbarX + 3, scrollbarY + scrollbarH, 0x1AFFFFFF);
                // Thumb
                boolean hoverThumb = ctx.mouseX >= scrollbarX - 2 && ctx.mouseX < scrollbarX + 5 && ctx.mouseY >= thumbY && ctx.mouseY < thumbY + thumbH;
                int thumbColor = hoverThumb ? 0xAAFFFFFF : 0x66FFFFFF;
                ctx.batcher.box(scrollbarX, thumbY, scrollbarX + 3, thumbY + thumbH, thumbColor);
            }
        }

        // --- RENDER RIGHT PANEL (SETTINGS) ---
        if (animRightPanelW > 0) {
            if (activeSettingsPanel != null) {
                activeSettingsPanel.render(ctx);
            } else {
                // Draw placeholder message
                ctx.batcher.text(CALKeys.NO_LIGHT_SELECTED.get(), w - animRightPanelW + 25, topMenuH + tabsH + 40, 0xFF777788);
                ctx.batcher.text(CALKeys.CLICK_ANY_LIGHT.get(), w - animRightPanelW + 25, topMenuH + tabsH + 55, 0xFF555566);
                ctx.batcher.text(CALKeys.CLICK_IN_VIEWPORT.get(), w - animRightPanelW + 25, topMenuH + tabsH + 70, 0xFF555566);
            }
        }

        // Render bottom timeline panel before popups and overlays
        if (animTimelineH > 0 && timelinePanel != null) {
            timelinePanel.render(ctx);
        }

        // Hover diagnostics for timeline border
        boolean hoverTimelineBorder = showTimeline && selected != null
                && ctx.mouseX >= animLeftPanelW && ctx.mouseX <= w - animRightPanelW
                && ctx.mouseY >= contentH - 3 && ctx.mouseY <= contentH + 3;

        // Draw elegant resize border highlights and grab handle markers when hovered or dragged (Renders on top of panels!)
        if (showLeftSidebar && animLeftPanelW > 0) {
            if (hoverLeftBorder || draggingLeftBorder) {
                int col = draggingLeftBorder ? 0xFFFFAA00 : 0x88FFAA00;
                ctx.batcher.box(animLeftPanelW - 1, topMenuH, animLeftPanelW + 1, h - statusBarH, col);
            }
            boolean active = hoverLeftBorder || draggingLeftBorder;
            int handleBg = active ? 0xFF2B2B36 : 0xFF141418;
            int handleBorder = active ? 0xFFFFAA00 : 0xFF3E3E4D;
            int dotCol = active ? 0xFFFFAA00 : 0xFF777788;
            int cy = topMenuH + (h - statusBarH - topMenuH) / 2;
            ctx.batcher.box(animLeftPanelW - 3, cy - 15, animLeftPanelW + 3, cy + 15, handleBg);
            ctx.batcher.outline(animLeftPanelW - 3, cy - 15, animLeftPanelW + 3, cy + 15, handleBorder, 1);
            ctx.batcher.box(animLeftPanelW - 1, cy - 5, animLeftPanelW + 1, cy - 3, dotCol);
            ctx.batcher.box(animLeftPanelW - 1, cy - 1, animLeftPanelW + 1, cy + 1, dotCol);
            ctx.batcher.box(animLeftPanelW - 1, cy + 3, animLeftPanelW + 1, cy + 5, dotCol);
        }
        if (showRightSidebar && animRightPanelW > 0) {
            if (hoverRightBorder || draggingRightBorder) {
                int col = draggingRightBorder ? 0xFFFFAA00 : 0x88FFAA00;
                ctx.batcher.box(w - animRightPanelW - 1, topMenuH, w - animRightPanelW + 1, h - statusBarH, col);
            }
            boolean active = hoverRightBorder || draggingRightBorder;
            int handleBg = active ? 0xFF2B2B36 : 0xFF141418;
            int handleBorder = active ? 0xFFFFAA00 : 0xFF3E3E4D;
            int dotCol = active ? 0xFFFFAA00 : 0xFF777788;
            int cy = topMenuH + (h - statusBarH - topMenuH) / 2;
            int rx = w - animRightPanelW;
            ctx.batcher.box(rx - 3, cy - 15, rx + 3, cy + 15, handleBg);
            ctx.batcher.outline(rx - 3, cy - 15, rx + 3, cy + 15, handleBorder, 1);
            ctx.batcher.box(rx - 1, cy - 5, rx + 1, cy - 3, dotCol);
            ctx.batcher.box(rx - 1, cy - 1, rx + 1, cy + 1, dotCol);
            ctx.batcher.box(rx - 1, cy + 3, rx + 1, cy + 5, dotCol);
        }
        if (showTimeline && selected != null && animTimelineH > 0) {
            if (hoverTimelineBorder || draggingTimelineBorder) {
                int col = draggingTimelineBorder ? 0xFFFFAA00 : 0x88FFAA00;
                ctx.batcher.box(animLeftPanelW, contentH - 1, w - animRightPanelW, contentH + 1, col);
            }
            boolean active = hoverTimelineBorder || draggingTimelineBorder;
            int handleBg = active ? 0xFF2B2B36 : 0xFF141418;
            int handleBorder = active ? 0xFFFFAA00 : 0xFF3E3E4D;
            int dotCol = active ? 0xFFFFAA00 : 0xFF777788;
            int cx = animLeftPanelW + (w - animLeftPanelW - animRightPanelW) / 2;
            ctx.batcher.box(cx - 15, contentH - 3, cx + 15, contentH + 3, handleBg);
            ctx.batcher.outline(cx - 15, contentH - 3, cx + 15, contentH + 3, handleBorder, 1);
            ctx.batcher.box(cx - 5, contentH - 1, cx - 3, contentH + 1, dotCol);
            ctx.batcher.box(cx - 1, contentH - 1, cx + 1, contentH + 1, dotCol);
            ctx.batcher.box(cx + 3, contentH - 1, cx + 5, contentH + 1, dotCol);
        }

        // --- RENDER DROPDOWN OVERLAYS (ALWAYS ON TOP) ---
        if (activeMenuDropdown == 1) {
            int dropX = 110;
            int dropY = topMenuH;
            int dropW = 120;
            int itemH = 18;
            int dropH = itemH * 4;

            ctx.batcher.box(dropX, dropY, dropX + dropW, dropY + dropH, 0xFF141418);
            ctx.batcher.outline(dropX, dropY, dropX + dropW, dropY + dropH, 0xFFFFAA00, 1);

            // Item 1: Ajustes
            boolean hover1 = ctx.mouseX >= dropX && ctx.mouseX < dropX + dropW && ctx.mouseY >= dropY && ctx.mouseY < dropY + itemH;
            ctx.batcher.box(dropX + 1, dropY + 1, dropX + dropW - 1, dropY + itemH - 1, hover1 ? 0xFF2A2A35 : 0xFF141418);
            ctx.batcher.text(CALKeys.SETTINGS.get(), dropX + 8, dropY + 5, hover1 ? 0xFFFFAA00 : 0xFFE0E0E0);

            // Item 2: Guardar
            boolean hover2 = ctx.mouseX >= dropX && ctx.mouseX < dropX + dropW && ctx.mouseY >= dropY + itemH && ctx.mouseY < dropY + itemH * 2;
            ctx.batcher.box(dropX + 1, dropY + itemH + 1, dropX + dropW - 1, dropY + itemH * 2 - 1, hover2 ? 0xFF2A2A35 : 0xFF141418);
            ctx.batcher.text(CALKeys.SAVE.get(), dropX + 8, dropY + itemH + 5, hover2 ? 0xFFFFAA00 : 0xFFE0E0E0);

            // Item 3: Parchar Shaders
            boolean hover3 = ctx.mouseX >= dropX && ctx.mouseX < dropX + dropW && ctx.mouseY >= dropY + itemH * 2 && ctx.mouseY < dropY + itemH * 3;
            ctx.batcher.box(dropX + 1, dropY + itemH * 2 + 1, dropX + dropW - 1, dropY + itemH * 2 - 1, hover3 ? 0xFF2A2A35 : 0xFF141418);
            ctx.batcher.text(CALKeys.PATCH_MENU.get(), dropX + 8, dropY + itemH * 2 + 5, hover3 ? 0xFFFFAA00 : 0xFFE0E0E0);

            // Item 4: Salir
            boolean hover4 = ctx.mouseX >= dropX && ctx.mouseX < dropX + dropW && ctx.mouseY >= dropY + itemH * 3 && ctx.mouseY < dropY + dropH;
            ctx.batcher.box(dropX + 1, dropY + itemH * 3 + 1, dropX + dropW - 1, dropY + dropH - 1, hover4 ? 0xFF2A2A35 : 0xFF141418);
            ctx.batcher.text(CALKeys.EXIT.get(), dropX + 8, dropY + itemH * 3 + 5, hover4 ? 0xFFEF5350 : 0xFFE0E0E0);
        } else if (activeMenuDropdown == 2) {
            int dropX = 165;
            int dropY = topMenuH;
            int dropW = 110;
            int itemH = 18;
            int dropH = itemH * 3;

            ctx.batcher.box(dropX, dropY, dropX + dropW, dropY + dropH, 0xFF141418);
            ctx.batcher.outline(dropX, dropY, dropX + dropW, dropY + dropH, 0xFFFFAA00, 1);

            // Item 1: Esquema
            boolean hover1 = ctx.mouseX >= dropX && ctx.mouseX < dropX + dropW && ctx.mouseY >= dropY && ctx.mouseY < dropY + itemH;
            ctx.batcher.box(dropX + 1, dropY + 1, dropX + dropW - 1, dropY + itemH - 1, hover1 ? 0xFF2A2A35 : 0xFF141418);
            String esqLabel = showLeftSidebar ? "[v] " + CALKeys.SCHEME.get() : "[ ] " + CALKeys.SCHEME.get();
            ctx.batcher.text(esqLabel, dropX + 8, dropY + 5, hover1 ? 0xFFFFAA00 : 0xFFE0E0E0);

            // Item 2: Inspector
            boolean hover2 = ctx.mouseX >= dropX && ctx.mouseX < dropX + dropW && ctx.mouseY >= dropY + itemH && ctx.mouseY < dropY + itemH * 2;
            ctx.batcher.box(dropX + 1, dropY + itemH + 1, dropX + dropW - 1, dropY + itemH * 2 - 1, hover2 ? 0xFF2A2A35 : 0xFF141418);
            String insLabel = showRightSidebar ? "[v] " + CALKeys.INSPECTOR.get() : "[ ] " + CALKeys.INSPECTOR.get();
            ctx.batcher.text(insLabel, dropX + 8, dropY + itemH + 5, hover2 ? 0xFFFFAA00 : 0xFFE0E0E0);

            // Item 3: Timeline
            boolean hover3 = ctx.mouseX >= dropX && ctx.mouseX < dropX + dropW && ctx.mouseY >= dropY + itemH * 2 && ctx.mouseY < dropY + dropH;
            ctx.batcher.box(dropX + 1, dropY + itemH * 2 + 1, dropX + dropW - 1, dropY + dropH - 1, hover3 ? 0xFF2A2A35 : 0xFF141418);
            String timelineLabel = showTimeline ? "[v] Timeline" : "[ ] Timeline";
            ctx.batcher.text(timelineLabel, dropX + 8, dropY + itemH * 2 + 5, hover3 ? 0xFFFFAA00 : 0xFFE0E0E0);
        }

        // --- RENDER AJUSTES POPUP (BLOCKBENCH STYLE MODAL) ---
        if (settingsPopupScale > 0.01f) {
            // Dark Backdrop Focus overlay with fading opacity
            int alpha = (int) (0x90 * settingsPopupScale);
            ctx.batcher.box(0, 0, w, h, (alpha << 24) | 0x0B0B0E);

            // Apply scaling matrix for the popup contents
            ctx.batcher.getCtx().getMatrices().push();
            ctx.batcher.getCtx().getMatrices().translate(w / 2.0f, h / 2.0f, 0.0f);
            ctx.batcher.getCtx().getMatrices().scale(settingsPopupScale, settingsPopupScale, 1.0f);
            ctx.batcher.getCtx().getMatrices().translate(-w / 2.0f, -h / 2.0f, 0.0f);

            // Modal Box Dimensions (Premium BBS size: 520x320)
            int pW = 520;
            int pH = 320;
            int pX = (w - pW) / 2;
            int pY = (h - pH) / 2;

            // Background & Outline
            ctx.batcher.box(pX, pY, pX + pW, pY + pH, 0xFF141418);
            ctx.batcher.outline(pX, pY, pX + pW, pY + pH, 0xFFFFAA00, 1);

            // Header Row
            int headerH = 22;
            ctx.batcher.box(pX, pY, pX + pW, pY + headerH, 0xFF1A1A22);
            ctx.batcher.outline(pX, pY, pX + pW, pY + headerH, 0xFF2A2A35, 1);
            ctx.batcher.text(CALKeys.SETTINGS.get(), pX + 10, pY + 6, 0xFFFFAA00);

            // Header close [X]
            boolean hoverX = ctx.mouseX >= pX + pW - 22 && ctx.mouseX < pX + pW - 6 && ctx.mouseY >= pY + 4 && ctx.mouseY < pY + 18;
            ctx.batcher.text("X", pX + pW - 18, pY + 6, hoverX ? 0xFFEF5350 : 0xFF9E9E9E);

            // Left category list panel (90 width)
            int sideW = 90;
            ctx.batcher.box(pX, pY + headerH, pX + sideW, pY + pH, 0xFF111115);
            ctx.batcher.outline(pX, pY + headerH, pX + sideW, pY + pH, 0xFF22222A, 1);

            // Category 1: General
            boolean hoverCat0 = ctx.mouseX >= pX + 2 && ctx.mouseX < pX + sideW - 2 && ctx.mouseY >= pY + headerH + 6 && ctx.mouseY < pY + headerH + 24;
            int cat0Bg = (selectedSettingsCategory == 0) ? 0xFF1D1D26 : (hoverCat0 ? 0xFF181820 : 0xFF111115);
            ctx.batcher.box(pX + 2, pY + headerH + 6, pX + sideW - 2, pY + headerH + 24, cat0Bg);
            ctx.batcher.text(CALKeys.GENERAL.get(), pX + 10, pY + headerH + 11, (selectedSettingsCategory == 0) ? 0xFFFFAA00 : 0xFFCCCCCC);
            if (selectedSettingsCategory == 0) {
                ctx.batcher.box(pX + 2, pY + headerH + 6, pX + 5, pY + headerH + 24, 0xFF1976D2);
            }

            // Category 2: Interfaz
            boolean hoverCat1 = ctx.mouseX >= pX + 2 && ctx.mouseX < pX + sideW - 2 && ctx.mouseY >= pY + headerH + 28 && ctx.mouseY < pY + headerH + 46;
            int cat1Bg = (selectedSettingsCategory == 1) ? 0xFF1D1D26 : (hoverCat1 ? 0xFF181820 : 0xFF111115);
            ctx.batcher.box(pX + 2, pY + headerH + 28, pX + sideW - 2, pY + headerH + 46, cat1Bg);
            ctx.batcher.text(CALKeys.INTERFACE.get(), pX + 10, pY + headerH + 33, (selectedSettingsCategory == 1) ? 0xFFFFAA00 : 0xFFCCCCCC);
            if (selectedSettingsCategory == 1) {
                ctx.batcher.box(pX + 2, pY + headerH + 28, pX + 5, pY + headerH + 46, 0xFF1976D2);
            }

            // Category 3: Teclas
            boolean hoverCat2 = ctx.mouseX >= pX + 2 && ctx.mouseX < pX + sideW - 2 && ctx.mouseY >= pY + headerH + 50 && ctx.mouseY < pY + headerH + 68;
            int cat2Bg = (selectedSettingsCategory == 2) ? 0xFF1D1D26 : (hoverCat2 ? 0xFF181820 : 0xFF111115);
            ctx.batcher.box(pX + 2, pY + headerH + 50, pX + sideW - 2, pY + headerH + 68, cat2Bg);
            ctx.batcher.text(CALKeys.KEYBINDS.get(), pX + 10, pY + headerH + 55, (selectedSettingsCategory == 2) ? 0xFFFFAA00 : 0xFFCCCCCC);
            if (selectedSettingsCategory == 2) {
                ctx.batcher.box(pX + 2, pY + headerH + 50, pX + 5, pY + headerH + 68, 0xFF1976D2);
            }

            // Category 4: Motor
            boolean hoverCat3 = ctx.mouseX >= pX + 2 && ctx.mouseX < pX + sideW - 2 && ctx.mouseY >= pY + headerH + 72 && ctx.mouseY < pY + headerH + 90;
            int cat3Bg = (selectedSettingsCategory == 3) ? 0xFF1D1D26 : (hoverCat3 ? 0xFF181820 : 0xFF111115);
            ctx.batcher.box(pX + 2, pY + headerH + 72, pX + sideW - 2, pY + headerH + 90, cat3Bg);
            ctx.batcher.text(CALKeys.MOTOR.get(), pX + 10, pY + headerH + 77, (selectedSettingsCategory == 3) ? 0xFFFFAA00 : 0xFFCCCCCC);
            if (selectedSettingsCategory == 3) {
                ctx.batcher.box(pX + 2, pY + headerH + 72, pX + 5, pY + headerH + 90, 0xFF1976D2);
            }

            // Content Area Rendering
            int contentX = pX + sideW + 10;
            if (selectedSettingsCategory == 0) {
                // GENERAL SETTINGS CONTENTS
                ctx.batcher.text(CALKeys.LANGUAGE.get(), contentX, pY + headerH + 10, 0xFFCCCCCC);
                ctx.batcher.text(CALKeys.LANGUAGE_SUB.get(), contentX, pY + headerH + 22, 0xFF777788);

                boolean hoverLang = ctx.mouseX >= contentX && ctx.mouseX < contentX + 150 && ctx.mouseY >= pY + headerH + 34 && ctx.mouseY < pY + headerH + 52;
                ctx.batcher.box(contentX, pY + headerH + 34, contentX + 150, pY + headerH + 52, hoverLang ? 0xFF2A2A35 : 0xFF1E1E24);
                ctx.batcher.outline(contentX, pY + headerH + 34, contentX + 150, pY + headerH + 52, 0xFF3E3E4D, 1);
                String langLabel = "Español";
                switch (CalSettings.INSTANCE.language) {
                    case "en_us": langLabel = "English"; break;
                    case "fr_fr": langLabel = "Français"; break;
                    case "ru_ru": langLabel = "Русский"; break;
                    case "pt_br": langLabel = "Português (BR)"; break;
                    case "pt_pt": langLabel = "Português (PT)"; break;
                }
                ctx.batcher.text(langLabel, contentX + 8, pY + headerH + 39, 0xFFFFFFFF);
            } else if (selectedSettingsCategory == 1) {
                // INTERFAZ SETTINGS CONTENTS
                ctx.batcher.text(CALKeys.GUI_SCALE.get(), contentX, pY + headerH + 10, 0xFFCCCCCC);
                ctx.batcher.text(CALKeys.GUI_SCALE_SUB.get(), contentX, pY + headerH + 22, 0xFF777788);

                for (int sc = 1; sc <= 4; sc++) {
                    int scX = contentX + (sc - 1) * 32;
                    int scY = pY + headerH + 34;
                    boolean hoverS = ctx.mouseX >= scX && ctx.mouseX < scX + 26 && ctx.mouseY >= scY && ctx.mouseY < scY + 18;
                    boolean isCurrent = (CalSettings.INSTANCE.guiScale == sc);
                    int sBg = isCurrent ? 0xFF1976D2 : (hoverS ? 0xFF2A2A35 : 0xFF1E1E24);
                    ctx.batcher.box(scX, scY, scX + 26, scY + 18, sBg);
                    ctx.batcher.outline(scX, scY, scX + 26, scY + 18, isCurrent ? 0xFF64B5F6 : 0xFF3E3E4D, 1);
                    ctx.batcher.text(String.valueOf(sc), scX + 10, scY + 5, 0xFFFFFFFF);
                }

                // GIZMO SIZE SLIDER SETTINGS (Volume Slider aesthetics)
                int sliderSectionY = pY + headerH + 58;
                ctx.batcher.text(CALKeys.GIZMO_SIZE.get(), contentX, sliderSectionY, 0xFFCCCCCC);
                ctx.batcher.text(CALKeys.GIZMO_SIZE_SUB.get(CalSettings.INSTANCE.gizmoSize), contentX, sliderSectionY + 12, 0xFF777788);

                int sliderY = sliderSectionY + 28;
                int trackW = 160;
                float sliderT = CalSettings.INSTANCE.gizmoSize / 10.0f;
                sliderT = Math.max(0.0f, Math.min(1.0f, sliderT));
                int handleX = contentX + (int)(sliderT * trackW);

                ctx.batcher.box(contentX, sliderY, handleX, sliderY + 3, 0xFF1976D2); // Filled Left
                ctx.batcher.box(handleX, sliderY, contentX + trackW, sliderY + 3, 0xFF33333D); // Empty Right
                ctx.batcher.outline(contentX, sliderY, contentX + trackW, sliderY + 3, 0xFF212126, 1);

                ctx.batcher.box(handleX - 3, sliderY - 3, handleX + 3, sliderY + 6, 0xFF64B5F6);
                ctx.batcher.outline(handleX - 3, sliderY - 3, handleX + 3, sliderY + 6, 0xFF1976D2, 1);

                // UNIDAD DE TIEMPO
                int timeUnitY = pY + headerH + 110;
                ctx.batcher.text(CALKeys.TIME_UNIT.get(), contentX, timeUnitY, 0xFFCCCCCC);
                ctx.batcher.text(CALKeys.TIME_FORMAT_DESC.get(), contentX, timeUnitY + 12, 0xFF777788);

                int ticksBtnX = contentX;
                int secsBtnX = contentX + 80;
                int btnW2 = 72;
                int btnH2 = 18;
                int btnY2 = timeUnitY + 28;

                boolean hoverTicks = ctx.mouseX >= ticksBtnX && ctx.mouseX < ticksBtnX + btnW2 && ctx.mouseY >= btnY2 && ctx.mouseY < btnY2 + btnH2;
                boolean isTicks = CalSettings.INSTANCE.durationMode.equals("ticks");
                ctx.batcher.box(ticksBtnX, btnY2, ticksBtnX + btnW2, btnY2 + btnH2, isTicks ? 0xFF1976D2 : (hoverTicks ? 0xFF2A2A35 : 0xFF1E1E24));
                ctx.batcher.outline(ticksBtnX, btnY2, ticksBtnX + btnW2, btnY2 + btnH2, isTicks ? 0xFF64B5F6 : 0xFF3E3E4D, 1);
                int ticksTextW = MinecraftClient.getInstance().textRenderer.getWidth(CALKeys.TICKS.get());
                ctx.batcher.text(CALKeys.TICKS.get(), ticksBtnX + (btnW2 - ticksTextW) / 2, btnY2 + 5, 0xFFFFFFFF);

                boolean hoverSecs = ctx.mouseX >= secsBtnX && ctx.mouseX < secsBtnX + btnW2 && ctx.mouseY >= btnY2 && ctx.mouseY < btnY2 + btnH2;
                boolean isSecs = CalSettings.INSTANCE.durationMode.equals("seconds");
                ctx.batcher.box(secsBtnX, btnY2, secsBtnX + btnW2, btnY2 + btnH2, isSecs ? 0xFF1976D2 : (hoverSecs ? 0xFF2A2A35 : 0xFF1E1E24));
                ctx.batcher.outline(secsBtnX, btnY2, secsBtnX + btnW2, btnY2 + btnH2, isSecs ? 0xFF64B5F6 : 0xFF3E3E4D, 1);
                int secsTextW = MinecraftClient.getInstance().textRenderer.getWidth(CALKeys.SECONDS.get());
                ctx.batcher.text(CALKeys.SECONDS.get(), secsBtnX + (btnW2 - secsTextW) / 2, btnY2 + 5, 0xFFFFFFFF);

                // SIMPLIFICAR ANIMACIONES
                int animSectionY = timeUnitY + 52;
                ctx.batcher.text(CALKeys.SIMPLIFY_ANIMS.get(), contentX, animSectionY, 0xFFCCCCCC);
                ctx.batcher.text(CALKeys.SIMPLIFY_ANIMS_SUB.get(), contentX, animSectionY + 12, 0xFF777788);

                int yesBtnX = contentX;
                int noBtnX = contentX + 80;
                int animBtnY = animSectionY + 28;

                boolean hoverYes = ctx.mouseX >= yesBtnX && ctx.mouseX < yesBtnX + btnW2 && ctx.mouseY >= animBtnY && ctx.mouseY < animBtnY + btnH2;
                boolean isYes = CalSettings.INSTANCE.simplifyAnimations;
                ctx.batcher.box(yesBtnX, animBtnY, yesBtnX + btnW2, animBtnY + btnH2, isYes ? 0xFF1976D2 : (hoverYes ? 0xFF2A2A35 : 0xFF1E1E24));
                ctx.batcher.outline(yesBtnX, animBtnY, yesBtnX + btnW2, animBtnY + btnH2, isYes ? 0xFF64B5F6 : 0xFF3E3E4D, 1);
                int yesTextW = MinecraftClient.getInstance().textRenderer.getWidth(CALKeys.YES.get());
                ctx.batcher.text(CALKeys.YES.get(), yesBtnX + (btnW2 - yesTextW) / 2, animBtnY + 5, 0xFFFFFFFF);

                boolean hoverNo = ctx.mouseX >= noBtnX && ctx.mouseX < noBtnX + btnW2 && ctx.mouseY >= animBtnY && ctx.mouseY < animBtnY + btnH2;
                boolean isNo = !CalSettings.INSTANCE.simplifyAnimations;
                ctx.batcher.box(noBtnX, animBtnY, noBtnX + btnW2, animBtnY + btnH2, isNo ? 0xFF1976D2 : (hoverNo ? 0xFF2A2A35 : 0xFF1E1E24));
                ctx.batcher.outline(noBtnX, animBtnY, noBtnX + btnW2, animBtnY + btnH2, isNo ? 0xFF64B5F6 : 0xFF3E3E4D, 1);
                int noTextW = MinecraftClient.getInstance().textRenderer.getWidth(CALKeys.NO.get());
                ctx.batcher.text(CALKeys.NO.get(), noBtnX + (btnW2 - noTextW) / 2, animBtnY + 5, 0xFFFFFFFF);

            } else if (selectedSettingsCategory == 2) {
                // TECLAS (CONTROLS) SETTINGS CONTENTS
                int keyListY = pY + headerH + 10;
                int rowHeight = 16;
                int colWidth = 195;
                
                for (int i = 0; i < KEY_NAMES.length; i++) {
                    int col = i / 6;
                    int row = i % 6;
                    
                    int rowX = contentX + col * colWidth;
                    int rowY = keyListY + row * (rowHeight + 6);
                    
                    ctx.batcher.text(KEY_NAMES[i].get(), rowX, rowY + 3, 0xFFCCCCCC);
                    
                    // Button to click and rebind / show shortcut
                    int btnX = rowX + 110;
                    int btnW3 = 75;
                    int btnH3 = 14;
                    
                    boolean isRebindable = (i < 3);
                    boolean isRebinding = isRebindable && (activeRebindingKeyIndex == i);
                    boolean hoverBtn = isRebindable && ctx.mouseX >= btnX && ctx.mouseX < btnX + btnW3 && ctx.mouseY >= rowY && ctx.mouseY < rowY + btnH3;
                    
                    int btnBg, btnOutline;
                    if (isRebindable) {
                        btnBg = isRebinding ? 0xFFB71C1C : (hoverBtn ? 0xFF2A2A35 : 0xFF1E1E24);
                        btnOutline = isRebinding ? 0xFFEF5350 : 0xFF3E3E4D;
                    } else {
                        btnBg = 0xFF141418;
                        btnOutline = 0xFF282830;
                    }
                    
                    ctx.batcher.box(btnX, rowY, btnX + btnW3, rowY + btnH3, btnBg);
                    ctx.batcher.outline(btnX, rowY, btnX + btnW3, rowY + btnH3, btnOutline, 1);
                    
                    String keyLabel;
                    if (isRebinding) {
                        keyLabel = "???";
                    } else if (isRebindable) {
                        keyLabel = getKeyName(getSettingKey(i));
                    } else {
                        keyLabel = switch (i) {
                            case 3 -> "Ctrl + Z";
                            case 4 -> "Ctrl + Y";
                            case 5 -> "Ctrl + A";
                            case 6 -> "Ctrl + C";
                            case 7 -> "Ctrl + V";
                            case 8 -> "Ctrl + X";
                            case 9 -> "F1";
                            case 10 -> CALLightsClient.createLightKeyBinding != null ? CALLightsClient.createLightKeyBinding.getBoundKeyLocalizedText().getString() : "F7";
                            case 11 -> CALLightsClient.editorKeyBinding != null ? CALLightsClient.editorKeyBinding.getBoundKeyLocalizedText().getString() : "F8";
                            default -> "";
                        };
                    }
                    
                    int keyTextW = MinecraftClient.getInstance().textRenderer.getWidth(keyLabel);
                    ctx.batcher.text(keyLabel, btnX + (btnW3 - keyTextW) / 2, rowY + 3, isRebindable ? 0xFFFFFFFF : 0xFF888899);
                }
            } else if (selectedSettingsCategory == 3) {
                // MOTOR SETTINGS CONTENTS (Two-column layout)
                int colW = 200;
                int leftColX = contentX;
                int rightColX = contentX + 215;
                int startY = pY + headerH + 10;
                int itemSpacing = 20;

                // --- COLUMNA IZQUIERDA: SOMBRAS & GENERAL ---
                int curY = startY;
                ctx.batcher.text(CALKeys.SHADOW_QUALITY.get(), leftColX, curY, 0xFFCCCCCC);
                curY += 12;

                // Segmented button for shadow quality: LOW, MED, HIGH, ULTRA
                String[] qLabels = {"LOW", "MED", "HIGH", "ULTRA"};
                int segW = (colW - 9) / 4;
                for (int i = 0; i < qLabels.length; i++) {
                    int btnX = leftColX + i * (segW + 3);
                    boolean hoverQ = ctx.mouseX >= btnX && ctx.mouseX < btnX + segW && ctx.mouseY >= curY && ctx.mouseY < curY + 14;
                    boolean isCurrentQ = (LightConfig.shadowQuality == i);
                    int qBg = isCurrentQ ? 0xFF1976D2 : (hoverQ ? 0xFF2A2A35 : 0xFF1E1E24);
                    ctx.batcher.box(btnX, curY, btnX + segW, curY + 14, qBg);
                    ctx.batcher.outline(btnX, curY, btnX + segW, curY + 14, isCurrentQ ? 0xFF64B5F6 : 0xFF3E3E4D, 1);
                    int textW = MinecraftClient.getInstance().textRenderer.getWidth(qLabels[i]);
                    ctx.batcher.text(qLabels[i], btnX + (segW - textW) / 2, curY + 3, 0xFFFFFFFF);
                }
                curY += 20;

                // Shadow Cache
                {
                    int boxSize = 10;
                    boolean hover = ctx.mouseX >= leftColX && ctx.mouseX < leftColX + colW && ctx.mouseY >= curY && ctx.mouseY < curY + 12;
                    ctx.batcher.box(leftColX, curY + 1, leftColX + boxSize, curY + 1 + boxSize, hover ? 0xFF2A2A35 : 0xFF141418);
                    ctx.batcher.outline(leftColX, curY + 1, leftColX + boxSize, curY + 1 + boxSize, hover ? 0xFFFFAA00 : 0xFF3E3E4D, 1);
                    if (LightConfig.shadowCache) {
                        ctx.batcher.box(leftColX + 2, curY + 3, leftColX + boxSize - 2, curY + boxSize - 1, 0xFFFFAA00);
                    }
                    ctx.batcher.text(CALKeys.SHADOW_CACHE.get(), leftColX + boxSize + 6, curY + 2, 0xFFE0E0E0);
                }
                curY += itemSpacing;

                // Shadow Blocks
                {
                    int boxSize = 10;
                    boolean hover = ctx.mouseX >= leftColX && ctx.mouseX < leftColX + colW && ctx.mouseY >= curY && ctx.mouseY < curY + 12;
                    ctx.batcher.box(leftColX, curY + 1, leftColX + boxSize, curY + 1 + boxSize, hover ? 0xFF2A2A35 : 0xFF141418);
                    ctx.batcher.outline(leftColX, curY + 1, leftColX + boxSize, curY + 1 + boxSize, hover ? 0xFFFFAA00 : 0xFF3E3E4D, 1);
                    if (LightConfig.shadowBlocks) {
                        ctx.batcher.box(leftColX + 2, curY + 3, leftColX + boxSize - 2, curY + boxSize - 1, 0xFFFFAA00);
                    }
                    ctx.batcher.text(CALKeys.SHADOW_BLOCKS.get(), leftColX + boxSize + 6, curY + 2, 0xFFE0E0E0);
                }
                curY += itemSpacing;

                // Shadow Block Radius Trackpad
                trackShadowBlockRadius.resize(leftColX, curY, colW, 14);
                trackShadowBlockRadius.setValue(LightConfig.shadowBlockRadius);
                trackShadowBlockRadius.render(ctx);
                curY += itemSpacing;


                // --- COLUMNA DERECHA: AUTO-LUCES ---
                curY = startY;

                // Auto Lights Enable Checkbox
                {
                    int boxSize = 10;
                    boolean hover = ctx.mouseX >= rightColX && ctx.mouseX < rightColX + colW && ctx.mouseY >= curY && ctx.mouseY < curY + 12;
                    ctx.batcher.box(rightColX, curY + 1, rightColX + boxSize, curY + 1 + boxSize, hover ? 0xFF2A2A35 : 0xFF141418);
                    ctx.batcher.outline(rightColX, curY + 1, rightColX + boxSize, curY + 1 + boxSize, hover ? 0xFFFFAA00 : 0xFF3E3E4D, 1);
                    if (LightConfig.autoLights) {
                        ctx.batcher.box(rightColX + 2, curY + 3, rightColX + boxSize - 2, curY + boxSize - 1, 0xFFFFAA00);
                    }
                    ctx.batcher.text(CALKeys.AUTO_LIGHTS.get(), rightColX + boxSize + 6, curY + 2, 0xFFE0E0E0);
                }
                curY += itemSpacing;

                // Auto Lights Shadows Checkbox (Only enabled / clickable if autoLights is true)
                boolean autoLightsOn = LightConfig.autoLights;
                {
                    int boxSize = 10;
                    if (!autoLightsOn) {
                        ctx.batcher.box(rightColX, curY + 1, rightColX + boxSize, curY + 1 + boxSize, 0xFF111115);
                        ctx.batcher.outline(rightColX, curY + 1, rightColX + boxSize, curY + 1 + boxSize, 0xFF282830, 1);
                        if (LightConfig.autoLightShadows) {
                            ctx.batcher.box(rightColX + 2, curY + 3, rightColX + boxSize - 2, curY + boxSize - 1, 0xFF555555);
                        }
                        ctx.batcher.text(CALKeys.AUTO_LIGHT_SHADOWS.get(), rightColX + boxSize + 6, curY + 2, 0xFF666666);
                    } else {
                        boolean hover = ctx.mouseX >= rightColX && ctx.mouseX < rightColX + colW && ctx.mouseY >= curY && ctx.mouseY < curY + 12;
                        ctx.batcher.box(rightColX, curY + 1, rightColX + boxSize, curY + 1 + boxSize, hover ? 0xFF2A2A35 : 0xFF141418);
                        ctx.batcher.outline(rightColX, curY + 1, rightColX + boxSize, curY + 1 + boxSize, hover ? 0xFFFFAA00 : 0xFF3E3E4D, 1);
                        if (LightConfig.autoLightShadows) {
                            ctx.batcher.box(rightColX + 2, curY + 3, rightColX + boxSize - 2, curY + boxSize - 1, 0xFFFFAA00);
                        }
                        ctx.batcher.text(CALKeys.AUTO_LIGHT_SHADOWS.get(), rightColX + boxSize + 6, curY + 2, 0xFFE0E0E0);
                    }
                }
                curY += itemSpacing;

                // Trackpads for Auto Lights (Disabled if autoLights is false)
                if (!autoLightsOn) {
                    ctx.batcher.box(rightColX, curY, rightColX + colW, curY + 14, 0xFF111115);
                    ctx.batcher.outline(rightColX, curY, rightColX + colW, curY + 14, 0xFF282830, 1);
                    ctx.batcher.text(CALKeys.AUTO_LIGHT_INTENSITY.get() + ": " + String.format("%.2f", LightConfig.autoLightIntensity), rightColX + 6, curY + 3, 0xFF666666);
                    curY += itemSpacing;

                    ctx.batcher.box(rightColX, curY, rightColX + colW, curY + 14, 0xFF111115);
                    ctx.batcher.outline(rightColX, curY, rightColX + colW, curY + 14, 0xFF282830, 1);
                    ctx.batcher.text(CALKeys.AUTO_LIGHT_REACH.get() + ": " + String.format("%.2f", LightConfig.autoLightReach), rightColX + 6, curY + 3, 0xFF666666);
                    curY += itemSpacing;

                    ctx.batcher.box(rightColX, curY, rightColX + colW, curY + 14, 0xFF111115);
                    ctx.batcher.outline(rightColX, curY, rightColX + colW, curY + 14, 0xFF282830, 1);
                    ctx.batcher.text(CALKeys.AUTO_LIGHT_RADIUS.get() + ": " + LightConfig.autoLightRadius, rightColX + 6, curY + 3, 0xFF666666);
                    curY += itemSpacing;

                    ctx.batcher.box(rightColX, curY, rightColX + colW, curY + 14, 0xFF111115);
                    ctx.batcher.outline(rightColX, curY, rightColX + colW, curY + 14, 0xFF282830, 1);
                    ctx.batcher.text(CALKeys.AUTO_LIGHT_MAX.get() + ": " + LightConfig.autoLightMax, rightColX + 6, curY + 3, 0xFF666666);
                    curY += itemSpacing;
                } else {
                    trackAutoLightIntensity.resize(rightColX, curY, colW, 14);
                    trackAutoLightIntensity.setValue(LightConfig.autoLightIntensity);
                    trackAutoLightIntensity.render(ctx);
                    curY += itemSpacing;

                    trackAutoLightReach.resize(rightColX, curY, colW, 14);
                    trackAutoLightReach.setValue(LightConfig.autoLightReach);
                    trackAutoLightReach.render(ctx);
                    curY += itemSpacing;

                    trackAutoLightRadius.resize(rightColX, curY, colW, 14);
                    trackAutoLightRadius.setValue(LightConfig.autoLightRadius);
                    trackAutoLightRadius.render(ctx);
                    curY += itemSpacing;

                    trackAutoLightMax.resize(rightColX, curY, colW, 14);
                    trackAutoLightMax.setValue(LightConfig.autoLightMax);
                    trackAutoLightMax.render(ctx);
                    curY += itemSpacing;
                }

                // Active lights diagnostics string
                int activeCount = AutoLightManager.count();
                ctx.batcher.text(CALKeys.AUTO_LIGHT_ACTIVE.get(activeCount), rightColX, curY, 0xFF888899);
            }

            // Footer row - Cerrar button
            int btnW = 56;
            int btnH = 16;
            int btnX = pX + pW - btnW - 10;
            int btnY = pY + pH - btnH - 10;
            boolean hoverClose = ctx.mouseX >= btnX && ctx.mouseX < btnX + btnW && ctx.mouseY >= btnY && ctx.mouseY < btnY + btnH;
            ctx.batcher.box(btnX, btnY, btnX + btnW, btnY + btnH, hoverClose ? 0xFF3A3A4A : 0xFF212126);
            ctx.batcher.outline(btnX, btnY, btnX + btnW, btnY + btnH, hoverClose ? 0xFFFFAA00 : 0xFF3E3E4D, 1);
            ctx.batcher.text(CALKeys.CLOSE.get(), btnX + 12, btnY + 4, 0xFFE0E0E0);

            if (showLanguageDropdown) {
                int dropX = contentX;
                int dropY = pY + headerH + 52;
                int dropW = 150;
                int itemH = 18;
                int dropH = itemH * 6;

                ctx.batcher.box(dropX, dropY, dropX + dropW, dropY + dropH, 0xFF141418);
                ctx.batcher.outline(dropX, dropY, dropX + dropW, dropY + dropH, 0xFFFFAA00, 1);

                String[] langs = {"English", "Español", "Français", "Русский", "Português (BR)", "Português (PT)"};
                for (int i = 0; i < langs.length; i++) {
                    int itemY = dropY + i * itemH;
                    boolean hoverItem = ctx.mouseX >= dropX && ctx.mouseX < dropX + dropW && ctx.mouseY >= itemY && ctx.mouseY < itemY + itemH;
                    ctx.batcher.box(dropX + 1, itemY + 1, dropX + dropW - 1, itemY + itemH - 1, hoverItem ? 0xFF2A2A35 : 0xFF141418);
                    ctx.batcher.text(langs[i], dropX + 8, itemY + 5, hoverItem ? 0xFFFFAA00 : 0xFFE0E0E0);
                }
            }

            ctx.batcher.getCtx().getMatrices().pop();
        }

        // --- RENDER PATCHER POPUP ---
        if (patcherPopupScale > 0.01f) {
            // Dark Backdrop Focus overlay with fading opacity
            int alpha = (int) (0x90 * patcherPopupScale);
            ctx.batcher.box(0, 0, w, h, (alpha << 24) | 0x0B0B0E);

            // Apply scaling matrix for the popup contents
            ctx.batcher.getCtx().getMatrices().push();
            ctx.batcher.getCtx().getMatrices().translate(w / 2.0f, h / 2.0f, 0.0f);
            ctx.batcher.getCtx().getMatrices().scale(patcherPopupScale, patcherPopupScale, 1.0f);
            ctx.batcher.getCtx().getMatrices().translate(-w / 2.0f, -h / 2.0f, 0.0f);

            // Patcher Box Dimensions (BBS size: 450x300 or similar)
            int pW = 450;
            int pH = 300;
            int pX = (w - pW) / 2;
            int pY = (h - pH) / 2;

            if (patcherPanel != null) {
                patcherPanel.resize(pX, pY, pW, pH);
                patcherPanel.render(ctx);
            }

            ctx.batcher.getCtx().getMatrices().pop();
        }

        // --- RENDER VIEWPORT RIGHT-CLICK CONTEXT POPUP ---
        if (showRightClickPopup) {
            int popW = 120;
            int itemH = 18;
            int popH = itemH * (isRightClickingLight ? 1 : 2);
            int popX = rightClickPopupX;
            int popY = rightClickPopupY;
            
            if (popX + popW > w) popX = w - popW - 4;
            if (popY + popH > h) popY = h - popH - 4;
            
            ctx.batcher.box(popX, popY, popX + popW, popY + popH, 0xFF141418);
            ctx.batcher.outline(popX, popY, popX + popW, popY + popH, 0xFFFFAA00, 1);
            
            if (isRightClickingLight) {
                // Borrar Luz (Delete Light)
                boolean hover1 = ctx.mouseX >= popX && ctx.mouseX < popX + popW && ctx.mouseY >= popY && ctx.mouseY < popY + popH;
                ctx.batcher.box(popX + 1, popY + 1, popX + popW - 1, popY + popH - 1, hover1 ? 0xFF352024 : 0xFF141418);
                ctx.batcher.text(CALKeys.DELETE_LIGHT.get(), popX + 8, popY + 5, hover1 ? 0xFFEF5350 : 0xFFE0E0E0);
            } else {
                // Point Light
                boolean hover1 = ctx.mouseX >= popX && ctx.mouseX < popX + popW && ctx.mouseY >= popY && ctx.mouseY < popY + itemH;
                ctx.batcher.box(popX + 1, popY + 1, popX + popW - 1, popY + itemH - 1, hover1 ? 0xFF2B2B38 : 0xFF141418);
                ctx.batcher.text(CALKeys.ADD_POINT.get(), popX + 8, popY + 5, hover1 ? 0xFFFFAA00 : 0xFFE0E0E0);
                
                // Spot Light
                boolean hover2 = ctx.mouseX >= popX && ctx.mouseX < popX + popW && ctx.mouseY >= popY + itemH && ctx.mouseY < popY + popH;
                ctx.batcher.box(popX + 1, popY + itemH + 1, popX + popW - 1, popY + popH - 1, hover2 ? 0xFF2B2B38 : 0xFF141418);
                ctx.batcher.text(CALKeys.ADD_SPOT.get(), popX + 8, popY + itemH + 5, hover2 ? 0xFFFFAA00 : 0xFFE0E0E0);
            }
        }

        // --- 5. RENDER DIAGNOSTIC STATUS BAR ---
        int barY = botY;
        ctx.batcher.box(0, barY, w, barY + statusBarH, 0xFF0E0E12);
        ctx.batcher.outline(0, barY, w, barY + statusBarH, 0xFF1C1C24, 1);

        // Left side: Mouse position
        ctx.batcher.text(CALKeys.MOUSE_POS.get(ctx.mouseX, ctx.mouseY), 8, barY + 4, 0xFF888899);

        // Center: Scroll value
        String scrollStr = CALKeys.SCROLL_VALUE.get(lastScrollY);
        int scrollW = 100;
        ctx.batcher.text(scrollStr, (w - scrollW) / 2, barY + 4, 0xFF888899);

        // Right side: Pressed keys
        String keysStr = CALKeys.PRESSED_KEYS.get(getPressedKeysString());
        ctx.batcher.text(keysStr, w - 180, barY + 4, 0xFF888899);
    }

    private String getPressedKeysString() {
        if (currentlyPressedKeys.isEmpty()) return "None";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < currentlyPressedKeys.size(); i++) {
            int key = currentlyPressedKeys.get(i);
            String name = getKeyName(key);
            if (name != null) {
                if (sb.length() > 0) sb.append(" + ");
                sb.append(name);
            }
        }
        return sb.toString();
    }

    private String getKeyName(int key) {
        if (key == GLFW.GLFW_KEY_UNKNOWN) return "???";
        try {
            InputUtil.Key inputKey = InputUtil.fromKeyCode(key, 0);
            String name = inputKey.getLocalizedText().getString();
            if (name != null && !name.isEmpty() && !name.contains("key.keyboard")) {
                return name;
            }
        } catch (Throwable ignored) {}

        return switch (key) {
            case GLFW.GLFW_KEY_LEFT_CONTROL, GLFW.GLFW_KEY_RIGHT_CONTROL -> "Ctrl";
            case GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_KEY_RIGHT_SHIFT -> "Shift";
            case GLFW.GLFW_KEY_LEFT_ALT, GLFW.GLFW_KEY_RIGHT_ALT -> "Alt";
            case GLFW.GLFW_KEY_LEFT_SUPER, GLFW.GLFW_KEY_RIGHT_SUPER -> "Win";
            case GLFW.GLFW_KEY_SPACE -> "Space";
            case GLFW.GLFW_KEY_ENTER -> "Enter";
            case GLFW.GLFW_KEY_ESCAPE -> "Esc";
            case GLFW.GLFW_KEY_DELETE -> "Delete";
            case GLFW.GLFW_KEY_BACKSPACE -> "Backspace";
            case GLFW.GLFW_KEY_TAB -> "Tab";
            case GLFW.GLFW_KEY_INSERT -> "Insert";
            case GLFW.GLFW_KEY_PAGE_UP -> "Page Up";
            case GLFW.GLFW_KEY_PAGE_DOWN -> "Page Down";
            case GLFW.GLFW_KEY_HOME -> "Home";
            case GLFW.GLFW_KEY_END -> "End";
            case GLFW.GLFW_KEY_UP -> "Up";
            case GLFW.GLFW_KEY_DOWN -> "Down";
            case GLFW.GLFW_KEY_LEFT -> "Left";
            case GLFW.GLFW_KEY_RIGHT -> "Right";
            case GLFW.GLFW_KEY_CAPS_LOCK -> "Caps Lock";
            case GLFW.GLFW_KEY_SCROLL_LOCK -> "Scroll Lock";
            case GLFW.GLFW_KEY_NUM_LOCK -> "Num Lock";
            case GLFW.GLFW_KEY_PRINT_SCREEN -> "Print Screen";
            case GLFW.GLFW_KEY_PAUSE -> "Pause";
            default -> {
                try {
                    String glfwName = GLFW.glfwGetKeyName(key, 0);
                    if (glfwName != null) yield glfwName.toUpperCase();
                } catch (Throwable ignored) {}
                yield "Key " + key;
            }
        };
    }

    @Override
    public boolean scroll(int mx, int my, double amount) {
        if (showPatcherPopup) {
            if (patcherPanel != null) {
                patcherPanel.scroll(mx, my, amount);
            }
            return true;
        }
        if (showSettingsPopup) return true;

        
        float targetLeftPanelW = showLeftSidebar ? leftSidebarW : 0;
        int animLeftPanelW = (int) (CalSettings.INSTANCE.simplifyAnimations ? targetLeftPanelW : (currentLeftPanelW < 0 ? targetLeftPanelW : currentLeftPanelW));
        float targetTimelineH = (showTimeline && LightGizmo.INSTANCE.getSelectedLight() != null) ? 140 : 0;
        int animTimelineH = (int) (CalSettings.INSTANCE.simplifyAnimations ? targetTimelineH : (currentTimelineH < 0 ? targetTimelineH : currentTimelineH));
        int animRightPanelW = (int) (CalSettings.INSTANCE.simplifyAnimations ? (showRightSidebar ? rightSidebarW : 0) : (currentRightPanelW < 0 ? (showRightSidebar ? rightSidebarW : 0) : currentRightPanelW));

        int contentH = h - statusBarH - animTimelineH;
        if (animTimelineH > 0 && timelinePanel != null && mx >= 0 && mx < w && my >= contentH && my < h - statusBarH) {
            timelinePanel.scroll(mx, my, amount);
            return true;
        }
        int leftPanelW = animLeftPanelW;
        if (showLeftSidebar && mx >= 0 && mx < leftPanelW && my >= topMenuH + tabsH) {
            int listStartY = topMenuH + tabsH + 6 + 22 + 22;
            contentH = h - statusBarH;
            int visibleHeight = contentH - listStartY - 10;
            int itemH = 20;
            int lightCount = 0;
            for (LightInstance l : LightManager.INSTANCE.getPointLights()) {
                boolean matches = searchQuery.isEmpty() || 
                                  String.valueOf(l.id).contains(searchQuery) || 
                                  (l.name != null && l.name.toLowerCase().contains(searchQuery.toLowerCase()));
                if (matches) {
                    lightCount++;
                }
            }
            for (LightInstance l : LightManager.INSTANCE.getSpotLights()) {
                boolean matches = searchQuery.isEmpty() || 
                                  String.valueOf(l.id).contains(searchQuery) || 
                                  (l.name != null && l.name.toLowerCase().contains(searchQuery.toLowerCase()));
                if (matches) {
                    lightCount++;
                }
            }
            int totalListHeight = lightCount * (itemH + 2);
            int maxScroll = Math.min(0, visibleHeight - totalListHeight);
            leftPanelScrollY = Math.max(maxScroll, Math.min(0, leftPanelScrollY + (int) (amount * 16)));
            return true;
        }
        if (animRightPanelW > 0 && activeSettingsPanel != null && mx >= w - animRightPanelW && mx < w && my >= topMenuH + tabsH && my < h - statusBarH - animTimelineH) {
            return activeSettingsPanel.scroll(mx, my, amount);
        }
        cameraSpeedIndex = Math.max(1, Math.min(40, cameraSpeedIndex + (int) amount));
        lastScrollY = getCameraSpeed();
        return true;
    }

    @Override
    public boolean mouseClicked(int mx, int my, int btn) {
        if (showPatcherPopup) {
            if (patcherPanel != null) {
                patcherPanel.mouseClicked(mx, my, btn);
            }
            return true;
        }
        if (CLUITrackpad.activeEditingTrackpad != null) {
            if (!CLUITrackpad.activeEditingTrackpad.isHovered(mx, my)) {
                CLUITrackpad.activeEditingTrackpad.commitEdit();
            }
        }
        
        float targetLeftPanelW = showLeftSidebar ? leftSidebarW : 0;
        int animLeftPanelW = (int) (CalSettings.INSTANCE.simplifyAnimations ? targetLeftPanelW : (currentLeftPanelW < 0 ? targetLeftPanelW : currentLeftPanelW));
        float targetTimelineH = (showTimeline && LightGizmo.INSTANCE.getSelectedLight() != null) ? 140 : 0;
        int animTimelineH = (int) (CalSettings.INSTANCE.simplifyAnimations ? targetTimelineH : (currentTimelineH < 0 ? targetTimelineH : currentTimelineH));
        int animRightPanelW = (int) (CalSettings.INSTANCE.simplifyAnimations ? (showRightSidebar ? rightSidebarW : 0) : (currentRightPanelW < 0 ? (showRightSidebar ? rightSidebarW : 0) : currentRightPanelW));

        int contentH = h - statusBarH - animTimelineH;
        if (!showSettingsPopup && activeMenuDropdown == 0) {
            if (timelinePanel != null && timelinePanel.isShowEasingDropdown()) {
                timelinePanel.mouseClicked(mx, my, btn);
                return true;
            }
            if (animTimelineH > 0 && timelinePanel != null && mx >= 0 && mx < w && my >= contentH && my < h - statusBarH) {
                timelinePanel.mouseClicked(mx, my, btn);
                return true;
            }
        }
        int leftPanelW = animLeftPanelW;

        // Active right-click popup click handling
        if (showRightClickPopup) {
            int popW = 120;
            int itemH = 18;
            int popH = itemH * (isRightClickingLight ? 1 : 2);
            int popX = rightClickPopupX;
            int popY = rightClickPopupY;
            
            if (popX + popW > w) popX = w - popW - 4;
            if (popY + popH > h) popY = h - popH - 4;
            
            if (mx >= popX && mx < popX + popW && my >= popY && my < popY + popH) {
                int clickedIdx = (my - popY) / itemH;
                if (isRightClickingLight) {
                    if (clickedIdx == 0) { // Borrar Luz (Delete Light)
                        CALUndoManager.pushState();
                        LightInstance selected = LightGizmo.INSTANCE.getSelectedLight();
                        if (selected != null) {
                            if (selected.isSpot) {
                                LightManager.INSTANCE.removeSpot(selected.id);
                            } else {
                                LightManager.INSTANCE.removePoint(selected.id);
                            }
                            LightGizmo.INSTANCE.setSelectedLight(null);
                            rebuildSettings();
                        }
                    }
                } else {
                    if (clickedIdx == 0) { // Point Light
                        CALUndoManager.pushState();
                        int id = ThreadLocalRandom.current().nextInt(100000, 999999);
                        LightInstance light = LightManager.INSTANCE.updatePoint(
                            id, 
                            (float) rightClickWorldPos.x, 
                            (float) rightClickWorldPos.y, 
                            (float) rightClickWorldPos.z, 
                            1f, 1f, 1f, 1.0f, 10.0f
                        );
                        light.persistent = true;
                        LightGizmo.INSTANCE.setSelectedLight(light);
                        rebuildSettings();
                    } else if (clickedIdx == 1) { // Spot Light
                        CALUndoManager.pushState();
                        int id = ThreadLocalRandom.current().nextInt(100000, 999999);
                        LightInstance light = LightManager.INSTANCE.updateSpot(
                            id, 
                            (float) rightClickWorldPos.x, 
                            (float) rightClickWorldPos.y, 
                            (float) rightClickWorldPos.z, 
                            0f, -1f, 0f, 1f, 1f, 1f, 1.0f, 15.0f, 30.0f, 15.0f
                        );
                        light.persistent = true;
                        LightGizmo.INSTANCE.setSelectedLight(light);
                        rebuildSettings();
                    }
                }
                showRightClickPopup = false;
                isRightClickingLight = false;
                return true;
            }
            showRightClickPopup = false;
            isRightClickingLight = false;
        }

        // Viewport right click detection
        if (btn == 1 && !showSettingsPopup && activeMenuDropdown == 0) {
            if (mx >= leftPanelW && mx < w - animRightPanelW && my >= topMenuH + tabsH && my < h - statusBarH - animTimelineH) {
                MinecraftClient client = MinecraftClient.getInstance();
                if (client.world != null) {
                    Vec3d rayDir = LightGizmo.INSTANCE.getRayDirection(mx, my);
                    net.minecraft.client.render.Camera camera = client.gameRenderer.getCamera();
                    Vec3d rayStart = camera.getPos();
                    
                    // Check if right-clicking directly on a light billboard
                    LightInstance clickedLight = LightGizmo.INSTANCE.checkBillboardClickExternal(rayStart, rayDir);
                    if (clickedLight != null) {
                        LightGizmo.INSTANCE.setSelectedLight(clickedLight);
                        rebuildSettings();
                        isRightClickingLight = true;
                        rightClickWorldPos = new Vec3d(clickedLight.x, clickedLight.y, clickedLight.z);
                    } else {
                        isRightClickingLight = false;
                        Vec3d rayEnd = rayStart.add(rayDir.multiply(100.0));
                        BlockHitResult hit = client.world.raycast(new RaycastContext(
                            rayStart,
                            rayEnd,
                            RaycastContext.ShapeType.OUTLINE,
                            RaycastContext.FluidHandling.NONE,
                            client.player
                        ));
                        
                        if (hit != null && hit.getType() == HitResult.Type.BLOCK) {
                            rightClickWorldPos = hit.getPos().add(Vec3d.of(hit.getSide().getVector()).multiply(0.2));
                        } else {
                            rightClickWorldPos = rayStart.add(rayDir.multiply(8.0));
                        }
                    }
                    
                    showRightClickPopup = true;
                    rightClickPopupX = mx;
                    rightClickPopupY = my;
                    return true;
                }
            }
        }

        // Border dragging start detection
        if (btn == 0 && !showSettingsPopup && activeMenuDropdown == 0) {
            boolean hoverLeftBorder = showLeftSidebar && mx >= leftPanelW - 3 && mx <= leftPanelW + 3 && my >= topMenuH && my < contentH;
            boolean hoverRightBorder = showRightSidebar && mx >= w - animRightPanelW - 3 && mx <= w - animRightPanelW + 3 && my >= topMenuH && my < contentH;

            if (hoverLeftBorder) {
                draggingLeftBorder = true;
                return true;
            }
            if (hoverRightBorder) {
                draggingRightBorder = true;
                return true;
            }

            // Timeline top border dragging start detection
            boolean hoverTimelineBorder = showTimeline && LightGizmo.INSTANCE.getSelectedLight() != null
                    && mx >= leftPanelW && mx <= w - animRightPanelW
                    && my >= contentH - 3 && my <= contentH + 3;
            if (hoverTimelineBorder) {
                draggingTimelineBorder = true;
                return true;
            }
        }

        // --- AJUSTES POPUP CLICKS HANDLING ---
        if (showSettingsPopup) {
            int pW = 520;
            int pH = 320;
            int pX = (w - pW) / 2;
            int pY = (h - pH) / 2;
            int headerH = 22;
            int sideW = 90;
            int contentX = pX + sideW + 10;

            if (showLanguageDropdown) {
                int dropX = contentX;
                int dropY = pY + headerH + 52;
                int dropW = 150;
                int itemH = 18;
                int dropH = itemH * 6;

                if (mx >= dropX && mx < dropX + dropW && my >= dropY && my < dropY + dropH) {
                    int clickedIdx = (my - dropY) / itemH;
                    String nextLang = "en_us";
                    switch (clickedIdx) {
                        case 0: nextLang = "en_us"; break;
                        case 1: nextLang = "es_es"; break;
                        case 2: nextLang = "fr_fr"; break;
                        case 3: nextLang = "ru_ru"; break;
                        case 4: nextLang = "pt_br"; break;
                        case 5: nextLang = "pt_pt"; break;
                    }
                    CalSettings.INSTANCE.language = nextLang;
                    CalSettings.INSTANCE.englishSelected = nextLang.equals("en_us");
                    CalSettings.INSTANCE.save();
                    showLanguageDropdown = false;
                    return true;
                } else {
                    showLanguageDropdown = false;
                    if (mx >= contentX && mx < contentX + 150 && my >= pY + headerH + 34 && my < pY + headerH + 52) {
                        return true;
                    }
                }
            }

            // Close when clicking Header close [X]
            if (mx >= pX + pW - 22 && mx < pX + pW - 6 && my >= pY + 4 && my < pY + 18) {
                showSettingsPopup = false;
                activeRebindingKeyIndex = -1;
                return true;
            }

            // Close when clicking footer Cerrar button
            int btnW = 56;
            int btnH = 16;
            int btnX = pX + pW - btnW - 10;
            int btnY = pY + pH - btnH - 10;
            if (mx >= btnX && mx < btnX + btnW && my >= btnY && my < btnY + btnH) {
                showSettingsPopup = false;
                activeRebindingKeyIndex = -1;
                return true;
            }

            // Category list clicks (sidebar)
            if (mx >= pX + 2 && mx < pX + sideW - 2) {
                if (my >= pY + headerH + 6 && my < pY + headerH + 24) {
                    selectedSettingsCategory = 0;
                    activeRebindingKeyIndex = -1;
                    return true;
                }
                if (my >= pY + headerH + 28 && my < pY + headerH + 46) {
                    selectedSettingsCategory = 1;
                    activeRebindingKeyIndex = -1;
                    return true;
                }
                if (my >= pY + headerH + 50 && my < pY + headerH + 68) {
                    selectedSettingsCategory = 2;
                    activeRebindingKeyIndex = -1;
                    return true;
                }
                if (my >= pY + headerH + 72 && my < pY + headerH + 90) {
                    selectedSettingsCategory = 3;
                    activeRebindingKeyIndex = -1;
                    return true;
                }
            }

            // Category Content Clicks
            if (selectedSettingsCategory == 0) {
                // Language selection
                if (mx >= contentX && mx < contentX + 150 && my >= pY + headerH + 34 && my < pY + headerH + 52) {
                    showLanguageDropdown = !showLanguageDropdown;
                    return true;
                }
            } else if (selectedSettingsCategory == 1) {
                // Scale selections
                for (int sc = 1; sc <= 4; sc++) {
                    int scX = contentX + (sc - 1) * 32;
                    int scY = pY + headerH + 34;
                    if (mx >= scX && mx < scX + 26 && my >= scY && my < scY + 18) {
                        CalSettings.INSTANCE.guiScale = sc;
                        CalSettings.INSTANCE.save();
                        MinecraftClient.getInstance().options.getGuiScale().setValue(sc);
                        MinecraftClient.getInstance().onResolutionChanged();
                        return true;
                    }
                }

                // Slider click tracking
                int sliderSectionY = pY + headerH + 58;
                int sliderY = sliderSectionY + 28;
                if (mx >= contentX - 4 && mx <= contentX + 164 && my >= sliderY - 6 && my <= sliderY + 8) {
                    float t = (float)(mx - contentX) / 160.0f;
                    t = Math.max(0.0f, Math.min(1.0f, t));
                    CalSettings.INSTANCE.gizmoSize = t * 10.0f;
                    CalSettings.INSTANCE.save();
                    draggingGizmoSlider = true;
                    return true;
                }

                // Unidad de tiempo button clicks
                int timeUnitY = pY + headerH + 110;
                int ticksBtnX = contentX;
                int secsBtnX = contentX + 80;
                int btnW2 = 72;
                int btnH2 = 18;
                int btnY2 = timeUnitY + 28;
                if (my >= btnY2 && my < btnY2 + btnH2) {
                    if (mx >= ticksBtnX && mx < ticksBtnX + btnW2) {
                        CalSettings.INSTANCE.durationMode = "ticks";
                        CalSettings.INSTANCE.save();
                        return true;
                    }
                    if (mx >= secsBtnX && mx < secsBtnX + btnW2) {
                        CalSettings.INSTANCE.durationMode = "seconds";
                        CalSettings.INSTANCE.save();
                        return true;
                    }
                }

                // Simplificar animaciones clicks
                int animSectionY = timeUnitY + 52;
                int yesBtnX = contentX;
                int noBtnX = contentX + 80;
                int animBtnY = animSectionY + 28;
                if (my >= animBtnY && my < animBtnY + btnH2) {
                    if (mx >= yesBtnX && mx < yesBtnX + btnW2) {
                        CalSettings.INSTANCE.simplifyAnimations = true;
                        CalSettings.INSTANCE.save();
                        return true;
                    }
                    if (mx >= noBtnX && mx < noBtnX + btnW2) {
                        CalSettings.INSTANCE.simplifyAnimations = false;
                        CalSettings.INSTANCE.save();
                        return true;
                    }
                }
            } else if (selectedSettingsCategory == 2) {
                // Key rebind button clicks
                int keyListY = pY + headerH + 10;
                int rowHeight = 16;
                int colWidth = 195;
                
                for (int i = 0; i < KEY_NAMES.length; i++) {
                    int col = i / 6;
                    int row = i % 6;
                    
                    int rowX = contentX + col * colWidth;
                    int rowY = keyListY + row * (rowHeight + 6);
                    
                    int kBtnX = rowX + 110;
                    int btnW3 = 75;
                    int btnH3 = 14;
                    
                    if (mx >= kBtnX && mx < kBtnX + btnW3 && my >= rowY && my < rowY + btnH3) {
                        if (i < 3) {
                            activeRebindingKeyIndex = i;
                        }
                        return true;
                    }
                }
            } else if (selectedSettingsCategory == 3) {
                int colW = 200;
                int leftColX = contentX;
                int rightColX = contentX + 215;
                int startY = pY + headerH + 10;
                int itemSpacing = 20;

                // --- COLUMNA IZQUIERDA CLICKS ---
                int curY = startY;
                curY += 12; // Title label offset

                // Shadow Quality segmented buttons
                String[] qLabels = {"LOW", "MED", "HIGH", "ULTRA"};
                int segW = (colW - 9) / 4;
                for (int i = 0; i < qLabels.length; i++) {
                    int qBtnX = leftColX + i * (segW + 3);
                    if (mx >= qBtnX && mx < qBtnX + segW && my >= curY && my < curY + 14) {
                        LightConfig.shadowQuality = i;
                        return true;
                    }
                }
                curY += 20;

                // Shadow Cache Checkbox click
                if (mx >= leftColX && mx < leftColX + colW && my >= curY && my < curY + 12) {
                    LightConfig.shadowCache = !LightConfig.shadowCache;
                    return true;
                }
                curY += itemSpacing;

                // Shadow Blocks Checkbox click
                if (mx >= leftColX && mx < leftColX + colW && my >= curY && my < curY + 12) {
                    LightConfig.shadowBlocks = !LightConfig.shadowBlocks;
                    return true;
                }
                curY += itemSpacing;

                // Shadow Block Radius Trackpad click
                if (trackShadowBlockRadius.mouseClicked(mx, my, btn)) {
                    return true;
                }


                // --- COLUMNA DERECHA CLICKS ---
                curY = startY;

                // Auto Lights Enable Checkbox click
                if (mx >= rightColX && mx < rightColX + colW && my >= curY && my < curY + 12) {
                    LightConfig.autoLights = !LightConfig.autoLights;
                    return true;
                }
                curY += itemSpacing;

                // Auto Lights Shadows Checkbox click
                boolean autoLightsOn = LightConfig.autoLights;
                if (autoLightsOn) {
                    if (mx >= rightColX && mx < rightColX + colW && my >= curY && my < curY + 12) {
                        LightConfig.autoLightShadows = !LightConfig.autoLightShadows;
                        return true;
                    }
                }
                curY += itemSpacing;

                if (autoLightsOn) {
                    if (trackAutoLightIntensity.mouseClicked(mx, my, btn) ||
                        trackAutoLightReach.mouseClicked(mx, my, btn) ||
                        trackAutoLightRadius.mouseClicked(mx, my, btn) ||
                        trackAutoLightMax.mouseClicked(mx, my, btn)) {
                        return true;
                    }
                }
            }

            // Absorb any click inside modal bounding box to prevent clicks leaking through
            if (mx >= pX && mx < pX + pW && my >= pY && my < pY + pH) {
                return true;
            }

            // Click outside the popup closes it
            showSettingsPopup = false;
            activeRebindingKeyIndex = -1;
            return true;
        }



        // --- DROPDOWN OVERLAY CLICKS ---
        if (activeMenuDropdown != 0) {
            if (activeMenuDropdown == 1) {
                int dropX = 110;
                int dropY = topMenuH;
                int dropW = 120;
                int itemH = 18;
                if (mx >= dropX && mx < dropX + dropW && my >= dropY && my < dropY + itemH * 4) {
                    int clickedIdx = (my - dropY) / itemH;
                    if (clickedIdx == 0) {
                        showSettingsPopup = true;
                    } else if (clickedIdx == 1) {
                        LightSaveManager.forceSaveCurrent();
                        MinecraftClient mc = MinecraftClient.getInstance();
                        if (mc.player != null) {
                            mc.player.sendMessage(Text.literal(CALKeys.SAVED_SUCCESS.get()), false);
                        }
                    } else if (clickedIdx == 2) {
                        showPatcherPopup = true;
                        if (patcherPanel != null) {
                            patcherPanel.reload();
                        }
                    } else if (clickedIdx == 3) {
                        MinecraftClient mc = MinecraftClient.getInstance();
                        if (mc.currentScreen != null) {
                            mc.currentScreen.close();
                        } else {
                            mc.setScreen(null);
                        }
                    }
                    activeMenuDropdown = 0;
                    return true;
                }
            } else if (activeMenuDropdown == 2) {
                int dropX = 165;
                int dropY = topMenuH;
                int dropW = 110;
                int itemH = 18;
                if (mx >= dropX && mx < dropX + dropW && my >= dropY && my < dropY + itemH * 3) {
                    int clickedIdx = (my - dropY) / itemH;
                    if (clickedIdx == 0) {
                        showLeftSidebar = !showLeftSidebar;
                    } else if (clickedIdx == 1) {
                        showRightSidebar = !showRightSidebar;
                        if (showRightSidebar) {
                            showTimeline = false;
                        }
                    } else if (clickedIdx == 2) {
                        showTimeline = !showTimeline;
                        if (showTimeline) {
                            showRightSidebar = false;
                        }
                    }
                    rebuildSettings();
                    activeMenuDropdown = 0;
                    return true;
                }
            }
            activeMenuDropdown = 0;
            return true;
        }

        // --- TOP MENU TABS CLICKS ---
        if (my >= 0 && my < topMenuH) {
            if (mx >= 110 && mx < 165) {
                activeMenuDropdown = 1;
                return true;
            } else if (mx >= 165 && mx < 240) {
                activeMenuDropdown = 2;
                return true;
            }
        }

        // --- RIGHT SIDEBAR INSPECTOR CLICKS ---
        if (showRightSidebar && activeSettingsPanel != null && activeSettingsPanel.mouseClicked(mx, my, btn)) {
            return true;
        }

        // --- LEFT SIDEBAR ESQUEMA CLICKS ---
        if (showLeftSidebar) {
            // Search bar click focus
            int searchY = topMenuH + tabsH + 6;
            if (mx >= 8 && mx < leftPanelW - 8 && my >= searchY && my < searchY + 16) {
                if (!searchQuery.isEmpty() && mx >= leftPanelW - 20 && mx < leftPanelW - 8 && my >= searchY + 2 && my < searchY + 14) {
                    searchQuery = "";
                    leftPanelScrollY = 0;
                    return true;
                }
                searchFocused = true;
                return true;
            } else {
                searchFocused = false;
            }

            // Quick Add Point
            int addY = searchY + 22;
            int btnW = (leftPanelW - 22) / 2;
            if (mx >= 8 && mx < 8 + btnW && my >= addY && my < addY + 16) {
                CALUndoManager.pushState();
                Vec3d p = MinecraftClient.getInstance().gameRenderer.getCamera().getPos();
                int id = ThreadLocalRandom.current().nextInt(100000, 999999);
                LightInstance light = LightManager.INSTANCE.updatePoint(id, (float) p.x, (float) p.y, (float) p.z, 1f, 1f, 1f, 1.0f, 10.0f);
                light.persistent = true;
                LightGizmo.INSTANCE.setSelectedLight(light);
                rebuildSettings();
                return true;
            }

            // Quick Add Spot
            if (mx >= 8 + btnW + 6 && mx < leftPanelW - 8 && my >= addY && my < addY + 16) {
                CALUndoManager.pushState();
                Vec3d p = MinecraftClient.getInstance().gameRenderer.getCamera().getPos();
                int id = ThreadLocalRandom.current().nextInt(100000, 999999);
                LightInstance light = LightManager.INSTANCE.updateSpot(id, (float) p.x, (float) p.y, (float) p.z, 0f, -1f, 0f, 1f, 1f, 1f, 1.0f, 15.0f, 30.0f, 15.0f);
                light.persistent = true;
                LightGizmo.INSTANCE.setSelectedLight(light);
                rebuildSettings();
                return true;
            }

            // Left sidebar list clicking
            int listStartY = addY + 22;
            int itemH = 20;
            int visibleHeight = h - statusBarH - listStartY - 10;
            if (my >= listStartY && my < listStartY + visibleHeight) {
                for (int i = 0; i < cachedLights.size(); i++) {
                    LightInstance light = cachedLights.get(i);
                    int itemY = listStartY + i * (itemH + 2) + leftPanelScrollY;

                    // Check eye click
                    if (mx >= leftPanelW - 42 && mx < leftPanelW - 28 && my >= itemY + 3 && my < itemY + 17) {
                        light.visible = !light.visible;
                        return true;
                    }

                    // Check trash click
                    if (mx >= leftPanelW - 24 && mx < leftPanelW - 10 && my >= itemY + 3 && my < itemY + 17) {
                        CALUndoManager.pushState();
                        if (light.isSpot) {
                            LightManager.INSTANCE.removeSpot(light.id);
                        } else {
                            LightManager.INSTANCE.removePoint(light.id);
                        }
                        if (LightGizmo.INSTANCE.getSelectedLight() == light) {
                            LightGizmo.INSTANCE.setSelectedLight(null);
                            rebuildSettings();
                        }
                        return true;
                    }

                    // Check list item select click
                    if (mx >= 8 && mx < leftPanelW - 8 && my >= itemY && my < itemY + itemH) {
                        LightGizmo.INSTANCE.setSelectedLight(light);
                        rebuildSettings();
                        return true;
                    }
                }
            }
        }

        // --- VIEWPORT TOOLBAR CLICKS ---
        // Light Icons Toggle Button (Moved up into the Tab row!)
        if (mx >= leftPanelW + 10 && mx < leftPanelW + 110 && my >= topMenuH + 2 && my < topMenuH + 18) {
            LightGizmo.renderLightIcons = !LightGizmo.renderLightIcons;
            return true;
        }

        // Alternating Viewport Panel Toggle Button click
        if (mx >= leftPanelW + 115 && mx < leftPanelW + 225 && my >= topMenuH + 2 && my < topMenuH + 18) {
            if (showTimeline) {
                showTimeline = false;
                showRightSidebar = true;
            } else {
                showTimeline = true;
                showRightSidebar = false;
            }
            rebuildSettings();
            return true;
        }

        return false;
    }

    @Override
    public boolean mouseReleased(int mx, int my, int btn) {
        if (showPatcherPopup) return true;
        if (btn == 0) {
            draggingGizmoSlider = false;
            if (draggingLeftBorder || draggingRightBorder || draggingTimelineBorder) {
                draggingLeftBorder = false;
                draggingRightBorder = false;
                draggingTimelineBorder = false;
                return true;
            }
        }
        if (showSettingsPopup) {
            if (selectedSettingsCategory == 3) {
                return trackShadowBlockRadius.mouseReleased(mx, my, btn) ||
                       trackAutoLightIntensity.mouseReleased(mx, my, btn) ||
                       trackAutoLightReach.mouseReleased(mx, my, btn) ||
                       trackAutoLightRadius.mouseReleased(mx, my, btn) ||
                       trackAutoLightMax.mouseReleased(mx, my, btn);
            }
            return true;
        }

        float targetTimelineH = (showTimeline && LightGizmo.INSTANCE.getSelectedLight() != null) ? timelineHeight : 0;
        int animTimelineH = (int) (CalSettings.INSTANCE.simplifyAnimations ? targetTimelineH : (currentTimelineH < 0 ? targetTimelineH : currentTimelineH));
        int animRightPanelW = (int) (CalSettings.INSTANCE.simplifyAnimations ? (showRightSidebar ? rightSidebarW : 0) : (currentRightPanelW < 0 ? (showRightSidebar ? rightSidebarW : 0) : currentRightPanelW));

        int contentH = h - statusBarH - animTimelineH;
        if (animTimelineH > 0 && timelinePanel != null && mx >= 0 && mx < w && my >= contentH && my < h - statusBarH) {
            timelinePanel.mouseReleased(mx, my, btn);
            return true;
        }
        if (animRightPanelW > 0 && activeSettingsPanel != null && activeSettingsPanel.mouseReleased(mx, my, btn)) {
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        if (btn == 0 && (draggingLeftBorder || draggingRightBorder || draggingTimelineBorder)) {
            if (draggingLeftBorder) {
                leftSidebarW = Math.max(80, Math.min(w / 3, (int) mx));
            }
            if (draggingRightBorder) {
                rightSidebarW = Math.max(100, Math.min(w / 3, (int) (w - mx)));
            }
            if (draggingTimelineBorder) {
                timelineHeight = h - statusBarH - (int) my;
                timelineHeight = Math.max(60, Math.min(h / 2, timelineHeight));
            }
            rebuildSettings();
            return true;
        }
        if (showPatcherPopup) return true;
        if (showSettingsPopup) {
            if (btn == 0 && selectedSettingsCategory == 1 && draggingGizmoSlider) {
                int pW = 520;
                int pX = (w - pW) / 2;
                int sideW = 90;
                int contentX = pX + sideW + 10;
                float t = (float)(mx - contentX) / 160.0f;
                t = Math.max(0.0f, Math.min(1.0f, t));
                CalSettings.INSTANCE.gizmoSize = t * 10.0f;
                CalSettings.INSTANCE.save();
            } else if (selectedSettingsCategory == 3) {
                return trackShadowBlockRadius.mouseDragged(mx, my, btn, dx, dy) ||
                       trackAutoLightIntensity.mouseDragged(mx, my, btn, dx, dy) ||
                       trackAutoLightReach.mouseDragged(mx, my, btn, dx, dy) ||
                       trackAutoLightRadius.mouseDragged(mx, my, btn, dx, dy) ||
                       trackAutoLightMax.mouseDragged(mx, my, btn, dx, dy);
            }
            return true;
        }
        float targetTimelineH = (showTimeline && LightGizmo.INSTANCE.getSelectedLight() != null) ? timelineHeight : 0;
        int animTimelineH = (int) (CalSettings.INSTANCE.simplifyAnimations ? targetTimelineH : (currentTimelineH < 0 ? targetTimelineH : currentTimelineH));
        int animRightPanelW = (int) (CalSettings.INSTANCE.simplifyAnimations ? (showRightSidebar ? rightSidebarW : 0) : (currentRightPanelW < 0 ? (showRightSidebar ? rightSidebarW : 0) : currentRightPanelW));

        int contentH = h - statusBarH - animTimelineH;
        if (animTimelineH > 0 && timelinePanel != null && mx >= 0 && mx < w && my >= contentH && my < h - statusBarH) {
            timelinePanel.mouseDragged(mx, my, btn, dx, dy);
            return true;
        }
        if (animRightPanelW > 0 && activeSettingsPanel != null && activeSettingsPanel.mouseDragged(mx, my, btn, dx, dy)) {
            return true;
        }
        return false;
    }



    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (showPatcherPopup) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                showPatcherPopup = false;
                return true;
            }
            return true;
        }
        boolean ctrl = Screen.hasControlDown();
        if (ctrl && keyCode == GLFW.GLFW_KEY_Z) {
            CALUndoManager.undo();
            return true;
        }
        if (ctrl && keyCode == GLFW.GLFW_KEY_Y) {
            CALUndoManager.redo();
            return true;
        }

        if (showSettingsPopup && activeRebindingKeyIndex != -1) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                activeRebindingKeyIndex = -1;
            } else {
                setSettingKey(activeRebindingKeyIndex, keyCode);
                activeRebindingKeyIndex = -1;
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_F1) {
            boolean visible = !showLeftSidebar || !showRightSidebar;
            showLeftSidebar = visible;
            showRightSidebar = visible;
            rebuildSettings();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_F8) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.currentScreen != null) {
                mc.currentScreen.close();
            }
            return true;
        }
        if (showSettingsPopup) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                showSettingsPopup = false;
                activeRebindingKeyIndex = -1;
                return true;
            }
            return true;
        }
        if (showLeftSidebar && searchFocused) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (!searchQuery.isEmpty()) {
                    searchQuery = searchQuery.substring(0, searchQuery.length() - 1);
                }
                return true;
            } else if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_ESCAPE) {
                searchFocused = false;
                return true;
            }
        }
        if (showRightSidebar && activeSettingsPanel != null && activeSettingsPanel.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (showTimeline && timelinePanel != null && timelinePanel.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }

        if (ctrl && keyCode == GLFW.GLFW_KEY_C) {
            // Copy light
            LightInstance selected = LightGizmo.INSTANCE.getSelectedLight();
            if (selected != null) {
                copiedLight = new LightSaveManager.LightInstanceDto(selected);
                return true;
            }
        }
        if (ctrl && keyCode == GLFW.GLFW_KEY_V) {
            // Paste light
            if (copiedLight != null) {
                CALUndoManager.pushState();
                int newId = ThreadLocalRandom.current().nextInt(100000, 999999);
                float posX = copiedLight.x + 1f;
                float posZ = copiedLight.z + 1f;
                LightInstance newLight;
                if (copiedLight.isSpot) {
                    newLight = LightManager.INSTANCE.updateSpot(
                        newId, posX, copiedLight.y, posZ, copiedLight.dx, copiedLight.dy, copiedLight.dz,
                        copiedLight.r, copiedLight.g, copiedLight.b, copiedLight.intensity,
                        copiedLight.innerAngle, copiedLight.outerAngle, copiedLight.distance
                    );
                    newLight.rx = copiedLight.rx;
                    newLight.ry = copiedLight.ry;
                    newLight.rz = copiedLight.rz;
                } else {
                    newLight = LightManager.INSTANCE.updatePoint(
                        newId, posX, copiedLight.y, posZ, copiedLight.r, copiedLight.g, copiedLight.b,
                        copiedLight.intensity, copiedLight.radius
                    );
                }
                newLight.persistent = true;
                newLight.visible = copiedLight.visible;
                newLight.fogEnabled = copiedLight.fogEnabled;
                newLight.name = (copiedLight.name == null ? "" : copiedLight.name) + " (Copy)";
                newLight.beamStrength = copiedLight.fogDispersion;
                newLight.vlDensity = copiedLight.fogDensity;
                newLight.anisotropy = copiedLight.fogAnisotropy;
                newLight.shadowEnabled = copiedLight.shadowEnabled;
                newLight.bulbSize = copiedLight.shadowSoftness;
                newLight.entitiesOnly = copiedLight.entitiesOnly;
                newLight.blocksOnly = copiedLight.blocksOnly;
                newLight.goboName = copiedLight.goboName;
                newLight.goboRotation = copiedLight.goboRotation;
                newLight.cookieScale = copiedLight.cookieScale;
                newLight.cookieInvert = copiedLight.cookieInvert;

                if (copiedLight.animation != null) {
                    Gson gson = new Gson();
                    String animJson = gson.toJson(copiedLight.animation);
                    newLight.animation = gson.fromJson(animJson, LightAnimation.class);
                }

                LightGizmo.INSTANCE.setSelectedLight(newLight);
                rebuildSettings();
                return true;
            }
        }
        if (keyCode == GLFW.GLFW_KEY_DELETE) {
            // Delete light
            if (!searchFocused && CLUITrackpad.activeEditingTrackpad == null) {
                LightInstance selected = LightGizmo.INSTANCE.getSelectedLight();
                if (selected != null) {
                    CALUndoManager.pushState();
                    if (selected.isSpot) {
                        LightManager.INSTANCE.removeSpot(selected.id);
                    } else {
                        LightManager.INSTANCE.removePoint(selected.id);
                    }
                    LightGizmo.INSTANCE.setSelectedLight(null);
                    rebuildSettings();
                    return true;
                }
            }
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (showPatcherPopup) return true;
        if (showSettingsPopup) return true;
        if (showLeftSidebar && searchFocused) {
            if (chr >= 32 && chr < 127) {
                searchQuery += chr;
            }
            return true;
        }
        if (showRightSidebar && activeSettingsPanel != null && activeSettingsPanel.charTyped(chr, modifiers)) {
            return true;
        }
        if (showTimeline && timelinePanel != null && timelinePanel.charTyped(chr, modifiers)) {
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    public boolean isFullyClosed() {
        if (!closing) return false;
        if (!CalSettings.INSTANCE.simplifyAnimations) {
            return currentLeftPanelW <= 1f 
                && currentRightPanelW <= 1f 
                && currentTimelineH <= 1f
                && currentTopMenuY <= -topMenuH + 1f
                && currentStatusBarY >= h - 1f;
        }
        return true;
    }
}
