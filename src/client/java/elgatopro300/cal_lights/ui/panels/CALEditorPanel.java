package elgatopro300.cal_lights.ui.panels;

import elgatopro300.cal_lights.CALLightsClient;
import elgatopro300.cal_lights.animation.LightAnimation;
import elgatopro300.cal_lights.gizmo.LightGizmo;
import elgatopro300.cal_lights.graphics.CalLightsIcons;
import elgatopro300.cal_lights.light.LightConfig;
import elgatopro300.cal_lights.light.SettingsPresets;
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

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import com.mojang.blaze3d.platform.InputConstants;

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
    private final CLUITrackpad trackShadowSoftness;
    private final CLUITrackpad trackVlIntensity;
    private final CLUITrackpad trackVlSteps;
    private final CLUITrackpad trackVlMaxDist;
    private final CLUITrackpad trackVlTipBoost;
    private final CLUITrackpad trackVlTipRadius;
    private final CLUITrackpad trackVlNoiseAmount;
    private final CLUITrackpad trackVlNoiseScale;
    private final CLUITrackpad trackVlNoiseSpeed;
    private final CLUITrackpad trackVlNoiseMorph;
    
    private final CLUITrackpad trackOutlineStrength;
    private final CLUITrackpad trackOutlinePixelSize;
    private final CLUITrackpad trackOutlineFresnelPower;
    private final CLUITrackpad trackOutlineBack;
    private final CLUITrackpad trackOutlineFrontStrength;
    private final CLUITrackpad trackOutlineGlowStrength;
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
    private Vec3 rightClickWorldPos = null;
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
        this.trackShadowBlockRadius = new CLUITrackpad(CALKeys.SHADOW_BLOCK_RADIUS, LightConfig.shadowBlockRadius, 4f, 96f, val -> {
            LightConfig.shadowBlockRadius = Math.round(val);
        }).setArrowStep(1f);
        this.trackShadowSoftness = new CLUITrackpad(CALKeys.SHADOW_SOFTNESS, LightConfig.shadowSoftness, 0.0f, 0.8f, val -> {
            LightConfig.shadowSoftness = val;
        }).setArrowStep(0.01f);

        this.trackVlIntensity = new CLUITrackpad(CALKeys.VL_INTENSITY, LightConfig.vlIntensity, 0.0f, 5.0f, val -> {
            LightConfig.vlIntensity = val;
        }).setArrowStep(0.05f);
        this.trackVlSteps = new CLUITrackpad(CALKeys.VL_STEPS, LightConfig.vlSteps, 1f, 96f, val -> {
            LightConfig.vlSteps = Math.round(val);
        }).setArrowStep(1f);
        this.trackVlMaxDist = new CLUITrackpad(CALKeys.VL_MAX_DIST, LightConfig.vlMaxDist, 10f, 300f, val -> {
            LightConfig.vlMaxDist = val;
        }).setArrowStep(5f);
        this.trackVlTipBoost = new CLUITrackpad(CALKeys.VL_TIP_BOOST, LightConfig.vlTipBoost, 0.0f, 10.0f, val -> {
            LightConfig.vlTipBoost = val;
        }).setArrowStep(0.1f);
        this.trackVlTipRadius = new CLUITrackpad(CALKeys.VL_TIP_RADIUS, LightConfig.vlTipRadius, 0.0f, 10.0f, val -> {
            LightConfig.vlTipRadius = val;
        }).setArrowStep(0.1f);
        this.trackVlNoiseAmount = new CLUITrackpad(CALKeys.VL_NOISE_AMOUNT, LightConfig.vlNoiseAmount, 0.0f, 1.0f, val -> {
            LightConfig.vlNoiseAmount = val;
        }).setArrowStep(0.05f);
        this.trackVlNoiseScale = new CLUITrackpad(CALKeys.VL_NOISE_SCALE, LightConfig.vlNoiseScale, 0.1f, 10.0f, val -> {
            LightConfig.vlNoiseScale = val;
        }).setArrowStep(0.1f);
        this.trackVlNoiseSpeed = new CLUITrackpad(CALKeys.VL_NOISE_SPEED, LightConfig.vlNoiseSpeed, 0.0f, 5.0f, val -> {
            LightConfig.vlNoiseSpeed = val;
        }).setArrowStep(0.25f);
        this.trackVlNoiseMorph = new CLUITrackpad(CALKeys.VL_NOISE_MORPH, LightConfig.vlNoiseMorph, 0.0f, 3.0f, val -> {
            LightConfig.vlNoiseMorph = val;
        }).setArrowStep(0.25f);

        this.trackOutlineStrength = new CLUITrackpad(CALKeys.OUTLINE_STRENGTH, LightConfig.outlineStrength, 0.0f, 3.0f, val -> {
            LightConfig.outlineStrength = val;
        }).setArrowStep(0.05f);
        this.trackOutlinePixelSize = new CLUITrackpad(CALKeys.OUTLINE_PIXEL_SIZE, LightConfig.outlinePixelSize, 1f, 6f, val -> {
            LightConfig.outlinePixelSize = Math.round(val);
        }).setArrowStep(1f);
        this.trackOutlineFresnelPower = new CLUITrackpad(CALKeys.OUTLINE_FRESNEL_POWER, LightConfig.outlineFresnelPower, 0.1f, 10.0f, val -> {
            LightConfig.outlineFresnelPower = val;
        }).setArrowStep(0.1f);
        this.trackOutlineBack = new CLUITrackpad(CALKeys.OUTLINE_BACK, LightConfig.outlineBack, 0.0f, 2.0f, val -> {
            LightConfig.outlineBack = val;
        }).setArrowStep(0.05f);
        this.trackOutlineFrontStrength = new CLUITrackpad(CALKeys.OUTLINE_FRONT_STRENGTH, LightConfig.outlineFrontStrength, 0.0f, 1.5f, val -> {
            LightConfig.outlineFrontStrength = val;
        }).setArrowStep(0.05f);
        this.trackOutlineGlowStrength = new CLUITrackpad(CALKeys.OUTLINE_GLOW_STRENGTH, LightConfig.outlineGlowStrength, 0.0f, 1.0f, val -> {
            LightConfig.outlineGlowStrength = val;
        }).setArrowStep(0.05f);

        this.trackAutoLightIntensity = new CLUITrackpad(CALKeys.AUTO_LIGHT_INTENSITY, LightConfig.autoLightIntensity, 0.0f, 5.0f, val -> {
            LightConfig.autoLightIntensity = val;
        }).setArrowStep(0.05f);
        this.trackAutoLightReach = new CLUITrackpad(CALKeys.AUTO_LIGHT_REACH, LightConfig.autoLightReach, 0.25f, 3.0f, val -> {
            LightConfig.autoLightReach = val;
        }).setArrowStep(0.05f);
        this.trackAutoLightRadius = new CLUITrackpad(CALKeys.AUTO_LIGHT_RADIUS, LightConfig.autoLightRadius, 8f, 96f, val -> {
            LightConfig.autoLightRadius = Math.round(val);
        }).setArrowStep(1f);
        this.trackAutoLightMax = new CLUITrackpad(CALKeys.AUTO_LIGHT_MAX, LightConfig.autoLightMax, 0f, 2000f, val -> {
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

        // Gizmo Mode Switcher Buttons (Move, Rotate, Combined)
        int modeStartX = animLeftPanelW + 235;
        LightGizmo.Mode currentMode = LightGizmo.INSTANCE.getMode();
        LightGizmo.Mode[] modes = { LightGizmo.Mode.TRANSLATE, LightGizmo.Mode.ROTATE, LightGizmo.Mode.COMBINED };
        IKey[] modeLabels = { CALKeys.GIZMO_MODE_MOVE, CALKeys.GIZMO_MODE_ROTATE, CALKeys.GIZMO_MODE_COMBINED };
        int modeBtnW = 60;

        for (int i = 0; i < modes.length; i++) {
            int btnX = modeStartX + i * (modeBtnW + 4);
            boolean isSelected = currentMode == modes[i];
            boolean hoverMode = ctx.mouseX >= btnX && ctx.mouseX < btnX + modeBtnW && ctx.mouseY >= topY + topMenuH + 2 && ctx.mouseY < topY + topMenuH + 18;
            int bg = isSelected ? 0xFF1565C0 : (hoverMode ? 0xFF3A3A4A : 0xFF1E1E24);
            int border = isSelected ? 0xFF64B5F6 : (hoverMode ? 0xFFFFAA00 : 0xFF3E3E4D);

            ctx.batcher.box(btnX, topY + topMenuH + 2, btnX + modeBtnW, topY + topMenuH + 18, bg);
            ctx.batcher.outline(btnX, topY + topMenuH + 2, btnX + modeBtnW, topY + topMenuH + 18, border, 1);
            String labelStr = modeLabels[i].get();
            int lblW = MinecraftClient.getInstance().textRenderer.getWidth(labelStr);
            ctx.batcher.text(labelStr, btnX + (modeBtnW - lblW) / 2, topY + topMenuH + 5, isSelected ? 0xFFFFFFFF : (hoverMode ? 0xFFFFAA00 : 0xFFCCCCCC));
        }

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
            int pointTextX = 8 + (btnW - Minecraft.getInstance().font.width(CALKeys.ADD_POINT_SHORT.get())) / 2;
            ctx.batcher.text(CALKeys.ADD_POINT_SHORT.get(), pointTextX, addY + 4, 0xFFFFFFFF);

            boolean hoverS = ctx.mouseX >= 8 + btnW + 6 && ctx.mouseX < animLeftPanelW - 8 && ctx.mouseY >= addY && ctx.mouseY < addY + 16;
            ctx.batcher.box(8 + btnW + 6, addY, animLeftPanelW - 8, addY + 16, hoverS ? 0xFF1565C0 : 0xFF0D47A1);
            ctx.batcher.outline(8 + btnW + 6, addY, animLeftPanelW - 8, addY + 16, 0xFF64B5F6, 1);
            int spotTextX = 8 + btnW + 6 + (btnW - Minecraft.getInstance().font.width(CALKeys.ADD_SPOT_SHORT.get())) / 2;
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
            ctx.batcher.getCtx().pose().pushMatrix();
            ctx.batcher.getCtx().pose().translate(w / 2.0f, h / 2.0f);
            ctx.batcher.getCtx().pose().scale(settingsPopupScale, settingsPopupScale);
            ctx.batcher.getCtx().pose().translate(-w / 2.0f, -h / 2.0f);

            // Modal Box Dimensions (Premium BBS size: 590x320)
            int pW = 590;
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

            // Left category list panel (145 width)
            int sideW = 145;
            ctx.batcher.box(pX, pY + headerH, pX + sideW, pY + pH, 0xFF111115);
            ctx.batcher.outline(pX, pY + headerH, pX + sideW, pY + pH, 0xFF22222A, 1);

            // Render 7 categories dynamically
            int categoryStartY = pY + headerH + 6;
            int categoryRowH = 22;
            String[] categoryNames = {
                CALKeys.GENERAL.get(),
                CALKeys.INTERFACE.get(),
                CALKeys.KEYBINDS.get(),
                CALKeys.PRESETS.get(),
                CALKeys.SHADOWS.get(),
                CALKeys.VOLUMETRICS.get(),
                CALKeys.OUTLINE.get(),
                CALKeys.AUTO_LIGHTS.get()
            };
            for (int i = 0; i < categoryNames.length; i++) {
                boolean hover = ctx.mouseX >= pX + 2 && ctx.mouseX < pX + sideW - 2 && ctx.mouseY >= categoryStartY + i * categoryRowH && ctx.mouseY < categoryStartY + i * categoryRowH + 18;
                int bg = (selectedSettingsCategory == i) ? 0xFF1D1D26 : (hover ? 0xFF181820 : 0xFF111115);
                ctx.batcher.box(pX + 2, categoryStartY + i * categoryRowH, pX + sideW - 2, categoryStartY + i * categoryRowH + 18, bg);
                ctx.batcher.text(categoryNames[i], pX + 10, categoryStartY + i * categoryRowH + 5, (selectedSettingsCategory == i) ? 0xFFFFAA00 : 0xFFCCCCCC);
                if (selectedSettingsCategory == i) {
                    ctx.batcher.box(pX + 2, categoryStartY + i * categoryRowH, pX + 5, categoryStartY + i * categoryRowH + 18, 0xFF1976D2);
                }
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
                int ticksTextW = Minecraft.getInstance().font.width(CALKeys.TICKS.get());
                ctx.batcher.text(CALKeys.TICKS.get(), ticksBtnX + (btnW2 - ticksTextW) / 2, btnY2 + 5, 0xFFFFFFFF);

                boolean hoverSecs = ctx.mouseX >= secsBtnX && ctx.mouseX < secsBtnX + btnW2 && ctx.mouseY >= btnY2 && ctx.mouseY < btnY2 + btnH2;
                boolean isSecs = CalSettings.INSTANCE.durationMode.equals("seconds");
                ctx.batcher.box(secsBtnX, btnY2, secsBtnX + btnW2, btnY2 + btnH2, isSecs ? 0xFF1976D2 : (hoverSecs ? 0xFF2A2A35 : 0xFF1E1E24));
                ctx.batcher.outline(secsBtnX, btnY2, secsBtnX + btnW2, btnY2 + btnH2, isSecs ? 0xFF64B5F6 : 0xFF3E3E4D, 1);
                int secsTextW = Minecraft.getInstance().font.width(CALKeys.SECONDS.get());
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
                int yesTextW = Minecraft.getInstance().font.width(CALKeys.YES.get());
                ctx.batcher.text(CALKeys.YES.get(), yesBtnX + (btnW2 - yesTextW) / 2, animBtnY + 5, 0xFFFFFFFF);

                boolean hoverNo = ctx.mouseX >= noBtnX && ctx.mouseX < noBtnX + btnW2 && ctx.mouseY >= animBtnY && ctx.mouseY < animBtnY + btnH2;
                boolean isNo = !CalSettings.INSTANCE.simplifyAnimations;
                ctx.batcher.box(noBtnX, animBtnY, noBtnX + btnW2, animBtnY + btnH2, isNo ? 0xFF1976D2 : (hoverNo ? 0xFF2A2A35 : 0xFF1E1E24));
                ctx.batcher.outline(noBtnX, animBtnY, noBtnX + btnW2, animBtnY + btnH2, isNo ? 0xFF64B5F6 : 0xFF3E3E4D, 1);
                int noTextW = Minecraft.getInstance().font.width(CALKeys.NO.get());
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
                            case 10 -> CALLightsClient.createLightKeyBinding != null ? CALLightsClient.createLightKeyBinding.getTranslatedKeyMessage().getString() : "F7";
                            case 11 -> CALLightsClient.editorKeyBinding != null ? CALLightsClient.editorKeyBinding.getTranslatedKeyMessage().getString() : "F8";
                            default -> "";
                        };
                    }
                    
                    int keyTextW = Minecraft.getInstance().font.width(keyLabel);
                    ctx.batcher.text(keyLabel, btnX + (btnW3 - keyTextW) / 2, rowY + 3, isRebindable ? 0xFFFFFFFF : 0xFF888899);
                }
            } else if (selectedSettingsCategory == 3) {
                // PRESETS SETTINGS
                int startY = pY + headerH + 10;
                int curY = startY;
                int fullW = 400;

                // Quality Presets
                ctx.batcher.text(CALKeys.PRESETS_QUALITY_TITLE.get(), contentX, curY, 0xFFCCCCCC);
                curY += 12;
                String[] qLabels = SettingsPresets.QUALITY_LABELS;
                int qActive = SettingsPresets.quality();
                int qSegW = (fullW - 12) / 5;
                for (int i = 0; i < qLabels.length; i++) {
                    int btnX = contentX + i * (qSegW + 3);
                    boolean hoverQ = ctx.mouseX >= btnX && ctx.mouseX < btnX + qSegW && ctx.mouseY >= curY && ctx.mouseY < curY + 16;
                    boolean isCurrentQ = (qActive == i);
                    int qBg = isCurrentQ ? 0xFF1976D2 : (hoverQ ? 0xFF2A2A35 : 0xFF1E1E24);
                    ctx.batcher.box(btnX, curY, btnX + qSegW, curY + 16, qBg);
                    ctx.batcher.outline(btnX, curY, btnX + qSegW, curY + 16, isCurrentQ ? 0xFF64B5F6 : 0xFF3E3E4D, 1);
                    int textW = MinecraftClient.getInstance().textRenderer.getWidth(qLabels[i]);
                    ctx.batcher.text(qLabels[i], btnX + (qSegW - textW) / 2, curY + 4, isCurrentQ ? 0xFFFFFFFF : 0xFFBBBBBB);
                }
                curY += 32;

                // Style Presets
                ctx.batcher.text(CALKeys.PRESETS_STYLE_TITLE.get(), contentX, curY, 0xFFCCCCCC);
                curY += 12;
                String[] sLabels = SettingsPresets.STYLE_LABELS;
                int sActive = SettingsPresets.style();
                int sSegW = (fullW - 9) / 4;
                for (int i = 0; i < sLabels.length; i++) {
                    int btnX = contentX + i * (sSegW + 3);
                    boolean hoverS = ctx.mouseX >= btnX && ctx.mouseX < btnX + sSegW && ctx.mouseY >= curY && ctx.mouseY < curY + 16;
                    boolean isCurrentS = (sActive == i);
                    int sBg = isCurrentS ? 0xFF1976D2 : (hoverS ? 0xFF2A2A35 : 0xFF1E1E24);
                    ctx.batcher.box(btnX, curY, btnX + sSegW, curY + 16, sBg);
                    ctx.batcher.outline(btnX, curY, btnX + sSegW, curY + 16, isCurrentS ? 0xFF64B5F6 : 0xFF3E3E4D, 1);
                    int textW = MinecraftClient.getInstance().textRenderer.getWidth(sLabels[i]);
                    ctx.batcher.text(sLabels[i], btnX + (sSegW - textW) / 2, curY + 4, isCurrentS ? 0xFFFFFFFF : 0xFFBBBBBB);
                }
                curY += 32;

                // Dev debug presets: hold bake toggle + show guides toggle
                ctx.batcher.text(CALKeys.PRESETS_DEV_TITLE.get(), contentX, curY, 0xFFCCCCCC);
                curY += 12;

                // Hold Bake (Pausar primer bakeado) Checkbox
                {
                    int boxSize = 10;
                    boolean hover = ctx.mouseX >= contentX && ctx.mouseX < contentX + 200 && ctx.mouseY >= curY && ctx.mouseY < curY + 12;
                    ctx.batcher.box(contentX, curY + 1, contentX + boxSize, curY + 1 + boxSize, hover ? 0xFF2A2A35 : 0xFF141418);
                    ctx.batcher.outline(contentX, curY + 1, contentX + boxSize, curY + 1 + boxSize, hover ? 0xFFFFAA00 : 0xFF3E3E4D, 1);
                    if (LightConfig.holdBake) {
                        ctx.batcher.box(contentX + 2, curY + 3, contentX + boxSize - 2, curY + boxSize - 1, 0xFFFFAA00);
                    }
                    ctx.batcher.text(CALKeys.HOLD_BAKE.get(), contentX + boxSize + 6, curY + 2, 0xFFE0E0E0);
                }
                curY += 20;

                // Hold Bake On Join Checkbox
                {
                    int boxSize = 10;
                    boolean hover = ctx.mouseX >= contentX && ctx.mouseX < contentX + 200 && ctx.mouseY >= curY && ctx.mouseY < curY + 12;
                    ctx.batcher.box(contentX, curY + 1, contentX + boxSize, curY + 1 + boxSize, hover ? 0xFF2A2A35 : 0xFF141418);
                    ctx.batcher.outline(contentX, curY + 1, contentX + boxSize, curY + 1 + boxSize, hover ? 0xFFFFAA00 : 0xFF3E3E4D, 1);
                    if (LightConfig.holdBakeOnJoin) {
                        ctx.batcher.box(contentX + 2, curY + 3, contentX + boxSize - 2, curY + boxSize - 1, 0xFFFFAA00);
                    }
                    ctx.batcher.text(CALKeys.HOLD_BAKE_ON_JOIN.get(), contentX + boxSize + 6, curY + 2, 0xFFE0E0E0);
                }

            } else if (selectedSettingsCategory == 4) {
                // SOMBRAS SETTINGS
                int colW = 200;
                int startY = pY + headerH + 10;
                int itemSpacing = 20;
                int curY = startY;

                // Calidad de sombras
                ctx.batcher.text(CALKeys.SHADOW_QUALITY.get(), contentX, curY, 0xFFCCCCCC);
                curY += 12;
                String[] qLabels = {"LOW", "MED", "HIGH", "ULTRA"};
                int segW = (colW - 9) / 4;
                for (int i = 0; i < qLabels.length; i++) {
                    int btnX = contentX + i * (segW + 3);
                    boolean hoverQ = ctx.mouseX >= btnX && ctx.mouseX < btnX + segW && ctx.mouseY >= curY && ctx.mouseY < curY + 14;
                    boolean isCurrentQ = (LightConfig.shadowQuality == i);
                    int qBg = isCurrentQ ? 0xFF1976D2 : (hoverQ ? 0xFF2A2A35 : 0xFF1E1E24);
                    ctx.batcher.box(btnX, curY, btnX + segW, curY + 14, qBg);
                    ctx.batcher.outline(btnX, curY, btnX + segW, curY + 14, isCurrentQ ? 0xFF64B5F6 : 0xFF3E3E4D, 1);
                    int textW = Minecraft.getInstance().font.width(qLabels[i]);
                    ctx.batcher.text(qLabels[i], btnX + (segW - textW) / 2, curY + 3, 0xFFFFFFFF);
                }
                curY += 20;

                // Shadows Live Checkbox
                {
                    int boxSize = 10;
                    boolean hover = ctx.mouseX >= contentX && ctx.mouseX < contentX + colW && ctx.mouseY >= curY && ctx.mouseY < curY + 12;
                    ctx.batcher.box(contentX, curY + 1, contentX + boxSize, curY + 1 + boxSize, hover ? 0xFF2A2A35 : 0xFF141418);
                    ctx.batcher.outline(contentX, curY + 1, contentX + boxSize, curY + 1 + boxSize, hover ? 0xFFFFAA00 : 0xFF3E3E4D, 1);
                    if (LightConfig.shadowsLive) {
                        ctx.batcher.box(contentX + 2, curY + 3, contentX + boxSize - 2, curY + boxSize - 1, 0xFFFFAA00);
                    }
                    ctx.batcher.text(CALKeys.SHADOWS_LIVE.get(), contentX + boxSize + 6, curY + 2, 0xFFE0E0E0);
                }
                curY += itemSpacing;

                // Shadow Cache Checkbox
                {
                    int boxSize = 10;
                    boolean hover = ctx.mouseX >= contentX && ctx.mouseX < contentX + colW && ctx.mouseY >= curY && ctx.mouseY < curY + 12;
                    ctx.batcher.box(contentX, curY + 1, contentX + boxSize, curY + 1 + boxSize, hover ? 0xFF2A2A35 : 0xFF141418);
                    ctx.batcher.outline(contentX, curY + 1, contentX + boxSize, curY + 1 + boxSize, hover ? 0xFFFFAA00 : 0xFF3E3E4D, 1);
                    if (LightConfig.shadowCache) {
                        ctx.batcher.box(contentX + 2, curY + 3, contentX + boxSize - 2, curY + boxSize - 1, 0xFFFFAA00);
                    }
                    ctx.batcher.text(CALKeys.SHADOW_CACHE.get(), contentX + boxSize + 6, curY + 2, 0xFFE0E0E0);
                }
                curY += itemSpacing;

                // Shadow Blocks Checkbox
                {
                    int boxSize = 10;
                    boolean hover = ctx.mouseX >= contentX && ctx.mouseX < contentX + colW && ctx.mouseY >= curY && ctx.mouseY < curY + 12;
                    ctx.batcher.box(contentX, curY + 1, contentX + boxSize, curY + 1 + boxSize, hover ? 0xFF2A2A35 : 0xFF141418);
                    ctx.batcher.outline(contentX, curY + 1, contentX + boxSize, curY + 1 + boxSize, hover ? 0xFFFFAA00 : 0xFF3E3E4D, 1);
                    if (LightConfig.shadowBlocks) {
                        ctx.batcher.box(contentX + 2, curY + 3, contentX + boxSize - 2, curY + boxSize - 1, 0xFFFFAA00);
                    }
                    ctx.batcher.text(CALKeys.SHADOW_BLOCKS.get(), contentX + boxSize + 6, curY + 2, 0xFFE0E0E0);
                }
                curY += itemSpacing;

                // Hold Bake Checkbox
                {
                    int boxSize = 10;
                    boolean hover = ctx.mouseX >= contentX && ctx.mouseX < contentX + colW && ctx.mouseY >= curY && ctx.mouseY < curY + 12;
                    ctx.batcher.box(contentX, curY + 1, contentX + boxSize, curY + 1 + boxSize, hover ? 0xFF2A2A35 : 0xFF141418);
                    ctx.batcher.outline(contentX, curY + 1, contentX + boxSize, curY + 1 + boxSize, hover ? 0xFFFFAA00 : 0xFF3E3E4D, 1);
                    if (LightConfig.holdBake) {
                        ctx.batcher.box(contentX + 2, curY + 3, contentX + boxSize - 2, curY + boxSize - 1, 0xFFFFAA00);
                    }
                    ctx.batcher.text(CALKeys.HOLD_BAKE.get(), contentX + boxSize + 6, curY + 2, 0xFFE0E0E0);
                }
                curY += itemSpacing;

                // Right column for trackpads
                int rightColX = contentX + 215;
                curY = startY;

                trackShadowBlockRadius.resize(rightColX, curY, colW, 14);
                trackShadowBlockRadius.setValue(LightConfig.shadowBlockRadius);
                trackShadowBlockRadius.render(ctx);
                curY += itemSpacing;

                trackShadowSoftness.resize(rightColX, curY, colW, 14);
                trackShadowSoftness.setValue(LightConfig.shadowSoftness);
                trackShadowSoftness.render(ctx);
                curY += itemSpacing;

            } else if (selectedSettingsCategory == 5) {
                // VOLUMETRIC SETTINGS
                int colW = 200;
                int startY = pY + headerH + 10;
                int itemSpacing = 20;
                int curY = startY;

                // Column 1 (Left) - Volumetric controls
                trackVlIntensity.resize(contentX, curY, colW, 14);
                trackVlIntensity.setValue(LightConfig.vlIntensity);
                trackVlIntensity.render(ctx);
                curY += itemSpacing;

                trackVlSteps.resize(contentX, curY, colW, 14);
                trackVlSteps.setValue(LightConfig.vlSteps);
                trackVlSteps.render(ctx);
                curY += itemSpacing;

                trackVlMaxDist.resize(contentX, curY, colW, 14);
                trackVlMaxDist.setValue(LightConfig.vlMaxDist);
                trackVlMaxDist.render(ctx);
                curY += itemSpacing;

                trackVlTipBoost.resize(contentX, curY, colW, 14);
                trackVlTipBoost.setValue(LightConfig.vlTipBoost);
                trackVlTipBoost.render(ctx);
                curY += itemSpacing;

                trackVlTipRadius.resize(contentX, curY, colW, 14);
                trackVlTipRadius.setValue(LightConfig.vlTipRadius);
                trackVlTipRadius.render(ctx);
                curY += itemSpacing;

                // Column 2 (Right) - Volumetric Noise & Toggles
                int rightColX = contentX + 215;
                curY = startY;

                trackVlNoiseAmount.resize(rightColX, curY, colW, 14);
                trackVlNoiseAmount.setValue(LightConfig.vlNoiseAmount);
                trackVlNoiseAmount.render(ctx);
                curY += itemSpacing;

                trackVlNoiseScale.resize(rightColX, curY, colW, 14);
                trackVlNoiseScale.setValue(LightConfig.vlNoiseScale);
                trackVlNoiseScale.render(ctx);
                curY += itemSpacing;

                trackVlNoiseSpeed.resize(rightColX, curY, colW, 14);
                trackVlNoiseSpeed.setValue(LightConfig.vlNoiseSpeed);
                trackVlNoiseSpeed.render(ctx);
                curY += itemSpacing;

                trackVlNoiseMorph.resize(rightColX, curY, colW, 14);
                trackVlNoiseMorph.setValue(LightConfig.vlNoiseMorph);
                trackVlNoiseMorph.render(ctx);
                curY += itemSpacing;

                // Toggles for Volumetrics
                int togglesY = curY;
                {
                    // VL Shadows
                    int boxSize = 10;
                    boolean hover = ctx.mouseX >= rightColX && ctx.mouseX < rightColX + 90 && ctx.mouseY >= togglesY && ctx.mouseY < togglesY + 12;
                    ctx.batcher.box(rightColX, togglesY + 1, rightColX + boxSize, togglesY + 1 + boxSize, hover ? 0xFF2A2A35 : 0xFF141418);
                    ctx.batcher.outline(rightColX, togglesY + 1, rightColX + boxSize, togglesY + 1 + boxSize, hover ? 0xFFFFAA00 : 0xFF3E3E4D, 1);
                    if (LightConfig.vlShadows) {
                        ctx.batcher.box(rightColX + 2, togglesY + 3, rightColX + boxSize - 2, togglesY + boxSize - 1, 0xFFFFAA00);
                    }
                    ctx.batcher.text(CALKeys.VL_SHADOWS_TOGGLE.get(), rightColX + boxSize + 6, togglesY + 2, 0xFFE0E0E0);
                }
                {
                    // VL Noise
                    int boxSize = 10;
                    int xOff = 100;
                    boolean hover = ctx.mouseX >= rightColX + xOff && ctx.mouseX < rightColX + xOff + 90 && ctx.mouseY >= togglesY && ctx.mouseY < togglesY + 12;
                    ctx.batcher.box(rightColX + xOff, togglesY + 1, rightColX + xOff + boxSize, togglesY + 1 + boxSize, hover ? 0xFF2A2A35 : 0xFF141418);
                    ctx.batcher.outline(rightColX + xOff, togglesY + 1, rightColX + xOff + boxSize, togglesY + 1 + boxSize, hover ? 0xFFFFAA00 : 0xFF3E3E4D, 1);
                    if (LightConfig.vlNoise) {
                        ctx.batcher.box(rightColX + xOff + 2, togglesY + 3, rightColX + xOff + boxSize - 2, togglesY + boxSize - 1, 0xFFFFAA00);
                    }
                    ctx.batcher.text(CALKeys.VL_NOISE_TOGGLE.get(), rightColX + xOff + boxSize + 6, togglesY + 2, 0xFFE0E0E0);
                }
                togglesY += 15;
                {
                    // Blue Noise
                    int boxSize = 10;
                    boolean hover = ctx.mouseX >= rightColX && ctx.mouseX < rightColX + 90 && ctx.mouseY >= togglesY && ctx.mouseY < togglesY + 12;
                    ctx.batcher.box(rightColX, togglesY + 1, rightColX + boxSize, togglesY + 1 + boxSize, hover ? 0xFF2A2A35 : 0xFF141418);
                    ctx.batcher.outline(rightColX, togglesY + 1, rightColX + boxSize, togglesY + 1 + boxSize, hover ? 0xFFFFAA00 : 0xFF3E3E4D, 1);
                    if (LightConfig.vlBlueNoise) {
                        ctx.batcher.box(rightColX + 2, togglesY + 3, rightColX + boxSize - 2, togglesY + boxSize - 1, 0xFFFFAA00);
                    }
                    ctx.batcher.text(CALKeys.VL_BLUE_NOISE.get(), rightColX + boxSize + 6, togglesY + 2, 0xFFE0E0E0);
                }
                {
                    // Temporal Dither
                    int boxSize = 10;
                    int xOff = 100;
                    boolean hover = ctx.mouseX >= rightColX + xOff && ctx.mouseX < rightColX + xOff + 90 && ctx.mouseY >= togglesY && ctx.mouseY < togglesY + 12;
                    ctx.batcher.box(rightColX + xOff, togglesY + 1, rightColX + xOff + boxSize, togglesY + 1 + boxSize, hover ? 0xFF2A2A35 : 0xFF141418);
                    ctx.batcher.outline(rightColX + xOff, togglesY + 1, rightColX + xOff + boxSize, togglesY + 1 + boxSize, hover ? 0xFFFFAA00 : 0xFF3E3E4D, 1);
                    if (LightConfig.vlDitherTemporal) {
                        ctx.batcher.box(rightColX + xOff + 2, togglesY + 3, rightColX + xOff + boxSize - 2, togglesY + boxSize - 1, 0xFFFFAA00);
                    }
                    ctx.batcher.text(CALKeys.VL_DITHER_TEMPORAL.get(), rightColX + xOff + boxSize + 6, togglesY + 2, 0xFFE0E0E0);
                }
                togglesY += 15;
                {
                    // Cluster Cull
                    int boxSize = 10;
                    boolean hover = ctx.mouseX >= rightColX && ctx.mouseX < rightColX + 90 && ctx.mouseY >= togglesY && ctx.mouseY < togglesY + 12;
                    ctx.batcher.box(rightColX, togglesY + 1, rightColX + boxSize, togglesY + 1 + boxSize, hover ? 0xFF2A2A35 : 0xFF141418);
                    ctx.batcher.outline(rightColX, togglesY + 1, rightColX + boxSize, togglesY + 1 + boxSize, hover ? 0xFFFFAA00 : 0xFF3E3E4D, 1);
                    if (LightConfig.vlClusterCull) {
                        ctx.batcher.box(rightColX + 2, togglesY + 3, rightColX + boxSize - 2, togglesY + boxSize - 1, 0xFFFFAA00);
                    }
                    ctx.batcher.text(CALKeys.VL_CLUSTER_CULL.get(), rightColX + boxSize + 6, togglesY + 2, 0xFFE0E0E0);
                }
                {
                    // Shadow Hi-Z
                    int boxSize = 10;
                    int xOff = 100;
                    boolean hover = ctx.mouseX >= rightColX + xOff && ctx.mouseX < rightColX + xOff + 90 && ctx.mouseY >= togglesY && ctx.mouseY < togglesY + 12;
                    ctx.batcher.box(rightColX + xOff, togglesY + 1, rightColX + xOff + boxSize, togglesY + 1 + boxSize, hover ? 0xFF2A2A35 : 0xFF141418);
                    ctx.batcher.outline(rightColX + xOff, togglesY + 1, rightColX + xOff + boxSize, togglesY + 1 + boxSize, hover ? 0xFFFFAA00 : 0xFF3E3E4D, 1);
                    if (LightConfig.vlShadowHiz) {
                        ctx.batcher.box(rightColX + xOff + 2, togglesY + 3, rightColX + xOff + boxSize - 2, togglesY + boxSize - 1, 0xFFFFAA00);
                    }
                    ctx.batcher.text(CALKeys.VL_HIZ_CULL.get(), rightColX + xOff + boxSize + 6, togglesY + 2, 0xFFE0E0E0);
                }

            } else if (selectedSettingsCategory == 6) {
                // OUTLINE SETTINGS
                int colW = 200;
                int startY = pY + headerH + 10;
                int itemSpacing = 20;
                int curY = startY;

                // Column 1 (Left) - Outline toggles & parameters
                {
                    int boxSize = 10;
                    boolean hover = ctx.mouseX >= contentX && ctx.mouseX < contentX + colW && ctx.mouseY >= curY && ctx.mouseY < curY + 12;
                    ctx.batcher.box(contentX, curY + 1, contentX + boxSize, curY + 1 + boxSize, hover ? 0xFF2A2A35 : 0xFF141418);
                    ctx.batcher.outline(contentX, curY + 1, contentX + boxSize, curY + 1 + boxSize, hover ? 0xFFFFAA00 : 0xFF3E3E4D, 1);
                    if (LightConfig.outline) {
                        ctx.batcher.box(contentX + 2, curY + 3, contentX + boxSize - 2, curY + boxSize - 1, 0xFFFFAA00);
                    }
                    ctx.batcher.text(CALKeys.OUTLINE_ENABLED.get(), contentX + boxSize + 6, curY + 2, 0xFFE0E0E0);
                }
                curY += itemSpacing;

                // Target segmented: ALL, ENTITIES, BLOCKS
                ctx.batcher.text(CALKeys.OUTLINE_TARGET.get(), contentX, curY, 0xFFCCCCCC);
                curY += 12;
                IKey[] tLabels = { CALKeys.TARGET_ALL, CALKeys.TARGET_ENTITIES, CALKeys.TARGET_BLOCKS };
                int segW = (colW - 6) / 3;
                for (int i = 0; i < tLabels.length; i++) {
                    int btnX = contentX + i * (segW + 3);
                    boolean hoverT = ctx.mouseX >= btnX && ctx.mouseX < btnX + segW && ctx.mouseY >= curY && ctx.mouseY < curY + 14;
                    boolean isCurrentT = (LightConfig.outlineTarget == i);
                    int tBg = isCurrentT ? 0xFF1976D2 : (hoverT ? 0xFF2A2A35 : 0xFF1E1E24);
                    ctx.batcher.box(btnX, curY, btnX + segW, curY + 14, tBg);
                    ctx.batcher.outline(btnX, curY, btnX + segW, curY + 14, isCurrentT ? 0xFF64B5F6 : 0xFF3E3E4D, 1);
                    String tStr = tLabels[i].get();
                    int textW = MinecraftClient.getInstance().textRenderer.getWidth(tStr);
                    ctx.batcher.text(tStr, btnX + (segW - textW) / 2, curY + 3, 0xFFFFFFFF);
                }
                curY += 20;

                // Outline Front Checkbox
                {
                    int boxSize = 10;
                    boolean hover = ctx.mouseX >= contentX && ctx.mouseX < contentX + colW && ctx.mouseY >= curY && ctx.mouseY < curY + 12;
                    ctx.batcher.box(contentX, curY + 1, contentX + boxSize, curY + 1 + boxSize, hover ? 0xFF2A2A35 : 0xFF141418);
                    ctx.batcher.outline(contentX, curY + 1, contentX + boxSize, curY + 1 + boxSize, hover ? 0xFFFFAA00 : 0xFF3E3E4D, 1);
                    if (LightConfig.outlineFront) {
                        ctx.batcher.box(contentX + 2, curY + 3, contentX + boxSize - 2, curY + boxSize - 1, 0xFFFFAA00);
                    }
                    ctx.batcher.text(CALKeys.OUTLINE_FRONT_TOGGLE.get(), contentX + boxSize + 6, curY + 2, 0xFFE0E0E0);
                }
                curY += itemSpacing;

                // Outline Glow Checkbox
                {
                    int boxSize = 10;
                    boolean hover = ctx.mouseX >= contentX && ctx.mouseX < contentX + colW && ctx.mouseY >= curY && ctx.mouseY < curY + 12;
                    ctx.batcher.box(contentX, curY + 1, contentX + boxSize, curY + 1 + boxSize, hover ? 0xFF2A2A35 : 0xFF141418);
                    ctx.batcher.outline(contentX, curY + 1, contentX + boxSize, curY + 1 + boxSize, hover ? 0xFFFFAA00 : 0xFF3E3E4D, 1);
                    if (LightConfig.outlineGlow) {
                        ctx.batcher.box(contentX + 2, curY + 3, contentX + boxSize - 2, curY + boxSize - 1, 0xFFFFAA00);
                    }
                    ctx.batcher.text(CALKeys.OUTLINE_GLOW_TOGGLE.get(), contentX + boxSize + 6, curY + 2, 0xFFE0E0E0);
                }
                curY += itemSpacing;

                // Column 2 (Right) - Outline trackpads
                int rightColX = contentX + 215;
                curY = startY;

                trackOutlineStrength.resize(rightColX, curY, colW, 14);
                trackOutlineStrength.setValue(LightConfig.outlineStrength);
                trackOutlineStrength.render(ctx);
                curY += itemSpacing;

                trackOutlinePixelSize.resize(rightColX, curY, colW, 14);
                trackOutlinePixelSize.setValue(LightConfig.outlinePixelSize);
                trackOutlinePixelSize.render(ctx);
                curY += itemSpacing;

                trackOutlineFresnelPower.resize(rightColX, curY, colW, 14);
                trackOutlineFresnelPower.setValue(LightConfig.outlineFresnelPower);
                trackOutlineFresnelPower.render(ctx);
                curY += itemSpacing;

                trackOutlineBack.resize(rightColX, curY, colW, 14);
                trackOutlineBack.setValue(LightConfig.outlineBack);
                trackOutlineBack.render(ctx);
                curY += itemSpacing;

                trackOutlineFrontStrength.resize(rightColX, curY, colW, 14);
                trackOutlineFrontStrength.setValue(LightConfig.outlineFrontStrength);
                trackOutlineFrontStrength.render(ctx);
                curY += itemSpacing;

                trackOutlineGlowStrength.resize(rightColX, curY, colW, 14);
                trackOutlineGlowStrength.setValue(LightConfig.outlineGlowStrength);
                trackOutlineGlowStrength.render(ctx);
                curY += itemSpacing;

            } else if (selectedSettingsCategory == 7) {
                // AUTO LIGHTS SETTINGS
                int colW = 200;
                int startY = pY + headerH + 10;
                int itemSpacing = 20;
                int curY = startY;

                // Column 1 (Left) - Toggles
                // Auto Lights Enable Checkbox
                {
                    int boxSize = 10;
                    boolean hover = ctx.mouseX >= contentX && ctx.mouseX < contentX + colW && ctx.mouseY >= curY && ctx.mouseY < curY + 12;
                    ctx.batcher.box(contentX, curY + 1, contentX + boxSize, curY + 1 + boxSize, hover ? 0xFF2A2A35 : 0xFF141418);
                    ctx.batcher.outline(contentX, curY + 1, contentX + boxSize, curY + 1 + boxSize, hover ? 0xFFFFAA00 : 0xFF3E3E4D, 1);
                    if (LightConfig.autoLights) {
                        ctx.batcher.box(contentX + 2, curY + 3, contentX + boxSize - 2, curY + boxSize - 1, 0xFFFFAA00);
                    }
                    ctx.batcher.text(CALKeys.AUTO_LIGHTS.get(), contentX + boxSize + 6, curY + 2, 0xFFE0E0E0);
                }
                curY += itemSpacing;
                boolean autoLightsOn = LightConfig.autoLights;

                // Auto Lights Culling Checkbox
                if (autoLightsOn) {
                    int boxSize = 10;
                    boolean hover = ctx.mouseX >= contentX && ctx.mouseX < contentX + colW && ctx.mouseY >= curY && ctx.mouseY < curY + 12;
                    ctx.batcher.box(contentX, curY + 1, contentX + boxSize, curY + 1 + boxSize, hover ? 0xFF2A2A35 : 0xFF141418);
                    ctx.batcher.outline(contentX, curY + 1, contentX + boxSize, curY + 1 + boxSize, hover ? 0xFFFFAA00 : 0xFF3E3E4D, 1);
                    if (LightConfig.autoLightCulling) {
                        ctx.batcher.box(contentX + 2, curY + 3, contentX + boxSize - 2, curY + boxSize - 1, 0xFFFFAA00);
                    }
                    ctx.batcher.text(CALKeys.AUTO_LIGHT_CULLING.get(), contentX + boxSize + 6, curY + 2, 0xFFE0E0E0);
                }
                curY += itemSpacing;

                // Auto Lights Shadows Checkbox
                if (autoLightsOn) {
                    int boxSize = 10;
                    boolean hover = ctx.mouseX >= contentX && ctx.mouseX < contentX + colW && ctx.mouseY >= curY && ctx.mouseY < curY + 12;
                    ctx.batcher.box(contentX, curY + 1, contentX + boxSize, curY + 1 + boxSize, hover ? 0xFF2A2A35 : 0xFF141418);
                    ctx.batcher.outline(contentX, curY + 1, contentX + boxSize, curY + 1 + boxSize, hover ? 0xFFFFAA00 : 0xFF3E3E4D, 1);
                    if (LightConfig.autoLightShadows) {
                        ctx.batcher.box(contentX + 2, curY + 3, contentX + boxSize - 2, curY + boxSize - 1, 0xFFFFAA00);
                    }
                    ctx.batcher.text(CALKeys.AUTO_LIGHT_SHADOWS.get(), contentX + boxSize + 6, curY + 2, 0xFFE0E0E0);
                }
                curY += itemSpacing;

                // Column 2 (Right) - Trackpads
                int rightColX = contentX + 215;
                curY = startY;

                if (autoLightsOn) {
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
                int activeCount = AutoLightManager.activeCount();
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

            ctx.batcher.getCtx().pose().popMatrix();
        }

        // --- RENDER PATCHER POPUP ---
        if (patcherPopupScale > 0.01f) {
            // Dark Backdrop Focus overlay with fading opacity
            int alpha = (int) (0x90 * patcherPopupScale);
            ctx.batcher.box(0, 0, w, h, (alpha << 24) | 0x0B0B0E);

            // Apply scaling matrix for the popup contents
            ctx.batcher.getCtx().pose().pushMatrix();
            ctx.batcher.getCtx().pose().translate(w / 2.0f, h / 2.0f);
            ctx.batcher.getCtx().pose().scale(patcherPopupScale, patcherPopupScale);
            ctx.batcher.getCtx().pose().translate(-w / 2.0f, -h / 2.0f);

            // Patcher Box Dimensions (BBS size: 450x300 or similar)
            int pW = 450;
            int pH = 300;
            int pX = (w - pW) / 2;
            int pY = (h - pH) / 2;

            if (patcherPanel != null) {
                patcherPanel.resize(pX, pY, pW, pH);
                patcherPanel.render(ctx);
            }

            ctx.batcher.getCtx().pose().popMatrix();
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
            InputConstants.Key inputKey = InputConstants.Type.KEYSYM.getOrCreate(key);
            String name = inputKey.getDisplayName().getString();
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
                            1f, 1f, 1f, 1.0f, 6.0f
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
                            0f, -1f, 0f, 1f, 1f, 1f, 1.0f, 35.0f, 10.0f, 12.0f
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
                Minecraft client = Minecraft.getInstance();
                if (client.level != null) {
                    Vec3 rayDir = LightGizmo.INSTANCE.getRayDirection(mx, my);
                    Camera camera = client.gameRenderer.getMainCamera();
                    Vec3 rayStart = camera.position();
                    
                    // Check if right-clicking directly on a light billboard
                    LightInstance clickedLight = LightGizmo.INSTANCE.checkBillboardClickExternal(rayStart, rayDir);
                    if (clickedLight != null) {
                        LightGizmo.INSTANCE.setSelectedLight(clickedLight);
                        rebuildSettings();
                        isRightClickingLight = true;
                        rightClickWorldPos = new Vec3(clickedLight.x, clickedLight.y, clickedLight.z);
                    } else {
                        isRightClickingLight = false;
                        Vec3 rayEnd = rayStart.add(rayDir.scale(100.0));
                        BlockHitResult hit = client.level.clip(new ClipContext(
                            rayStart,
                            rayEnd,
                            ClipContext.Block.OUTLINE,
                            ClipContext.Fluid.NONE,
                            client.player
                        ));
                        
                        if (hit != null && hit.getType() == HitResult.Type.BLOCK) {
                            rightClickWorldPos = hit.getLocation().add(Vec3.atLowerCornerOf(hit.getDirection().getUnitVec3i()).scale(0.2));
                        } else {
                            rightClickWorldPos = rayStart.add(rayDir.scale(8.0));
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
            int pW = 590;
            int pH = 320;
            int pX = (w - pW) / 2;
            int pY = (h - pH) / 2;
            int headerH = 22;
            int sideW = 145;
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
                int startCategoryY = pY + headerH + 6;
                int categoryRowH = 22;
                for (int i = 0; i <= 7; i++) {
                    int catY = startCategoryY + i * categoryRowH;
                    if (my >= catY && my < catY + 18) {
                        selectedSettingsCategory = i;
                        activeRebindingKeyIndex = -1;
                        return true;
                    }
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
                        Minecraft.getInstance().options.guiScale().set(sc);
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
                int startY = pY + headerH + 10;
                int fullW = 400;

                // Quality Presets clicks
                int qSegW = (fullW - 12) / 5;
                for (int i = 0; i < 5; i++) {
                    int presetBtnX = contentX + i * (qSegW + 3);
                    if (mx >= presetBtnX && mx < presetBtnX + qSegW && my >= startY + 12 && my < startY + 12 + 16) {
                        SettingsPresets.applyQuality(i);
                        return true;
                    }
                }

                // Style Presets clicks
                int sSegW = (fullW - 9) / 4;
                for (int i = 0; i < 4; i++) {
                    int presetBtnX = contentX + i * (sSegW + 3);
                    if (mx >= presetBtnX && mx < presetBtnX + sSegW && my >= startY + 56 && my < startY + 56 + 16) {
                        SettingsPresets.applyStyle(i);
                        return true;
                    }
                }

                // Hold Bake click
                int curY = startY + 100;
                if (mx >= contentX && mx < contentX + 200 && my >= curY && my < curY + 12) {
                    LightConfig.holdBake = !LightConfig.holdBake;
                    return true;
                }

                // Hold Bake On Join click
                curY += 20;
                if (mx >= contentX && mx < contentX + 200 && my >= curY && my < curY + 12) {
                    LightConfig.holdBakeOnJoin = !LightConfig.holdBakeOnJoin;
                    return true;
                }
            } else if (selectedSettingsCategory == 4) {
                int colW = 200;
                int startY = pY + headerH + 10;
                int itemSpacing = 20;

                // Column 1 (Left) clicks
                int curY = startY;
                curY += 12; // Title label offset

                // Shadow Quality segmented buttons
                String[] qLabels = {"LOW", "MED", "HIGH", "ULTRA"};
                int segW = (colW - 9) / 4;
                for (int i = 0; i < qLabels.length; i++) {
                    int qBtnX = contentX + i * (segW + 3);
                    if (mx >= qBtnX && mx < qBtnX + segW && my >= curY && my < curY + 14) {
                        LightConfig.shadowQuality = i;
                        return true;
                    }
                }
                curY += 20;

                // Shadows Live Checkbox click
                if (mx >= contentX && mx < contentX + colW && my >= curY && my < curY + 12) {
                    LightConfig.shadowsLive = !LightConfig.shadowsLive;
                    return true;
                }
                curY += itemSpacing;

                // Shadow Cache Checkbox click
                if (mx >= contentX && mx < contentX + colW && my >= curY && my < curY + 12) {
                    LightConfig.shadowCache = !LightConfig.shadowCache;
                    return true;
                }
                curY += itemSpacing;

                // Shadow Blocks Checkbox click
                if (mx >= contentX && mx < contentX + colW && my >= curY && my < curY + 12) {
                    LightConfig.shadowBlocks = !LightConfig.shadowBlocks;
                    return true;
                }
                curY += itemSpacing;

                // Hold Bake Checkbox click
                if (mx >= contentX && mx < contentX + colW && my >= curY && my < curY + 12) {
                    LightConfig.holdBake = !LightConfig.holdBake;
                    return true;
                }
                curY += itemSpacing;

                // Column 2 (Right) clicks
                int rightColX = contentX + 215;
                if (trackShadowBlockRadius.mouseClicked(mx, my, btn) ||
                    trackShadowSoftness.mouseClicked(mx, my, btn)) {
                    return true;
                }
            } else if (selectedSettingsCategory == 5) {
                int colW = 200;
                int startY = pY + headerH + 10;
                int itemSpacing = 20;

                // Column 1 (Left) clicks
                if (trackVlIntensity.mouseClicked(mx, my, btn) ||
                    trackVlSteps.mouseClicked(mx, my, btn) ||
                    trackVlMaxDist.mouseClicked(mx, my, btn) ||
                    trackVlTipBoost.mouseClicked(mx, my, btn) ||
                    trackVlTipRadius.mouseClicked(mx, my, btn)) {
                    return true;
                }

                // Column 2 (Right) clicks
                int rightColX = contentX + 215;
                if (trackVlNoiseAmount.mouseClicked(mx, my, btn) ||
                    trackVlNoiseScale.mouseClicked(mx, my, btn) ||
                    trackVlNoiseSpeed.mouseClicked(mx, my, btn) ||
                    trackVlNoiseMorph.mouseClicked(mx, my, btn)) {
                    return true;
                }

                int togglesY = startY + 4 * itemSpacing;
                // VL Shadows click
                if (mx >= rightColX && mx < rightColX + 90 && my >= togglesY && my < togglesY + 12) {
                    LightConfig.vlShadows = !LightConfig.vlShadows;
                    return true;
                }
                // VL Noise click
                if (mx >= rightColX + 100 && mx < rightColX + 100 + 90 && my >= togglesY && my < togglesY + 12) {
                    LightConfig.vlNoise = !LightConfig.vlNoise;
                    return true;
                }
                togglesY += 15;
                // Blue Noise click
                if (mx >= rightColX && mx < rightColX + 90 && my >= togglesY && my < togglesY + 12) {
                    LightConfig.vlBlueNoise = !LightConfig.vlBlueNoise;
                    return true;
                }
                // Temporal Dither click
                if (mx >= rightColX + 100 && mx < rightColX + 100 + 90 && my >= togglesY && my < togglesY + 12) {
                    LightConfig.vlDitherTemporal = !LightConfig.vlDitherTemporal;
                    return true;
                }
                togglesY += 15;
                // Cluster Cull click
                if (mx >= rightColX && mx < rightColX + 90 && my >= togglesY && my < togglesY + 12) {
                    LightConfig.vlClusterCull = !LightConfig.vlClusterCull;
                    return true;
                }
                // Shadow Hi-Z click
                if (mx >= rightColX + 100 && mx < rightColX + 100 + 90 && my >= togglesY && my < togglesY + 12) {
                    LightConfig.vlShadowHiz = !LightConfig.vlShadowHiz;
                    return true;
                }
            } else if (selectedSettingsCategory == 6) {
                int colW = 200;
                int startY = pY + headerH + 10;
                int itemSpacing = 20;
                int curY = startY;

                // Outline Enabled click
                if (mx >= contentX && mx < contentX + colW && my >= curY && my < curY + 12) {
                    LightConfig.outline = !LightConfig.outline;
                    return true;
                }
                curY += itemSpacing;

                // Target clicks
                curY += 12;
                String[] tLabels = {"ALL", "ENTITIES", "BLOCKS"};
                int segW = (colW - 6) / 3;
                for (int i = 0; i < tLabels.length; i++) {
                    int targetBtnX = contentX + i * (segW + 3);
                    if (mx >= targetBtnX && mx < targetBtnX + segW && my >= curY && my < curY + 14) {
                        LightConfig.outlineTarget = i;
                        return true;
                    }
                }
                curY += 20;

                // Front outline click
                if (mx >= contentX && mx < contentX + colW && my >= curY && my < curY + 12) {
                    LightConfig.outlineFront = !LightConfig.outlineFront;
                    return true;
                }
                curY += itemSpacing;

                // Glow outline click
                if (mx >= contentX && mx < contentX + colW && my >= curY && my < curY + 12) {
                    LightConfig.outlineGlow = !LightConfig.outlineGlow;
                    return true;
                }
                curY += itemSpacing;

                // Right column trackpads
                if (trackOutlineStrength.mouseClicked(mx, my, btn) ||
                    trackOutlinePixelSize.mouseClicked(mx, my, btn) ||
                    trackOutlineFresnelPower.mouseClicked(mx, my, btn) ||
                    trackOutlineBack.mouseClicked(mx, my, btn) ||
                    trackOutlineFrontStrength.mouseClicked(mx, my, btn) ||
                    trackOutlineGlowStrength.mouseClicked(mx, my, btn)) {
                    return true;
                }
            } else if (selectedSettingsCategory == 7) {
                int colW = 200;
                int startY = pY + headerH + 10;
                int itemSpacing = 20;
                int curY = startY;

                // Auto Lights Enable Checkbox click
                if (mx >= contentX && mx < contentX + colW && my >= curY && my < curY + 12) {
                    LightConfig.autoLights = !LightConfig.autoLights;
                    return true;
                }
                curY += itemSpacing;
                boolean autoLightsOn = LightConfig.autoLights;

                if (autoLightsOn) {
                    if (mx >= contentX && mx < contentX + colW && my >= curY && my < curY + 12) {
                        LightConfig.autoLightCulling = !LightConfig.autoLightCulling;
                        return true;
                    }
                    curY += itemSpacing;

                    // Auto Lights Shadows Checkbox click
                    if (mx >= contentX && mx < contentX + colW && my >= curY && my < curY + 12) {
                        LightConfig.autoLightShadows = !LightConfig.autoLightShadows;
                        return true;
                    }
                }

                // Right column trackpads
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
                        Minecraft mc = Minecraft.getInstance();
                        if (mc.player != null) {
                            mc.player.sendSystemMessage(Component.literal(CALKeys.SAVED_SUCCESS.get()));
                        }
                    } else if (clickedIdx == 2) {
                        showPatcherPopup = true;
                        if (patcherPanel != null) {
                            patcherPanel.reload();
                        }
                    } else if (clickedIdx == 3) {
                        Minecraft mc = Minecraft.getInstance();
                        if (mc.screen != null) {
                            mc.screen.onClose();
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
                Vec3 p = Minecraft.getInstance().gameRenderer.getMainCamera().position();
                int id = ThreadLocalRandom.current().nextInt(100000, 999999);
                LightInstance light = LightManager.INSTANCE.updatePoint(id, (float) p.x, (float) p.y, (float) p.z, 1f, 1f, 1f, 1.0f, 6.0f);
                light.persistent = true;
                LightGizmo.INSTANCE.setSelectedLight(light);
                rebuildSettings();
                return true;
            }

            // Quick Add Spot
            if (mx >= 8 + btnW + 6 && mx < leftPanelW - 8 && my >= addY && my < addY + 16) {
                CALUndoManager.pushState();
                Vec3 p = Minecraft.getInstance().gameRenderer.getMainCamera().position();
                int id = ThreadLocalRandom.current().nextInt(100000, 999999);
                LightInstance light = LightManager.INSTANCE.updateSpot(id, (float) p.x, (float) p.y, (float) p.z, 0f, -1f, 0f, 1f, 1f, 1f, 1.0f, 35.0f, 10.0f, 12.0f);
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

        // Gizmo Mode Buttons click
        int modeStartX = leftPanelW + 235;
        LightGizmo.Mode[] modes = { LightGizmo.Mode.TRANSLATE, LightGizmo.Mode.ROTATE, LightGizmo.Mode.COMBINED };
        int modeBtnW = 60;
        for (int i = 0; i < modes.length; i++) {
            int btnX = modeStartX + i * (modeBtnW + 4);
            if (mx >= btnX && mx < btnX + modeBtnW && my >= topMenuH + 2 && my < topMenuH + 18) {
                LightGizmo.INSTANCE.setMode(modes[i]);
                return true;
            }
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
            if (selectedSettingsCategory == 4) {
                return trackShadowBlockRadius.mouseReleased(mx, my, btn) ||
                       trackShadowSoftness.mouseReleased(mx, my, btn);
            } else if (selectedSettingsCategory == 5) {
                return trackVlIntensity.mouseReleased(mx, my, btn) ||
                       trackVlSteps.mouseReleased(mx, my, btn) ||
                       trackVlMaxDist.mouseReleased(mx, my, btn) ||
                       trackVlTipBoost.mouseReleased(mx, my, btn) ||
                       trackVlTipRadius.mouseReleased(mx, my, btn) ||
                       trackVlNoiseAmount.mouseReleased(mx, my, btn) ||
                       trackVlNoiseScale.mouseReleased(mx, my, btn) ||
                       trackVlNoiseSpeed.mouseReleased(mx, my, btn) ||
                       trackVlNoiseMorph.mouseReleased(mx, my, btn);
            } else if (selectedSettingsCategory == 6) {
                return trackOutlineStrength.mouseReleased(mx, my, btn) ||
                       trackOutlinePixelSize.mouseReleased(mx, my, btn) ||
                       trackOutlineFresnelPower.mouseReleased(mx, my, btn) ||
                       trackOutlineBack.mouseReleased(mx, my, btn) ||
                       trackOutlineFrontStrength.mouseReleased(mx, my, btn) ||
                       trackOutlineGlowStrength.mouseReleased(mx, my, btn);
            } else if (selectedSettingsCategory == 7) {
                return trackAutoLightIntensity.mouseReleased(mx, my, btn) ||
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
                int pW = 590;
                int pX = (w - pW) / 2;
                int sideW = 145;
                int contentX = pX + sideW + 10;
                float t = (float)(mx - contentX) / 160.0f;
                t = Math.max(0.0f, Math.min(1.0f, t));
                CalSettings.INSTANCE.gizmoSize = t * 10.0f;
                CalSettings.INSTANCE.save();
            } else if (selectedSettingsCategory == 4) {
                return trackShadowBlockRadius.mouseDragged(mx, my, btn, dx, dy) ||
                       trackShadowSoftness.mouseDragged(mx, my, btn, dx, dy);
            } else if (selectedSettingsCategory == 5) {
                return trackVlIntensity.mouseDragged(mx, my, btn, dx, dy) ||
                       trackVlSteps.mouseDragged(mx, my, btn, dx, dy) ||
                       trackVlMaxDist.mouseDragged(mx, my, btn, dx, dy) ||
                       trackVlTipBoost.mouseDragged(mx, my, btn, dx, dy) ||
                       trackVlTipRadius.mouseDragged(mx, my, btn, dx, dy) ||
                       trackVlNoiseAmount.mouseDragged(mx, my, btn, dx, dy) ||
                       trackVlNoiseScale.mouseDragged(mx, my, btn, dx, dy) ||
                       trackVlNoiseSpeed.mouseDragged(mx, my, btn, dx, dy) ||
                       trackVlNoiseMorph.mouseDragged(mx, my, btn, dx, dy);
            } else if (selectedSettingsCategory == 6) {
                return trackOutlineStrength.mouseDragged(mx, my, btn, dx, dy) ||
                       trackOutlinePixelSize.mouseDragged(mx, my, btn, dx, dy) ||
                       trackOutlineFresnelPower.mouseDragged(mx, my, btn, dx, dy) ||
                       trackOutlineBack.mouseDragged(mx, my, btn, dx, dy) ||
                       trackOutlineFrontStrength.mouseDragged(mx, my, btn, dx, dy) ||
                       trackOutlineGlowStrength.mouseDragged(mx, my, btn, dx, dy);
            } else if (selectedSettingsCategory == 7) {
                return trackAutoLightIntensity.mouseDragged(mx, my, btn, dx, dy) ||
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
        boolean ctrl = isCtrlDown();
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
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen != null) {
                mc.screen.onClose();
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
                double posX = copiedLight.x + 1.0;
                double posZ = copiedLight.z + 1.0;
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

    private static boolean isCtrlDown() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return false;
        return InputConstants.isKeyDown(mc.getWindow(), GLFW.GLFW_KEY_LEFT_CONTROL) || InputConstants.isKeyDown(mc.getWindow(), GLFW.GLFW_KEY_RIGHT_CONTROL);
    }
}
