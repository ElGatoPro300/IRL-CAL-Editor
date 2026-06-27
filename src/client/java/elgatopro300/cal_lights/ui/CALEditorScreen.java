package elgatopro300.cal_lights.ui;

import elgatopro300.cal_lights.gizmo.LightGizmo;
import elgatopro300.cal_lights.ui.panels.CALEditorPanel;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.phys.Vec3;

import org.lwjgl.glfw.GLFW;

public class CALEditorScreen extends CLUIScreen {
    public static int editorGuiScale = 2;
    private int lastGuiScale;
    private boolean originalNoClip = false;
    private boolean originalHudHidden = false;
    private CameraType originalPerspective;
    private double originalX;
    private double originalY;
    private double originalZ;
    private float originalYaw;
    private float originalPitch;

    public CALEditorScreen() {
        super(new CALEditorPanel());
    }

    @Override
    public void added() {
        CalSettings.INSTANCE.load();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            this.originalNoClip = mc.player.noPhysics;
            this.originalX = mc.player.getX();
            this.originalY = mc.player.getY();
            this.originalZ = mc.player.getZ();
            this.originalYaw = mc.player.getYRot();
            this.originalPitch = mc.player.getXRot();
        }
        this.originalPerspective = mc.options.getCameraType();
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        this.originalHudHidden = mc.options.hideGui;
        mc.options.hideGui = true;
        this.lastGuiScale = mc.options.guiScale().get();
        mc.options.guiScale().set(CalSettings.INSTANCE.guiScale);
        mc.resizeDisplay();
        super.added();
    }

    @Override
    public void removed() {
        CALEditorPanel.currentlyPressedKeys.clear();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.noPhysics = this.originalNoClip;
            mc.player.setYRot(this.originalYaw);
            mc.player.setXRot(this.originalPitch);
            mc.player.yHeadRot = this.originalYaw;
            mc.player.yBodyRot = this.originalYaw;
            mc.player.setPos(this.originalX, this.originalY, this.originalZ);
            mc.player.setDeltaMovement(0, 0, 0);
        }
        mc.options.hideGui = this.originalHudHidden;
        if (this.originalPerspective != null) {
            mc.options.setCameraType(this.originalPerspective);
        }
        CalSettings.INSTANCE.guiScale = mc.options.guiScale().get();
        CalSettings.INSTANCE.save();
        mc.options.guiScale().set(this.lastGuiScale);
        mc.resizeDisplay();
        super.removed();
    }

    @Override
    public void onClose() {
        if (this.root instanceof CALEditorPanel panel) {
            if (!panel.closing) {
                panel.closing = true;
                return; // Wait for the transition to finish!
            }
        }
        super.onClose();
    }

    @Override
    public boolean keyPressed(KeyEvent key) {
        if (!CALEditorPanel.currentlyPressedKeys.contains(key.key())) {
            CALEditorPanel.currentlyPressedKeys.add(key.key());
        }
        return super.keyPressed(key);
    }

    @Override
    public boolean keyReleased(KeyEvent key) {
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
    public boolean mouseClicked(MouseButtonEvent click, boolean bool) {
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
    public boolean mouseReleased(MouseButtonEvent click) {
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
    public boolean mouseDragged(MouseButtonEvent click, double deltaX, double deltaY) {
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
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) {
                    mc.player.setYRot(mc.player.getYRot() + (float) deltaX * 0.15f);
                    mc.player.setXRot(Math.max(-90.0f, Math.min(90.0f, mc.player.getXRot() + (float) deltaY * 0.15f)));
                    return true;
                }
            }
        }
        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            // Freeze the player and allow block clipping
            mc.player.setDeltaMovement(0, 0, 0);
            mc.player.noPhysics = true;

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

                Vec3 move = Vec3.ZERO;
                float yawRad = (float) Math.toRadians(mc.player.getYRot());
                Vec3 fwd = mc.player.getViewVector(1.0f);
                Vec3 rgt = new Vec3(-Math.cos(yawRad), 0, -Math.sin(yawRad));

                if (forward) move = move.add(fwd);
                if (backward) move = move.subtract(fwd);
                if (right) move = move.add(rgt);
                if (left) move = move.subtract(rgt);
                if (up) move = move.add(0, 1, 0);
                if (down) move = move.add(0, -1, 0);

                if (move.lengthSqr() > 0) {
                    move = move.normalize();
                    double speed = 0.15 * CALEditorPanel.getCameraSpeed() * mc.getDeltaTracker().getGameTimeDeltaTicks();
                    if (CALEditorPanel.currentlyPressedKeys.contains(GLFW.GLFW_KEY_LEFT_CONTROL) || CALEditorPanel.currentlyPressedKeys.contains(GLFW.GLFW_KEY_RIGHT_CONTROL)) {
                        speed *= 3.0; // Fast flight
                    }
                    if (CALEditorPanel.currentlyPressedKeys.contains(GLFW.GLFW_KEY_LEFT_ALT) || CALEditorPanel.currentlyPressedKeys.contains(GLFW.GLFW_KEY_RIGHT_ALT)) {
                        speed *= 0.25; // Slower movement
                    }
                    Vec3 vel = move.scale(speed);
                    mc.player.setPos(mc.player.getX() + vel.x, mc.player.getY() + vel.y, mc.player.getZ() + vel.z);
                }
            }
        }
        super.render(context, mouseX, mouseY, delta);
        if (this.root instanceof CALEditorPanel panel && panel.closing) {
            if (panel.isFullyClosed()) {
                this.onClose();
            }
        }
    }
}
