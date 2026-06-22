package elgatopro300.cal_lights.ui;

public abstract class CLUIElement {
    public int x;
    public int y;
    public int w;
    public int h;

    public abstract void render(CLUIContext ctx);

    public boolean mouseClicked(int mx, int my, int btn) {
        return false;
    }

    public boolean mouseReleased(int mx, int my, int btn) {
        return false;
    }

    public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        return false;
    }

    public boolean keyPressed(int key, int scan, int action) {
        return false;
    }

    public boolean charTyped(char chr, int modifiers) {
        return false;
    }

    public boolean scroll(int mx, int my, double amount) {
        return false;
    }

    public boolean isHovered(int mx, int my) {
        return mx >= this.x && mx < this.x + this.w && my >= this.y && my < this.y + this.h;
    }

    public void resize(int px, int py, int pw, int ph) {
        this.x = px;
        this.y = py;
        this.w = pw;
        this.h = ph;
    }
}
