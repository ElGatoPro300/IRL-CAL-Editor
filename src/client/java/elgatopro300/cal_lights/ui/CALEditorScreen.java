package elgatopro300.cal_lights.ui;

import elgatopro300.cal_lights.gizmo.LightGizmo;
import elgatopro300.cal_lights.ui.panels.CALEditorPanel;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.option.Perspective;
import net.minecraft.util.math.Vec3d;

import org.lwjgl.glfw.GLFW;

public class CALEditorScreen extends CLUIScreen {
    public static int editorGuiScale = 2;
    private int lastGuiScale;
    private boolean originalNoClip = false;
    private boolean originalHudHidden = false;
    private Perspective originalPerspective;
    private double originalX;
    private double originalY;
    private double originalZ;
    private float originalYaw;
    private float originalPitch;

    public CALEditorScreen() {
        super(new CALEditorPanel());
    }

    @Override
    public void onDisplayed() {
        CalSettings.INSTANCE.load();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            this.originalNoClip = mc.player.noClip;
            this.originalX = mc.player.getX();
            this.originalY = mc.player.getY();
            this.originalZ = mc.player.getZ();
            this.originalYaw = mc.player.getYaw();
            this.originalPitch = mc.player.getPitch();
        }
        this.originalPerspective = mc.options.getPerspective();
        mc.options.setPerspective(Perspective.FIRST_PERSON);
        this.originalHudHidden = mc.options.hudHidden;
        mc.options.hudHidden = true;
        this.lastGuiScale = mc.options.getGuiScale().getValue();
        mc.options.getGuiScale().setValue(CalSettings.INSTANCE.guiScale);
        mc.onResolutionChanged();
        super.onDisplayed();
    }

    @Override
    public void removed() {
        CALEditorPanel.currentlyPressedKeys.clear();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            mc.player.noClip = this.originalNoClip;
            mc.player.setYaw(this.originalYaw);
            mc.player.setPitch(this.originalPitch);
            mc.player.headYaw = this.originalYaw;
            mc.player.bodyYaw = this.originalYaw;
            mc.player.setPosition(this.originalX, this.originalY, this.originalZ);
            mc.player.setVelocity(0, 0, 0);
        }
        mc.options.hudHidden = this.originalHudHidden;
        if (this.originalPerspective != null) {
            mc.options.setPerspective(this.originalPerspective);
        }
        CalSettings.INSTANCE.guiScale = mc.options.getGuiScale().getValue();
        CalSettings.INSTANCE.save();
        mc.options.getGuiScale().setValue(this.lastGuiScale);
        mc.onResolutionChanged();
        super.removed();
    }

    @Override
    public void close() {
        if (this.root instanceof CALEditorPanel panel) {
            if (!panel.closing) {
                panel.closing = true;
                return; // Wait for the transition to finish!
            }
        }
        super.close();
    }

    @Override
    public boolean keyPressed(KeyInput key) {
        if (!CALEditorPanel.currentlyPressedKeys.contains(key.key())) {
            CALEditorPanel.currentlyPressedKeys.add(key.key());
        }
        return super.keyPressed(key);
    }

    @Override
    public boolean keyReleased(KeyInput key) {
        CALEditorPanel.currentlyPressedKeys.remove(Integer.valueOf(key.key()));
        return super.keyReleased(key);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        CALEditorPanel.lastScrollX = horizontalAmount;
        if (this.root != null && this.root.scroll((int) mouseX, (int) mouseY, verticalAmount)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseClicked(Click click, boolean bool) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();
        if (this.root != null && this.root.mouseClicked((int) mouseX, (int) mouseY, button)) {
            return true;
        }
        
        // Restrict Gizmo clicks to viewport area
        int leftPanelW = CALEditorPanel.showLeftSidebar ? CALEditorPanel.leftSidebarW : 0;
        int rightPanelW = CALEditorPanel.showRightSidebar ? CALEditorPanel.rightSidebarW : 0;
        int topBarH = 40;
        if (mouseX >= leftPanelW && mouseX <= this.width - rightPanelW && mouseY >= topBarH && mouseY <= this.height) {
            if (LightGizmo.INSTANCE.onMouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        return super.mouseClicked(click, bool);
    }

    @Override
    public boolean mouseReleased(Click click) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();
        if (this.root != null && this.root.mouseReleased((int) mouseX, (int) mouseY, button)) {
            return true;
        }
        if (LightGizmo.INSTANCE.onMouseReleased(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();
        if (this.root != null && this.root.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) {
            return true;
        }
        if (LightGizmo.INSTANCE.onMouseDragged(mouseX, mouseY, button, deltaX, deltaY)) {
            return true;
        }

        // Camera rotation if dragging within viewport with Left or Right Mouse Button
        int leftPanelW = CALEditorPanel.showLeftSidebar ? CALEditorPanel.leftSidebarW : 0;
        int rightPanelW = CALEditorPanel.showRightSidebar ? CALEditorPanel.rightSidebarW : 0;
        int topBarH = 40;
        if (mouseX >= leftPanelW && mouseX <= this.width - rightPanelW && mouseY >= topBarH && mouseY <= this.height) {
            if (button == 0 || button == 1) {
                MinecraftClient mc = MinecraftClient.getInstance();
                if (mc.player != null) {
                    mc.player.setYaw(mc.player.getYaw() + (float) deltaX * 0.15f);
                    mc.player.setPitch(Math.max(-90.0f, Math.min(90.0f, mc.player.getPitch() + (float) deltaY * 0.15f)));
                    return true;
                }
            }
        }
        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            // Freeze the player and allow block clipping
            mc.player.setVelocity(0, 0, 0);
            mc.player.noClip = true;

            boolean settingsOpen = false;
            boolean searchFocused = false;
            if (this.root instanceof CALEditorPanel panel) {
                settingsOpen = panel.showSettingsPopup;
                searchFocused = panel.searchFocused;
            }

            if (!settingsOpen && !searchFocused) {
                boolean forward = CALEditorPanel.currentlyPressedKeys.contains(GLFW.GLFW_KEY_W);
                boolean backward = CALEditorPanel.currentlyPressedKeys.contains(GLFW.GLFW_KEY_S);
                boolean left = CALEditorPanel.currentlyPressedKeys.contains(GLFW.GLFW_KEY_A);
                boolean right = CALEditorPanel.currentlyPressedKeys.contains(GLFW.GLFW_KEY_D);
                boolean up = CALEditorPanel.currentlyPressedKeys.contains(GLFW.GLFW_KEY_SPACE);
                boolean down = CALEditorPanel.currentlyPressedKeys.contains(GLFW.GLFW_KEY_LEFT_SHIFT);

                Vec3d move = Vec3d.ZERO;
                float yawRad = (float) Math.toRadians(mc.player.getYaw());
                Vec3d fwd = mc.player.getRotationVec(1.0f);
                Vec3d rgt = new Vec3d(-Math.cos(yawRad), 0, -Math.sin(yawRad));

                if (forward) move = move.add(fwd);
                if (backward) move = move.subtract(fwd);
                if (right) move = move.add(rgt);
                if (left) move = move.subtract(rgt);
                if (up) move = move.add(0, 1, 0);
                if (down) move = move.add(0, -1, 0);

                if (move.lengthSquared() > 0) {
                    move = move.normalize();
                    double speed = 0.15 * CALEditorPanel.getCameraSpeed() * mc.getRenderTickCounter().getDynamicDeltaTicks();
                    if (CALEditorPanel.currentlyPressedKeys.contains(GLFW.GLFW_KEY_LEFT_CONTROL) || CALEditorPanel.currentlyPressedKeys.contains(GLFW.GLFW_KEY_RIGHT_CONTROL)) {
                        speed *= 3.0; // Fast flight
                    }
                    if (CALEditorPanel.currentlyPressedKeys.contains(GLFW.GLFW_KEY_LEFT_ALT) || CALEditorPanel.currentlyPressedKeys.contains(GLFW.GLFW_KEY_RIGHT_ALT)) {
                        speed *= 0.25; // Slower movement
                    }
                    Vec3d vel = move.multiply(speed);
                    mc.player.setPosition(mc.player.getX() + vel.x, mc.player.getY() + vel.y, mc.player.getZ() + vel.z);
                }
            }
        }
        // Draw the 3D gizmo/billboards in the GUI phase (over the composited
        // world, under the UI panels) so they survive Iris shader pipelines.
        LightGizmo.INSTANCE.renderOverlay(context);

        super.render(context, mouseX, mouseY, delta);
        if (this.root instanceof CALEditorPanel panel && panel.closing) {
            if (panel.isFullyClosed()) {
                this.close();
            }
        }
    }
}
