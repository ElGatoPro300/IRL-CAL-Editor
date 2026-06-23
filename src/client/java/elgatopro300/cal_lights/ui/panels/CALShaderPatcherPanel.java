package elgatopro300.cal_lights.ui.panels;

import elgatopro300.cal_lights.graphics.CalLightsIcons;
import elgatopro300.cal_lights.ui.CALKeys;
import elgatopro300.cal_lights.ui.CLUIContext;
import elgatopro300.cal_lights.ui.CLUIElement;
import elgatopro300.cal_lights.ui.L10n;
import net.minecraft.client.MinecraftClient;
import org.qualet.irl.patcher.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CALShaderPatcherPanel extends CLUIElement {
    private static final Logger LOG = LoggerFactory.getLogger("IRL CAL Editor Patcher");

    private List<String> packs = List.of();
    private List<Path> patches = List.of();
    private List<String> patchLabels = List.of();

    private int selPack = -1;
    private int selPatch = -1;

    private boolean newPackEachTime = false;

    private int parsedPatch = -1;
    private boolean patchBroken;
    private String patchTarget = "";

    private static final int ST_GUARD = 0;
    private static final int ST_OK = 1;
    private static final int ST_ERR = 2;

    private String statusKey = "cal.ui.patcher_status_selectboth";
    private String statusArg = null;
    private int statusKind = ST_GUARD;

    private int packsScrollY = 0;
    private int patchesScrollY = 0;

    private final Runnable onClose;

    public CALShaderPatcherPanel(Runnable onClose) {
        this.onClose = onClose;
        reload();
    }

    public void reload() {
        String keepPack = (selPack >= 0 && selPack < packs.size()) ? packs.get(selPack) : null;
        Path keepPatch = (selPatch >= 0 && selPatch < patches.size()) ? patches.get(selPatch) : null;

        packs = Shaderpacks.list();
        patches = PatchLibrary.list();

        List<String> labels = new ArrayList<>(patches.size());
        for (Path p : patches) {
            labels.add(p.getFileName().toString());
        }
        patchLabels = labels;

        selPack = (keepPack == null) ? -1 : packs.indexOf(keepPack);
        selPatch = (keepPatch == null) ? -1 : patches.indexOf(keepPatch);
        parsedPatch = -1; // force parse refresh
    }

    private void parseSelectedPatch() {
        parsedPatch = selPatch;
        patchBroken = false;
        patchTarget = "";

        if (selPatch < 0 || selPatch >= patches.size()) {
            return;
        }

        IrlPatch parsed;
        try {
            parsed = IrlPatchParser.parse(Files.readString(patches.get(selPatch), StandardCharsets.UTF_8));
        } catch (Exception e) {
            patchBroken = true;
            LOG.warn("failed to parse patch {}", patches.get(selPatch), e);
            return;
        }

        patchTarget = parsed.target;

        if (selPack < 0 && patchTarget != null && !patchTarget.isEmpty()) {
            int match = -1;
            for (int i = 0; i < packs.size(); i++) {
                if (packMatchesTarget(packs.get(i), patchTarget)) {
                    if (match >= 0) {
                        match = -1;
                        break;
                    }
                    match = i;
                }
            }
            if (match >= 0) {
                selPack = match;
            }
        }
    }

    private static boolean packMatchesTarget(String pack, String target) {
        String p = norm(pack);
        String t = norm(target);
        return t.isEmpty() || p.contains(t);
    }

    private static String norm(String s) {
        String lower = s.toLowerCase();
        if (lower.endsWith(".zip")) {
            lower = lower.substring(0, lower.length() - 4);
        }
        return lower.replaceAll("[^a-z0-9]", "");
    }

    private void onAction(boolean validate) {
        if (selPack < 0 && selPatch < 0) {
            setGuard("cal.ui.patcher_status_selectboth");
            return;
        }
        if (selPack < 0) {
            setGuard("cal.ui.patcher_status_selectpack");
            return;
        }
        if (selPatch < 0) {
            setGuard("cal.ui.patcher_status_selectpatch");
            return;
        }

        IrlPatch parsed;
        try {
            parsed = IrlPatchParser.parse(Files.readString(patches.get(selPatch), StandardCharsets.UTF_8));
        } catch (Exception e) {
            LOG.warn("failed to parse patch {}", patches.get(selPatch), e);
            setStatus(ST_ERR, "cal.ui.patcher_result_failparse", null);
            return;
        }

        String packName = packs.get(selPack);
        if (validate) {
            PatchResult result = IrlPatchApplier.validate(Shaderpacks.packPath(packName), parsed);
            logResult("validate", result);
            applyResult(true, result, null);
        } else {
            String outName = outputName(packName);
            Path source = Shaderpacks.packPath(packName);
            Path output = Shaderpacks.dir().resolve(outName);
            PatchResult result = IrlPatchApplier.apply(source, output, parsed);
            logResult("patch", result);
            applyResult(false, result, outName);
            reload();
        }
    }

    private String outputName(String packName) {
        String base = packName;
        if (base.toLowerCase().endsWith(".zip")) {
            base = base.substring(0, base.length() - 4);
        }
        base = base + "_IRLights";

        if (!newPackEachTime) {
            return base;
        }
        if (!Files.exists(Shaderpacks.dir().resolve(base))) {
            return base;
        }
        for (int i = 2; i < 1000; i++) {
            String candidate = base + "_" + i;
            if (!Files.exists(Shaderpacks.dir().resolve(candidate))) {
                return candidate;
            }
        }
        return base;
    }

    private static void logResult(String tag, PatchResult result) {
        for (String line : result.log) {
            LOG.info("[{}] {}", tag, line);
        }
    }

    private void setGuard(String key) {
        setStatus(ST_GUARD, key, null);
    }

    private void setStatus(int kind, String key, String arg) {
        statusKind = kind;
        statusKey = key;
        statusArg = arg;
    }

    private void applyResult(boolean validate, PatchResult result, String outputName) {
        if (result.ok) {
            if (validate) {
                setStatus(ST_OK, "cal.ui.patcher_result_validateok", null);
            } else {
                setStatus(ST_OK, "cal.ui.patcher_result_patchok", outputName);
            }
            return;
        }

        String s = result.summary == null ? "" : result.summary.toLowerCase(Locale.ROOT);
        String key;
        if (s.contains("already patched") || s.contains("already exists")) {
            key = "cal.ui.patcher_result_failalreadypatched";
        } else if (s.contains("contract")) {
            key = "cal.ui.patcher_result_failversion";
        } else if (s.contains("not a folder or .zip") || s.contains("no shaders/")) {
            key = "cal.ui.patcher_result_failbadpack";
        } else if (s.contains("io error")) {
            key = "cal.ui.patcher_result_failio";
        } else {
            key = "cal.ui.patcher_result_failnofit";
        }
        setStatus(ST_ERR, key, null);
    }

    @Override
    public void render(CLUIContext ctx) {
        ctx.batcher.box(x, y, x + w, y + h, 0xFF141418);
        ctx.batcher.outline(x, y, x + w, y + h, 0xFFFFAA00, 1);

        int headerH = 22;
        ctx.batcher.box(x, y, x + w, y + headerH, 0xFF1A1A22);
        ctx.batcher.outline(x, y, x + w, y + headerH, 0xFF2A2A35, 1);
        ctx.batcher.text(L10n.get("cal.ui.patcher_title"), x + 10, y + 6, 0xFFFFAA00);

        boolean hoverX = ctx.mouseX >= x + w - 22 && ctx.mouseX < x + w - 6 && ctx.mouseY >= y + 4 && ctx.mouseY < y + 18;
        ctx.batcher.text("X", x + w - 18, y + 6, hoverX ? 0xFFEF5350 : 0xFF9E9E9E);

        int colW = 200;
        int colH = 140;
        int leftColX = x + 15;
        int rightColX = x + w - colW - 15;
        int colY = y + headerH + 20;

        renderColumnHeader(ctx, leftColX, colY - 16, colW, L10n.get("cal.ui.patcher_shaderpacks"), true);
        renderListBox(ctx, "packs", leftColX, colY, colW, colH, packs, selPack, L10n.get("cal.ui.patcher_emptypacks"), packsScrollY);

        renderColumnHeader(ctx, rightColX, colY - 16, colW, L10n.get("cal.ui.patcher_patches"), false);
        renderListBox(ctx, "patches", rightColX, colY, colW, colH, patchLabels, selPatch, L10n.get("cal.ui.patcher_emptypatches"), patchesScrollY);

        int metaY = colY + colH + 10;
        renderMetaLine(ctx, x + 15, metaY, w - 30);

        int optY = metaY + 28;
        renderCheckbox(ctx, x + 15, optY, L10n.get("cal.ui.patcher_newpack"), newPackEachTime);

        int btnW = (w - 38) / 2;
        int btnY = optY + 22;
        int btnH = 20;

        boolean hoverVal = ctx.mouseX >= x + 15 && ctx.mouseX < x + 15 + btnW && ctx.mouseY >= btnY && ctx.mouseY < btnY + btnH;
        int valBg = hoverVal ? 0xFF3A3A4A : 0xFF212126;
        ctx.batcher.box(x + 15, btnY, x + 15 + btnW, btnY + btnH, valBg);
        ctx.batcher.outline(x + 15, btnY, x + 15 + btnW, btnY + btnH, hoverVal ? 0xFFFFAA00 : 0xFF3E3E4D, 1);
        int valTextW = MinecraftClient.getInstance().textRenderer.getWidth(L10n.get("cal.ui.patcher_validate"));
        ctx.batcher.text(L10n.get("cal.ui.patcher_validate"), x + 15 + (btnW - valTextW) / 2, btnY + 6, 0xFFE0E0E0);

        boolean hoverPatch = ctx.mouseX >= x + w - 15 - btnW && ctx.mouseX < x + w - 15 && ctx.mouseY >= btnY && ctx.mouseY < btnY + btnH;
        int patchBg = hoverPatch ? 0xFF2E7D32 : 0xFF1B5E20;
        ctx.batcher.box(x + w - 15 - btnW, btnY, x + w - 15, btnY + btnH, patchBg);
        ctx.batcher.outline(x + w - 15 - btnW, btnY, x + w - 15, btnY + btnH, hoverPatch ? 0xFF81C784 : 0xFF555555, 1);
        int patchTextW = MinecraftClient.getInstance().textRenderer.getWidth(L10n.get("cal.ui.patcher_patch"));
        ctx.batcher.text(L10n.get("cal.ui.patcher_patch"), x + w - 15 - btnW + (btnW - patchTextW) / 2, btnY + 6, 0xFFFFFFFF);

        int statusY = btnY + btnH + 10;
        renderStatusLine(ctx, x + 15, statusY, w - 30);
    }

    private void renderColumnHeader(CLUIContext ctx, int cx, int cy, int cw, String title, boolean withRefresh) {
        ctx.batcher.text(title, cx, cy + 4, 0xFFFFAA00);
        int iconSize = 16;
        int rightX = cx + cw - iconSize;

        if (withRefresh) {
            boolean hoverFolder = ctx.mouseX >= rightX && ctx.mouseX < rightX + iconSize && ctx.mouseY >= cy && ctx.mouseY < cy + 16;
            ctx.batcher.icon(CalLightsIcons.FOLDER, rightX, cy + 1, hoverFolder ? 0xFFFFFFFF : 0xFFB4B4B4);

            int refX = rightX - 22;
            boolean hoverRef = ctx.mouseX >= refX && ctx.mouseX < refX + iconSize && ctx.mouseY >= cy && ctx.mouseY < cy + 16;
            ctx.batcher.icon(CalLightsIcons.REFRESH, refX, cy + 1, hoverRef ? 0xFFFFFFFF : 0xFFB4B4B4);
        } else {
            boolean hoverFolder = ctx.mouseX >= rightX && ctx.mouseX < rightX + iconSize && ctx.mouseY >= cy && ctx.mouseY < cy + 16;
            ctx.batcher.icon(CalLightsIcons.FOLDER, rightX, cy + 1, hoverFolder ? 0xFFFFFFFF : 0xFFB4B4B4);
        }
    }

    private void renderListBox(CLUIContext ctx, String id, int lx, int ly, int lw, int lh, List<String> items, int selected, String emptyLabel, int scrollVal) {
        ctx.batcher.box(lx, ly, lx + lw, ly + lh, 0xFF111115);
        ctx.batcher.outline(lx, ly, lx + lw, ly + lh, 0xFF22222A, 1);

        ctx.batcher.clip(lx + 2, ly + 2, lw - 4, lh - 4);
        int itemH = 16;
        int visibleH = lh - 4;
        int totalH = items.size() * itemH;
        int maxScroll = Math.min(0, visibleH - totalH);

        int currentScroll = scrollVal;
        if (currentScroll < maxScroll) currentScroll = maxScroll;

        for (int i = 0; i < items.size(); i++) {
            int itemY = ly + 2 + i * itemH + currentScroll;
            if (itemY + itemH < ly || itemY > ly + lh) continue;

            boolean isSel = (i == selected);
            boolean hoverItem = ctx.mouseX >= lx + 2 && ctx.mouseX < lx + lw - 2 && ctx.mouseY >= itemY && ctx.mouseY < itemY + itemH && ctx.mouseY >= ly + 2 && ctx.mouseY < ly + lh - 2;

            int bg = isSel ? 0xFF2B2B36 : (hoverItem ? 0xFF1A1A22 : 0xFF111115);
            ctx.batcher.box(lx + 2, itemY, lx + lw - 2, itemY + itemH, bg);
            if (isSel) {
                ctx.batcher.outline(lx + 2, itemY, lx + lw - 2, itemY + itemH, 0xFFFFAA00, 1);
            }
            ctx.batcher.text(items.get(i), lx + 6, itemY + 4, 0xFFE0E0E0);
        }

        if (items.isEmpty()) {
            ctx.batcher.text(emptyLabel, lx + 6, ly + lh / 2 - 4, 0xFF777788);
        }

        ctx.batcher.unclip();

        if (totalH > visibleH) {
            int scrollbarX = lx + lw - 5;
            int scrollbarY = ly + 2;
            int scrollbarH = visibleH;
            float ratio = (float) visibleH / totalH;
            int thumbH = Math.max(12, (int) (scrollbarH * ratio));
            float scrollPercent = (float) -currentScroll / (totalH - visibleH);
            int thumbY = scrollbarY + (int) (scrollPercent * (scrollbarH - thumbH));

            ctx.batcher.box(scrollbarX, scrollbarY, scrollbarX + 3, scrollbarY + scrollbarH, 0x1AFFFFFF);
            boolean hoverThumb = ctx.mouseX >= scrollbarX - 1 && ctx.mouseX < scrollbarX + 4 && ctx.mouseY >= thumbY && ctx.mouseY < thumbY + thumbH;
            ctx.batcher.box(scrollbarX, thumbY, scrollbarX + 3, thumbY + thumbH, hoverThumb ? 0xFFFFAA00 : 0x66FFFFFF);
        }
    }

    private void renderMetaLine(CLUIContext ctx, int sx, int sy, int sw) {
        if (selPatch != parsedPatch) {
            parseSelectedPatch();
        }

        if (selPatch < 0) {
            return;
        }

        if (patchBroken) {
            ctx.batcher.text(L10n.get("cal.ui.patcher_meta_broken"), sx, sy, 0xFFFF5555);
            return;
        }

        String pack = (selPack >= 0 && selPack < packs.size()) ? packs.get(selPack) : null;
        boolean hasTarget = !patchTarget.isEmpty();

        if (pack == null) {
            String msg = hasTarget
                ? String.format(L10n.get("cal.ui.patcher_meta_pickpack"), patchTarget)
                : L10n.get("cal.ui.patcher_meta_pickpack_notarget");
            ctx.batcher.text(msg, sx, sy, 0xFF888899);
        } else if (hasTarget && !packMatchesTarget(pack, patchTarget)) {
            String msg = String.format(L10n.get("cal.ui.patcher_meta_mismatch"), patchTarget);
            ctx.batcher.text(msg, sx, sy, 0xFFFFAA00);
        } else {
            String msg = String.format(L10n.get("cal.ui.patcher_meta_match"), hasTarget ? patchTarget : pack);
            ctx.batcher.text(msg, sx, sy, 0xFF55FF55);
        }
    }

    private void renderCheckbox(CLUIContext ctx, int cx, int cy, String label, boolean checked) {
        int boxSize = 10;
        boolean hover = ctx.mouseX >= cx && ctx.mouseX < cx + 150 && ctx.mouseY >= cy && ctx.mouseY < cy + 14;

        ctx.batcher.box(cx, cy + 2, cx + boxSize, cy + 2 + boxSize, hover ? 0xFF2A2A35 : 0xFF141418);
        ctx.batcher.outline(cx, cy + 2, cx + boxSize, cy + 2 + boxSize, hover ? 0xFFFFAA00 : 0xFF3E3E4D, 1);

        if (checked) {
            ctx.batcher.box(cx + 2, cy + 4, cx + boxSize - 2, cy + boxSize, 0xFFFFAA00);
        }

        ctx.batcher.text(label, cx + boxSize + 6, cy + 3, 0xFFE0E0E0);
    }

    private void renderStatusLine(CLUIContext ctx, int sx, int sy, int sw) {
        String raw = L10n.get(statusKey);
        String msg = (statusArg != null) ? String.format(raw, statusArg) : raw;

        int color;
        if (statusKind == ST_OK) {
            color = 0xFF55FF55;
        } else if (statusKind == ST_ERR) {
            color = 0xFFFF5555;
        } else {
            color = 0xFF888899;
        }
        ctx.batcher.text(msg, sx, sy, color);
    }

    @Override
    public boolean mouseClicked(int mx, int my, int btn) {
        if (mx < x || mx >= x + w || my < y || my >= y + h) {
            return false;
        }

        if (mx >= x + w - 22 && mx < x + w - 6 && my >= y + 4 && my < y + 18) {
            if (onClose != null) onClose.run();
            return true;
        }

        int colW = 200;
        int colH = 140;
        int leftColX = x + 15;
        int rightColX = x + w - colW - 15;
        int colY = y + 22 + 20;

        int leftHeaderY = colY - 16;
        int leftFolderX = leftColX + colW - 16;
        int leftRefreshX = leftFolderX - 22;

        if (mx >= leftRefreshX && mx < leftRefreshX + 16 && my >= leftHeaderY && my < leftHeaderY + 16) {
            reload();
            return true;
        }
        if (mx >= leftFolderX && mx < leftFolderX + 16 && my >= leftHeaderY && my < leftHeaderY + 16) {
            Shaderpacks.openFolder();
            return true;
        }

        int rightHeaderY = colY - 16;
        int rightFolderX = rightColX + colW - 16;
        if (mx >= rightFolderX && mx < rightFolderX + 16 && my >= rightHeaderY && my < rightHeaderY + 16) {
            PatchLibrary.openFolder();
            return true;
        }

        if (mx >= leftColX && mx < leftColX + colW && my >= colY && my < colY + colH) {
            int clickedIdx = (my - colY - 2 - packsScrollY) / 16;
            if (clickedIdx >= 0 && clickedIdx < packs.size()) {
                selPack = clickedIdx;
                return true;
            }
        }
        if (mx >= rightColX && mx < rightColX + colW && my >= colY && my < colY + colH) {
            int clickedIdx = (my - colY - 2 - patchesScrollY) / 16;
            if (clickedIdx >= 0 && clickedIdx < patches.size()) {
                selPatch = clickedIdx;
                return true;
            }
        }

        int metaY = colY + colH + 10;

        int optY = metaY + 28;
        if (mx >= x + 15 && mx < x + 150 && my >= optY && my < optY + 14) {
            newPackEachTime = !newPackEachTime;
            return true;
        }

        int btnW = (w - 38) / 2;
        int btnY = optY + 22;
        int btnH = 20;

        if (mx >= x + 15 && mx < x + 15 + btnW && my >= btnY && my < btnY + btnH) {
            onAction(true);
            return true;
        }
        if (mx >= x + w - 15 - btnW && mx < x + w - 15 && my >= btnY && my < btnY + btnH) {
            onAction(false);
            return true;
        }

        return true;
    }

    @Override
    public boolean scroll(int mx, int my, double amount) {
        int colW = 200;
        int colH = 140;
        int leftColX = x + 15;
        int rightColX = x + w - colW - 15;
        int colY = y + 22 + 20;

        if (mx >= leftColX && mx < leftColX + colW && my >= colY && my < colY + colH) {
            int totalH = packs.size() * 16;
            int visibleH = colH - 4;
            int maxScroll = Math.min(0, visibleH - totalH);
            packsScrollY = Math.max(maxScroll, Math.min(0, packsScrollY + (int) (amount * 16)));
            return true;
        }
        if (mx >= rightColX && mx < rightColX + colW && my >= colY && my < colY + colH) {
            int totalH = patches.size() * 16;
            int visibleH = colH - 4;
            int maxScroll = Math.min(0, visibleH - totalH);
            patchesScrollY = Math.max(maxScroll, Math.min(0, patchesScrollY + (int) (amount * 16)));
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(int mx, int my, int btn) {
        return false;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        return false;
    }
}
