package com.alonie.brbe.util;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.cache.RecipeViewerIndex;
import com.alonie.brbe.mixins.accessors.AbstractContainerScreenAccessor;
import com.alonie.brbe.mixins.accessors.InventoryAccessor;
import com.alonie.brbe.mixins.accessors.OverlayRecipeButtonAccessor;
import com.alonie.brbe.mixins.accessors.OverlayRecipeComponentAccessor;
import com.alonie.brbe.recipeviewer.CompostRecipeCategory;
import com.alonie.brbe.recipeviewer.RecipeViewerCategories;
import com.alonie.brbe.recipeviewer.RecipeViewerCategory;
import com.alonie.brbe.recipeviewer.engine.RecipeViewerEngine;
import com.alonie.brbe.render.PopupRenderer;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.recipebook.OverlayRecipeComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 1.21.1 查询浮层 —— 按 1.21.11 的 RecipeViewerOverlay 结构逐段移植
 * （2026-08-29，用户指示"系统性地照着 1.21.11 重做"）。
 *
 * <p>与 1.21.11 相同的部件：</p>
 * <ul>
 *   <li>**框体** = {@code recipe_book/overlay_recipe} 9-slice，锚定打开时光标快照
 *       （首个对象中心对齐光标，自底向上排布，fitBoxToPage 按实际页内容收缩）；</li>
 *   <li>**配方网格** = vanilla {@link OverlayRecipeComponent}（一页一个
 *       {@link RecipeCollection}），按钮重排到页面列宽、行自下而上；JEI 条目
 *       （无 RecipeHolder）以 plain_overlay 格子补画在同一网格位置；</li>
 *   <li>**分类标签条** = {@code brbe:textures/rbip/bottom_tab(.selected).png}
 *       -90° 旋转 + 中部 TAB_CUT 拼贴（1.21.11 同款几何与常量），未选标签垫高
 *       2px 被框体盖住顶边、选中标签首层重绘，标签 icon 按 25px 列距对齐；</li>
 *   <li>**翻页按钮** = {@code brbe:textures/rbip/recipe_book_buttons.png}
 *       （14x13，框上方），悬停高亮/禁用态/Ctrl 跳页/scrollAround 绕回同 1.21.11；</li>
 *   <li>**左侧工作站列** = 框左外挂 25px 列（column_panel 9-slice + plain_overlay
 *       24px 格子，自底向上窗口滚动，点击重新查询该工作站）；</li>
 *   <li>**纯信息网格**（燃料/堆肥/酿造）= plain_overlay 格子（悬停换高亮贴图）；</li>
 *   <li>**模态交互**：框内点击吞掉、框外关闭、滚轮翻页/切标签/滑工作站列、
 *       弹窗打开时全屏吞点击与滚轮、Ctrl+O 浏览全部。</li>
 * </ul>
 *
 * <p>1.21.1 已知降级（数据/API 鸿沟，见 1.21.1/CLAUDE.md 轮次记录）：</p>
 * <ul>
 *   <li>无 PinOverlay 独立浮层——A 键固定后以按钮旁弹窗"固定即预览"；</li>
 *   <li>Shift 预览用轻量 PopupRenderer（固定 48x48 布局，无 JEI 完整界面）；</li>
 *   <li>tooltip 为文本行（1.21.1 GuiGraphics 无带 style 的 ClientTooltipComponent
 *       渲染入口），燃料行/堆肥概率/模组名保留；</li>
 *   <li>光标手势（requestCursor）1.21.1 无此 API，省略；</li>
 *   <li>配方按钮点击只吞 + 按钮音（无幽灵放置——1.21.1 无 tryPlaceRecipe 注入链）。</li>
 * </ul>
 */
public final class RecipeViewerOverlay {

    // ── 组件 ────────────────────────────────────────────────────────────────
    /** 当前页的 vanilla 替代配方网格（1.21.11 同款组件）。 */
    private static final OverlayRecipeComponent overlayComponent = new OverlayRecipeComponent();
    /** overlayComponent 的数据源（当前页集合）。 */
    private static RecipeCollection currentCollection;

    // ── 状态 ────────────────────────────────────────────────────────────────
    private static boolean active;
    /** 打开 viewer 的宿主屏幕：只在它打开时绘制（其他屏幕自动关闭，防泄漏）。 */
    private static AbstractContainerScreen<?> hostScreen;
    private static boolean queryUsage;
    private static ItemStack queryTarget = ItemStack.EMPTY;
    private static RecipeViewerCategory currentCategory;
    /** 当前类别全部条目（跨页）。 */
    private static List<DisplayEntry> entries = new ArrayList<>();
    private static int page;
    private static int pageCount = 1;

    // ── 几何（1.21.11 同款模型） ────────────────────────────────────────────
    private static int boxX;
    private static int boxY;
    private static int boxW;
    private static int boxH;
    /** 首行首个对象中心的锚点（打开=光标快照；每次 fitBoxToPage 后刷新）。 */
    private static int anchorScreenX;
    private static int anchorScreenY;
    /** 标签条锚定线（框底），打开时固定 = 锚点+16，永不被钳位改写。 */
    private static int bottomAnchor;
    /** REI 式标签滑动窗口起点。 */
    private static int tabWindowStart;
    /** Ctrl+O 浏览全部模式。 */
    private static boolean browseAllMode;
    private static int browseAllReturnPage;
    private static RecipeViewerCategory browseAllReturnCategory;

    // ── grid 类别 ───────────────────────────────────────────────────────────
    private static List<ItemStack> gridItems = List.of();
    private static ItemStack gridHoverStack;
    private static RecipeViewerCategory gridHoverCategory;

    // ── 工作站列 ────────────────────────────────────────────────────────────
    private static List<ItemStack> stationColumnItems = List.of();
    private static int stationScroll;

    // ── pin 弹窗（1.21.1 轻量：固定即预览） ─────────────────────────────────
    private static DisplayEntry pinPopupEntry;
    private static int pinPopupX;
    private static int pinPopupY;
    private static boolean pinPopupActive;

    // ── 每页渲染态（showPage 重建） ─────────────────────────────────────────
    /** 当前页条目（与 pageButtons 平行：holder→按钮，jei→null）。 */
    private static List<DisplayEntry> pageEntries = List.of();
    private static List<AbstractWidget> pageButtons = List.of();
    private static int pageColumns = 1;
    /** 渲染期悬停捕获。 */
    private static AbstractWidget hoveredButton;
    private static int hoveredIndex = -1;
    // ── Shift 预览弹窗 ──
    private static boolean popupOpen;
    private static int[] popupRect;
    private static int popupAnchorIndex = -1;
    // ── 延迟 tooltip（渲染期暂存，renderTooltip 通道统一绘制在最上层） ──
    private static List<Component> pendingTabTooltip;
    private static int pendingTabTooltipX;
    private static int pendingTabTooltipY;
    private static List<Component> pendingStationTooltip;
    private static int pendingStationTooltipX;
    private static int pendingStationTooltipY;

    // ── 常量（1.21.11 同值） ────────────────────────────────────────────────
    private static final ResourceLocation OVERLAY_RECIPE_SPRITE =
            ResourceLocation.withDefaultNamespace("recipe_book/overlay_recipe");
    private static final ResourceLocation RBIP_PAGE_BUTTONS =
            ResourceLocation.fromNamespaceAndPath("brbe", "textures/rbip/recipe_book_buttons.png");
    private static final int PAGE_COLS = 10;
    private static final int PAGE_ROWS = 5;
    private static final int PAGE_SIZE = PAGE_COLS * PAGE_ROWS;
    private static final int PAGE_BTN_WIDTH = 14;
    private static final int PAGE_BTN_HEIGHT = 13;
    private static final int STATION_CELL = 24;
    private static final int STATION_PITCH = 25;
    private static final int STATION_COL_WIDTH = 25;

    /** 分类标签（1.21.11 拼贴几何）：35x27 贴图 -90° 旋转显示为 27x35，
     *  中部 6px（TAB_CUT）横向切除拼接，TAB_V_CUT 纵向切除使面板恰为 25px 宽。 */
    private static final ResourceLocation UNSELECTED_BOTTOM_TAB =
            ResourceLocation.fromNamespaceAndPath("brbe", "textures/rbip/bottom_tab.png");
    private static final ResourceLocation SELECTED_BOTTOM_TAB =
            ResourceLocation.fromNamespaceAndPath("brbe", "textures/rbip/bottom_tab_selected.png");
    private static final int TAB_TEX_WIDTH = 35;
    private static final int TAB_TEX_HEIGHT = 27;
    private static final int TAB_CUT = 6;
    private static final int TAB_LEFT = 16;
    private static final int TAB_RIGHT_START = TAB_LEFT + TAB_CUT;
    private static final int TAB_WIDTH = 25;
    private static final int TAB_DRAW_WIDTH = TAB_WIDTH;
    private static final int TAB_V_TOP = 13;
    private static final int TAB_V_CUT = TAB_TEX_HEIGHT - TAB_DRAW_WIDTH;
    private static final int TAB_V_BOTTOM = TAB_TEX_HEIGHT - TAB_V_TOP - TAB_V_CUT;
    private static final int TAB_HEIGHT = TAB_TEX_WIDTH - TAB_CUT;
    private static final int TAB_OVERHANG = TAB_HEIGHT - 4;
    private static final int MAX_TABS = 10;

    /** 工作站列面板（9-slice，右开口与框体无缝相接；顶部裁切变体）。 */
    private static final ResourceLocation COLUMN_PANEL_SPRITE =
            ResourceLocation.fromNamespaceAndPath("brbe", "recipe_book/column_panel");
    private static final ResourceLocation COLUMN_PANEL_TOP_SPRITE =
            ResourceLocation.fromNamespaceAndPath("brbe", "recipe_book/column_panel_top");
    /** 纯信息格（1.21.11 同款：普通/悬停高亮）。 */
    private static final ResourceLocation PLAIN_OVERLAY =
            ResourceLocation.fromNamespaceAndPath("brbe", "recipe_book/plain_overlay");
    private static final ResourceLocation PLAIN_OVERLAY_HIGHLIGHTED =
            ResourceLocation.fromNamespaceAndPath("brbe", "recipe_book/plain_overlay_highlighted");

    /** 统一网格条目：vanilla RecipeHolder 或 JEI 条目。 */
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
        boolean isPinned() {
            if (holder != null) return BetterRecipeBook.pinnedRecipeManager.isPinnedEntry(holder);
            return jei != null && BetterRecipeBook.pinnedRecipeManager.isPinnedUid(jei.typeUid());
        }
        void togglePin() {
            if (holder != null) {
                BetterRecipeBook.pinnedRecipeManager.toggleFavourite(holder);
            } else if (jei != null) {
                BetterRecipeBook.pinnedRecipeManager.toggleFavouriteUid(jei.typeUid());
            }
        }
    }

    private RecipeViewerOverlay() {}

    // ── 状态查询 ────────────────────────────────────────────────────────────
    public static boolean isActive() {
        return active;
    }

    public static ItemStack target() {
        return queryTarget;
    }

    public static RecipeViewerCategory currentCategory() {
        return currentCategory;
    }

    private static boolean isGridMode() {
        return currentCategory != null && currentCategory.isGridCategory();
    }

    // ── 打开/关闭 ───────────────────────────────────────────────────────────
    public static boolean open(ItemStack stack, boolean usage, AbstractContainerScreen<?> screen) {
        if (stack == null || stack.isEmpty()) return false;
        return openFor(screen, stack, usage);
    }

    private static boolean openFor(AbstractContainerScreen<?> screen, ItemStack target, boolean usage) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return false;
        // 引擎按需重建：rebuildEngine 正常由配方书 setupCollections 触发，
        // 但进游戏后、配方书组件首次初始化前引擎是空的——此时 R/U 会打不开
        // （旧日志"U 查询工作台 opened=false"即此窗口）。查询前兜底重建一次。
        RecipeViewerIndex.flushEngineRebuildIfDirty();
        if (RecipeViewerEngine.allRecipes("minecraft:crafting").isEmpty()
                && RecipeViewerEngine.allRecipes("minecraft:smelting").isEmpty()
                && RecipeViewerEngine.allRecipes("minecraft:stonecutting").isEmpty()
                && RecipeViewerEngine.allRecipes("minecraft:smithing").isEmpty()) {
            RecipeViewerIndex.rebuildEngine();
        }
        RecipeViewerCategory cat = RecipeViewerCategories.defaultFor(
                target, usage, screen == null ? null : screen.getMenu());
        if (cat == null) {
            BetterRecipeBook.LOGGER.info("[BRBE-VIEWER] open refused: no category item={} usage={}",
                    target.getHoverName().getString(), usage);
            return false;
        }
        if (!cat.hasContent(target, usage)) {
            // 默认类别的命中为空：换"有内容的最高优先级类别"（1.21.11 防御性重选）
            RecipeViewerCategory alt = bestContentCategory(target, usage, cat);
            if (alt == null) {
                BetterRecipeBook.LOGGER.info("[BRBE-VIEWER] open refused: empty content cat={} item={} usage={}",
                        cat.id(), target.getHoverName().getString(), usage);
                return false;
            }
            cat = alt;
        }
        resetBrowseAllState();
        queryTarget = target;
        queryUsage = usage;
        currentCategory = cat;
        hostScreen = screen;
        // 锚点 = 光标快照（限制在窗口内）
        anchorScreenX = mouseXFor();
        anchorScreenY = mouseYFor();
        if (cat.isGridCategory()) {
            rebuildGrid(gridSource(cat));
        } else {
            rebuildWithHits(categoryHits(cat));
        }
        repaginateToSelected();
        rebuildStationColumn();
        active = true;
        BetterRecipeBook.LOGGER.info("[BRBE-VIEWER] opened cat={} entries={} pages={} item={} usage={}",
                cat.id(), entries.size(), pageCount, target.getHoverName().getString(), usage);
        return true;
    }

    /** 有内容且优先级最高的类别（排除 {@code exclude}）——1.21.11 语义。 */
    private static RecipeViewerCategory bestContentCategory(ItemStack target, boolean usage,
                                                            RecipeViewerCategory exclude) {
        RecipeViewerCategory best = null;
        int bestPriority = -1;
        for (RecipeViewerCategory category : RecipeViewerCategories.all()) {
            if (category == exclude) continue;
            int priority = category.defaultPriority(target);
            if (priority <= bestPriority) continue;
            if (category.hasContent(target, usage)) {
                best = category;
                bestPriority = priority;
            }
        }
        return best;
    }

    /** Dismiss the viewer: clear state before hiding so no guard cancels this
     *  sanctioned close. */
    public static void close() {
        active = false;
        hostScreen = null;
        queryTarget = ItemStack.EMPTY;
        queryUsage = false;
        currentCategory = null;
        entries = new ArrayList<>();
        page = 0;
        pageCount = 1;
        boxX = boxY = boxW = boxH = 0;
        anchorScreenX = anchorScreenY = bottomAnchor = 0;
        tabWindowStart = 0;
        resetBrowseAllState();
        browseAllReturnPage = 0;
        browseAllReturnCategory = null;
        gridItems = List.of();
        gridHoverStack = null;
        gridHoverCategory = null;
        stationColumnItems = List.of();
        stationScroll = 0;
        pinPopupActive = false;
        pinPopupEntry = null;
        pageEntries = List.of();
        pageButtons = List.of();
        pageColumns = 1;
        hoveredButton = null;
        hoveredIndex = -1;
        popupOpen = false;
        popupRect = null;
        popupAnchorIndex = -1;
        pendingTabTooltip = null;
        pendingStationTooltip = null;
        currentCollection = null;
        overlayComponent.setVisible(false);
    }

    // ── 键输入 ──────────────────────────────────────────────────────────────
    public static boolean keyPressed(int keyCode, int scanCode, int modifiers,
                                     AbstractContainerScreen<?> screen,
                                     Slot hoveredSlot) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != screen) return false;

        if (active) {
            // Ctrl+O：浏览全部（仅光标在查询界面内时生效，1.21.11 语义）
            if (keyCode == InputConstants.KEY_O) {
                int mx = mouseXFor();
                int my = mouseYFor();
                if (contains(mx, my) || (popupOpen && inRect(mx, my, popupRect))) {
                    toggleBrowseAll();
                    return true;
                }
            }
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
                int mx = mouseXFor();
                int my = mouseYFor();
                DisplayEntry hovered = cellEntryAt(mx, my);
                if (hovered != null) {
                    boolean wasPinned = hovered.isPinned();
                    hovered.togglePin();
                    boolean nowPinned = !wasPinned;
                    if (nowPinned) {
                        pinPopupEntry = hovered;
                        int[] cell = cellOfEntryAt(mx, my);
                        pinPopupX = cell[0] + 28;
                        pinPopupY = cell[1] - 8;
                        pinPopupActive = true;
                    } else {
                        pinPopupActive = false;
                        pinPopupEntry = null;
                    }
                    refreshAfterPin();
                    return true;
                }
            }
            return false;
        }

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

    /** R/U 重新查询：整体重开（锚点重新取光标，1.21.11 openFor 语义）。 */
    private static void reopen(AbstractContainerScreen<?> screen, boolean usage) {
        ItemStack target = queryTarget;
        close();
        if (screen != null && !target.isEmpty()) {
            openFor(screen, target, usage);
        }
    }

    // ── 鼠标 ────────────────────────────────────────────────────────────────
    /** Click handling while the viewer is up.  Returns true when consumed. */
    public static boolean mouseClicked(double mouseX, double mouseY, int button,
                                       AbstractContainerScreen<?> screen) {
        if (!active) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return false;

        // Shift 预览弹窗 = 硬模态：弹窗内左键给按钮音反馈并吞掉，弹窗外点击吞掉。
        if (popupOpen) {
            if (button == 0 && inRect(mouseX, mouseY, popupRect)) {
                playButtonClick(mc);
            }
            return true;
        }
        // 左侧工作站列点击：重新查询该工作站（优先于框背景吞点击）
        if (handleStationColumnClick(mouseX, mouseY, button)) {
            return true;
        }
        // 框内点击：吞掉（配方按钮格子点击给按钮音反馈）
        if (inBox(mouseX, mouseY)) {
            if (button == 0 && cellEntryAt(mouseX, mouseY) != null) {
                playButtonClick(mc);
            }
            return true;
        }
        // 分类标签点击：切类别；点击已选标签 = 浏览全部切换（1.21.11 语义）
        if (handleCategoryTabClick(mouseX, mouseY, button)) {
            return true;
        }
        // 翻页按钮点击
        if (handlePageButtonClick(mouseX, mouseY, button)) {
            return true;
        }
        // 框外：关闭 viewer（下层屏幕保持打开）
        close();
        return true;
    }

    /** Scroll while the viewer is up.  Returns true when consumed. */
    public static boolean mouseScrolled(double mouseX, double mouseY, double vertical) {
        if (!active) return false;
        // Shift 预览弹窗吞掉滚轮（翻页会重建按钮销毁弹窗）
        if (popupOpen) return true;
        if (vertical == 0) return true;
        // 分类标签条：切类别（REI 式滑动窗口）
        if (mouseScrolledTabs(mouseX, mouseY, vertical)) return true;
        // 左侧工作站列：滑窗口
        if (handleStationColumnScroll(mouseX, mouseY, vertical)) return true;
        if (pageCount > 1 && overScrollZone(mouseX, mouseY)) {
            int delta = vertical > 0 ? -1 : 1;
            int next = page + delta;
            if (BetterRecipeBook.config.scrolling.scrollAround && pageCount > 1) {
                next = (next % pageCount + pageCount) % pageCount;
            }
            if (next >= 0 && next < pageCount) {
                page = next;
                afterPageFlip();
            }
            return true;
        }
        // 打开的 viewer 是模态层：滚轮不穿透到下层（1.21.11 语义）
        return true;
    }

    private static void afterPageFlip() {
        ClientCompat.playPageFlipSound(Minecraft.getInstance());
        if (isGridMode()) {
            fitGridBoxToPage();
        } else {
            showPage(hostScreen);
        }
    }

    /** 框 + 上方翻页按钮条的滚轮翻页区。 */
    private static boolean overScrollZone(double mx, double my) {
        if (inside(mx, my, boxX, boxY, boxW, boxH)) return true;
        int btnY = boxY - PAGE_BTN_HEIGHT - 2;
        return inside(mx, my, boxX, btnY, PAGE_BTN_WIDTH * 2 + 15, PAGE_BTN_HEIGHT);
    }

    // ── 渲染 ────────────────────────────────────────────────────────────────
    public static void render(GuiGraphics gui, int mouseX, int mouseY, float delta) {
        if (!active) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != hostScreen) {
            close();
            return;
        }
        // 悬停/弹窗状态每帧重建
        hoveredButton = null;
        hoveredIndex = -1;
        gridHoverStack = null;
        gridHoverCategory = null;
        pendingTabTooltip = null;
        pendingStationTooltip = null;
        boolean shift = ClientCompat.isShiftDown();
        // 上一帧弹窗状态（"光标在已打开弹窗内保持打开"判定用），随后无条件复位——
        // 复位必须每帧执行：切到 grid 类别后若残留 popupOpen=true 会永久吞点击。
        boolean wasPopupOpen = popupOpen;
        int[] lastPopupRect = popupRect;
        popupOpen = false;

        // 纯信息网格类别（燃料/堆肥/酿造）：独立物品网格，无配方按钮
        if (isGridMode()) {
            drawCategoryTabs(gui, mouseX, mouseY, true);
            drawItemGrid(gui, mouseX, mouseY);
            drawPageControls(gui, mouseX, mouseY);
            drawCategoryTabs(gui, mouseX, mouseY, false);
            drawStationColumn(gui, mouseX, mouseY);
            drawPinPopup(gui);
            return;
        }

        // 未选标签先画（框体盖其顶边），框体、按钮、pin 标记、翻页控制、
        // 选中标签重绘（最上）、工作站列、pin 弹窗、Shift 预览弹窗。
        drawCategoryTabs(gui, mouseX, mouseY, true);
        gui.blitSprite(OVERLAY_RECIPE_SPRITE, boxX, boxY, boxW, boxH);
        for (int li = 0; li < pageEntries.size(); li++) {
            DisplayEntry entry = pageEntries.get(li);
            AbstractWidget w = pageButtons.get(li);
            int[] cell = gridCellFor(li);
            if (w != null) {
                w.render(gui, mouseX, mouseY, delta);
                if (w.isMouseOver(mouseX, mouseY)) {
                    hoveredButton = w;
                    hoveredIndex = page * PAGE_SIZE + li;
                }
            } else {
                // JEI 条目（无 RecipeHolder）：plain_overlay 格子 + 结果图标
                boolean hovered = inside(mouseX, mouseY, cell[0], cell[1], 25, 25);
                gui.blitSprite(hovered ? PLAIN_OVERLAY_HIGHLIGHTED : PLAIN_OVERLAY,
                        cell[0], cell[1], 24, 24);
                gui.renderItem(entry.result(), cell[0] + 4, cell[1] + 4);
                if (hovered) hoveredIndex = page * PAGE_SIZE + li;
            }
        }
        // 悬停按钮 2x 放大重绘（vanilla 替代配方网格观感；Shift 预览接管时跳过）
        if (hoveredButton != null && !shift) {
            gui.pose().pushPose();
            int cx = hoveredButton.getX() + 12;
            int cy = hoveredButton.getY() + 12;
            gui.pose().translate(cx, cy, 0);
            gui.pose().scale(2.0F, 2.0F, 1.0F);
            gui.pose().translate(-cx, -cy, 0);
            hoveredButton.render(gui, mouseX, mouseY, delta);
            gui.pose().popPose();
        }
        drawPinMarkers(gui);
        drawPageControls(gui, mouseX, mouseY);
        drawCategoryTabs(gui, mouseX, mouseY, false);
        drawStationColumn(gui, mouseX, mouseY);
        drawPinPopup(gui);
        // Shift 预览弹窗（最上层；悬停对象或已打开弹窗内保持）
        if (shift) {
            if (hoveredIndex >= 0 && hoveredIndex < entries.size()) {
                popupAnchorIndex = hoveredIndex;
            }
            if (popupAnchorIndex >= 0 && popupAnchorIndex < entries.size()) {
                if (hoveredIndex == popupAnchorIndex
                        || (wasPopupOpen && inRect(mouseX, mouseY, lastPopupRect))) {
                    renderShiftPopup(gui);
                }
            }
        } else {
            popupAnchorIndex = -1;
        }
    }

    /** 已 pin 条目角标（holder 按钮左上角 / JEI 格子左上角，1.21.11 同款锚点）。 */
    private static void drawPinMarkers(GuiGraphics gui) {
        for (int li = 0; li < pageEntries.size(); li++) {
            if (!pageEntries.get(li).isPinned()) continue;
            int[] cell = gridCellFor(li);
            gui.blitSprite(BRBTextures.RECIPE_BOOK_PIN_SPRITE,
                    cell[0] - 4, cell[1] - 4, 32, 32);
        }
    }

    /** 翻页按钮（RBIP 贴图 14x13，框上方左侧）+ 页码 tooltip。 */
    private static void drawPageControls(GuiGraphics gui, int mouseX, int mouseY) {
        if (pageCount <= 1) return;
        int bx = boxX;
        int btnY = boxY - PAGE_BTN_HEIGHT - 2;
        boolean wrap = BetterRecipeBook.config.scrolling.scrollAround;
        boolean prevActive = wrap || page > 0;
        boolean nextActive = wrap || page < pageCount - 1;
        drawPageButton(gui, bx, btnY, false, prevActive, mouseX, mouseY);
        drawPageButton(gui, bx + 15, btnY, true, nextActive, mouseX, mouseY);
    }

    private static void drawPageButton(GuiGraphics gui, int x, int y, boolean next,
                                       boolean activeButton, int mouseX, int mouseY) {
        int u = next ? 14 : 0;
        if (activeButton && inside(mouseX, mouseY, x, y, PAGE_BTN_WIDTH, PAGE_BTN_HEIGHT)) {
            u += 28;
        }
        int v = activeButton ? 0 : 13;
        gui.blit(RBIP_PAGE_BUTTONS, x, y, u, v, PAGE_BTN_WIDTH, PAGE_BTN_HEIGHT, 256, 256);
    }

    /** 分类标签条（-90° 旋转 + TAB_CUT 拼贴，1.21.11 同款）。 */
    private static void drawCategoryTabs(GuiGraphics gui, int mouseX, int mouseY,
                                         boolean behind) {
        if (!active) return;
        List<RecipeViewerCategory> cats = visibleCategories();
        if (cats.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        int perPage = MAX_TABS;
        int maxStart = Math.max(0, cats.size() - perPage);
        tabWindowStart = Math.max(0, Math.min(tabWindowStart, maxStart));
        int start = tabWindowStart;
        int end = Math.min(start + perPage, cats.size());
        int tabY = tabTop();
        for (int i = start; i < end; i++) {
            RecipeViewerCategory cat = cats.get(i);
            boolean selected = cat == currentCategory;
            if (selected == behind) continue;
            int x = tabX(i - start);
            ResourceLocation sprite = selected ? SELECTED_BOTTOM_TAB : UNSELECTED_BOTTOM_TAB;
            // 未选标签垫高 2px（部分藏在框后），选中标签整体下移露出
            int tabNudge = selected ? 0 : -2;
            gui.pose().pushPose();
            gui.pose().translate(x, tabY + TAB_HEIGHT + tabNudge, 0);
            gui.pose().mulPose(com.mojang.math.Axis.ZP.rotation(-(float) Math.PI / 2.0F));
            // 左半：纵向切掉 TAB_V_CUT 中部（保持两端圆角线）
            gui.blit(sprite, 0, 0, 0, 0, TAB_LEFT, TAB_V_TOP, TAB_TEX_WIDTH, TAB_TEX_HEIGHT);
            gui.blit(sprite, 0, TAB_V_TOP, 0, TAB_V_TOP + TAB_V_CUT,
                    TAB_LEFT, TAB_V_BOTTOM, TAB_TEX_WIDTH, TAB_TEX_HEIGHT);
            // 右半拼接到左半（跳过中部 TAB_CUT px）
            gui.blit(sprite, TAB_LEFT, 0, TAB_RIGHT_START, 0,
                    TAB_TEX_WIDTH - TAB_RIGHT_START, TAB_V_TOP, TAB_TEX_WIDTH, TAB_TEX_HEIGHT);
            gui.blit(sprite, TAB_LEFT, TAB_V_TOP, TAB_RIGHT_START, TAB_V_TOP + TAB_V_CUT,
                    TAB_TEX_WIDTH - TAB_RIGHT_START, TAB_V_BOTTOM, TAB_TEX_WIDTH, TAB_TEX_HEIGHT);
            gui.pose().popPose();
            int iconX = x + (TAB_DRAW_WIDTH - 16) / 2;
            int iconY = tabY + (selected ? 6 : 4);
            gui.renderItem(cat.icon(), iconX, iconY);
            if (cat.isFuelCategory()) {
                gui.blitSprite(BRBTextures.FURNACE_FIRE_SPRITE, iconX + 10, iconY + 10, 6, 6);
            }
            if (inside(mouseX, mouseY, x, tabY, TAB_WIDTH, TAB_HEIGHT)) {
                List<Component> lines = new ArrayList<>();
                lines.add(cat.name());
                appendModName(lines, cat.icon());
                pendingTabTooltip = lines;
                pendingTabTooltipX = mouseX;
                pendingTabTooltipY = mouseY;
            }
        }
    }

    /** X of the i-th visible tab (icon center 对齐列中线，1.21.11 同款)。 */
    private static int tabX(int i) {
        return boxX + 4 + i * TAB_WIDTH;
    }

    /** 标签条顶边（框底上方 4px，下压 1px）。 */
    private static int tabTop() {
        return boxY + boxH - 4 + 1;
    }

    /** 有内容的类别（标签隐藏空类别；浏览全部时 = 完整池非空）。 */
    private static List<RecipeViewerCategory> visibleCategories() {
        if (queryTarget == null || queryTarget.isEmpty()) return List.of();
        List<RecipeViewerCategory> out = new ArrayList<>();
        for (RecipeViewerCategory cat : RecipeViewerCategories.all()) {
            boolean has;
            if (browseAllMode) {
                has = cat.isGridCategory()
                        ? !cat.allGridItems().isEmpty()
                        : (!cat.allEntries().isEmpty() || !cat.allJeiEntries().isEmpty());
            } else {
                has = cat.hasContent(queryTarget, queryUsage);
            }
            if (has) out.add(cat);
        }
        return out;
    }

    /** 纯信息网格：plain_overlay 格子（悬停换高亮贴图），行自底向上。 */
    private static void drawItemGrid(GuiGraphics gui, int mouseX, int mouseY) {
        if (gridItems.isEmpty()) return;
        gui.blitSprite(OVERLAY_RECIPE_SPRITE, boxX, boxY, boxW, boxH);
        int start = page * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, gridItems.size());
        int columns = Math.max(1, Math.min(PAGE_COLS, end - start));
        gridHoverStack = null;
        gridHoverCategory = currentCategory;
        for (int i = start; i < end; i++) {
            int idx = i - start;
            int row = idx / columns;
            int gx = boxX + 4 + (idx % columns) * 25;
            int gy = boxY + boxH - 28 - row * 25;
            boolean hovered = inside(mouseX, mouseY, gx, gy, 24, 24);
            gui.blitSprite(hovered ? PLAIN_OVERLAY_HIGHLIGHTED : PLAIN_OVERLAY, gx, gy, 24, 24);
            gui.renderItem(gridItems.get(i), gx + 4, gy + 4);
            if (hovered) {
                gridHoverStack = gridItems.get(i);
            }
        }
    }

    // ── 工作站列 ────────────────────────────────────────────────────────────
    private static void rebuildStationColumn() {
        stationColumnItems = RecipeViewerIndex.stationColumnItemsFor(
                currentCategory == null ? "" : currentCategory.id());
        stationScroll = 0;
    }

    private static int stationViewRows() {
        return Math.max(1, (boxH - 8) / STATION_PITCH);
    }

    /** 裁切后列面板的 [top, height]（顶边距最顶格 5px，与框体同内边距）。 */
    private static int[] stationColumnPanelRect(int shown) {
        int bottom = boxY + boxH - 4;
        int colTop = bottom - shown * STATION_PITCH + 1 - 5;
        int colH = (boxY + boxH) - colTop;
        return new int[] { colTop, colH };
    }

    private static void drawStationColumnSurfaces(GuiGraphics gui) {
        if (stationColumnItems.isEmpty()) return;
        int rows = stationViewRows();
        int shown = Math.min(stationColumnItems.size(), rows);
        if (shown <= 0) return;
        int[] rect = stationColumnPanelRect(shown);
        ResourceLocation sprite = rect[0] == boxY ? COLUMN_PANEL_TOP_SPRITE : COLUMN_PANEL_SPRITE;
        gui.blitSprite(sprite, panelLeft(), rect[0], STATION_COL_WIDTH + 4, rect[1]);
    }

    /** 左侧工作站列：plain_overlay 24px 格子，自底向上，窗口滚动。 */
    private static void drawStationColumn(GuiGraphics gui, int mouseX, int mouseY) {
        if (stationColumnItems.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        drawStationColumnSurfaces(gui);
        int rows = stationViewRows();
        int maxScroll = Math.max(0, stationColumnItems.size() - rows);
        stationScroll = Math.max(0, Math.min(stationScroll, maxScroll));
        int x = panelLeft() + 4;
        int bottom = boxY + boxH - 4;
        int shown = Math.min(stationColumnItems.size(), rows);
        for (int j = 0; j < shown; j++) {
            int i = stationScroll + j;
            if (i >= stationColumnItems.size()) break;
            ItemStack stack = stationColumnItems.get(i);
            int gy = bottom - STATION_CELL - j * STATION_PITCH;
            boolean hovered = inside(mouseX, mouseY, x, gy, STATION_CELL, STATION_CELL);
            gui.blitSprite(hovered ? PLAIN_OVERLAY_HIGHLIGHTED : PLAIN_OVERLAY,
                    x, gy, STATION_CELL, STATION_CELL);
            gui.renderItem(stack, x + 4, gy + 4);
            if (hovered) {
                List<Component> lines = new ArrayList<>();
                lines.add(stack.getHoverName());
                appendModName(lines, stack);
                pendingStationTooltip = lines;
                pendingStationTooltipX = mouseX;
                pendingStationTooltipY = mouseY;
            }
        }
    }

    private static ItemStack stationCellAt(int mx, int my) {
        if (stationColumnItems.isEmpty()) return ItemStack.EMPTY;
        int rows = stationViewRows();
        int maxScroll = Math.max(0, stationColumnItems.size() - rows);
        stationScroll = Math.max(0, Math.min(stationScroll, maxScroll));
        int x = panelLeft() + 4;
        int bottom = boxY + boxH - 4;
        int shown = Math.min(stationColumnItems.size(), rows);
        for (int j = 0; j < shown; j++) {
            int i = stationScroll + j;
            if (i >= stationColumnItems.size()) break;
            int gy = bottom - STATION_CELL - j * STATION_PITCH;
            if (inside(mx, my, x, gy, STATION_CELL, STATION_CELL)) {
                return stationColumnItems.get(i);
            }
        }
        return ItemStack.EMPTY;
    }

    /** 点击工作站列对象 → 重新查询该对象（R 语义）+ 按钮音。 */
    private static boolean handleStationColumnClick(double mx, double my, int button) {
        if (button != 0 || stationColumnItems.isEmpty() || hostScreen == null) return false;
        ItemStack hit = stationCellAt((int) mx, (int) my);
        if (hit.isEmpty()) return false;
        Minecraft mc = Minecraft.getInstance();
        playButtonClick(mc);
        AbstractContainerScreen<?> screen = hostScreen;
        return openFor(screen, hit, false);
    }

    /** 工作站列滚轮：窗口滑动（仅当超过可视行数）。 */
    private static boolean handleStationColumnScroll(double mx, double my, double vertical) {
        if (vertical == 0) return false;
        if (stationColumnItems.size() <= stationViewRows()) return false;
        int[] rect = stationColumnPanelRect(
                Math.min(stationColumnItems.size(), stationViewRows()));
        if (!inside(mx, my, panelLeft(), rect[0], STATION_COL_WIDTH + 4, rect[1])) {
            return false;
        }
        int maxScroll = Math.max(0, stationColumnItems.size() - stationViewRows());
        int next = stationScroll + (vertical > 0 ? 1 : -1);
        if (next < 0 || next > maxScroll) return false;
        stationScroll = next;
        ClientCompat.playPageFlipSound(Minecraft.getInstance());
        return true;
    }

    // ── 标签点击/滚轮 ───────────────────────────────────────────────────────
    private static boolean handleCategoryTabClick(double mx, double my, int button) {
        if (button != 0) return false;
        int tabY = tabTop();
        List<RecipeViewerCategory> cats = visibleCategories();
        int perPage = MAX_TABS;
        int start = tabWindowStart;
        int end = Math.min(start + perPage, cats.size());
        for (int i = start; i < end; i++) {
            if (inside(mx, my, tabX(i - start), tabY, TAB_WIDTH, TAB_HEIGHT)) {
                RecipeViewerCategory cat = cats.get(i);
                Minecraft mc = Minecraft.getInstance();
                if (cat != currentCategory) {
                    ClientCompat.playPageFlipSound(mc);
                    switchCategory(cat);
                } else {
                    // 点击已选标签 = 浏览全部切换（1.21.11 语义）
                    ClientCompat.playPageFlipSound(mc);
                    toggleBrowseAll();
                }
                return true;
            }
        }
        return false;
    }

    private static boolean overTabStrip(double mx, double my) {
        int catCount = visibleCategories().size();
        if (catCount == 0) return false;
        int shown = Math.min(MAX_TABS, catCount);
        return inside(mx, my, boxX, tabTop(), shown * TAB_WIDTH, TAB_HEIGHT);
    }

    /** 标签条滚轮：切类别 + REI 式窗口滑动（选中到第 6 槽起随窗口滑动）。 */
    public static boolean mouseScrolledTabs(double mx, double my, double vertical) {
        if (!active || vertical == 0) return false;
        List<RecipeViewerCategory> cats = visibleCategories();
        if (cats.size() <= 1) return false;
        if (!overTabStrip(mx, my)) return false;
        int idx = cats.indexOf(currentCategory);
        if (idx < 0) return false;
        int delta = vertical > 0 ? -1 : 1;
        int newIdx = idx + delta;
        if (newIdx < 0 || newIdx >= cats.size()) return false;
        int maxStart = Math.max(0, cats.size() - MAX_TABS);
        int slot = idx - tabWindowStart;
        if (delta > 0 && maxStart > 0 && slot >= 5) {
            tabWindowStart = Math.min(maxStart, tabWindowStart + 1);
        } else if (delta < 0 && maxStart > 0 && slot <= 4) {
            tabWindowStart = Math.max(0, tabWindowStart - 1);
        }
        if (newIdx < tabWindowStart) {
            tabWindowStart = newIdx;
        } else if (newIdx >= tabWindowStart + MAX_TABS) {
            tabWindowStart = Math.min(maxStart, newIdx - (MAX_TABS - 1));
        }
        switchCategory(cats.get(newIdx));
        ClientCompat.playPageFlipSound(Minecraft.getInstance());
        return true;
    }

    // ── 翻页按钮点击 ────────────────────────────────────────────────────────
    private static boolean handlePageButtonClick(double mx, double my, int button) {
        if (pageCount <= 1 || button != 0) return false;
        int bx = boxX;
        int btnY = boxY - PAGE_BTN_HEIGHT - 2;
        Minecraft mc = Minecraft.getInstance();
        boolean wrap = BetterRecipeBook.config.scrolling.scrollAround;
        if (inside(mx, my, bx, btnY, PAGE_BTN_WIDTH, PAGE_BTN_HEIGHT)) {
            int prev = ClientCompat.isControlDown()
                    ? 0
                    : (wrap ? (page - 1 + pageCount) % pageCount : Math.max(0, page - 1));
            if (prev != page) {
                page = prev;
                afterPageFlip();
            }
            return true;
        }
        if (inside(mx, my, bx + 15, btnY, PAGE_BTN_WIDTH, PAGE_BTN_HEIGHT)) {
            int next = ClientCompat.isControlDown()
                    ? pageCount - 1
                    : (wrap ? (page + 1) % pageCount : Math.min(pageCount - 1, page + 1));
            if (next != page) {
                page = next;
                afterPageFlip();
            }
            return true;
        }
        return false;
    }

    // ── tooltip ─────────────────────────────────────────────────────────────
    /** Deferred tooltip pass（after-render 通道最上层绘制）。 */
    public static void renderTooltip(GuiGraphics gui, int mouseX, int mouseY) {
        if (!active) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != hostScreen) {
            close();
            return;
        }
        if (mc.player == null || mc.level == null) return;
        // 弹窗打开时只吞 tooltip（1.21.1 轻量弹窗无槽位命中模型）
        if (popupOpen && inRect(mouseX, mouseY, popupRect)) return;
        // grid 类别
        if (isGridMode() && gridHoverStack != null && !gridHoverStack.isEmpty()) {
            List<Component> lines = new ArrayList<>();
            lines.add(gridHoverStack.getHoverName());
            lines.addAll(gridTooltipLines(
                    gridHoverCategory != null ? gridHoverCategory : currentCategory,
                    gridHoverStack));
            appendModName(lines, gridHoverStack);
            gui.renderComponentTooltip(mc.font, lines, mouseX, mouseY);
            return;
        }
        // 悬停条目（按钮/JEI 格）
        if (hoveredIndex >= 0 && hoveredIndex < entries.size()) {
            renderEntryTooltip(gui, entries.get(hoveredIndex), mouseX, mouseY);
            return;
        }
        // 标签 tooltip（渲染期暂存，保证在最上层）
        if (pendingTabTooltip != null) {
            gui.renderComponentTooltip(mc.font, pendingTabTooltip, pendingTabTooltipX, pendingTabTooltipY);
            pendingTabTooltip = null;
            return;
        }
        // 工作站列 tooltip
        if (pendingStationTooltip != null) {
            gui.renderComponentTooltip(mc.font, pendingStationTooltip, pendingStationTooltipX, pendingStationTooltipY);
            pendingStationTooltip = null;
            return;
        }
        // 翻页按钮 tooltip = 页码
        if (pageCount > 1) {
            int btnY = boxY - PAGE_BTN_HEIGHT - 2;
            if (inside(mouseX, mouseY, boxX, btnY, PAGE_BTN_WIDTH * 2 + 15, PAGE_BTN_HEIGHT)) {
                gui.renderComponentTooltip(mc.font,
                        List.of(Component.literal((page + 1) + "/" + pageCount)), mouseX, mouseY);
            }
        }
    }

    /** 条目 tooltip：结果名 → 熔炼 XP/耗时 → 材料 → 模组名（1.21.1 文本版）。 */
    private static void renderEntryTooltip(GuiGraphics gui, DisplayEntry e, int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getInstance();
        ItemStack result = e.result();
        if (result.isEmpty()) return;
        List<Component> lines = new ArrayList<>();
        lines.add(result.getHoverName());
        if (e.holder() != null) {
            if (e.holder().value() instanceof AbstractCookingRecipe cooking) {
                lines.add(Component.empty());
                float xp = cooking.getExperience();
                String xpText = xp % 1.0f == 0f ? String.valueOf((int) xp)
                        : String.format(Locale.ROOT, "%.2f", xp);
                lines.add(Component.literal(xpText + " XP").withStyle(ChatFormatting.GREEN));
                RecipeType<?> type = cooking.getType();
                String labelKey;
                Style style;
                if (type == RecipeType.BLASTING) {
                    labelKey = "brbe.cooktime.blast";
                    style = Style.EMPTY.withColor(ChatFormatting.GRAY);
                } else if (type == RecipeType.SMOKING) {
                    labelKey = "brbe.cooktime.smoker";
                    style = Style.EMPTY.withColor(0xF5DEB3);
                } else if (type == RecipeType.CAMPFIRE_COOKING) {
                    labelKey = "brbe.cooktime.campfire";
                    style = Style.EMPTY.withColor(0xB5651D);
                } else {
                    labelKey = "brbe.cooktime.furnace";
                    style = Style.EMPTY.withColor(ChatFormatting.RED);
                }
                String value = cookSeconds(cooking.getCookingTime());
                lines.add(Component.translatable(labelKey).withStyle(style)
                        .append(Component.literal("：").withStyle(style))
                        .append(Component.literal(value).withStyle(style)));
            }
            // 材料行（1.21.1 无内嵌预览——材料行是预览信息的文本替代）
            List<ItemStack> inputs = inputsOf(e.holder());
            if (!inputs.isEmpty()) {
                String suffix = inputs.size() > 1 ? " …" : "";
                lines.add(Component.translatable("brbe.viewer.materials")
                        .append(": ")
                        .append(inputs.get(0).getHoverName().copy()
                                .append(Component.literal(suffix))));
            }
        } else if (e.jei() != null && e.jei().inputs() != null && !e.jei().inputs().isEmpty()) {
            List<ItemStack> inputs = e.jei().inputs();
            String suffix = inputs.size() > 1 ? " …" : "";
            lines.add(Component.translatable("brbe.viewer.materials")
                    .append(": ")
                    .append(inputs.get(0).getHoverName().copy()
                            .append(Component.literal(suffix))));
        }
        appendModName(lines, result);
        gui.renderComponentTooltip(mc.font, lines, mouseX, mouseY);
    }

    /** grid 类别信息行：燃料三行烧炼量 / 堆肥概率。 */
    private static List<Component> gridTooltipLines(RecipeViewerCategory cat, ItemStack stack) {
        List<Component> lines = new ArrayList<>();
        if (cat.isFuelCategory()) {
            int burn = RecipeViewerIndex.burnDuration(stack);
            String unit = Component.translatable("brbe.cooktime.unit.items").getString();
            lines.add(Component.empty());
            for (int i = 0; i < 3; i++) {
                String key = i == 0 ? "brbe.cooktime.furnace"
                        : i == 1 ? "brbe.cooktime.blast" : "brbe.cooktime.smoker";
                Style style = i == 0 ? Style.EMPTY.withColor(ChatFormatting.RED)
                        : i == 1 ? Style.EMPTY.withColor(ChatFormatting.GRAY)
                        : Style.EMPTY.withColor(0xF5DEB3);
                String value = fuelCount(burn, 200) + unit;
                lines.add(Component.translatable(key).withStyle(style)
                        .append(Component.literal("：").withStyle(style))
                        .append(Component.literal(value).withStyle(style)));
            }
        } else if (cat instanceof CompostRecipeCategory compost) {
            int percent = (int) Math.floor(compost.chanceOf(stack) * 100);
            lines.add(Component.empty());
            lines.add(Component.translatable("brbe.category.compost.chance", percent)
                    .withStyle(ChatFormatting.GREEN));
        }
        return lines;
    }

    private static void appendModName(List<Component> lines, ItemStack stack) {
        if (!BetterRecipeBook.config.showModName) return;
        Component mod = ModNameUtil.getFormattedModName(stack);
        if (mod != null && !mod.getString().isEmpty()) {
            lines.add(Component.empty());
            lines.add(mod);
        }
    }

    // ── 数据/分页/布局 ──────────────────────────────────────────────────────
    private static List<DisplayEntry> categoryHits(RecipeViewerCategory cat) {
        List<DisplayEntry> hits = new ArrayList<>();
        if (browseAllMode) {
            for (RecipeHolder<?> h : cat.allEntries()) hits.add(DisplayEntry.of(h));
            for (RecipeViewerEngine.JeiEntry j : cat.allJeiEntries()) hits.add(DisplayEntry.of(j));
        } else {
            for (RecipeHolder<?> h : cat.query(queryTarget, queryUsage)) hits.add(DisplayEntry.of(h));
            for (RecipeViewerEngine.JeiEntry j : cat.queryJei(queryTarget, queryUsage)) hits.add(DisplayEntry.of(j));
        }
        // pin 置顶（命中 >1 时才重排）
        if (hits.size() > 1) {
            List<DisplayEntry> pinned = new ArrayList<>();
            List<DisplayEntry> rest = new ArrayList<>();
            for (DisplayEntry e : hits) {
                (e.isPinned() ? pinned : rest).add(e);
            }
            if (!pinned.isEmpty()) {
                hits.clear();
                hits.addAll(pinned);
                hits.addAll(rest);
            }
        }
        return hits;
    }

    private static List<ItemStack> gridSource(RecipeViewerCategory cat) {
        return browseAllMode ? cat.allGridItems() : cat.gridItems(queryTarget, queryUsage);
    }

    /** 重建类别视图：全量排序（pin → 可合成 → 残缺 → 不可合成）+ 第一页。 */
    private static void rebuildWithHits(List<DisplayEntry> hits) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || hits.isEmpty()) return;
        // 全量集合（排序/残缺判定用）
        List<RecipeHolder<?>> holders = new ArrayList<>();
        for (DisplayEntry e : hits) {
            if (e.holder() != null) holders.add(e.holder());
        }
        if (!holders.isEmpty()) {
            RecipeCollection all = new RecipeCollection(mc.level.registryAccess(), holders);
            all.updateKnownRecipes(mc.player.getRecipeBook());
            markViewerPartials(all, mc);
            hits.sort((a, b) -> Integer.compare(recipeRank(all, b), recipeRank(all, a)));
        }
        entries = new ArrayList<>(hits);
        computeBoxSize(entries.size());
        page = 0;
        showPage(hostScreen);
    }

    /** viewer 集合的残缺标记（1.21.11 prepareForViewer 的 1.21.1 等价物）。 */
    private static void markViewerPartials(RecipeCollection collection, Minecraft mc) {
        Set<Item> items = new HashSet<>();
        if (mc.player != null) {
            InventoryAccessor inv = (InventoryAccessor) mc.player.getInventory();
            for (NonNullList<ItemStack> compartment : inv.getCompartments()) {
                for (ItemStack stack : compartment) {
                    if (!stack.isEmpty()) items.add(stack.getItem());
                }
            }
        }
        PartialCraftingUtil.markPartialMaterials(collection, items);
    }

    private static int recipeRank(RecipeCollection collection, DisplayEntry e) {
        if (e.isPinned()) return 3;
        if (e.holder() == null) return 0;
        boolean craftable = collection.isCraftable(e.holder());
        boolean partial = PartialCraftingUtil.isPartiallyCraftable(collection, e.holder());
        if (craftable && !partial) return 2;
        if (partial) return 1;
        return 0;
    }

    /** 框尺寸（全页 258x133）+ 页数；实际收缩在 fitBoxToPage。 */
    private static void computeBoxSize(int total) {
        pageCount = total > PAGE_SIZE ? (total + PAGE_SIZE - 1) / PAGE_SIZE : 1;
        boxW = PAGE_COLS * 25 + 8;
        boxH = PAGE_ROWS * 25 + 8;
        ensureTabWidth();
    }

    private static void computeGridBoxSize() {
        computeBoxSize(gridItems.size());
    }

    /** 按当前页实际对象数收缩框体并重新钳位（1.21.11 fitBoxToPage）。 */
    private static int fitBoxToPage(int count) {
        int columns = Math.max(1, Math.min(PAGE_COLS, count));
        int rows = (count + columns - 1) / columns;
        boxW = columns * 25 + 8;
        boxH = rows * 25 + 8;
        ensureTabWidth();
        boxX = anchorScreenX - 16;
        boxY = anchorScreenY - boxH + 16;
        clampBoxToAnchor();
        clampBoxX();
        avoidCraftingGrid();
        // 规则：每次钳位调整后刷新锚点到实际首对象中心（1.21.11 ESTABLISHED RULE）
        anchorScreenX = boxX + 16;
        anchorScreenY = boxY + boxH - 16;
        bottomAnchor = anchorScreenY + 16;
        return columns;
    }

    private static void fitGridBoxToPage() {
        int start = page * PAGE_SIZE;
        int count = Math.min(start + PAGE_SIZE, gridItems.size()) - start;
        fitBoxToPage(count);
    }

    /** 标签条最多 MAX_TABS 个标签：不足 10 列宽时加空列撑宽框体。 */
    private static void ensureTabWidth() {
        int tabCount = Math.min(visibleCategories().size(), MAX_TABS);
        int tabW = tabCount * TAB_WIDTH + 8;
        if (tabW > boxW) {
            boxW = tabW;
        }
    }

    private static void clampBoxX() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getWindow() == null) return;
        int guiW = mc.getWindow().getGuiScaledWidth();
        if (boxW <= guiW - 50) {
            boxX = Math.max(25, Math.min(boxX, guiW - boxW - 25));
        } else {
            boxX = Math.max(0, Math.min(boxX, guiW - boxW));
        }
    }

    private static void clampBoxToAnchor() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getWindow() == null) return;
        int guiH = mc.getWindow().getGuiScaledHeight();
        int overlayH = boxH + TAB_OVERHANG;
        if (overlayH <= guiH - 50) {
            boxY = Math.max(25, Math.min(bottomAnchor - boxH, guiH - overlayH - 25));
        } else {
            boxY = Math.max(0, Math.min(bottomAnchor - boxH, guiH - overlayH));
        }
    }

    /** 框体覆盖合成网格时下推（1.21.11 avoidCraftingGrid；1.21.1 以
     *  CraftingContainer 槽位扫描定位网格——无 AbstractCraftingMenu）。 */
    private static void avoidCraftingGrid() {
        if (hostScreen == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.getWindow() == null) return;
        int guiH = mc.getWindow().getGuiScaledHeight();
        int gridLeft = Integer.MAX_VALUE;
        int gridRight = -1;
        int gridBottom = -1;
        AbstractContainerScreenAccessor acc = (AbstractContainerScreenAccessor) hostScreen;
        int left = acc.getLeftPos();
        int top = acc.getTopPos();
        for (Slot slot : hostScreen.getMenu().slots) {
            if (slot.container instanceof CraftingContainer) {
                gridLeft = Math.min(gridLeft, left + slot.x);
                gridRight = Math.max(gridRight, left + slot.x + 18);
                gridBottom = Math.max(gridBottom, top + slot.y + 18);
            }
        }
        if (gridLeft == Integer.MAX_VALUE || gridRight < 0 || gridBottom < 0) return;
        if (boxX >= gridRight || boxX + boxW <= gridLeft || boxY >= gridBottom) return;
        boxY = gridBottom;
        int overlayH = boxH + TAB_OVERHANG;
        if (boxY + overlayH > guiH) {
            boxY = Math.max(0, guiH - overlayH);
        }
    }

    private static void repaginateToSelected() {
        List<RecipeViewerCategory> cats = visibleCategories();
        if (currentCategory == null || cats.isEmpty()) {
            tabWindowStart = 0;
            return;
        }
        int maxStart = Math.max(0, cats.size() - MAX_TABS);
        tabWindowStart = Math.max(0, Math.min(tabWindowStart, maxStart));
        int idx = cats.indexOf(currentCategory);
        if (idx < 0) return;
        if (idx < tabWindowStart) {
            tabWindowStart = idx;
        } else if (idx >= tabWindowStart + MAX_TABS) {
            tabWindowStart = Math.min(maxStart, idx - (MAX_TABS - 1));
        }
    }

    /** 布局当前页：页集合 → overlayComponent.init → 按钮按页序重排（行自底向上）。 */
    private static void showPage(AbstractContainerScreen<?> screen) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        int start = page * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, entries.size());
        List<DisplayEntry> pageSlice = new ArrayList<>(entries.subList(start, end));
        List<RecipeHolder<?>> holders = new ArrayList<>();
        for (DisplayEntry e : pageSlice) {
            if (e.holder() != null) holders.add(e.holder());
        }
        if (holders.isEmpty()) {
            currentCollection = null;
            overlayComponent.setVisible(false);
        } else {
            RecipeCollection subset = new RecipeCollection(mc.level.registryAccess(), holders);
            subset.updateKnownRecipes(mc.player.getRecipeBook());
            markViewerPartials(subset, mc);
            overlayComponent.init(mc, subset, boxX + 4, boxY + 4,
                    (int) mc.mouseHandler.xpos(), (int) mc.mouseHandler.ypos(), 25);
            overlayComponent.setVisible(true);
            currentCollection = subset;
        }
        int columns = fitBoxToPage(pageSlice.size());
        pageColumns = columns;
        List<AbstractWidget> buttons = currentCollection == null
                ? List.of()
                : ((OverlayRecipeComponentAccessor) (Object) overlayComponent).getRecipeButtons();
        // holder → 按钮映射，按页序重排（原版 init 按可合成优先排序会打乱 pin 映射）
        java.util.Map<RecipeHolder<?>, AbstractWidget> byHolder = new java.util.HashMap<>();
        for (AbstractWidget w : buttons) {
            RecipeHolder<?> r = ((OverlayRecipeButtonAccessor) w).getRecipe();
            if (r != null) byHolder.put(r, w);
        }
        List<AbstractWidget> ordered = new ArrayList<>(pageSlice.size());
        for (int li = 0; li < pageSlice.size(); li++) {
            DisplayEntry e = pageSlice.get(li);
            AbstractWidget w = e.holder() == null ? null : byHolder.get(e.holder());
            ordered.add(w);
            if (w != null) {
                int col = li % columns;
                int row = li / columns;
                w.setPosition(boxX + 4 + col * 25, boxY + boxH - 28 - row * 25);
            }
        }
        pageEntries = pageSlice;
        pageButtons = ordered;
    }

    private static void rebuildGrid(List<ItemStack> items) {
        gridItems = items;
        gridHoverStack = null;
        gridHoverCategory = null;
        pageEntries = List.of();
        pageButtons = List.of();
        currentCollection = null;
        overlayComponent.setVisible(false);
        computeGridBoxSize();
        page = 0;
        fitGridBoxToPage();
    }

    private static void switchCategory(RecipeViewerCategory category) {
        if (category == null || category == currentCategory) return;
        if (category.isGridCategory()) {
            List<ItemStack> items = gridSource(category);
            if (items.isEmpty()) return;
            currentCategory = category;
            rebuildGrid(items);
        } else {
            List<DisplayEntry> hits = categoryHits(category);
            if (hits.isEmpty()) return;
            currentCategory = category;
            rebuildWithHits(hits);
        }
        clampBoxX();
        repaginateToSelected();
        rebuildStationColumn();
    }

    // ── 浏览全部（Ctrl+O） ──────────────────────────────────────────────────
    private static void toggleBrowseAll() {
        if (!active || hostScreen == null) return;
        if (browseAllMode) {
            browseAllMode = false;
            RecipeViewerCategory saved = browseAllReturnCategory;
            int savedPage = browseAllReturnPage;
            if (saved != null && saved != currentCategory && categoryHasQueryContent(saved)) {
                currentCategory = saved;
            } else if (!categoryHasQueryContent(currentCategory)) {
                RecipeViewerCategory alt = bestContentCategory(queryTarget, queryUsage, currentCategory);
                if (alt != null) currentCategory = alt;
            }
            refreshCurrentCategory();
            page = Math.min(savedPage, Math.max(0, pageCount - 1));
            showPage(hostScreen);
            browseAllReturnCategory = null;
            browseAllReturnPage = 0;
        } else {
            browseAllReturnPage = page;
            browseAllReturnCategory = currentCategory;
            browseAllMode = true;
            page = 0;
            refreshCurrentCategory();
        }
    }

    private static boolean categoryHasQueryContent(RecipeViewerCategory cat) {
        if (cat == null) return false;
        if (cat.isGridCategory()) return !cat.gridItems(queryTarget, queryUsage).isEmpty();
        return cat.hasContent(queryTarget, queryUsage);
    }

    private static void refreshCurrentCategory() {
        if (currentCategory == null || hostScreen == null) return;
        if (currentCategory.isGridCategory()) {
            rebuildGrid(gridSource(currentCategory));
        } else {
            rebuildWithHits(categoryHits(currentCategory));
        }
        clampBoxX();
        repaginateToSelected();
        rebuildStationColumn();
    }

    private static void resetBrowseAllState() {
        browseAllMode = false;
    }

    /** pin 后刷新（pin 置顶排序生效，页码保持）。 */
    private static void refreshAfterPin() {
        if (isGridMode()) return;
        int old = page;
        rebuildWithHits(categoryHits(currentCategory));
        page = Math.min(old, Math.max(0, pageCount - 1));
        showPage(hostScreen);
        clampBoxX();
    }

    // ── Shift 预览弹窗（轻量 PopupRenderer） ────────────────────────────────
    private static void renderShiftPopup(GuiGraphics gui) {
        if (popupAnchorIndex < 0 || popupAnchorIndex >= entries.size()) return;
        DisplayEntry e = entries.get(popupAnchorIndex);
        int li = popupAnchorIndex - page * PAGE_SIZE;
        if (li < 0 || li >= pageEntries.size()) return;
        int[] cell = gridCellFor(li);
        int cx = cell[0] + 12;
        int cy = cell[1] + 12;
        if (e.jei() != null) {
            popupRect = PopupRenderer.renderJeiPopup(gui, e.jei(), cx - 12, cy - 12, 24, 24, 2.0F);
        } else {
            int mode = PopupRenderer.modeFor(currentCategory == null ? null : currentCategory.id());
            boolean craftable = currentCollection != null && currentCollection.isCraftable(e.holder());
            boolean partial = currentCollection != null
                    && PartialCraftingUtil.isPartiallyCraftable(currentCollection, e.holder());
            popupRect = PopupRenderer.renderRecipePopup(gui, e.holder(), mode, craftable, partial,
                    cx - 12, cy - 12, 24, 24, false, 2.0F);
        }
        popupOpen = true;
    }

    private static void drawPinPopup(GuiGraphics gui) {
        if (!pinPopupActive || pinPopupEntry == null) return;
        if (pinPopupEntry.jei() != null) {
            PopupRenderer.renderJeiPopup(gui, pinPopupEntry.jei(),
                    pinPopupX - 12, pinPopupY - 12, 25, 25, 2.0F);
        } else {
            PopupRenderer.renderRecipePopup(gui, pinPopupEntry.holder(),
                    PopupRenderer.modeFor(currentCategory == null ? null : currentCategory.id()),
                    false, false,
                    pinPopupX - 12, pinPopupY - 12, 25, 25, false, 2.0F);
        }
    }

    // ── 辅助 ────────────────────────────────────────────────────────────────
    private static int[] gridCellFor(int li) {
        int col = li % pageColumns;
        int row = li / pageColumns;
        return new int[] { boxX + 4 + col * 25, boxY + boxH - 28 - row * 25 };
    }

    /** 网格格命中（按钮与 JEI 格统一 25x25 几何）。 */
    private static DisplayEntry cellEntryAt(double mx, double my) {
        if (isGridMode() || pageEntries.isEmpty()) return null;
        for (int li = 0; li < pageEntries.size(); li++) {
            int[] cell = gridCellFor(li);
            if (inside(mx, my, cell[0], cell[1], 25, 25)) return pageEntries.get(li);
        }
        return null;
    }

    private static int[] cellOfEntryAt(double mx, double my) {
        if (isGridMode() || pageEntries.isEmpty()) return new int[] { boxX + 4, boxY + boxH - 28 };
        for (int li = 0; li < pageEntries.size(); li++) {
            int[] cell = gridCellFor(li);
            if (inside(mx, my, cell[0], cell[1], 25, 25)) return cell;
        }
        return new int[] { boxX + 4, boxY + boxH - 28 };
    }

    /** 框 + 工作站列裁切面板的命中区（1.21.11 inBox 语义）。 */
    private static boolean inBox(double mx, double my) {
        if (mx >= boxX && mx < boxX + boxW && my >= boxY && my < boxY + boxH) {
            return true;
        }
        if (!stationColumnItems.isEmpty()
                && mx >= panelLeft() && mx < panelLeft() + STATION_COL_WIDTH) {
            int shown = Math.min(stationColumnItems.size(), stationViewRows());
            int[] rect = stationColumnPanelRect(shown);
            return my >= rect[0] && my < rect[0] + rect[1];
        }
        return false;
    }

    /** viewer 自身绘制区（框 + 标签垂出 + 工作站列裁切面板），Ctrl+O 门控用。 */
    private static boolean contains(double mx, double my) {
        if (!active) return false;
        if (mx >= panelLeft() && mx < panelLeft() + STATION_COL_WIDTH) {
            if (stationColumnItems.isEmpty()) return false;
            int shown = Math.min(stationColumnItems.size(), stationViewRows());
            int[] rect = stationColumnPanelRect(shown);
            return my >= rect[0] && my < rect[0] + rect[1];
        }
        return mx >= boxX && mx < boxX + boxW && my >= boxY && my < boxY + boxH + TAB_OVERHANG;
    }

    private static int panelLeft() {
        return boxX - STATION_COL_WIDTH;
    }

    private static boolean inside(double x, double y, int left, int top, int width, int height) {
        return x >= left && x < left + width && y >= top && y < top + height;
    }

    private static boolean inside(int x, int y, int left, int top, int width, int height) {
        return x >= left && x < left + width && y >= top && y < top + height;
    }

    private static boolean inRect(double mx, double my, int[] rect) {
        return rect != null && mx >= rect[0] && mx < rect[0] + rect[2]
                && my >= rect[1] && my < rect[1] + rect[3];
    }

    private static void playButtonClick(Minecraft mc) {
        if (mc.getSoundManager() != null) {
            // 与 AbstractWidget.playButtonClickSound 同音源/音量（1.21.1 无静态版）
            mc.getSoundManager().play(SimpleSoundInstance.forUI(
                    SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, 0.25f));
        }
    }

    private static int mouseXFor() {
        var mc = Minecraft.getInstance();
        return mc.mouseHandler != null ? (int) mc.mouseHandler.xpos() : 0;
    }

    private static int mouseYFor() {
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

    private static List<ItemStack> inputsOf(RecipeHolder<?> holder) {
        List<ItemStack> inputs = new ArrayList<>();
        for (var ingredient : holder.value().getIngredients()) {
            ItemStack[] stacks = ingredient.getItems();
            if (stacks.length > 0) inputs.add(stacks[0]);
        }
        return inputs;
    }

    /** 熔炼耗时（秒，整秒不带小数）。 */
    private static String cookSeconds(int ticks) {
        String value = ticks % 20 == 0 ? String.valueOf(ticks / 20)
                : String.format(Locale.ROOT, "%.1f", ticks / 20.0f);
        return value + "s";
    }

    /** 燃料可烧炼件数（标准 200 tick 一件；整件不带小数）。 */
    private static String fuelCount(int burn, int cookTime) {
        if (burn <= 0 || cookTime <= 0) return "0";
        return burn % cookTime == 0 ? String.valueOf(burn / cookTime)
                : String.format(Locale.ROOT, "%.1f", burn / (float) cookTime);
    }
}
