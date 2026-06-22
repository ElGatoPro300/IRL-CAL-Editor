package elgatopro300.cal_lights.ui.panels;

import elgatopro300.cal_lights.animation.LightAnimation;
import elgatopro300.cal_lights.graphics.CalLightsIcons;
import elgatopro300.cal_lights.manager.CALUndoManager;
import elgatopro300.cal_lights.manager.GoboManager;
import elgatopro300.cal_lights.manager.LightInstance;
import elgatopro300.cal_lights.manager.LightManager;
import elgatopro300.cal_lights.ui.CALEditorScreen;
import elgatopro300.cal_lights.ui.CALKeys;
import elgatopro300.cal_lights.ui.CLUIContext;
import elgatopro300.cal_lights.ui.CLUIElement;
import elgatopro300.cal_lights.ui.CalSettings;
import elgatopro300.cal_lights.ui.L10n;
import elgatopro300.cal_lights.ui.elements.CLUIColorPicker;
import elgatopro300.cal_lights.ui.elements.CLUISwitch;
import elgatopro300.cal_lights.ui.elements.CLUITrackpad;

import net.minecraft.client.MinecraftClient;

import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class LightInspectorPanel extends CLUIElement {
    private final LightInstance light;
    
    // Transform coordinates (Compact horizontal row)
    private final CLUITrackpad trackX;
    private final CLUITrackpad trackY;
    private final CLUITrackpad trackZ;

    // Spot Direction vectors (Rotation X, Y, Z)
    private final CLUITrackpad trackRX;
    private final CLUITrackpad trackRY;
    private final CLUITrackpad trackRZ;

    // Light general adjustments
    private final CLUITrackpad trackIntensity;
    private CLUIColorPicker colorPicker;

    // Specific properties
    private final CLUITrackpad trackRadius;
    private final CLUITrackpad trackInner;
    private final CLUITrackpad trackOuter;
    private final CLUITrackpad trackDistance;

    // Volumetric Fog properties
    private final CLUITrackpad trackFogDispersion;
    private final CLUITrackpad trackFogDensity;
    private final CLUITrackpad trackFogAnisotropy;

    // Shadow properties
    private final CLUITrackpad trackShadowSoftness;
    private final CLUITrackpad trackShadowIntensity;

    // Rim light properties
    private final CLUITrackpad trackRimIntensity;
    private final CLUITrackpad trackRimPower;
    private final CLUITrackpad trackRimHardness;

    // Outline properties
    private final CLUITrackpad trackOutlineIntensity;
    private final CLUITrackpad trackOutlineThickness;
    private boolean outlineExpanded = false;
    private float outlineAnimH = -1f;
    private final CLUISwitch outlineSwitch;

    // Flare properties
    private final CLUITrackpad trackFlareGlowSize;
    private final CLUITrackpad trackFlareGlowIntensity;
    private final CLUITrackpad trackFlareRayLength;
    private final CLUITrackpad trackFlareRayThickness;
    private final CLUITrackpad trackFlareRayLength2;
    private final CLUITrackpad trackFlareRayThickness2;
    private final CLUITrackpad trackFlareRayLength3;
    private final CLUITrackpad trackFlareRayThickness3;
    private final CLUITrackpad trackFlareRotation;
    private final CLUITrackpad trackFlareStartAngle;
    private final CLUITrackpad trackFlareEndAngle;

    // Gobo properties
    private final CLUITrackpad trackGoboRotation;
    private boolean goboExpanded = false;
    private boolean goboSelectorExpanded = false;
    private float goboAnimH = -1f;

    // Animation
    private final AnimationEditorPanel animationPanel;
    private final Runnable onUpdate;

    // Accordion Expansion States
    private boolean transformExpanded = true;
    private boolean lightExpanded = true;
    private boolean specificExpanded = true;
    private boolean fogExpanded = true;
    private boolean shadowExpanded = true;
    private boolean rimExpanded = false;
    private boolean flareExpanded = false;
    private boolean animationExpanded = false;
    private boolean nameFocused = false;
    private int renameCursorIdx = 0;

    // Accordion Animated Heights
    private float transformAnimH = -1f;
    private float lightAnimH = -1f;
    private float specificAnimH = -1f;
    private float fogAnimH = -1f;
    private float shadowAnimH = -1f;
    private float rimAnimH = -1f;
    private float flareAnimH = -1f;
    private float animationAnimH = -1f;

    // Inline Switches
    private final CLUISwitch fogSwitch;
    private final CLUISwitch shadowSwitch;
    private final CLUISwitch rimSwitch;
    private final CLUISwitch flareSwitch;
    private final CLUISwitch animationSwitch;

    // Scrolling vertical offset (Y coordinate offset)
    private int scrollY = 0;

    public LightInspectorPanel(LightInstance light, Runnable onUpdate) {
        this.light = light;
        this.onUpdate = onUpdate;

        // Position coordinates (Red, Green, Blue colored badges inside CLUITrackpad)
        this.trackX = new CLUITrackpad("X", light.x, -10000, 10000, val -> {
            light.x = val;
            if (onUpdate != null) onUpdate.run();
        });
        this.trackY = new CLUITrackpad("Y", light.y, -10000, 10000, val -> {
            light.y = val;
            if (onUpdate != null) onUpdate.run();
        });
        this.trackZ = new CLUITrackpad("Z", light.z, -10000, 10000, val -> {
            light.z = val;
            if (onUpdate != null) onUpdate.run();
        });

        // Direction vectors represented as Rotation X, Y, Z
        this.trackRX = new CLUITrackpad("RX", light.rx, -360000f, 360000f, val -> {
            light.rx = val;
            float[] dir = getDirFromRotXYZ(light.rx, light.ry, light.rz);
            light.dx = dir[0];
            light.dy = dir[1];
            light.dz = dir[2];
            if (onUpdate != null) onUpdate.run();
        });

        this.trackRY = new CLUITrackpad("RY", light.ry, -360000f, 360000f, val -> {
            light.ry = val;
            float[] dir = getDirFromRotXYZ(light.rx, light.ry, light.rz);
            light.dx = dir[0];
            light.dy = dir[1];
            light.dz = dir[2];
            if (onUpdate != null) onUpdate.run();
        });

        this.trackRZ = new CLUITrackpad("RZ", light.rz, -360000f, 360000f, val -> {
            light.rz = val;
            float[] dir = getDirFromRotXYZ(light.rx, light.ry, light.rz);
            light.dx = dir[0];
            light.dy = dir[1];
            light.dz = dir[2];
            if (onUpdate != null) onUpdate.run();
        });

        // General adjustments
        this.trackIntensity = new CLUITrackpad(CALKeys.PROP_INTENSITY.get(), light.intensity, 0.0f, 100.0f, val -> {
            light.intensity = val;
            if (onUpdate != null) onUpdate.run();
        }).setArrowStep(0.05f);
        this.colorPicker = new CLUIColorPicker(light.r, light.g, light.b, () -> {
            if (this.colorPicker != null) {
                light.r = this.colorPicker.getR();
                light.g = this.colorPicker.getG();
                light.b = this.colorPicker.getB();
                if (onUpdate != null) onUpdate.run();
            }
        });

        // Specific point / spot adjustments
        this.trackRadius = new CLUITrackpad(CALKeys.PROP_RADIUS.get(), light.radius, 0.1f, 1000.0f, val -> {
            light.radius = val;
            if (onUpdate != null) onUpdate.run();
        }).setArrowStep(0.5f);
        this.trackInner = new CLUITrackpad(CALKeys.PROP_INNER_ANGLE.get(), light.innerAngle, 0.0f, 90.0f, val -> {
            light.innerAngle = val;
            if (onUpdate != null) onUpdate.run();
        }).setArrowStep(1.0f);
        this.trackOuter = new CLUITrackpad(CALKeys.PROP_OUTER_ANGLE.get(), light.outerAngle, 0.0f, 90.0f, val -> {
            light.outerAngle = val;
            if (onUpdate != null) onUpdate.run();
        }).setArrowStep(1.0f);
        this.trackDistance = new CLUITrackpad(CALKeys.PROP_DISTANCE.get(), light.distance, 0.1f, 1000.0f, val -> {
            light.distance = val;
            if (onUpdate != null) onUpdate.run();
        }).setArrowStep(1.0f);

        // Volumetric fog adjustments
        this.trackFogDispersion = new CLUITrackpad(CALKeys.PANEL_FOG_DISPERSION.get(), light.fogDispersion, 0.0f, 10.0f, val -> {
            light.fogDispersion = val;
            if (onUpdate != null) onUpdate.run();
        }).setArrowStep(0.05f);
        this.trackFogDensity = new CLUITrackpad(CALKeys.PANEL_FOG_DENSITY.get(), light.fogDensity, 0.0f, 10.0f, val -> {
            light.fogDensity = val;
            if (onUpdate != null) onUpdate.run();
        }).setArrowStep(0.05f);
        this.trackFogAnisotropy = new CLUITrackpad(CALKeys.PANEL_FOG_ANISOTROPY.get(), light.fogAnisotropy, -10.0f, 10.0f, val -> {
            light.fogAnisotropy = val;
            if (onUpdate != null) onUpdate.run();
        }).setArrowStep(0.05f);

        // Shadow adjustments
        this.trackShadowSoftness = new CLUITrackpad(CALKeys.PANEL_SHADOW_SOFTNESS.get(), light.shadowSoftness, 0.0f, 5.0f, val -> {
            light.shadowSoftness = val;
            if (onUpdate != null) onUpdate.run();
        }).setArrowStep(0.1f);
        this.trackShadowIntensity = new CLUITrackpad(CALKeys.PANEL_SHADOW_INTENSITY.get(), light.shadowIntensity, 0.0f, 1.0f, val -> {
            light.shadowIntensity = val;
            if (onUpdate != null) onUpdate.run();
        }).setArrowStep(0.05f);

        // Rim light adjustments
        this.trackRimIntensity = new CLUITrackpad(CALKeys.PROP_RIM_INTENSITY_LABEL.get(), light.rimIntensity, 0.0f, 20.0f, val -> {
            light.rimIntensity = val;
            if (onUpdate != null) onUpdate.run();
        }).setArrowStep(0.1f);
        this.trackRimPower = new CLUITrackpad(CALKeys.PROP_RIM_POWER_LABEL.get(), light.rimPower, 0.5f, 16.0f, val -> {
            light.rimPower = val;
            if (onUpdate != null) onUpdate.run();
        }).setArrowStep(0.25f);
        // 0-1: Fresnel→Cel silhouette | 1-2: Cel→Aristas geométricas (dFdx/dFdy)
        this.trackRimHardness = new CLUITrackpad(CALKeys.PROP_RIM_HARDNESS_LABEL.get(), light.rimHardness, 0.0f, 2.0f, val -> {
            light.rimHardness = val;
            if (onUpdate != null) onUpdate.run();
        }).setArrowStep(0.05f);

        // Outline adjustments
        this.trackOutlineIntensity = new CLUITrackpad(CALKeys.PROP_OUTLINE_INTENSITY_LABEL.get(), light.outlineIntensity, 0.0f, 20.0f, val -> {
            light.outlineIntensity = val;
            if (onUpdate != null) onUpdate.run();
        }).setArrowStep(0.1f);
        this.trackOutlineThickness = new CLUITrackpad(CALKeys.PROP_OUTLINE_THICKNESS.get(), light.outlineThickness, 0.0f, 16.0f, val -> {
            light.outlineThickness = val;
            if (onUpdate != null) onUpdate.run();
        }).setArrowStep(0.1f);

        this.trackFlareGlowSize = new CLUITrackpad(CALKeys.PROP_FLARE_GLOW_SIZE_LABEL.get(), light.flareGlowSize, 0.0f, 100.0f, val -> {
            light.flareGlowSize = val;
            if (onUpdate != null) onUpdate.run();
        }).setArrowStep(0.05f);

        this.trackFlareGlowIntensity = new CLUITrackpad(CALKeys.PROP_FLARE_GLOW_INTENSITY_LABEL.get(), light.flareGlowIntensity, 0.0f, 10.0f, val -> {
            light.flareGlowIntensity = val;
            if (onUpdate != null) onUpdate.run();
        }).setArrowStep(0.05f);

        this.trackFlareRayLength = new CLUITrackpad(CALKeys.PROP_FLARE_RAY_LENGTH_LABEL.get(), light.flareRayLength, 0.0f, 100.0f, val -> {
            light.flareRayLength = val;
            if (onUpdate != null) onUpdate.run();
        }).setArrowStep(0.1f);

        this.trackFlareRayThickness = new CLUITrackpad(CALKeys.PROP_FLARE_RAY_THICKNESS_LABEL.get(), light.flareRayThickness, 0.0f, 20.0f, val -> {
            light.flareRayThickness = val;
            if (onUpdate != null) onUpdate.run();
        }).setArrowStep(0.05f);

        this.trackFlareRayLength2 = new CLUITrackpad(CALKeys.PROP_FLARE_RAY_LENGTH2_LABEL.get(), light.flareRayLength2, 0.0f, 100.0f, val -> {
            light.flareRayLength2 = val;
            if (onUpdate != null) onUpdate.run();
        }).setArrowStep(0.1f);

        this.trackFlareRayThickness2 = new CLUITrackpad(CALKeys.PROP_FLARE_RAY_THICKNESS2_LABEL.get(), light.flareRayThickness2, 0.0f, 20.0f, val -> {
            light.flareRayThickness2 = val;
            if (onUpdate != null) onUpdate.run();
        }).setArrowStep(0.05f);

        this.trackFlareRayLength3 = new CLUITrackpad(CALKeys.PROP_FLARE_RAY_LENGTH3_LABEL.get(), light.flareRayLength3, 0.0f, 100.0f, val -> {
            light.flareRayLength3 = val;
            if (onUpdate != null) onUpdate.run();
        }).setArrowStep(0.1f);

        this.trackFlareRayThickness3 = new CLUITrackpad(CALKeys.PROP_FLARE_RAY_THICKNESS3_LABEL.get(), light.flareRayThickness3, 0.0f, 20.0f, val -> {
            light.flareRayThickness3 = val;
            if (onUpdate != null) onUpdate.run();
        }).setArrowStep(0.05f);

        this.trackFlareRotation = new CLUITrackpad(CALKeys.PROP_FLARE_ROTATION_LABEL.get(), light.flareRotation, -360.0f, 360.0f, val -> {
            light.flareRotation = val;
            if (onUpdate != null) onUpdate.run();
        }).setArrowStep(1.0f);

        this.trackFlareStartAngle = new CLUITrackpad(CALKeys.PROP_FLARE_START_ANGLE_LABEL.get(), light.flareStartAngle, 0.0f, 180.0f, val -> {
            light.flareStartAngle = val;
            if (onUpdate != null) onUpdate.run();
        }).setArrowStep(0.5f);

        this.trackFlareEndAngle = new CLUITrackpad(CALKeys.PROP_FLARE_END_ANGLE_LABEL.get(), light.flareEndAngle, 0.0f, 180.0f, val -> {
            light.flareEndAngle = val;
            if (onUpdate != null) onUpdate.run();
        }).setArrowStep(0.5f);

        this.trackGoboRotation = new CLUITrackpad(CALKeys.PROP_GOBO_ROTATION_LABEL.get(), light.goboRotation, 0.0f, 360.0f, val -> {
            light.goboRotation = val;
            if (onUpdate != null) onUpdate.run();
        }).setArrowStep(1.0f);

        // Animation panel
        this.animationPanel = new AnimationEditorPanel(light, onUpdate);

        // Switches
        this.fogSwitch = new CLUISwitch(CALKeys.PANEL_FOG_ENABLED, light.fogEnabled, val -> {
            light.fogEnabled = val;
            if (onUpdate != null) onUpdate.run();
        });
        this.shadowSwitch = new CLUISwitch(CALKeys.PANEL_SHADOW_ENABLED, light.shadowEnabled, val -> {
            light.shadowEnabled = val;
            if (onUpdate != null) onUpdate.run();
        }).setColors(0xFF5C6BC0, 0xFF7986CB, 0xFF283593);
        this.rimSwitch = new CLUISwitch(CALKeys.PANEL_SHADOW_ENABLED, light.rimEnabled, val -> {
            light.rimEnabled = val;
            if (onUpdate != null) onUpdate.run();
        }).setColors(0xFFE65100, 0xFFFF8F00, 0xFF7B1FA2); // naranja → morado para diferenciarlo
        this.outlineSwitch = new CLUISwitch(CALKeys.PANEL_OUTLINE_ENABLED, light.outlineEnabled, val -> {
            light.outlineEnabled = val;
            if (onUpdate != null) onUpdate.run();
        }).setColors(0xFF4A148C, 0xFF7B1FA2, 0xFF311B92); // purple theme
        this.flareSwitch = new CLUISwitch(CALKeys.PANEL_FLARE_ENABLED, light.flareEnabled, val -> {
            light.flareEnabled = val;
            if (onUpdate != null) onUpdate.run();
        }).setColors(0xFF00ACC1, 0xFF26C6DA, 0xFF006064);
        this.animationSwitch = new CLUISwitch(CALKeys.PANEL_FOG_ENABLED, light.animation != null && light.animation.enabled, val -> {
            if (light.animation == null) {
                light.animation = new LightAnimation();
            }
            light.animation.enabled = val;
            if (light.animation.enabled) light.animation.resetBase();
            if (onUpdate != null) onUpdate.run();
        });
    }

    @Override
    public boolean scroll(int mx, int my, double amount) {
        // If cursor is over the animation panel, delegate to it
        if (animationExpanded && animationPanel != null &&
            mx >= animationPanel.x && mx < animationPanel.x + animationPanel.w &&
            my >= animationPanel.y && my < animationPanel.y + animationPanel.h) {
            return animationPanel.scroll(mx, my, amount);
        }
        // Otherwise scroll the inspector
        scrollY += (int)(amount * 16);
        if (scrollY > 0) scrollY = 0;
        
        // Prevent scrolling infinitely downwards
        int maxScroll = -800; 
        if (scrollY < maxScroll) scrollY = maxScroll;
        return true;
    }

    @Override
    public void render(CLUIContext ctx) {
        // Calculate target animated heights for all collapsible inspector sections
        float targetTransformH = transformExpanded ? (16 + 3) : 0;
        float targetLightH = lightExpanded ? (16 + 3 + 16 + 3 + 16 + 3 + 110 + 3) : 0;
        float targetSpecificH = specificExpanded ? (!light.isSpot ? (16 + 3) : (16 + 3 + 16 + 3 + 16 + 3 + 16 + 3)) : 0;
        float targetFogH = fogExpanded ? (light.fogEnabled ? (16 + 3 + 16 + 3 + 16 + 3 + 16 + 3) : (16 + 3)) : 0;
        float targetShadowH = shadowExpanded ? (light.shadowEnabled ? (16 + 3 + 16 + 3) : (16 + 3)) : 0;
        float targetRimH = rimExpanded ? (light.rimEnabled ? (16 + 3 + 16 + 3 + 16 + 3 + 16 + 3 + 16 + 3) : (16 + 3)) : 0;
        float targetOutlineH = outlineExpanded ? (light.outlineEnabled ? (16 + 3 + 16 + 3 + 16 + 3) : (16 + 3)) : 0;
        float targetFlareH = flareExpanded ? (light.flareEnabled ? (16 + 3 + 11 * (16 + 3)) : (16 + 3)) : 0;
        float targetAnimationH = animationExpanded ? (16 + 3) : 0;

        boolean showGobo = light.isSpot;
        int numGobos = 1 + GoboManager.INSTANCE.getGoboNames().size();
        float targetGoboH = showGobo ? (goboExpanded ? ((16 + 3) + (goboSelectorExpanded ? numGobos * (16 + 3) : 0) + (16 + 3)) : 0) : 0;

        if (transformAnimH < 0) transformAnimH = targetTransformH;
        if (lightAnimH < 0) lightAnimH = targetLightH;
        if (specificAnimH < 0) specificAnimH = targetSpecificH;
        if (fogAnimH < 0) fogAnimH = targetFogH;
        if (shadowAnimH < 0) shadowAnimH = targetShadowH;
        if (rimAnimH < 0) rimAnimH = targetRimH;
        if (outlineAnimH < 0) outlineAnimH = targetOutlineH;
        if (flareAnimH < 0) flareAnimH = targetFlareH;
        if (animationAnimH < 0) animationAnimH = targetAnimationH;
        if (goboAnimH < 0) goboAnimH = targetGoboH;

        fogSwitch.setValue(light.fogEnabled);
        shadowSwitch.setValue(light.shadowEnabled);
        rimSwitch.setValue(light.rimEnabled);
        outlineSwitch.setValue(light.outlineEnabled);
        flareSwitch.setValue(light.flareEnabled);
        animationSwitch.setValue(light.animation != null && light.animation.enabled);

        if (CalSettings.INSTANCE.simplifyAnimations) {
            transformAnimH = targetTransformH;
            lightAnimH = targetLightH;
            specificAnimH = targetSpecificH;
            fogAnimH = targetFogH;
            shadowAnimH = targetShadowH;
            rimAnimH = targetRimH;
            outlineAnimH = targetOutlineH;
            flareAnimH = targetFlareH;
            animationAnimH = targetAnimationH;
            goboAnimH = targetGoboH;
        } else {
            transformAnimH += (targetTransformH - transformAnimH) * 0.25f;
            lightAnimH += (targetLightH - lightAnimH) * 0.25f;
            specificAnimH += (targetSpecificH - specificAnimH) * 0.25f;
            fogAnimH += (targetFogH - fogAnimH) * 0.25f;
            shadowAnimH += (targetShadowH - shadowAnimH) * 0.25f;
            rimAnimH += (targetRimH - rimAnimH) * 0.25f;
            outlineAnimH += (targetOutlineH - outlineAnimH) * 0.25f;
            flareAnimH += (targetFlareH - flareAnimH) * 0.25f;
            animationAnimH += (targetAnimationH - animationAnimH) * 0.25f;
            goboAnimH += (targetGoboH - goboAnimH) * 0.25f;
        }

        // Draw solid background over right panel
        ctx.batcher.box(x, y, x + w, y + h, 0xFF141418);
        ctx.batcher.outline(x, y, x + w, y + h, 0xFF22222A, 1);

        // Header (Title)
        String title = light.isSpot ? CALKeys.PANEL_SPOT_TITLE.get() : CALKeys.PANEL_POINT_TITLE.get();
        ctx.batcher.text(title, x + 10, y + 10, 0xFFFFAA00);

        // Clip scrollable content to the panel body
        ctx.batcher.clip(x + 2, y + 25, w - 4, h - 27);

        int currentY = y + 25 + scrollY;
        int elementH = 16;
        int headerH = 18;
        int gap = 3;

        // ==========================================
        // 0. ANIMACIONES (now at the very top!)
        // ==========================================
        boolean hoverAnimRow = ctx.mouseX >= x + 8 && ctx.mouseX < x + w - 8 && ctx.mouseY >= currentY && ctx.mouseY < currentY + headerH;
        int animBg = hoverAnimRow ? 0xFFFF6F00 : 0xFFE65100;
        ctx.batcher.box(x + 8, currentY, x + w - 8, currentY + headerH, animBg);
        ctx.batcher.icon(animationExpanded ? CalLightsIcons.ARROW_DOWN : CalLightsIcons.ARROW_RIGHT, x + 12, currentY + 1, 0xFFFFFFFF);
        ctx.batcher.text("🎬 " + CALKeys.ANIMATIONS.get(), x + 28, currentY + 5, 0xFFFFFFFF);
        currentY += headerH + gap;

        int animH_animation = (int) animationAnimH;
        if (animH_animation > 0) {
            ctx.batcher.clip(x + 2, currentY, w - 4, animH_animation);

            // Toggle animation active state
            animationSwitch.resize(x, currentY, w, elementH);
            animationSwitch.render(ctx);

            ctx.batcher.unclip();
            currentY += animH_animation;
        }

        // ==========================================
        // 1. TRANSFORMACION COLLAPSIBLE HEADER
        // ==========================================
        boolean hoverTrans = ctx.mouseX >= x + 8 && ctx.mouseX < x + w - 8 && ctx.mouseY >= currentY && ctx.mouseY < currentY + headerH;
        int transBg = hoverTrans ? 0xFF2196F3 : 0xFF1976D2;
        ctx.batcher.box(x + 8, currentY, x + w - 8, currentY + headerH, transBg);
        ctx.batcher.icon(transformExpanded ? CalLightsIcons.ARROW_DOWN : CalLightsIcons.ARROW_RIGHT, x + 12, currentY + 1, 0xFFFFFFFF);
        ctx.batcher.icon(CalLightsIcons.MOVE_TO, x + 28, currentY + 1, 0xFFFFFFFF);
        ctx.batcher.text(CALKeys.TRANSFORM.get(), x + 46, currentY + 5, 0xFFFFFFFF);
        currentY += headerH + gap;

        int animH_trans = (int) transformAnimH;
        if (animH_trans > 0) {
            ctx.batcher.clip(x + 2, currentY, w - 4, animH_trans);
            
            int iconW = 16;
            ctx.batcher.icon(CalLightsIcons.ALL_DIRECTIONS, x + 10, currentY, 0xFFFFFFFF);
            int startX = x + 10 + iconW + 4;
            int colW = (w - 20 - iconW - 4 - 4) / 3;
            
            trackX.resize(startX, currentY, colW, elementH);
            trackX.setValue(light.x);
            trackX.render(ctx);

            trackY.resize(startX + colW + 2, currentY, colW, elementH);
            trackY.setValue(light.y);
            trackY.render(ctx);

            trackZ.resize(startX + (colW + 2) * 2, currentY, colW, elementH);
            trackZ.setValue(light.z);
            trackZ.render(ctx);

            ctx.batcher.unclip();
            currentY += animH_trans;
        }

        // ==========================================
        // 2. LUZ (LIGHT ADJUSTMENTS)
        // ==========================================
        boolean hoverLight = ctx.mouseX >= x + 8 && ctx.mouseX < x + w - 8 && ctx.mouseY >= currentY && ctx.mouseY < currentY + headerH;
        int lightBg = hoverLight ? 0xFF4CAF50 : 0xFF388E3C;
        ctx.batcher.box(x + 8, currentY, x + w - 8, currentY + headerH, lightBg);
        ctx.batcher.icon(lightExpanded ? CalLightsIcons.ARROW_DOWN : CalLightsIcons.ARROW_RIGHT, x + 12, currentY + 1, 0xFFFFFFFF);
        ctx.batcher.icon(CalLightsIcons.LIGHT, x + 28, currentY + 1, 0xFFFFFFFF);
        ctx.batcher.text(CALKeys.LIGHT_SETTINGS.get(), x + 46, currentY + 5, 0xFFFFFFFF);
        currentY += headerH + gap;

        int animH_light = (int) lightAnimH;
        if (animH_light > 0) {
            ctx.batcher.clip(x + 2, currentY, w - 4, animH_light);

            // Rename input bar
            int inputH = 16;
            int inputBg = nameFocused ? 0xFF0D0D11 : 0xFF1A1A22;
            ctx.batcher.box(x + 10, currentY, x + w - 10, currentY + inputH, inputBg);
            ctx.batcher.outline(x + 10, currentY, x + w - 10, currentY + inputH, nameFocused ? 0xFFFFAA00 : 0xFF3E3E4D, 1);
            
            // Draw placeholder or actual name text
            String nameText = (light.name == null) ? "" : light.name;
            if (nameText.isEmpty() && !nameFocused) {
                ctx.batcher.text(CALKeys.RENAME_PLACEHOLDER.get(), x + 16, currentY + 4, 0xFF666677);
            } else {
                ctx.batcher.text(nameText, x + 16, currentY + 4, 0xFFFFFFFF);
                
                // Draw separate blinking cursor box!
                if (nameFocused && System.currentTimeMillis() / 500 % 2 == 0) {
                    int subW = MinecraftClient.getInstance().textRenderer.getWidth(nameText.substring(0, renameCursorIdx));
                    int cursorX = x + 16 + subW;
                    int cursorY = currentY + 3;
                    ctx.batcher.box(cursorX, cursorY, cursorX + 1, cursorY + 10, 0xFFFFFFFF);
                }
            }
            
            int drawY = currentY + inputH + gap;

            // Type switching button row [ POINT ] [ SPOT ]
            int btnW = (w - 22) / 2;
            
            // POINT Button
            boolean hoverPoint = ctx.mouseX >= x + 10 && ctx.mouseX < x + 10 + btnW && ctx.mouseY >= drawY && ctx.mouseY < drawY + elementH;
            int pointBg = (!light.isSpot) ? 0xFF00796B : (hoverPoint ? 0xFF2A2A35 : 0xFF1C1C22);
            int pointBorder = (!light.isSpot) ? 0xFF00F0FF : 0xFF2D2D38;
            ctx.batcher.box(x + 10, drawY, x + 10 + btnW, drawY + elementH, pointBg);
            ctx.batcher.outline(x + 10, drawY, x + 10 + btnW, drawY + elementH, pointBorder, 1);
            int pTextW = MinecraftClient.getInstance().textRenderer.getWidth(CALKeys.TYPE_POINT.get());
            ctx.batcher.text(CALKeys.TYPE_POINT.get(), x + 10 + (btnW - pTextW) / 2, drawY + 4, 0xFFFFFFFF);

            // SPOT Button
            boolean hoverSpot = ctx.mouseX >= x + 10 + btnW + 2 && ctx.mouseX < x + w - 10 && ctx.mouseY >= drawY && ctx.mouseY < drawY + elementH;
            int spotBg = (light.isSpot) ? 0xFF00796B : (hoverSpot ? 0xFF2A2A35 : 0xFF1C1C22);
            int spotBorder = (light.isSpot) ? 0xFF00F0FF : 0xFF2D2D38;
            ctx.batcher.box(x + 10 + btnW + 2, drawY, x + w - 10, drawY + elementH, spotBg);
            ctx.batcher.outline(x + 10 + btnW + 2, drawY, x + w - 10, drawY + elementH, spotBorder, 1);
            int sTextW = MinecraftClient.getInstance().textRenderer.getWidth(CALKeys.TYPE_SPOT.get());
            ctx.batcher.text(CALKeys.TYPE_SPOT.get(), x + 10 + btnW + 2 + (btnW - sTextW) / 2, drawY + 4, 0xFFFFFFFF);

            drawY += elementH + gap;

            // Intensity Trackpad
            trackIntensity.resize(x + 10, drawY, w - 20, elementH);
            trackIntensity.setValue(light.intensity);
            trackIntensity.render(ctx);
            drawY += elementH + gap;

            // Color Picker
            colorPicker.resize(x + 10, drawY, w - 20, 110);
            colorPicker.setColors(light.r, light.g, light.b);
            colorPicker.render(ctx);

            ctx.batcher.unclip();
            currentY += animH_light;
        }

        // ==========================================
        // 3. SPECIFIC ADJUSTMENTS (POINT OR SPOT DETAILS)
        // ==========================================
        boolean hoverSpec = ctx.mouseX >= x + 8 && ctx.mouseX < x + w - 8 && ctx.mouseY >= currentY && ctx.mouseY < currentY + headerH;
        int specBg = hoverSpec ? 0xFFE65100 : 0xFFEF6C00;
        ctx.batcher.box(x + 8, currentY, x + w - 8, currentY + headerH, specBg);
        ctx.batcher.icon(specificExpanded ? CalLightsIcons.ARROW_DOWN : CalLightsIcons.ARROW_RIGHT, x + 12, currentY + 1, 0xFFFFFFFF);
        int lightCol = 0xFF000000 | ((int) (light.r * 255) << 16) | ((int) (light.g * 255) << 8) | (int) (light.b * 255);
        ctx.batcher.icon(light.isSpot ? CalLightsIcons.SPOT_LIGHT : CalLightsIcons.POINT_LIGHT, x + 28, currentY + 1, lightCol);
        String specTitle = light.isSpot ? CALKeys.PANEL_SPOT_SETTINGS.get() : CALKeys.PANEL_POINT_SETTINGS.get();
        ctx.batcher.text(specTitle, x + 46, currentY + 5, 0xFFFFFFFF);
        currentY += headerH + gap;

        int animH_spec = (int) specificAnimH;
        if (animH_spec > 0) {
            ctx.batcher.clip(x + 2, currentY, w - 4, animH_spec);

            if (!light.isSpot) {
                // Radius slider for POINT light
                trackRadius.resize(x + 10, currentY, w - 20, elementH);
                trackRadius.setValue(light.radius);
                trackRadius.render(ctx);
            } else {
                int iconW = 16;
                ctx.batcher.icon(CalLightsIcons.REFRESH, x + 10, currentY, 0xFFFFFFFF);
                int startX = x + 10 + iconW + 4;
                int colW = (w - 20 - iconW - 4 - 4) / 3;
                
                trackRX.resize(startX, currentY, colW, elementH);
                trackRX.setValue(light.rx);
                trackRX.render(ctx);

                trackRY.resize(startX + colW + 2, currentY, colW, elementH);
                trackRY.setValue(light.ry);
                trackRY.render(ctx);

                trackRZ.resize(startX + (colW + 2) * 2, currentY, colW, elementH);
                trackRZ.setValue(light.rz);
                trackRZ.render(ctx);

                int drawY = currentY + elementH + gap;

                // Inner Angle
                trackInner.resize(x + 10, drawY, w - 20, elementH);
                trackInner.setValue(light.innerAngle);
                trackInner.render(ctx);
                drawY += elementH + gap;

                // Outer Angle
                trackOuter.resize(x + 10, drawY, w - 20, elementH);
                trackOuter.setValue(light.outerAngle);
                trackOuter.render(ctx);
                drawY += elementH + gap;

                // Distance
                trackDistance.resize(x + 10, drawY, w - 20, elementH);
                trackDistance.setValue(light.distance);
                trackDistance.render(ctx);
            }

            ctx.batcher.unclip();
            currentY += animH_spec;
        }

        // ==========================================
        // 4. NIEBLA VOLUMETRICA (VOLUMETRIC FOG)
        // ==========================================
        boolean hoverFog = ctx.mouseX >= x + 8 && ctx.mouseX < x + w - 8 && ctx.mouseY >= currentY && ctx.mouseY < currentY + headerH;
        int fogBg = hoverFog ? 0xFF6D1B7B : 0xFF4A148C;
        ctx.batcher.box(x + 8, currentY, x + w - 8, currentY + headerH, fogBg);
        ctx.batcher.icon(fogExpanded ? CalLightsIcons.ARROW_DOWN : CalLightsIcons.ARROW_RIGHT, x + 12, currentY + 1, 0xFFFFFFFF);
        ctx.batcher.icon(CalLightsIcons.FADING, x + 28, currentY + 1, 0xFFFFFFFF);
        ctx.batcher.text(CALKeys.PANEL_VOLUMETRIC_FOG.get(), x + 46, currentY + 5, 0xFFFFFFFF);
        currentY += headerH + gap;

        int animH_fog = (int) fogAnimH;
        if (animH_fog > 0) {
            ctx.batcher.clip(x + 2, currentY, w - 4, animH_fog);

            // BBS-style Toggle Switch for Fog Enabled
            fogSwitch.resize(x, currentY, w, elementH);
            fogSwitch.render(ctx);
            
            int drawY = currentY + elementH + gap;

            if (light.fogEnabled) {
                // Dispersion
                trackFogDispersion.resize(x + 10, drawY, w - 20, elementH);
                trackFogDispersion.setValue(light.fogDispersion);
                trackFogDispersion.render(ctx);
                drawY += elementH + gap;

                // Density
                trackFogDensity.resize(x + 10, drawY, w - 20, elementH);
                trackFogDensity.setValue(light.fogDensity);
                trackFogDensity.render(ctx);
                drawY += elementH + gap;

                // Anisotropy
                trackFogAnisotropy.resize(x + 10, drawY, w - 20, elementH);
                trackFogAnisotropy.setValue(light.fogAnisotropy);
                trackFogAnisotropy.render(ctx);
            }

            ctx.batcher.unclip();
            currentY += animH_fog;
        }

        // ==========================================
        // 5. SOMBRAS (SHADOWS)
        // ==========================================
        boolean hoverShadow = ctx.mouseX >= x + 8 && ctx.mouseX < x + w - 8 && ctx.mouseY >= currentY && ctx.mouseY < currentY + headerH;
        int shadowBg = hoverShadow ? 0xFF1A237E : 0xFF0D1260;
        ctx.batcher.box(x + 8, currentY, x + w - 8, currentY + headerH, shadowBg);
        ctx.batcher.icon(shadowExpanded ? CalLightsIcons.ARROW_DOWN : CalLightsIcons.ARROW_RIGHT, x + 12, currentY + 1, 0xFFFFFFFF);
        ctx.batcher.icon(CalLightsIcons.FADING, x + 28, currentY + 1, 0xFF8888FF);
        ctx.batcher.text(CALKeys.PANEL_SHADOWS.get(), x + 46, currentY + 5, 0xFFFFFFFF);
        currentY += headerH + gap;

        int animH_shadow = (int) shadowAnimH;
        if (animH_shadow > 0) {
            ctx.batcher.clip(x + 2, currentY, w - 4, animH_shadow);

            // Toggle switch for Shadow Enabled
            shadowSwitch.resize(x, currentY, w, elementH);
            shadowSwitch.render(ctx);
            
            int drawY = currentY + elementH + gap;

            if (light.shadowEnabled) {
                // Softness
                trackShadowSoftness.resize(x + 10, drawY, w - 20, elementH);
                trackShadowSoftness.setValue(light.shadowSoftness);
                trackShadowSoftness.render(ctx);
                drawY += elementH + gap;

                // Intensity
                trackShadowIntensity.resize(x + 10, drawY, w - 20, elementH);
                trackShadowIntensity.setValue(light.shadowIntensity);
                trackShadowIntensity.render(ctx);
            }

            ctx.batcher.unclip();
            currentY += animH_shadow;
        }

        // ==========================================
        // 6. RIM LIGHT (CONTORNO FRESNEL)
        // ==========================================
        boolean hoverRim = ctx.mouseX >= x + 8 && ctx.mouseX < x + w - 8 && ctx.mouseY >= currentY && ctx.mouseY < currentY + headerH;
        int rimBg = hoverRim ? 0xFFBF360C : 0xFF8D3200;
        ctx.batcher.box(x + 8, currentY, x + w - 8, currentY + headerH, rimBg);
        ctx.batcher.icon(rimExpanded ? CalLightsIcons.ARROW_DOWN : CalLightsIcons.ARROW_RIGHT, x + 12, currentY + 1, 0xFFFFFFFF);
        ctx.batcher.icon(CalLightsIcons.FADING, x + 28, currentY + 1, 0xFFFF8C00);
        ctx.batcher.text(CALKeys.PANEL_RIM_LIGHT.get(), x + 46, currentY + 5, 0xFFFFFFFF);
        currentY += headerH + gap;

        int animH_rim = (int) rimAnimH;
        if (animH_rim > 0) {
            ctx.batcher.clip(x + 2, currentY, w - 4, animH_rim);

            rimSwitch.resize(x, currentY, w, elementH);
            rimSwitch.render(ctx);

            int drawY = currentY + elementH + gap;
            if (light.rimEnabled) {
                trackRimIntensity.resize(x + 10, drawY, w - 20, elementH);
                trackRimIntensity.setValue(light.rimIntensity);
                trackRimIntensity.render(ctx);
                drawY += elementH + gap;

                trackRimPower.resize(x + 10, drawY, w - 20, elementH);
                trackRimPower.setValue(light.rimPower);
                trackRimPower.render(ctx);
                drawY += elementH + gap;

                trackRimHardness.resize(x + 10, drawY, w - 20, elementH);
                trackRimHardness.setValue(light.rimHardness);
                trackRimHardness.render(ctx);
                drawY += elementH + gap;

                // Direction selector button row [ Atras ] [ Frente ] [ Ambos ]
                int btnW = (w - 24) / 3;

                // BACK Button
                boolean hoverBack = ctx.mouseX >= x + 10 && ctx.mouseX < x + 10 + btnW && ctx.mouseY >= drawY && ctx.mouseY < drawY + elementH;
                int backBg = (light.rimDirection == 0.0f) ? 0xFF00796B : (hoverBack ? 0xFF2A2A35 : 0xFF1C1C22);
                int backBorder = (light.rimDirection == 0.0f) ? 0xFF00F0FF : 0xFF2D2D38;
                ctx.batcher.box(x + 10, drawY, x + 10 + btnW, drawY + elementH, backBg);
                ctx.batcher.outline(x + 10, drawY, x + 10 + btnW, drawY + elementH, backBorder, 1);
                int bTextW = MinecraftClient.getInstance().textRenderer.getWidth(CALKeys.PROP_RIM_DIR_BACK.get());
                ctx.batcher.text(CALKeys.PROP_RIM_DIR_BACK.get(), x + 10 + (btnW - bTextW) / 2, drawY + 4, 0xFFFFFFFF);

                // FRONT Button
                boolean hoverFront = ctx.mouseX >= x + 10 + btnW + 2 && ctx.mouseX < x + 10 + (btnW * 2) + 2 && ctx.mouseY >= drawY && ctx.mouseY < drawY + elementH;
                int frontBg = (light.rimDirection == 1.0f) ? 0xFF00796B : (hoverFront ? 0xFF2A2A35 : 0xFF1C1C22);
                int frontBorder = (light.rimDirection == 1.0f) ? 0xFF00F0FF : 0xFF2D2D38;
                ctx.batcher.box(x + 10 + btnW + 2, drawY, x + 10 + (btnW * 2) + 2, drawY + elementH, frontBg);
                ctx.batcher.outline(x + 10 + btnW + 2, drawY, x + 10 + (btnW * 2) + 2, drawY + elementH, frontBorder, 1);
                int fTextW = MinecraftClient.getInstance().textRenderer.getWidth(CALKeys.PROP_RIM_DIR_FRONT.get());
                ctx.batcher.text(CALKeys.PROP_RIM_DIR_FRONT.get(), x + 10 + btnW + 2 + (btnW - fTextW) / 2, drawY + 4, 0xFFFFFFFF);

                // BOTH Button
                boolean hoverBoth = ctx.mouseX >= x + 10 + (btnW * 2) + 4 && ctx.mouseX < x + w - 10 && ctx.mouseY >= drawY && ctx.mouseY < drawY + elementH;
                int bothBg = (light.rimDirection == 2.0f) ? 0xFF00796B : (hoverBoth ? 0xFF2A2A35 : 0xFF1C1C22);
                int bothBorder = (light.rimDirection == 2.0f) ? 0xFF00F0FF : 0xFF2D2D38;
                ctx.batcher.box(x + 10 + (btnW * 2) + 4, drawY, x + w - 10, drawY + elementH, bothBg);
                ctx.batcher.outline(x + 10 + (btnW * 2) + 4, drawY, x + w - 10, drawY + elementH, bothBorder, 1);
                int boTextW = MinecraftClient.getInstance().textRenderer.getWidth(CALKeys.PROP_RIM_DIR_BOTH.get());
                ctx.batcher.text(CALKeys.PROP_RIM_DIR_BOTH.get(), x + 10 + (btnW * 2) + 4 + (btnW - boTextW) / 2, drawY + 4, 0xFFFFFFFF);
            }

            ctx.batcher.unclip();
            currentY += animH_rim;
        }

        // ==========================================
        // 6.5. OUTLINE (CONTOUR)
        // ==========================================
        boolean hoverOutline = ctx.mouseX >= x + 8 && ctx.mouseX < x + w - 8 && ctx.mouseY >= currentY && ctx.mouseY < currentY + headerH;
        int outlineBg = hoverOutline ? 0xFF5E35B1 : 0xFF311B92; // Deep purple theme
        ctx.batcher.box(x + 8, currentY, x + w - 8, currentY + headerH, outlineBg);
        ctx.batcher.icon(outlineExpanded ? CalLightsIcons.ARROW_DOWN : CalLightsIcons.ARROW_RIGHT, x + 12, currentY + 1, 0xFFFFFFFF);
        ctx.batcher.icon(CalLightsIcons.OUTLINE, x + 28, currentY + 1, 0xFFE040FB);
        ctx.batcher.text(CALKeys.PANEL_OUTLINE.get(), x + 46, currentY + 5, 0xFFFFFFFF);
        currentY += headerH + gap;

        int animH_outline = (int) outlineAnimH;
        if (animH_outline > 0) {
            ctx.batcher.clip(x + 2, currentY, w - 4, animH_outline);

            outlineSwitch.resize(x, currentY, w, elementH);
            outlineSwitch.render(ctx);

            int drawY = currentY + elementH + gap;
            if (light.outlineEnabled) {
                trackOutlineIntensity.resize(x + 10, drawY, w - 20, elementH);
                trackOutlineIntensity.setValue(light.outlineIntensity);
                trackOutlineIntensity.render(ctx);
                drawY += elementH + gap;

                trackOutlineThickness.resize(x + 10, drawY, w - 20, elementH);
                trackOutlineThickness.setValue(light.outlineThickness);
                trackOutlineThickness.render(ctx);
            }

            ctx.batcher.unclip();
            currentY += animH_outline;
        }

        // ==========================================
        // 7. DESTELLO (LENS FLARE)
        // ==========================================
        boolean hoverFlare = ctx.mouseX >= x + 8 && ctx.mouseX < x + w - 8 && ctx.mouseY >= currentY && ctx.mouseY < currentY + headerH;
        int flareBg = hoverFlare ? 0xFF00ACC1 : 0xFF00838F;
        ctx.batcher.box(x + 8, currentY, x + w - 8, currentY + headerH, flareBg);
        ctx.batcher.icon(flareExpanded ? CalLightsIcons.ARROW_DOWN : CalLightsIcons.ARROW_RIGHT, x + 12, currentY + 1, 0xFFFFFFFF);
        ctx.batcher.icon(CalLightsIcons.PARTICLE, x + 28, currentY + 1, 0xFFFFFFFF);
        ctx.batcher.text(CALKeys.PANEL_FLARE.get(), x + 46, currentY + 5, 0xFFFFFFFF);
        currentY += headerH + gap;

        int animH_flare = (int) flareAnimH;
        if (animH_flare > 0) {
            ctx.batcher.clip(x + 2, currentY, w - 4, animH_flare);

            // Toggle switch for Flare Enabled
            flareSwitch.resize(x, currentY, w, elementH);
            flareSwitch.render(ctx);
            
            int drawY = currentY + elementH + gap;

            if (light.flareEnabled) {
                // Glow Size
                trackFlareGlowSize.resize(x + 10, drawY, w - 20, elementH);
                trackFlareGlowSize.setValue(light.flareGlowSize);
                trackFlareGlowSize.render(ctx);
                drawY += elementH + gap;

                // Glow Intensity
                trackFlareGlowIntensity.resize(x + 10, drawY, w - 20, elementH);
                trackFlareGlowIntensity.setValue(light.flareGlowIntensity);
                trackFlareGlowIntensity.render(ctx);
                drawY += elementH + gap;

                // Ray 1 Length
                trackFlareRayLength.resize(x + 10, drawY, w - 20, elementH);
                trackFlareRayLength.setValue(light.flareRayLength);
                trackFlareRayLength.render(ctx);
                drawY += elementH + gap;

                // Ray 1 Thickness
                trackFlareRayThickness.resize(x + 10, drawY, w - 20, elementH);
                trackFlareRayThickness.setValue(light.flareRayThickness);
                trackFlareRayThickness.render(ctx);
                drawY += elementH + gap;

                // Ray 2 Length
                trackFlareRayLength2.resize(x + 10, drawY, w - 20, elementH);
                trackFlareRayLength2.setValue(light.flareRayLength2);
                trackFlareRayLength2.render(ctx);
                drawY += elementH + gap;

                // Ray 2 Thickness
                trackFlareRayThickness2.resize(x + 10, drawY, w - 20, elementH);
                trackFlareRayThickness2.setValue(light.flareRayThickness2);
                trackFlareRayThickness2.render(ctx);
                drawY += elementH + gap;

                // Ray 3 Length
                trackFlareRayLength3.resize(x + 10, drawY, w - 20, elementH);
                trackFlareRayLength3.setValue(light.flareRayLength3);
                trackFlareRayLength3.render(ctx);
                drawY += elementH + gap;

                // Ray 3 Thickness
                trackFlareRayThickness3.resize(x + 10, drawY, w - 20, elementH);
                trackFlareRayThickness3.setValue(light.flareRayThickness3);
                trackFlareRayThickness3.render(ctx);
                drawY += elementH + gap;

                // Rotation
                trackFlareRotation.resize(x + 10, drawY, w - 20, elementH);
                trackFlareRotation.setValue(light.flareRotation);
                trackFlareRotation.render(ctx);
                drawY += elementH + gap;

                // Start Angle
                trackFlareStartAngle.resize(x + 10, drawY, w - 20, elementH);
                trackFlareStartAngle.setValue(light.flareStartAngle);
                trackFlareStartAngle.render(ctx);
                drawY += elementH + gap;

                // End Angle
                trackFlareEndAngle.resize(x + 10, drawY, w - 20, elementH);
                trackFlareEndAngle.setValue(light.flareEndAngle);
                trackFlareEndAngle.render(ctx);
            }

            ctx.batcher.unclip();
            currentY += animH_flare;
        }

        // ==========================================
        // 7. GOBO (GOBO PROJECTION)
        // ==========================================
        if (light.isSpot) {
            boolean hoverGobo = ctx.mouseX >= x + 8 && ctx.mouseX < x + w - 8 && ctx.mouseY >= currentY && ctx.mouseY < currentY + headerH;
            int goboBg = hoverGobo ? 0xFF00796B : 0xFF004D40;
            ctx.batcher.box(x + 8, currentY, x + w - 8, currentY + headerH, goboBg);
            ctx.batcher.icon(goboExpanded ? CalLightsIcons.ARROW_DOWN : CalLightsIcons.ARROW_RIGHT, x + 12, currentY + 1, 0xFFFFFFFF);
            ctx.batcher.icon(CalLightsIcons.IMAGE, x + 28, currentY + 1, 0xFFFFFFFF);
            ctx.batcher.text(CALKeys.PANEL_GOBO.get(), x + 46, currentY + 5, 0xFFFFFFFF);
            currentY += headerH + gap;

            int animH_gobo = (int) goboAnimH;
            if (animH_gobo > 0) {
                ctx.batcher.clip(x + 2, currentY, w - 4, animH_gobo);

                // 1. Gobo selection box / dropdown button
                int drawY = currentY;
                boolean hoverSelect = ctx.mouseX >= x + 10 && ctx.mouseX < x + w - 10 && ctx.mouseY >= drawY && ctx.mouseY < drawY + elementH;
                int selectBg = hoverSelect ? 0xFF2A2A35 : 0xFF1C1C22;
                ctx.batcher.box(x + 10, drawY, x + w - 10, drawY + elementH, selectBg);
                ctx.batcher.outline(x + 10, drawY, x + w - 10, drawY + elementH, goboSelectorExpanded ? 0xFFFFAA00 : 0xFF2D2D38, 1);
                
                String labelText = CALKeys.PROP_GOBO_NAME.get() + ": " + light.goboName;
                ctx.batcher.text(labelText, x + 16, drawY + 4, 0xFFFFFFFF);
                
                // Draw small arrow indicator on the right side of the select button
                ctx.batcher.icon(goboSelectorExpanded ? CalLightsIcons.ARROW_DOWN : CalLightsIcons.ARROW_RIGHT, x + w - 24, drawY, 0xFFE0E0E0);
                drawY += elementH + gap;

                // 2. If dropdown is expanded, draw the list of gobos
                if (goboSelectorExpanded) {
                    List<String> names = new ArrayList<>();
                    names.add("None");
                    names.addAll(GoboManager.INSTANCE.getGoboNames());

                    for (String name : names) {
                        boolean hoverItem = ctx.mouseX >= x + 12 && ctx.mouseX < x + w - 12 && ctx.mouseY >= drawY && ctx.mouseY < drawY + elementH;
                        boolean isCurrent = name.equals(light.goboName);
                        int itemBg = isCurrent ? 0xFF00796B : (hoverItem ? 0xFF2A2A30 : 0xFF15151B);
                        ctx.batcher.box(x + 12, drawY, x + w - 12, drawY + elementH, itemBg);
                        ctx.batcher.outline(x + 12, drawY, x + w - 12, drawY + elementH, isCurrent ? 0xFF00F0FF : 0xFF222228, 1);
                        ctx.batcher.text(name, x + 18, drawY + 4, 0xFFFFFFFF);
                        drawY += elementH + gap;
                    }
                }

                // 3. Rotation Trackpad
                trackGoboRotation.resize(x + 10, drawY, w - 20, elementH);
                trackGoboRotation.setValue(light.goboRotation);
                trackGoboRotation.render(ctx);

                ctx.batcher.unclip();
                currentY += animH_gobo;
            }
        }

        // Restore standard drawing state at the very end
        ctx.batcher.unclip();
    }

    @Override
    public boolean mouseClicked(int mx, int my, int btn) {
        // Adjust coordinate context for mouse clicking based on scrolling offset
        int relativeMy = my - scrollY;

        int currentY = y + 25;
        int headerH = 18;
        int elementH = 16;
        int gap = 3;

        // 6. Animation accordion toggle (now at the very top!)
        if (mx >= x + 8 && mx < x + w - 8 && relativeMy >= currentY && relativeMy < currentY + headerH) {
            animationExpanded = !animationExpanded;
            return true;
        }
        currentY += headerH + gap;
        if (animationExpanded) {
            // Animation enabled toggle
            if (animationSwitch.mouseClicked(mx, my, btn)) return true;
        }
        currentY += (int) animationAnimH;

        // 1. Transform expanded toggle click
        if (mx >= x + 8 && mx < x + w - 8 && relativeMy >= currentY && relativeMy < currentY + headerH) {
            transformExpanded = !transformExpanded;
            return true;
        }
        currentY += headerH + gap;
        if (transformExpanded) {
            if (trackX.mouseClicked(mx, my, btn) || 
                trackY.mouseClicked(mx, my, btn) || 
                trackZ.mouseClicked(mx, my, btn)) {
                return true;
            }
        }
        currentY += (int) transformAnimH;

        // 2. Light expanded toggle click
        if (mx >= x + 8 && mx < x + w - 8 && relativeMy >= currentY && relativeMy < currentY + headerH) {
            lightExpanded = !lightExpanded;
            return true;
        }
        currentY += headerH + gap;
        if (lightExpanded) {
            // Name Input Click
            int inputH = 16;
            if (mx >= x + 10 && mx < x + w - 10 && relativeMy >= currentY && relativeMy < currentY + inputH) {
                nameFocused = true;
                if (light.name == null) light.name = "";
                renameCursorIdx = light.name.length();
                return true;
            } else {
                nameFocused = false;
            }
            int drawY = currentY + inputH + gap;

            int btnW = (w - 22) / 2;

            // POINT Button Click
            if (mx >= x + 10 && mx < x + 10 + btnW && relativeMy >= drawY && relativeMy < drawY + elementH) {
                if (light.isSpot) {
                    LightManager.INSTANCE.convertLightType(light.id, false);
                }
                return true;
            }
            // SPOT Button Click
            if (mx >= x + 10 + btnW + 2 && mx < x + w - 10 && relativeMy >= drawY && relativeMy < drawY + elementH) {
                if (!light.isSpot) {
                    LightManager.INSTANCE.convertLightType(light.id, true);
                }
                return true;
            }
            drawY += elementH + gap;

            // Intensity Slider
            if (trackIntensity.mouseClicked(mx, my, btn)) return true;
            drawY += elementH + gap;

            // Color Picker
            if (colorPicker.mouseClicked(mx, my, btn)) return true;
        }
        currentY += (int) lightAnimH;

        // 3. Specific properties expanded toggle click
        if (mx >= x + 8 && mx < x + w - 8 && relativeMy >= currentY && relativeMy < currentY + headerH) {
            specificExpanded = !specificExpanded;
            return true;
        }
        currentY += headerH + gap;
        if (specificExpanded) {
            if (!light.isSpot) {
                if (trackRadius.mouseClicked(mx, my, btn)) return true;
            } else {
                if (trackRX.mouseClicked(mx, my, btn) || 
                    trackRY.mouseClicked(mx, my, btn) || 
                    trackRZ.mouseClicked(mx, my, btn)) {
                    return true;
                }
                int drawY = currentY + elementH + gap;

                if (trackInner.mouseClicked(mx, my, btn)) return true;
                drawY += elementH + gap;

                if (trackOuter.mouseClicked(mx, my, btn)) return true;
                drawY += elementH + gap;

                if (trackDistance.mouseClicked(mx, my, btn)) return true;
            }
        }
        currentY += (int) specificAnimH;

        // 4. Volumetric fog expanded toggle click
        if (mx >= x + 8 && mx < x + w - 8 && relativeMy >= currentY && relativeMy < currentY + headerH) {
            fogExpanded = !fogExpanded;
            return true;
        }
        currentY += headerH + gap;
        if (fogExpanded) {
            // Checkbox click
            if (fogSwitch.mouseClicked(mx, my, btn)) return true;
            int drawY = currentY + elementH + gap;

            if (light.fogEnabled) {
                if (trackFogDispersion.mouseClicked(mx, my, btn)) return true;
                drawY += elementH + gap;

                if (trackFogDensity.mouseClicked(mx, my, btn)) return true;
                drawY += elementH + gap;

                if (trackFogAnisotropy.mouseClicked(mx, my, btn)) return true;
            }
        }
        currentY += (int) fogAnimH;

        // 5. Shadow expanded toggle click
        if (mx >= x + 8 && mx < x + w - 8 && relativeMy >= currentY && relativeMy < currentY + headerH) {
            shadowExpanded = !shadowExpanded;
            return true;
        }
        currentY += headerH + gap;
        if (shadowExpanded) {
            // Shadow enabled toggle click
            if (shadowSwitch.mouseClicked(mx, my, btn)) return true;
            int drawY = currentY + elementH + gap;

            if (light.shadowEnabled) {
                if (trackShadowSoftness.mouseClicked(mx, my, btn)) return true;
                drawY += elementH + gap;

                if (trackShadowIntensity.mouseClicked(mx, my, btn)) return true;
            }
        }
        currentY += (int) shadowAnimH;

        // 6. Rim light expanded toggle click
        if (mx >= x + 8 && mx < x + w - 8 && relativeMy >= currentY && relativeMy < currentY + headerH) {
            rimExpanded = !rimExpanded;
            return true;
        }
        currentY += headerH + gap;
        if (rimExpanded) {
            if (rimSwitch.mouseClicked(mx, my, btn)) return true;

            if (light.rimEnabled) {
                if (trackRimIntensity.mouseClicked(mx, my, btn)) return true;
                if (trackRimPower.mouseClicked(mx, my, btn)) return true;
                if (trackRimHardness.mouseClicked(mx, my, btn)) return true;

                int drawY = relativeMy - (currentY + elementH + gap);
                // Check if clicking in the fifth element row
                int clickY = currentY + elementH + gap + elementH + gap + elementH + gap + elementH + gap;
                if (relativeMy >= clickY && relativeMy < clickY + elementH) {
                    int btnW = (w - 24) / 3;
                    // BACK Button Click
                    if (mx >= x + 10 && mx < x + 10 + btnW) {
                        CALUndoManager.pushState();
                        light.rimDirection = 0.0f;
                        if (onUpdate != null) onUpdate.run();
                        return true;
                    }
                    // FRONT Button Click
                    if (mx >= x + 10 + btnW + 2 && mx < x + 10 + (btnW * 2) + 2) {
                        CALUndoManager.pushState();
                        light.rimDirection = 1.0f;
                        if (onUpdate != null) onUpdate.run();
                        return true;
                    }
                    // BOTH Button Click
                    if (mx >= x + 10 + (btnW * 2) + 4 && mx < x + w - 10) {
                        CALUndoManager.pushState();
                        light.rimDirection = 2.0f;
                        if (onUpdate != null) onUpdate.run();
                        return true;
                    }
                }
            }
        }
        currentY += (int) rimAnimH;

        // 6.5. Outline expanded toggle click
        if (mx >= x + 8 && mx < x + w - 8 && relativeMy >= currentY && relativeMy < currentY + headerH) {
            outlineExpanded = !outlineExpanded;
            return true;
        }
        currentY += headerH + gap;
        if (outlineExpanded) {
            if (outlineSwitch.mouseClicked(mx, my, btn)) return true;

            if (light.outlineEnabled) {
                if (trackOutlineIntensity.mouseClicked(mx, my, btn)) return true;
                if (trackOutlineThickness.mouseClicked(mx, my, btn)) return true;
            }
        }
        currentY += (int) outlineAnimH;

        // 7. Flare expanded toggle click
        if (mx >= x + 8 && mx < x + w - 8 && relativeMy >= currentY && relativeMy < currentY + headerH) {
            flareExpanded = !flareExpanded;
            return true;
        }
        currentY += headerH + gap;
        if (flareExpanded) {
            // Flare enabled toggle click
            if (flareSwitch.mouseClicked(mx, my, btn)) return true;
            int drawY = currentY + elementH + gap;

            if (light.flareEnabled) {
                if (trackFlareGlowSize.mouseClicked(mx, my, btn)) return true;
                drawY += elementH + gap;
                if (trackFlareGlowIntensity.mouseClicked(mx, my, btn)) return true;
                drawY += elementH + gap;
                if (trackFlareRayLength.mouseClicked(mx, my, btn)) return true;
                drawY += elementH + gap;
                if (trackFlareRayThickness.mouseClicked(mx, my, btn)) return true;
                drawY += elementH + gap;
                if (trackFlareRayLength2.mouseClicked(mx, my, btn)) return true;
                drawY += elementH + gap;
                if (trackFlareRayThickness2.mouseClicked(mx, my, btn)) return true;
                drawY += elementH + gap;
                if (trackFlareRayLength3.mouseClicked(mx, my, btn)) return true;
                drawY += elementH + gap;
                if (trackFlareRayThickness3.mouseClicked(mx, my, btn)) return true;
                drawY += elementH + gap;
                if (trackFlareRotation.mouseClicked(mx, my, btn)) return true;
                drawY += elementH + gap;
                if (trackFlareStartAngle.mouseClicked(mx, my, btn)) return true;
                drawY += elementH + gap;
                if (trackFlareEndAngle.mouseClicked(mx, my, btn)) return true;
            }
        }
        currentY += (int) flareAnimH;

        // 7. Gobo expanded toggle click
        if (light.isSpot) {
            if (mx >= x + 8 && mx < x + w - 8 && relativeMy >= currentY && relativeMy < currentY + headerH) {
                goboExpanded = !goboExpanded;
                return true;
            }
            currentY += headerH + gap;
            if (goboExpanded) {
                int drawY = currentY;

                // Click select button
                if (mx >= x + 10 && mx < x + w - 10 && relativeMy >= drawY && relativeMy < drawY + elementH) {
                    goboSelectorExpanded = !goboSelectorExpanded;
                    return true;
                }
                drawY += elementH + gap;

                // Click list items
                if (goboSelectorExpanded) {
                    List<String> names = new ArrayList<>();
                    names.add("None");
                    names.addAll(GoboManager.INSTANCE.getGoboNames());

                    for (String name : names) {
                        if (mx >= x + 12 && mx < x + w - 12 && relativeMy >= drawY && relativeMy < drawY + elementH) {
                            light.goboName = name;
                            goboSelectorExpanded = false;
                            if (onUpdate != null) onUpdate.run();
                            return true;
                        }
                        drawY += elementH + gap;
                    }
                }

                // Click rotation trackpad
                if (trackGoboRotation.mouseClicked(mx, my, btn)) return true;
            }
            currentY += (int) goboAnimH;
        }

        return false;
    }

    @Override
    public boolean mouseReleased(int mx, int my, int btn) {
        return trackX.mouseReleased(mx, my, btn) ||
               trackY.mouseReleased(mx, my, btn) ||
               trackZ.mouseReleased(mx, my, btn) ||
               trackRX.mouseReleased(mx, my, btn) ||
               trackRY.mouseReleased(mx, my, btn) ||
               trackRZ.mouseReleased(mx, my, btn) ||
               trackIntensity.mouseReleased(mx, my, btn) ||
               colorPicker.mouseReleased(mx, my, btn) ||
               trackRadius.mouseReleased(mx, my, btn) ||
               trackInner.mouseReleased(mx, my, btn) ||
               trackOuter.mouseReleased(mx, my, btn) ||
               trackDistance.mouseReleased(mx, my, btn) ||
               trackFogDispersion.mouseReleased(mx, my, btn) ||
               trackFogDensity.mouseReleased(mx, my, btn) ||
               trackFogAnisotropy.mouseReleased(mx, my, btn) ||
               trackShadowSoftness.mouseReleased(mx, my, btn) ||
               trackShadowIntensity.mouseReleased(mx, my, btn) ||
               trackRimIntensity.mouseReleased(mx, my, btn) ||
               trackRimPower.mouseReleased(mx, my, btn) ||
               trackRimHardness.mouseReleased(mx, my, btn) ||
               trackOutlineIntensity.mouseReleased(mx, my, btn) ||
               trackOutlineThickness.mouseReleased(mx, my, btn) ||
               trackFlareGlowSize.mouseReleased(mx, my, btn) ||
               trackFlareGlowIntensity.mouseReleased(mx, my, btn) ||
               trackFlareRayLength.mouseReleased(mx, my, btn) ||
               trackFlareRayThickness.mouseReleased(mx, my, btn) ||
               trackFlareRayLength2.mouseReleased(mx, my, btn) ||
               trackFlareRayThickness2.mouseReleased(mx, my, btn) ||
               trackFlareRayLength3.mouseReleased(mx, my, btn) ||
               trackFlareRayThickness3.mouseReleased(mx, my, btn) ||
               trackFlareRotation.mouseReleased(mx, my, btn) ||
               trackFlareStartAngle.mouseReleased(mx, my, btn) ||
               trackFlareEndAngle.mouseReleased(mx, my, btn) ||
               trackGoboRotation.mouseReleased(mx, my, btn) ||
               animationPanel.mouseReleased(mx, my, btn);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        return trackX.mouseDragged(mx, my, btn, dx, dy) ||
               trackY.mouseDragged(mx, my, btn, dx, dy) ||
               trackZ.mouseDragged(mx, my, btn, dx, dy) ||
               trackRX.mouseDragged(mx, my, btn, dx, dy) ||
               trackRY.mouseDragged(mx, my, btn, dx, dy) ||
               trackRZ.mouseDragged(mx, my, btn, dx, dy) ||
               trackIntensity.mouseDragged(mx, my, btn, dx, dy) ||
               colorPicker.mouseDragged(mx, my, btn, dx, dy) ||
               trackRadius.mouseDragged(mx, my, btn, dx, dy) ||
               trackInner.mouseDragged(mx, my, btn, dx, dy) ||
               trackOuter.mouseDragged(mx, my, btn, dx, dy) ||
               trackDistance.mouseDragged(mx, my, btn, dx, dy) ||
               trackFogDispersion.mouseDragged(mx, my, btn, dx, dy) ||
               trackFogDensity.mouseDragged(mx, my, btn, dx, dy) ||
               trackFogAnisotropy.mouseDragged(mx, my, btn, dx, dy) ||
               trackShadowSoftness.mouseDragged(mx, my, btn, dx, dy) ||
               trackShadowIntensity.mouseDragged(mx, my, btn, dx, dy) ||
               trackRimIntensity.mouseDragged(mx, my, btn, dx, dy) ||
               trackRimPower.mouseDragged(mx, my, btn, dx, dy) ||
               trackRimHardness.mouseDragged(mx, my, btn, dx, dy) ||
               trackOutlineIntensity.mouseDragged(mx, my, btn, dx, dy) ||
               trackOutlineThickness.mouseDragged(mx, my, btn, dx, dy) ||
               trackFlareGlowSize.mouseDragged(mx, my, btn, dx, dy) ||
               trackFlareGlowIntensity.mouseDragged(mx, my, btn, dx, dy) ||
               trackFlareRayLength.mouseDragged(mx, my, btn, dx, dy) ||
               trackFlareRayThickness.mouseDragged(mx, my, btn, dx, dy) ||
               trackFlareRayLength2.mouseDragged(mx, my, btn, dx, dy) ||
               trackFlareRayThickness2.mouseDragged(mx, my, btn, dx, dy) ||
               trackFlareRayLength3.mouseDragged(mx, my, btn, dx, dy) ||
               trackFlareRayThickness3.mouseDragged(mx, my, btn, dx, dy) ||
               trackFlareRotation.mouseDragged(mx, my, btn, dx, dy) ||
               trackFlareStartAngle.mouseDragged(mx, my, btn, dx, dy) ||
               trackFlareEndAngle.mouseDragged(mx, my, btn, dx, dy) ||
               trackGoboRotation.mouseDragged(mx, my, btn, dx, dy) ||
               animationPanel.mouseDragged(mx, my, btn, dx, dy);
    }

    @Override
    public boolean keyPressed(int key, int scan, int action) {
        if (nameFocused) {
            if (key == GLFW.GLFW_KEY_LEFT) {
                if (renameCursorIdx > 0) renameCursorIdx--;
                return true;
            } else if (key == GLFW.GLFW_KEY_RIGHT) {
                if (renameCursorIdx < light.name.length()) renameCursorIdx++;
                return true;
            } else if (key == GLFW.GLFW_KEY_BACKSPACE) {
                if (renameCursorIdx > 0) {
                    light.name = light.name.substring(0, renameCursorIdx - 1) + light.name.substring(renameCursorIdx);
                    renameCursorIdx--;
                }
                return true;
            } else if (key == GLFW.GLFW_KEY_DELETE) {
                if (renameCursorIdx < light.name.length()) {
                    light.name = light.name.substring(0, renameCursorIdx) + light.name.substring(renameCursorIdx + 1);
                }
                return true;
            } else if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_ESCAPE) {
                nameFocused = false;
                return true;
            }
            return true;
        }
        if (CLUITrackpad.activeEditingTrackpad != null) {
            if (CLUITrackpad.activeEditingTrackpad.handleKeyPressed(key, scan, action)) {
                return true;
            }
        }
        if (lightExpanded && colorPicker != null && colorPicker.keyPressed(key, scan, action)) {
            return true;
        }
        return false;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (nameFocused) {
            if (chr >= 32 && chr < 127) {
                if (light.name == null) light.name = "";
                light.name = light.name.substring(0, renameCursorIdx) + chr + light.name.substring(renameCursorIdx);
                renameCursorIdx++;
            }
            return true;
        }
        if (CLUITrackpad.activeEditingTrackpad != null) {
            if (CLUITrackpad.activeEditingTrackpad.handleCharTyped(chr, modifiers)) {
                return true;
            }
        }
        if (lightExpanded && colorPicker != null && colorPicker.charTyped(chr, modifiers)) {
            return true;
        }
        return false;
    }

    private int interpolateColor(int c1, int c2, float t) {
        int a1 = (c1 >> 24) & 0xFF;
        int r1 = (c1 >> 16) & 0xFF;
        int g1 = (c1 >> 8) & 0xFF;
        int b1 = c1 & 0xFF;

        int a2 = (c2 >> 24) & 0xFF;
        int r2 = (c2 >> 16) & 0xFF;
        int g2 = (c2 >> 8) & 0xFF;
        int b2 = c2 & 0xFF;

        int a = (int) (a1 + (a2 - a1) * t);
        int r = (int) (r1 + (r2 - r1) * t);
        int g = (int) (g1 + (g2 - g1) * t);
        int b = (int) (b1 + (b2 - b1) * t);

        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    // Convert RX, RY, RZ in degrees to (dx, dy, dz) vector by rotating (0, 0, -1)
    private static float[] getDirFromRotXYZ(float rx, float ry, float rz) {
        float radX = (float) Math.toRadians(rx);
        float radY = (float) Math.toRadians(ry);
        float radZ = (float) Math.toRadians(rz);

        float x = 0f;
        float y = 0f;
        float z = -1f;

        // 1. Rotate around X axis (radX)
        float cosX = (float) Math.cos(radX);
        float sinX = (float) Math.sin(radX);
        float y1 = y * cosX - z * sinX;
        float z1 = y * sinX + z * cosX;
        float x1 = x;

        // 2. Rotate around Y axis (radY)
        float cosY = (float) Math.cos(radY);
        float sinY = (float) Math.sin(radY);
        float x2 = x1 * cosY + z1 * sinY;
        float y2 = y1;
        float z2 = -x1 * sinY + z1 * cosY;

        // 3. Rotate around Z axis (radZ)
        float cosZ = (float) Math.cos(radZ);
        float sinZ = (float) Math.sin(radZ);
        float x3 = x2 * cosZ - y2 * sinZ;
        float y3 = x2 * sinZ + y2 * cosZ;
        float z3 = z2;

        return new float[] { x3, y3, z3 };
    }
}
