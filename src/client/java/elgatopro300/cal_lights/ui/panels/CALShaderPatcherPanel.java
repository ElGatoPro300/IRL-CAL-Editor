package elgatopro300.cal_lights.ui.panels;

import elgatopro300.cal_lights.shaders.CALShaderPatcher;
import elgatopro300.cal_lights.ui.CALKeys;
import elgatopro300.cal_lights.ui.CLUIContext;
import elgatopro300.cal_lights.ui.CLUIElement;
import elgatopro300.cal_lights.ui.L10n;

import net.minecraft.client.MinecraftClient;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

public class CALShaderPatcherPanel extends CLUIElement {
    private int activeTab = 0; // 0 = Apply Patch, 1 = Create Patch
    
    // Lists of scanned shaderpacks
    private final List<String> originalPacks = new ArrayList<>();
    private final List<String> patchFiles = new ArrayList<>();
    private final List<String> modifiedPacks = new ArrayList<>();

    // Selected items
    private String selectedOriginal = "";
    private String selectedPatchFile = "";
    private String selectedModified = "";

    // Input Names (Target Output names)
    private String outputPackName = "";
    private String outputPatchName = "";

    // Dropdown open states
    private int activeDropdown = 0; // 0 = none, 1 = original, 2 = patch, 3 = modified
    private int dropdownScroll = 0;

    // Status message
    private String statusText = "Ready";
    private int statusColor = 0xFFCCCCCC; // 0xFFCCCCCC = neutral, 0xFF00E676 = success, 0xFFEF5350 = error
    private boolean processing = false;

    private final Runnable onClose;

    public CALShaderPatcherPanel(Runnable onClose) {
        this.onClose = onClose;
        scanShaderpacks();
        
        // Pick default selections if available
        if (!originalPacks.isEmpty()) selectedOriginal = originalPacks.get(0);
        if (!patchFiles.isEmpty()) selectedPatchFile = patchFiles.get(0);
        if (!modifiedPacks.isEmpty()) selectedModified = modifiedPacks.get(0);

        updateDefaultNames();
    }

    private void scanShaderpacks() {
        originalPacks.clear();
        patchFiles.clear();
        modifiedPacks.clear();

        Path runDir = MinecraftClient.getInstance().runDirectory.toPath();
        Path shaderpacksDir = runDir.resolve("shaderpacks");
        Path patchesDir = runDir.resolve("config").resolve("cal_lights").resolve("patches");

        // Ensure directories exist
        try {
            if (!Files.exists(shaderpacksDir)) Files.createDirectories(shaderpacksDir);
            if (!Files.exists(patchesDir)) Files.createDirectories(patchesDir);
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Extract built-in patches from resources
        String[] builtinPatches = {"BSL_v10.0.calpatch", "ComplementaryReimagined_r5.8.1.calpatch"};
        for (String patchName : builtinPatches) {
            Path targetPath = patchesDir.resolve(patchName);
            try (InputStream is = CALShaderPatcherPanel.class.getResourceAsStream("/patches/" + patchName)) {
                if (is != null) {
                    Files.copy(is, targetPath, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // 1. Scan config/cal_lights/patches for .calpatch files
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(patchesDir)) {
            for (Path entry : stream) {
                String name = entry.getFileName().toString();
                if (name.endsWith(".calpatch")) {
                    patchFiles.add(name);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        // 2. Scan shaderpacks for original and modified shaderpacks
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(shaderpacksDir)) {
            for (Path entry : stream) {
                String name = entry.getFileName().toString();
                if (!name.endsWith(".txt") && !name.startsWith(".") && !name.endsWith(".json") && !name.endsWith(".calpatch")) {
                    originalPacks.add(name);
                    modifiedPacks.add(name);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void updateDefaultNames() {
        if (!selectedOriginal.isEmpty()) {
            String base = selectedOriginal;
            if (base.endsWith(".zip")) {
                base = base.substring(0, base.length() - 4);
            }
            // Strip any trailing " CAL Patch" or " CAL" to avoid accumulation
            if (base.endsWith(" CAL Patch")) {
                base = base.substring(0, base.length() - 10);
            } else if (base.endsWith(" CAL")) {
                base = base.substring(0, base.length() - 4);
            }
            outputPackName = base + " CAL Patch.zip";
            outputPatchName = base + ".calpatch";
        } else {
            outputPackName = "PatchedShader.zip";
            outputPatchName = "NewShaderPatch.calpatch";
        }
    }

    @Override
    public void render(CLUIContext ctx) {
        // Modal window container dimensions
        int pW = 380;
        int pH = 220;
        int pX = x + (w - pW) / 2;
        int pY = y + (h - pH) / 2;

        // Backdrop tint to isolate dialog focus
        ctx.batcher.box(x, y, x + w, y + h, 0xAA0B0B0E);

        // Main Premium Window Box
        ctx.batcher.box(pX, pY, pX + pW, pY + pH, 0xFF141418);
        ctx.batcher.outline(pX, pY, pX + pW, pY + pH, 0xFFFFAA00, 1);

        // Header Title Bar
        int headerH = 22;
        ctx.batcher.box(pX, pY, pX + pW, pY + headerH, 0xFF1A1A22);
        ctx.batcher.outline(pX, pY, pX + pW, pY + headerH, 0xFF2A2A35, 1);
        ctx.batcher.text(CALKeys.PATCHER_TITLE.get(), pX + 10, pY + 6, 0xFFFFAA00);

        // Close [X] Button in corner
        boolean hoverX = ctx.mouseX >= pX + pW - 20 && ctx.mouseX < pX + pW - 6 && ctx.mouseY >= pY + 4 && ctx.mouseY < pY + 18;
        ctx.batcher.text("X", pX + pW - 16, pY + 6, hoverX ? 0xFFEF5350 : 0xFF9E9E9E);

        // Subdescription
        ctx.batcher.text(CALKeys.PATCHER_DESC.get(), pX + 12, pY + headerH + 6, 0xFF888899);

        // Tab selection row
        int tabY = pY + headerH + 18;
        int tabW = (pW - 24) / 2;
        int tabH = 16;

        // TAB 0: Aplicar Parche
        boolean hoverT0 = ctx.mouseX >= pX + 12 && ctx.mouseX < pX + 12 + tabW && ctx.mouseY >= tabY && ctx.mouseY < tabY + tabH;
        int t0Bg = (activeTab == 0) ? 0xFF1976D2 : (hoverT0 ? 0xFF2A2A35 : 0xFF1C1C22);
        ctx.batcher.box(pX + 12, tabY, pX + 12 + tabW, tabY + tabH, t0Bg);
        ctx.batcher.outline(pX + 12, tabY, pX + 12 + tabW, tabY + tabH, (activeTab == 0) ? 0xFF64B5F6 : 0xFF3D3D4D, 1);
        ctx.batcher.text(CALKeys.PATCHER_APPLY.get(), pX + 12 + (tabW - MinecraftClient.getInstance().textRenderer.getWidth(CALKeys.PATCHER_APPLY.get())) / 2, tabY + 4, 0xFFFFFFFF);

        // TAB 1: Crear Parche
        boolean hoverT1 = ctx.mouseX >= pX + 12 + tabW + 2 && ctx.mouseX < pX + pW - 12 && ctx.mouseY >= tabY && ctx.mouseY < tabY + tabH;
        int t1Bg = (activeTab == 1) ? 0xFF1976D2 : (hoverT1 ? 0xFF2A2A35 : 0xFF1C1C22);
        ctx.batcher.box(pX + 12 + tabW + 2, tabY, pX + pW - 12, tabY + tabH, t1Bg);
        ctx.batcher.outline(pX + 12 + tabW + 2, tabY, pX + pW - 12, tabY + tabH, (activeTab == 1) ? 0xFF64B5F6 : 0xFF3D3D4D, 1);
        ctx.batcher.text(CALKeys.PATCHER_CREATE.get(), pX + 12 + tabW + 2 + (tabW - MinecraftClient.getInstance().textRenderer.getWidth(CALKeys.PATCHER_CREATE.get())) / 2, tabY + 4, 0xFFFFFFFF);

        // Tab Content drawing area
        int contentY = tabY + 22;

        if (activeTab == 0) {
            // ==========================================
            // TAB 0: APPLY PATCH CONTENT
            // ==========================================
            // 1. Pack Original Dropdown Selector
            ctx.batcher.text(CALKeys.PATCHER_ORIGINAL.get(), pX + 12, contentY + 4, 0xFFCCCCCC);
            int boxX = pX + 100;
            int boxW = pW - 112;
            int boxH = 15;
            boolean hoverSelOrig = ctx.mouseX >= boxX && ctx.mouseX < boxX + boxW && ctx.mouseY >= contentY && ctx.mouseY < contentY + boxH;
            ctx.batcher.box(boxX, contentY, boxX + boxW, contentY + boxH, hoverSelOrig ? 0xFF252530 : 0xFF181820);
            ctx.batcher.outline(boxX, contentY, boxX + boxW, contentY + boxH, 0xFF3E3E4D, 1);
            String displayOrig = selectedOriginal.isEmpty() ? "No shaders found" : selectedOriginal;
            ctx.batcher.text(displayOrig, boxX + 6, contentY + 3, 0xFFE0E0E0);
            ctx.batcher.text("v", boxX + boxW - 12, contentY + 3, 0xFF888899);

            // 2. Patch File Dropdown Selector
            contentY += 20;
            ctx.batcher.text(CALKeys.PATCHER_FILE.get(), pX + 12, contentY + 4, 0xFFCCCCCC);
            boolean hoverSelPatch = ctx.mouseX >= boxX && ctx.mouseX < boxX + boxW && ctx.mouseY >= contentY && ctx.mouseY < contentY + boxH;
            ctx.batcher.box(boxX, contentY, boxX + boxW, contentY + boxH, hoverSelPatch ? 0xFF252530 : 0xFF181820);
            ctx.batcher.outline(boxX, contentY, boxX + boxW, contentY + boxH, 0xFF3E3E4D, 1);
            String displayPatch = selectedPatchFile.isEmpty() ? "No patches found" : selectedPatchFile;
            ctx.batcher.text(displayPatch, boxX + 6, contentY + 3, 0xFFE0E0E0);
            ctx.batcher.text("v", boxX + boxW - 12, contentY + 3, 0xFF888899);

            // 3. Output pack preview
            contentY += 20;
            ctx.batcher.text(CALKeys.OUTPUT_PACK.get(), pX + 12, contentY + 4, 0xFFCCCCCC);
            ctx.batcher.box(boxX, contentY, boxX + boxW, contentY + boxH, 0xFF111115);
            ctx.batcher.outline(boxX, contentY, boxX + boxW, contentY + boxH, 0xFF26262D, 1);
            ctx.batcher.text(outputPackName, boxX + 6, contentY + 3, 0xFF777788);

            // 4. Action Button - Apply Patch
            contentY += 24;
            int actW = 100;
            int actH = 18;
            int actX = pX + pW - actW - 12;
            boolean hoverAct = ctx.mouseX >= actX && ctx.mouseX < actX + actW && ctx.mouseY >= contentY && ctx.mouseY < contentY + actH;
            int actBg = processing ? 0xFF3E3E4D : (hoverAct ? 0xFF2E7D32 : 0xFF1B5E20);
            ctx.batcher.box(actX, contentY, actX + actW, contentY + actH, actBg);
            ctx.batcher.outline(actX, contentY, actX + actW, contentY + actH, processing ? 0xFF555555 : 0xFF81C784, 1);
            String btnText = processing ? "Applying..." : CALKeys.PATCHER_APPLY.get();
            ctx.batcher.text(btnText, actX + (actW - MinecraftClient.getInstance().textRenderer.getWidth(btnText)) / 2, contentY + 5, 0xFFFFFFFF);

            // Close button next to it
            int clsW = 56;
            int clsX = actX - clsW - 6;
            boolean hoverCls = ctx.mouseX >= clsX && ctx.mouseX < clsX + clsW && ctx.mouseY >= contentY && ctx.mouseY < contentY + actH;
            ctx.batcher.box(clsX, contentY, clsX + clsW, contentY + actH, hoverCls ? 0xFF3E3E4D : 0xFF212126);
            ctx.batcher.outline(clsX, contentY, clsX + clsW, contentY + actH, 0xFF3D3D4D, 1);
            ctx.batcher.text(CALKeys.CLOSE.get(), clsX + (clsW - MinecraftClient.getInstance().textRenderer.getWidth(CALKeys.CLOSE.get())) / 2, contentY + 5, 0xFFCCCCCC);

            // 5. Live status report text
            ctx.batcher.text(String.format(CALKeys.PATCHER_STATUS.get(), statusText), pX + 12, contentY + 6, statusColor);

        } else {
            // ==========================================
            // TAB 1: CREATE PATCH CONTENT
            // ==========================================
            // 1. Pack Original Dropdown Selector
            ctx.batcher.text(CALKeys.PATCHER_ORIGINAL.get(), pX + 12, contentY + 4, 0xFFCCCCCC);
            int boxX = pX + 100;
            int boxW = pW - 112;
            int boxH = 15;
            boolean hoverSelOrig = ctx.mouseX >= boxX && ctx.mouseX < boxX + boxW && ctx.mouseY >= contentY && ctx.mouseY < contentY + boxH;
            ctx.batcher.box(boxX, contentY, boxX + boxW, contentY + boxH, hoverSelOrig ? 0xFF252530 : 0xFF181820);
            ctx.batcher.outline(boxX, contentY, boxX + boxW, contentY + boxH, 0xFF3E3E4D, 1);
            String displayOrig = selectedOriginal.isEmpty() ? "No shaders found" : selectedOriginal;
            ctx.batcher.text(displayOrig, boxX + 6, contentY + 3, 0xFFE0E0E0);
            ctx.batcher.text("v", boxX + boxW - 12, contentY + 3, 0xFF888899);

            // 2. Pack Modificado Dropdown Selector
            contentY += 20;
            ctx.batcher.text(CALKeys.PATCHER_MODIFIED.get(), pX + 12, contentY + 4, 0xFFCCCCCC);
            boolean hoverSelMod = ctx.mouseX >= boxX && ctx.mouseX < boxX + boxW && ctx.mouseY >= contentY && ctx.mouseY < contentY + boxH;
            ctx.batcher.box(boxX, contentY, boxX + boxW, contentY + boxH, hoverSelMod ? 0xFF252530 : 0xFF181820);
            ctx.batcher.outline(boxX, contentY, boxX + boxW, contentY + boxH, 0xFF3E3E4D, 1);
            String displayMod = selectedModified.isEmpty() ? "No modified shaders" : selectedModified;
            ctx.batcher.text(displayMod, boxX + 6, contentY + 3, 0xFFE0E0E0);
            ctx.batcher.text("v", boxX + boxW - 12, contentY + 3, 0xFF888899);

            // 3. Output patch preview
            contentY += 20;
            ctx.batcher.text(CALKeys.OUTPUT_PATCH.get(), pX + 12, contentY + 4, 0xFFCCCCCC);
            ctx.batcher.box(boxX, contentY, boxX + boxW, contentY + boxH, 0xFF111115);
            ctx.batcher.outline(boxX, contentY, boxX + boxW, contentY + boxH, 0xFF26262D, 1);
            ctx.batcher.text(outputPatchName, boxX + 6, contentY + 3, 0xFF777788);

            // 4. Action Button - Create Patch
            contentY += 24;
            int actW = 100;
            int actH = 18;
            int actX = pX + pW - actW - 12;
            boolean hoverAct = ctx.mouseX >= actX && ctx.mouseX < actX + actW && ctx.mouseY >= contentY && ctx.mouseY < contentY + actH;
            int actBg = processing ? 0xFF3E3E4D : (hoverAct ? 0xFF1565C0 : 0xFF0D47A1);
            ctx.batcher.box(actX, contentY, actX + actW, contentY + actH, actBg);
            ctx.batcher.outline(actX, contentY, actX + actW, contentY + actH, processing ? 0xFF555555 : 0xFF64B5F6, 1);
            String btnText = processing ? "Creating..." : CALKeys.PATCHER_CREATE.get();
            ctx.batcher.text(btnText, actX + (actW - MinecraftClient.getInstance().textRenderer.getWidth(btnText)) / 2, contentY + 5, 0xFFFFFFFF);

            // Close button
            int clsW = 56;
            int clsX = actX - clsW - 6;
            boolean hoverCls = ctx.mouseX >= clsX && ctx.mouseX < clsX + clsW && ctx.mouseY >= contentY && ctx.mouseY < contentY + actH;
            ctx.batcher.box(clsX, contentY, clsX + clsW, contentY + actH, hoverCls ? 0xFF3E3E4D : 0xFF212126);
            ctx.batcher.outline(clsX, contentY, clsX + clsW, contentY + actH, 0xFF3D3D4D, 1);
            ctx.batcher.text(CALKeys.CLOSE.get(), clsX + (clsW - MinecraftClient.getInstance().textRenderer.getWidth(CALKeys.CLOSE.get())) / 2, contentY + 5, 0xFFCCCCCC);

            // 5. Live status report text
            ctx.batcher.text(String.format(CALKeys.PATCHER_STATUS.get(), statusText), pX + 12, contentY + 6, statusColor);
        }

        // ==========================================
        // OVERLAY DRAWING FOR EXPANDED DROPDOWNS
        // ==========================================
        if (activeDropdown != 0) {
            List<String> listToRender = (activeDropdown == 1) ? originalPacks : ((activeDropdown == 2) ? patchFiles : modifiedPacks);
            int dropX = pX + 100;
            
            // Calculate position EXACTLY below the dropdown box (boxH = 15)
            int dropY = pY + headerH + 40 + 15; // default is below original pack dropdown
            if (activeDropdown == 2 || activeDropdown == 3) {
                dropY = pY + headerH + 60 + 15; // below patch or modified pack dropdown
            }

            int dropItemH = 15;
            int maxItems = 5;
            int dropH = Math.min(listToRender.size(), maxItems) * dropItemH;
            
            // Draw background shadow
            ctx.batcher.box(dropX, dropY, dropX + (pW - 112), dropY + dropH, 0xFF1A1A22);
            ctx.batcher.outline(dropX, dropY, dropX + (pW - 112), dropY + dropH, 0xFFFFAA00, 1);

            int startIdx = dropdownScroll;
            int endIdx = Math.min(listToRender.size(), startIdx + maxItems);
            
            boolean hasScrollbar = listToRender.size() > maxItems;
            int listW = pW - 112;
            int contentW = hasScrollbar ? (listW - 6) : listW;
            
            if (hasScrollbar) {
                // Draw thin scrollbar track
                int sbX = dropX + listW - 5;
                ctx.batcher.box(sbX, dropY + 2, sbX + 3, dropY + dropH - 2, 0xFF141418);
                
                // Draw scrollbar thumb
                float progress = (float) startIdx / (listToRender.size() - maxItems);
                int thumbH = Math.max(8, (int) ((float) maxItems / listToRender.size() * (dropH - 4)));
                int thumbY = dropY + 2 + (int) (progress * (dropH - 4 - thumbH));
                ctx.batcher.box(sbX, thumbY, sbX + 3, thumbY + thumbH, 0xFFFFAA00);
            }

            for (int i = startIdx; i < endIdx; i++) {
                String val = listToRender.get(i);
                int renderIdx = i - startIdx;
                int itemY = dropY + renderIdx * dropItemH;
                boolean hoverItem = ctx.mouseX >= dropX && ctx.mouseX < dropX + contentW && ctx.mouseY >= itemY && ctx.mouseY < itemY + dropItemH;
                
                ctx.batcher.box(dropX + 1, itemY + 1, dropX + contentW - 1, itemY + dropItemH - 1, hoverItem ? 0xFF2B2B38 : 0xFF1A1A22);
                ctx.batcher.text(val, dropX + 6, itemY + 3, 0xFFE0E0E0);
            }
        }
    }

    @Override
    public boolean mouseClicked(int mx, int my, int btn) {
        int pW = 380;
        int pH = 220;
        int pX = x + (w - pW) / 2;
        int pY = y + (h - pH) / 2;
        int headerH = 22;

        // Bounding box of the dialog
        if (mx < pX || mx >= pX + pW || my < pY || my >= pY + pH) {
            if (onClose != null) onClose.run();
            return true;
        }

        // Close [X] in corner
        if (mx >= pX + pW - 20 && mx < pX + pW - 6 && my >= pY + 4 && my < pY + 18) {
            if (onClose != null) onClose.run();
            return true;
        }

        // Active dropdown overlay click handling (higher priority than background controls!)
        if (activeDropdown != 0) {
            List<String> listToRender = (activeDropdown == 1) ? originalPacks : ((activeDropdown == 2) ? patchFiles : modifiedPacks);
            int dropX = pX + 100;
            
            // Calculate position EXACTLY below the dropdown box (boxH = 15)
            int dropY = pY + headerH + 40 + 15; // default is below original pack dropdown
            if (activeDropdown == 2 || activeDropdown == 3) {
                dropY = pY + headerH + 60 + 15; // below patch or modified pack dropdown
            }

            int dropItemH = 15;
            int maxItems = 5;
            int dropH = Math.min(listToRender.size(), maxItems) * dropItemH;
            
            if (mx >= dropX && mx < dropX + (pW - 112) && my >= dropY && my < dropY + dropH) {
                int clickedIdx = dropdownScroll + (my - dropY) / dropItemH;
                if (clickedIdx >= 0 && clickedIdx < listToRender.size()) {
                    String selected = listToRender.get(clickedIdx);
                    if (activeDropdown == 1) {
                        selectedOriginal = selected;
                        updateDefaultNames();
                    } else if (activeDropdown == 2) {
                        selectedPatchFile = selected;
                    } else if (activeDropdown == 3) {
                        selectedModified = selected;
                    }
                }
                activeDropdown = 0;
                dropdownScroll = 0;
                return true;
            }
            activeDropdown = 0;
            dropdownScroll = 0;
            return true;
        }

        // Tab switcher row clicks
        int tabY = pY + headerH + 18;
        int tabW = (pW - 24) / 2;
        int tabH = 16;
        if (mx >= pX + 12 && mx < pX + 12 + tabW && my >= tabY && my < tabY + tabH) {
            activeTab = 0;
            activeDropdown = 0;
            statusText = "Ready";
            statusColor = 0xFFCCCCCC;
            return true;
        }
        if (mx >= pX + 12 + tabW + 2 && mx < pX + pW - 12 && my >= tabY && my < tabY + tabH) {
            activeTab = 1;
            activeDropdown = 0;
            statusText = "Ready";
            statusColor = 0xFFCCCCCC;
            return true;
        }

        int contentY = tabY + 22;
        int boxX = pX + 100;
        int boxW = pW - 112;
        int boxH = 15;

        if (activeTab == 0) {
            // Dropdown 1: Original Pack
            if (mx >= boxX && mx < boxX + boxW && my >= contentY && my < contentY + boxH) {
                activeDropdown = 1;
                dropdownScroll = 0;
                return true;
            }

            // Dropdown 2: Patch File
            contentY += 20;
            if (mx >= boxX && mx < boxX + boxW && my >= contentY && my < contentY + boxH) {
                activeDropdown = 2;
                dropdownScroll = 0;
                return true;
            }

            // Action Buttons
            contentY += 44;
            int actW = 100;
            int actH = 18;
            int actX = pX + pW - actW - 12;

            // Apply Patch Action Trigger
            if (mx >= actX && mx < actX + actW && my >= contentY && my < contentY + actH) {
                if (!processing) {
                    triggerApplyPatch();
                }
                return true;
            }

            // Close Action Trigger
            int clsW = 56;
            int clsX = actX - clsW - 6;
            if (mx >= clsX && mx < clsX + clsW && my >= contentY && my < contentY + actH) {
                if (onClose != null) onClose.run();
                return true;
            }

        } else {
            // Dropdown 1: Original Pack
            if (mx >= boxX && mx < boxX + boxW && my >= contentY && my < contentY + boxH) {
                activeDropdown = 1;
                dropdownScroll = 0;
                return true;
            }

            // Dropdown 3: Modified Pack
            contentY += 20;
            if (mx >= boxX && mx < boxX + boxW && my >= contentY && my < contentY + boxH) {
                activeDropdown = 3;
                dropdownScroll = 0;
                return true;
            }

            // Action Buttons
            contentY += 44;
            int actW = 100;
            int actH = 18;
            int actX = pX + pW - actW - 12;

            // Create Patch Action Trigger
            if (mx >= actX && mx < actX + actW && my >= contentY && my < contentY + actH) {
                if (!processing) {
                    triggerCreatePatch();
                }
                return true;
            }

            // Close Action Trigger
            int clsW = 56;
            int clsX = actX - clsW - 6;
            if (mx >= clsX && mx < clsX + clsW && my >= contentY && my < contentY + actH) {
                if (onClose != null) onClose.run();
                return true;
            }
        }

        return true;
    }

    @Override
    public boolean scroll(int mx, int my, double amount) {
        if (activeDropdown != 0) {
            List<String> list = (activeDropdown == 1) ? originalPacks : ((activeDropdown == 2) ? patchFiles : modifiedPacks);
            int maxScroll = Math.max(0, list.size() - 5);
            dropdownScroll -= (int) amount;
            if (dropdownScroll < 0) dropdownScroll = 0;
            if (dropdownScroll > maxScroll) dropdownScroll = maxScroll;
            return true;
        }
        return false;
    }

    private void triggerApplyPatch() {
        if (selectedOriginal.isEmpty() || selectedPatchFile.isEmpty()) {
            statusText = "Missing selections";
            statusColor = 0xFFEF5350;
            return;
        }

        processing = true;
        statusText = "Applying Patch...";
        statusColor = 0xFFFFAA00;

        new Thread(() -> {
            try {
                Path runDir = MinecraftClient.getInstance().runDirectory.toPath();
                Path original = runDir.resolve("shaderpacks").resolve(selectedOriginal);
                Path patch = runDir.resolve("config").resolve("cal_lights").resolve("patches").resolve(selectedPatchFile);
                Path output = runDir.resolve("shaderpacks").resolve(outputPackName);

                CALShaderPatcher.applyPatch(original, patch, output);

                statusText = CALKeys.PATCHER_SUCCESS.get();
                statusColor = 0xFF00E676;
            } catch (Exception e) {
                statusText = String.format(CALKeys.PATCHER_ERROR.get(), e.getMessage());
                statusColor = 0xFFEF5350;
                e.printStackTrace();
            } finally {
                processing = false;
                scanShaderpacks();
            }
        }).start();
    }

    private void triggerCreatePatch() {
        if (selectedOriginal.isEmpty() || selectedModified.isEmpty()) {
            statusText = "Missing selections";
            statusColor = 0xFFEF5350;
            return;
        }

        processing = true;
        statusText = "Creating Patch...";
        statusColor = 0xFFFFAA00;

        new Thread(() -> {
            try {
                Path runDir = MinecraftClient.getInstance().runDirectory.toPath();
                Path original = runDir.resolve("shaderpacks").resolve(selectedOriginal);
                Path modified = runDir.resolve("shaderpacks").resolve(selectedModified);
                Path patchOut = runDir.resolve("config").resolve("cal_lights").resolve("patches").resolve(outputPatchName);

                CALShaderPatcher.createPatch(original, modified, patchOut);

                statusText = CALKeys.PATCHER_SUCCESS.get();
                statusColor = 0xFF00E676;
            } catch (Exception e) {
                statusText = String.format(CALKeys.PATCHER_ERROR.get(), e.getMessage());
                statusColor = 0xFFEF5350;
                e.printStackTrace();
            } finally {
                processing = false;
                scanShaderpacks();
            }
        }).start();
    }
}
