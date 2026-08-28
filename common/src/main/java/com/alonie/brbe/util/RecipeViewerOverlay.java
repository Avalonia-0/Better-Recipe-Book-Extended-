package com.alonie.brbe.util;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.mixins.accessors.OverlayRecipeComponentAccessor;
import com.alonie.brbe.recipeviewer.RecipeViewerCategories;
import com.alonie.brbe.recipeviewer.RecipeViewerCategory;
import com.alonie.brbe.recipeviewer.engine.RecipeViewerEngine;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.recipebook.OverlayRecipeComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.inventory.AbstractContainerMenu;

import java.util.ArrayList;
import java.util.List;

/**
 * 1.21.1 查询浮层 —— **按 1.21.11 结构重写**（2026-08-29，用户指示"对照高版本
 * 重写，不再复制后小修小改"）。
 *
 * <p>旧版浮层全部自绘（recipe_book 背景 + 红框格子 + 手工 tab），与高版本参考
 * 界面（截图：overlay_recipe 大框 + 原版 alternative-overlay 格子 + rbip 底部
 * 标签条）差之千里。重写采用与 1.21.11 相同的组件/资源：</p>
 * <ul>
 *   <li>**网格 = vanilla {@link OverlayRecipeComponent}**：一页一个
 *       {@link RecipeCollection}（条目 RecipeHolder 列表 → updateKnownRecipes），
 *       其 recipe 按钮（crafting/furnace overlay 纹理格子）就是 1.21.11 参考图
 *       中的格子（1.21.1 版本的原版纹理，色调随版本），重排到 10 列</li>
 *   <li>**框体 = {@code recipe_book/overlay_recipe} 9-slice**（1.21.11 查询框同款）
 *       ，锚定打开时光标快照（左上展开，边界钳制）</li>
 *   <li>**分类标签 = {@code brbe:textures/rbip/bottom_tab(.selected).png}**
 *       （RBIP 同源贴图，与 1.21.11 的标签条同款）</li>
 *   <li>翻页控制（&lt; &gt; 文本按钮）· pin 标记 · Shift 预览（PopupRenderer）·
 *       tooltip 与 1.21.11 语义一致</li>
 * </ul>
 *
 * <p>数据/输入语义不变：R=查询配方、U=查询用途、A=固定悬停配方、ESC=关闭；
 * grid 类别（燃料/堆肥等）保持独立物品网格（参考图的纯信息网格）。</p>
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
    /** 打开时的锚点（光标位置快照）：面板固定在该处展开——**不能**用每帧
     *  实时鼠标位置（面板会跟着光标游走）。 */
    private static int anchorX;
    private static int anchorY;
    /** pinoverlay 弹窗：A 键固定后展示的配方（旁边大弹窗）；关闭 viewer 清空。 */
    private static DisplayEntry pinPopupEntry;
    private static int pinPopupX;
    private static int pinPopupY;
    private static boolean pinPopupActive;

    /** 当前页的 vanilla 替代配方网格（1.21.11 同款组件）。 */
    private static final OverlayRecipeComponent overlayComponent = new OverlayRecipeComponent();
    /** 当前页集合（overlayComponent 的数据源）。 */
    private static RecipeCollection displayCollection;

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

    // -- Geometry (reference-style anchored box) --------------------------------

    /** 面板框体（原版替代配方组背景 sprite，9-slice 可拉伸——1.21.11 查询框同款）。 */
    private static final ResourceLocation PANEL_BACKGROUND =
            ResourceLocation.withDefaultNamespace("recipe_book/overlay_recipe");

    private static final int PAGE_COLS = 10;
    private static final int PAGE_ROWS = 5;
    private static final int CELL = 25;
    private static final int BOX_PAD = 8;
    private static final int PANEL_W = PAGE_COLS * CELL + BOX_PAD;  // 258
    private static final int PANEL_H = PAGE_ROWS * CELL + BOX_PAD;  // 133
    private static final int GRID_OFFSET = 4;

    /** 分类标签条（rbip bottom_tab 贴图，35x27 内取 24x22 绘制）。 */
    private static final ResourceLocation BOTTOM_TAB =
            ResourceLocation.fromNamespaceAndPath("brbe", "textures/rbip/bottom_tab.png");
    private static final ResourceLocation BOTTOM_TAB_SELECTED =
            ResourceLocation.fromNamespaceAndPath("brbe", "textures/rbip/bottom_tab_selected.png");
    private static final int TAB_W = 24;
    private static final int TAB_H = 22;
    private static final int TAB_GAP = 2;

    private static final int ANCHOR_DX = 16;
    private static final int ANCHOR_DY = 16;

    private RecipeViewerOverlay() {}

    // -- Public API -------------------------------------------------------------

    public static boolean isActive() {
        return active;
    }

    public static ItemStack target() {
        return target;
    }

    public static RecipeViewerCategory currentCategory() {
        return category;
    }

    // -- Open / close -----------------------------------------------------------

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
        anchorX = mouseXFor(screen);
        anchorY = mouseYFor(screen);
        applyCategory();
        return true;
    }

    public static void close() {
        active = false;
        hostScreen = null;
        target = ItemStack.EMPTY;
        category = null;
        entries = new ArrayList<>();
        page = 0;
        pageCount = 1;
        displayCollection = null;
        pinPopupActive = false;
        pinPopupEntry = null;
    }

    // -- Key input ---------------------------------------------------------------

    public static boolean keyPressed(int keyCode, int scanCode, int modifiers,
                                     AbstractContainerScreen<?> screen,
                                     net.minecraft.world.inventory.Slot hoveredSlot) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != screen) return false;

        if (active) {
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

    public static boolean mouseClicked(double mouseX, double mouseY, int button,
                                       AbstractContainerScreen<?> screen) {
        if (!active) return false;

        // Page controls (top-left, above the box)
        if (button == 0) {
            int p = pageAt(mouseX, mouseY);
            if (p == -1 && page > 0) { page--; rebuildButtons(); return true; }
            if (p == 1 && page < pageCount - 1) { page++; rebuildButtons(); return true; }
        }

        // Category tabs (bottom strip)
        if (button == 0) {
            List<RecipeViewerCategory> cats = RecipeViewerCategories.all();
            int idx = tabAt(mouseX, mouseY);
            if (idx >= 0 && idx < cats.size() && cats.get(idx) != category) {
                category = cats.get(idx);
                applyCategory();
                return true;
            }
        }

        return false;
    }

    // -- Render -------------------------------------------------------------------

    /** Render the viewer overlay (called from platform after-render hooks —
     *  整屏渲染完成后、所有内容之上). */
    public static void render(GuiGraphics gui, int mouseX, int mouseY, float delta) {
        if (!active) return;
        if (Minecraft.getInstance().screen != hostScreen) {
            close();
            return;
        }

        int left = panelLeft();
        int top = panelTop();

        // 标签条先画（背层），框体覆盖其顶边（1.21.11 同款层次）
        drawTabs(gui, left, top, false);

        // 框体
        gui.blitSprite(PANEL_BACKGROUND, left, top, PANEL_W, PANEL_H);

        // 标题行（框体上方，参考界面无框内标题——避免盖住首行格子）
        gui.renderFakeItem(target, left + 2, top - 12);
        gui.drawString(Minecraft.getInstance().font,
                Component.translatable(viewUsage ? "brbe.viewer.usage" : "brbe.viewer.recipe")
                        .append(": ")
                        .append(target.getHoverName()),
                left + 22, top - 9, 0xFFFFFF);

        // Grid category (fuel/compost/info): standalone item grid
        if (category.isGridCategory()) {
            drawItemGrid(gui, left, top, mouseX, mouseY);
            drawTabs(gui, left, top, true);
            drawPageControls(gui, left, top);
            return;
        }

        // 配方网格：vanilla overlay 按钮（10 列重排）
        List<AbstractWidget> buttons = currentButtons();
        for (AbstractWidget w : buttons) {
            w.render(gui, mouseX, mouseY, delta);
        }
        drawPinMarkers(gui, buttons);

        // pinoverlay 弹窗（固定配方展示）
        if (pinPopupActive && pinPopupEntry != null) {
            renderPinPopup(gui);
        }

        // 标签条选中态盖顶 + 翻页控制
        drawTabs(gui, left, top, true);
        drawPageControls(gui, left, top);
    }

    private static List<AbstractWidget> currentButtons() {
        return displayCollection == null ? List.of()
                : ((OverlayRecipeComponentAccessor) (Object) overlayComponent).getRecipeButtons();
    }

    private static void drawPinMarkers(GuiGraphics gui, List<AbstractWidget> buttons) {
        if (buttons.isEmpty()) return;
        int start = page * (PAGE_COLS * PAGE_ROWS);
        for (int i = 0; i < buttons.size(); i++) {
            int idx = start + i;
            if (idx < entries.size() && entries.get(idx).isPinned()) {
                AbstractWidget w = buttons.get(i);
                gui.blitSprite(BRBTextures.RECIPE_BOOK_PIN_SPRITE,
                        w.getX() - 4, w.getY() - 4, 32, 32);
            }
        }
    }

    /** 高版本参考：左上（框外）◀ ▶ 翻页按钮。 */
    private static void drawPageControls(GuiGraphics gui, int left, int top) {
        if (pageCount <= 1) return;
        String label = (page + 1) + "/" + pageCount;
        int y = top + 2;
        gui.drawString(Minecraft.getInstance().font, "<", left - 12, y, 0xFFFFFF);
        gui.drawString(Minecraft.getInstance().font, ">", left - 4, y, 0xFFFFFF);
        gui.drawString(Minecraft.getInstance().font, label, left + PANEL_W - Minecraft.getInstance().font.width(label) - 4, y, 0xFFFFFF);
    }

    /** 分类标签条（rbip bottom_tab 纹理；selected 在首层重绘盖顶）。 */
    private static void drawTabs(GuiGraphics gui, int left, int top, boolean selectedOnTop) {
        List<RecipeViewerCategory> cats = RecipeViewerCategories.all();
        int x = left + 2;
        int y = top + PANEL_H - 8;
        for (RecipeViewerCategory cat : cats) {
            boolean sel = cat == category;
            if (sel != selectedOnTop) continue;
            gui.blit(sel ? BOTTOM_TAB_SELECTED : BOTTOM_TAB,
                    x, y, 0, 0, TAB_W, TAB_H + 6, 35, 27);
            gui.renderFakeItem(cat.icon(), x + 4, y + 3);
            x += TAB_W + TAB_GAP;
        }
    }

    /** 纯信息网格（燃料/堆肥/信息类别）：overlay 框体内的物品格子。 */
    private static void drawItemGrid(GuiGraphics gui, int left, int top, int mouseX, int mouseY) {
        List<ItemStack> grid = category.gridItems(target, viewUsage);
        List<ItemStack> all = grid.isEmpty() ? category.allGridItems() : grid;
        int perPage = PAGE_COLS * PAGE_ROWS;
        int gridStart = page * perPage;
        int gridEnd = Math.min(gridStart + perPage, all.size());
        for (int gi = gridStart; gi < gridEnd; gi++) {
            int col = (gi - gridStart) % PAGE_COLS;
            int row = (gi - gridStart) / PAGE_COLS;
            int bx = left + GRID_OFFSET + col * CELL;
            int by = top + GRID_OFFSET + row * CELL;
            boolean hovered = mouseX >= bx && mouseX < bx + CELL
                    && mouseY >= by && mouseY < by + CELL;
            gui.blitSprite(BRBTextures.RECIPE_BOOK_BUTTON_SLOT_UNCRAFTABLE_SPRITE,
                    bx, by, CELL, CELL);
            gui.renderFakeItem(all.get(gi), bx + 4, by + 4);
            if (hovered) {
                gui.fill(bx, by, bx + CELL, by + CELL, 0x40FFFFFF);
            }
        }
    }

    private static void renderPinPopup(GuiGraphics gui) {
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

    /** Render the entry tooltip (called from the deferred tooltip channel). */
    public static void renderTooltip(GuiGraphics gui, int mouseX, int mouseY) {
        if (!active) return;
        if (Minecraft.getInstance().screen != hostScreen) {
            close();
            return;
        }
        int left = panelLeft();
        int top = panelTop();
        int perPage = PAGE_COLS * PAGE_ROWS;
        int start = page * perPage;
        int end = Math.min(start + perPage, entries.size());
        for (int i = start; i < end; i++) {
            int col = (i - start) % PAGE_COLS;
            int row = (i - start) / PAGE_COLS;
            int bx = left + GRID_OFFSET + col * CELL;
            int by = top + GRID_OFFSET + row * CELL;
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
        pageCount = Math.max(1, (entries.size() + PAGE_COLS * PAGE_ROWS - 1) / (PAGE_COLS * PAGE_ROWS));
        rebuildButtons();
    }

    /** 重建当前页的 vanilla overlay 网格（一页一个 RecipeCollection，10 列重排）。 */
    private static void rebuildButtons() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null || mc.player == null) return;
        int start = page * (PAGE_COLS * PAGE_ROWS);
        int end = Math.min(start + PAGE_COLS * PAGE_ROWS, entries.size());
        List<RecipeHolder<?>> holders = new ArrayList<>();
        for (int i = start; i < end; i++) {
            if (entries.get(i).holder() != null) {
                holders.add(entries.get(i).holder());
            }
        }
        if (holders.isEmpty()) {
            displayCollection = null;
            return;
        }
        displayCollection = new RecipeCollection(mc.level.registryAccess(), holders);
        displayCollection.updateKnownRecipes(mc.player.getRecipeBook());
        int left = panelLeft();
        int top = panelTop();
        overlayComponent.init(mc, displayCollection, left + GRID_OFFSET, top + GRID_OFFSET,
                (int) mc.mouseHandler.xpos(), (int) mc.mouseHandler.ypos(), CELL);
        List<AbstractWidget> buttons =
                ((OverlayRecipeComponentAccessor) (Object) overlayComponent).getRecipeButtons();
        for (int i = 0; i < buttons.size(); i++) {
            int col = i % PAGE_COLS;
            int row = i / PAGE_COLS;
            buttons.get(i).setPosition(left + GRID_OFFSET + col * CELL,
                    top + GRID_OFFSET + row * CELL);
        }
    }

    /** 面板锚定：固定在打开时的锚点（光标快照）左上展开，屏幕边界钳制。 */
    private static int panelLeft() {
        Minecraft mc = Minecraft.getInstance();
        int w = mc.getWindow() == null ? 200 : mc.getWindow().getGuiScaledWidth();
        if (mc.screen instanceof AbstractContainerScreen<?> cs) {
            w = cs.width;
        }
        int left = anchorX - ANCHOR_DX - PANEL_W + 32;
        if (left < 2) {
            left = anchorX + ANCHOR_DX;
        }
        return Math.max(2, Math.min(left, w - PANEL_W - 2));
    }

    private static int panelTop() {
        Minecraft mc = Minecraft.getInstance();
        int h = mc.getWindow() == null ? 200 : mc.getWindow().getGuiScaledHeight();
        if (mc.screen instanceof AbstractContainerScreen<?> cs) {
            h = cs.height;
        }
        int top = anchorY - PANEL_H + ANCHOR_DY;
        if (top < 2) {
            top = anchorY + 20;
        }
        return Math.min(top, h - PANEL_H - 2);
    }

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

    /** 翻页控制命中（框左侧上方 < 与 >）。 */
    private static int pageAt(double mouseX, double mouseY) {
        int left = panelLeft();
        int top = panelTop();
        if (mouseY >= top - 2 && mouseY < top + 12) {
            int x = (int) mouseX;
            if (x >= left - 14 && x < left - 6) return -1;
            if (x >= left - 5 && x < left + 3) return 1;
        }
        return 0;
    }

    private static int tabAt(double mouseX, double mouseY) {
        int left = panelLeft();
        int top = panelTop();
        int y0 = top + PANEL_H - 8;
        if (mouseY < y0 - 2 || mouseY >= y0 + TAB_H + 8) return -1;
        int x = left + 2;
        for (int i = 0; i < RecipeViewerCategories.all().size(); i++) {
            if (mouseX >= x && mouseX < x + TAB_W) return i;
            x += TAB_W + TAB_GAP;
        }
        return -1;
    }

    private static int buttonRectXFor(int mouseX, int mouseY) {
        int left = panelLeft();
        int top = panelTop();
        int perPage = PAGE_COLS * PAGE_ROWS;
        int start = page * perPage;
        int end = Math.min(start + perPage, entries.size());
        for (int i = start; i < end; i++) {
            int col = (i - start) % PAGE_COLS;
            int row = (i - start) / PAGE_COLS;
            int bx = left + GRID_OFFSET + col * CELL;
            int by = top + GRID_OFFSET + row * CELL;
            if (mouseX >= bx && mouseX < bx + CELL
                    && mouseY >= by && mouseY < by + CELL) {
                return bx;
            }
        }
        return left + GRID_OFFSET;
    }

    private static int buttonRectYFor(int mouseX, int mouseY) {
        int left = panelLeft();
        int top = panelTop();
        int perPage = PAGE_COLS * PAGE_ROWS;
        int start = page * perPage;
        int end = Math.min(start + perPage, entries.size());
        for (int i = start; i < end; i++) {
            int col = (i - start) % PAGE_COLS;
            int row = (i - start) / PAGE_COLS;
            int bx = left + GRID_OFFSET + col * CELL;
            int by = top + GRID_OFFSET + row * CELL;
            if (mouseX >= bx && mouseX < bx + CELL
                    && mouseY >= by && mouseY < by + CELL) {
                return by;
            }
        }
        return top + GRID_OFFSET;
    }

    private static DisplayEntry hoveredEntry(int mouseX, int mouseY) {
        int left = panelLeft();
        int top = panelTop();
        int perPage = PAGE_COLS * PAGE_ROWS;
        int start = page * perPage;
        int end = Math.min(start + perPage, entries.size());
        for (int i = start; i < end; i++) {
            int col = (i - start) % PAGE_COLS;
            int row = (i - start) / PAGE_COLS;
            int bx = left + GRID_OFFSET + col * CELL;
            int by = top + GRID_OFFSET + row * CELL;
            if (mouseX >= bx && mouseX < bx + CELL
                    && mouseY >= by && mouseY < by + CELL) {
                return entries.get(i);
            }
        }
        return null;
    }
}
