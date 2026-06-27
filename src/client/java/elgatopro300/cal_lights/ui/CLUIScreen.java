package elgatopro300.cal_lights.ui;

import elgatopro300.cal_lights.graphics.CLBatcher;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public class CLUIScreen extends Screen {
    protected final CLUIElement root;
    private long tickCount = 0;

    public CLUIScreen(CLUIElement root) {
        super(Component.empty());
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
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractRenderState(context, mouseX, mouseY, delta);

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
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        // Override with no-op to disable vanilla background dim and blur shader
    }

    @Override
    public void tick() {
        super.tick();
        this.tickCount++;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean bool) {
        if (this.root != null && this.root.mouseClicked((int) click.x(), (int) click.y(), click.button())) {
            return true;
        }
        return super.mouseClicked(click, bool);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        if (this.root != null && this.root.mouseReleased((int) click.x(), (int) click.y(), click.button())) {
            return true;
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double deltaX, double deltaY) {
        if (this.root != null && this.root.mouseDragged(click.x(), click.y(), click.button(), deltaX, deltaY)) {
            return true;
        }
        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean keyPressed(KeyEvent key) {
        if (this.root != null && this.root.keyPressed(key.key(), key.scancode(), key.modifiers())) {
            return true;
        }
        return super.keyPressed(key);
    }

    @Override
    public boolean charTyped(CharacterEvent input) {
        if (this.root != null && this.root.charTyped((char) input.codepoint(), 0)) {
            return true;
        }
        return super.charTyped(input);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (this.root != null && this.root.scroll((int) mouseX, (int) mouseY, verticalAmount)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
