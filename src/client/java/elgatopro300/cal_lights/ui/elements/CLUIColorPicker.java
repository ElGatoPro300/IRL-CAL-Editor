package elgatopro300.cal_lights.ui.elements;

import elgatopro300.cal_lights.manager.CALUndoManager;
import elgatopro300.cal_lights.ui.CLUIContext;
import elgatopro300.cal_lights.ui.CLUIElement;

import org.lwjgl.glfw.GLFW;

public class CLUIColorPicker extends CLUIElement {
    private float r;
    private float g;
    private float b;
    private final Runnable onChange;

    // HSV representation
    private float hVal; // [0, 360]
    private float sVal; // [0, 1]
    private float vVal; // [0, 1]

    // Dragging state
    private boolean draggingSV = false;
    private boolean draggingHue = false;

    // Hex Input text box state
    private boolean hexFocused = false;
    private String hexInput = "";
    private int cursorTick = 0;

    public CLUIColorPicker(float ir, float ig, float ib, Runnable onChange) {
        this.r = ir;
        this.g = ig;
        this.b = ib;
        this.onChange = onChange;
        rgbToHsv(ir, ig, ib);
    }

    public void setColors(float r, float g, float b) {
        // Prevent updating HSV while actively editing/dragging or if color didn't change
        if (Math.abs(this.r - r) < 0.001f && Math.abs(this.g - g) < 0.001f && Math.abs(this.b - b) < 0.001f) {
            return;
        }
        this.r = r;
        this.g = g;
        this.b = b;
        if (!draggingSV && !draggingHue && !hexFocused) {
            rgbToHsv(r, g, b);
        }
    }

    public float getR() { return r; }
    public float getG() { return g; }
    public float getB() { return b; }

    private void rgbToHsv(float r, float g, float b) {
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float delta = max - min;

        vVal = max;
        sVal = (max == 0) ? 0 : delta / max;

        if (delta == 0) {
            hVal = 0;
        } else {
            if (max == r) {
                hVal = ((g - b) / delta) % 6;
            } else if (max == g) {
                hVal = (b - r) / delta + 2;
            } else {
                hVal = (r - g) / delta + 4;
            }
            hVal *= 60;
            if (hVal < 0) {
                hVal += 360;
            }
        }
    }

    private void hsvToRgb() {
        float c = vVal * sVal;
        float xVal = c * (1 - Math.abs((hVal / 60.0f) % 2 - 1));
        float m = vVal - c;

        float r1 = 0, g1 = 0, b1 = 0;
        if (hVal >= 0 && hVal < 60) {
            r1 = c; g1 = xVal; b1 = 0;
        } else if (hVal >= 60 && hVal < 120) {
            r1 = xVal; g1 = c; b1 = 0;
        } else if (hVal >= 120 && hVal < 180) {
            r1 = 0; g1 = c; b1 = xVal;
        } else if (hVal >= 180 && hVal < 240) {
            r1 = 0; g1 = xVal; b1 = c;
        } else if (hVal >= 240 && hVal < 300) {
            r1 = xVal; g1 = 0; b1 = c;
        } else if (hVal >= 300 && hVal <= 360) {
            r1 = c; g1 = 0; b1 = xVal;
        }

        this.r = r1 + m;
        this.g = g1 + m;
        this.b = b1 + m;
    }

    private int getHueColor(float h) {
        float xVal = (1 - Math.abs((h / 60.0f) % 2 - 1));
        float r1 = 0, g1 = 0, b1 = 0;
        if (h >= 0 && h < 60) {
            r1 = 1; g1 = xVal; b1 = 0;
        } else if (h >= 60 && h < 120) {
            r1 = xVal; g1 = 1; b1 = 0;
        } else if (h >= 120 && h < 180) {
            r1 = 0; g1 = 1; b1 = xVal;
        } else if (h >= 180 && h < 240) {
            r1 = 0; g1 = xVal; b1 = 1;
        } else if (h >= 240 && h < 300) {
            r1 = xVal; g1 = 0; b1 = 1;
        } else if (h >= 300 && h <= 360) {
            r1 = 1; g1 = 0; b1 = xVal;
        }
        int ir = (int)(r1 * 255) & 0xFF;
        int ig = (int)(g1 * 255) & 0xFF;
        int ib = (int)(b1 * 255) & 0xFF;
        return 0xFF000000 | (ir << 16) | (ig << 8) | ib;
    }

    private float clamp(float val, float min, float max) {
        return Math.max(min, Math.min(max, val));
    }

    private String getHexCode() {
        int ir = (int)(r * 255) & 0xFF;
        int ig = (int)(g * 255) & 0xFF;
        int ib = (int)(b * 255) & 0xFF;
        return String.format("#%02x%02x%02x", ir, ig, ib);
    }

    @Override
    public void render(CLUIContext ctx) {
        cursorTick++;

        int svSize = h - 20;
        int sy = y + 18;

        // Top Row layout
        int previewW = 14;
        int previewH = 14;
        int ir = (int)(r * 255) & 0xFF;
        int ig = (int)(g * 255) & 0xFF;
        int ib = (int)(b * 255) & 0xFF;
        int colorInt = 0xFF000000 | (ir << 16) | (ig << 8) | ib;

        // 1. Color preview box
        ctx.batcher.box(x, y, x + previewW, y + previewH, colorInt);
        ctx.batcher.outline(x, y, x + previewW, y + previewH, 0xFF555555, 1);

        // 2. Hex code box text
        int hexX = x + 18;
        ctx.batcher.box(hexX, y, x + w, y + previewH, 0xFF141416);
        ctx.batcher.outline(hexX, y, x + w, y + previewH, hexFocused ? 0xFF2196F3 : 0xFF2D2D35, 1);

        String displayText = hexFocused ? hexInput : getHexCode();
        if (hexFocused && (cursorTick / 10) % 2 == 0) {
            displayText += "|";
        }
        ctx.batcher.text(displayText, hexX + 4, y + 3, hexFocused ? 0xFFFFFFFF : 0xFF888890);

        // 3. SV Plane Gradient rendering
        int svWidth = w - 22;
        int svHeight = h - 20;

        int hueColor = getHueColor(hVal);
        float hueR = ((hueColor >> 16) & 0xFF) / 255.0f;
        float hueG = ((hueColor >> 8) & 0xFF) / 255.0f;
        float hueB = (hueColor & 0xFF) / 255.0f;

        for (int i = 0; i < svWidth; i++) {
            float s = i / (float) (svWidth - 1);
            float rTop = 1.0f + s * (hueR - 1.0f);
            float gTop = 1.0f + s * (hueG - 1.0f);
            float bTop = 1.0f + s * (hueB - 1.0f);

            int topColorInt = 0xFF000000 | (((int)(rTop * 255) & 0xFF) << 16) | (((int)(gTop * 255) & 0xFF) << 8) | ((int)(bTop * 255) & 0xFF);
            int bottomColorInt = 0xFF000000;

            float sliceX1 = x + i;
            float sliceX2 = x + i + 1;
            ctx.batcher.gradientV(sliceX1, sy, sliceX2, sy + svHeight, topColorInt, bottomColorInt);
        }
        ctx.batcher.outline(x, sy, x + svWidth, sy + svHeight, 0xFF2D2D35, 1);

        // Drag SV selector marker (small white box with black shadow)
        float sx = x + sVal * svWidth;
        float syCoord = sy + (1f - vVal) * svHeight;
        ctx.batcher.outline(sx - 2, syCoord - 2, sx + 2, syCoord + 2, 0xFF000000, 2);
        ctx.batcher.outline(sx - 2, syCoord - 2, sx + 2, syCoord + 2, 0xFFFFFFFF, 1);

        // 4. Vertical Hue rainbow slider
        int sliderX = x + svWidth + 8;
        int sliderW = 14;
        int[] hueColors = {
            0xFFFF0000, // Red (0)
            0xFFFFFF00, // Yellow (60)
            0xFF00FF00, // Green (120)
            0xFF00FFFF, // Cyan (180)
            0xFF0000FF, // Blue (240)
            0xFFFF00FF, // Magenta (300)
            0xFFFF0000  // Red (360)
        };
        for (int i = 0; i < 6; i++) {
            float yTop = sy + i * (svHeight / 6.0f);
            float yBottom = sy + (i + 1) * (svHeight / 6.0f);
            ctx.batcher.gradientV(sliderX, yTop, sliderX + sliderW, yBottom, hueColors[i], hueColors[i + 1]);
        }
        ctx.batcher.outline(sliderX, sy, sliderX + sliderW, sy + svHeight, 0xFF2D2D35, 1);

        // Drag Hue vertical slider handles
        float hy = sy + (hVal / 360f) * svHeight;
        ctx.batcher.outline(sliderX - 1, hy - 1, sliderX + sliderW + 1, hy + 1, 0xFF000000, 2);
        ctx.batcher.outline(sliderX - 1, hy - 1, sliderX + sliderW + 1, hy + 1, 0xFFFFFFFF, 1);
    }

    @Override
    public boolean mouseClicked(int mx, int my, int btn) {
        int svWidth = w - 22;
        int svHeight = h - 20;
        int sy = y + 18;

        // Click Hex text box
        if (mx >= x + 18 && mx < x + w && my >= y && my < y + 14) {
            CALUndoManager.pushState();
            hexFocused = true;
            hexInput = getHexCode();
            return true;
        }

        // Click SV Box
        if (mx >= x && mx < x + svWidth && my >= sy && my < sy + svHeight) {
            CALUndoManager.pushState();
            hexFocused = false;
            draggingSV = true;
            updateSVFromMouse(mx, my);
            return true;
        }

        // Click Hue Slider
        int sliderX = x + svWidth + 8;
        int sliderW = 14;
        if (mx >= sliderX && mx < sliderX + sliderW && my >= sy && my < sy + svHeight) {
            CALUndoManager.pushState();
            hexFocused = false;
            draggingHue = true;
            updateHueFromMouse(my);
            return true;
        }

        hexFocused = false;
        return false;
    }

    @Override
    public boolean mouseReleased(int mx, int my, int btn) {
        draggingSV = false;
        draggingHue = false;
        return false;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        if (draggingSV) {
            updateSVFromMouse((int) mx, (int) my);
            return true;
        }
        if (draggingHue) {
            updateHueFromMouse((int) my);
            return true;
        }
        return false;
    }

    private void updateSVFromMouse(int mx, int my) {
        int svWidth = w - 22;
        int svHeight = h - 20;
        int sy = y + 18;
        float dx = mx - x;
        float dy = my - sy;

        sVal = clamp(dx / svWidth, 0f, 1f);
        vVal = clamp(1f - (dy / svHeight), 0f, 1f);

        hsvToRgb();
        if (onChange != null) onChange.run();
    }

    private void updateHueFromMouse(int my) {
        int svHeight = h - 20;
        int sy = y + 18;
        float dy = my - sy;

        hVal = clamp(dy / svHeight, 0f, 1f) * 360f;

        hsvToRgb();
        if (onChange != null) onChange.run();
    }

    @Override
    public boolean keyPressed(int key, int scan, int action) {
        if (hexFocused) {
            if (key == GLFW.GLFW_KEY_BACKSPACE) {
                if (hexInput.length() > 1) {
                    hexInput = hexInput.substring(0, hexInput.length() - 1);
                }
                return true;
            }
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_ESCAPE) {
                hexFocused = false;
                return true;
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (hexFocused) {
            String valids = "0123456789abcdefABCDEF";
            if (valids.indexOf(chr) != -1 && hexInput.length() < 7) {
                hexInput += Character.toLowerCase(chr);
                // Automatically apply if full hex length reached
                if (hexInput.length() == 7) {
                    try {
                        int color = Integer.parseInt(hexInput.substring(1), 16);
                        this.r = ((color >> 16) & 0xFF) / 255.0f;
                        this.g = ((color >> 8) & 0xFF) / 255.0f;
                        this.b = (color & 0xFF) / 255.0f;
                        rgbToHsv(r, g, b);
                        if (onChange != null) onChange.run();
                    } catch (Exception ignored) {}
                }
            }
            return true;
        }
        return false;
    }
}
