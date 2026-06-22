package elgatopro300.cal_lights.ui.elements;

import elgatopro300.cal_lights.ui.CLUIContext;
import elgatopro300.cal_lights.ui.CLUIElement;
import elgatopro300.cal_lights.ui.CalSettings;

import java.util.function.Consumer;

public class CLUIToggle extends CLUIElement {
    private boolean value;
    private final Consumer<Boolean> onChange;
    private final String label;
    private float currentKnobX = -1f;

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

    public CLUIToggle(String label, boolean initial, Consumer<Boolean> onChange) {
        this.label = label;
        this.value = initial;
        this.onChange = onChange;
    }

    public void setValue(boolean val) {
        this.value = val;
    }

    public boolean getValue() {
        return this.value;
    }

    @Override
    public void render(CLUIContext ctx) {
        int bg = isHovered(ctx.mouseX, ctx.mouseY) ? 0xFF3C3C3C : 0xFF2A2A2A;
        ctx.batcher.box(x, y, x + w, y + h, bg);
        ctx.batcher.outline(x, y, x + w, y + h, 0xFF555555, 1);

        // Draw premium sliding switch trackpad
        int trackW = 20;
        int trackH = 10;
        int trackX = x + w - trackW - 6;
        int trackY = y + (h - trackH) / 2;

        float targetKnobX = value ? 1.0f : 0.0f;
        if (currentKnobX < 0) {
            currentKnobX = targetKnobX;
        }

        if (CalSettings.INSTANCE.simplifyAnimations) {
            currentKnobX = targetKnobX;
        } else {
            currentKnobX += (targetKnobX - currentKnobX) * 0.25f;
        }

        // Interpolate track color
        int activeTrackColor = 0xFF4CAF50;
        int inactiveTrackColor = 0xFF424242;
        int trackColor = interpolateColor(inactiveTrackColor, activeTrackColor, currentKnobX);
        int activeBorderColor = 0xFF2E7D32;
        int inactiveBorderColor = 0xFF1F1F24;
        int borderColor = interpolateColor(inactiveBorderColor, activeBorderColor, currentKnobX);

        ctx.batcher.box(trackX, trackY, trackX + trackW, trackY + trackH, trackColor);
        ctx.batcher.outline(trackX, trackY, trackX + trackW, trackY + trackH, borderColor, 1);

        int knobSize = 8;
        int knobX = trackX + 1 + (int) (currentKnobX * (trackW - knobSize - 2));
        int knobY = trackY + 1;

        ctx.batcher.box(knobX, knobY + 1, knobX + knobSize, knobY + knobSize + 1, 0x66000000); // Shadow
        ctx.batcher.box(knobX, knobY, knobX + knobSize, knobY + knobSize, 0xFFFFFFFF); // White body
        ctx.batcher.box(knobX, knobY + knobSize - 2, knobX + knobSize, knobY + knobSize, 0xFFD2D2D6); // Bevel

        ctx.batcher.text(label, x + 6, y + (h - 8) / 2, 0xFFFFFFFF);
    }

    @Override
    public boolean mouseClicked(int mx, int my, int btn) {
        if (btn == 0 && isHovered(mx, my)) {
            value = !value;
            if (onChange != null) {
                onChange.accept(value);
            }
            return true;
        }
        return false;
    }
}
