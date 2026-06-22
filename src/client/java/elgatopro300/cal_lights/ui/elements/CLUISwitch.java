package elgatopro300.cal_lights.ui.elements;

import elgatopro300.cal_lights.manager.CALUndoManager;
import elgatopro300.cal_lights.ui.CLUIContext;
import elgatopro300.cal_lights.ui.CLUIElement;
import elgatopro300.cal_lights.ui.CalSettings;
import elgatopro300.cal_lights.ui.IKey;

import java.util.function.Consumer;

public class CLUISwitch extends CLUIElement {
    private boolean value;
    private final Consumer<Boolean> onChange;
    private final IKey label;
    private float currentKnobX = -1f;

    // Custom coloring fields
    private int activeColor = 0xFF00E676;
    private int activeHoverColor = 0xFF00FF88;
    private int activeBorderColor = 0xFF007A3E;

    private int inactiveColor = 0xFF3B3B43;
    private int inactiveHoverColor = 0xFF4A4A52;
    private int inactiveBorderColor = 0xFF1F1F24;

    public CLUISwitch(IKey label, boolean initial, Consumer<Boolean> onChange) {
        this.label = label;
        this.value = initial;
        this.onChange = onChange;
    }

    public CLUISwitch setColors(int active, int activeHover, int activeBorder) {
        this.activeColor = active;
        this.activeHoverColor = activeHover;
        this.activeBorderColor = activeBorder;
        return this;
    }

    public void setValue(boolean val) {
        this.value = val;
    }

    public boolean getValue() {
        return this.value;
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

    @Override
    public void render(CLUIContext ctx) {
        // BBS-style Toggle Switch
        boolean hoverCheck = ctx.mouseX >= x + 10 && ctx.mouseX < x + 100 && ctx.mouseY >= y && ctx.mouseY < y + h;
        
        int tx = x + 10;
        int ty = y + 3;
        int tw = 20;
        int th = 10;

        float targetKnobX = value ? 1.0f : 0.0f;
        if (currentKnobX < 0) {
            currentKnobX = targetKnobX;
        }

        if (CalSettings.INSTANCE.simplifyAnimations) {
            currentKnobX = targetKnobX;
        } else {
            currentKnobX += (targetKnobX - currentKnobX) * 0.25f;
        }

        int finalActive = hoverCheck ? activeHoverColor : activeColor;
        int finalInactive = hoverCheck ? inactiveHoverColor : inactiveColor;
        int trackColor = interpolateColor(finalInactive, finalActive, currentKnobX);
        int borderColor = interpolateColor(inactiveBorderColor, activeBorderColor, currentKnobX);

        ctx.batcher.box(tx, ty, tx + tw, ty + th, trackColor);
        ctx.batcher.outline(tx, ty, tx + tw, ty + th, borderColor, 1);

        int knobSize = 8;
        int knobX = tx + 1 + (int) (currentKnobX * (tw - knobSize - 2));
        int knobY = ty + 1;

        ctx.batcher.box(knobX, knobY + 1, knobX + knobSize, knobY + knobSize + 1, 0x66000000); // Shadow
        ctx.batcher.box(knobX, knobY, knobX + knobSize, knobY + knobSize, 0xFFFFFFFF); // White body
        ctx.batcher.box(knobX, knobY + knobSize - 2, knobX + knobSize, knobY + knobSize, 0xFFD2D2D6); // Bottom bevel

        ctx.batcher.text(label.get(), x + 36, y + 3, 0xFFFFFFFF);
    }

    @Override
    public boolean mouseClicked(int mx, int my, int btn) {
        if (btn == 0 && mx >= x + 10 && mx < x + 100 && my >= y && my < y + h) {
            CALUndoManager.pushState();
            value = !value;
            if (onChange != null) {
                onChange.accept(value);
            }
            return true;
        }
        return false;
    }
}
