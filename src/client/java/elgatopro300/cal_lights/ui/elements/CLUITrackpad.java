package elgatopro300.cal_lights.ui.elements;

import elgatopro300.cal_lights.manager.CALUndoManager;
import elgatopro300.cal_lights.ui.CLUIContext;
import elgatopro300.cal_lights.ui.CLUIElement;
import elgatopro300.cal_lights.ui.CalSettings;
import elgatopro300.cal_lights.ui.panels.AnimationEditorPanel;

import net.minecraft.client.Minecraft;

import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;

public class CLUITrackpad extends CLUIElement {
    public static CLUITrackpad activeEditingTrackpad = null;
    private float value;
    private float min;
    private float max;
    private float step = 0.05f;
    private float arrowStep = 0.05f;
    private boolean dragging = false;
    private boolean hasDragged = false;
    private int dragStartX;
    private float dragStartVal;
    private Consumer<Float> onChange;
    private String label;
    private String editBuffer = "";
    private int cursorIdx = 0;
    private float hoverAnim = 0.0f;
    private float leftHoverAnim = 0.0f;
    private float rightHoverAnim = 0.0f;

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

    public CLUITrackpad(String label, float initial, float min, float max, Consumer<Float> onChange) {
        this.label = label;
        this.value = initial;
        this.min = min;
        this.max = max;
        this.onChange = onChange;
        configureArrowStep(label);
    }

    private void configureArrowStep(String label) {
        boolean isTranslation = label.equals("X") || label.equals("Y") || label.equals("Z");
        boolean isRotation = label.equals("RX") || label.equals("RY") || label.equals("RZ");
        if (isTranslation) {
            this.arrowStep = 1.0f;
        } else if (isRotation) {
            this.arrowStep = 15.0f;
        } else if (label.toLowerCase().contains("ángulo") || label.toLowerCase().contains("angle") ||
                   label.toLowerCase().contains("distancia") || label.toLowerCase().contains("distance") ||
                   label.toLowerCase().contains("radius") || label.toLowerCase().contains("radio")) {
            this.arrowStep = 1.0f;
        } else {
            this.arrowStep = 0.05f;
        }
    }

    public void updateConfig(String label, float min, float max) {
        this.label = label;
        this.min = min;
        this.max = max;
        configureArrowStep(label);
    }

    public void setOnChange(Consumer<Float> onChange) {
        this.onChange = onChange;
    }

    public CLUITrackpad setArrowStep(float val) {
        this.arrowStep = val;
        return this;
    }

    public void setValue(float val) {
        this.value = Math.max(min, Math.min(max, val));
    }

    public float getValue() {
        return this.value;
    }

    @Override
    public void render(CLUIContext ctx) {
        boolean isTranslation = label.equals("X") || label.equals("Y") || label.equals("Z");
        boolean isRotation = label.equals("RX") || label.equals("RY") || label.equals("RZ");
        boolean isBadge = isTranslation || isRotation || label.isEmpty();

        int btnY = y;
        int btnH = h;

        if (isBadge) {
            // Layout 1: Arrows on the sides (left/right edges), value in the middle
            int leftBtnX = x;
            int rightBtnX = x + w - 12;
            int mainX = x + 12;
            int mainW = w - 24;

            boolean hoverLeft = ctx.mouseX >= leftBtnX && ctx.mouseX < leftBtnX + 12 && ctx.mouseY >= btnY && ctx.mouseY < btnY + btnH;
            boolean hoverRight = ctx.mouseX >= rightBtnX && ctx.mouseX < rightBtnX + 12 && ctx.mouseY >= btnY && ctx.mouseY < btnY + btnH;
            boolean hoverValue = ctx.mouseX >= mainX && ctx.mouseX < mainX + mainW && ctx.mouseY >= btnY && ctx.mouseY < btnY + btnH;

            // Animate states
            boolean activeValue = (activeEditingTrackpad == this) || dragging || hoverValue;
            float targetHover = activeValue ? 1.0f : 0.0f;
            float targetLeft = hoverLeft ? 1.0f : 0.0f;
            float targetRight = hoverRight ? 1.0f : 0.0f;

            if (CalSettings.INSTANCE.simplifyAnimations) {
                hoverAnim = targetHover;
                leftHoverAnim = targetLeft;
                rightHoverAnim = targetRight;
            } else {
                hoverAnim += (targetHover - hoverAnim) * 0.2f;
                leftHoverAnim += (targetLeft - leftHoverAnim) * 0.2f;
                rightHoverAnim += (targetRight - rightHoverAnim) * 0.2f;
            }

            int targetBg = (activeEditingTrackpad == this) ? 0xFF0D0D11 : (dragging ? 0xFF2A2A35 : 0xFF222228);
            int bg = interpolateColor(0xFF16161C, targetBg, hoverAnim);

            int targetBorder = (activeEditingTrackpad == this) ? 0xFFFFAA00 : 0xFF5F5F6F;
            int border = interpolateColor(0xFF2D2D38, targetBorder, hoverAnim);

            // Draw integrated trackpad background
            ctx.batcher.box(x, y, x + w, y + h, 0xFF16161C);

            // Highlight the value box area with interpolated bg
            ctx.batcher.box(mainX, y, mainX + mainW, y + h, bg);

            // Draw a single cohesive border around the entire widget
            ctx.batcher.outline(x, y, x + w, y + h, border, 1);

            // Draw color-coded numerical value directly
            int textColor = 0xFFE0E0E0;
            if (label.contains("X")) textColor = 0xFFFF3F3F;
            else if (label.contains("Y")) textColor = 0xFF3FFF3F;
            else if (label.contains("Z")) textColor = 0xFF3F8FFF;

            String valStr;
            if (activeEditingTrackpad == this) {
                valStr = editBuffer;
            } else {
                if (label.isEmpty() && AnimationEditorPanel.isTicksMode()) {
                    valStr = String.format("%.0f", value);
                } else {
                    valStr = String.format("%.2f", value);
                }
            }

            int textW = Minecraft.getInstance().font.width(valStr);
            int drawX = mainX + (mainW - textW) / 2;
            ctx.batcher.text(valStr, drawX, y + (h - 8) / 2, textColor);

            // Draw separate blinking cursor line!
            if (activeEditingTrackpad == this && System.currentTimeMillis() / 500 % 2 == 0) {
                int subW = Minecraft.getInstance().font.width(editBuffer.substring(0, cursorIdx));
                int cursorX = drawX + subW;
                int cursorY = y + (h - 10) / 2;
                ctx.batcher.box(cursorX, cursorY, cursorX + 1, cursorY + 10, 0xFFFFFFFF);
            }

            // Draw solid grey background for chevron buttons
            int leftBtnBg = interpolateColor(0xFF1D1D24, 0xFF2B2B36, leftHoverAnim);
            int rightBtnBg = interpolateColor(0xFF1D1D24, 0xFF2B2B36, rightHoverAnim);

            ctx.batcher.box(leftBtnX + 1, btnY + 1, leftBtnX + 11, btnY + btnH - 1, leftBtnBg);
            ctx.batcher.box(rightBtnX + 1, btnY + 1, rightBtnX + 11, btnY + btnH - 1, rightBtnBg);

            // Draw vertical dividers separating the buttons from the main area
            ctx.batcher.box(leftBtnX + 11, btnY + 1, leftBtnX + 12, btnY + btnH - 1, 0xFF2D2D38);
            ctx.batcher.box(rightBtnX, btnY + 1, rightBtnX + 1, btnY + btnH - 1, 0xFF2D2D38);

            // Draw beautiful BBS chevrons on the sides
            int mColor = interpolateColor(0x80FFFFFF, 0xFFFFFFFF, leftHoverAnim);
            int pColor = interpolateColor(0x80FFFFFF, 0xFFFFFFFF, rightHoverAnim);

            drawChevron(ctx, leftBtnX + 6, btnY + btnH / 2, true, mColor);
            drawChevron(ctx, rightBtnX + 6, btnY + btnH / 2, false, pColor);
        } else {
            // Layout 2: Arrows grouped on the right side, label + value on the left
            int leftBtnX = x + w - 24;
            int rightBtnX = x + w - 12;
            int mainX = x;
            int mainW = w - 24;

            boolean hoverLeft = ctx.mouseX >= leftBtnX && ctx.mouseX < rightBtnX && ctx.mouseY >= btnY && ctx.mouseY < btnY + btnH;
            boolean hoverRight = ctx.mouseX >= rightBtnX && ctx.mouseX < x + w && ctx.mouseY >= btnY && ctx.mouseY < btnY + btnH;
            boolean hoverValue = ctx.mouseX >= mainX && ctx.mouseX < mainX + mainW && ctx.mouseY >= btnY && ctx.mouseY < btnY + btnH;

            // Animate states
            boolean activeValue = (activeEditingTrackpad == this) || dragging || hoverValue;
            float targetHover = activeValue ? 1.0f : 0.0f;
            float targetLeft = hoverLeft ? 1.0f : 0.0f;
            float targetRight = hoverRight ? 1.0f : 0.0f;

            if (CalSettings.INSTANCE.simplifyAnimations) {
                hoverAnim = targetHover;
                leftHoverAnim = targetLeft;
                rightHoverAnim = targetRight;
            } else {
                hoverAnim += (targetHover - hoverAnim) * 0.2f;
                leftHoverAnim += (targetLeft - leftHoverAnim) * 0.2f;
                rightHoverAnim += (targetRight - rightHoverAnim) * 0.2f;
            }

            int targetBg = (activeEditingTrackpad == this) ? 0xFF0D0D11 : (dragging ? 0xFF2A2A35 : 0xFF222228);
            int bg = interpolateColor(0xFF16161C, targetBg, hoverAnim);

            int targetBorder = (activeEditingTrackpad == this) ? 0xFFFFAA00 : 0xFF5F5F6F;
            int border = interpolateColor(0xFF2D2D38, targetBorder, hoverAnim);

            // Draw integrated trackpad background
            ctx.batcher.box(x, y, x + w, y + h, 0xFF16161C);

            // Highlight the value box area with interpolated bg
            ctx.batcher.box(mainX, y, mainX + mainW, y + h, bg);

            // Draw a single cohesive border around the entire widget
            ctx.batcher.outline(x, y, x + w, y + h, border, 1);

            // Draw standard label and numerical value
            String text;
            if (activeEditingTrackpad == this) {
                text = label + ": " + editBuffer;
            } else {
                text = label + ": " + String.format("%.2f", value);
            }
            ctx.batcher.text(text, x + 6, y + (h - 8) / 2, 0xFFE0E0E0);

            // Draw separate blinking cursor line!
            if (activeEditingTrackpad == this && System.currentTimeMillis() / 500 % 2 == 0) {
                String prefix = label + ": ";
                int prefW = Minecraft.getInstance().font.width(prefix);
                int subW = Minecraft.getInstance().font.width(editBuffer.substring(0, cursorIdx));
                int cursorX = x + 6 + prefW + subW;
                int cursorY = y + (h - 10) / 2;
                ctx.batcher.box(cursorX, cursorY, cursorX + 1, cursorY + 10, 0xFFFFFFFF);
            }

            // Draw solid grey background for chevron buttons
            int leftBtnBg = interpolateColor(0xFF1D1D24, 0xFF2B2B36, leftHoverAnim);
            int rightBtnBg = interpolateColor(0xFF1D1D24, 0xFF2B2B36, rightHoverAnim);

            ctx.batcher.box(leftBtnX + 1, btnY + 1, rightBtnX, btnY + btnH - 1, leftBtnBg);
            ctx.batcher.box(rightBtnX, btnY + 1, x + w - 1, btnY + btnH - 1, rightBtnBg);

            // Draw vertical divider separating the buttons from the main area
            ctx.batcher.box(leftBtnX, btnY + 1, leftBtnX + 1, btnY + btnH - 1, 0xFF2D2D38);

            // Draw beautiful BBS chevrons grouped on the right
            int mColor = interpolateColor(0x80FFFFFF, 0xFFFFFFFF, leftHoverAnim);
            int pColor = interpolateColor(0x80FFFFFF, 0xFFFFFFFF, rightHoverAnim);

            drawChevron(ctx, leftBtnX + 6, btnY + btnH / 2, true, mColor);
            drawChevron(ctx, rightBtnX + 6, btnY + btnH / 2, false, pColor);
        }
    }

    @Override
    public boolean mouseClicked(int mx, int my, int btn) {
        boolean isTranslation = label.equals("X") || label.equals("Y") || label.equals("Z");
        boolean isRotation = label.equals("RX") || label.equals("RY") || label.equals("RZ");
        boolean isBadge = isTranslation || isRotation || label.isEmpty();

        if (isBadge) {
            // Layout 1: Arrows on the sides (left/right edges)
            int leftBtnX = x;
            int rightBtnX = x + w - 12;
            int mainX = x + 12;
            int mainW = w - 24;

            if (btn == 0 && mx >= leftBtnX && mx < leftBtnX + 12 && my >= y && my < y + h) {
                if (activeEditingTrackpad != null) {
                    activeEditingTrackpad.commitEdit();
                }
                CALUndoManager.pushState();
                value = Math.max(min, Math.min(max, value - arrowStep));
                if (onChange != null) {
                    onChange.accept(value);
                }
                return true;
            }

            if (btn == 0 && mx >= rightBtnX && mx < rightBtnX + 12 && my >= y && my < y + h) {
                if (activeEditingTrackpad != null) {
                    activeEditingTrackpad.commitEdit();
                }
                CALUndoManager.pushState();
                value = Math.max(min, Math.min(max, value + arrowStep));
                if (onChange != null) {
                    onChange.accept(value);
                }
                return true;
            }

            if (btn == 0 && mx >= mainX && mx < mainX + mainW && my >= y && my < y + h) {
                if (activeEditingTrackpad != null && activeEditingTrackpad != this) {
                    activeEditingTrackpad.commitEdit();
                }
                CALUndoManager.pushState();
                dragging = true;
                hasDragged = false;
                dragStartX = mx;
                dragStartVal = value;
                return true;
            }
        } else {
            // Layout 2: Arrows grouped on the right side
            int leftBtnX = x + w - 24;
            int rightBtnX = x + w - 12;
            int mainX = x;
            int mainW = w - 24;

            if (btn == 0 && mx >= leftBtnX && mx < rightBtnX && my >= y && my < y + h) {
                if (activeEditingTrackpad != null) {
                    activeEditingTrackpad.commitEdit();
                }
                CALUndoManager.pushState();
                value = Math.max(min, Math.min(max, value - arrowStep));
                if (onChange != null) {
                    onChange.accept(value);
                }
                return true;
            }

            if (btn == 0 && mx >= rightBtnX && mx < x + w && my >= y && my < y + h) {
                if (activeEditingTrackpad != null) {
                    activeEditingTrackpad.commitEdit();
                }
                CALUndoManager.pushState();
                value = Math.max(min, Math.min(max, value + arrowStep));
                if (onChange != null) {
                    onChange.accept(value);
                }
                return true;
            }

            if (btn == 0 && mx >= mainX && mx < mainX + mainW && my >= y && my < y + h) {
                if (activeEditingTrackpad != null && activeEditingTrackpad != this) {
                    activeEditingTrackpad.commitEdit();
                }
                CALUndoManager.pushState();
                dragging = true;
                hasDragged = false;
                dragStartX = mx;
                dragStartVal = value;
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseReleased(int mx, int my, int btn) {
        if (btn == 0 && dragging) {
            dragging = false;
            if (!hasDragged) {
                // Click without dragging: activate editing mode
                activeEditingTrackpad = this;
                this.editBuffer = String.format("%.2f", value).replace(",", ".");
                this.cursorIdx = this.editBuffer.length();
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        if (btn == 0 && dragging) {
            if (Math.abs(mx - dragStartX) > 1.5) {
                hasDragged = true;
            }
            Minecraft mc = Minecraft.getInstance();
            int scaledWidth = mc.getWindow().getGuiScaledWidth();
            double scaleFactor = mc.getWindow().getGuiScale();
            long handle = mc.getWindow().handle();

            int border = 5;
            int padding = 10;

            if (mx <= border) {
                // Wrap to the right side of the screen
                int newMx = scaledWidth - padding;
                GLFW.glfwSetCursorPos(handle, newMx * scaleFactor, mc.mouseHandler.ypos());
                dragStartX += (newMx - mx);
                mx = newMx;
            } else if (mx >= scaledWidth - border) {
                // Wrap to the left side of the screen
                int newMx = padding;
                GLFW.glfwSetCursorPos(handle, newMx * scaleFactor, mc.mouseHandler.ypos());
                dragStartX += (newMx - mx);
                mx = newMx;
            }

            float delta = (float)(mx - dragStartX) * step;
            float newVal = Math.max(min, Math.min(max, dragStartVal + delta));
            if (newVal != value) {
                value = newVal;
                if (onChange != null) {
                    onChange.accept(value);
                }
            }
            return true;
        }
        return false;
    }

    public void commitEdit() {
        if (activeEditingTrackpad == this) {
            activeEditingTrackpad = null;
            try {
                float parsed = Float.parseFloat(editBuffer);
                float newVal = Math.max(min, Math.min(max, parsed));
                if (newVal != value) {
                    CALUndoManager.pushState();
                    value = newVal;
                    if (onChange != null) {
                        onChange.accept(value);
                    }
                }
            } catch (NumberFormatException ignored) {}
        }
    }

    public void cancelEdit() {
        if (activeEditingTrackpad == this) {
            activeEditingTrackpad = null;
        }
    }

    public boolean handleKeyPressed(int key, int scan, int action) {
        if (activeEditingTrackpad != this) return false;

        if (key == GLFW.GLFW_KEY_LEFT) {
            if (cursorIdx > 0) cursorIdx--;
            return true;
        } else if (key == GLFW.GLFW_KEY_RIGHT) {
            if (cursorIdx < editBuffer.length()) cursorIdx++;
            return true;
        } else if (key == GLFW.GLFW_KEY_BACKSPACE) {
            if (cursorIdx > 0) {
                editBuffer = editBuffer.substring(0, cursorIdx - 1) + editBuffer.substring(cursorIdx);
                cursorIdx--;
            }
            return true;
        } else if (key == GLFW.GLFW_KEY_DELETE) {
            if (cursorIdx < editBuffer.length()) {
                editBuffer = editBuffer.substring(0, cursorIdx) + editBuffer.substring(cursorIdx + 1);
            }
            return true;
        } else if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            commitEdit();
            return true;
        } else if (key == GLFW.GLFW_KEY_ESCAPE) {
            cancelEdit();
            return true;
        }
        return true; // Absorb keys
    }

    public boolean handleCharTyped(char chr, int modifiers) {
        if (activeEditingTrackpad != this) return false;

        // Allow digits, dot, and minus sign
        if ((chr >= '0' && chr <= '9') || chr == '.' || chr == '-') {
            editBuffer = editBuffer.substring(0, cursorIdx) + chr + editBuffer.substring(cursorIdx);
            cursorIdx++;
            return true;
        }
        return true; // Absorb chars
    }

    private static void drawChevron(CLUIContext ctx, int cx, int cy, boolean pointLeft, int color) {
        for (int i = -2; i <= 2; i++) {
            int depth = Math.abs(i);
            int bx = pointLeft ? (cx - 1 + depth) : (cx - 1 - depth);
            ctx.batcher.box(bx, cy + i, bx + 2, cy + i + 1, color);
        }
    }
}
