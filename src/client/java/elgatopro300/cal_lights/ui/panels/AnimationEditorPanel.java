package elgatopro300.cal_lights.ui.panels;

import elgatopro300.cal_lights.animation.LightAnimation;
import elgatopro300.cal_lights.animation.LightAnimationTrack;
import elgatopro300.cal_lights.animation.LightInterpolation;
import elgatopro300.cal_lights.animation.LightKeyframe;
import elgatopro300.cal_lights.graphics.CLBatcher;
import elgatopro300.cal_lights.graphics.CLIcon;
import elgatopro300.cal_lights.graphics.CalLightsIcons;
import elgatopro300.cal_lights.manager.CALUndoManager;
import elgatopro300.cal_lights.manager.LightInstance;
import elgatopro300.cal_lights.ui.CALEditorScreen;
import elgatopro300.cal_lights.ui.CALKeys;
import elgatopro300.cal_lights.ui.CLUIContext;
import elgatopro300.cal_lights.ui.CLUIElement;
import elgatopro300.cal_lights.ui.CalSettings;
import elgatopro300.cal_lights.ui.elements.CLUIColorPicker;
import elgatopro300.cal_lights.ui.elements.CLUITrackpad;

import net.minecraft.client.Minecraft;

import com.mojang.blaze3d.platform.InputConstants;

import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Premium BBS-style Timeline Animation Editor.
 * Shows a horizontal track timeline grid with a playhead at the bottom of the screen.
 */
public class AnimationEditorPanel extends CLUIElement {

    private final LightInstance light;
    private final Runnable onUpdate;

    private int selectedTrack = -1;
    private int selectedKeyframe = -1;
    private boolean draggingKeyframe = false;
    private boolean draggingPlayhead = false;
    private boolean showEasingDropdown = false;
    private int easingScrollY = 0;
    private int timelineScrollY = 0;

    // Selection and Drag Select Box
    public final Set<LightKeyframe> selectedKeyframes = new HashSet<>();
    private final Set<LightKeyframe> initialSelection = new HashSet<>();
    private final Map<LightKeyframe, Float> dragStartTicks = new HashMap<>();
    private boolean isSelecting = false;
    private int selectStartX, selectStartY;
    private int selectEndX, selectEndY;
    private float dragStartX;
    private boolean hasPushedUndoForDrag = false;

    // Clipboard for keyframes
    public static class CopiedKeyframe {
        public String property;
        public float relativeTick;
        public float value;
        public Float vecY;
        public Float vecZ;
        public LightInterpolation interp;
    }
    public static final List<CopiedKeyframe> keyframeClipboard = new ArrayList<>();

    // Trackpads for editing keyframes/tracks
    private final CLUITrackpad kfTickPad;
    private final CLUITrackpad kfValuePad;
    private final CLUITrackpad kfValueYPad;
    private final CLUITrackpad kfValueZPad;
    private final CLUITrackpad trackDurationPad;
    private CLUIColorPicker kfColorPicker;

    public AnimationEditorPanel(LightInstance light, Runnable onUpdate) {
        this.light = light;
        this.onUpdate = onUpdate;

        kfTickPad = new CLUITrackpad(CALKeys.TIME_LABEL, 0, 0, 60000, val -> {
            LightKeyframe kf = getSelectedKeyframe();
            if (kf != null) {
                kf.tick = parseTimeToMs(val);
                sortKeyframesOfSelectedTrack();
                if (onUpdate != null) onUpdate.run();
            }
        });

        kfValuePad = new CLUITrackpad("X", 0, -1000f, 1000f, val -> {
            LightKeyframe kf = getSelectedKeyframe();
            if (kf != null) {
                kf.value = val;
                if (onUpdate != null) onUpdate.run();
            }
        });

        kfValueYPad = new CLUITrackpad("Y", 0, -1000f, 1000f, val -> {
            LightKeyframe kf = getSelectedKeyframe();
            if (kf != null) {
                kf.vecY = val;
                if (onUpdate != null) onUpdate.run();
            }
        });

        kfValueZPad = new CLUITrackpad("Z", 0, -1000f, 1000f, val -> {
            LightKeyframe kf = getSelectedKeyframe();
            if (kf != null) {
                kf.vecZ = val;
                if (onUpdate != null) onUpdate.run();
            }
        });

        trackDurationPad = new CLUITrackpad(CALKeys.DURATION_LABEL, 2000, 100, 60000, val -> {
            LightAnimationTrack tr = getSelectedTrackObj();
            if (tr != null) {
                tr.duration = parseTimeToMs(val);
                if (onUpdate != null) onUpdate.run();
            }
        });

        kfColorPicker = new CLUIColorPicker(1f, 1f, 1f, () -> {
            LightKeyframe kf = getSelectedKeyframe();
            if (kf != null) {
                kf.value = kfColorPicker.getR();
                kf.vecY = kfColorPicker.getG();
                kf.vecZ = kfColorPicker.getB();
                if (onUpdate != null) onUpdate.run();
            }
        });
    }

    private LightAnimation getOrCreateAnimation() {
        if (light.animation == null) {
            light.animation = new LightAnimation();
        }
        return light.animation;
    }

    private LightAnimationTrack getSelectedTrackObj() {
        if (light.animation == null) return null;
        List<LightAnimationTrack> tracks = light.animation.tracks;
        if (selectedTrack < 0 || selectedTrack >= tracks.size()) return null;
        return tracks.get(selectedTrack);
    }

    private LightKeyframe getSelectedKeyframe() {
        LightAnimationTrack tr = getSelectedTrackObj();
        if (tr == null) return null;
        if (selectedKeyframe < 0 || selectedKeyframe >= tr.keyframes.size()) return null;
        return tr.keyframes.get(selectedKeyframe);
    }

    private void sortKeyframesOfSelectedTrack() {
        LightAnimationTrack tr = getSelectedTrackObj();
        if (tr == null) return;
        LightKeyframe currentSel = getSelectedKeyframe();
        tr.keyframes.sort((a, b) -> Float.compare(a.tick, b.tick));
        if (currentSel != null) {
            selectedKeyframe = tr.keyframes.indexOf(currentSel);
        }
    }

    private void drawDiamond(CLBatcher batcher, int cx, int cy, int color) {
        batcher.box(cx - 3, cy, cx + 4, cy + 1, color);
        batcher.box(cx, cy - 3, cx + 1, cy + 4, color);
        batcher.box(cx - 2, cy - 1, cx + 3, cy + 2, color);
        batcher.box(cx - 1, cy - 2, cx + 2, cy + 3, color);
    }

    private void drawCurvePreview(CLBatcher batcher, int px, int py, int pw, int ph, LightInterpolation interp) {
        batcher.box(px, py, px + pw, py + ph, 0xFF141418);
        batcher.outline(px, py, px + pw, py + ph, 0xFF444455, 1);
        
        // Draw grid lines
        batcher.box(px + pw / 2, py, px + pw / 2 + 1, py + ph, 0x1AFFFFFF);
        batcher.box(px, py + ph / 2, px + pw, py + ph / 2 + 1, 0x1AFFFFFF);
        
        // Plot points
        int prevX = -1, prevY = -1;
        int steps = 30;
        int padding = 6;
        for (int i = 0; i <= steps; i++) {
            float factor = (float) i / steps;
            float val = (float) LightAnimationTrack.calculateEasing(interp, factor);
            
            int ptX = px + padding + (int) (factor * (pw - padding * 2));
            int ptY = py + ph - padding - (int) (val * (ph - padding * 2));
            
            if (prevX != -1) {
                batcher.box(prevX, Math.min(prevY, ptY), ptX + 1, Math.max(prevY, ptY) + 1, 0xFF00E5FF);
            }
            prevX = ptX;
            prevY = ptY;
        }
    }

    public static CLIcon getInterpIcon(LightInterpolation interp) {
        if (interp == null) return CalLightsIcons.INTERP_LINEAR;
        return switch (interp) {
            case STEP -> CalLightsIcons.INTERP_STEP;
            case LINEAR -> CalLightsIcons.INTERP_LINEAR;
            case CONSTANT -> CalLightsIcons.INTERP_CONST;
            case SINE_IN -> CalLightsIcons.INTERP_SINE_IN;
            case SINE_OUT -> CalLightsIcons.INTERP_SINE_OUT;
            case SINE_INOUT -> CalLightsIcons.INTERP_SINE_INOUT;
            case CIRCLE_IN -> CalLightsIcons.INTERP_CIRCLE_IN;
            case CIRCLE_OUT -> CalLightsIcons.INTERP_CIRCLE_OUT;
            case CIRCLE_INOUT -> CalLightsIcons.INTERP_CIRCLE_INOUT;
            case QUAD_IN -> CalLightsIcons.INTERP_QUAD_IN;
            case QUAD_OUT -> CalLightsIcons.INTERP_QUAD_OUT;
            case QUAD_INOUT -> CalLightsIcons.INTERP_QUAD_INOUT;
            case CUBIC_IN -> CalLightsIcons.INTERP_CUBIC_IN;
            case CUBIC_OUT -> CalLightsIcons.INTERP_CUBIC_OUT;
            case CUBIC_INOUT -> CalLightsIcons.INTERP_CUBIC_INOUT;
            case QUART_IN -> CalLightsIcons.INTERP_QUART_IN;
            case QUART_OUT -> CalLightsIcons.INTERP_QUART_OUT;
            case QUART_INOUT -> CalLightsIcons.INTERP_QUART_INOUT;
            case QUINT_IN -> CalLightsIcons.INTERP_QUINT_IN;
            case QUINT_OUT -> CalLightsIcons.INTERP_QUINT_OUT;
            case QUINT_INOUT -> CalLightsIcons.INTERP_QUINT_INOUT;
            case EXP_IN -> CalLightsIcons.INTERP_EXP_IN;
            case EXP_OUT -> CalLightsIcons.INTERP_EXP_OUT;
            case EXP_INOUT -> CalLightsIcons.INTERP_EXP_INOUT;
            case BACK_IN -> CalLightsIcons.INTERP_BACK_IN;
            case BACK_OUT -> CalLightsIcons.INTERP_BACK_OUT;
            case BACK_INOUT -> CalLightsIcons.INTERP_BACK_INOUT;
            case ELASTIC_IN -> CalLightsIcons.INTERP_ELASTIC_IN;
            case ELASTIC_OUT -> CalLightsIcons.INTERP_ELASTIC_OUT;
            case ELASTIC_INOUT -> CalLightsIcons.INTERP_ELASTIC_INOUT;
            case BOUNCE_IN -> CalLightsIcons.INTERP_BOUNCE_IN;
            case BOUNCE_OUT -> CalLightsIcons.INTERP_BOUNCE_OUT;
            case BOUNCE_INOUT -> CalLightsIcons.INTERP_BOUNCE_INOUT;
        };
    }

    public static CLIcon getTrackIcon(String property) {
        if (property == null) return CalLightsIcons.HELP;
        return switch (property) {
            case "transform" -> CalLightsIcons.MOVE_TO;
            case "rotation" -> CalLightsIcons.ORBIT;
            case "color" -> CalLightsIcons.COLOR;
            case "intensity" -> CalLightsIcons.LIGHT;
            case "radius" -> CalLightsIcons.SPHERE;
            case "angle", "soft" -> CalLightsIcons.ARC;
            case "distance" -> CalLightsIcons.LINE;
            case "fogEnabled", "beamStrength", "vlDensity", "anisotropy" -> CalLightsIcons.FADING;
            case "shadowEnabled", "bulbSize" -> CalLightsIcons.FADING;
            default -> CalLightsIcons.HELP;
        };
    }

    public static String getPropertyLabel(String property) {
        if (property == null) return "";
        return switch (property) {
            case "transform" -> CALKeys.TRANSFORM.get();
            case "rotation" -> CALKeys.DIRECTION.get();
            case "color" -> CALKeys.COLOR.get();
            case "intensity" -> CALKeys.PROP_INTENSITY_LABEL.get();
            case "radius" -> CALKeys.PROP_RADIUS_LABEL.get();
            case "angle" -> CALKeys.PROP_OUTER_ANGLE_LABEL.get();
            case "soft" -> CALKeys.PROP_SOFT.get();
            case "distance" -> CALKeys.PROP_DISTANCE_LABEL.get();
            case "shadowEnabled" -> CALKeys.PANEL_SHADOW_ENABLED.get();
            case "bulbSize" -> CALKeys.PROP_SHADOW_SOFTNESS_LABEL.get();
            case "fogEnabled" -> CALKeys.PANEL_FOG_ENABLED.get();
            case "vlDensity" -> CALKeys.PROP_FOG_DENSITY_LABEL.get();
            case "beamStrength" -> CALKeys.PROP_FOG_DISPERSION_LABEL.get();
            case "anisotropy" -> CALKeys.PROP_FOG_ANISOTROPY_LABEL.get();
            default -> property;
        };
    }

    public static int getTrackColor(String property) {
        if (property == null) return 0xFFFFFFFF;
        if (property.startsWith("shadow")) return 0xFF8888FF;
        if (property.startsWith("fog")) return 0xFFCCCCCC;
        return switch (property) {
            case "transform" -> 0xFF42A5F5;
            case "rotation" -> 0xFFAB47BC;
            case "color" -> 0xFFFFCA28;
            case "intensity" -> 0xFFFF7043;
            default -> 0xFFE0E0E0;
        };
    }

    private void drawMiniCurve(CLBatcher batcher, int cx, int cy, int size, LightInterpolation interp, int color, boolean isSelected) {
        int cellBg = isSelected ? 0xFF2B2B36 : 0xFF141418;
        int borderCol = isSelected ? 0xFFFFAA00 : 0xFF2D2D38;
        batcher.box(cx, cy, cx + size, cy + size, cellBg);
        batcher.outline(cx, cy, cx + size, cy + size, borderCol, 1);
        
        CLIcon icon = getInterpIcon(interp);
        if (icon != null) {
            batcher.icon(icon, cx + (size - 16) / 2, cy + (size - 16) / 2, color);
        }
    }

    public static boolean isTicksMode() {
        return CalSettings.INSTANCE.durationMode == null || CalSettings.INSTANCE.durationMode.equals("ticks");
    }

    public static String formatTime(float ms) {
        if (isTicksMode()) {
            return String.format("%.1ft", ms / 50f);
        } else {
            return String.format("%.2fs", ms / 1000f);
        }
    }

    public static float parseTimeToMs(float visualValue) {
        if (isTicksMode()) {
            return visualValue * 50f;
        } else {
            return visualValue * 1000f;
        }
    }

    public static float msToVisual(float ms) {
        if (isTicksMode()) {
            return ms / 50f;
        } else {
            return ms / 1000f;
        }
    }

    @Override
    public void render(CLUIContext ctx) {
        LightAnimation anim = getOrCreateAnimation();
        // Synchronize fixed tracks list automatically based on Point vs Spot
        anim.synchronizeTracks(light.isSpot);

        // 1. Draw overall panel backing (Glassmorphic dark slate)
        ctx.batcher.box(x, y, x + w, y + h, 0xFF121216);
        ctx.batcher.outline(x, y, x + w, y + h, 0xFF22222A, 1);

        // Define columns
        int c1W = 150;
        int c3W = 180;
        int c2W = w - c1W - c3W;

        int x_c1 = x;
        int x_c2 = x + c1W;
        int x_c3 = x + w - c3W;

        // ── COLUMN 1: CONTROLS & TRACK LIST ────────────────────────────────
        ctx.batcher.box(x_c1, y, x_c1 + c1W, y + h, 0xFF0E0E12);
        ctx.batcher.outline(x_c1, y, x_c1 + c1W, y + h, 0xFF1C1C24, 1);
        ctx.batcher.icon(CalLightsIcons.FILM, x_c1 + 10, y + 4, 0xFFFFAA00);
        ctx.batcher.text(CALKeys.ANIMATIONS.get().toUpperCase(), x_c1 + 28, y + 6, 0xFFFFAA00);

        // Play / Pause button at the top of Column 1
        boolean hoverPlay = ctx.mouseX >= x_c1 + 8 && ctx.mouseX < x_c1 + c1W - 8
                            && ctx.mouseY >= y + 18 && ctx.mouseY < y + 34;
        int playBg = anim.isPlaying ? 0xFFC62828 : 0xFF1B5E20;
        if (hoverPlay) {
            playBg = anim.isPlaying ? 0xFFD32F2F : 0xFF2E7D32;
        }
        ctx.batcher.box(x_c1 + 8, y + 18, x_c1 + c1W - 8, y + 34, playBg);
        ctx.batcher.outline(x_c1 + 8, y + 18, x_c1 + c1W - 8, y + 34, anim.isPlaying ? 0xFFEF5350 : 0xFF81C784, 1);
        ctx.batcher.icon(anim.isPlaying ? CalLightsIcons.PAUSE : CalLightsIcons.PLAY, x_c1 + 12, y + 20, 0xFFFFFFFF);
        ctx.batcher.text(anim.isPlaying ? CALKeys.PAUSE.get() : CALKeys.PLAY.get(), x_c1 + 32, y + 23, 0xFFFFFFFF);

        // Draw track rows (Fixed: No Añadir Track button, no row delete button)
        List<LightAnimationTrack> tracks = anim.tracks;
        int listStartY = y + 38;
        int rowH = 18;

        ctx.batcher.clip(x_c1 + 6, y + 38, c1W - 12, h - 40);
        for (int i = 0; i < tracks.size(); i++) {
            LightAnimationTrack tr = tracks.get(i);
            int rowY = listStartY + i * rowH - timelineScrollY;

            boolean isSel = (i == selectedTrack);
            boolean hoverRow = ctx.mouseX >= x_c1 + 6 && ctx.mouseX < x_c1 + c1W - 6
                               && ctx.mouseY >= rowY && ctx.mouseY < rowY + rowH - 2
                               && ctx.mouseY >= y + 74 && ctx.mouseY < y + h;

            int rowBg = isSel ? 0xFF2B2B36 : (hoverRow ? 0xFF1F1F26 : 0xFF16161C);
            ctx.batcher.box(x_c1 + 6, rowY, x_c1 + c1W - 6, rowY + rowH - 2, rowBg);
            ctx.batcher.outline(x_c1 + 6, rowY, x_c1 + c1W - 6, rowY + rowH - 2, isSel ? 0xFFFFAA00 : 0xFF2D2D38, 1);
            CLIcon tIcon = getTrackIcon(tr.property);
            int tColor = getTrackColor(tr.property);
            ctx.batcher.icon(tIcon, x_c1 + 10, rowY - 1, tColor);
            ctx.batcher.text(getPropertyLabel(tr.property), x_c1 + 28, rowY + 3, 0xFFE0E0E0);
        }
        ctx.batcher.unclip();

        // ── COLUMN 2: TIMELINE GRID ────────────────────────────────────────
        ctx.batcher.box(x_c2, y, x_c2 + c2W, y + h, 0xFF17171E);
        ctx.batcher.outline(x_c2, y, x_c2 + c2W, y + h, 0xFF22222A, 1);

        float timelineLen = (getSelectedTrackObj() != null) ? getSelectedTrackObj().duration : 2000f;

        // Draw Time Ruler (Header)
        ctx.batcher.box(x_c2, y, x_c2 + c2W, y + 16, 0xFF111116);
        ctx.batcher.outline(x_c2, y, x_c2 + c2W, y + 16, 0xFF1C1C24, 1);

        // Ruler ticks and labels
        int numTicks = 5;
        for (int t = 0; t < numTicks; t++) {
            float tVal = (timelineLen / (numTicks - 1)) * t;
            int tickX = x_c2 + 10 + (int) ((tVal / timelineLen) * (c2W - 20));
            ctx.batcher.box(tickX, y + 10, tickX + 1, y + 16, 0xFF555566);
            ctx.batcher.text(formatTime(tVal), tickX - 10, y + 1, 0xFF888899);

            // Vertical grid lines down the grid
            if (t > 0 && t < numTicks - 1) {
                ctx.batcher.box(tickX, y + 16, tickX + 1, y + h, 0x18FFFFFF);
            }
        }

        ctx.batcher.clip(x_c2, y + 16, c2W, h - 18);

        // Draw horizontal grid lines and keyframes
        for (int i = 0; i < tracks.size(); i++) {
            LightAnimationTrack tr = tracks.get(i);
            int rowY = listStartY + i * rowH - timelineScrollY;

            int yCenter = rowY + (rowH - 2) / 2;

            // Draw horizontal track line
            ctx.batcher.box(x_c2 + 6, yCenter, x_c2 + c2W - 6, yCenter + 1, 0xFF2D2D38);

            // Draw keyframes as diamonds
            for (int k = 0; k < tr.keyframes.size(); k++) {
                LightKeyframe kf = tr.keyframes.get(k);
                if (kf.tick > timelineLen) continue;

                int kfX = x_c2 + 10 + (int) ((kf.tick / timelineLen) * (c2W - 20));

                boolean isKfSel = selectedKeyframes.contains(kf);
                boolean hoverKf = ctx.mouseX >= kfX - 4 && ctx.mouseX <= kfX + 4
                                  && ctx.mouseY >= yCenter - 4 && ctx.mouseY <= yCenter + 4
                                  && ctx.mouseY >= y + 16 && ctx.mouseY < y + h;

                boolean isCtrlPressed = isCtrlDown();
                int kfCol = isKfSel ? 0xFFFFAA00 : (hoverKf ? (isCtrlPressed ? 0xFFFF0000 : 0xFFFFFFFF) : 0xFF00E5FF);
                drawDiamond(ctx.batcher, kfX, yCenter, kfCol);
            }
        }

        // Render visual Ctrl pulsing preview diamond if Ctrl is held!
        boolean isCtrlPressed = isCtrlDown();
        if (isCtrlPressed && ctx.mouseX >= x_c2 + 10 && ctx.mouseX <= x_c2 + c2W - 10 && ctx.mouseY >= y + 16 && ctx.mouseY < y + h) {
            int hoveredTrackIdx = (ctx.mouseY - listStartY + timelineScrollY) / rowH;
            if (hoveredTrackIdx >= 0 && hoveredTrackIdx < tracks.size()) {
                float localMouseX = (float) (ctx.mouseX - (x_c2 + 10));
                float hoverTime = (localMouseX / (c2W - 20)) * timelineLen;
                if (!isShiftDown()) {
                    hoverTime = Math.round(hoverTime / 50f) * 50f;
                }
                hoverTime = Math.max(0f, Math.min(timelineLen, hoverTime));

                boolean nearExisting = false;
                LightAnimationTrack tr = tracks.get(hoveredTrackIdx);
                int kfX = x_c2 + 10 + (int) ((hoverTime / timelineLen) * (c2W - 20));
                int rowY = listStartY + hoveredTrackIdx * rowH - timelineScrollY;
                int yCenter = rowY + (rowH - 2) / 2;

                for (LightKeyframe kf : tr.keyframes) {
                    int exKfX = x_c2 + 10 + (int) ((kf.tick / timelineLen) * (c2W - 20));
                    if (Math.abs(ctx.mouseX - exKfX) <= 6 && Math.abs(ctx.mouseY - yCenter) <= 6) {
                        nearExisting = true;
                        break;
                    }
                }

                if (!nearExisting) {
                    float a = (float) Math.sin(ctx.tick / 2.0) * 0.15f + 0.6f;
                    int pulseColor = ((int)(a * 255f) << 24) | 0xFFFFFF;
                    drawDiamond(ctx.batcher, kfX, yCenter, pulseColor);
                }
            }
        }

        // Draw real-time Playhead (based on anim.playbackTimeMs!)
        float playheadTime = anim.playbackTimeMs % Math.max(timelineLen, 1f);
        int playheadX = x_c2 + 10 + (int) ((playheadTime / timelineLen) * (c2W - 20));

        if (playheadX >= x_c2 + 10 && playheadX <= x_c2 + c2W - 10) {
            // Vertical playhead line
            ctx.batcher.box(playheadX, y + 16, playheadX + 1, y + h, 0xFF00E676);
            // Playhead handle arrow/box at ruler top
            ctx.batcher.box(playheadX - 3, y + 11, playheadX + 4, y + 16, 0xFF00E676);
        }

        ctx.batcher.unclip();

        // ── COLUMN 3: KEYFRAME / TRACK EDITOR INSPECTOR ────────────────────
        ctx.batcher.box(x_c3, y, x_c3 + c3W, y + h, 0xFF111115);
        ctx.batcher.outline(x_c3, y, x_c3 + c3W, y + h, 0xFF22222A, 1);
        ctx.batcher.icon(CalLightsIcons.EDITOR, x_c3 + 10, y + 4, 0xFFFFAA00);
        ctx.batcher.text(CALKeys.EDITOR.get(), x_c3 + 28, y + 6, 0xFFFFAA00);

        LightKeyframe selectedKfObj = getSelectedKeyframe();
        LightAnimationTrack selectedTrackObj = getSelectedTrackObj();

        if (selectedKfObj != null) {
            if (selectedTrackObj.property.equals("color")) {
                // Combined Easing Icon and Time on row 1
                int interpBtnX = x_c3 + 8;
                int interpBtnW = 20;
                int interpBtnY = y + 18;
                int interpBtnH = 14;

                boolean hoverInterp = ctx.mouseX >= interpBtnX && ctx.mouseX < interpBtnX + interpBtnW
                                   && ctx.mouseY >= interpBtnY && ctx.mouseY < interpBtnY + interpBtnH;
                ctx.batcher.box(interpBtnX, interpBtnY, interpBtnX + interpBtnW, interpBtnY + interpBtnH, hoverInterp ? 0xFF2A2A35 : 0xFF1E1E24);
                ctx.batcher.outline(interpBtnX, interpBtnY, interpBtnX + interpBtnW, interpBtnY + interpBtnH, 0xFF3E3E4D, 1);
                CLIcon icon = getInterpIcon(selectedKfObj.interp);
                if (icon != null) {
                    ctx.batcher.icon(icon, interpBtnX + (interpBtnW - 16) / 2, interpBtnY + (interpBtnH - 16) / 2, 0xFFFFAA00);
                }

                kfTickPad.updateConfig("", 0f, msToVisual(selectedTrackObj.duration));
                kfTickPad.setValue(msToVisual(selectedKfObj.tick));
                kfTickPad.resize(x_c3 + 32, y + 18, c3W - 40, 14);
                kfTickPad.render(ctx);

                kfColorPicker.resize(x_c3 + 8, y + 36, c3W - 16, 68);
                kfColorPicker.setColors(selectedKfObj.value, selectedKfObj.vecY != null ? selectedKfObj.vecY : 1f, selectedKfObj.vecZ != null ? selectedKfObj.vecZ : 1f);
                kfColorPicker.render(ctx);
            } else {
                boolean is3D = selectedTrackObj.property.equals("transform") 
                            || selectedTrackObj.property.equals("rotation");

                if (is3D) {
                    // Configure labels and limits for transform / rotation
                    String labelX, labelY, labelZ;
                    float minV, maxV;
                    if (selectedTrackObj.property.equals("rotation")) {
                        labelX = "RX"; labelY = "RY"; labelZ = "RZ";
                        minV = -360f; maxV = 360f;
                    } else {
                        labelX = "X"; labelY = "Y"; labelZ = "Z";
                        minV = -1000f; maxV = 1000f;
                    }

                    // Combined Easing Icon and Time on row 1
                    int interpBtnX = x_c3 + 8;
                    int interpBtnW = 20;
                    int interpBtnY = y + 18;
                    int interpBtnH = 14;

                    boolean hoverInterp = ctx.mouseX >= interpBtnX && ctx.mouseX < interpBtnX + interpBtnW
                                       && ctx.mouseY >= interpBtnY && ctx.mouseY < interpBtnY + interpBtnH;
                    ctx.batcher.box(interpBtnX, interpBtnY, interpBtnX + interpBtnW, interpBtnY + interpBtnH, hoverInterp ? 0xFF2A2A35 : 0xFF1E1E24);
                    ctx.batcher.outline(interpBtnX, interpBtnY, interpBtnX + interpBtnW, interpBtnY + interpBtnH, 0xFF3E3E4D, 1);
                    CLIcon icon = getInterpIcon(selectedKfObj.interp);
                    if (icon != null) {
                        ctx.batcher.icon(icon, interpBtnX + (interpBtnW - 16) / 2, interpBtnY + (interpBtnH - 16) / 2, 0xFFFFAA00);
                    }

                    kfTickPad.updateConfig("", 0f, msToVisual(selectedTrackObj.duration));
                    kfTickPad.setValue(msToVisual(selectedKfObj.tick));
                    kfTickPad.resize(x_c3 + 32, y + 18, c3W - 40, 14);
                    kfTickPad.render(ctx);

                    kfValuePad.updateConfig(labelX, minV, maxV);
                    kfValuePad.setValue(selectedKfObj.value);
                    kfValuePad.resize(x_c3 + 8, y + 34, c3W - 16, 14);
                    kfValuePad.render(ctx);

                    kfValueYPad.updateConfig(labelY, minV, maxV);
                    kfValueYPad.setValue(selectedKfObj.vecY != null ? selectedKfObj.vecY : 0f);
                    kfValueYPad.resize(x_c3 + 8, y + 50, c3W - 16, 14);
                    kfValueYPad.render(ctx);

                    kfValueZPad.updateConfig(labelZ, minV, maxV);
                    kfValueZPad.setValue(selectedKfObj.vecZ != null ? selectedKfObj.vecZ : 0f);
                    kfValueZPad.resize(x_c3 + 8, y + 66, c3W - 16, 14);
                    kfValueZPad.render(ctx);
                } else if (selectedTrackObj.property.equals("shadowEnabled") || selectedTrackObj.property.equals("fogEnabled")) {
                    // Combined Easing Icon and Time on row 1
                    int interpBtnX = x_c3 + 8;
                    int interpBtnW = 20;
                    int interpBtnY = y + 20;
                    int interpBtnH = 16;

                    boolean hoverInterp = ctx.mouseX >= interpBtnX && ctx.mouseX < interpBtnX + interpBtnW
                                       && ctx.mouseY >= interpBtnY && ctx.mouseY < interpBtnY + interpBtnH;
                    ctx.batcher.box(interpBtnX, interpBtnY, interpBtnX + interpBtnW, interpBtnY + interpBtnH, hoverInterp ? 0xFF2A2A35 : 0xFF1E1E24);
                    ctx.batcher.outline(interpBtnX, interpBtnY, interpBtnX + interpBtnW, interpBtnY + interpBtnH, 0xFF3E3E4D, 1);
                    CLIcon icon = getInterpIcon(selectedKfObj.interp);
                    if (icon != null) {
                        ctx.batcher.icon(icon, interpBtnX + (interpBtnW - 16) / 2, interpBtnY + (interpBtnH - 16) / 2, 0xFFFFAA00);
                    }

                    kfTickPad.updateConfig("", 0f, msToVisual(selectedTrackObj.duration));
                    kfTickPad.setValue(msToVisual(selectedKfObj.tick));
                    kfTickPad.resize(x_c3 + 32, y + 20, c3W - 40, 16);
                    kfTickPad.render(ctx);

                    int toggleY = y + 40;
                    boolean isEnabled = selectedKfObj.value >= 0.5f;
                    boolean hoverToggle = ctx.mouseX >= x_c3 + 8 && ctx.mouseX < x_c3 + c3W - 8
                                       && ctx.mouseY >= toggleY && ctx.mouseY < toggleY + 16;
                    
                    int bgCol = isEnabled ? 0xFF1B5E20 : (hoverToggle ? 0xFF3A3A4A : 0xFF212126);
                    int borderCol = isEnabled ? 0xFF81C784 : 0xFF3E3E4D;
                    
                    ctx.batcher.box(x_c3 + 8, toggleY, x_c3 + c3W - 8, toggleY + 16, bgCol);
                    ctx.batcher.outline(x_c3 + 8, toggleY, x_c3 + c3W - 8, toggleY + 16, borderCol, 1);
                    
                    ctx.batcher.icon(isEnabled ? CalLightsIcons.VISIBLE : CalLightsIcons.INVISIBLE, x_c3 + 12, toggleY, 0xFFFFFFFF);
                    ctx.batcher.text(isEnabled ? CALKeys.STATUS_ACTIVE.get() : CALKeys.STATUS_INACTIVE.get(), x_c3 + 32, toggleY + 4, 0xFFFFFFFF);
                } else {
                    // Combined Easing Icon and Time on row 1
                    int interpBtnX = x_c3 + 8;
                    int interpBtnW = 20;
                    int interpBtnY = y + 20;
                    int interpBtnH = 16;

                    boolean hoverInterp = ctx.mouseX >= interpBtnX && ctx.mouseX < interpBtnX + interpBtnW
                                       && ctx.mouseY >= interpBtnY && ctx.mouseY < interpBtnY + interpBtnH;
                    ctx.batcher.box(interpBtnX, interpBtnY, interpBtnX + interpBtnW, interpBtnY + interpBtnH, hoverInterp ? 0xFF2A2A35 : 0xFF1E1E24);
                    ctx.batcher.outline(interpBtnX, interpBtnY, interpBtnX + interpBtnW, interpBtnY + interpBtnH, 0xFF3E3E4D, 1);
                    CLIcon icon = getInterpIcon(selectedKfObj.interp);
                    if (icon != null) {
                        ctx.batcher.icon(icon, interpBtnX + (interpBtnW - 16) / 2, interpBtnY + (interpBtnH - 16) / 2, 0xFFFFAA00);
                    }

                    kfTickPad.updateConfig("", 0f, msToVisual(selectedTrackObj.duration));
                    kfTickPad.setValue(msToVisual(selectedKfObj.tick));
                    kfTickPad.resize(x_c3 + 32, y + 20, c3W - 40, 16);
                    kfTickPad.render(ctx);

                    float minVal = -1000f;
                    float maxVal = 1000f;
                    String label = "Valor";

                    label = getPropertyLabel(selectedTrackObj.property);
                    switch (selectedTrackObj.property) {
                        case "intensity" -> { minVal = 0.0f; maxVal = 20.0f; }
                        case "radius" -> { minVal = 0.1f; maxVal = 64.0f; }
                        case "angle" -> { minVal = 1.0f; maxVal = 179.0f; }
                        case "soft" -> { minVal = 0.0f; maxVal = 60.0f; }
                        case "distance" -> { minVal = 0.1f; maxVal = 128.0f; }
                        case "bulbSize" -> { minVal = 0.0f; maxVal = 2.0f; }
                        case "vlDensity" -> { minVal = 0.005f; maxVal = 0.5f; }
                        case "beamStrength" -> { minVal = 0.0f; maxVal = 5.0f; }
                        case "anisotropy" -> { minVal = -0.95f; maxVal = 0.95f; }
                    }

                    kfValuePad.updateConfig(label, minVal, maxVal);
                    kfValuePad.setValue(selectedKfObj.value);
                    kfValuePad.resize(x_c3 + 8, y + 38, c3W - 16, 16);
                    kfValuePad.render(ctx);
                }
            }
        } else if (selectedTrackObj != null) {
            // TRACK IS SELECTED BUT NO KEYFRAME
            trackDurationPad.updateConfig(isTicksMode() ? CALKeys.DURATION_TICKS.get() : CALKeys.DURATION_SECS.get(), msToVisual(100f), msToVisual(60000f));
            trackDurationPad.setValue(msToVisual(selectedTrackObj.duration));
            trackDurationPad.resize(x_c3 + 8, y + 20, c3W - 16, 16);
            trackDurationPad.render(ctx);

            // Looping mode toggle button
            int loopY = y + 38;
            boolean hoverLoop = ctx.mouseX >= x_c3 + 8 && ctx.mouseX < x_c3 + c3W - 8
                                && ctx.mouseY >= loopY && ctx.mouseY < loopY + 16;
            ctx.batcher.box(x_c3 + 8, loopY, x_c3 + c3W - 8, loopY + 16, selectedTrackObj.looping ? 0xFF00796B : (hoverLoop ? 0xFF3A3A4A : 0xFF212126));
            ctx.batcher.outline(x_c3 + 8, loopY, x_c3 + c3W - 8, loopY + 16, selectedTrackObj.looping ? 0xFF00F0FF : 0xFF3E3E4D, 1);
            ctx.batcher.icon(CalLightsIcons.REFRESH, x_c3 + 12, loopY, 0xFFFFFFFF);
            ctx.batcher.text(selectedTrackObj.looping ? CALKeys.LOOP_ACTIVE.get() : CALKeys.LOOP_DISABLED.get(), x_c3 + 32, loopY + 4, 0xFFFFFFFF);

            // Add Keyframe help text (BBS style: Ctrl+Click timeline to add!)
            ctx.batcher.text(CALKeys.CTRL_CLICK_TIMELINE_1.get(), x_c3 + 14, y + 66, 0xFF777788);
            ctx.batcher.text(CALKeys.CTRL_CLICK_TIMELINE_2.get(), x_c3 + 14, y + 78, 0xFF777788);
            ctx.batcher.text(CALKeys.CTRL_CLICK_TIMELINE_3.get(), x_c3 + 14, y + 90, 0xFF777788);

        } else {
            // NOTHING SELECTED
            ctx.batcher.text(CALKeys.SELECT_TRACK_1.get(), x_c3 + 14, y + 36, 0xFF777788);
            ctx.batcher.text(CALKeys.SELECT_TRACK_2.get(), x_c3 + 14, y + 48, 0xFF777788);
            ctx.batcher.text(CALKeys.SELECT_TRACK_3.get(), x_c3 + 14, y + 60, 0xFF777788);
        }

        // ── UPWARD VISUAL EASING GRID OVERLAY ────────────────────────────────
        if (showEasingDropdown && selectedKfObj != null) {
            LightInterpolation[] allInterps = LightInterpolation.values();
            int dropW = c3W - 16;
            int dropH = 220;
            int dropX = x_c3 + 8;
            int dropY = y - dropH - 4; // Floating above the editor panel

            // Draw backing card
            ctx.batcher.box(dropX, dropY, dropX + dropW, dropY + dropH, 0xFF141418);
            ctx.batcher.outline(dropX, dropY, dropX + dropW, dropY + dropH, 0xFFFFAA00, 1);

            // Determine hovered cell
            int cellSize = 22;
            int spacing = 3;
            int gridStartX = dropX + 8;
            int gridStartY = dropY + 28;
            int hoveredCellIdx = -1;

            for (int i = 0; i < allInterps.length; i++) {
                int col = i % 6;
                int row = i / 6;
                int cellX = gridStartX + col * (cellSize + spacing);
                int cellY = gridStartY + row * (cellSize + spacing);

                if (ctx.mouseX >= cellX && ctx.mouseX < cellX + cellSize &&
                    ctx.mouseY >= cellY && ctx.mouseY < cellY + cellSize) {
                    hoveredCellIdx = i;
                    break;
                }
            }

            LightInterpolation activeInterp = selectedKfObj.interp == null ? LightInterpolation.LINEAR : selectedKfObj.interp;
            LightInterpolation displayInterp = hoveredCellIdx != -1 ? allInterps[hoveredCellIdx] : activeInterp;

            // Draw Easing Name Header inside card
            ctx.batcher.text(displayInterp.name(), dropX + 10, dropY + 8, 0xFFFFAA00);

            // Draw visual mini-curve cells
            for (int i = 0; i < allInterps.length; i++) {
                int col = i % 6;
                int row = i / 6;
                int cellX = gridStartX + col * (cellSize + spacing);
                int cellY = gridStartY + row * (cellSize + spacing);

                LightInterpolation interp = allInterps[i];
                boolean isCurrent = activeInterp == interp;
                boolean isHovered = hoveredCellIdx == i;

                int color = isCurrent ? 0xFFFFAA00 : (isHovered ? 0xFFFFFFFF : 0xFF00E5FF);
                drawMiniCurve(ctx.batcher, cellX, cellY, cellSize, interp, color, isCurrent);
            }

            // High-fidelity floating large curve preview graph card on the left side!
            drawCurvePreview(ctx.batcher, dropX - 110, dropY + 8, 100, 100, displayInterp);
        }

        // Draw selection box overlay
        if (isSelecting) {
            int x1 = Math.min(selectStartX, selectEndX);
            int x2 = Math.max(selectStartX, selectEndX);
            int y1 = Math.min(selectStartY, selectEndY);
            int y2 = Math.max(selectStartY, selectEndY);
            ctx.batcher.box(x1, y1, x2, y2, 0x33FFAA00);
            ctx.batcher.outline(x1, y1, x2, y2, 0xFFFFAA00, 1);
        }
    }

    @Override
    public boolean mouseClicked(int mx, int my, int btn) {
        LightAnimation anim = getOrCreateAnimation();

        int c1W = 150;
        int c3W = 180;
        int c2W = w - c1W - c3W;

        int x_c1 = x;
        int x_c2 = x + c1W;
        int x_c3 = x + w - c3W;

        LightKeyframe selectedKfObj = getSelectedKeyframe();
        LightAnimationTrack selectedTrackObj = getSelectedTrackObj();

        // ── EASING DROPDOWN OVERLAY INTERACTION ───────────────────────────
        if (showEasingDropdown && selectedKfObj != null) {
            LightInterpolation[] allInterps = LightInterpolation.values();
            int dropW = c3W - 16;
            int dropH = 220;
            int dropX = x_c3 + 8;
            int dropY = y - dropH - 4;

            int cellSize = 22;
            int spacing = 3;
            int gridStartX = dropX + 8;
            int gridStartY = dropY + 28;

            if (mx >= dropX && mx < dropX + dropW && my >= dropY && my < dropY + dropH) {
                // Find clicked cell
                for (int i = 0; i < allInterps.length; i++) {
                    int col = i % 6;
                    int row = i / 6;
                    int cellX = gridStartX + col * (cellSize + spacing);
                    int cellY = gridStartY + row * (cellSize + spacing);

                    if (mx >= cellX && mx < cellX + cellSize && my >= cellY && my < cellY + cellSize) {
                        CALUndoManager.pushState();
                        selectedKfObj.interp = allInterps[i];
                        showEasingDropdown = false;
                        if (onUpdate != null) onUpdate.run();
                        break;
                    }
                }
                return true;
            }
            showEasingDropdown = false;
            return true;
        }

        // --- COLUMN 1 CLICKS ---
        if (mx >= x_c1 && mx < x_c1 + c1W) {
            // Play/Pause button click
            if (mx >= x_c1 + 8 && mx < x_c1 + c1W - 8 && my >= y + 18 && my < y + 34) {
                anim.isPlaying = !anim.isPlaying;
                if (onUpdate != null) onUpdate.run();
                return true;
            }

            // Track list rows selection
            int listStartY = y + 38;
            int rowH = 18;
            for (int i = 0; i < anim.tracks.size(); i++) {
                int rowY = listStartY + i * rowH - timelineScrollY;
                if (rowY + rowH > y + h) break;

                if (mx >= x_c1 + 6 && mx < x_c1 + c1W - 6 && my >= rowY && my < rowY + rowH - 2) {
                    selectedTrack = i;
                    selectedKeyframe = -1;
                    showEasingDropdown = false;
                    return true;
                }
            }
            return true;
        }

        // --- COLUMN 2 CLICKS (Scrubbing / Grid keys) ---
        if (mx >= x_c2 && mx < x_c2 + c2W) {
            float timelineLen = (getSelectedTrackObj() != null) ? getSelectedTrackObj().duration : 2000f;

            // 1. Playhead scrubbing when clicking the ruler area (top 16px of grid)
            if (my >= y && my < y + 16) {
                float localMouseX = (float) (mx - (x_c2 + 10));
                float clickTime = (localMouseX / (c2W - 20)) * timelineLen;
                anim.playbackTimeMs = (long) Math.max(0f, Math.min(timelineLen, clickTime));
                draggingPlayhead = true;
                showEasingDropdown = false;
                if (onUpdate != null) onUpdate.run();
                return true;
            }

            // 2. Select keyframe clicks
            int listStartY = y + 38;
            int rowH = 18;
            for (int i = 0; i < anim.tracks.size(); i++) {
                LightAnimationTrack tr = anim.tracks.get(i);
                int rowY = listStartY + i * rowH - timelineScrollY;
                int yCenter = rowY + (rowH - 2) / 2;

                for (int k = 0; k < tr.keyframes.size(); k++) {
                    LightKeyframe kf = tr.keyframes.get(k);
                    int kfX = x_c2 + 10 + (int) ((kf.tick / timelineLen) * (c2W - 20));

                    if (Math.abs(mx - kfX) <= 6 && Math.abs(my - yCenter) <= 6) {
                        boolean shiftOrCtrl = isShiftDown() || isCtrlDown();
                        if (shiftOrCtrl) {
                            if (selectedKeyframes.contains(kf)) {
                                selectedKeyframes.remove(kf);
                            } else {
                                selectedKeyframes.add(kf);
                            }
                        } else {
                            if (!selectedKeyframes.contains(kf)) {
                                selectedKeyframes.clear();
                                selectedKeyframes.add(kf);
                            }
                        }

                        selectedTrack = i;
                        selectedKeyframe = k;
                        draggingKeyframe = true;
                        hasPushedUndoForDrag = false;
                        dragStartX = mx;
                        dragStartTicks.clear();
                        for (LightKeyframe kfSel : selectedKeyframes) {
                            dragStartTicks.put(kfSel, kfSel.tick);
                        }

                        showEasingDropdown = false;
                        if (onUpdate != null) onUpdate.run();
                        return true;
                    }
                }
            }

            // Clicked empty area in grid - select track row or create keyframe if Ctrl is held!
            for (int i = 0; i < anim.tracks.size(); i++) {
                LightAnimationTrack tr = anim.tracks.get(i);
                int rowY = listStartY + i * rowH - timelineScrollY;
                if (my >= rowY && my < rowY + rowH) {
                    if (isCtrlDown()) {
                        CALUndoManager.pushState();
                        float localMouseX = (float) (mx - (x_c2 + 10));
                        float clickTime = (localMouseX / (c2W - 20)) * timelineLen;
                        if (!isShiftDown()) {
                            clickTime = Math.round(clickTime / 50f) * 50f;
                        }
                        clickTime = Math.max(0f, Math.min(timelineLen, clickTime));

                        LightKeyframe newKf = new LightKeyframe(clickTime, 0f);
                        if (tr.property.equals("transform") || tr.property.equals("rotation") || tr.property.equals("color")) {
                            newKf.vecY = 0f;
                            newKf.vecZ = 0f;
                            if (tr.property.equals("color")) {
                                newKf.value = 1f;
                                newKf.vecY = 1f;
                                newKf.vecZ = 1f;
                            }
                        }
                        tr.keyframes.add(newKf);
                        selectedTrack = i;
                        selectedKeyframe = tr.keyframes.size() - 1;
                        selectedKeyframes.clear();
                        selectedKeyframes.add(newKf);
                        sortKeyframesOfSelectedTrack();
                        if (onUpdate != null) onUpdate.run();
                        return true;
                    } else {
                        selectedTrack = i;
                        selectedKeyframe = -1;

                        // Drag selection box initialization
                        isSelecting = true;
                        selectStartX = mx;
                        selectStartY = my;
                        selectEndX = mx;
                        selectEndY = my;
                        initialSelection.clear();
                        if (isShiftDown() || isCtrlDown()) {
                            initialSelection.addAll(selectedKeyframes);
                        } else {
                            selectedKeyframes.clear();
                        }

                        showEasingDropdown = false;
                        if (onUpdate != null) onUpdate.run();
                        return true;
                    }
                }
            }
            return true;
        }

        // --- COLUMN 3 CLICKS (Inspector Controls) ---
        if (mx >= x_c3 && mx < x_c3 + c3W) {
            LightKeyframe kf = getSelectedKeyframe();
            LightAnimationTrack tr = getSelectedTrackObj();

            if (kf != null) {
                boolean is3D = tr.property.equals("transform") || tr.property.equals("rotation");
                if (tr.property.equals("color")) {
                    if (kfTickPad.mouseClicked(mx, my, btn)) return true;
                    if (my >= y + 36 && my < y + 104) {
                        if (kfColorPicker.mouseClicked(mx, my, btn)) return true;
                    }

                    int interpBtnX = x_c3 + 8;
                    int interpBtnW = 20;
                    int interpBtnY = y + 18;
                    int interpBtnH = 14;

                    if (mx >= interpBtnX && mx < interpBtnX + interpBtnW && my >= interpBtnY && my < interpBtnY + interpBtnH) {
                        showEasingDropdown = !showEasingDropdown;
                        easingScrollY = 0;
                        return true;
                    }
                } else if (is3D) {
                    if (kfTickPad.mouseClicked(mx, my, btn)) return true;
                    if (kfValuePad.mouseClicked(mx, my, btn)) return true;
                    if (kfValueYPad.mouseClicked(mx, my, btn)) return true;
                    if (kfValueZPad.mouseClicked(mx, my, btn)) return true;

                    int interpBtnX = x_c3 + 8;
                    int interpBtnW = 20;
                    int interpBtnY = y + 18;
                    int interpBtnH = 14;

                    if (mx >= interpBtnX && mx < interpBtnX + interpBtnW && my >= interpBtnY && my < interpBtnY + interpBtnH) {
                        showEasingDropdown = !showEasingDropdown;
                        easingScrollY = 0;
                        return true;
                    }
                } else if (tr.property.equals("shadowEnabled") || tr.property.equals("fogEnabled")) {
                    if (kfTickPad.mouseClicked(mx, my, btn)) return true;
                    int toggleY = y + 40;
                    if (mx >= x_c3 + 8 && mx < x_c3 + c3W - 8 && my >= toggleY && my < toggleY + 16) {
                        CALUndoManager.pushState();
                        kf.value = kf.value >= 0.5f ? 0.0f : 1.0f;
                        if (onUpdate != null) onUpdate.run();
                        return true;
                    }

                    int interpBtnX = x_c3 + 8;
                    int interpBtnW = 20;
                    int interpBtnY = y + 20;
                    int interpBtnH = 16;

                    if (mx >= interpBtnX && mx < interpBtnX + interpBtnW && my >= interpBtnY && my < interpBtnY + interpBtnH) {
                        showEasingDropdown = !showEasingDropdown;
                        easingScrollY = 0;
                        return true;
                    }
                } else {
                    // 1D Track
                    if (kfTickPad.mouseClicked(mx, my, btn)) return true;
                    if (kfValuePad.mouseClicked(mx, my, btn)) return true;

                    int interpBtnX = x_c3 + 8;
                    int interpBtnW = 20;
                    int interpBtnY = y + 20;
                    int interpBtnH = 16;

                    if (mx >= interpBtnX && mx < interpBtnX + interpBtnW && my >= interpBtnY && my < interpBtnY + interpBtnH) {
                        showEasingDropdown = !showEasingDropdown;
                        easingScrollY = 0;
                        return true;
                    }
                }
            } else if (tr != null) {
                if (trackDurationPad.mouseClicked(mx, my, btn)) return true;

                // Bucle toggle
                int loopY = y + 38;
                if (mx >= x_c3 + 8 && mx < x_c3 + c3W - 8 && my >= loopY && my < loopY + 16) {
                    CALUndoManager.pushState();
                    tr.looping = !tr.looping;
                    if (onUpdate != null) onUpdate.run();
                    return true;
                }
            }
            return true;
        }

        return false;
    }

    @Override
    public boolean mouseReleased(int mx, int my, int btn) {
        if (isSelecting) {
            isSelecting = false;
            if (onUpdate != null) onUpdate.run();
        }
        draggingKeyframe = false;
        draggingPlayhead = false;
        sortModifiedTracks();
        dragStartTicks.clear();
        
        LightKeyframe kf = getSelectedKeyframe();
        LightAnimationTrack tr = getSelectedTrackObj();
        if (kf != null && tr != null && tr.property.equals("color")) {
            kfColorPicker.mouseReleased(mx, my, btn);
        }

        return kfTickPad.mouseReleased(mx, my, btn)
            || kfValuePad.mouseReleased(mx, my, btn)
            || kfValueYPad.mouseReleased(mx, my, btn)
            || kfValueZPad.mouseReleased(mx, my, btn)
            || trackDurationPad.mouseReleased(mx, my, btn);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        LightAnimation anim = getOrCreateAnimation();
        
        if (draggingPlayhead) {
            float timelineLen = (getSelectedTrackObj() != null) ? getSelectedTrackObj().duration : 2000f;
            int c1W = 150;
            int c3W = 180;
            int c2W = w - c1W - c3W;
            int x_c2 = x + c1W;

            float localMouseX = (float) (mx - (x_c2 + 10));
            float newTime = (localMouseX / (c2W - 20)) * timelineLen;
            anim.playbackTimeMs = (long) Math.max(0f, Math.min(timelineLen, newTime));
            if (onUpdate != null) onUpdate.run();
            return true;
        }

        if (isSelecting) {
            selectEndX = (int) mx;
            selectEndY = (int) my;
            
            int c1W = 150;
            int c3W = 180;
            int c2W = w - c1W - c3W;
            int x_c2 = x + c1W;
            float timelineLen = (getSelectedTrackObj() != null) ? getSelectedTrackObj().duration : 2000f;
            int listStartY = y + 38;
            int rowH = 18;

            selectedKeyframes.clear();
            selectedKeyframes.addAll(initialSelection);
            
            int x1 = Math.min(selectStartX, selectEndX);
            int x2 = Math.max(selectStartX, selectEndX);
            int y1 = Math.min(selectStartY, selectEndY);
            int y2 = Math.max(selectStartY, selectEndY);

            for (int i = 0; i < anim.tracks.size(); i++) {
                LightAnimationTrack tr = anim.tracks.get(i);
                int rowY = listStartY + i * rowH - timelineScrollY;
                int yCenter = rowY + (rowH - 2) / 2;

                for (LightKeyframe kf : tr.keyframes) {
                    int kfX = x_c2 + 10 + (int) ((kf.tick / timelineLen) * (c2W - 20));

                    // Check bounds (both inside Column 2 timeline and inside selection box)
                    if (kfX >= x_c2 && kfX <= x_c2 + c2W && yCenter >= y + 16 && yCenter < y + h) {
                        if (kfX >= x1 && kfX <= x2 && yCenter >= y1 && yCenter <= y2) {
                            if (isCtrlDown() || isShiftDown()) {
                                if (initialSelection.contains(kf)) {
                                    selectedKeyframes.remove(kf); // Toggle off if already selected
                                } else {
                                    selectedKeyframes.add(kf);
                                }
                            } else {
                                selectedKeyframes.add(kf);
                            }
                        }
                    }
                }
            }

            // Sync primary selection to one of selected keyframes
            if (!selectedKeyframes.isEmpty()) {
                LightKeyframe current = getSelectedKeyframe();
                if (current == null || !selectedKeyframes.contains(current)) {
                    LightKeyframe any = selectedKeyframes.iterator().next();
                    for (int tIdx = 0; tIdx < anim.tracks.size(); tIdx++) {
                        int kIdx = anim.tracks.get(tIdx).keyframes.indexOf(any);
                        if (kIdx != -1) {
                            selectedTrack = tIdx;
                            selectedKeyframe = kIdx;
                            break;
                        }
                    }
                }
            } else {
                selectedKeyframe = -1;
            }

            if (onUpdate != null) onUpdate.run();
            return true;
        }

        if (draggingKeyframe) {
            int c1W = 150;
            int c3W = 180;
            int c2W = w - c1W - c3W;
            int x_c2 = x + c1W;
            float timelineLen = (getSelectedTrackObj() != null) ? getSelectedTrackObj().duration : 2000f;

            float deltaX = (float) (mx - dragStartX);
            float deltaTicks = (deltaX / (c2W - 20)) * timelineLen;

            if (!hasPushedUndoForDrag) {
                CALUndoManager.pushState();
                hasPushedUndoForDrag = true;
            }

            for (LightKeyframe kf : selectedKeyframes) {
                LightAnimationTrack tr = findTrackForKeyframe(kf);
                if (tr != null) {
                    float origTick = dragStartTicks.getOrDefault(kf, kf.tick);
                    float newTick = origTick + deltaTicks;
                    newTick = Math.max(0f, Math.min(tr.duration, newTick));
                    if (!isShiftDown()) {
                        newTick = Math.round(newTick / 50f) * 50f;
                    }
                    kf.tick = newTick;
                }
            }

            LightKeyframe primaryKf = getSelectedKeyframe();
            if (primaryKf != null) {
                kfTickPad.setValue(msToVisual(primaryKf.tick));
            }

            if (onUpdate != null) onUpdate.run();
            return true;
        }

        LightKeyframe kf = getSelectedKeyframe();
        LightAnimationTrack tr = getSelectedTrackObj();
        if (kf != null && tr != null && tr.property.equals("color")) {
            if (kfColorPicker.mouseDragged(mx, my, btn, dx, dy)) return true;
        }

        return kfTickPad.mouseDragged(mx, my, btn, dx, dy)
            || kfValuePad.mouseDragged(mx, my, btn, dx, dy)
            || kfValueYPad.mouseDragged(mx, my, btn, dx, dy)
            || kfValueZPad.mouseDragged(mx, my, btn, dx, dy)
            || trackDurationPad.mouseDragged(mx, my, btn, dx, dy);
    }

    public boolean scroll(int mx, int my, double amount) {
        // Timeline tracks vertical scroll: Column 1 or Column 2
        int c1W = 150;
        int x_c2 = x + c1W;
        int c3W = 180;
        int c2W = w - c1W - c3W;
        if (mx >= x && mx < x_c2 + c2W && my >= y + 56 && my < y + h) {
            LightAnimation anim = getOrCreateAnimation();
            int rowH = 18;
            int maxScrollY = Math.max(0, (anim.tracks.size() * rowH) - (h - 58));
            timelineScrollY = Math.max(0, Math.min(maxScrollY, timelineScrollY - (int)(amount * 18)));
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (CLUITrackpad.activeEditingTrackpad != null) {
            if (CLUITrackpad.activeEditingTrackpad.handleKeyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
        }

        LightKeyframe kf = getSelectedKeyframe();
        LightAnimationTrack tr = getSelectedTrackObj();
        if (kf != null && tr != null && tr.property.equals("color")) {
            if (kfColorPicker.keyPressed(keyCode, scanCode, modifiers)) return true;
        }

        LightAnimation anim = getOrCreateAnimation();

        // 1. Play / Pause key mapping
        if (keyCode == CalSettings.INSTANCE.keyPlayPause) {
            anim.isPlaying = !anim.isPlaying;
            if (onUpdate != null) onUpdate.run();
            return true;
        }

        // --- CLIPBOARD & KEYS SHORTCUTS FROM BBS ---
        boolean ctrl = isCtrlDown();
        if (ctrl && keyCode == GLFW.GLFW_KEY_A) {
            selectAllKeyframes();
            return true;
        }
        if (ctrl && keyCode == GLFW.GLFW_KEY_C) {
            if (!selectedKeyframes.isEmpty()) {
                copySelectedKeyframes();
                return true;
            }
            return false; // let it fall through to CALEditorPanel to copy light!
        }
        if (ctrl && keyCode == GLFW.GLFW_KEY_X) {
            if (!selectedKeyframes.isEmpty()) {
                cutSelectedKeyframes();
                return true;
            }
            return false;
        }
        if (ctrl && keyCode == GLFW.GLFW_KEY_V) {
            if (!keyframeClipboard.isEmpty()) {
                pasteCopiedKeyframes();
                return true;
            }
            return false; // let it fall through to CALEditorPanel to paste light!
        }
        if (keyCode == GLFW.GLFW_KEY_DELETE) {
            if (!selectedKeyframes.isEmpty()) {
                deleteSelectedKeyframes();
                return true;
            }
            return false; // let it fall through to delete selected light!
        }



        return false;
    }

    public boolean isShowEasingDropdown() {
        return showEasingDropdown;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (CLUITrackpad.activeEditingTrackpad != null) {
            if (CLUITrackpad.activeEditingTrackpad.handleCharTyped(chr, modifiers)) {
                return true;
            }
        }
        
        LightKeyframe kf = getSelectedKeyframe();
        LightAnimationTrack tr = getSelectedTrackObj();
        if (kf != null && tr != null && tr.property.equals("color")) {
            if (kfColorPicker.charTyped(chr, modifiers)) return true;
        }

        return false;
    }

    // ── HELPER & CLIPBOARD METHODS ───────────────────────────────────────
    
    private LightAnimationTrack findTrackForKeyframe(LightKeyframe kf) {
        LightAnimation anim = getOrCreateAnimation();
        for (LightAnimationTrack tr : anim.tracks) {
            if (tr.keyframes.contains(kf)) {
                return tr;
            }
        }
        return null;
    }

    private void sortModifiedTracks() {
        LightAnimation anim = getOrCreateAnimation();
        LightKeyframe primarySel = getSelectedKeyframe();
        for (LightAnimationTrack tr : anim.tracks) {
            boolean hasModifiedKf = false;
            for (LightKeyframe kf : tr.keyframes) {
                if (selectedKeyframes.contains(kf)) {
                    hasModifiedKf = true;
                    break;
                }
            }
            if (hasModifiedKf) {
                tr.keyframes.sort((a, b) -> Float.compare(a.tick, b.tick));
            }
        }
        if (primarySel != null) {
            LightAnimationTrack tr = getSelectedTrackObj();
            if (tr != null) {
                selectedKeyframe = tr.keyframes.indexOf(primarySel);
            }
        }
    }

    private void deleteSelectedKeyframes() {
        if (selectedKeyframes.isEmpty()) return;
        CALUndoManager.pushState();
        LightAnimation anim = getOrCreateAnimation();
        for (LightAnimationTrack tr : anim.tracks) {
            tr.keyframes.removeAll(selectedKeyframes);
        }
        selectedKeyframes.clear();
        selectedKeyframe = -1;
        showEasingDropdown = false;
        if (onUpdate != null) onUpdate.run();
    }

    private void selectAllKeyframes() {
        selectedKeyframes.clear();
        LightAnimation anim = getOrCreateAnimation();
        for (LightAnimationTrack tr : anim.tracks) {
            selectedKeyframes.addAll(tr.keyframes);
        }
        if (!selectedKeyframes.isEmpty()) {
            selectedTrack = 0;
            selectedKeyframe = 0;
        }
        if (onUpdate != null) onUpdate.run();
    }

    private void copySelectedKeyframes() {
        if (selectedKeyframes.isEmpty()) return;
        float minTick = Float.MAX_VALUE;
        for (LightKeyframe kf : selectedKeyframes) {
            if (kf.tick < minTick) {
                minTick = kf.tick;
            }
        }
        keyframeClipboard.clear();
        LightAnimation anim = getOrCreateAnimation();
        for (LightAnimationTrack tr : anim.tracks) {
            for (LightKeyframe kf : tr.keyframes) {
                if (selectedKeyframes.contains(kf)) {
                    CopiedKeyframe ck = new CopiedKeyframe();
                    ck.property = tr.property;
                    ck.relativeTick = kf.tick - minTick;
                    ck.value = kf.value;
                    ck.vecY = kf.vecY;
                    ck.vecZ = kf.vecZ;
                    ck.interp = kf.interp;
                    keyframeClipboard.add(ck);
                }
            }
        }
    }

    private void cutSelectedKeyframes() {
        if (selectedKeyframes.isEmpty()) return;
        copySelectedKeyframes();
        deleteSelectedKeyframes();
    }

    private void pasteCopiedKeyframes() {
        if (keyframeClipboard.isEmpty()) return;
        CALUndoManager.pushState();
        LightAnimation anim = getOrCreateAnimation();
        
        float pasteAnchor = (float) anim.playbackTimeMs;
        
        selectedKeyframes.clear();
        
        for (CopiedKeyframe ck : keyframeClipboard) {
            LightAnimationTrack targetTrack = null;
            for (LightAnimationTrack tr : anim.tracks) {
                if (tr.property.equals(ck.property)) {
                    targetTrack = tr;
                    break;
                }
            }
            
            if (targetTrack != null) {
                float newTick = pasteAnchor + ck.relativeTick;
                newTick = Math.max(0f, Math.min(targetTrack.duration, newTick));
                
                LightKeyframe existing = null;
                for (LightKeyframe kf : targetTrack.keyframes) {
                    if (Math.abs(kf.tick - newTick) < 0.1f) {
                        existing = kf;
                        break;
                    }
                }
                
                if (existing != null) {
                    existing.value = ck.value;
                    existing.vecY = ck.vecY;
                    existing.vecZ = ck.vecZ;
                    existing.interp = ck.interp;
                    selectedKeyframes.add(existing);
                } else {
                    LightKeyframe newKf = new LightKeyframe(newTick, ck.value);
                    newKf.vecY = ck.vecY;
                    newKf.vecZ = ck.vecZ;
                    newKf.interp = ck.interp;
                    targetTrack.keyframes.add(newKf);
                    selectedKeyframes.add(newKf);
                }
            }
        }
        
        for (LightAnimationTrack tr : anim.tracks) {
            tr.keyframes.sort((a, b) -> Float.compare(a.tick, b.tick));
        }
        
        if (!selectedKeyframes.isEmpty()) {
            LightKeyframe first = selectedKeyframes.iterator().next();
            for (int tIdx = 0; tIdx < anim.tracks.size(); tIdx++) {
                int kIdx = anim.tracks.get(tIdx).keyframes.indexOf(first);
                if (kIdx != -1) {
                    selectedTrack = tIdx;
                    selectedKeyframe = kIdx;
                    break;
                }
            }
        }
        
        if (onUpdate != null) onUpdate.run();
    }

    private static boolean isCtrlDown() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return false;
        return InputConstants.isKeyDown(mc.getWindow(), GLFW.GLFW_KEY_LEFT_CONTROL) || InputConstants.isKeyDown(mc.getWindow(), GLFW.GLFW_KEY_RIGHT_CONTROL);
    }

    private static boolean isShiftDown() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return false;
        return InputConstants.isKeyDown(mc.getWindow(), GLFW.GLFW_KEY_LEFT_SHIFT) || InputConstants.isKeyDown(mc.getWindow(), GLFW.GLFW_KEY_RIGHT_SHIFT);
    }
}
