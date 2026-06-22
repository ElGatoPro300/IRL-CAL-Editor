package elgatopro300.cal_lights.ui;

import elgatopro300.cal_lights.graphics.CLBatcher;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

public class CLUIScreen extends Screen {
    protected final CLUIElement root;
    private long tickCount = 0;

    public CLUIScreen(CLUIElement root) {
        super(Text.empty());
        this.client = MinecraftClient.getInstance();
        this.root = root;
    }

    public CLUIElement getRoot() {
        return this.root;
    }

    @Override
    protected void init() {
        super.init();
        if (this.root != null) {
            this.root.resize(0, 0, this.width, this.height);
        }
    }

    @Override
    public void resize(MinecraftClient client, int width, int height) {
        super.resize(client, width, height);
        if (this.root != null) {
            this.root.resize(0, 0, width, height);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        CLUIContext uiCtx = new CLUIContext();
        uiCtx.batcher = new CLBatcher(context);
        uiCtx.mouseX = mouseX;
        uiCtx.mouseY = mouseY;
        uiCtx.screenW = this.width;
        uiCtx.screenH = this.height;
        uiCtx.tick = this.tickCount;

        if (this.root != null) {
            this.root.render(uiCtx);
        }
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // Override with no-op to disable vanilla background dim and blur shader
    }

    @Override
    public void tick() {
        super.tick();
        this.tickCount++;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.root != null && this.root.mouseClicked((int) mouseX, (int) mouseY, button)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (this.root != null && this.root.mouseReleased((int) mouseX, (int) mouseY, button)) {
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (this.root != null && this.root.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.root != null && this.root.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (this.root != null && this.root.charTyped(chr, modifiers)) {
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (this.root != null && this.root.scroll((int) mouseX, (int) mouseY, verticalAmount)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
