package com.alonie.brbe.util;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.cache.RecipeViewerIndex;
import com.alonie.brbe.mixins.accessors.AbstractContainerScreenAccessor;
import com.alonie.brbe.mixins.accessors.InventoryAccessor;
import com.alonie.brbe.mixins.accessors.OverlayRecipeButtonAccessor;
import com.alonie.brbe.mixins.accessors.OverlayRecipeComponentAccessor;
import com.alonie.brbe.mixins.accessors.RecipeBookComponentAccessor;
import com.alonie.brbe.mixins.accessors.RecipeBookPageAccessor;
import com.alonie.brbe.mixins.accessors.GhostRecipeAccessor;
import com.alonie.brbe.recipeviewer.CompostRecipeCategory;
import com.alonie.brbe.recipeviewer.RecipeViewerCategories;
import com.alonie.brbe.recipeviewer.RecipeViewerCategory;
import com.alonie.brbe.recipeviewer.engine.RecipeViewerEngine;
import com.alonie.brbe.recipeviewer.PluginRecipeViewerCategory;
import com.alonie.brbe.render.PopupRenderer;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.util.Mth;
import net.minecraft.client.gui.screens.recipebook.GhostRecipe;
import net.minecraft.client.gui.screens.recipebook.RecipeUpdateListener;
import net.minecraft.client.gui.screens.recipebook.OverlayRecipeComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeButton;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.RecipeBookMenu;
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

    // ── 捕获态（1.21.11 captureTarget 链） ────────────────────────────────────
    /** 捕获到 viewer overlay 按钮（R/U 从替代 overlay 按钮发起）。 */
    private static AbstractWidget anchorOverlayWidget;
    /** 捕获到配方书页码按钮。 */
    private static RecipeButton anchorBookButton;

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
        /** 条目身份键（阶段二 B：holder→idFor(holder)，JEI→idForJei(typeUid)）。 */
        RecipeViewerEngine.RecipeDisplayId id() {
            if (holder != null) return RecipeViewerEngine.idFor(holder);
            if (jei != null) return RecipeViewerEngine.idForJei(jei.typeUid());
            return null;
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
            // 1.21.11 语义：BRBE 引擎无命中 → 回退外部 viewer（JEI/REI）；
            // hide 开启时抑制回退（BRBE 无法判定的对象不泄漏给外部 viewer）。
            if (BetterRecipeBook.config.hideNoRecipeBookStationObjects) {
                return false;
            }
            return fallbackToViewer(target, usage);
        }
        // 实际内容判定（不信任 hasContent 的"声称"）：grid 类别看 grid 列表，
        // 配方类别看合并命中——类别声称有内容而实际为空时（旧日志"cat=fuel
        // empty content"类故障）换"实际有内容的最高优先级类别"，再没有才拒绝。
        // 先落 queryTarget/queryUsage：hasActualContent 走 gridSource/categoryHits，
        // 二者读字段而非参数。
        resetBrowseAllState();
        queryTarget = target;
        queryUsage = usage;
        currentCategory = cat;
        if (!hasActualContent(cat)) {
            RecipeViewerCategory alt = bestContentCategory(target, usage, cat);
            if (alt == null) {
                BetterRecipeBook.LOGGER.info("[BRBE-VIEWER] open refused: empty content cat={} item={} usage={}",
                        cat.id(), target.getHoverName().getString(), usage);
                if (BetterRecipeBook.config.hideNoRecipeBookStationObjects) {
                    return false;
                }
                return fallbackToViewer(target, usage);
            }
            cat = alt;
            currentCategory = cat;
        }
        hostScreen = screen;
        // 锚点 = 光标快照（限制在窗口内）。bottomAnchor 必须在此初始化
        // （1.21.11 openFor 同款）：首个 fitBoxToPage 的 clampBoxToAnchor 依赖它
        // ——漏初始化则首开框体被钳死在 Y=25、重开时用旧会话锚点。
        anchorScreenX = mouseXFor();
        anchorScreenY = mouseYFor();
        bottomAnchor = anchorScreenY + 16;
        if (cat.isGridCategory()) {
            rebuildGrid(gridSource(cat));
        } else {
            rebuildWithHits(categoryHits(cat));
        }
        repaginateToSelected();
        rebuildStationColumn();
        active = true;
        viewerZ = com.alonie.brbe.pinoverlay.PinOverlayManager.nextZ();
        com.alonie.brbe.cache.RecipeViewerIndex.setViewerActive(true);
        com.alonie.brbe.cache.RecipeViewerIndex.setViewerOpenedFromBook(anchorBookButton != null);
        BetterRecipeBook.LOGGER.info("[BRBE-VIEWER] opened cat={} entries={} pages={} item={} usage={}",
                cat.id(), entries.size(), pageCount, target.getHoverName().getString(), usage);
        return true;
    }

    /** 类别是否"实际"有可显示内容（grid 列表 / 合并命中非空）——openFor 与
     *  最佳类别重选的唯一判据，不信任 hasContent 的声称。 */
    private static boolean hasActualContent(RecipeViewerCategory category) {
        if (category == null) return false;
        if (category.isGridCategory()) {
            return !gridSource(category).isEmpty();
        }
        return !categoryHits(category).isEmpty();
    }

    /** 实际有内容且优先级最高的类别（排除 {@code exclude}）——1.21.11 语义。 */
    private static RecipeViewerCategory bestContentCategory(ItemStack target, boolean usage,
                                                            RecipeViewerCategory exclude) {
        RecipeViewerCategory best = null;
        int bestPriority = -1;
        for (RecipeViewerCategory category : RecipeViewerCategories.all()) {
            if (category == exclude) continue;
            int priority = category.defaultPriority(target);
            if (priority <= bestPriority) continue;
            if (hasActualContent(category)) {
                best = category;
                bestPriority = priority;
            }
        }
        return best;
    }

    /** Dismiss the viewer: clear state before hiding so no guard cancels this
     *  sanctioned close. */
    public static void close() {
        // 先清 viewerActive 再 setVisible(false)（OverlayRecipeComponentMixin
        // 守卫取消非授权关闭——合规路径必须先清标志，1.21.11 闭环）
        com.alonie.brbe.cache.RecipeViewerIndex.setViewerActive(false);
        com.alonie.brbe.cache.RecipeViewerIndex.clearViewerPartials(currentCollection);
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

    // ── 键输入（1.21.11 语义：captureTarget 捕获 + R/U/A/ESC/O） ─────────────
    public static boolean keyPressed(int keyCode, int scanCode, int modifiers,
                                     AbstractContainerScreen<?> screen) {
        // JVM 盾：查询功能整体屏蔽（brbe.disableRecipeViewer=true，默认）——
        // R/U 打不开、不渲染。A 键 pin 走 PinOverlayManager（独立于 viewer），
        // 仍可用；配方书 pin 走 mixins/pins，更不受影响。
        if (com.alonie.brbe.config.RecipeViewerFeatureFlag.isDisabled()) return false;

        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != screen) return false;

        // ESC：只关最顶层（pin 打开 → 只关 pin？1.21.11 语义：ESC 只关 viewer，
        // pin 永不因 ESC 关闭）——pin 由 PinOverlayManager.handleEscape 处理。
        if (keyCode == 256) {
            return com.alonie.brbe.pinoverlay.PinOverlayManager.handleEscape();
        }

        if (active) {
            // Ctrl+O：浏览全部（仅光标在查询界面内时生效，1.21.11 语义）
            if (keyCode == InputConstants.KEY_O) {
                int mx = mouseXFor();
                int my = mouseYFor();
                if (contains(mx, my) || previewOwnsCursor(mx, my)) {
                    toggleBrowseAll();
                    return true;
                }
            }
            if (ClientCompat.matches(BetterRecipeBook.RECIPE_VIEW_MAPPING, keyCode, scanCode, modifiers)) {
                reopen(screen, false);
                return true;
            }
            if (ClientCompat.matches(BetterRecipeBook.USAGE_VIEW_MAPPING, keyCode, scanCode, modifiers)) {
                reopen(screen, true);
                return true;
            }
            return false;
        }

        if (!BetterRecipeBook.config.recipeViewerEnabled) return false;
        boolean viewRecipe = ClientCompat.matches(BetterRecipeBook.RECIPE_VIEW_MAPPING,
                keyCode, scanCode, modifiers);
        boolean viewUsage = ClientCompat.matches(BetterRecipeBook.USAGE_VIEW_MAPPING,
                keyCode, scanCode, modifiers);
        // A 键：pin 创建/移除（未激活 viewer 时——激活时由 pin 层管理）
        if (ClientCompat.matchesPinKey(keyCode, scanCode, modifiers)) {
            return com.alonie.brbe.pinoverlay.PinOverlayManager.handleKeyPressed(
                    keyCode, scanCode, modifiers, screen);
        }
        if (!viewRecipe && !viewUsage) return false;

        ItemStack target = captureTarget(screen);
        if (target.isEmpty()) return false;
        return openFor(screen, target, viewUsage);
    }

    /** 捕获查询目标（1.21.11 captureTarget 链，7 级优先级适配 1.21.1）：
     *  ① 弹窗内槽位物品 ② 弹窗配方结果 ③ viewer overlay 悬停按钮 ④ 槽位
     *  ⑤ ghost 预览槽 ⑥ 配方书页码按钮 ⑦ grid 悬停 / 站列。全部落空 → EMPTY。 */
    public static ItemStack captureTarget(AbstractContainerScreen<?> screen) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return ItemStack.EMPTY;
        anchorOverlayWidget = null;
        anchorBookButton = null;
        // ③ viewer overlay 悬停按钮（优先于槽位——viewer 打开时不悬停槽位）
        if (active && !isGridMode()) {
            int mx = mouseXFor();
            int my = mouseYFor();
            // ① 弹窗槽位
            if (popupOpen && popupRect != null && inRect(mx, my, popupRect)
                    && popupAnchorIndex >= 0 && popupAnchorIndex < entries.size()) {
                DisplayEntry e = entries.get(popupAnchorIndex);
                if (e.result() != null && !e.result().isEmpty()) {
                    return e.result();
                }
            }
            // ③ 悬停按钮结果
            DisplayEntry hovered = cellEntryAt(mx, my);
            if (hovered != null) {
                ItemStack result = hovered.result();
                if (result != null && !result.isEmpty()) return result;
            }
        }
        // ④ 槽位
        AbstractContainerScreenAccessor acc = (AbstractContainerScreenAccessor) screen;
        Slot hoveredSlot = acc.brbe$getHoveredSlot();
        if (hoveredSlot != null && hoveredSlot.hasItem()) {
            return hoveredSlot.getItem();
        }
        // ⑤ ghost 预览槽（RecipeUpdateListener 屏的配方书 ghost）
        ItemStack ghost = captureGhostItem(screen, hoveredSlot, mc);
        if (!ghost.isEmpty()) return ghost;
        // ⑥ 配方书页码按钮
        if (screen instanceof RecipeUpdateListener rul) {
            RecipeBookPageAccessor pageAcc =
                    (RecipeBookPageAccessor) ((RecipeBookComponentAccessor) rul.getRecipeBookComponent())
                            .getRecipeBookPage();
            if (pageAcc != null) {
                int mx = mouseXFor();
                int my = mouseYFor();
                for (RecipeButton button : pageAcc.getButtons()) {
                    if (button != null && button.isMouseOver(mx, my)) {
                        anchorBookButton = button;
                        ItemStack result = recipeResultOf(button);
                        if (!result.isEmpty()) return result;
                    }
                }
            }
        }
        // ⑦ grid 悬停 / 站列
        if (isGridMode() && gridHoverStack != null && !gridHoverStack.isEmpty()) {
            return gridHoverStack;
        }
        ItemStack station = stationCellAt(mouseXFor(), mouseYFor());
        if (!station.isEmpty()) {
            return station;
        }
        return ItemStack.EMPTY;
    }

    private static ItemStack recipeResultOf(RecipeButton button) {
        try {
            RecipeHolder<?> holder = button.getRecipe();
            if (holder == null) return ItemStack.EMPTY;
            return holder.value().getResultItem(Minecraft.getInstance().level.registryAccess());
        } catch (Exception e) {
            return ItemStack.EMPTY;
        }
    }

    /** ⑤ ghost 预览槽位物品（1.21.1 GhostRecipe 公开 API 读取；无法解析 → EMPTY）。 */
    private static ItemStack captureGhostItem(AbstractContainerScreen<?> screen,
                                              Slot hoveredSlot, Minecraft mc) {
        try {
            if (screen instanceof RecipeUpdateListener rul && hoveredSlot != null) {
                GhostRecipe ghost = ((RecipeBookComponentAccessor) rul.getRecipeBookComponent())
                        .getGhostRecipe();
                if (ghost != null && ghost.getRecipe() != null) {
                    List<GhostRecipe.GhostIngredient> ingredients =
                            ((GhostRecipeAccessor) ghost).getIngredients();
                    if (ingredients != null && !ingredients.isEmpty()) {
                        // 任一 ghost 材料物品即可作为查询目标（无配方书屏则不适用）
                        ItemStack item = ingredients.get(0).getItem();
                        if (item != null && !item.isEmpty()) return item;
                    }
                }
            }
        } catch (Exception e) {
            // broken ghost — fall through
        }
        return ItemStack.EMPTY;
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

        // Shift 预览弹窗 = 硬模态：弹窗内左键放置配方（阶段一 #3，1.21.11
        // 语义——popup click 继承按钮完整点击）+ 音效，弹窗外点击吞掉。
        if (popupOpen) {
            if (button == 0 && inRect(mouseX, mouseY, popupRect)
                    && popupAnchorIndex >= 0 && popupAnchorIndex < entries.size()) {
                DisplayEntry e = entries.get(popupAnchorIndex);
                if (e.holder() != null) {
                    placeRecipe(mouseX, mouseY, button, screen, e.holder());
                } else if (e.jei() != null) {
                    playButtonClick(mc);
                }
            }
            return true;
        }
        // 左侧工作站列点击：重新查询该工作站（优先于框背景吞点击）
        if (handleStationColumnClick(mouseX, mouseY, button)) {
            return true;
        }
        // 框内点击：配方按钮 = 放置（guarded by station match——crafting 配方
        // 在熔炉屏内点击不填格，只吞并回音）；框背景仅吞。
        if (inBox(mouseX, mouseY)) {
            if (button == 0) {
                DisplayEntry clicked = cellEntryAt(mouseX, mouseY);
                if (clicked != null) {
                    if (clicked.holder() != null) {
                        placeRecipe(mouseX, mouseY, button, screen, clicked.holder());
                    } else {
                        // JEI 条目无 RecipeHolder——只给按钮音反馈（无放置）
                        playButtonClick(mc);
                    }
                }
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
        // pin 在光标下：吞掉滚轮（防翻 viewer 页）
        if (com.alonie.brbe.pinoverlay.PinOverlayManager.handleMouseScrolled(mouseX, mouseY, vertical)) {
            return true;
        }
        // Alt+滚轮：步进轮循变体（最高优先，1.21.11 语义）
        if (vertical != 0 && isCycleAltDown() && active) {
            stepCycledVariants(vertical);
            return true;
        }
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
        if (com.alonie.brbe.config.RecipeViewerFeatureFlag.isDisabled()) return;
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
                    hoveredEntryInternal(mouseX, mouseY);
                }
            } else {
                // JEI 条目（无 RecipeHolder）：plain_overlay 格子 + 结果图标
                // + 前几项输入材料（阶段一 #2：对齐 1.21.11 按钮显示材料而非
                // 只有结果）。单元格 24px：结果居中，材料以 0.6 缩放排布在其
                // 下方两格（最多 3 项，超出省略）。
                boolean hovered = inside(mouseX, mouseY, cell[0], cell[1], 25, 25);
                gui.blitSprite(hovered ? PLAIN_OVERLAY_HIGHLIGHTED : PLAIN_OVERLAY,
                        cell[0], cell[1], 24, 24);
                gui.renderItem(entry.result(), cell[0] + 4, cell[1] + 4);
                List<ItemStack> matInputs = entry.jei() != null && entry.jei().inputs() != null
                        ? entry.jei().inputs() : List.of();
                for (int mi = 0; mi < Math.min(3, matInputs.size()); mi++) {
                    ItemStack mat = matInputs.get(mi);
                    if (mat == null || mat.isEmpty()) continue;
                    renderScaledCellItem(gui, mat, cell[0] + 6 + mi * 6, cell[1] + 15);
                }
                if (hovered) {
                    hoveredIndex = page * PAGE_SIZE + li;
                    hoveredEntryInternal(mouseX, mouseY);
                }
            }
        }
        // 悬停按钮重绘（无 2x 放大——阶段一 #4：对齐 1.21.11 "viewer 内悬停只换
        // _highlighted sprite，不放大；放大只属 Shift 预览"。vanilla 按钮悬停
        // 自绘高亮 sprite，这里仅把它最后画一遍盖住邻居，保证高亮在最上）。
        if (hoveredButton != null && !shift) {
            hoveredButton.render(gui, mouseX, mouseY, delta);
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
                // renderItem 以 z=150 绘制熔炉图标；火焰用 blitSprite(z=0) 会落在其下
                // （GUI 深度测试，实物图标盖住火焰）。blitSprite 6 参重载带 z——抬高到
                // 图标之上（150 + 余量），火焰盖在熔炉面上（1.21.11 nextStratum 语义）。
                gui.blitSprite(BRBTextures.FURNACE_FIRE_SPRITE, iconX + 10, iconY + 10, 160, 6, 6);
            }
            if (inside(mouseX, mouseY, x, tabY, TAB_WIDTH, TAB_HEIGHT)) {
                List<Component> lines = new ArrayList<>();
                lines.add(cat.name());
                appendModName(lines, cat.icon());
                // 阶段一 #7：标签窗可滑动时附 ◀▶ 标记（1.21.11 滑窗标记的信息
                // 语义：窗口未至最左最右 → ◀▶；最左/最右分别 → ▶/◀）。
                if (tabWindowCount() > MAX_TABS) {
                    boolean canLeft = tabWindowStart > 0;
                    boolean canRight = tabWindowStart + MAX_TABS < tabWindowCount();
                    if (canLeft && canRight) {
                        lines.add(Component.literal("◀ ▶"));
                    } else if (canLeft) {
                        lines.add(Component.literal("◀"));
                    } else if (canRight) {
                        lines.add(Component.literal("▶"));
                    }
                }
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
     /** 标签窗内可见类别数（tab 滑动标记 ▲▶ 判定用）。 */
    private static int tabWindowCount() {
        return visibleCategories().size();
    }

    private static List<RecipeViewerCategory> visibleCategories() {
        if (queryTarget == null || queryTarget.isEmpty()) return List.of();
        Set<String> hidden = hiddenCategoryIds();
        List<RecipeViewerCategory> out = new ArrayList<>();
        for (RecipeViewerCategory cat : RecipeViewerCategories.all()) {
            if (BetterRecipeBook.config.hideNoRecipeBookStationObjects
                    && hidden.contains(cat.id())) {
                continue;
            }
            // 站类别连接被切（非法站 + hide 开）不显示 tab（grid 类别豁免）
            if (BetterRecipeBook.config.hideNoRecipeBookStationObjects
                    && !cat.isGridCategory()
                    && cat.appliesToStation(queryTarget)
                    && !RecipeViewerEngine.isRecipeBookStation(queryTarget)) {
                continue;
            }
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
        // 阶段一 #5：插件/mod 类别用其注册的工作站列表（plugin.stations()），
        // 否则 stationColumnItemsFor 对插件类别 id 返回空 → mod 类别站列空。
        if (currentCategory instanceof com.alonie.brbe.recipeviewer.PluginRecipeViewerCategory plugin) {
            List<ItemStack> stations = plugin.stations();
            stationColumnItems = stations == null ? List.of() : stations;
        } else {
            stationColumnItems = RecipeViewerIndex.stationColumnItemsFor(
                    currentCategory == null ? "" : currentCategory.id());
        }
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
        if (com.alonie.brbe.config.RecipeViewerFeatureFlag.isDisabled()) return;
        if (!active) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != hostScreen) {
            close();
            return;
        }
        if (mc.player == null || mc.level == null) return;
        // 弹窗打开：光标在弹窗内 → 槽位 tooltip（阶段一 #3，1.21.11 语义）；
        // 无槽位命中（背景）则只吞 tooltip。
        if (popupOpen && inRect(mouseX, mouseY, popupRect)) {
            ItemStack slotStack = popupSlotStack(mouseX, mouseY);
            if (!slotStack.isEmpty()) {
                List<Component> slotLines = new ArrayList<>(Screen.getTooltipFromItem(mc, slotStack));
                Component slotMod = ModNameUtil.getFormattedModName(slotStack);
                if (slotMod != null && !slotMod.getString().isEmpty()) {
                    slotLines.add(Component.empty());
                    slotLines.add(slotMod);
                }
                gui.renderComponentTooltip(mc.font, slotLines, mouseX, mouseY);
            }
            return;
        }
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
            renderEntryTooltipRich(gui, entries.get(hoveredIndex), mouseX, mouseY);
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
        } else if (cat instanceof com.alonie.brbe.recipeviewer.InfoRecipeCategory info) {
            // 阶段一 #6：info 类别网格 tooltip 显示 JEI 信息文案行（1.21.11 语义）。
            java.util.List<net.minecraft.network.chat.FormattedText> descriptions =
                    info.descriptionFor(stack);
            if (!descriptions.isEmpty()) {
                lines.add(Component.empty());
                for (net.minecraft.network.chat.FormattedText text : descriptions) {
                    lines.add(net.minecraft.network.chat.Component.literal(
                            text.getString()));
                }
            }
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
        // hideNoRecipeBookStationObjects 对象级过滤（1.21.11 链）
        if (BetterRecipeBook.config.hideNoRecipeBookStationObjects) {
            hits = filterByRecipeBookStations(hits, cat);
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
        // 仅当玩家菜单是 RecipeBookMenu 时才能用 vanilla OverlayRecipeComponent：
        // 其 init 会把 containerMenu 强转成 RecipeBookMenu，创造模式 ItemPickerMenu
        // 等非配方书菜单会抛 ClassCastException（2026-08-29 用户实测崩溃）。
        boolean recipeBookMenu = mc.player.containerMenu instanceof RecipeBookMenu;
        if (holders.isEmpty()) {
            currentCollection = null;
            overlayComponent.setVisible(false);
        } else {
            RecipeCollection subset = new RecipeCollection(mc.level.registryAccess(), holders);
            subset.updateKnownRecipes(mc.player.getRecipeBook());
            markViewerPartials(subset, mc);
            if (recipeBookMenu) {
                overlayComponent.init(mc, subset, boxX + 4, boxY + 4,
                        (int) mc.mouseHandler.xpos(), (int) mc.mouseHandler.ypos(), 25);
            }
            overlayComponent.setVisible(true);
            currentCollection = subset;
        }
        int columns = fitBoxToPage(pageSlice.size());
        pageColumns = columns;
        // 非配方书菜单不调用 init → 无 vanilla 按钮，全部走 render 的 plain 图标兜底
        // （pageButtons 置 null，render 里 w==null 分支画 OVERLAY 格子+结果图标）。
        List<AbstractWidget> buttons = (currentCollection == null || !recipeBookMenu)
                ? List.of()
                : ((OverlayRecipeComponentAccessor) (Object) overlayComponent).getRecipeButtons();
        // holder → 按钮映射，按页序重排（原版 init 按可合成优先排序会打乱 pin 映射）
        java.util.Map<RecipeHolder<?>, AbstractWidget> byHolder = new java.util.HashMap<>();
        for (AbstractWidget w : buttons) {
            if (w == null) continue;
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
        // hide 开关的站类别切连：非法站不浮出其配方（grid 类别豁免）
        if (!browseAllMode && BetterRecipeBook.config.hideNoRecipeBookStationObjects
                && queryUsage && queryTarget != null && !queryTarget.isEmpty()
                && category.appliesToStation(queryTarget)
                && !RecipeViewerEngine.isRecipeBookStation(queryTarget)) {
            return;
        }
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
            // 页面恢复仅配方类别执行：grid 类别的 entries 恒为空（rebuildGrid 不填），
            // 对其调 showPage 会让 fitBoxToPage(0) 把框体塌缩（1.21.11 同款分支结构）。
            if (!currentCategory.isGridCategory()) {
                page = Math.min(savedPage, Math.max(0, pageCount - 1));
                showPage(hostScreen);
            }
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
            // 1:1 完整 JEI 界面优先（headless-jei 有布局时）；失败回退固定布局
            int[] rect = PopupRenderer.renderJeiPopup1to1(gui, e.jei(), cx - 12, cy - 12, 24, 24);
            popupRect = rect != null ? rect
                    : PopupRenderer.renderJeiPopup(gui, e.jei(), cx - 12, cy - 12, 24, 24, 2.0F);
        } else {
            // 阶段二 B P3：holder 条目挂接 JEI 原生布局（切石/锻造）→ 1:1 完整
            // JEI UI 委托（与 1.21.11 attachVanillaLayouts 后 canRender 同语义）。
            RecipeViewerEngine.JeiEntry attached =
                    com.alonie.brbe.cache.BrbeJeiBridge.attachedJeiEntry(e.id());
            if (attached != null) {
                int mode = PopupRenderer.modeFor(currentCategory == null ? null : currentCategory.id());
                boolean partial = currentCollection != null
                        && PartialCraftingUtil.isPartiallyCraftable(currentCollection, e.holder());
                int[] rect = PopupRenderer.renderJeiPopup1to1(gui, attached, cx - 12, cy - 12, 24, 24);
                popupRect = rect != null ? rect
                        : PopupRenderer.renderJeiPopup(gui, attached, cx - 12, cy - 12, 24, 24, 2.0F);
                // 非 crafting 模式（切石/锻造）委托的 JEI UI 无自带背景，残缺
                // 配方补红罩（1.21.11 同语义——仅非 crafting）。
                if (partial && mode != PopupRenderer.MODE_CRAFTING && popupRect != null) {
                    gui.fill(popupRect[0], popupRect[1],
                            popupRect[0] + popupRect[2], popupRect[1] + popupRect[3], 0x60FF3333);
                }
            } else {
                int mode = PopupRenderer.modeFor(currentCategory == null ? null : currentCategory.id());
                boolean craftable = currentCollection != null && currentCollection.isCraftable(e.holder());
                boolean partial = currentCollection != null
                        && PartialCraftingUtil.isPartiallyCraftable(currentCollection, e.holder());
                popupRect = PopupRenderer.renderRecipePopup(gui, e.holder(), mode, craftable, partial,
                        cx - 12, cy - 12, 24, 24, false, 2.0F);
            }
        }
        popupOpen = true;
    }

    private static void drawPinPopup(GuiGraphics gui) {
        if (!pinPopupActive || pinPopupEntry == null) return;
        if (pinPopupEntry.jei() != null) {
            PopupRenderer.renderJeiPopup(gui, pinPopupEntry.jei(),
                    pinPopupX - 12, pinPopupY - 12, 25, 25, 2.0F);
        } else {
            // 阶段二 B P3：holder 条目带 JEI 布局（切石/锻造）→ 完整 JEI UI。
            RecipeViewerEngine.JeiEntry attached =
                    com.alonie.brbe.cache.BrbeJeiBridge.attachedJeiEntry(pinPopupEntry.id());
            if (attached != null) {
                int[] rect = PopupRenderer.renderJeiPopup1to1(gui, attached,
                        pinPopupX - 12, pinPopupY - 12, 25, 25);
                if (rect == null) {
                    PopupRenderer.renderJeiPopup(gui, attached,
                            pinPopupX - 12, pinPopupY - 12, 25, 25, 2.0F);
                }
            } else {
                PopupRenderer.renderRecipePopup(gui, pinPopupEntry.holder(),
                        PopupRenderer.modeFor(currentCategory == null ? null : currentCategory.id()),
                        false, false,
                        pinPopupX - 12, pinPopupY - 12, 25, 25, false, 2.0F);
            }
        }
    }

    // ── 辅助 ────────────────────────────────────────────────────────────────
    /** 0.6 缩放绘制一个小材料图标（JEI 按钮材料行用；translate 即左上角）。 */
    private static void renderScaledCellItem(GuiGraphics gui, ItemStack stack, int tx, int ty) {
        if (stack == null || stack.isEmpty()) return;
        gui.pose().pushPose();
        gui.pose().translate(tx, ty, 0);
        gui.pose().scale(0.6f, 0.6f, 1.0F);
        gui.renderItem(stack, 0, 0);
        gui.pose().popPose();
    }

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
    public static boolean contains(double mx, double my) {
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

    /** GUI 缩放后的鼠标 X（1.21.1 无 getScaledXPos——按 vanilla 同款换算
     *  xpos * guiScaledWidth / screenWidth；直接取原始窗口坐标会在 guiScale>1
     *  时锚点/命中判定全错位）。 */
    public static int mouseXFor() {
        var mc = Minecraft.getInstance();
        if (mc.mouseHandler == null || mc.getWindow() == null) return 0;
        double x = mc.mouseHandler.xpos();
        int guiW = mc.getWindow().getGuiScaledWidth();
        int winW = mc.getWindow().getScreenWidth();
        return winW <= 0 ? (int) x : (int) (x * guiW / winW);
    }

    /** GUI 缩放后的鼠标 Y（同上换算）。 */
    public static int mouseYFor() {
        var mc = Minecraft.getInstance();
        if (mc.mouseHandler == null || mc.getWindow() == null) return 0;
        double y = mc.mouseHandler.ypos();
        int guiH = mc.getWindow().getGuiScaledHeight();
        int winH = mc.getWindow().getScreenHeight();
        return winH <= 0 ? (int) y : (int) (y * guiH / winH);
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

    /** 打开的容器菜单是否为 {@code menuClass}（熔炉 tooltip 白点标记用）。 */
    private static boolean menuIs(Class<?> menuClass) {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && menuClass.isInstance(mc.player.containerMenu);
    }

    /** 第 {@code i} 个烧炼子站（0=furnace 1=blast 2=smoker 3=campfire）标签键。 */
    private static String furnaceStationLabel(int i) {
        return switch (i) {
            case 0 -> "brbe.cooktime.furnace";
            case 1 -> "brbe.cooktime.blast";
            case 2 -> "brbe.cooktime.smoker";
            case 3 -> "brbe.cooktime.campfire";
            default -> "brbe.cooktime.furnace";
        };
    }

    /** 第 {@code i} 个烧炼子站的 recipe-book 前缀（工作站图标查询用）。 */
    private static String furnaceStationPrefix(int i) {
        return switch (i) {
            case 0 -> "furnace_";
            case 1 -> "blast_furnace_";
            case 2 -> "smoker_";
            case 3 -> "campfire";
            default -> "furnace_";
        };
    }

    /** 第 {@code i} 个烧炼子站行颜色（1.21.11 同表）。 */
    private static Style stationStyle(int i) {
        return switch (i) {
            case 0 -> Style.EMPTY.withColor(ChatFormatting.RED);
            case 1 -> Style.EMPTY.withColor(ChatFormatting.GRAY);
            case 2 -> Style.EMPTY.withColor(0xF5DEB3);
            case 3 -> Style.EMPTY.withColor(0xB5651D);
            default -> Style.EMPTY;
        };
    }

    private static boolean stationMatches(int i, boolean furnace, boolean blast, boolean smoker) {
        return switch (i) {
            case 0 -> furnace;
            case 1 -> blast;
            case 2 -> smoker;
            default -> false;
        };
    }

    /** 一行标注的烧炼耗时：当前站配白点 "•" + 标签 + 冒号 + 值（同色）。 */
    private static Component stationTimeLine(String labelKey, String value,
                                             Style valueStyle, boolean currentStation) {
        net.minecraft.network.chat.MutableComponent line = Component.literal("");
        if (currentStation) {
            line.append(Component.literal("•").withStyle(ChatFormatting.WHITE));
        }
        line.append(Component.translatable(labelKey).withStyle(valueStyle));
        line.append(Component.literal("：").withStyle(valueStyle));
        line.append(Component.literal(value).withStyle(valueStyle));
        return line;
    }

    /** 燃料可烧炼件数（标准 200 tick 一件；整件不带小数）。 */
    private static String fuelCount(int burn, int cookTime) {
        if (burn <= 0 || cookTime <= 0) return "0";
        return burn % cookTime == 0 ? String.valueOf(burn / cookTime)
                : String.format(Locale.ROOT, "%.1f", burn / (float) cookTime);
    }

    // ══ 1.21.11 移植补充（2026-08-29） ═══════════════════════════════════════

    // ── viewer 激活状态（索引层权威标志，mixin 守卫共用） ─────────────────────

    private static int viewerZ = -1;

    public static int viewerZ() {
        return viewerZ;
    }

    /** Whether the overlay instance is the standalone viewer's own. */
    public static boolean isOwnOverlay(OverlayRecipeComponent o) {
        return o == overlayComponent;
    }

    public static boolean isPaged() {
        return active && pageCount > 1;
    }

    // ── 查询兜底 ─────────────────────────────────────────────────────────────

    /** BRBE's engine found nothing for this item: route to the active recipe
     *  viewer (JEI/REI) so mod recipes still open. */
    private static boolean fallbackToViewer(ItemStack target, boolean viewUsage) {
        if (!com.alonie.brbe.compat.ItemViewCompat.isLoaded()) return false;
        return viewUsage
                ? com.alonie.brbe.compat.ItemViewCompat.openUsageView(target)
                : com.alonie.brbe.compat.ItemViewCompat.openRecipeView(target);
    }

    // ── 光标所有权（创造屏标签悬停抑制） ─────────────────────────────────────

    /** Whether the open popup owns the cursor. */
    public static boolean previewOwnsCursor(int mx, int my) {
        return popupOpen && popupRect != null && inRect(mx, my, popupRect);
    }

    /** Whether the query UI (viewer box / popup) or a pin owns the cursor —
     *  the creative tab under it must not hover (no tooltip, no hand cursor). */
    public static boolean modalMaskOwnsCursor(int mx, int my) {
        if (com.alonie.brbe.pinoverlay.PinOverlayManager.covers(mx, my)) return true;
        return active && (contains(mx, my) || previewOwnsCursor(mx, my));
    }

    // ── 屏幕关闭时清理 ───────────────────────────────────────────────────────

    public static void onScreenClosed(AbstractContainerScreen<?> screen) {
        if (hostScreen == screen) {
            close();
        }
    }

    // ── 弹窗模式（pin/popup 布局冻结用，1.21.11 viewerMode 语义） ─────────────

    public static int viewerMode() {
        return modeForCategory(currentCategory);
    }

    public static int modeForCategory(RecipeViewerCategory category) {
        if (category == null) return PopupRenderer.MODE_CRAFTING;
        return switch (category.id()) {
            case "furnace", "fuel" -> PopupRenderer.MODE_FURNACE;
            case "stonecutting" -> PopupRenderer.MODE_STONECUTTING;
            case "smithing" -> PopupRenderer.MODE_SMITHING;
            case "anvil" -> PopupRenderer.MODE_ANVIL;
            case "brewing" -> PopupRenderer.MODE_BREWING;
            case "grindstone" -> PopupRenderer.MODE_GRINDSTONE;
            default -> PopupRenderer.MODE_CRAFTING;
        };
    }

    /** 类别与打开菜单的匹配（点击放置前置，1.21.11 recipeFitsScreen 语义）。 */
    private static boolean recipeFitsScreen(RecipeHolder<?> holder,
                                            AbstractContainerScreen<?> screen) {
        if (holder == null || screen == null) return false;
        net.minecraft.world.item.crafting.RecipeType<?> type = holder.value().getType();
        if (screen.getMenu() == null) return false;
        net.minecraft.world.inventory.AbstractContainerMenu menu = screen.getMenu();
        if (type == net.minecraft.world.item.crafting.RecipeType.CRAFTING) {
            return menu instanceof net.minecraft.world.inventory.CraftingMenu
                    || menu instanceof net.minecraft.world.inventory.InventoryMenu;
        }
        if (type == net.minecraft.world.item.crafting.RecipeType.SMELTING
                || type == net.minecraft.world.item.crafting.RecipeType.BLASTING
                || type == net.minecraft.world.item.crafting.RecipeType.SMOKING) {
            return menu instanceof net.minecraft.world.inventory.AbstractFurnaceMenu;
        }
        if (type == net.minecraft.world.item.crafting.RecipeType.STONECUTTING) {
            return menu instanceof net.minecraft.world.inventory.StonecutterMenu;
        }
        if (type == net.minecraft.world.item.crafting.RecipeType.SMITHING) {
            return menu instanceof net.minecraft.world.inventory.SmithingMenu;
        }
        return false;
    }

    // ── 点击放置（1.21.11 placeRecipe 移植；1.21.1 无 tryPlaceRecipe →
    //  MultiPlayerGameMode.handlePlaceRecipe 直发，服务端 ServerPlaceRecipeMixin
    //  放行 contains，缺料回幽灵包 → RecipeUpdateListener.setupGhostRecipe） ──

    public static boolean placeRecipe(double mouseX, double mouseY, int button,
                                      AbstractContainerScreen<?> screen,
                                      RecipeHolder<?> holder) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getSoundManager() != null) {
            playButtonClick(mc);
        }
        if (holder == null || !recipeFitsScreen(holder, screen)) return false;
        if (mc.player == null) return false;
        try {
            // 清残留 ghost（无配方书组件则跳过——非配方书屏放置本就不适用）
            if (screen instanceof RecipeUpdateListener rul) {
                ((RecipeBookComponentAccessor) rul.getRecipeBookComponent()).getGhostRecipe().clear();
            }
            boolean shift = ClientCompat.isShiftDown();
            mc.gameMode.handlePlaceRecipe(mc.player.containerMenu.containerId, holder, shift);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ── hideNoRecipeBookStationObjects 过滤链（1.21.11 移植） ───────────────

    /** 对象级过滤：hide 开时仅保留有配方书工作站的条目。 */
    private static List<DisplayEntry> filterByRecipeBookStations(List<DisplayEntry> hits,
                                                                 RecipeViewerCategory category) {
        if (!BetterRecipeBook.config.hideNoRecipeBookStationObjects) return hits;
        if (hits.isEmpty()) return hits;
        List<DisplayEntry> out = new ArrayList<>();
        for (DisplayEntry entry : hits) {
            if (hasRecipeBookStation(entry, category)) out.add(entry);
        }
        return out;
    }

    /** 条目是否可归属到有配方书的工作站。 */
    private static boolean hasRecipeBookStation(DisplayEntry entry, RecipeViewerCategory category) {
        if (entry == null) return false;
        if (!browseAllMode && queryUsage && queryTarget != null && !queryTarget.isEmpty()
                && !RecipeViewerEngine.isRecipeBookStation(queryTarget)
                && category != null && category.appliesToStation(queryTarget)) {
            return false; // 站连接被切
        }
        if (isBuiltinCategory(category)) return true;
        List<ItemStack> icons = category == null ? List.of() : category.stationIconsFor(null);
        return entryHasRecipeBookStation(icons);
    }

    private static boolean entryHasRecipeBookStation(List<ItemStack> icons) {
        if (icons == null) return false;
        for (ItemStack icon : icons) {
            if (RecipeViewerEngine.isRecipeBookStation(icon)) return true;
        }
        return false;
    }

    /** 内置类别豁免（id ∈ {furnace, crafting, smithing, fuel}；stonecutting 不在
     *  豁免——切石机无配方书 UI，hide 开时按无配方书工作站过滤）。 */
    private static boolean isBuiltinCategory(RecipeViewerCategory category) {
        if (category == null) return false;
        String id = category.id();
        return id.equals("furnace") || id.equals("crafting")
                || id.equals("smithing") || id.equals("fuel");
    }

    /** 类别级隐藏集（插件类别全对象非法站 → 隐藏 tab）。 */
    private static Set<String> hiddenCategoryIds() {
        Set<String> out = new HashSet<>();
        for (RecipeViewerCategory category : RecipeViewerCategories.all()) {
            if (!(category instanceof PluginRecipeViewerCategory)) continue;
            if (category.isGridCategory()) continue;
            List<ItemStack> icons = category.stationIconsFor(null);
            boolean any = false;
            for (RecipeHolder<?> holder : RecipeViewerEngine.allRecipes(category.id())) {
                if (entryHasRecipeBookStation(icons)) { any = true; break; }
            }
            if (!any) out.add(category.id());
        }
        return out;
    }

    // ── pin 协作接口（PinOverlayManager 调用） ───────────────────────────────

    /** ESC 关闭：先清状态再 setVisible(false)（守卫不取消合规关闭）。 */
    public static boolean closeSilently() {
        if (!active) return false;
        close();
        return true;
    }

    /** 捕获条目（viewer 悬停按钮的配方）：pin 创建用。 */
    public record CapturedEntry(RecipeHolder<?> holder, RecipeViewerEngine.JeiEntry jei) {}

    private static CapturedEntry capturedEntryCache;

    public static CapturedEntry capturedEntry() {
        return capturedEntryCache;
    }

    /** 悬停命中（含 JEI 条目）→ 更新 capturedEntryCache 并返回。 */
    private static DisplayEntry hoveredEntryInternal(double mx, double my) {
        if (isGridMode()) return null;
        for (int li = 0; li < pageEntries.size(); li++) {
            int[] cell = gridCellFor(li);
            if (inside(mx, my, cell[0], cell[1], 25, 25)) {
                DisplayEntry e = pageEntries.get(li);
                capturedEntryCache = new CapturedEntry(e.holder(), e.jei());
                return e;
            }
        }
        return null;
    }

    /** pin 详细 tooltip（pin 渲染调用；1.21.1 文本行版）。 */
    public static void renderDetailedRecipeTooltip(GuiGraphics gui, RecipeHolder<?> holder,
                                                   int mode) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) return;
        ItemStack result;
        try {
            result = holder.value().getResultItem(mc.level.registryAccess());
        } catch (Exception e) {
            return;
        }
        if (result.isEmpty()) return;
        List<Component> lines = new ArrayList<>();
        lines.add(result.getHoverName());
        List<ItemStack> inputs = inputsOf(holder);
        if (!inputs.isEmpty()) {
            String suffix = inputs.size() > 1 ? " …" : "";
            lines.add(Component.translatable("brbe.viewer.materials")
                    .append(": ")
                    .append(inputs.get(0).getHoverName().copy()
                            .append(Component.literal(suffix))));
        }
        appendModName(lines, result);
        gui.renderComponentTooltip(mc.font, lines,
                (int) Minecraft.getInstance().mouseHandler.xpos(),
                (int) Minecraft.getInstance().mouseHandler.ypos());
    }

    /** Public mod-name append（pin 工具提示共用）。 */
    public static void appendModNamePublic(List<Component> lines, ItemStack stack) {
        appendModName(lines, stack);
    }


    /** 富条目 tooltip（1.21.11 语义 1.21.1 版）：标题+图标 → 工作站行 →
     *  模组名；ClientTooltipComponent 列表经 renderTooltipInternal 绘制。 */
    private static void renderEntryTooltipRich(GuiGraphics gui, DisplayEntry e,
                                               int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.font == null) return;
        ItemStack result = e.result();
        if (result.isEmpty()) return;
        List<net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent> components =
                new ArrayList<>();
        StringBuilder tooltip = new StringBuilder();
        tooltip.append(result.getHoverName().getString());
        components.add(new com.alonie.brbe.render.AbstractBrbeTooltipComponent.TitleWithIcon(
                result.getHoverName().getVisualOrderText(), result));
        // 熔炼信息行（XP + 按站别行+图标，阶段一 #2 对齐 1.21.11
        // furnaceTooltipComponents；四个烧炼子站各一行，颜色随站、白点标记当前站）。
        if (e.holder() != null && e.holder().value() instanceof AbstractCookingRecipe cooking) {
            float xp = cooking.getExperience();
            String xpText = xp % 1.0f == 0f ? String.valueOf((int) xp)
                    : String.format(Locale.ROOT, "%.2f", xp);
            components.add(componentLine(Component.literal(xpText + " XP").withStyle(ChatFormatting.GREEN)));
            int[] ticks = RecipeViewerIndex.furnaceStationTicks(e.holder());
            boolean furnaceStn = menuIs(net.minecraft.world.inventory.FurnaceMenu.class);
            boolean blastStn = menuIs(net.minecraft.world.inventory.BlastFurnaceMenu.class);
            boolean smokerStn = menuIs(net.minecraft.world.inventory.SmokerMenu.class);
            for (int si = 0; si < ticks.length; si++) {
                if (ticks[si] <= 0) continue;
                Component line = stationTimeLine(furnaceStationLabel(si),
                        cookSeconds(ticks[si]), stationStyle(si),
                        stationMatches(si, furnaceStn, blastStn, smokerStn));
                List<ItemStack> icons = RecipeViewerIndex.workstationsIconsForPrefix(
                        furnaceStationPrefix(si));
                if (BetterRecipeBook.config.hideNoRecipeBookStationObjects) {
                    List<ItemStack> filtered = new ArrayList<>();
                    for (ItemStack icon : icons) {
                        if (RecipeViewerEngine.isRecipeBookStation(icon)) filtered.add(icon);
                    }
                    icons = filtered;
                }
                components.add(new com.alonie.brbe.render.AbstractBrbeTooltipComponent.StationLine(
                        List.of(new com.alonie.brbe.render.AbstractBrbeTooltipComponent.StationLine.Segment(
                                line.getVisualOrderText(), icons, false))));
            }
        }
        // 内嵌完整预览（1.21.11 语义：标题/熔炼行之后、工作站行之前）。
        // 行序对齐 1.21.11 renderDetailedRecipeTooltip：标题+图标 → 熔炼行 →
        // 预览 → 工作站图标行 → 空行+模组名。
        RecipeViewerEngine.JeiEntry previewJei = e.jei();
        if (previewJei == null && e.holder() != null) {
            // 阶段二 B P4：holder 条目带 JEI 布局（切石/锻造）→ 预览走缩放委托。
            previewJei = com.alonie.brbe.cache.BrbeJeiBridge.attachedJeiEntry(e.id());
        }
        if (e.holder() != null || (previewJei != null && previewJei.layoutWidth() > 0)) {
            components.add(new com.alonie.brbe.render.RecipePreviewTooltipComponent(
                    e.holder(), previewJei, PopupRenderer.modeFor(
                            currentCategory == null ? null : currentCategory.id()),
                    isViewerCraftable(e), isViewerPartial(e)));
        }
        // 工作站图标行（仅非 grid 类别；熔炉/燃料走文本行）
        if (!isGridMode() && currentCategory != null && !currentCategory.isFuelCategory()) {
            List<ItemStack> icons = categoryStationIcons(currentCategory);
            if (!icons.isEmpty()) {
                List<com.alonie.brbe.render.AbstractBrbeTooltipComponent.StationLine.Segment> segs =
                        new ArrayList<>();
                segs.add(new com.alonie.brbe.render.AbstractBrbeTooltipComponent.StationLine.Segment(
                        Component.empty().getVisualOrderText(), icons, false));
                components.add(new com.alonie.brbe.render.AbstractBrbeTooltipComponent.StationLine(segs));
            }
        }
        // 模组名
        if (BetterRecipeBook.config.showModName) {
            Component mod = ModNameUtil.getFormattedModName(result);
            if (mod != null && !mod.getString().isEmpty()) {
                components.add(componentLine(Component.empty()));
                components.add(componentLine(mod));
            }
        }
        com.alonie.brbe.mixins.accessors.GuiGraphicsAccessor acc =
                (com.alonie.brbe.mixins.accessors.GuiGraphicsAccessor) gui;
        acc.brbe$renderTooltipInternal(mc.font, components, mouseX, mouseY,
                net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner.INSTANCE);
    }

    /** 条目是否可合成（viewer 集合 craftable 判定；无集合时兜底 false）。 */
    private static boolean isViewerCraftable(DisplayEntry e) {
        if (e.holder() == null || currentCollection == null) return false;
        try {
            return currentCollection.isCraftable(e.holder());
        } catch (Exception ex) {
            return false;
        }
    }

    /** 条目是否残缺（viewer partial 判定，与 Shift 弹窗同源）。 */
    private static boolean isViewerPartial(DisplayEntry e) {
        if (e.holder() == null || currentCollection == null) return false;
        try {
            return PartialCraftingUtil.isPartiallyCraftable(currentCollection, e.holder());
        } catch (Exception ex) {
            return false;
        }
    }

    private static net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent
            componentLine(Component text) {
        return net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent.create(
                text.getVisualOrderText());
    }

    /** 类别的工作站图标（站列数据源同款；hide 开时过滤）。 */
    private static List<ItemStack> categoryStationIcons(RecipeViewerCategory category) {
        List<ItemStack> icons = new ArrayList<>();
        if (category instanceof PluginRecipeViewerCategory plugin) {
            icons.addAll(plugin.stations());
        } else {
            icons.addAll(RecipeViewerIndex.stationColumnItemsFor(category.id()));
        }
        if (BetterRecipeBook.config.hideNoRecipeBookStationObjects) {
            List<ItemStack> filtered = new ArrayList<>();
            for (ItemStack icon : icons) {
                if (RecipeViewerEngine.isRecipeBookStation(icon)) filtered.add(icon);
            }
            return filtered;
        }
        return icons;
    }


    // ── 变体轮循（Alt+滚轮手工步进；1.21.11 SlotSelectTime 语义的 1.21.1 版） ──

    /** Alt 按住期间冻结的轮循索引（松开后自动轮循恢复）。 */
    private static int manualCycleIndex = -1;
    private static boolean cyclePaused;

    private static boolean isCycleAltDown() {
        return ClientCompat.isAltDown();
    }

    /** The slot-select cycle index used by every BRBE front-end (viewer overlay
     *  buttons, popup, tooltip preview, pin): while Alt is held the rotation
     *  freezes on the Alt-press index and Alt+wheel steps it; on release the
     *  automatic cycle resumes (1.21.11 currentSlotSelectIndex 语义）。 */
    public static int currentSlotSelectIndex(int autoIndex) {
        boolean alt = isCycleAltDown();
        if (alt) {
            if (!cyclePaused) {
                cyclePaused = true;
                manualCycleIndex = autoIndex;
            }
        } else if (cyclePaused) {
            cyclePaused = false;
            manualCycleIndex = -1;
        }
        return cyclePaused ? manualCycleIndex : autoIndex;
    }

    /** Shift 弹窗内光标所在槽位的物品（阶段一 #3；经 PopupGeometry.itemAt
     *  命中，selIdx 与按钮轮循同源——游戏时间 /30）。无命中返回空。 */
    private static ItemStack popupSlotStack(double mx, double my) {
        PopupRenderer.GeometryRef ref = PopupRenderer.lastGeometry();
        if (ref == null || ref.geometry() == null) return ItemStack.EMPTY;
        Minecraft mc = Minecraft.getInstance();
        int autoIndex = mc != null && mc.player != null
                ? Mth.floor(mc.player.tickCount / 30.0f) : 0;
        int selIdx = currentSlotSelectIndex(autoIndex % Math.max(1, 3));
        return ref.geometry().itemAt(mx, my, selIdx);
    }

    /** Alt+wheel: step the paused slot-select index for the viewer's overlay
     *  buttons (the overlay .time / 30 % len auto-cycle freezes under Alt). */
    private static boolean stepCycledVariants(double vertical) {
        cyclePaused = true;
        if (manualCycleIndex < 0) {
            manualCycleIndex = (int) (((OverlayRecipeComponentAccessor) (Object) overlayComponent)
                    .getTime() / 30);
        }
        manualCycleIndex += vertical > 0 ? -1 : 1;
        if (manualCycleIndex < 0) manualCycleIndex = 0;
        return true;
    }
}
