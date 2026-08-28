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
 * 1.21.1 轻量版查询浮层（自研 R/U viewer 原型）。
 *
 * <p>对齐 1.21.11 RecipeViewerOverlay 的入口/渲染语义，但实现按 1.21.1 API
 * 精简：面板 + 配方按钮网格（result 图标）+ 分类 tab + R/U 键 + 基础 tooltip。
 * 完整弹窗/预览/硬模态/pin 浮层由后续轮次逐步扩展（display 鸿沟解除后）。
 *
 * <p>用法：{@link #keyPressed(int,int,int,AbstractContainerScreen)} 处理 R/U 键；
 * {@link #render(GuiGraphics,int,int,float)} 由 ScreenRenderMixin 回调。</p>
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

    // -- Geometry (centered panel) --------------------------------------------

    private static final int PANEL_W = 176;
    private static final int PANEL_H = 148;
    private static final int BUTTON_W = 24;
    private static final int BUTTON_H = 24;
    private static final int COLS = 6;
    private static final int ROWS = 4;
    private static final int GRID_PAD_X = 8;
    private static final int GRID_PAD_Y = 16;
    private static final int TAB_W = 26;
    private static final int TAB_H = 24;
    private static final int TAB_BAR_Y = 154;

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
        if (cat == null) return false;
        if (cat.query(stack, usage).isEmpty() && cat.queryJei(stack, usage).isEmpty()) {
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
                return open(hovered, false, screen);
            }
            if (BetterRecipeBook.USAGE_VIEW_MAPPING.matches(keyCode, scanCode)) {
                return open(hovered, true, screen);
            }
        }
        return false;
    }

    // -- Mouse ------------------------------------------------------------------

    /** Handle clicks on viewer buttons; returns true when consumed. */
    public static boolean mouseClicked(double mouseX, double mouseY, int button,
                                       AbstractContainerScreen<?> screen) {
        if (!active) return false;

        // Category tabs
        if (button == 0 && mouseY >= TAB_BAR_Y + panelTop(screen) + GRID_PAD_Y) {
            List<RecipeViewerCategory> cats = RecipeViewerCategories.all();
            int idx = tabAt(mouseX, mouseY, screen);
            if (idx >= 0 && idx < cats.size() && cats.get(idx) != category) {
                category = cats.get(idx);
                applyCategory();
                return true;
            }
        }

        // Page arrows
        if (button == 0 && mouseX < panelLeft(screen) + 2) {
            int idx = pageAt(mouseX, mouseY, screen);
            if (idx == -1 && page > 0) { page--; return true; }
            if (idx == 1 && page < pageCount - 1) { page++; return true; }
        }

        // Recipe buttons: no placement in prototype (placement later)
        return false;
    }

    // -- Render -------------------------------------------------------------------

    /** Render the viewer overlay (called from ScreenRenderMixin after screen render). */
    public static void render(GuiGraphics gui, int mouseX, int mouseY, float delta) {
        if (!active) return;
        // 宿主屏幕被替换（如打开配置界面/离开世界）：viewer 不再绘制并自动关闭。
        if (Minecraft.getInstance().screen != hostScreen) {
            close();
            return;
        }

        int left = panelLeft(Minecraft.getInstance().screen);
        int top = panelTop(Minecraft.getInstance().screen);

        // Panel background (vanilla recipe book texture)
        gui.blit(BRBTextures.RECIPE_BOOK_BACKGROUND_TEXTURE,
                left, top, 0, 0, PANEL_W, PANEL_H, 256, 256);

        // Title
        gui.drawString(Minecraft.getInstance().font,
                Component.translatable(viewUsage ? "brbe.viewer.usage" : "brbe.viewer.recipe")
                        .append(": ")
                        .append(target.getHoverName()),
                left + 6, top + 5, 0x404040);

        // Category tabs (bottom bar)
        List<RecipeViewerCategory> cats = RecipeViewerCategories.all();
        int tabX = left + 4;
        int tabY = top + TAB_BAR_Y - 18;
        for (RecipeViewerCategory cat : cats) {
            boolean sel = cat == category;
            gui.fill(tabX, tabY, tabX + TAB_W - 2, tabY + TAB_H - 2,
                    sel ? 0xFFA0A0A0 : 0xFF505050);
            gui.renderFakeItem(cat.icon(), tabX + 4, tabY + 3);
            tabX += TAB_W;
        }

        // Grid category (fuel): standalone item grid, no recipe buttons
        if (category.isGridCategory()) {
            List<ItemStack> grid = category.gridItems(target, viewUsage);
            List<ItemStack> all = grid.isEmpty() ? category.allGridItems() : grid;
            int gridStart = page * 24;
            int gridEnd = Math.min(gridStart + 24, all.size());
            for (int gi = gridStart; gi < gridEnd; gi++) {
                int col = (gi - gridStart) % 6;
                int row = (gi - gridStart) / 6;
                int bx = left + GRID_PAD_X + col * (BUTTON_W + 3);
                int by = top + GRID_PAD_Y + 8 + row * (BUTTON_H + 3);
                boolean hovered = mouseX >= bx && mouseX < bx + BUTTON_W
                        && mouseY >= by && mouseY < by + BUTTON_H;
                gui.blitSprite(BRBTextures.RECIPE_BOOK_BUTTON_SLOT_UNCRAFTABLE_SPRITE,
                        bx, by, BUTTON_W, BUTTON_H);
                gui.renderFakeItem(all.get(gi), bx + 4, by + 4);
                if (hovered) {
                    gui.fill(bx, by, bx + BUTTON_W, by + BUTTON_H, 0x40FFFFFF);
                }
            }
            return;
        }

        // Recipe buttons grid (paged)
        int perPage = COLS * ROWS;
        int start = page * perPage;
        int end = Math.min(start + perPage, entries.size());
        for (int i = start; i < end; i++) {
            int col = (i - start) % COLS;
            int row = (i - start) / COLS;
            int bx = left + GRID_PAD_X + col * (BUTTON_W + 3);
            int by = top + GRID_PAD_Y + 8 + row * (BUTTON_H + 3);
            DisplayEntry entry = entries.get(i);
            ItemStack result = entry.result();
            boolean hovered = mouseX >= bx && mouseX < bx + BUTTON_W
                    && mouseY >= by && mouseY < by + BUTTON_H;
            gui.blitSprite(BRBTextures.RECIPE_BOOK_BUTTON_SLOT_UNCRAFTABLE_SPRITE,
                    bx, by, BUTTON_W, BUTTON_H);
            gui.renderFakeItem(result, bx + 4, by + 4);
            // 已固定配方：左上角 pin 图标（与配方书 pin 一致）
            if (entry.isPinned()) {
                gui.blitSprite(BRBTextures.RECIPE_BOOK_PIN_SPRITE, bx - 4, by - 4, 32, 32);
            }
            if (hovered) {
                gui.fill(bx, by, bx + BUTTON_W, by + BUTTON_H, 0x40FFFFFF);
                // Shift 悬停：渲染放大弹窗（PopupRenderer 1.21.1 简化版 / JEI 委托版）
                if (com.alonie.brbe.util.ClientCompat.isShiftDown()) {
                    int[] rect;
                    if (entry.jei() != null) {
                        rect = com.alonie.brbe.render.PopupRenderer.renderJeiPopup(
                                gui, entry.jei(), bx, by, BUTTON_W, BUTTON_H, 2.0F);
                    } else {
                        rect = com.alonie.brbe.render.PopupRenderer.renderRecipePopup(
                                gui, entry.holder(),
                                com.alonie.brbe.render.PopupRenderer.modeFor(
                                        category != null ? category.id() : null),
                                false, false,
                                bx, by, BUTTON_W, BUTTON_H, true, 2.0F);
                    }
                    if (rect == null) {
                        // JEI runtime/category 缺失：退回按钮高亮
                        gui.fill(bx, by, bx + BUTTON_W, by + BUTTON_H, 0x40FFFFFF);
                    }
                }
            }
        }

        // pinoverlay 弹窗（固定配方展示）
        if (pinPopupActive && pinPopupEntry != null) {
            if (pinPopupEntry.jei() != null) {
                com.alonie.brbe.render.PopupRenderer.renderJeiPopup(
                        gui, pinPopupEntry.jei(),
                        pinPopupX - 12, pinPopupY - 12, 24, 24, 2.0F);
            } else {
                com.alonie.brbe.render.PopupRenderer.renderRecipePopup(
                        gui, pinPopupEntry.holder(),
                        com.alonie.brbe.render.PopupRenderer.modeFor(
                                category != null ? category.id() : null),
                        false, false,
                        pinPopupX - 12, pinPopupY - 12, 24, 24, false, 2.0F);
            }
        }

        // Page indicator
        if (pageCount > 1) {
            gui.drawString(Minecraft.getInstance().font,
                    Component.literal((page + 1) + "/" + pageCount),
                    left + PANEL_W - 40, top + 6, 0xFFFFFF);
            gui.drawString(Minecraft.getInstance().font,
                    Component.literal("< >"), left + 2, top + PANEL_H - 12, 0xFFFFFF);
        }
    }

    /** Render the entry tooltip (called from the deferred tooltip channel). */
    public static void renderTooltip(GuiGraphics gui, int mouseX, int mouseY) {
        if (!active) return;
        if (Minecraft.getInstance().screen != hostScreen) {
            close();
            return;
        }
        int left = panelLeft(Minecraft.getInstance().screen);
        int top = panelTop(Minecraft.getInstance().screen);
        int perPage = COLS * ROWS;
        int start = page * perPage;
        int end = Math.min(start + perPage, entries.size());
        for (int i = start; i < end; i++) {
            int col = (i - start) % COLS;
            int row = (i - start) / COLS;
            int bx = left + GRID_PAD_X + col * (BUTTON_W + 3);
            int by = top + GRID_PAD_Y + 8 + row * (BUTTON_H + 3);
            if (mouseX >= bx && mouseX < bx + BUTTON_W
                    && mouseY >= by && mouseY < by + BUTTON_H) {
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

    private static int panelLeft(Object screen) {
        Minecraft mc = Minecraft.getInstance();
        int w = mc.getWindow() == null ? 200 : mc.getWindow().getGuiScaledWidth();
        if (mc.screen instanceof AbstractContainerScreen<?> cs) {
            w = cs.width;
        }
        return (w - PANEL_W) / 2;
    }

    private static int panelTop(Object screen) {
        Minecraft mc = Minecraft.getInstance();
        int h = mc.getWindow() == null ? 200 : mc.getWindow().getGuiScaledHeight();
        if (mc.screen instanceof AbstractContainerScreen<?> cs) {
            h = cs.height;
        }
        return (h - PANEL_H) / 2;
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

    /** 当前鼠标悬停的配方条目（网格命中）。 */
    /** 命中按钮的 X（网格矩形反推）。 */
    private static int buttonRectXFor(int mouseX, int mouseY) {
        int left = panelLeft(Minecraft.getInstance().screen);
        int top = panelTop(Minecraft.getInstance().screen);
        int perPage = COLS * ROWS;
        int start = page * perPage;
        int end = Math.min(start + perPage, entries.size());
        for (int i = start; i < end; i++) {
            int col = (i - start) % COLS;
            int row = (i - start) / COLS;
            int bx = left + GRID_PAD_X + col * (BUTTON_W + 3);
            int by = top + GRID_PAD_Y + 8 + row * (BUTTON_H + 3);
            if (mouseX >= bx && mouseX < bx + BUTTON_W
                    && mouseY >= by && mouseY < by + BUTTON_H) {
                return bx;
            }
        }
        return left + GRID_PAD_X;
    }

    /** 命中按钮的 Y（网格矩形反推）。 */
    private static int buttonRectYFor(int mouseX, int mouseY) {
        int left = panelLeft(Minecraft.getInstance().screen);
        int top = panelTop(Minecraft.getInstance().screen);
        int perPage = COLS * ROWS;
        int start = page * perPage;
        int end = Math.min(start + perPage, entries.size());
        for (int i = start; i < end; i++) {
            int col = (i - start) % COLS;
            int row = (i - start) / COLS;
            int bx = left + GRID_PAD_X + col * (BUTTON_W + 3);
            int by = top + GRID_PAD_Y + 8 + row * (BUTTON_H + 3);
            if (mouseX >= bx && mouseX < bx + BUTTON_W
                    && mouseY >= by && mouseY < by + BUTTON_H) {
                return by;
            }
        }
        return top + GRID_PAD_Y + 8;
    }

    private static DisplayEntry hoveredEntry(int mouseX, int mouseY) {
        int left = panelLeft(Minecraft.getInstance().screen);
        int top = panelTop(Minecraft.getInstance().screen);
        int perPage = COLS * ROWS;
        int start = page * perPage;
        int end = Math.min(start + perPage, entries.size());
        for (int i = start; i < end; i++) {
            int col = (i - start) % COLS;
            int row = (i - start) / COLS;
            int bx = left + GRID_PAD_X + col * (BUTTON_W + 3);
            int by = top + GRID_PAD_Y + 8 + row * (BUTTON_H + 3);
            if (mouseX >= bx && mouseX < bx + BUTTON_W
                    && mouseY >= by && mouseY < by + BUTTON_H) {
                return entries.get(i);
            }
        }
        return null;
    }

    private static int mouseXFor(Object screen) {
        var mc = Minecraft.getInstance();
        return mc.mouseHandler != null ? (int) mc.mouseHandler.xpos() : 0;
    }

    private static int mouseYFor(Object screen) {
        var mc = Minecraft.getInstance();
        return mc.mouseHandler != null ? (int) mc.mouseHandler.ypos() : 0;
    }

    private static int tabAt(double mouseX, double mouseY, AbstractContainerScreen<?> screen) {
        int tabX = panelLeft(screen) + 4;
        int top = panelTop(screen);
        int tabY = top + TAB_BAR_Y - 18;
        List<RecipeViewerCategory> cats = RecipeViewerCategories.all();
        for (int i = 0; i < cats.size(); i++) {
            if (mouseX >= tabX && mouseX < tabX + TAB_W - 2
                    && mouseY >= tabY && mouseY < tabY + TAB_H - 2) {
                return i;
            }
            tabX += TAB_W;
        }
        return -1;
    }

    private static int pageAt(double mouseX, double mouseY, AbstractContainerScreen<?> screen) {
        int left = panelLeft(screen);
        int top = panelTop(screen);
        if (mouseY >= top + PANEL_H - 14 && mouseY < top + PANEL_H - 4) {
            if (mouseX >= left + 4 && mouseX < left + 10) return -1; // prev
            if (mouseX >= left + 12 && mouseX < left + 18) return 1; // next
        }
        return 0;
    }
}
