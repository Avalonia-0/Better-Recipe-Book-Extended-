package com.alonie.brbe.pinoverlay;

import com.alonie.brbe.config.RecipeViewerFeatureFlag;
import com.alonie.brbe.recipeviewer.RecipeViewerCategories;
import com.alonie.brbe.recipeviewer.RecipeViewerCategory;
import com.alonie.brbe.recipeviewer.engine.RecipeViewerEngine;
import com.alonie.brbe.util.ClientCompat;
import com.alonie.brbe.util.RecipeViewerOverlay;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 1.21.1 版 pin 浮层管理器（1.21.11 PinOverlayManager 移植；条目双类型
 * RecipeHolder/JEI）。
 *
 * <p>z 序交错渲染（与查询 viewer 共享单调 z）、拖动（3px 阈值）后点击放置、
 * 持久化 {@code brbe.pinoverlays.json}（1Hz 恢复重试）、ESC 只关 viewer 永不关
 * pin、点击 pin 前置。</p>
 */
public final class PinOverlayManager {

    private PinOverlayManager() {}

    /** z 序列表（渲染序）。 */
    private static final List<PinOverlay> PINS = new ArrayList<>();
    private static int zCounter = 0;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // 按压/拖动态
    private static PinOverlay pressPin;
    private static double grabDX;
    private static double grabDY;
    private static double pressX;
    private static double pressY;
    private static boolean dragMoved;

    // 持久化
    private static Path pinFile;
    private static final List<PinOverlay.PinSpec> pendingSpecs = new ArrayList<>();
    private static long lastResolveAttempt = -1;
    private static boolean initialized;

    // ── 生命周期 ──────────────────────────────────────────────────────────────

    public static void init() {
        if (initialized) return;
        initialized = true;
        // 查询屏蔽：不加载持久化 pin 浮层——PINS 保持空，渲染/命中/交互全部
        // 自然惰性（无条目可画、无条目可点），与查询 viewer 一并禁用。
        if (RecipeViewerFeatureFlag.isDisabled()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.gameDirectory == null) return;
        pinFile = mc.gameDirectory.toPath().resolve("brbe.pinoverlays.json");
        load();
    }

    private static void load() {
        try {
            if (pinFile == null || !Files.exists(pinFile)) return;
            String json = Files.readString(pinFile, StandardCharsets.UTF_8);
            PinOverlay.PinSpec[] specs = GSON.fromJson(json, PinOverlay.PinSpec[].class);
            if (specs != null) {
                for (PinOverlay.PinSpec spec : specs) {
                    pendingSpecs.add(spec);
                }
            }
        } catch (Exception e) {
            // corrupt file — ignore
        }
    }

    /** 清空全部查询对象 pin 并落盘（{@code /brbe clear leipin}）。返回清理前的数量。 */
    public static int clearAllAndSave() {
        int cleared = PINS.size();
        clearAll();
        save();
        return cleared;
    }

    private static void save() {
        List<PinOverlay.PinSpec> specs = new ArrayList<>();
        for (PinOverlay pin : PINS) {
            specs.add(pin.toSpec());
        }
        specs.addAll(pendingSpecs);
        if (pinFile == null) return;
        String json = GSON.toJson(specs);
        CompletableFuture.runAsync(() -> {
            try {
                Files.writeString(pinFile, json, StandardCharsets.UTF_8);
            } catch (Exception e) {
                // async write failure is non-fatal
            }
        });
    }

    // ── 状态查询 ──────────────────────────────────────────────────────────────

    public static boolean hasPins() {
        return !PINS.isEmpty() || !pendingSpecs.isEmpty();
    }

    public static int nextZ() {
        return ++zCounter;
    }

    public static void clearAll() {
        PINS.clear();
        pendingSpecs.clear();
        pressPin = null;
        dragMoved = false;
    }

    // ── 渲染（z 序交错 + 远处光标 + tooltip 仲裁） ────────────────────────────

    public static void render(GuiGraphics gui, int mouseX, int mouseY, float delta) {
        // 查询屏蔽：不渲染 pin 浮层（含持久化恢复的条目），viewer 也由
        // RecipeViewerOverlay.render 自身的屏蔽守卫跳过。
        if (RecipeViewerFeatureFlag.isDisabled()) return;
        init();
        resolvePending();
        refreshRecipeStates();
        boolean viewerActive = RecipeViewerOverlay.isActive();
        int viewerZ = viewerActive ? RecipeViewerOverlay.viewerZ() : -1;
        // 先渲染 z < viewerZ 的 pin
        for (PinOverlay pin : PINS) {
            if (pin.z() < viewerZ) pin.render(gui, mouseX, mouseY, delta);
        }
        // viewer 用远处光标渲染（其按钮不弹起、不显 tooltip），若其下无 pin 覆盖
        int vmx = mouseX;
        int vmy = mouseY;
        PinOverlay topUnderCursor = topInteractivePin(mouseX, mouseY);
        if (viewerActive && topUnderCursor != null) {
            vmx = -1;
            vmy = -1;
        }
        if (viewerActive) {
            RecipeViewerOverlay.render(gui, vmx, vmy, delta);
        }
        // 再渲染 z >= viewerZ 的 pin
        for (PinOverlay pin : PINS) {
            if (pin.z() >= viewerZ) pin.render(gui, mouseX, mouseY, delta);
        }
        // 光标下最顶层 pin 的 tooltip；其余 pin 全 arm
        if (topUnderCursor != null) {
            for (PinOverlay pin : PINS) {
                if (pin != topUnderCursor) pin.armTooltip();
            }
            if (topUnderCursor.tooltipArmed()) {
                if (ClientCompat.isShiftDown()) {
                    renderPinItemTooltip(gui, topUnderCursor, mouseX, mouseY);
                } else {
                    renderPinRecipeTooltip(gui, topUnderCursor);
                }
            }
        } else {
            for (PinOverlay pin : PINS) {
                pin.armTooltip();
            }
        }
    }

    private static void renderPinRecipeTooltip(GuiGraphics gui, PinOverlay pin) {
        if (pin.holder() != null) {
            RecipeViewerOverlay.renderDetailedRecipeTooltip(gui, pin.holder(), pin.mode());
        }
    }

    private static void renderPinItemTooltip(GuiGraphics gui, PinOverlay pin, int mouseX, int mouseY) {
        ItemStack item = pin.resultOf();
        if (item.isEmpty() || Minecraft.getInstance().player == null) return;
        List<net.minecraft.network.chat.Component> lines =
                new ArrayList<>(net.minecraft.client.gui.screens.Screen.getTooltipFromItem(
                        Minecraft.getInstance(), item));
        lines.add(net.minecraft.network.chat.Component.empty());
        RecipeViewerOverlay.appendModNamePublic(lines, item);
        gui.renderComponentTooltip(Minecraft.getInstance().font, lines, mouseX, mouseY);
    }

    // ── 命中/覆盖 ─────────────────────────────────────────────────────────────

    /** 光标下 z 最高的 pin；若 viewer 打开且 z 高于它、光标在 viewer 内 → null。 */
    public static PinOverlay topInteractivePin(double mx, double my) {
        PinOverlay best = null;
        for (PinOverlay pin : PINS) {
            if (pin.contains(mx, my)) {
                if (best == null || pin.z() > best.z()) best = pin;
            }
        }
        if (best != null && RecipeViewerOverlay.isActive()
                && RecipeViewerOverlay.viewerZ() > best.z()
                && RecipeViewerOverlay.contains(mx, my)) {
            return null;
        }
        return best;
    }

    public static boolean covers(double mx, double my) {
        return topInteractivePin(mx, my) != null;
    }

    public static boolean isDragging(PinOverlay pin) {
        return dragMoved && pressPin == pin;
    }

    /** JEI 排除区（真实 JEI 共存时 overlay 避开 pin 区域）。 */
    public static List<net.minecraft.client.renderer.Rect2i> exclusionAreas() {
        List<net.minecraft.client.renderer.Rect2i> out = new ArrayList<>();
        for (PinOverlay pin : PINS) {
            out.add(new net.minecraft.client.renderer.Rect2i(pin.boxX(), pin.boxY(),
                    pin.boxW(), pin.boxH()));
        }
        return out;
    }

    // ── 输入 ──────────────────────────────────────────────────────────────────

    /** 点击 pin：置前 + 开始按压；未命中 → false（穿透容器）。 */
    public static boolean handleMouseClicked(double mouseX, double mouseY, int button,
                                             AbstractContainerScreen<?> screen) {
        init();
        PinOverlay top = topInteractivePin(mouseX, mouseY);
        if (top == null) return false;
        bringToFront(top);
        pressPin = top;
        grabDX = mouseX - top.cx();
        grabDY = mouseY - top.cy();
        pressX = mouseX;
        pressY = mouseY;
        dragMoved = false;
        return true;
    }

    /** 拖动 pin。 */
    public static boolean handleMouseDragged(double mouseX, double mouseY, int button,
                                             double dragX, double dragY) {
        if (pressPin == null) return false;
        bringToFront(pressPin);
        double dx = mouseX - pressX;
        double dy = mouseY - pressY;
        if (!dragMoved && dx * dx + dy * dy > 9.0) {
            dragMoved = true;
        }
        if (dragMoved) {
            pressPin.setCenter(Math.round((float) (mouseX - grabDX)), Math.round((float) (mouseY - grabDY)));
        }
        return true;
    }

    /** 释放：未拖动 = 点击（放置配方）；拖动结束 = 保存位置。 */
    public static boolean handleMouseReleased(double mouseX, double mouseY, int button,
                                              AbstractContainerScreen<?> screen) {
        if (pressPin == null) return false;
        boolean click = !dragMoved;
        PinOverlay pin = pressPin;
        pressPin = null;
        if (click && screen != null && pin.holder() != null) {
            // 点击 pin 继承 viewer 按钮点击 → 放置配方
            RecipeViewerOverlay.placeRecipe(mouseX, mouseY, button, screen, pin.holder());
        } else {
            save();
        }
        dragMoved = false;
        return true;
    }

    /** ESC：只关 viewer，永不关 pin。 */
    public static boolean handleEscape() {
        return RecipeViewerOverlay.isActive() && RecipeViewerOverlay.closeSilently();
    }

    /** 滚轮：pin 在光标下 → 吞掉（防翻 viewer 页）。 */
    public static boolean handleMouseScrolled(double mx, double my, double vertical) {
        return topInteractivePin(mx, my) != null;
    }

    /** A 键：消费 pin 键。 */
    public static boolean handleKeyPressed(int keyCode, int scanCode, int modifiers,
                                           AbstractContainerScreen<?> screen) {
        // 查询屏蔽：A 键不再创建/移除 pin 浮层（查询预览界面禁用）。
        // 配方书 pin（mixins/pins → PinnedRecipeManager.toggleFavourite）不受影响。
        if (RecipeViewerFeatureFlag.isDisabled()) return false;
        init();
        if (!ClientCompat.matchesPinKey(keyCode, scanCode, modifiers)) return false;
        if (screen == null) return false;
        // 光标下 pin → 移除
        PinOverlay under = topInteractivePin(
                RecipeViewerOverlay.mouseXFor(), RecipeViewerOverlay.mouseYFor());
        if (under != null) {
            PINS.remove(under);
            cleanPress(under);
            save();
            return true;
        }
        // viewer 捕获 → 创建 pin；否则 false（保持 vanilla）
        ItemStack target = RecipeViewerOverlay.captureTarget(screen);
        if (target.isEmpty()) return false;
        return createPin(screen, target);
    }

    private static boolean createPin(AbstractContainerScreen<?> screen, ItemStack target) {
        int px = RecipeViewerOverlay.mouseXFor();
        int py = RecipeViewerOverlay.mouseYFor();
        RecipeViewerOverlay.CapturedEntry captured = RecipeViewerOverlay.capturedEntry();
        if (captured != null) {
            int mode = RecipeViewerOverlay.viewerMode();
            PINS.add(PinOverlay.create(captured.holder(), captured.jei(), mode,
                    nextZ(), px, py));
            save();
            return true;
        }
        // 无 viewer：按类别查询目标配方（首个命中类别）
        for (RecipeViewerCategory cat : RecipeViewerCategories.all()) {
            if (cat.isGridCategory()) continue;
            List<RecipeHolder<?>> holders = cat.query(target, false);
            if (!holders.isEmpty()) {
                int mode = RecipeViewerOverlay.modeForCategory(cat);
                PINS.add(PinOverlay.create(holders.get(0), null, mode, nextZ(), px, py));
                save();
                return true;
            }
            List<RecipeViewerEngine.JeiEntry> jei = cat.queryJei(target, false);
            if (!jei.isEmpty()) {
                int mode = RecipeViewerOverlay.modeForCategory(cat);
                PINS.add(PinOverlay.create(null, jei.get(0), mode, nextZ(), px, py));
                save();
                return true;
            }
        }
        return false;
    }

    private static void bringToFront(PinOverlay pin) {
        pin.setZ(++zCounter);
        PINS.remove(pin);
        PINS.add(pin);
    }

    private static void cleanPress(PinOverlay pin) {
        if (pressPin == pin) {
            pressPin = null;
            dragMoved = false;
        }
    }

    // ── 持久化恢复 ────────────────────────────────────────────────────────────

    private static void resolvePending() {
        if (pendingSpecs.isEmpty() || pinFile == null) return;
        long now = System.currentTimeMillis();
        if (now - lastResolveAttempt < 1000) return;
        lastResolveAttempt = now;
        Iterator<PinOverlay.PinSpec> it = pendingSpecs.iterator();
        while (it.hasNext()) {
            PinOverlay.PinSpec spec = it.next();
            PinOverlay pin = materialize(spec);
            if (pin != null) {
                PINS.add(pin);
                zCounter = Math.max(zCounter, pin.z());
                it.remove();
            }
        }
    }

    private static PinOverlay materialize(PinOverlay.PinSpec spec) {
        ItemStack target = PinOverlay.itemFromKey(spec.resultItem());
        if (target.isEmpty()) return null;
        for (RecipeViewerCategory cat : RecipeViewerCategories.all()) {
            if (cat.isGridCategory()) continue;
            for (RecipeHolder<?> holder : cat.query(target, false)) {
                if (PinOverlay.create(holder, null, 0, 0, 0, 0).fingerprint().equals(spec.inputs())) {
                    return PinOverlay.create(holder, null, spec.mode(), spec.z(), spec.x(), spec.y());
                }
            }
            for (RecipeViewerEngine.JeiEntry entry : cat.queryJei(target, false)) {
                if (PinOverlay.create(null, entry, 0, 0, 0, 0).fingerprint().equals(spec.inputs())) {
                    return PinOverlay.create(null, entry, spec.mode(), spec.z(), spec.x(), spec.y());
                }
            }
        }
        return null;
    }

    // ── 状态刷新（物品栏哈希驱动每帧检测） ────────────────────────────────────

    private static long lastSearchSpaceHash;
    private static boolean hashInit;

    private static void refreshRecipeStates() {
        if (PINS.isEmpty()) {
            hashInit = false;
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        // 简单物品栏哈希（pin 渲染时动态判定 craftable/partial，无需预刷新）
        long hash = 0;
        for (ItemStack stack : mc.player.getInventory().items) {
            hash = hash * 31 + stack.getItem().hashCode() * 1000003 + stack.getCount();
        }
        if (hashInit && hash == lastSearchSpaceHash) return;
        hashInit = true;
        lastSearchSpaceHash = hash;
    }
}
