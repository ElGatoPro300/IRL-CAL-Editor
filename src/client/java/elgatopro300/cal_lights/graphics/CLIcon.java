package elgatopro300.cal_lights.graphics;

public class CLIcon {
    public final CLTexture texture;
    public final CLTexture staticTexture;
    public final CLTexture tintTexture;
    public final int x;
    public final int y;
    public final int w;
    public final int h;

    public CLIcon(CLTexture texture, int x, int y, int w, int h) {
        this.texture = texture;
        this.staticTexture = null;
        this.tintTexture = null;
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
    }

    public CLIcon(CLTexture staticTexture, CLTexture tintTexture, int x, int y, int w, int h) {
        this.texture = staticTexture;
        this.staticTexture = staticTexture;
        this.tintTexture = tintTexture;
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
    }
}
