package com.alonie.brbe.util;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.recipeviewer.RecipeViewerCategories;
import com.alonie.brbe.recipeviewer.RecipeViewerCategory;
import com.alonie.brbe.recipeviewer.engine.RecipeViewerEngine;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.inventory.AbstractContainerMenu;

import java.util.ArrayList;
import java.util.List;

/**
 * 1.21.1 轻量版查询浮层（自研 R/U viewer）。
 *
 * <p>**视觉重做（2026-08-28，对照 1.21.11 底层差异）**：旧版面板直接复用原版
 * recipe_book 背景且居中对齐——与打开的配方书/背包界面重叠后视觉完全混淆
 * （用户截图：两套配方书纹理 + 红框格子 + 标签溢出面板）。新版按 1.21.11 的
 * 框体语言对齐：</p>
 * <ul>
 *   <li>面板 = 原版 {@code recipe_book/overlay_recipe} 9-slice 框体（1.21.11 的
 *       查询框同款背景），不再复用书页纹理</li>
 *   <li>面板**锚定光标**（左上方，box 在光标上方展开，与 1.21.11 的锚定语义一致），
 *       屏幕边界钳制——不再压在背包正中</li>
 *   <li>面板内有：标题行（查询对象图标 + 查询配方/用途 + 页码）、8×4 物品网格、
 *       底部分类 tab 行（全部位于面板内）</li>
 * </ul>
 *
 * <p>数据/输入语义不变：R=查询配方、U=查询用途、A=固定悬停配方、ESC=关闭、
 * Shift 悬停=PopupRenderer 放大弹窗、A 固定后=pinoverlay 轻量浮层。</p>
 */
public final class RecipeViewerOverlay {

    // -- State -----------------------------------------------------------------

    private static boolean active;
    /** 打开 viewer 的宿主屏幕：只在该屏幕打开时绘制（配置界面等其他屏幕
     *  打开时自动关闭，不会把查询浮层泄漏到非容器屏上）。 */
    private static AbstractContainerScreen<?> hostScreen;
    private static boolean viewUsage;
    private static ItemStack target = ItemStack.EMPTY;
    private static RecipeViewerCategory category;
    private static List<DisplayEntry> entries = new ArrayList<>();
    private static int page;
    private static int pageCount = 1;
    /** pinoverlay 弹窗：A 键固定后展示的配方（旁边大弹窗）；关闭 viewer 清空。 */
    private static DisplayEntry pinPopupEntry;
    private static int pinPopupX;
    private static int pinPopupY;
    private static boolean pinPopupActive;

    /** Unified grid entry: a vanilla RecipeHolder or a JEI-backed entry. */
    private record DisplayEntry(RecipeHolder<?> holder, RecipeViewerEngine.JeiEntry jei) {
        static DisplayEntry of(RecipeHolder<?> h) {
            return new DisplayEntry(h, null);
        }
        static DisplayEntry of(RecipeViewerEngine.JeiEntry j) {
            return new DisplayEntry(null, j);
        }
        ItemStack result() {
            if (holder != null) return recipeResult(holder);
            if (jei != null && jei.outputs() != null && !jei.outputs().isEmpty()) {
                return jei.outputs().get(0);
            }
            return ItemStack.EMPTY;
        }
        List<ItemStack> inputs() {
            if (holder != null) return recipeInputs(holder);
            return jei == null || jei.inputs() == null ? List.of() : jei.inputs();
        }
        boolean isPinned() {
            if (holder != null) return BetterRecipeBook.pinnedRecipeManager.isPinnedEntry(holder);
            return BetterRecipeBook.pinnedRecipeManager.isPinnedUid(jei.typeUid());
        }
        void togglePin() {
            if (holder != null) {
                BetterRecipeBook.pinnedRecipeManager.toggleFavourite(holder);
            } else {
                BetterRecipeBook.pinnedRecipeManager.toggleFavouriteUid(jei.typeUid());
            }
        }
    }

    // -- Geometry (anchored box, 1.21.11-style) --------------------------------

    /** 面板框体（原版替代配方组背景 sprite，9-slice 可拉伸——1.21.11 查询框同款）。 */
    private static final ResourceLocation PANEL_BACKGROUND =
            ResourceLocation.withDefaultNamespace("recipe_book/overlay_recipe");

    private static final int PANEL_W = 240;
    private static final int PANEL_H = 162;
    private static final int PAD = 6;
    private static final int TITLE_H = 20;
    private static final int CELL = 25;
    private static final int COLS = 8;
    private static final int ROWS = 4;
    private static final int GRID_X = (PANEL_W - COLS * CELL) / 2;   // 20
    private static final int GRID_Y = PAD + TITLE_H + 2;             // 28
    private static final int TAB_H = 22;
    private static final int TAB_W = 26;
    private static final int TAB_Y = PANEL_H - TAB_H - PAD;          // 134
    private static final int TAB_X_START = (PANEL_W - PANEL_W) / 2 + 3;

    /** 锚定偏移（与 1.21.11 一致）：面板右端 16px、底端 16px 围绕锚点（光标处）。 */
    private static final int ANCHOR_DX = 16;
    private static final int ANCHOR_DY = 16;

    private RecipeViewerOverlay() {}

    // -- Public API -------------------------------------------------------------

    /** Whether the BRBE query viewer is currently open. */
    public static boolean isActive() {
        return active;
    }

    /** Current queried item (for pin / tooltip logic). */
    public static ItemStack target() {
        return target;
    }

    /** Current category id, or null. */
    public static RecipeViewerCategory currentCategory() {
        return category;
    }

    // -- Open / close -----------------------------------------------------------

    /** Open the viewer for {@code stack} (usage=true = U key).  Returns true
     *  when the viewer opened (any category had content). */
    public static boolean open(ItemStack stack, boolean usage, AbstractContainerScreen<?> screen) {
        if (stack == null || stack.isEmpty()) return false;
        RecipeViewerCategory cat = RecipeViewerCategories.defaultFor(
                stack, usage, screen == null ? null : screen.getMenu());
        if (cat == null) {
            BetterRecipeBook.LOGGER.info("[BRBE-VIEWER-DIAG] open: defaultFor=null item={} usage={}",
                    stack.getHoverName().getString(), usage);
            return false;
        }
        if (cat.query(stack, usage).isEmpty() && cat.queryJei(stack, usage).isEmpty()) {
            BetterRecipeBook.LOGGER.info("[BRBE-VIEWER-DIAG] open: empty content cat={} item={} usage={}",
                    cat.id(), stack.getHoverName().getString(), usage);
            return false;
        }

        active = true;
        hostScreen = screen;
        viewUsage = usage;
        target = stack;
        category = cat;
        applyCategory();
        return true;
    }

    /** Close the viewer. */
    public static void close() {
        active = false;
        hostScreen = null;
        target = ItemStack.EMPTY;
        category = null;
        entries = new ArrayList<>();
        page = 0;
        pageCount = 1;
        pinPopupActive = false;
        pinPopupEntry = null;
    }

    // -- Key input ---------------------------------------------------------------

    /** Handle R/U keys on container screens; returns true when consumed. */
    public static boolean keyPressed(int keyCode, int scanCode, int modifiers,
                                     AbstractContainerScreen<?> screen,
                                     net.minecraft.world.inventory.Slot hoveredSlot) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != screen) return false;

        if (active) {
            // ESC-equivalent handled by screen; R/U while open toggles usage mode
            if (BetterRecipeBook.RECIPE_VIEW_MAPPING.matches(keyCode, scanCode)) {
                reopen(screen, false);
                return true;
            }
            if (BetterRecipeBook.USAGE_VIEW_MAPPING.matches(keyCode, scanCode)) {
                reopen(screen, true);
                return true;
            }
            // A 键：固定/取消固定悬停配方（单配方 pin，与配方书 pin 语义一致）
            if (BetterRecipeBook.PIN_MAPPING.matches(keyCode, scanCode)) {
                DisplayEntry hovered = hoveredEntry(mouseXFor(screen), mouseYFor(screen));
                if (hovered != null) {
                    boolean wasPinned = hovered.isPinned();
                    hovered.togglePin();
                    boolean nowPinned = !wasPinned;
                    int mx = mouseXFor(screen);
                    int my = mouseYFor(screen);
                    if (nowPinned) {
                        // 固定成功：在悬停按钮旁展示 pinoverlay 弹窗（PopupRenderer 复用）
                        pinPopupEntry = hovered;
                        int bx = buttonRectXFor(mx, my);
                        int by = buttonRectYFor(mx, my);
                        pinPopupX = bx + 28;
                        pinPopupY = by - 8;
                        pinPopupActive = true;
                    } else {
                        pinPopupActive = false;
                        pinPopupEntry = null;
                    }
                    return true;
                }
            }
            return false;
        }

        // Open from hovered slot (caller passes hovered slot; null-safe)
        if (screen != null && hoveredSlot != null && hoveredSlot.hasItem()) {
            ItemStack hovered = hoveredSlot.getItem();
            if (BetterRecipeBook.RECIPE_VIEW_MAPPING.matches(keyCode, scanCode)) {
                boolean opened = open(hovered, false, screen);
                BetterRecipeBook.LOGGER.info("[BRBE-VIEWER-DIAG] R key={} item={} opened={} active={}",
                        keyCode, hovered.getHoverName().getString(), opened, active);
                return opened;
            }
            if (BetterRecipeBook.USAGE_VIEW_MAPPING.matches(keyCode, scanCode)) {
                boolean opened = open(hovered, true, screen);
                BetterRecipeBook.LOGGER.info("[BRBE-VIEWER-DIAG] U key={} item={} opened={} active={}",
                        keyCode, hovered.getHoverName().getString(), opened, active);
                return opened;
            }
        } else if (BetterRecipeBook.RECIPE_VIEW_MAPPING.matches(keyCode, scanCode)
                || BetterRecipeBook.USAGE_VIEW_MAPPING.matches(keyCode, scanCode)) {
            BetterRecipeBook.LOGGER.info("[BRBE-VIEWER-DIAG] R/U key {} no slot (slot={} screen={})",
                    keyCode, hoveredSlot == null ? "null" : "empty", screen == null ? "null" : screen.getClass().getSimpleName());
        }
        return false;
    }

    // -- Mouse ------------------------------------------------------------------

    /** Handle clicks on viewer buttons; returns true when consumed. */
    public static boolean mouseClicked(double mouseX, double mouseY, int button,
                                       AbstractContainerScreen<?> screen) {
        if (!active) return false;

        // Page arrows (top-right header)
        if (button == 0 && mouseY >= panelTop() + PAD - 2 && mouseY < panelTop() + PAD + TITLE_H + 2) {
            int p = pageAtHeader(mouseX, mouseY);
            if (p == -1 && page > 0) { page--; return true; }
            if (p == 1 && page < pageCount - 1) { page++; return true; }
        }

        // Category tabs (bottom bar, inside the panel)
        if (button == 0 && mouseY >= panelTop() + TAB_Y - 2 && mouseY < panelTop() + TAB_Y + TAB_H + 2) {
            List<RecipeViewerCategory> cats = RecipeViewerCategories.all();
            int idx = tabAt(mouseX, mouseY);
            if (idx >= 0 && idx < cats.size() && cats.get(idx) != category) {
                category = cats.get(idx);
                applyCategory();
                return true;
            }
        }

        // Recipe buttons: no placement in prototype (placement later)
        return false;
    }

    // -- Render -------------------------------------------------------------------

    /** Render the viewer overlay (called from platform after-render hooks —
     *  整屏渲染完成后、所有内容之上). */
    public static void render(GuiGraphics gui, int mouseX, int mouseY, float delta) {
        if (!active) return;
        // 宿主屏幕被替换（如打开配置界面/离开世界）：viewer 不再绘制并自动关闭。
        if (Minecraft.getInstance().screen != hostScreen) {
            close();
            return;
        }

        int left = panelLeft();
        int top = panelTop();

        // 面板框体（overlay_recipe 9-slice，1.21.11 查询框同款背景）
        gui.blitSprite(PANEL_BACKGROUND, left, top, PANEL_W, PANEL_H);

        // 标题行：查询对象图标 + 文案 + 页码/翻页箭头
        gui.renderFakeItem(target, left + PAD, top + PAD + 2);
        gui.drawString(Minecraft.getInstance().font,
                Component.translatable(viewUsage ? "brbe.viewer.usage" : "brbe.viewer.recipe")
                        .append(": ")
                        .append(target.getHoverName()),
                left + PAD + 20, top + PAD + 5, 0x404040);
        if (pageCount > 1) {
            String pager = (page + 1) + "/" + pageCount;
            gui.drawString(Minecraft.getInstance().font, pager,
                    left + PANEL_W - PAD - Minecraft.getInstance().font.width(pager) - 14,
                    top + PAD + 5, 0x404040);
            gui.drawString(Minecraft.getInstance().font, "<",
                    left + PANEL_W - PAD - 12, top + PAD + 5, 0x404040);
            gui.drawString(Minecraft.getInstance().font, ">",
                    left + PANEL_W - PAD - 2, top + PAD + 5, 0x404040);
        }

        // Grid category (fuel/compost/info): standalone item grid, no recipe buttons
        if (category.isGridCategory()) {
            drawItemGrid(gui, left, top, mouseX, mouseY);
            return;
        }

        // Recipe entries grid (paged)
        int perPage = COLS * ROWS;
        int start = page * perPage;
        int end = Math.min(start + perPage, entries.size());
        for (int i = start; i < end; i++) {
            int col = (i - start) % COLS;
            int row = (i - start) / COLS;
            int bx = left + GRID_X + col * CELL;
            int by = top + GRID_Y + row * CELL;
            DisplayEntry entry = entries.get(i);
            ItemStack result = entry.result();
            boolean hovered = mouseX >= bx && mouseX < bx + CELL
                    && mouseY >= by && mouseY < by + CELL;
            gui.blitSprite(BRBTextures.RECIPE_BOOK_BUTTON_SLOT_UNCRAFTABLE_SPRITE,
                    bx, by, CELL, CELL);
            gui.renderFakeItem(result, bx + 5, by + 5);
            // 已固定配方：左上角 pin 图标（与配方书 pin 一致）
            if (entry.isPinned()) {
                gui.blitSprite(BRBTextures.RECIPE_BOOK_PIN_SPRITE, bx - 4, by - 4, 32, 32);
            }
            if (hovered) {
                gui.fill(bx, by, bx + CELL, by + CELL, 0x40FFFFFF);
                // Shift 悬停：渲染放大弹窗（PopupRenderer 1.21.1 简化版 / JEI 委托版）
                if (com.alonie.brbe.util.ClientCompat.isShiftDown()) {
                    int[] rect;
                    if (entry.jei() != null) {
                        rect = com.alonie.brbe.render.PopupRenderer.renderJeiPopup(
                                gui, entry.jei(), bx, by, CELL, CELL, 2.0F);
                    } else {
                        rect = com.alonie.brbe.render.PopupRenderer.renderRecipePopup(
                                gui, entry.holder(),
                                com.alonie.brbe.render.PopupRenderer.modeFor(
                                        category != null ? category.id() : null),
                                false, false,
                                bx, by, CELL, CELL, true, 2.0F);
                    }
                    if (rect == null) {
                        // JEI runtime/category 缺失：退回按钮高亮
                        gui.fill(bx, by, bx + CELL, by + CELL, 0x40FFFFFF);
                    }
                }
            }
        }

        // pinoverlay 弹窗（固定配方展示）
        if (pinPopupActive && pinPopupEntry != null) {
            if (pinPopupEntry.jei() != null) {
                com.alonie.brbe.render.PopupRenderer.renderJeiPopup(
                        gui, pinPopupEntry.jei(),
                        pinPopupX - 12, pinPopupY - 12, CELL, CELL, 2.0F);
            } else {
                com.alonie.brbe.render.PopupRenderer.renderRecipePopup(
                        gui, pinPopupEntry.holder(),
                        com.alonie.brbe.render.PopupRenderer.modeFor(
                                category != null ? category.id() : null),
                        false, false,
                        pinPopupX - 12, pinPopupY - 12, CELL, CELL, false, 2.0F);
            }
        }

        // 底部分类 tab 行（面板内）
        drawTabs(gui, left, top);
    }

    /** Standalone item grid (fuel / compost / info categories). */
    private static void drawItemGrid(GuiGraphics gui, int left, int top, int mouseX, int mouseY) {
        List<ItemStack> grid = category.gridItems(target, viewUsage);
        List<ItemStack> all = grid.isEmpty() ? category.allGridItems() : grid;
        int gridStart = page * (COLS * ROWS);
        int gridEnd = Math.min(gridStart + COLS * ROWS, all.size());
        for (int gi = gridStart; gi < gridEnd; gi++) {
            int col = (gi - gridStart) % COLS;
            int row = (gi - gridStart) / COLS;
            int bx = left + GRID_X + col * CELL;
            int by = top + GRID_Y + row * CELL;
            boolean hovered = mouseX >= bx && mouseX < bx + CELL
                    && mouseY >= by && mouseY < by + CELL;
            gui.blitSprite(BRBTextures.RECIPE_BOOK_BUTTON_SLOT_UNCRAFTABLE_SPRITE,
                    bx, by, CELL, CELL);
            gui.renderFakeItem(all.get(gi), bx + 5, by + 5);
            if (hovered) {
                gui.fill(bx, by, bx + CELL, by + CELL, 0x40FFFFFF);
            }
        }
        drawTabs(gui, left, top);
    }

    /** Category tab row: drawn INSIDE the panel bottom (no overflow). */
    private static void drawTabs(GuiGraphics gui, int left, int top) {
        List<RecipeViewerCategory> cats = RecipeViewerCategories.all();
        int tabX = left + TAB_X_START;
        int tabY = top + TAB_Y;
        for (RecipeViewerCategory cat : cats) {
            boolean sel = cat == category;
            gui.fill(tabX + 2, tabY + 2, tabX + TAB_W - 2, tabY + TAB_H - 2,
                    sel ? 0xFFA0A0A0 : 0xFF606060);
            gui.renderFakeItem(cat.icon(), tabX + 5, tabY + 4);
            tabX += TAB_W;
        }
    }

    /** Render the entry tooltip (called from the deferred tooltip channel). */
    public static void renderTooltip(GuiGraphics gui, int mouseX, int mouseY) {
        if (!active) return;
        if (Minecraft.getInstance().screen != hostScreen) {
            close();
            return;
        }
        int left = panelLeft();
        int top = panelTop();
        int perPage = COLS * ROWS;
        int start = page * perPage;
        int end = Math.min(start + perPage, entries.size());
        for (int i = start; i < end; i++) {
            int col = (i - start) % COLS;
            int row = (i - start) / COLS;
            int bx = left + GRID_X + col * CELL;
            int by = top + GRID_Y + row * CELL;
            if (mouseX >= bx && mouseX < bx + CELL
                    && mouseY >= by && mouseY < by + CELL) {
                List<Component> tooltip = new ArrayList<>();
                tooltip.add(entries.get(i).result().getHoverName());
                List<ItemStack> inputs = entries.get(i).inputs();
                if (!inputs.isEmpty()) {
                    String suffix = inputs.size() > 1 ? " …" : "";
                    tooltip.add(Component.translatable("brbe.viewer.materials")
                            .append(": ")
                            .append(inputs.get(0).getHoverName().copy()
                                    .append(Component.literal(suffix))));
                }
                gui.renderComponentTooltip(Minecraft.getInstance().font, tooltip, mouseX, mouseY);
                return;
            }
        }
    }

    // -- Helpers ------------------------------------------------------------------

    private static void reopen(AbstractContainerScreen<?> screen, boolean usage) {
        RecipeViewerCategory cat = RecipeViewerCategories.defaultFor(
                target, usage, screen == null ? null : screen.getMenu());
        if (cat == null) {
            close();
            return;
        }
        category = cat;
        viewUsage = usage;
        applyCategory();
    }

    private static void applyCategory() {
        List<DisplayEntry> merged = new ArrayList<>();
        for (RecipeHolder<?> holder : category.query(target, viewUsage)) {
            merged.add(DisplayEntry.of(holder));
        }
        for (RecipeViewerEngine.JeiEntry jei : category.queryJei(target, viewUsage)) {
            merged.add(DisplayEntry.of(jei));
        }
        entries = merged;
        page = 0;
        pageCount = Math.max(1, (entries.size() + COLS * ROWS - 1) / (COLS * ROWS));
    }

    /** 面板锚定：光标左上展开（box 在光标左侧、底部高于光标 16px，参考
     *  1.21.11 的锚定语义），屏幕边界钳制。 */
    private static int panelLeft() {
        Minecraft mc = Minecraft.getInstance();
        int w = mc.getWindow() == null ? 200 : mc.getWindow().getGuiScaledWidth();
        if (mc.screen instanceof AbstractContainerScreen<?> cs) {
            w = cs.width;
        }
        int mx = mouseXFor(mc.screen);
        int left = mx - PANEL_W - ANCHOR_DX;
        if (left < 2) {
            // 光标太靠左：改到光标右侧展开
            left = mx + ANCHOR_DX;
        }
        return Math.min(left, w - PANEL_W - 2);
    }

    private static int panelTop() {
        Minecraft mc = Minecraft.getInstance();
        int h = mc.getWindow() == null ? 200 : mc.getWindow().getGuiScaledHeight();
        if (mc.screen instanceof AbstractContainerScreen<?> cs) {
            h = cs.height;
        }
        int my = mouseYFor(mc.screen);
        int top = my - PANEL_H + ANCHOR_DY;
        if (top < 2) {
            // 光标太靠上：改到光标下方展开
            top = my + 20;
        }
        return Math.min(top, h - PANEL_H - 2);
    }

    /** 当前鼠标位置（GUI 缩放坐标）。 */
    private static int mouseXFor(Object screen) {
        var mc = Minecraft.getInstance();
        return mc.mouseHandler != null ? (int) mc.mouseHandler.xpos() : 0;
    }

    private static int mouseYFor(Object screen) {
        var mc = Minecraft.getInstance();
        return mc.mouseHandler != null ? (int) mc.mouseHandler.ypos() : 0;
    }

    private static ItemStack recipeResult(RecipeHolder<?> holder) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) return ItemStack.EMPTY;
            return holder.value().getResultItem(mc.level.registryAccess());
        } catch (Exception e) {
            return ItemStack.EMPTY;
        }
    }

    private static List<ItemStack> recipeInputs(RecipeHolder<?> holder) {
        List<ItemStack> inputs = new ArrayList<>();
        Recipe<?> recipe = holder.value();
        for (Ingredient ingredient : recipe.getIngredients()) {
            ItemStack[] stacks = ingredient.getItems();
            if (stacks.length > 0) inputs.add(stacks[0]);
        }
        return inputs;
    }

    /** 首页/尾页翻页箭头命中（标题行右侧 "<" ">"）。 */
    private static int pageAtHeader(double mouseX, double mouseY) {
        int left = panelLeft();
        int top = panelTop();
        int y0 = top + PAD;
        int y1 = top + PAD + TITLE_H;
        int x = (int) mouseX;
        if (mouseY < y0 || mouseY >= y1) return 0;
        if (x >= left + PANEL_W - PAD - 14 && x < left + PANEL_W - PAD - 8) return -1;
        if (x >= left + PANEL_W - PAD - 4 && x < left + PANEL_W - PAD) return 1;
        return 0;
    }

    private static int tabAt(double mouseX, double mouseY) {
        int tabX = panelLeft() + TAB_X_START;
        int tabY = panelTop() + TAB_Y;
        List<RecipeViewerCategory> cats = RecipeViewerCategories.all();
        for (int i = 0; i < cats.size(); i++) {
            if (mouseX >= tabX + 2 && mouseX < tabX + TAB_W - 2
                    && mouseY >= tabY + 2 && mouseY < tabY + TAB_H - 2) {
                return i;
            }
            tabX += TAB_W;
        }
        return -1;
    }

    /** 命中按钮的 X（网格矩形反推）。 */
    private static int buttonRectXFor(int mouseX, int mouseY) {
        int left = panelLeft();
        int top = panelTop();
        int perPage = COLS * ROWS;
        int start = page * perPage;
        int end = Math.min(start + perPage, entries.size());
        for (int i = start; i < end; i++) {
            int col = (i - start) % COLS;
            int row = (i - start) / COLS;
            int bx = left + GRID_X + col * CELL;
            int by = top + GRID_Y + row * CELL;
            if (mouseX >= bx && mouseX < bx + CELL
                    && mouseY >= by && mouseY < by + CELL) {
                return bx;
            }
        }
        return left + GRID_X;
    }

    /** 命中按钮的 Y（网格矩形反推）。 */
    private static int buttonRectYFor(int mouseX, int mouseY) {
        int left = panelLeft();
        int top = panelTop();
        int perPage = COLS * ROWS;
        int start = page * perPage;
        int end = Math.min(start + perPage, entries.size());
        for (int i = start; i < end; i++) {
            int col = (i - start) % COLS;
            int row = (i - start) / COLS;
            int bx = left + GRID_X + col * CELL;
            int by = top + GRID_Y + row * CELL;
            if (mouseX >= bx && mouseX < bx + CELL
                    && mouseY >= by && mouseY < by + CELL) {
                return by;
            }
        }
        return top + GRID_Y;
    }

    private static DisplayEntry hoveredEntry(int mouseX, int mouseY) {
        int left = panelLeft();
        int top = panelTop();
        int perPage = COLS * ROWS;
        int start = page * perPage;
        int end = Math.min(start + perPage, entries.size());
        for (int i = start; i < end; i++) {
            int col = (i - start) % COLS;
            int row = (i - start) / COLS;
            int bx = left + GRID_X + col * CELL;
            int by = top + GRID_Y + row * CELL;
            if (mouseX >= bx && mouseX < bx + CELL
                    && mouseY >= by && mouseY < by + CELL) {
                return entries.get(i);
            }
        }
        return null;
    }
}
