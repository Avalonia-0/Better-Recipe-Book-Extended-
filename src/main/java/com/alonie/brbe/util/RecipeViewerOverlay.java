package com.alonie.brbe.util;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.cache.RecipeViewerIndex;
import com.alonie.brbe.compat.ItemViewCompat;
import com.alonie.brbe.compat.SyntheticRecipeRenderer;
import com.alonie.brbe.compat.SyntheticRecipeRenderers;
import com.alonie.brbe.jei.plugins.engine.PluginRecipeViewerCategory;
import com.alonie.brbe.mixins.accessors.ClientRecipeBookAccessor;
import com.alonie.brbe.pinoverlay.PinOverlay;
import com.alonie.brbe.pinoverlay.PinOverlayManager;
import com.alonie.brbe.util.ModNameUtil;
import com.alonie.brbe.util.IncompatibleCraftingUtil;
import com.alonie.brbe.mixins.accessors.AbstractContainerScreenAccessor;
import com.alonie.brbe.mixins.accessors.AbstractRecipeBookScreenAccessor;
import com.alonie.brbe.mixins.accessors.GhostSlotsAccessor;
import com.alonie.brbe.mixins.accessors.GhostSlotsSetSlotAccessor;
import com.alonie.brbe.mixins.accessors.OverlayRecipeButtonAccessor;
import com.alonie.brbe.mixins.accessors.OverlayRecipeComponentAccessor;
import com.alonie.brbe.mixins.accessors.RecipeBookComponentAccessor;
import com.alonie.brbe.mixins.accessors.RecipeBookPageAccessor;
import com.alonie.brbe.recipeviewer.CompostRecipeCategory;
import com.alonie.brbe.recipeviewer.FuelRecipeCategory;
import com.alonie.brbe.recipeviewer.InfoRecipeCategory;
import com.alonie.brbe.recipeviewer.RecipeViewerCategories;
import com.alonie.brbe.recipeviewer.RecipeViewerCategory;
import com.alonie.brbe.render.PopupGeometry;
import com.alonie.brbe.render.RecipePreviewTooltipComponent;
import com.alonie.brbe.recipeviewer.engine.RecipeViewerEngine;
import net.fabricmc.loader.api.FabricLoader;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.gui.screens.recipebook.GhostSlots;
import net.minecraft.client.gui.screens.recipebook.OverlayRecipeComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookPage;
import net.minecraft.client.gui.screens.recipebook.RecipeButton;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.inventory.AbstractCraftingMenu;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.BlastFurnaceMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.FurnaceMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.SmokerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.display.FurnaceRecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.minecraft.world.item.crafting.display.SmithingRecipeDisplay;
import net.minecraft.world.item.crafting.display.StonecutterRecipeDisplay;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

/**
 * The BRBE R/U recipe-viewer as a <b>standalone overlay</b>, decoupled from the
 * recipe-book component.  A single {@link OverlayRecipeComponent} instance is
 * driven directly by {@code AbstractContainerScreen} mixins, so the viewer works
 * on every container screen (crafting table, inventory, furnace, chest, …) and
 * never forces the recipe book open.
 *
 * <p>R (synthesize) / U (usage) queries the hovered item against the vanilla
 * recipe book's known set, builds a temporary {@link RecipeCollection} and opens
 * the alternative-recipe group anchored near the hovered item.  The viewer closes
 * only on ESC (dismisses the overlay alone) or a click outside the box.</p>
 */
public final class RecipeViewerOverlay {


    private RecipeViewerOverlay() {}


    // ── Window chrome (desktop-window semantics) ───────────────────────────
    /** Panel sprite's 9-slice border thickness (the {@code overlay_recipe}
     *  nine-slice border is 4px; the extension geometry counts it). */
    private static final int PANEL_BORDER = 4;

    /** Turn-page button's overall offset from the box: 3px right, 5px down
     *  (user-fixed); the extension band follows it (the top border keeps its
     *  4px + 2px relationship above the buttons). */
    private static final int PAGE_BTN_SHIFT_X = 3;

    private static final int PAGE_BTN_SHIFT_Y = 5;

    /** Title-bar band height = the ORIGINAL goal (the background extension):
     *  the box's background is drawn one piece taller — the top border 4px
     *  moves up with it, ending flush at the turn-page buttons' top edge
     *  (the user moved the extension's top 2px down).  Band top = boxTop -
     *  14.  The title + ✕ share the buttons' row; the extension is ONE
     *  continuous panel — nothing else is drawn (no second blit, no divider,
     *  no separate panel). */
    private static final int TITLE_BAR_H = 14;

    /** 对象区的行上限 / 列上限（配置项「配方区行上限」「配方区列上限」，默认
     *  3 行 x 7 列）的兜底夹紧区间：这两个配置项是**无界限的整型输入框**，
     *  0 / 负数 / 天文数字都必须退化到能算的区间 —— 一页的容量 = 行 x 列既不能
     *  为 0（{@code (total + pageSize() - 1) / pageSize()} 会除零）也不能溢出
     *  int；见 {@link #pageRows()} / {@link #pageCols()}。 */
    private static final int LIMIT_MIN = 1;

    private static final int LIMIT_MAX = 64;

    /** 配置尚未就绪（Cloth Config 缺席 / 启动早期 {@code config == null}）时的
     *  退化值，与两个配置字段的默认值一致。 */
    private static final int DEFAULT_ROW_LIMIT = 3;

    private static final int DEFAULT_COL_LIMIT = 7;

    private static final int PAGE_BTN_WIDTH = 14;

    private static final int PAGE_BTN_HEIGHT = 13;

    /** The viewer's left workstation column is an independent object column
     *  ("grid column -1"): the panel extends one object-grid pitch (25px) left
     *  of the object area, and the station cells sit on the SAME 25px grid as
     *  the object columns — each cell is 24px wide at the same 4px inset
     *  (panelLeft+4), so the column's centreline (panelLeft+16) is exactly one
     *  pitch left of object column 0's centreline (boxLeft+16), i.e. the
     *  station cells line up with the object grid like a real column. */
    private static final int STATION_CELL = 24;

    private static final int STATION_PITCH = 25;

    private static final int STATION_COL_WIDTH = 25;

    private static final int TAB_TEX_WIDTH = 35;

    private static final int TAB_TEX_HEIGHT = 27;

    /** The rotated tab is too tall, so the texture's middle 4px (along its
     *  width) is cut out and the right half spliced onto the left half. */
    private static final int TAB_CUT = 6;

    private static final int TAB_LEFT = 16;

    private static final int TAB_RIGHT_START = TAB_LEFT + TAB_CUT;

    /** On-screen pitch between tab starts — the object-column pitch (25px):
     *  every tab's icon centers on its column's center line and the tab
     *  strip spans exactly the column grid.  Used for positioning, hit
     *  tests and the box-width calculation. */
    private static final int TAB_WIDTH = 25;

    /** Cropped on-screen panel width: the texture's 27-row vertical extent
     *  (which maps to the on-screen width after the -90° rotation) minus the
     *  MIDDLE {@link #TAB_V_CUT} rows — the rounded ends (the texture's
     *  solid v=0 / v=25 edge lines) stay intact, only the plain mid section
     *  is dropped.  The panel is exactly one pitch wide; the ~2px visual gap
     *  between tabs comes from the texture's transparent v=26 row, matching
     *  the original look at the old 27px pitch. */
    private static final int TAB_DRAW_WIDTH = TAB_WIDTH;

    /** Vertical (v) splice of the texture (v maps to the on-screen width):
     *  rows [0, TAB_V_TOP) and [TAB_V_TOP + TAB_V_CUT, TAB_TEX_HEIGHT) are
     *  kept, the TAB_V_CUT rows between them are dropped — a runtime crop of
     *  the tab's MIDDLE, keeping both edge lines; the texture files are not
     *  edited. */
    private static final int TAB_V_TOP = 13;

    private static final int TAB_V_CUT = TAB_TEX_HEIGHT - TAB_DRAW_WIDTH;

    private static final int TAB_V_BOTTOM = TAB_TEX_HEIGHT - TAB_V_TOP - TAB_V_CUT;

    private static final int TAB_HEIGHT = TAB_TEX_WIDTH - TAB_CUT;

    /** Tabs overhang the box bottom by TAB_HEIGHT - 4 (tab top is 4px above the box bottom). */
    private static final int TAB_OVERHANG = TAB_HEIGHT - 4;

    /** 查询界面「配方区行上限」（配置项，默认 3）：对象区**一页最多显示的行数**。
     *
     *  <p>行上限同时就是**工作站列的对象数量上限**：左侧工作站列的行数由框体
     *  高度推导（{@link #stationViewRows()} = {@code (boxH - 8) / 25}），而框体
     *  高度 = 本页行数 x 25 + 8（{@link #fitBoxToPage}），本页行数 =
     *  {@code ceil(对象数 / 列数)} 恒 <= 行上限 —— 所以工作站列里最多只会出现
     *  "行上限"个对象，无需再单独钳制列的高度。 */
    private static int pageRows() {
        com.alonie.brbe.config.BrbeConfig cfg = BetterRecipeBook.config;
        return clampLimit(cfg == null ? DEFAULT_ROW_LIMIT : cfg.recipeViewerRowLimit);
    }

    /** 查询界面「配方区列上限」（配置项，默认 7）：对象区**一页最多显示的列数**，
     *  同时也就是**底部标签的数量上限**（{@link #maxTabs()} = 列上限，一列一个标签；
     *  超出时标签条按 REI 式滑窗滑动而不是折行）。
     *
     *  <p>唯一的例外是**顶部元素**：标题栏整行的加列优先级高于本上限 ——
     *  {@link #ensureTitleWidth()} 仍可继续创建列（空列）把框体撑宽，直到标题
     *  文字连同旁边翻页键的占位一起放得下。 */
    private static int pageCols() {
        com.alonie.brbe.config.BrbeConfig cfg = BetterRecipeBook.config;
        return clampLimit(cfg == null ? DEFAULT_COL_LIMIT : cfg.recipeViewerColumnLimit);
    }

    /** 一页的对象容量 = 行上限 x 列上限。 */
    private static int pageSize() {
        return pageRows() * pageCols();
    }

    /** 标签条自身的**撑宽上限** = {@link #pageCols()} 列上限（一列一个标签）：标签条最多把框体
     *  撑到"列上限"那么宽。原来这里还写死了一个 {@code MAX_TABS = 10}，列上限调到 10 以上时标签数
     *  上不去（用户 2026-09-13 反馈）—— **标签数上限 = 列上限**。
     *
     *  <p>绘制/命中/滚轮用的**实际窗口大小**是 {@link #visibleTabCount()}：它取框体**真实的列数**，
     *  所以标题栏（{@link #ensureTitleWidth}）撑出额外列时，标签条也能跟着**临时突破列上限**。</p> */
    private static int maxTabs() {
        return Math.max(1, pageCols());
    }

    /** 把配置里的上限夹进 {@link #LIMIT_MIN}..{@link #LIMIT_MAX}。 */
    private static int clampLimit(int value) {
        return Math.max(LIMIT_MIN, Math.min(LIMIT_MAX, value));
    }


    /** Whether the cycle-lock key (the configurable 「锁定折叠物品」 binding,
     *  default Alt) is currently held.  The freeze itself is per ITEM — only the
     *  item under the cursor is held (user 2026-09-13) — see {@link CycleLock}. */
    public static boolean isCycleAltDown() {
        return CycleLock.isDown();
    }


    /** Whether scroll-around is enabled (turn-page buttons never hit a dead end). */
    private static boolean scrollWrap() {
        return BetterRecipeBook.config.scrolling.scrollAround;
    }

    /** 「在配方区使用自然的翻页方向」（默认开）：{@code true} = 鼠标滚轮向前
     *  （上滚）往后翻页；{@code false} = 旧方向（上滚往前翻页）。只作用于
     *  <b>配方区</b>的翻页——标签条翻页与 Alt+滚轮轮循走各自的方向。 */
    private static boolean naturalPageDirection() {
        return BetterRecipeBook.config == null || BetterRecipeBook.config.naturalPageDirection;
    }


    /** Cached ids of categories whose objects are ALL hidden by the filter
     *  (their tab is hidden too).  Rebuilt when the toggle state changes or
     *  after a plugin re-collection. */
    private static Set<String> cachedHiddenCategoryIds;

    private static boolean cachedHiddenConfigState;


    /** Category ids hidden in progress mode (hideNoRecipeBookStationObjects):
     *  信息行类别（燃料/堆肥/信息）与无配方书体系的工作站类别（切石/铁砧/
     *  研磨）整体隐藏；配方书体系的内置类别（合成/烧炼/锻造/酿造）与配方书
     *  驱动的 mod 类别保留。 */
    private static Set<String> computeHiddenCategoryIds() {
        Set<String> hidden = new HashSet<>();
        for (RecipeViewerCategory cat : RecipeViewerCategories.all()) {
            if (!RecipeViewerCategories.isProgressCategory(cat)) hidden.add(cat.id());
        }
        return hidden;
    }

    // ── Multi-window manager state ──
    /** All open query windows, in z order (last = topmost). */
    private static final java.util.List<ViewerInstance> WINDOWS =
            new java.util.ArrayList<>();
    /** The window currently being dragged, or null. */
    private static ViewerInstance dragWindow;

    /** 被"本界面内 ESC 关闭"抑制自动恢复的宿主界面。{@code ViewerInstance.close()}
     *  是被动关闭（保留持久化 spec、只清 {@code materialized}），好让窗口在**下一个**
     *  容器界面恢复；但 {@link #restorePendingViewers()} 每帧都会跑，同一界面里会把
     *  刚被 ESC 关掉的窗口立刻重新物化 → ESC 被反复吞掉、用户再也退不出当前界面。
     *  这里记下界面实例，界面一变（关闭/换屏）即自动解除。 */
    private static AbstractContainerScreen<?> restoreSuppressedScreen;

    // ── Query-window persistence (brbe.queryviewers.json, like pins) ──────
    /** A persisted query viewer window: the query identity (target item +
     *  usage/recipe mode), selected category, page and window position.  It
     *  restores onto the next container screen, exactly like pin overlays
     *  ({@code brbe.pinoverlays.json}); right-click close removes it.  The
     *  two session-only flags are never written to disk. */
    public static final class ViewSpec {
        String item;          // query target item's registry id
        boolean usage;        // true = 用途 query, false = 配方 query
        String category;      // selected category id (may be null)
        int page;
        int x, y;             // window box position at the last save
        transient boolean materialized;   // a window is bound to this spec now
        transient boolean snoozed;        // capped this session (no re-create)
    }

    private static final Gson PV_GSON = new Gson();
    private static final Type PV_SPECS_TYPE =
            new TypeToken<ArrayList<ViewSpec>>() {}.getType();
    private static Path viewerSpecFile;
    private static final List<ViewSpec> viewerSpecs = new ArrayList<>();
    private static boolean viewerPersistenceReady;

    /** Load the persisted query windows (lazy — the game directory is only
     *  available once Minecraft exists). */
    private static void initViewerPersistence() {
        if (viewerPersistenceReady) return;
        viewerPersistenceReady = true;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.gameDirectory == null) return;
        viewerSpecFile = mc.gameDirectory.toPath().resolve("brbe.queryviewers.json");
        loadViewerSpecs();
    }

    private static void loadViewerSpecs() {
        if (viewerSpecFile == null || !Files.exists(viewerSpecFile)) return;
        try {
            List<ViewSpec> specs = PV_GSON.fromJson(
                    Files.readString(viewerSpecFile, StandardCharsets.UTF_8),
                    PV_SPECS_TYPE);
            if (specs != null) {
                viewerSpecs.clear();
                for (ViewSpec spec : specs) {
                    if (spec != null) viewerSpecs.add(spec);
                }
            }
        } catch (Exception e) {
            System.err.println("[BRBE] Failed to read query viewers: " + e.getMessage());
        }
    }

    /** Write the spec list asynchronously (mirrors the pin overlay store). */
    private static void saveViewerSpecs() {
        if (viewerSpecFile == null) return;
        List<ViewSpec> snapshot = new ArrayList<>(viewerSpecs);
        CompletableFuture.runAsync(() -> {
            try {
                Files.createDirectories(viewerSpecFile.getParent());
                Files.writeString(viewerSpecFile,
                        PV_GSON.toJson(snapshot), StandardCharsets.UTF_8);
            } catch (Exception e) {
                System.err.println("[BRBE] Failed to write query viewers: " + e.getMessage());
            }
        });
    }

    /**
     * ESC 关闭查询窗口后调用：**本界面内**不再自动恢复该窗口。
     * <p>不加抑制的话，{@link #restorePendingViewers()}（每帧由
     * {@code PinOverlayManager.render} 调用）会在下一帧把 spec 重新物化，
     * 于是 ESC 每按一次都只换来"窗口闪一下又回来"，界面永远关不掉。
     * 界面一变（关闭/换屏）自动解除，窗口照常在新界面恢复。</p>
     */
    private static void suppressRestoreOnCurrentScreen() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            restoreSuppressedScreen = null;
            return;
        }
        if (mc.screen instanceof AbstractContainerScreen<?> host) {
            restoreSuppressedScreen = host;
            return;
        }
        restoreSuppressedScreen = null;
    }

    /** Restore persisted windows that are not open yet (called every frame
     *  from { PinOverlayManager#render} — NOT from { #render}: the
     *  viewer render is skipped when pins exist and no query window is open,
     *  so a warm-closed window would never come back until a new window made
     *  the viewer active): a spec that cannot be resolved yet (engine not
     *  ready, item unknown) stays pending, exactly like pin specs.  A window
     *  that passed its last save is ALSO restored here after a passive close
     *  (host screen closed) — the spec's { materialized} flag is cleared
     *  by { #close()}. */
    public static void restorePendingViewers() {
        // 预览模式：查询窗口生命周期不持久化——关闭当前界面并重开后不再恢复。
        if (BetterRecipeBook.config.previewMode) return;
        initViewerPersistence();
        if (viewerSpecs.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.level == null) return;
        // 界面已变（关闭/换屏）→ 解除"本界面内不恢复"的抑制；同一界面则跳过恢复，
        // 否则被 ESC 关掉的窗口会在这里复活（见 restoreSuppressedScreen）。
        if (mc.screen != restoreSuppressedScreen) restoreSuppressedScreen = null;
        if (!(mc.screen instanceof AbstractContainerScreen<?> screen)) return;
        for (ViewSpec spec : new ArrayList<>(viewerSpecs)) {
            if (spec.materialized || spec.snoozed) continue;
            ViewerInstance w = new ViewerInstance();
            if (w.restoreFrom(spec, screen)) {
                WINDOWS.add(w);
                trimWindows();
            }
        }
    }

    // ── MANAGER section written below ──
    // ── Multi-window manager ──────────────────────────────────────────────
    private static ViewerInstance topmost() {
        return WINDOWS.isEmpty() ? null : WINDOWS.get(WINDOWS.size() - 1);
    }

    /** The topmost window whose own region contains the point (else null). */
    private static ViewerInstance underCursor(double mx, double my) {
        for (int i = WINDOWS.size() - 1; i >= 0; i--) {
            ViewerInstance w = WINDOWS.get(i);
            if (w.contains(mx, my)) return w;
        }
        return null;
    }

    private static void raise(ViewerInstance w) {
        if (w != null && WINDOWS.remove(w)) WINDOWS.add(w);
    }

    private static void syncActiveFlag() {
        RecipeViewerIndex.setViewerActive(!WINDOWS.isEmpty());
    }

    /** Cap the window count (newest kept; oldest closed).  close() itself
     *  removes the window from the list (onWindowClosedSelf).  A capped
     *  window's spec is SNOOZED for the session so it does not immediately
     *  re-materialize on the same session (it returns next session). */
    private static void trimWindows() {
        while (WINDOWS.size() > 8) {
            ViewerInstance w = WINDOWS.get(0);
            if (w.spec != null) w.spec.snoozed = true;
            w.close();
        }
        syncActiveFlag();
    }

    /** Called by a window that closed itself (right-click on the window). */
    private static void onWindowClosedSelf(ViewerInstance w) {
        if (WINDOWS.remove(w)) {
            if (dragWindow == w) dragWindow = null;
            syncActiveFlag();
        }
    }

    public static boolean isActive() {
        return !WINDOWS.isEmpty();
    }

    public static int viewerZ() {
        ViewerInstance w = topmost();
        return w != null ? w.viewerZ : -1;
    }

    /** Union of every open window's exclusion rect (over-inclusive for JEI). */
    public static Rect2i exclusionArea() {
        int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE;
        int x1 = Integer.MIN_VALUE, y1 = Integer.MIN_VALUE;
        boolean any = false;
        for (ViewerInstance w : WINDOWS) {
            Rect2i r = w.exclusionArea();
            if (r == null) continue;
            any = true;
            x0 = Math.min(x0, r.getX());
            y0 = Math.min(y0, r.getY());
            x1 = Math.max(x1, r.getX() + r.getWidth());
            y1 = Math.max(y1, r.getY() + r.getHeight());
        }
        return any ? new Rect2i(x0, y0, x1 - x0, y1 - y0) : null;
    }

    public static boolean contains(double mx, double my) {
        return underCursor(mx, my) != null;
    }

    /** The topmost window's open popup exclusion rect (only one popup exists). */
    public static Rect2i popupExclusionArea() {
        for (int i = WINDOWS.size() - 1; i >= 0; i--) {
            Rect2i r = WINDOWS.get(i).popupExclusionArea();
            if (r != null) return r;
        }
        return null;
    }

    public static void onScreenClosed(AbstractContainerScreen<?> screen) {
        boolean removed = false;
        for (int i = WINDOWS.size() - 1; i >= 0; i--) {
            ViewerInstance w = WINDOWS.get(i);
            if (w.ownerScreen == screen) {
                w.close();
                // close() already self-removes via onWindowClosedSelf — an
                // extra WINDOWS.remove(i) here indexes the shifted list and
                // drops the WRONG window (or throws at the tail).
                removed = true;
            }
        }
        if (removed) syncActiveFlag();
    }

    /** Open a NEW query window (the R/U keypress path — every query opens its
     *  own window; already-open windows are never touched).  Returns whether
     *  the query was consumed. */
    private static boolean openNewViewer(AbstractContainerScreen<?> screen, boolean viewUsage) {
        ViewerInstance w = new ViewerInstance();
        if (w.open(screen, viewUsage)) {
            WINDOWS.add(w);
            trimWindows();
            return true;
        }
        return false;
    }

    /** Open a NEW query window for an explicit {@code target} (the left
     *  workstation column's click path): the station query behaves like the
     *  regular query path — a FAILED query opens nothing and the originating
     *  window keeps its content (the old in-place re-query fell through to
     *  the openFor fallback and CLOSED the live window). */
    private static boolean openNewViewer(AbstractContainerScreen<?> screen, ItemStack target,
                                         boolean viewUsage) {
        ViewerInstance w = new ViewerInstance();
        if (w.openFor(screen, target, viewUsage)) {
            WINDOWS.add(w);
            trimWindows();
            return true;
        }
        return false;
    }

    /** 工作站标题触发：以 {@code station} 为用途查询目标打开<b>新</b>查询窗口
     *  （看到该工作站所属的全部配方对象）。供 {@link WorkstationTitleTrigger}
     *  调用；失败（类别无内容）打开不了也不影响已有窗口。 */
    public static boolean openForStation(AbstractContainerScreen<?> screen, ItemStack station) {
        return openNewViewer(screen, station, true);
    }

    /** 是否有查询窗口拥有该点（窗口条带/框体/标签区）——工作站的标题触发
     *  在窗口覆盖该区域时让位。 */
    public static boolean ownsPoint(double mx, double my) {
        for (ViewerInstance w : WINDOWS) {
            if (w.contains(mx, my)) return true;
        }
        return false;
    }

    public static boolean keyPressed(KeyEvent event, AbstractContainerScreen<?> screen) {
        initViewerPersistence();
        boolean viewRecipe = ClientCompat.matches(BetterRecipeBook.RECIPE_VIEW_MAPPING,
                event.key(), event.scancode(), event.modifiers());
        boolean viewUsage = ClientCompat.matches(BetterRecipeBook.USAGE_VIEW_MAPPING,
                event.key(), event.scancode(), event.modifiers());
        if (viewRecipe || viewUsage) {
            // Every R/U opens a NEW query window (multi-window).
            return openNewViewer(screen, viewUsage);
        }
        if (event.isEscape()) {
            // ESC 永远交回屏幕：用户期望"无论查询界面是否存在，ESC 都能退出界面"。
            // 已打开的窗口随这次 ESC 一起关闭（持久化 spec 保留 → 在下一个容器界面
            // 恢复，与 pin 的"窗口随宿主界面存活"语义一致），并抑制它在本界面内复活：
            // 否则下一帧 restorePendingViewers() 会把它重新物化，ESC 被永久吞掉，
            // 用户再也退不出当前界面（2026-09-11 用户反馈）。
            // 返回 false = 不消费这次按键，vanilla 继续处理——配方书界面先收起配方书
            // （原版行为，与本 mod 无关），其它容器界面直接关闭、窗口随宿主界面一起关。
            if (WINDOWS.isEmpty()) return false;
            close();
            suppressRestoreOnCurrentScreen();
            return false;
        }
        return false;
    }

    public static boolean mouseClicked(MouseButtonEvent event, boolean doubleClick,
                                       AbstractContainerScreen<?> screen) {
        if (WINDOWS.isEmpty()) return false;
        ViewerInstance w = underCursor(event.x(), event.y());
        if (w == null && RecipePopupLayer.isActive()
                && RecipePopupLayer.contains(event.x(), event.y())) {
            // The hard-modal preview popup extends past the window box; a
            // click on its panel (while a window is open) must not fall
            // through to the desktop below it.
            w = topmost();
        }
        if (w == null) {
            // 预览模式：与查询界面以外的元素交互（点击区外）即关闭全部查询
            // 窗口——点击本身仍落到下方桌面（交互照常执行）。关闭模式（默认
            // 关）保留旧行为：区外点击只放行，不影响打开的窗口。
            if (BetterRecipeBook.config.previewMode && !WINDOWS.isEmpty()) {
                close();
            }
            return false;
        }
        raise(w);
        dragWindow = null;
        boolean handled = w.mouseClicked(event, doubleClick, screen);
        if (w.windowDragging) dragWindow = w;
        return handled;
    }

    public static boolean placeRecipe(MouseButtonEvent event, AbstractContainerScreen<?> screen,
                                      RecipeDisplayId recipe, RecipeCollection collection) {
        ViewerInstance w = topmost();
        return w != null && w.placeRecipe(event, screen, recipe, collection);
    }

    /** Scroll handling for the query windows, the LEI preview popup and the pin
     *  overlays.  The preview popup (Shift) is the top layer and may reach
     *  outside its window's box: with the cursor on it the wheel belongs to it
     *  — 锁定键按住时逐格翻动**它里面指针下那一件**折叠物品（用户 2026-09-13
     *  诉求 1），否则吞掉滚轮（翻页会重建按钮、销毁弹窗）。With a window under
     *  the cursor it owns the scroll; with NO window (pins alone) the wheel steps
     *  the folded item under the cursor or is swallowed by a pin so the desktop
     *  does not scroll. */
    public static boolean mouseScrolled(double mouseX, double mouseY, double vertical) {
        if (RecipePopupLayer.isActive() && RecipePopupLayer.contains(mouseX, mouseY)) {
            if (vertical != 0 && CycleLock.isDown() && CycleLock.step(vertical)) {
                return true;
            }
            return true;
        }
        ViewerInstance w = underCursor(mouseX, mouseY);
        if (w != null) return w.mouseScrolled(mouseX, mouseY, vertical);
        if (vertical != 0 && CycleLock.isDown()
                && PinOverlayManager.topInteractivePin(mouseX, mouseY) != null) {
            return CycleLock.step(vertical);
        }
        return PinOverlayManager.handleMouseScrolled(mouseX, mouseY, vertical);
    }

    public static boolean mouseDragged(MouseButtonEvent event) {
        return dragWindow != null && dragWindow.mouseDragged(event);
    }

    public static boolean mouseReleased(MouseButtonEvent event) {
        if (dragWindow == null) return false;
        dragWindow.mouseReleased(event);
        dragWindow = null;
        return true;
    }

    public static boolean isWindowDragging() {
        return dragWindow != null && dragWindow.windowDragging;
    }

    /** Draw every open window, bottom to top. */
    public static void render(GuiGraphics gui, int mouseX, int mouseY, float delta) {
        for (ViewerInstance w : WINDOWS) {
            // Pin state / search space can change underneath the windows
            // (A-pin in the recipe book, inventory pickup/drop): re-evaluate
            // ordering and states before drawing so the pin ordering and the
            // fuel owned→missing order stay live instead of on reopen.
            w.refreshIfDirty();
            w.render(gui, mouseX, mouseY, delta);
        }
        // The single popup layer follows the topmost window that has a hover
        // field (the window under the cursor set it during its render).
        ViewerInstance popup = null;
        for (int i = WINDOWS.size() - 1; i >= 0; i--) {
            ViewerInstance w = WINDOWS.get(i);
            if (w.hoverPopupField != null) {
                popup = w;
                break;
            }
        }
        if (popup != null) {
            RecipePopupLayer.update(popup.hoverPopupField);
        } else {
            RecipePopupLayer.close();
        }
        // While a window title-bar drag is actually MOVING the window (only
        // once the drag has produced displacement — a plain band press keeps
        // the normal cursor), show the system's closed-fist cursor (the
        // desktop's drag cursor; loaded from the active Xcursor theme at
        // runtime) — requested LAST so nothing drawn before can override it.
        // Falls back to the standard resize-all cursor when the fist cannot
        // be loaded.
        if (dragWindow != null && dragWindow.windowDragging
                && dragWindow.windowDragMoved) {
            com.mojang.blaze3d.platform.cursor.CursorType fist =
                    ViewerCursor.fist();
            gui.requestCursor(fist != null
                    ? fist
                    : com.mojang.blaze3d.platform.cursor.CursorTypes.RESIZE_ALL);
        }
    }

    public static void renderTooltip(GuiGraphics gui, int mouseX, int mouseY) {
        ViewerInstance w = underCursor(mouseX, mouseY);
        if (w != null) w.renderTooltip(gui, mouseX, mouseY);
    }

    public static boolean isOwnOverlay(OverlayRecipeComponent o) {
        for (ViewerInstance w : WINDOWS) {
            if (w.overlay == o) return true;
        }
        return false;
    }

    /** Whether {@code o} belongs to a viewer window that paints its own content
     *  this frame.  A BRBE window always draws the WHOLE box itself — background
     *  (extended {@code TITLE_BAR_H} upward), buttons, pin markers, page
     *  controls — <b>independently of paging</b>; vanilla's draw pass instead
     *  lays the background out for "at most 5 columns", i.e. a NARROWER box
     *  that — once the button count passes 25 — is also TALLER than ours, so its
     *  bottom border and its left border column (= exactly the workstation
     *  column / box junction x) peek out below the viewer.  That pass must
     *  therefore be skipped for every active own overlay, not only paged ones.
     *  @see com.alonie.brbe.mixins.recipeviewer.OverlayRecipeComponentMixin */
    public static boolean isOwnActiveOverlay(OverlayRecipeComponent o) {
        for (ViewerInstance w : WINDOWS) {
            if (w.overlay == o && w.isActive()) return true;
        }
        return false;
    }

    public static boolean isPaged() {
        ViewerInstance w = topmost();
        return w != null && w.isPaged();
    }

    public static ItemStack captureGhostItem(AbstractRecipeBookScreen<?> screen, Slot slot) {
        try {
            RecipeBookComponent<?> book = ((AbstractRecipeBookScreenAccessor) screen).brbe$getRecipeBookComponent();
            if (book == null) return ItemStack.EMPTY;
            GhostSlotsAccessor ghostAcc = (GhostSlotsAccessor) ((RecipeBookComponentAccessor) book).getGhostSlots();
            if (ghostAcc == null) return ItemStack.EMPTY;

            Object ghost = ghostAcc.getIngredients().get(slot);
            if (ghost == null) return ItemStack.EMPTY;

            // GhostSlot is a package-private Record(List<ItemStack>, boolean);
            // its public getItem(int) cannot be reflectively invoked from a
            // different package unless setAccessible(true).  Use the current
            // slot-select animation index so an interchangeable material that
            // rotates (~2s) resolves to the variant the user is seeing.
            int idx = CycleLock.hoveredOr(ghostAcc.getSlotSelectTime().currentIndex());
            for (java.lang.reflect.Method m : ghost.getClass().getMethods()) {
                if (m.getReturnType() == ItemStack.class && m.getParameterCount() == 1
                        && m.getParameterTypes()[0] == int.class) {
                    m.trySetAccessible();
                    Object item = m.invoke(ghost, idx);
                    if (item instanceof ItemStack stack && !stack.isEmpty()) {
                        return stack;
                    }
                    break;
                }
            }

            // Fallback: any public no-arg accessor returning a non-empty list.
            for (java.lang.reflect.Method m : ghost.getClass().getMethods()) {
                if (m.getReturnType() == List.class && m.getParameterCount() == 0) {
                    m.trySetAccessible();
                    List<?> items = (List<?>) m.invoke(ghost);
                    if (items != null) {
                        for (Object o : items) {
                            if (o instanceof ItemStack stack && !stack.isEmpty()) {
                                return stack;
                            }
                        }
                    }
                }
            }
            return ItemStack.EMPTY;
        } catch (Exception e) {
            return ItemStack.EMPTY;
        }
    }
    /** Pure screen capture (no window needed): hovered container slot →
     *  ghost-preview ingredient → vanilla recipe-book button.  Shared by
     *  every window's anchor capture and the no-window case (first R/U). */
    public static ItemStack captureScreenTarget(AbstractContainerScreen<?> screen) {
        // The host recipe book's alternative-recipe group (the popup a grouped
        // recipe button opens) is drawn ABOVE every other screen element, so a
        // hovered variant wins over whatever sits underneath it: R/U over a
        // variant queries that recipe's object exactly like a BRBE viewer
        // object does (2026-09-13 user request).  A cursor over the group's
        // padding (no button hovered) still falls through to the chain below.
        if (screen instanceof AbstractRecipeBookScreen<?> rbs) {
            ItemStack variant = captureAlternateGroupTarget(rbs);
            if (!variant.isEmpty()) return variant;
        }

        AbstractContainerScreenAccessor acc = (AbstractContainerScreenAccessor) screen;
        Slot slot = acc.brbe$getHoveredSlot();
        if (slot != null && slot.hasItem()) {
            return slot.getItem();
        }

        // Hovering a ghost-preview ingredient slot (no real item): use the ghost
        // item, so R/U works on ghost previews too.
        if (slot != null && screen instanceof AbstractRecipeBookScreen<?> rbs) {
            ItemStack ghost = captureGhostItem(rbs, slot);
            if (!ghost.isEmpty()) return ghost;
        }

        // Hovering a vanilla recipe-book button.
        if (screen instanceof AbstractRecipeBookScreen<?> rbs) {
            RecipeBookComponent<?> book = ((AbstractRecipeBookScreenAccessor) rbs).brbe$getRecipeBookComponent();
            if (book != null && book.isVisible()) {
                RecipeBookPage page = ((RecipeBookComponentAccessor) book).getRecipeBookPage();
                if (page != null) {
                    for (RecipeButton button : ((RecipeBookPageAccessor) page).getButtons()) {
                        if (button.isHoveredOrFocused()) {
                            ItemStack stack = button.getDisplayStack();
                            if (stack != null && !stack.isEmpty()) {
                                return stack;
                            }
                        }
                    }
                }
            }
        }
        return ItemStack.EMPTY;
    }

    /** Result item of the variant under the cursor inside the HOST recipe
     *  book's alternative-recipe group, or {@code EMPTY} when the group is
     *  closed / no variant is hovered / the variant has no result item.
     *
     *  <p>The lookup goes through the group's OWN {@link RecipeCollection}:
     *  {@link #entryFor(RecipeDisplayId)} only knows the recipes BRBE's own
     *  queries indexed, and a book-only variant need not be among them.
     *
     *  <p>Hidden buttons are skipped — their hover flag is stale, because a
     *  hidden widget's {@code render} returns early and never refreshes it
     *  (the pin path guards exactly the same way). */
    private static ItemStack captureAlternateGroupTarget(AbstractRecipeBookScreen<?> rbs) {
        RecipeBookComponent<?> book =
                ((AbstractRecipeBookScreenAccessor) rbs).brbe$getRecipeBookComponent();
        if (book == null || !book.isVisible()) return ItemStack.EMPTY;
        RecipeBookPage page = ((RecipeBookComponentAccessor) book).getRecipeBookPage();
        if (page == null) return ItemStack.EMPTY;
        OverlayRecipeComponent group = ((RecipeBookPageAccessor) page).getOverlay();
        if (group == null || !group.isVisible()) return ItemStack.EMPTY;
        RecipeCollection collection = group.getRecipeCollection();
        if (collection == null) return ItemStack.EMPTY;
        for (AbstractWidget button : ((OverlayRecipeComponentAccessor) group).getRecipeButtons()) {
            if (!button.visible || !button.isHoveredOrFocused()) continue;
            if (!(button instanceof OverlayRecipeButtonAccessor oba)) continue;
            RecipeDisplayId id = oba.brbe$getRecipe();
            if (id == null) continue;
            for (RecipeDisplayEntry entry : collection.getRecipes()) {
                if (entry == null || !id.equals(entry.id())) continue;
                List<ItemStack> results = resultItemsOf(entry);
                if (!results.isEmpty()) return results.get(0);
            }
        }
        return ItemStack.EMPTY;
    }

    /** {@code entry.resultItems(...)} with the level-backed context map, falling
     *  back to the context-free call when that throws (mod displays may need a
     *  level).  Never null. */
    private static List<ItemStack> resultItemsOf(RecipeDisplayEntry entry) {
        Minecraft mc = Minecraft.getInstance();
        try {
            List<ItemStack> results = entry.resultItems(
                    mc.level == null ? null : SlotDisplayContext.fromLevel(mc.level));
            if (results != null && !results.isEmpty()) return results;
        } catch (Exception ignored) {
            // fall through to the context-free call
        }
        try {
            List<ItemStack> results = entry.resultItems(null);
            return results == null ? List.of() : results;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    public static ItemStack captureTarget(AbstractContainerScreen<?> screen) {
        for (int i = WINDOWS.size() - 1; i >= 0; i--) {
            ItemStack a = WINDOWS.get(i).captureViewerAnchors(screen);
            if (!a.isEmpty()) return a;
        }
        return captureScreenTarget(screen);
    }

    public static RecipeDisplayId capturedOverlayRecipe() {
        ViewerInstance w = topmost();
        return w != null ? w.capturedOverlayRecipe() : null;
    }

    public static RecipeCollection capturedOverlayCollection() {
        ViewerInstance w = topmost();
        return w != null ? w.capturedOverlayCollection() : null;
    }

    public static int[] capturedOverlayButtonCentre() {
        ViewerInstance w = topmost();
        return w != null ? w.capturedOverlayButtonCentre() : null;
    }

    public static RecipeDisplayEntry entryFor(RecipeDisplayId id) {
        for (int i = WINDOWS.size() - 1; i >= 0; i--) {
            RecipeDisplayEntry e = WINDOWS.get(i).entryFor(id);
            if (e != null) return e;
        }
        return null;
    }

    public static int viewerMode() {
        ViewerInstance w = topmost();
        return w != null ? w.viewerMode() : 0;
    }

    public static boolean isFurnaceMode() {
        ViewerInstance w = topmost();
        return w != null && w.isFurnaceMode();
    }

    public static boolean isStonecuttingMode() {
        ViewerInstance w = topmost();
        return w != null && w.isStonecuttingMode();
    }

    public static boolean isSmithingMode() {
        ViewerInstance w = topmost();
        return w != null && w.isSmithingMode();
    }

    public static boolean isAnvilMode() {
        ViewerInstance w = topmost();
        return w != null && w.isAnvilMode();
    }

    public static boolean isBrewingMode() {
        ViewerInstance w = topmost();
        return w != null && w.isBrewingMode();
    }

    public static boolean isGrindstoneMode() {
        ViewerInstance w = topmost();
        return w != null && w.isGrindstoneMode();
    }

    /** The window whose standalone overlay is {@code o}, or null (a host
     *  recipe book's own overlay). */
    private static ViewerInstance windowOf(OverlayRecipeComponent o) {
        for (ViewerInstance w : WINDOWS) {
            if (w.overlay == o) return w;
        }
        return null;
    }

    /** Layout mode of the window OWNING {@code o}: each window's buttons must
     *  render with its OWN category's mode — the old topmost-based lookup let
     *  a bottom window degrade to the FOCUSED window's mode / furnace state
     *  (the multi-window focus bug: partial values were computed with the
     *  topmost window's furnace state, so a bottom window lost its partial
     *  red overlays).  Falls back to the topmost window (host-book buttons). */
    public static int windowMode(OverlayRecipeComponent o) {
        ViewerInstance w = windowOf(o);
        return w != null ? w.viewerMode() : viewerMode();
    }

    public static boolean modalMaskOwnsCursor(int mx, int my) {
        for (int i = WINDOWS.size() - 1; i >= 0; i--) {
            if (WINDOWS.get(i).modalMaskOwnsCursor(mx, my)) return true;
        }
        return false;
    }

    /** The pin overlays' no-shift tooltip: the pinned recipe's detailed result
     *  tooltip.  With a query window open the topmost window renders it (its
     *  category / collection context applies); with NO window open (pins
     *  alone) it must still render — the old dispatcher stopped here silently
     *  ({@code topmost() == null}), so the pin never showed its tooltip in the
     *  pins-only state. */
    public static void renderDetailedRecipeTooltip(GuiGraphics gui,
                                                   RecipeDisplayEntry entry, RecipeDisplayId id,
                                                   int mouseX, int mouseY, int selIdx) {
        ViewerInstance w = topmost();
        if (w != null) {
            w.renderDetailedRecipeTooltip(gui, entry, id, mouseX, mouseY, selIdx);
            return;
        }
        // Windowless pass (category / collection context absent): the station
        // row falls back to categoryFor(entry), the recipe-book incompatibility
        // warning is skipped, and no preview is embedded (the pin itself is
        // the full UI).
        ViewerInstance.renderDetailedTooltipImpl(gui, entry, id, null, false, false,
                mouseX, mouseY, selIdx,
                false, PinOverlay.MODE_CRAFTING, null, null);
    }

    public static void renderDetailedRecipeTooltip(GuiGraphics gui,
                                                   RecipeDisplayEntry entry, RecipeDisplayId id,
                                                   List<?> slots, boolean craftable, boolean partial,
                                                   int mouseX, int mouseY, int selIdx) {
        ViewerInstance w = topmost();
        if (w != null) {
            w.renderDetailedRecipeTooltip(gui, entry, id, slots, craftable, partial,
                    mouseX, mouseY, selIdx);
        }
    }

    /** Close every open window (screen removal / full reset). */
    public static void close() {
        for (ViewerInstance w : new java.util.ArrayList<>(WINDOWS)) {
            w.close();
        }
        WINDOWS.clear();
        dragWindow = null;
        syncActiveFlag();
    }



    /** One query window on screen (full instance state — a screen may hold several). */
    static final class ViewerInstance {


    /** The standalone overlay box.  SlotSelectTime drives the ingredient
     *  rotation animation: an index that advances every ~1.5s (same cadence as
     *  the vanilla recipe book's time/30), so the recipe previews rotate through
     *  interchangeable materials like the ghost ingredients do. */
    private final OverlayRecipeComponent overlay =
            new OverlayRecipeComponent(
                    () -> Mth.floor(net.minecraft.util.Util.getMillis() / 1500.0D), false);

    /** Collection backing the open overlay (for partial snapshot cleanup). */
    private RecipeCollection currentCollection;

    /** Viewer-overlay recipe button hovered when R/U was pressed (anchor). */
    private AbstractWidget anchorOverlayWidget;

    /** Recipe-book button hovered when R/U was pressed (anchor + fromBook flag). */
    private RecipeButton anchorBookButton;

    /** Screen the open overlay belongs to; the overlay closes when it is removed. */
    private AbstractContainerScreen<?> ownerScreen;
    /** Whether a title-bar drag is in progress (window follows the cursor). */
    private boolean windowDragging;
    /** Cursor offset from the window's box top-left at drag start. */
    private int windowDragOffX;
    private int windowDragOffY;
    /** A left-press started on the band TITLE (browse-all toggle armed —
     *  fires on RELEASE only if the window was NOT dragged). */
    private boolean titlePressActive;
    /** The armed title press actually moved the window (drag → no toggle). */
    private boolean titleDragMoved;
    /** The current drag has produced displacement (the window actually
     *  moved) — the dragging cursor (fist) is only shown once it has. */
    private boolean windowDragMoved;
    /** This window's persistent disk entry ({@code brbe.queryviewers.json}),
     *  or null before the first {@link #syncSpec()}.  Right-click close
     *  removes it; passive closes keep it for the next restore. */
    private ViewSpec spec;

    /** Last seen pin-set version: when the recipe book's pin state changes
     *  while this window is open (A-pinning a recipe in the book behind it),
     *  {@link #refreshIfDirty()} re-evaluates ordering/state so pinned
     *  objects move to the top immediately instead of on the next reopen. */
    private int lastPinVersion = BetterRecipeBook.pinnedRecipeManager.version();

    /** Whether a BRBE fuel-cell ghost is active on the furnace-family fuel slot
     *  (removed automatically once the slot gains a real item). */
    private boolean fuelGhostActive;

    /** Last observed search-space hash (real inventory + carried, same source
     *  as the pins' refresh): the ordering / state refresh trigger — the fuel
     *  grid's owned→missing order and every recipe's craftable/partial rank
     *  must follow the inventory in real time, not only on reopen. */
    private long lastSpaceHash = -1;

    /** The turn-page buttons' position (user-shifted 3px right / 5px down). */
    /** The turn-page buttons' position — RIGHT-ALIGNED in the chrome band:
     *  the two buttons sit at the band's right end (6px edge margin,
     *  mirroring the title's left padding); the title is left-aligned at the
     *  band's left edge.  Vertical placement unchanged (the old 3px-right
     *  seam shift belonged to the left-aligned layout and no longer applies). */
    private int pageBtnX() {
        return boxLeft() + boxW - (15 + PAGE_BTN_WIDTH) - 4;
    }

    private int pageBtnY() {
        return boxTop() - PAGE_BTN_HEIGHT - 2 + PAGE_BTN_SHIFT_Y;
    }

    /** The window chrome band rect: the extended background region above
     *  the box (border + gap + turn-page-button row at its shifted position).
     *  {x, y, w, h}. */
    private int[] windowChromeRect() {
        int top = boxTop() - TITLE_BAR_H;
        return new int[] {boxLeft(), top, boxW, TITLE_BAR_H + PAGE_BTN_SHIFT_Y - 2};
    }

    /** Title-bar text: the title of the currently selected category tab —
     *  the SAME display name the category tabs draw, so plugin (JEI)
     *  categories show their proper title instead of the raw id. */
    private String titleBarTitle() {
        if (currentCategory == null) return "";
        return currentCategory.name().getString();
    }


    // ── Paging ─────────────────────────────────────────────────────────────
    // More objects than one page holds (行上限 x 列上限，配置默认 3 x 7 = 21) the
    // overlay pages: one page's objects at a time with the turn-page buttons
    // above the box.
    /** The vanilla alternative-group background sprite (also used by the paged box). */
    private final Identifier OVERLAY_RECIPE_SPRITE =
            Identifier.withDefaultNamespace("recipe_book/overlay_recipe");
    /** The query window's OWN turn-page sheet (2026-09-13 user art): a 256x256
     *  canvas holding the same 14x13 cells as RBIP's {@code
     *  rbip/recipe_book_buttons.png} (u = 0 previous / 14 next / +28 hovered,
     *  v = 0 enabled / 13 disabled) but with VERTICAL arrows — down for the
     *  previous page at u=0, up for the next at u=14.  Only this window uses
     *  it: the RBIP tab strip's own page buttons keep the original horizontal
     *  sheet.  Drawn with the raw {@code blit} (not {@code blitSprite}), so no
     *  {@code .mcmeta} is involved — the Unique Dark pack overrides it by
     *  shipping a PNG of the same path, exactly like the rest of the pack. */
    private final Identifier LEI_PAGE_BUTTONS =
            Identifier.fromNamespaceAndPath("brbe",
                    "textures/gui/sprites/recipe_book/lei_page_button.png");

    /** Full ordered recipe list of the open viewer (across all pages). */
    private List<RecipeDisplayEntry> viewerRecipes = List.of();
    /** Current page index and total page count. */
    private int viewerPage;
    private int viewerPageCount = 1;

    // ── Category tabs (BRBE's bottom-tab textures, drawn rotated -90°:
    //    the 35x27 texture displays as a 27x35 tab hanging below the box) ──
    private final Identifier UNSELECTED_BOTTOM_TAB =
            Identifier.fromNamespaceAndPath("brbe", "textures/rbip/bottom_tab.png");
    private final Identifier SELECTED_BOTTOM_TAB =
            Identifier.fromNamespaceAndPath("brbe", "textures/rbip/bottom_tab_selected.png");

    // ── Category ────────────────────────────────────────────────────────────
    /** The item queried when R/U opened the viewer (re-queried on tab switch). */
    private ItemStack queryTarget;
    /** Whether the open query was "usage" (U) rather than "result" (R). */
    private boolean queryUsage;
    /** Category whose results are currently shown. */
    private RecipeViewerCategory currentCategory;

    /** Item under the cursor in a grid category (fuel / compost / info). */
    private ItemStack gridHoverStack;
    /** The grid category owning {@link #gridHoverStack} (differs from
     *  {@code currentCategory} for browse-all's plain cells). */
    private RecipeViewerCategory gridHoverCategory;
    /** Items shown by a grid category (cached on rebuild). */
    private List<ItemStack> gridItems = List.of();

    /** Whether the currently shown category is the furnace category. */
    public boolean isFurnaceMode() {
        return currentCategory != null && "furnace".equals(currentCategory.id());
    }

    /** Whether the currently shown category is the stonecutter category. */
    public boolean isStonecuttingMode() {
        return currentCategory != null && "stonecutting".equals(currentCategory.id());
    }

    /** Whether the currently shown category is the smithing category. */
    public boolean isSmithingMode() {
        return currentCategory != null && "smithing".equals(currentCategory.id());
    }

    /** Whether the currently shown category is the anvil category. */
    public boolean isAnvilMode() {
        return currentCategory != null && "anvil".equals(currentCategory.id());
    }

    /** Whether the currently shown category is the brewing category. */
    public boolean isBrewingMode() {
        return currentCategory != null && "brewing".equals(currentCategory.id());
    }

    /** Whether the currently shown category is the grindstone category. */
    public boolean isGrindstoneMode() {
        return currentCategory != null && "grindstone".equals(currentCategory.id());
    }

    /** First visible category index of the REI-style sliding tab window (window
     *  size = {@link #visibleTabCount()}); {@code 0} when every tab fits.  The wheel over
     *  the tab strip switches the selected category and slides the window when
     *  the selection reaches an edge. */
    private int tabWindowStart;

    /** Workstation objects of the open category, shown in the viewer's left
     *  column (bottom-up; a sliding window when there are more than the object
     *  area's rows). */
    private List<ItemStack> stationColumnItems = List.of();
    /** Left-column scroll: 0 = the bottom-most window, each step slides the
     *  window one cell up. */
    private int stationScroll;

    /** Ctrl+O browse-all mode: every category tab shows its COMPLETE object
     *  pool ({@code allEntries()} / {@code allGridItems()}) instead of the
     *  query-related subset — the "house" metaphor: the query herds the
     *  related objects into the viewer's categories, Ctrl+O gathers ALL
     *  queryable objects and distributes them into their correct categories
     *  (the tabs), a second Ctrl+O drives the newly added objects back out. */
    private boolean browseAllMode;
    /** Page of the selected category before browse-all was entered. */
    private int browseAllReturnPage;
    /** The category selected before browse-all was entered (a tab that existed
     *  pre-toggle): a restore re-selects it when the current tab only exists
     *  in browse-all. */
    private RecipeViewerCategory browseAllReturnCategory;

    /** The left-column hover tooltip, deferred until the END of the overlay's
     *  own render pass: the GUI paints in call order, and
     *  {@code gui.renderTooltip(...)} renders in place — a tooltip built
     *  inside the station cell loop was covered by the cells drawn after it. */
    private List<net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent>
            pendingStationTooltip;
    private int pendingStationTooltipX;
    private int pendingStationTooltipY;
    private Identifier pendingStationTooltipStyle;

    /** The category-tab hover tooltip, deferred to the end of the overlay's
     *  render pass exactly like the station column's (the hover tooltip is
     *  built in the tabs-behind pass, while the box itself is painted after
     *  it — an in-place render would be covered). */
    private List<net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent>
            pendingTabTooltip;
    private int pendingTabTooltipX;
    private int pendingTabTooltipY;
    private Identifier pendingTabTooltipStyle;

    /** Fixed box layout for the open viewer (reused when switching tabs). */
    private int boxX;
    private int boxY;
    private int boxW;
    private int boxH;
    /** Screen Y of the tab strip (the box bottom), fixed on open so switching
     *  tabs never makes the tabs jump vertically when the box height changes. */
    private int bottomAnchor;
    /** Pinned CENTRE of the first row's first object.  Initialised from the
     *  pointer on open, then FOLLOWS the actual centre after every layout
     *  (the band limit included): rebuilds start from the settled
     *  position, so the interface never snaps back to a pre-adjustment spot. */
    private int anchorScreenX;
    private int anchorScreenY;

    /** Opening-order value of the open viewer, shared with pin overlays for
     *  z-order stacking (-1 while closed). */
    private int viewerZ = -1;

    public boolean isActive() {
        return RecipeViewerIndex.isViewerActive();
    }

    /** The viewer's z (opening order) while open, or -1 when closed. */
    public int viewerZ() {
        return isActive() ? viewerZ : -1;
    }

    /** The viewer's full on-screen region (box plus the category tabs hanging
     *  below it) in screen coordinates, or null when the viewer is closed.
     *  JEI's {@code IGlobalGuiHandler.getGuiExtraAreas} keeps its ingredient
     *  list / recipe area out of this region. */
    public Rect2i exclusionArea() {
        if (!isActive()) return null;
        // Over-inclusive single rect (box + workstation column), extended up
        // through the title-bar band — for JEI to keep out of; the drawn band
        // itself only spans the box width (see {@link #windowChromeRect}).
        return new Rect2i(panelLeft(), boxTop() - TITLE_BAR_H,
                boxW + STATION_COL_WIDTH,
                boxH + TAB_OVERHANG + TITLE_BAR_H + PAGE_BTN_SHIFT_Y - 2);
    }

    /** Whether the point lies on the viewer's own region — everything the
     *  viewer actually draws: the box at full size, the category tabs below
     *  it, the window chrome (title bar / page-button strip) above it, and
     *  the left workstation panel TRIMMED to its content (see
     *  {@link #stationColumnPanelRect}).  The empty strip above a trimmed
     *  panel (fewer stations than the object area's rows) is background: it
     *  is not part of the viewer, so clicking there closes the viewer
     *  ({@link #inBox}) and hovering falls through to the underlying screen.
     *  {@link #exclusionArea()} stays over-inclusive (a single rect for JEI
     *  to keep out of — avoiding slightly more is harmless). */
    public boolean contains(double mx, double my) {
        if (!isActive()) return false;
        // The window chrome (title bar / close button) is always window-owned,
        // independent of the left panel's content (an empty station column
        // must not make the title bar fall through).
        int[] chr = windowChromeRect();
        if (inside(mx, my, chr[0], chr[1], chr[2], chr[3])) return true;
        // Left workstation panel, attached outside the box's left edge.  The
        // DRAWN panel is STATION_COL_WIDTH + 4 wide (see
        // drawStationColumnSurfaces) — block the whole drawn slab.
        if (mx >= panelLeft() && mx < panelLeft() + STATION_COL_WIDTH + 4) {
            if (stationColumnItems.isEmpty()) return false;
            int shown = Math.min(stationColumnItems.size(), stationViewRows());
            int[] rect = stationColumnPanelRect(shown);
            return my >= rect[0] && my < rect[0] + rect[1];
        }
        // Box + the bottom tab strip: the tabs hang from the box bottom and
        // span the full strip (tabTop() .. tabTop() + TAB_HEIGHT) — the whole
        // strip is window-owned, not just the drawn tab tops.
        return mx >= boxX && mx < boxX + boxW
                && my >= boxY && my < tabTop() + TAB_HEIGHT;
    }

    /** The open popup's on-screen region (its hit volume = texture bounds),
     *  or null when no popup is open.  JEI keeps its ingredient list / recipe
     *  area out of the exact same rect the popup's hit test uses. */
    public Rect2i popupExclusionArea() {
        if (!isActive() || hoverPopupField == null) return null;
        PopupGeometry geometry = popupGeometry(hoverPopupField);
        return new Rect2i(geometry.x, geometry.y, geometry.w, geometry.h);
    }

    /** Close the viewer when its host screen is being removed. */
    public void onScreenClosed(AbstractContainerScreen<?> screen) {
        if (ownerScreen == screen) {
            close();
        }
    }

    /** R/U / ESC / O handling.  Returns true when the event was consumed. */
    public boolean keyPressed(KeyEvent event, AbstractContainerScreen<?> screen) {
        if (event.isEscape()) {
            // ESC closes this query viewer (mirrors the static entry: the
            // window closes ONLY via ESC / right-click) — pin overlays stay.
            if (!isActive()) return false;
            RecipeViewerOverlay.close();
            return true;
        }

        if (!BetterRecipeBook.config.recipeViewerEnabled) return false;

        boolean viewRecipe = ClientCompat.matches(BetterRecipeBook.RECIPE_VIEW_MAPPING,
                event.key(), event.scancode(), event.modifiers());
        boolean viewUsage = ClientCompat.matches(BetterRecipeBook.USAGE_VIEW_MAPPING,
                event.key(), event.scancode(), event.modifiers());
        if (viewRecipe || viewUsage) {
            return open(screen, viewUsage);
        }

        // Modal window: the pin key stays delegated at the keyboard level
        // (KeyboardHandlerMixin calls PinOverlayManager.handleKeyPressed right
        // after this); every OTHER unhandled key is consumed only at the SCREEN
        // level (the screen keyPressed mixin guards), so global keys (F2
        // screenshot, F3, F11 …) which the KeyboardHandler processes before the
        // screen dispatch keep working while the query window is open.
        return false;
    }

    /** Click handling while the viewer is up.  Returns true when consumed. */
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick,
                                       AbstractContainerScreen<?> screen) {
        if (!isActive()) return false;
        // NEW RULE (2026-09-02): right-click ANYWHERE in the window region —
        // title band, box, tab strip, workstation column — closes the window.
        // The close gesture is no longer confined to the extension band; it
        // runs first so nothing inside the window can swallow it.
        if (event.button() == 1 && contains(event.x(), event.y())) {
            close(true);
            return true;
        }
        // Window chrome first: ✕ closes, any other band press starts a drag —
        // the band owns itself, so it wins over the button/box hit tests
        // below (the turn-page buttons inside the band fall through to their
        // own handler further down).
        if (handleWindowChromeClick(event)) {
            return true;
        }
        // The popup layer is a hard modal: while it is open, every click is
        // claimed by it — inside the popup it inherits the hovered button's
        // full click (placing the recipe, left button only), outside it is
        // swallowed so nothing underneath (other buttons, the container) ever
        // receives it.
        if (RecipePopupLayer.isActive()) {
            if (RecipePopupLayer.contains(event.x(), event.y())
                    && event.button() == 0
                    && RecipePopupLayer.button() instanceof OverlayRecipeButtonAccessor oba) {
                placeRecipe(event, screen, oba.brbe$getRecipe(),
                        oba.brbe$getOuterComponent().getRecipeCollection());
            }
            return true;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return false;

        // Fuel category cells: the grid has no recipe buttons — a left-click on
        // a fuel cell quick-fills the furnace-family fuel slot (real move or
        // ghost preview).  Handled before the overlay hit-test so it wins over
        // the box-background swallow.
        if (isGridMode() && handleFuelCellClick(event, screen)) {
            return true;
        }

        // The fuel category has no recipe buttons and is not clickable: skip
        // the overlay button hit-test (which may still hold the previous
        // category's buttons) so a click on a fuel cell keeps the viewer open.
        // Browse-all always has buttons (its currentCategory may be a grid
        // category — the button scan is what feeds them).
        boolean hitButton = isGridMode() ? false : overlay.mouseClicked(event, doubleClick);
        if (hitButton) {
            // Place the clicked recipe (with a ghost preview for missing
            // materials) only when its station matches the open screen — a
            // crafting recipe clicked inside a furnace must not fill items or
            // ghost slots of the wrong station.  Non-matching clicks are only
            // consumed.
            placeRecipe(event, screen, overlay.getLastRecipeClicked(),
                    overlay.getRecipeCollection());
            return true;
        }

        // Click on the left workstation column first: it queries that object's
        // uses, so it must win over the box-background swallow.
        if (handleStationColumnClick(event)) {
            return true;
        }

        // Click on the box background: keep the viewer open.
        if (inBox(event)) {
            return true;
        }

        // Category tabs along the box bottom switch the viewer category.
        if (handleCategoryTabClick(event)) {
            return true;
        }

        // The viewer's own turn-page buttons (inside the title-bar band).
        if (handlePageButtonClick(event)) {
            return true;
        }

        // Clicking the recipe book's turn-page buttons while the viewer is up:
        // keep the viewer open and swallow the click (the page turn itself is
        // blocked separately in the scrollable-pages mixin).
        if (isPageTurnButton(event, screen)) {
            return true;
        }

        // Region fallback: ANY click inside the window's own footprint — the
        // category tab strip, the workstation panel (non-cell parts included),
        // the chrome band edges, gaps between drawn widgets — is window-owned
        // and swallowed; only clicks truly OUTSIDE the window fall through to
        // the desktop below.
        if (contains(event.x(), event.y())) {
            return true;
        }

        // Clicks outside the window are NOT a close action any more (only
        // right-click on the window closes): fall through so the
        // desktop below handles the click.
        return false;
    }

    /** Place {@code id} from {@code collection} into the open recipe-book
     *  screen (with a ghost preview for missing materials), guarded by station
     *  matching — a crafting recipe clicked inside a furnace must not fill
     *  items or ghost slots of the wrong station.  Public so pin overlays can
     *  inherit the same click behaviour. */
    /** Left-click on a recipe object (viewer button or its preview popup):
     *  <b>transfer</b> a fully-craftable object's ingredients into the open
     *  workstation (ViewerTransfer engine — server-verified virtual drags /
     *  vanilla recipe-book placement).  Partial and uncraftable objects are
     *  neither transferred nor ghosted; a workstation mismatch transfers
     *  nothing (the click is consumed either way).  Public so pin overlays can
     *  inherit the same click behaviour. */
    public boolean placeRecipe(MouseButtonEvent event, AbstractContainerScreen<?> screen,
                               RecipeDisplayId id, RecipeCollection collection) {
        // Click feedback plays for every consumed click (even one that cannot
        // transfer anything — the invalid click must still sound).
        Minecraft mc = Minecraft.getInstance();
        if (mc.getSoundManager() != null) {
            AbstractWidget.playButtonClickSound(mc.getSoundManager());
        }
        if (id == null || collection == null || currentCategory == null) return false;
        if (!ViewerTransfer.isFullyCraftable(collection, id)) {
            // 残缺/不可合成对象：不转移、不补幽灵——仅消费点击。
            return true;
        }
        RecipeDisplayEntry entry = entryFor(id);
        return ViewerTransfer.transfer(currentCategory.id(), entry, id, collection, screen,
                event.hasShiftDown());
    }

    /** Whether the click lands on the open recipe book's turn-page buttons. */
    private boolean isPageTurnButton(MouseButtonEvent event, AbstractContainerScreen<?> screen) {
        if (!(screen instanceof AbstractRecipeBookScreen<?> rbs)) return false;
        if (event.button() != 0) return false;
        RecipeBookComponent<?> book = ((AbstractRecipeBookScreenAccessor) rbs).brbe$getRecipeBookComponent();
        if (book == null) return false;
        RecipeBookPage page = ((RecipeBookComponentAccessor) book).getRecipeBookPage();
        if (page == null) return false;
        ImageButton fwd = ((RecipeBookPageAccessor) page).getForwardButton();
        ImageButton back = ((RecipeBookPageAccessor) page).getBackButton();
        return (fwd != null && fwd.isMouseOver(event.x(), event.y()))
                || (back != null && back.isMouseOver(event.x(), event.y()));
    }

    /** Scroll over the overlay flips its page.  Returns true when consumed. */
    public boolean mouseScrolled(double mouseX, double mouseY, double vertical) {
        // 锁定键+滚轮：逐格翻动**指针下那一件**折叠物品（用户 2026-09-13 诉求 2）。
        // 物品由各前端在绘制时用 CycleLock.claim 登记——查询窗口对象按钮、LEI
        // 预览界面（Shift 弹窗）里的槽位、pin 里的槽位都算；预览/pin 画在窗口
        // 之上，所以指针在它们上面时登记的自然是它们自己的槽位（诉求 1：指着
        // 预览时滚轮翻动的就是预览里的那件物品）。没有物品被指着时不消费滚轮。
        if (vertical != 0 && CycleLock.isDown()
                && (isActive() || PinOverlayManager.hasPins())) {
            if (PinOverlayManager.topInteractivePin(mouseX, mouseY) != null
                    || RecipePopupLayer.contains(mouseX, mouseY)
                    || contains(mouseX, mouseY)) {
                if (CycleLock.step(vertical)) {
                    return true;
                }
            }
        }
        // A pin under the cursor swallows the scroll (no page flip underneath).
        if (PinOverlayManager.handleMouseScrolled(mouseX, mouseY, vertical)) {
            return true;
        }
        // The hard-modal popup swallows the scroll too (a page flip would
        // rebuild the buttons and destroy the open popup).
        if (RecipePopupLayer.isActive()) {
            return true;
        }
        // Category-tab strip first: folded categories page with the wheel.
        if (mouseScrolledTabs(mouseX, mouseY, vertical)) {
            return true;
        }
        // Left workstation column: slide its window (only when more stations
        // than visible rows).
        if (handleStationColumnScroll(mouseX, mouseY, vertical)) {
            return true;
        }
        // Modal-window fix: a non-paged open viewer must still consume the
        // scroll (the desktop underneath is inert), just without a page flip.
        // Non-paged viewers have nothing to flip: the wheel over the window is
        // still window-owned (no leakage to the desktop), elsewhere it falls
        // through.
        if (!isPaged()) return contains(mouseX, mouseY);
        if (vertical == 0) return false;
        // Scroll zone: the box plus the turn-page button strip above it.
        if (overScrollZone(mouseX, mouseY)) {
            // 配方区的翻页方向由配置决定（默认「自然方向」＝上滚往后翻页）。
            int delta = (vertical > 0) == naturalPageDirection() ? 1 : -1;
            int next = viewerPage + delta;
            if (BetterRecipeBook.config.scrolling.scrollAround && viewerPageCount > 1) {
                // Wrap around: a scroll past the last page returns to the first
                // (and past the first goes to the last), matching the recipe
                // area's scrollAround behaviour.
                next = (next % viewerPageCount + viewerPageCount) % viewerPageCount;
            }
            if (next >= 0 && next < viewerPageCount && ownerScreen != null) {
                viewerPage = next;
                ClientCompat.playPageFlipSound(Minecraft.getInstance());
                if (isGridMode()) {
                    // A grid category has no overlay buttons: showPage is a
                    // no-op for it, so re-fit the box to the new page here
                    // (empty rows/columns are dropped).
                    fitGridBoxToPage();
                }
                showPage(ownerScreen, boxLeft(), boxTop(),
                        pageCols() * 25 + 8, pageRows() * 25 + 8);
                syncSpec();
            }
            return true;
        }
        // Desktop-window semantics: the wheel over the WINDOW's own region is
        // consumed by it; outside the window the desktop below scrolls
        // normally (the query window is not a full-screen focus layer).
        return contains(mouseX, mouseY);
    }

    /** Whether the cursor is over the box or the page-button strip above it. */
    private boolean overScrollZone(double mouseX, double mouseY) {
        int bx = boxLeft();
        int by = boxTop();
        if (inside(mouseX, mouseY, bx, by, pageCols() * 25 + 8, pageRows() * 25 + 8)) {
            return true;
        }
        int btnY = pageBtnY();
        int btnW = PAGE_BTN_WIDTH * 2 + 15;
        return inside(mouseX, mouseY, pageBtnX(), btnY, btnW, PAGE_BTN_HEIGHT);
    }

    private int boxLeft() {
        // The fuel grid has no OverlayRecipeComponent (no recipe buttons), so
        // its box lives in the static boxX/boxY fields, not the overlay.
        return isGridMode() ? boxX : ((OverlayRecipeComponentAccessor) overlay).getX();
    }

    /** The panel's left edge: one object-grid pitch left of the box — the box
     *  plus the left workstation column ("grid column -1") attached OUTSIDE it
     *  (the object area / tabs / page buttons keep their layout; the station
     *  column is a grid column appended on the box's left). */
    private int panelLeft() {
        return boxLeft() - STATION_COL_WIDTH;
    }

    private int boxTop() {
        return isGridMode() ? boxY : ((OverlayRecipeComponentAccessor) overlay).getY();
    }

    /** Clicking the viewer's own turn-page buttons flips the page (or wraps when
     *  scroll-around is enabled); Ctrl+click jumps straight to the first / last
     *  page (the same edge-jump the recipe book's own turn buttons do). */
    private boolean handlePageButtonClick(MouseButtonEvent event) {
        if (!isPaged() || event.button() != 0) return false;
        int bx = pageBtnX();
        int by = boxTop();
        int btnY = by - PAGE_BTN_HEIGHT - 2 + PAGE_BTN_SHIFT_Y;
        int mx = Mth.floor(event.x());
        int my = Mth.floor(event.y());
        Minecraft mc = Minecraft.getInstance();
        boolean wrap = scrollWrap();
        if (inside(mx, my, bx, btnY, PAGE_BTN_WIDTH, PAGE_BTN_HEIGHT)) {
            int prev = ClientCompat.isControlDown()
                    ? 0
                    : (wrap
                            ? (viewerPage - 1 + viewerPageCount) % viewerPageCount
                            : Math.max(0, viewerPage - 1));
            if (prev != viewerPage && ownerScreen != null) {
                viewerPage = prev;
                ClientCompat.playPageFlipSound(mc);
                if (isGridMode()) {
                    fitGridBoxToPage();
                }
                showPage(ownerScreen, bx, by, pageCols() * 25 + 8, pageRows() * 25 + 8);
                syncSpec();
            }
            return true;
        }
        if (inside(mx, my, bx + 15, btnY, PAGE_BTN_WIDTH, PAGE_BTN_HEIGHT)) {
            int next = ClientCompat.isControlDown()
                    ? viewerPageCount - 1
                    : (wrap
                            ? (viewerPage + 1) % viewerPageCount
                            : Math.min(viewerPageCount - 1, viewerPage + 1));
            if (next != viewerPage && ownerScreen != null) {
                viewerPage = next;
                ClientCompat.playPageFlipSound(mc);
                if (isGridMode()) {
                    fitGridBoxToPage();
                }
                showPage(ownerScreen, bx, by, pageCols() * 25 + 8, pageRows() * 25 + 8);
                syncSpec();
            }
            return true;
        }
        return false;
    }

    /** Draw the overlay on the container's top render stratum. */
    public void render(GuiGraphics gui, int mouseX, int mouseY, float delta) {
        if (!isActive()) return;
        // A BRBE fuel ghost lives only while its fuel slot is empty (the same
        // gate vanilla's FurnaceRecipeBookComponent applies) — once a real item
        // lands in the slot the ghost is removed this frame.
        syncFuelGhost();
        // SELF-HEALING GEOMETRY: the vanilla OverlayRecipeComponent's own
        // init/auto-shift and the position mixin can leave the overlay's
        // x/y and button positions out of sync with the window fields
        // (boxX/boxY/boxW/boxH) — the panel/chrome draws from the fields'
        // derived values while the vanilla-drawn content would follow the
        // overlay — the "flying elements" symptom.  Re-pin the overlay and
        // re-flow the buttons from the FIELDS every frame BEFORE drawing, so
        // whatever moved them gets corrected; the drag handler moves both
        // consistently, so the drag is unaffected.
        if (!isGridMode()) {
            OverlayRecipeComponentAccessor acc0 = (OverlayRecipeComponentAccessor) overlay;
            acc0.setX(boxX);
            acc0.setY(boxY);
            int cols = Math.max(1, Math.min(pageCols(), acc0.getRecipeButtons().size()));
            List<AbstractWidget> btns0 = acc0.getRecipeButtons();
            for (int i = 0; i < btns0.size(); i++) {
                int row = i / cols;
                btns0.get(i).setX(boxX + 4 + (i % cols) * 25);
                btns0.get(i).setY(boxY + boxH - 28 - row * 25);
            }
        }
        // Desktop-window semantics: the window renders on top of a fully
        // interactive desktop (no scrim, no dead cursor) — the box's
        // background blits below stay untouched (its top border line remains
        // the buttons' 190602 reference); the title/✕ float in the
        // turn-page buttons' row (drawn by drawTitleBar).
        // The popup layer opens only while Shift is held (no hover-open, no
        // Shift magnify any more): the popup under the cursor behaves like a
        // modal — while the cursor is inside it, it stays open and blocks every
        // button behind it.  Only when the cursor leaves it (Shift still held)
        // does a button under the cursor trigger its own popup.
        hoveredViewerButton = null;
        hoverPopupField = null;
        boolean shift = ClientCompat.isShiftDown();
        if (!isGridMode()) {
            List<AbstractWidget> buttons =
                    ((OverlayRecipeComponentAccessor) overlay).getRecipeButtons();
            // First pass: the popup already open under the cursor keeps it —
            // while the cursor is inside the open preview's hit volume (its own
            // texture bounds), no other object's trigger area may steal it
            // (overlapping popup-bounds triggers used to switch the preview
            // while the cursor never left it).  Only when the cursor leaves the
            // preview does an object's trigger area open its own popup.
            if (shift) {
                AbstractWidget open = RecipePopupLayer.button();
                if (open != null && buttons.contains(open)
                        && RecipePopupLayer.contains(mouseX, mouseY)) {
                    hoverPopupField = open;
                } else {
                    for (AbstractWidget w : buttons) {
                        if (w instanceof OverlayRecipeButtonAccessor oba
                                && RecipeViewerIndex.isViewerCollection(
                                        oba.brbe$getOuterComponent().getRecipeCollection())
                                && isTriggerArea(w, mouseX, mouseY)) {
                            hoverPopupField = w;
                        }
                    }
                }
            }
            // Second pass: the hovered viewer button (any state — it also feeds
            // the always-detailed button tooltip); with Shift it opens the popup.
            // The test uses the ACTUAL cursor passed to this render (isMouseOver,
            // a plain rect test): isHoveredOrFocused() reads a per-widget field
            // refreshed only during the widget's own render, so with a far-away
            // cursor (-1,-1, used when a pin covers the cursor) it still carries
            // the previous frame's real-mouse hover — which used to render the
            // query object's tooltip at (-1,-1) (the screen's top-left) for one
            // frame right after the pin hotkey created a pin.
            for (AbstractWidget w : buttons) {
                if (w instanceof OverlayRecipeButtonAccessor oba
                        && RecipeViewerIndex.isViewerCollection(
                                oba.brbe$getOuterComponent().getRecipeCollection())
                        && w.isMouseOver(mouseX, mouseY)) {
                    hoveredViewerButton = w;
                    if (shift && hoverPopupField == null) {
                        hoverPopupField = w;
                    }
                    break;
                }
            }
        }
        // (The independent popup layer is driven by the manager once per
        // frame, after every window rendered — see RecipeViewerOverlay.render.)
        // The grid categories (fuel / compost / info) render a standalone item
        // grid (they are info sheets, not recipes — no buttons).
        if (isGridMode()) {
            drawCategoryTabs(gui, mouseX, mouseY, true);
            drawItemGrid(gui, mouseX, mouseY);
            drawPageControls(gui, mouseX, mouseY);
            drawCategoryTabs(gui, mouseX, mouseY, false);
            // The left workstation column (bottom-up, queryable objects) is
            // attached OUTSIDE the box's left edge and draws above the panel
            // background.
            drawStationColumn(gui, mouseX, mouseY);
            drawTitleBar(gui, mouseX, mouseY);
            renderTooltip(gui, mouseX, mouseY);
            flushStationTooltip(gui);
            flushTabTooltip(gui);
            return;
        }
        // Unselected tabs are drawn behind the box so the box's container UI
        // covers their top edge (only the bottom nub shows), mirroring the
        // vanilla creative inventory; the selected tab is redrawn on top.
        if (isPaged()) {
            // The vanilla overlay lays out at most 5 columns, so the paged box
            // is drawn entirely here: background, buttons, then the page
            // controls.  Category tabs and the hovered (zoomed) button are
            // redrawn last (tabs below the box, hover button above everything).
            drawCategoryTabs(gui, mouseX, mouseY, true);
            OverlayRecipeComponentAccessor acc = (OverlayRecipeComponentAccessor) overlay;
            int bx = boxLeft();
            int by = acc.getY();
            // THE extension (the original goal): one continuous panel, the
            // box's background drawn 21px taller — the sprite's top border
            // moves up with it; NOTHING else is drawn (exports no seam).
            ClientCompat.blitSprite(gui, OVERLAY_RECIPE_SPRITE, bx, by - TITLE_BAR_H,
                    boxW, boxH + TITLE_BAR_H);
            List<AbstractWidget> buttons = acc.getRecipeButtons();
            for (AbstractWidget w : buttons) {
                w.render(gui, mouseX, mouseY, delta);
            }
            drawViewerPinMarkers(gui, buttons);
            drawEmptyRowFillers(gui);
            drawPageControls(gui, mouseX, mouseY);
        } else {
            drawCategoryTabs(gui, mouseX, mouseY, true);
            // Draw the background at the widened box width (the extra columns
            // hold the tab strip), then the buttons at their re-flowed
            // pageCols()-column positions (see showPage).  vanilla's render
            // shrink-wraps the background to the recipe columns, which would
            // leave the widened tabs floating past the box edge.
            OverlayRecipeComponentAccessor acc = (OverlayRecipeComponentAccessor) overlay;
            int bx = boxLeft();
            int by = acc.getY();
            // THE extension (the original goal): one continuous panel, the
            // box's background drawn 21px taller — the sprite's top border
            // moves up with it; NOTHING else is drawn (exports no seam).
            ClientCompat.blitSprite(gui, OVERLAY_RECIPE_SPRITE, bx, by - TITLE_BAR_H,
                    boxW, boxH + TITLE_BAR_H);
            List<AbstractWidget> buttons = acc.getRecipeButtons();
            for (AbstractWidget w : buttons) {
                w.render(gui, mouseX, mouseY, delta);
            }
            drawViewerPinMarkers(gui, buttons);
            drawEmptyRowFillers(gui);
            drawPageControls(gui, mouseX, mouseY);
        }
        drawCategoryTabs(gui, mouseX, mouseY, false);
        // The left workstation column (bottom-up, queryable objects) is
        // attached OUTSIDE the box's left edge and draws above the panel
        // background.
        drawStationColumn(gui, mouseX, mouseY);
        // The title bar (extended background band): title text + ✕ close —
        // drawn after the panel background and the column surface, before the
        // transient popup/tooltip layers.
        drawTitleBar(gui, mouseX, mouseY);
        // The independent popup layer paints on top of everything (tabs and the
        // hovered button), then the viewer's tooltip (top-most) — the tooltip
        // is rendered here, not by the render RETURN hook which
        // skips the viewer instance.
        RecipePopupLayer.render(gui, delta);
        renderTooltip(gui, mouseX, mouseY);
        flushStationTooltip(gui);
        flushTabTooltip(gui);
    }

    /** 配方书 pin 的配方对象：在查询 viewer 的对象按钮左上角绘制 pin 贴图。
     *  按钮顺序与 {@link #showPage} 的排布一致（按钮 i ↔ 当前页第 i 条
     *  {@code viewerRecipes} 条目），pin 判定走与配方书相同的稳定 key。 */
    private void drawViewerPinMarkers(GuiGraphics gui, List<AbstractWidget> buttons) {
        if (buttons.isEmpty() || viewerRecipes.isEmpty()) return;
        int pageStart = viewerPage * pageSize();
        int count = Math.min(buttons.size(), viewerRecipes.size() - pageStart);
        if (count <= 0) return;
        for (int i = 0; i < count; i++) {
            RecipeDisplayEntry entry = viewerRecipes.get(pageStart + i);
            if (entry != null && BetterRecipeBook.pinnedRecipeManager.isPinnedEntry(entry)) {
                AbstractWidget button = buttons.get(i);
                ClientCompat.blitSprite(gui, BRBTextures.RECIPE_BOOK_PIN_SPRITE,
                        button.getX() - 4, button.getY() - 4, 32, 32);
            }
        }
    }

    /** Fill the current page's EMPTY cells with EMPTY placeholder objects:
     *  the box may be wider than the object columns (the tab strip widens it
     *  to fit up to {@link #visibleTabCount()} tabs) and a partially-filled row leaves
     *  trailing cells — the mechanism fills every empty cell of the box's
     *  content rows WITHOUT adding rows/columns (the box itself is never
     *  grown).  The placeholder face is HARD-CODED per the row's RIGHTMOST
     *  real object's state, mirroring {@code PopupRenderer.renderBaseButton} —
     *  the three scenarios: craftable → enabled face, partial → enabled face
     *  + the code-composited red overlay (0x60FF3333), uncraftable → disabled
     *  face.  Grid categories (fuel / compost / info) have no RECIPE state, but
     *  the FUEL grid does have a per-cell state (present → enabled face,
     *  missing fuel → disabled face, same as {@link #drawItemGrid}), so the
     *  placeholder follows the row's rightmost real CELL there too — a row
     *  ending on a missing fuel keeps its disabled face into the empty columns
     *  (user 2026-09-13: the placeholders used to be hard-enabled, i.e. the
     *  "craftable" texture, even when the row's rightmost object was a missing
     *  fuel).  Pure decoration: not clickable, no hover, no tooltip. */
    private void drawEmptyRowFillers(GuiGraphics gui) {
        int start = viewerPage * pageSize();
        int count;
        if (isGridMode()) {
            count = Math.min(pageSize(), gridItems.size() - start);
        } else {
            count = Math.min(pageSize(), viewerRecipes.size() - start);
        }
        if (count <= 0) return;
        // Content columns (mirror of fitBoxToPage) vs the box-wide columns:
        // the tab strip can widen the box WITHOUT adding object columns —
        // those tab-created empty columns belong to the filled area too.
        int columns = Math.max(1, Math.min(pageCols(), count));
        int rows = (count + columns - 1) / columns;
        int colsFill = Math.max(columns, (boxW - 8) / 25);
        List<AbstractWidget> buttons = isGridMode() ? List.of()
                : ((OverlayRecipeComponentAccessor) overlay).getRecipeButtons();
        // The grid cells' own state source (see drawItemGrid): only the fuel
        // category distinguishes present/missing objects; compost / info grids
        // are always "owned" (plain enabled face) exactly like there.
        Map<Item, Integer> gridFuelCounts = isGridMode() && currentCategory != null
                && currentCategory.isFuelCategory()
                ? PartialCraftingUtil.searchSpaceItemCounts() : null;
        for (int r = 0; r < rows; r++) {
            int inRow = Math.min(columns, count - r * columns);
            if (inRow <= 0) continue;
            if (isGridMode()) {
                int lastIdx = Math.min((r + 1) * columns, count) - 1;
                boolean owned = gridFuelCounts == null || (lastIdx >= 0 && lastIdx < count
                        && gridFuelCounts.containsKey(gridItems.get(start + lastIdx).getItem()));
                Identifier face = BRBTextures.RECIPE_BOOK_PLAIN_OVERLAY_SPRITE.get(owned, false);
                for (int c = inRow; c < colsFill; c++) {
                    int gx = boxX + 4 + c * 25;
                    int gy = boxY + boxH - 28 - r * 25;
                    ClientCompat.blitSprite(gui, face, gx, gy, 24, 24);
                }
                continue;
            }
            // The row's RIGHTMOST real object (the buttons list mirrors the
            // page order): its state picks the placeholder face.
            int lastIdx = Math.min((r + 1) * columns, count) - 1;
            boolean enabled = false;
            boolean partial = false;
            if (lastIdx >= 0 && lastIdx < buttons.size()
                    && buttons.get(lastIdx) instanceof OverlayRecipeButtonAccessor oba) {
                RecipeCollection col = oba.brbe$getOuterComponent().getRecipeCollection();
                boolean craftable = oba.brbe$getCraftable();
                // Mirror of the button mixin's computePartial viewer branch —
                // NOTE the partial check must NOT gate on !craftable: the
                // viewer's prepareForViewer adds partial recipes to the
                // collection's craftable set, so a partial button reports
                // isCraftable=true (the gating was the placeholder bug: the
                // partial row fell back to the plain craftable face).
                partial = viewerMode() != PinOverlay.MODE_FURNACE
                        && (RecipeViewerIndex.isViewerPartial(col, oba.brbe$getRecipe())
                                || PartialCraftingUtil.isPartiallyCraftableEvenIfStale(
                                        col, oba.brbe$getRecipe()));
                enabled = craftable || partial;
            }
            Identifier face = (viewerMode() == PinOverlay.MODE_FURNACE
                    ? BRBTextures.RECIPE_BOOK_PLAIN_OVERLAY_SPRITE
                    : BRBTextures.RECIPE_BOOK_CRAFTING_OVERLAY_SPRITE)
                    .get(enabled, false);
            for (int c = inRow; c < colsFill; c++) {
                int gx = boxX + 4 + c * 25;
                int gy = boxY + boxH - 28 - r * 25;
                ClientCompat.blitSprite(gui, face, gx, gy, 24, 24);
                if (partial) {
                    gui.fill(gx + 1, gy + 1, gx + 23, gy + 23, 0x60FF3333);
                }
            }
        }
    }

    /** Whether the currently shown category is a standalone grid category
     *  (fuel / compost / info): no recipe buttons, a cell grid instead. */
    private boolean isGridMode() {
        return currentCategory != null && currentCategory.isGridCategory();
    }

    /** Draw a grid category's standalone item grid: plain-overlay cells with
     *  a 16px item icon each; the hovered cell switches to the highlighted
     *  overlay (no zoom). */
    /** The fuel category's craftable/uncraftable states: a fuel present in the
     *  search space (real inventory) draws the craftable texture face, missing
     *  fuels draw the uncraftable face — same states as the recipe buttons.
     *  Hovered cells keep their own state's highlighted face (the fuel cells
     *  are clickable: see {@link #handleFuelCellClick}). */
    private void drawItemGrid(GuiGraphics gui, int mouseX, int mouseY) {
        if (gridItems.isEmpty()) return;
        ClientCompat.blitSprite(gui, OVERLAY_RECIPE_SPRITE, boxLeft(), boxY - TITLE_BAR_H,
                boxW, boxH + TITLE_BAR_H);
        // Rows grow upward: row 0 sits at the box bottom (against the tab
        // strip); the box was sized to this page's rows/columns by
        // fitGridBoxToPage, so empty rows/columns are already dropped.
        int start = viewerPage * pageSize();
        int end = Math.min(start + pageSize(), gridItems.size());
        int columns = Math.max(1, Math.min(pageCols(), end - start));
        gridHoverStack = null;
        gridHoverCategory = currentCategory;
        var fuelCounts = currentCategory.isFuelCategory()
                ? PartialCraftingUtil.searchSpaceItemCounts() : null;
        for (int i = start; i < end; i++) {
            int idx = i - start;
            int row = idx / columns;
            int gx = boxX + 4 + (idx % columns) * 25;
            int gy = boxY + boxH - 28 - row * 25;
            boolean hovered = inside(mouseX, mouseY, gx, gy, 24, 24);
            // The hovered cell swaps to the highlighted overlay sprite
            // (the query viewer's objects highlight on non-Shift hover).
            boolean owned = fuelCounts == null || fuelCounts.containsKey(gridItems.get(i).getItem());
            Identifier sprite = BRBTextures.RECIPE_BOOK_PLAIN_OVERLAY_SPRITE.get(owned, hovered);
            ClientCompat.blitSprite(gui, sprite, gx, gy, 24, 24);
            gui.renderItem(gridItems.get(i), gx + 4, gy + 4);
            if (hovered) {
                gridHoverStack = gridItems.get(i);
                gui.requestCursor(com.mojang.blaze3d.platform.cursor.CursorTypes.POINTING_HAND);
            }
        }
        // Row fillers: the page's trailing cells of a partially-filled row.
        drawEmptyRowFillers(gui);
    }

    /** Fuel-cell hit test (same geometry as {@link #drawItemGrid}); only the
     *  fuel category's cells are interactive. */
    private ItemStack fuelCellAt(int mx, int my) {
        if (gridItems.isEmpty() || currentCategory == null || !currentCategory.isFuelCategory()) {
            return ItemStack.EMPTY;
        }
        int start = viewerPage * pageSize();
        int end = Math.min(start + pageSize(), gridItems.size());
        int columns = Math.max(1, Math.min(pageCols(), end - start));
        for (int i = start; i < end; i++) {
            int idx = i - start;
            int row = idx / columns;
            int gx = boxX + 4 + (idx % columns) * 25;
            int gy = boxY + boxH - 28 - row * 25;
            if (inside(mx, my, gx, gy, 24, 24)) {
                return gridItems.get(i);
            }
        }
        return ItemStack.EMPTY;
    }

    /** Left-click a fuel cell: quick-fill the furnace-family fuel slot — owned
     *  fuel moves from the player inventory (server-verified shift-click),
     *  missing fuel gets a ghost preview in the slot (same look as the recipe
     *  book's ghost ingredients).  Cells on non-furnace screens only consume
     *  the click. */
    private boolean handleFuelCellClick(MouseButtonEvent event, AbstractContainerScreen<?> screen) {
        if (event.button() != 0) return false;
        ItemStack fuel = fuelCellAt(Mth.floor(event.x()), Mth.floor(event.y()));
        if (fuel.isEmpty()) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.getSoundManager() != null) {
            AbstractWidget.playButtonClickSound(mc.getSoundManager());
        }
        if (screen.getMenu() instanceof AbstractFurnaceMenu furnace) {
            placeFurnaceFuel(furnace, fuel, screen);
        }
        return true;
    }

    /** Fill the furnace-family fuel slot with {@code fuel}, through the vanilla
     *  container-click API (the same {@code GameMode} calls the vanilla screens
     *  use): owned fuel = a virtual drag — pick the fuel stack out of the
     *  player inventory, place it into the fuel slot, stash whatever the slot
     *  swapped out (the server validates {@code FuelSlot.mayPlace}, so a fuel
     *  that is also smeltable still lands in the FUEL slot — shift-click would
     *  route it to the input).  Missing fuel = the vanilla ghost-slot preview,
     *  gated exactly like {@code FurnaceRecipeBookComponent.fillGhostRecipe}
     *  (only while the fuel slot is empty) and auto-removed once an item lands
     *  in the slot. */
    private void placeFurnaceFuel(AbstractFurnaceMenu furnace, ItemStack fuel,
                                  AbstractContainerScreen<?> screen) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (PartialCraftingUtil.searchSpaceItemCounts().containsKey(fuel.getItem())) {
            if (!furnace.getCarried().isEmpty()) {
                ClientInventoryUtil.storeItem(-1, idx -> !RecipeMenuUtil.isCraftingMenuSlot(furnace, idx));
            }
            for (int i = 3; i < furnace.slots.size(); i++) {
                ItemStack slot = furnace.slots.get(i).getItem();
                if (!slot.isEmpty() && slot.is(fuel.getItem())) {
                    mc.gameMode.handleInventoryMouseClick(furnace.containerId, i, 0,
                            ClickType.PICKUP, mc.player);
                    mc.gameMode.handleInventoryMouseClick(furnace.containerId,
                            AbstractFurnaceMenu.FUEL_SLOT, 0, ClickType.PICKUP, mc.player);
                    ClientInventoryUtil.storeItem(-1, idx -> !RecipeMenuUtil.isCraftingMenuSlot(furnace, idx));
                    clearFuelGhost(furnace.getSlot(AbstractFurnaceMenu.FUEL_SLOT));
                    return;
                }
            }
            return;
        }
        if (screen instanceof AbstractRecipeBookScreen<?> rbs) {
            RecipeBookComponent<?> book = ((AbstractRecipeBookScreenAccessor) rbs)
                    .brbe$getRecipeBookComponent();
            if (book == null) return;
            GhostSlots ghostSlots = ((RecipeBookComponentAccessor) book).getGhostSlots();
            if (ghostSlots == null) return;
            Slot fuelSlot = furnace.getSlot(AbstractFurnaceMenu.FUEL_SLOT);
            if (!fuelSlot.getItem().isEmpty()) return;
            try {
                SlotDisplay display = new SlotDisplay.ItemStackSlotDisplay(
                        new ItemStack(fuel.getItem(), 1));
                ((GhostSlotsSetSlotAccessor) ghostSlots).brbe$setSlot(
                        fuelSlot, SlotDisplayContext.fromLevel(mc.level), display, false);
                fuelGhostActive = true;
            } catch (Exception | LinkageError ignored) {
                // a failed ghost fill must never break the click handling
            }
        }
    }

    /** Remove the BRBE fuel ghost once the fuel slot holds a real item (the
     *  ghost is only a "please fill" hint, matching vanilla's empty-slot gate). */
    private void syncFuelGhost() {
        if (!fuelGhostActive) return;
        if (ownerScreen == null
                || !(ownerScreen.getMenu() instanceof AbstractFurnaceMenu furnace)) return;
        Slot fuelSlot = furnace.getSlot(AbstractFurnaceMenu.FUEL_SLOT);
        if (!fuelSlot.getItem().isEmpty()) {
            clearFuelGhost(fuelSlot);
        }
    }

    private void clearFuelGhost(Slot fuelSlot) {
        fuelGhostActive = false;
        try {
            if (ownerScreen instanceof AbstractRecipeBookScreen<?> rbs) {
                RecipeBookComponent<?> book = ((AbstractRecipeBookScreenAccessor) rbs)
                        .brbe$getRecipeBookComponent();
                if (book != null) {
                    GhostSlots ghostSlots = ((RecipeBookComponentAccessor) book).getGhostSlots();
                    if (ghostSlots != null) {
                        ((GhostSlotsAccessor) ghostSlots).getIngredients().remove(fuelSlot);
                    }
                }
            }
        } catch (Exception | LinkageError ignored) {
            // a failed ghost removal must never break the frame
        }
    }

    /** X of the i-th category tab (i is the tab index within the current tab
     *  page).  The tab's icon center lands on the i-th column's center line
     *  (boxX + 16 + i*25): the icon sits (TAB_DRAW_WIDTH-16)/2 + 8 = 12px
     *  from the tab's left edge, so the tab starts at boxX + 4 + i*25. */
    private int tabX(int i) {
        return boxX + 4 + i * TAB_WIDTH;
    }

    /** Top edge of the category-tab strip (4px above the box bottom, nudged
     *  1px down). */
    private int tabTop() {
        return boxY + boxH - 4 + 1;
    }

    /** 标签条**每页显示的标签数** = REI 式滑窗（{@link #tabWindowStart}）的窗口大小 =
     *  框体**真实的列数**（{@code (boxW - 8) / TAB_WIDTH}）。
     *
     *  <p>框体宽度由三件事依次决定：对象列数（≤ 列上限）→ {@link #ensureTabWidth}（标签条自己
     *  最多撑到"列上限"列）→ {@link #ensureTitleWidth}（标题栏可再撑出额外的列）。于是
     *  **标签数上限 = 列上限**（用户 2026-09-13），而标题栏撑宽时标签条随之**临时突破列上限**。
     *  标签比窗口多时窗口滑动而不折页。</p> */
    private int visibleTabCount() {
        return Math.max(1, (boxW - 8) / TAB_WIDTH);
    }

    /** Categories that actually have results for the current query target
     *  (tabs with nothing to show are hidden).  With the "hide objects of
     *  workstations without a recipe book" toggle on, categories whose
     *  <b>every</b> object is hidden by the filter hide their tab too. */
    private List<RecipeViewerCategory> visibleCategories() {
        if (queryTarget == null || queryTarget.isEmpty()) return List.of();
        // Browse-all (Ctrl+O): the tab strip shows EVERY category whose
        // complete pool has objects — the "rooms" of the house — not just the
        // categories matching the query.
        if (browseAllMode) {
            return browseCategories();
        }
        Set<String> hidden = hiddenCategoryIds();
        List<RecipeViewerCategory> out = new ArrayList<>();
        for (RecipeViewerCategory cat : RecipeViewerCategories.all()) {
            if (BetterRecipeBook.config.hideNoRecipeBookStationObjects
                    && hidden.contains(cat.id())) {
                continue;
            }
            // A station category whose connection to the query target is cut
            // (illegal station, toggle on) must not show a tab either — it
            // would render but ignore clicks.  Grid categories are exempt.
            if (BetterRecipeBook.config.hideNoRecipeBookStationObjects
                    && !cat.isGridCategory()
                    && cat.appliesToStation(queryTarget)
                    && !RecipeViewerEngine.isRecipeBookStation(queryTarget)) {
                continue;
            }
            if (cat.hasContent(queryTarget, queryUsage)) {
                out.add(cat);
            }
        }
        return out;
    }

    /** The highest-priority category that has visible content for the query,
     *  excluding {@code exclude} — the defensive re-pick when the default
     *  category's hits were all filtered away.  Respects the workstation hide
     *  toggle (illegal stations are cut from their category connection). */
    private RecipeViewerCategory bestContentCategory(ItemStack target, boolean usage,
                                                            RecipeViewerCategory exclude) {
        RecipeViewerCategory best = null;
        int bestPriority = -1;
        for (RecipeViewerCategory category : RecipeViewerCategories.all()) {
            if (category == exclude) continue;
            if (BetterRecipeBook.config.hideNoRecipeBookStationObjects
                    && (!RecipeViewerCategories.isProgressCategory(category)
                        || (!category.isGridCategory()
                            && category.appliesToStation(target)
                            && !RecipeViewerEngine.isRecipeBookStation(target)))) {
                continue;
            }
            int priority = category.defaultPriority(target);
            if (priority <= bestPriority) continue;
            if (category.hasContent(target, usage)) {
                best = category;
                bestPriority = priority;
            }
        }
        return best;
    }
    /** Browse-mode category list cache (all categories with a non-empty
     *  complete pool, hidden set applied); invalidated with the hidden set
     *  and on every mode flip. */
    private List<RecipeViewerCategory> cachedBrowseCategories;
    private boolean cachedBrowseState;

    private Set<String> hiddenCategoryIds() {
        boolean config = BetterRecipeBook.config.hideNoRecipeBookStationObjects;
        if (cachedHiddenCategoryIds == null
                || cachedHiddenConfigState != config
                || RecipeViewerCategories.consumeVisibilityDirty()) {
            cachedHiddenCategoryIds = config ? computeHiddenCategoryIds() : Set.of();
            cachedHiddenConfigState = config;
            cachedBrowseCategories = null;
        }
        return cachedHiddenCategoryIds;
    }

    /** The browse-mode category list: every category whose complete object
     *  pool (allEntries / allGridItems) is non-empty, in tab order, the
     *  "hide objects of workstations without a recipe book" hidden set
     *  applied.  Cached — the pools are queried once per mode entry. */
    private List<RecipeViewerCategory> browseCategories() {
        if (cachedBrowseCategories == null || cachedBrowseState != browseAllMode) {
            cachedBrowseState = browseAllMode;
            cachedBrowseCategories = computeBrowseCategories();
        }
        return cachedBrowseCategories;
    }

    private List<RecipeViewerCategory> computeBrowseCategories() {
        Set<String> hidden = hiddenCategoryIds();
        List<RecipeViewerCategory> out = new ArrayList<>();
        for (RecipeViewerCategory cat : RecipeViewerCategories.all()) {
            if (BetterRecipeBook.config.hideNoRecipeBookStationObjects
                    && hidden.contains(cat.id())) {
                continue;
            }
            boolean has;
            if (cat.isGridCategory()) {
                has = !cat.allGridItems().isEmpty();
            } else {
                has = !filterByRecipeBookStations(cat.allEntries(), cat).isEmpty();
            }
            if (has) {
                out.add(cat);
            }
        }
        return out;
    }

    /** Category tabs along the box bottom (vanilla creative-inventory look).
     *  When more categories than columns, they are folded into pages of
     *  {@code visibleTabCount} and paged with the mouse wheel over the tab strip.
     *  {@code behind} selects the pass: {@code true} draws only the unselected
     *  tabs (painted before the box so its container UI covers their top edge);
     *  {@code false} draws only the selected tab, on top of the box. */
    private void drawCategoryTabs(GuiGraphics gui, int mouseX, int mouseY,
                                         boolean behind) {
        if (!isActive()) return;
        List<RecipeViewerCategory> cats = visibleCategories();
        if (cats.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        int perPage = visibleTabCount();
        // Keep the window valid in case the category list shrank (categories
        // are hidden by content filters); the selection stays visible.
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
            Identifier sprite = selected ? SELECTED_BOTTOM_TAB : UNSELECTED_BOTTOM_TAB;
            // BRBE bottom-tab texture is authored for a -90° (counter-clockwise)
            // display, so rotate the pose exactly like RBIP's bottom tabs.
            // Unselected tabs sit 2px higher (partly hidden behind the box),
            // so shift the whole tab (texture + icon) up together.
            int tabNudge = selected ? 0 : -2;
            gui.pose().pushMatrix();
            gui.pose().translate(x, tabY + TAB_HEIGHT + tabNudge);
            gui.pose().rotate(-(float) Math.PI / 2.0F);
            // Left half.
            // Vertical crop (runtime, no texture editing): keep the tab's
            // rounded ends, drop the plain MIDDLE TAB_V_CUT rows — the panel
            // is drawn exactly TAB_DRAW_WIDTH wide (2px narrower than the
            // pitch, keeping the original tab gap).  Each horizontal half is
            // spliced into an upper and a lower segment.
            gui.blit(RenderPipelines.GUI_TEXTURED, sprite, 0, 0,
                    0, 0, TAB_LEFT, TAB_V_TOP, TAB_TEX_WIDTH, TAB_TEX_HEIGHT);
            gui.blit(RenderPipelines.GUI_TEXTURED, sprite, 0, TAB_V_TOP,
                    0, TAB_V_TOP + TAB_V_CUT, TAB_LEFT, TAB_V_BOTTOM,
                    TAB_TEX_WIDTH, TAB_TEX_HEIGHT);
            // Right half spliced onto the left, skipping the middle TAB_CUT px.
            gui.blit(RenderPipelines.GUI_TEXTURED, sprite, TAB_LEFT, 0,
                    TAB_RIGHT_START, 0,
                    TAB_TEX_WIDTH - TAB_RIGHT_START, TAB_V_TOP,
                    TAB_TEX_WIDTH, TAB_TEX_HEIGHT);
            gui.blit(RenderPipelines.GUI_TEXTURED, sprite, TAB_LEFT, TAB_V_TOP,
                    TAB_RIGHT_START, TAB_V_TOP + TAB_V_CUT,
                    TAB_TEX_WIDTH - TAB_RIGHT_START, TAB_V_BOTTOM,
                    TAB_TEX_WIDTH, TAB_TEX_HEIGHT);
            gui.pose().popMatrix();
            int iconX = x + (TAB_DRAW_WIDTH - 16) / 2;
            int iconY = tabY + (selected ? 6 : 4);
            gui.renderItem(cat.icon(), iconX, iconY);
            // Fuel category: overlay the fire sprite on the furnace icon's
            // bottom-right (roughly the lower-right 4/9 region of the 16x16 icon).
            if (cat.isFuelCategory()) {
                ClientCompat.blitSprite(gui, BRBTextures.FURNACE_FIRE_SPRITE,
                        iconX + 10, iconY + 10, 6, 6);
            }
            if (!previewOwnsCursor(mouseX, mouseY)
                    && inside(mouseX, mouseY, x, tabY, TAB_WIDTH, TAB_HEIGHT)) {
                gui.requestCursor(com.mojang.blaze3d.platform.cursor.CursorTypes.POINTING_HAND);
                drawTabTooltip(gui, cat, mouseX, mouseY);
            }
        }
    }

    /** Whether the open preview (modal) owns the cursor: its hit volume covers
     *  the point, so everything behind it — recipe buttons, category tabs,
     *  page controls — must not hover. */
    private boolean previewOwnsCursor(int mx, int my) {
        return RecipePopupLayer.isActive() && RecipePopupLayer.contains(mx, my);
    }

    /** Whether the query UI owns the cursor: the open viewer's box, its open
     *  preview, or a pin overlay covers the point.  Underlying screen widgets
     *  (creative-inventory tabs, recipe-book tabs, …) must not hover or show
     *  their tooltips while the cursor is inside one of these modal regions. */
    public boolean modalMaskOwnsCursor(int mx, int my) {
        if (PinOverlayManager.covers(mx, my)) return true;
        if (!isActive()) return false;
        return contains(mx, my) || previewOwnsCursor(mx, my);
    }

    /** Tooltip for a category tab: the category name with the sliding-window
     *  indicators, and the source-mod line directly below the title (gated by
     *  {@code showModName} like every other mod-name line, resolved from the
     *  category's icon item).  The indicators appear only while the strip
     *  actually slides (more categories than {@link #maxTabs()}), like the
     *  station column's markers: ◀ solid while content remains to the LEFT of
     *  the window (window not at the leftmost edge) and hollow ◁ at the
     *  leftmost edge; ▶ solid while content remains to the RIGHT and hollow ▷
     *  at the rightmost edge.  Both share the title row — the left marker 4
     *  spaces (16px) right of the title, the right marker 1 space (4px) right
     *  of the left marker, at EXACT pixel anchors (no space padding — a 4px
     *  space grid cannot reproduce arbitrary glyph advances).
     *  <p>Like the station column's tooltip it is DEFERRED: the components
     *  are stored in {@link #pendingTabTooltip} and flushed at the very end
     *  of the overlay's render pass (nothing drawn later can cover it). */
    private void drawTabTooltip(GuiGraphics gui, RecipeViewerCategory cat,
                                       int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getInstance();
        List<net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent> components =
                new ArrayList<>();
        net.minecraft.util.FormattedCharSequence title = cat.name().getVisualOrderText();
        int maxStart = Math.max(0, visibleCategories().size() - visibleTabCount());
        if (maxStart > 0) {
            String left = tabWindowStart > 0 ? "\u25C0" : "\u25C1";
            String right = tabWindowStart < maxStart ? "\u25B6" : "\u25B7";
            net.minecraft.util.FormattedCharSequence leftSeq =
                    Component.literal(left).getVisualOrderText();
            net.minecraft.util.FormattedCharSequence rightSeq =
                    Component.literal(right).getVisualOrderText();
            int spaceW = Math.max(1, mc.font.width(" "));
            int leftX = mc.font.width(title) + 4 * spaceW;
            int rightX = leftX + mc.font.width(leftSeq) + spaceW;
            components.add(new TabMarkerTitleTooltipComponent(
                    title, leftSeq, rightSeq, leftX, rightX));
        } else {
            components.add(net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent
                    .create(title));
        }
        if (BetterRecipeBook.config.showModName) {
            // The info category's icon is a vanilla item, which would resolve
            // to "Minecraft"; its source mod is THIS mod.
            Component modName = cat instanceof InfoRecipeCategory
                    ? selfModName()
                    : ModNameUtil.getFormattedModName(cat.icon());
            if (modName != null && !modName.getString().isEmpty()) {
                components.add(net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent
                        .create(Component.empty().getVisualOrderText()));
                components.add(net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent
                        .create(modName.getVisualOrderText()));
            }
        }
        pendingTabTooltip = components;
        pendingTabTooltipX = mouseX;
        pendingTabTooltipY = mouseY;
        pendingTabTooltipStyle = cat.icon().get(
                net.minecraft.core.component.DataComponents.TOOLTIP_STYLE);
    }

    /** This mod's display name — the "source mod" of the info category, read
     *  straight from the FabricLoader mod metadata (same BLUE+ITALIC style as
     *  every other mod-name line). */
    private Component selfModName() {
        String name = null;
        try {
            name = FabricLoader.getInstance()
                    .getModContainer(BetterRecipeBook.MOD_ID)
                    .map(m -> m.getMetadata().getName())
                    .orElse(null);
        } catch (Throwable ignored) {
        }
        if (name == null || name.isEmpty()) {
            name = BetterRecipeBook.MOD_ID;
        }
        return Component.literal(name).withStyle(ChatFormatting.BLUE, ChatFormatting.ITALIC);
    }

    /** Render the deferred category-tab tooltip — must run at the very end of
     *  the overlay's render pass (after every tab and the box) so nothing
     *  drawn later can cover it. */
    private void flushTabTooltip(GuiGraphics gui) {
        if (pendingTabTooltip == null) return;
        gui.renderTooltip(Minecraft.getInstance().font, pendingTabTooltip,
                pendingTabTooltipX, pendingTabTooltipY,
                net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner.INSTANCE,
                ClientCompat.VIEWER_TOOLTIP_STYLE);
        pendingTabTooltip = null;
    }

    /** Clicking a visible category tab switches the viewer to that category. */
    private boolean handleCategoryTabClick(MouseButtonEvent event) {
        if (event.button() != 0) return false;
        int mx = Mth.floor(event.x());
        int my = Mth.floor(event.y());
        int tabY = tabTop();
        List<RecipeViewerCategory> cats = visibleCategories();
        int perPage = visibleTabCount();
        int start = tabWindowStart;
        int end = Math.min(start + perPage, cats.size());
        for (int i = start; i < end; i++) {
            if (inside(mx, my, tabX(i - start), tabY, TAB_WIDTH, TAB_HEIGHT)) {
                RecipeViewerCategory cat = cats.get(i);
                Minecraft mc = Minecraft.getInstance();
                if (cat != currentCategory) {
                    ClientCompat.playPageFlipSound(mc);
                    switchCategory(cat);
                }
                // Clicking the ALREADY-SELECTED tab does nothing any more
                // (browse-all is toggled ONLY by a click on the extension
                // area's title — release without a drag).  The click is still
                // consumed (it lies on the window's footprint).
                return true;
            }
        }
        return false;
    }

    /** The built-in family backing {@code category}, for the left station
     *  column ({@code null} for categories without stations, e.g. info). */
    private RecipeViewerIndex.Family familyForCategory(RecipeViewerCategory category) {
        if (category == null) return null;
        return switch (category.id()) {
            case "crafting" -> RecipeViewerIndex.Family.CRAFTING;
            case "furnace", "fuel" -> RecipeViewerIndex.Family.FURNACE;
            case "stonecutting" -> RecipeViewerIndex.Family.STONECUTTING;
            case "smithing" -> RecipeViewerIndex.Family.SMITHING;
            case "anvil" -> RecipeViewerIndex.Family.ANVIL;
            case "brewing" -> RecipeViewerIndex.Family.BREWING;
            case "grindstone" -> RecipeViewerIndex.Family.GRINDSTONE;
            case "compost" -> RecipeViewerIndex.Family.COMPOSTING;
            default -> null;
        };
    }

    /** Rebuild the left station column for the open category: the workstations
     *  it can use, in registry order (built-in families answer from the
     *  workstation registry — e.g. the furnace family lists furnace /
     *  blast_furnace / smoker / campfire / soul_campfire — plugin categories
     *  answer from the stations they were registered with).  The column is
     *  laid out bottom-up (index 0 renders at the bottom) and the window
     *  starts at the list bottom ({@code stationScroll = 0} shows the first
     *  rows, i.e. the bottommost content). */
    private void rebuildStationColumn() {
        stationColumnItems = List.of();
        stationScroll = 0;
        if (currentCategory == null) return;
        if (currentCategory instanceof PluginRecipeViewerCategory plugin) {
            stationColumnItems = plugin.stations();
            return;
        }
        RecipeViewerIndex.Family family = familyForCategory(currentCategory);
        if (family == null) return;
        if (family == RecipeViewerIndex.Family.FURNACE) {
            // Smelting / fuel: subcategory groups, bottom-up 烧炼 → 熔炼 →
            // 烟熏 → 营火, each group in the tooltip's left-to-right order.
            // The fuel category (烧炼燃料) drops the campfire-cooking group —
            // campfire cannot take fuel — while the furnace category keeps it.
            boolean fuel = "fuel".equals(currentCategory.id());
            stationColumnItems = RecipeViewerIndex.furnaceStationColumnItems(!fuel);
            return;
        }
        stationColumnItems = RecipeViewerIndex.workstationItems(family);
    }

    /** How many station cells fit in the object area's height (the box's row
     *  count): the station window's viewport. */
    private int stationViewRows() {
        return Math.max(1, (boxH - 8) / STATION_PITCH);
    }

    /** The workstation object in the column cell under (mx,my), or empty.
     *  Shared by the column click, R/U capture and the hover state. */
    private ItemStack stationCellAt(int mx, int my) {
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

    /** Vertical span of the trimmed column panel (top..height).  The top
     *  border sits 5px above the topmost cell — the same inset the main
     *  box uses (its cells start at boxY+5), so a full column panel is exactly
     *  as tall as the main box (no off-by-one).  {@code shown} = visible rows. */
    private int[] stationColumnPanelRect(int shown) {
        int bottom = boxY + boxH - 4;
        int colTop = bottom - shown * STATION_PITCH + 1 - 5;
        int colH = (boxY + boxH) - colTop;
        return new int[] { colTop, colH };
    }

    /** Draw the column surface merged with the main box, drawn AFTER the box
     *  blit and BEFORE the cells.  The surface uses a dedicated 9-slice
     *  sprite ({@link #COLUMN_PANEL_SPRITE}) derived from the box sprite with
     *  the RIGHT side opened: left / top / bottom borders and the TL / BL
     *  rounded corners keep the original texture, while the right-middle
     *  three columns and the TR / BR corners are repainted in the interior
     *  grey — the column's right side flows into the box's content with no
     *  seam line.  The TR corner is a FLAT T-junction: the panel's top black
     *  row ends on the box's left black column and the white row joins the
     *  box's white columns (a rounded arc there would cut the box's border
     *  lines into fragments, so only the TL corner stays rounded).  The
     *  bottom band runs to the panel's right edge, which lands on the box's
     *  left border and continues the box's bottom border line vertically;
     *  the trimmed top (5px above the topmost cell).
     *  <p>NOTE: GUI sprite ids are relative to {@code textures/gui/sprites/}
     *  (same convention as {@link #OVERLAY_RECIPE_SPRITE}) — including the full
     *  path makes the sprite look-up miss and render the error texture. */
    private final Identifier COLUMN_PANEL_SPRITE =
            Identifier.fromNamespaceAndPath("brbe", "recipe_book/column_panel");

    private void drawStationColumnSurfaces(GuiGraphics gui) {
        if (stationColumnItems.isEmpty()) return;
        int rows = stationViewRows();
        int shown = Math.min(stationColumnItems.size(), rows);
        if (shown <= 0) return;
        int[] rect = stationColumnPanelRect(shown);
        // The column always uses the normal (bottom-layer) texture; the
        // full-height "column_panel_top" variant is not used any more — with
        // the box's background extended upward the box's top border line
        // moved up, so the column's top border no longer continues it as one
        // straight line: the topmost workstation cell uses the same texture
        // as the lower ones.
        ClientCompat.blitSprite(gui, COLUMN_PANEL_SPRITE, panelLeft(), rect[0],
                STATION_COL_WIDTH + 4, rect[1]);
    }

    /** Draw the viewer's left workstation column: the open category's
     *  workstation objects as plain 24px cells (same look as the fuel grid,
     *  no info lines, queryable by click), bottom-aligned and laid out from
     *  the bottom up.  More stations than the object area's rows slide as a
     *  wheel-driven window; fewer show no empty carriers — the column's panel
     *  background is trimmed to the actual content (top edge follows the
     *  topmost cell; see {@link #drawStationColumnSurfaces}). */
    private void drawStationColumn(GuiGraphics gui, int mouseX, int mouseY) {
        if (stationColumnItems.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        // Paint the column surface merged with the box first (no 9-slice
        // corners involved), then the cells on top.
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
            boolean hovered = !previewOwnsCursor(mouseX, mouseY)
                    && inside(mouseX, mouseY, x, gy, STATION_CELL, STATION_CELL);
            Identifier sprite = BRBTextures.RECIPE_BOOK_PLAIN_OVERLAY_SPRITE.get(true, hovered);
            ClientCompat.blitSprite(gui, sprite, x, gy, STATION_CELL, STATION_CELL);
            gui.renderItem(stack, x + 4, gy + 4);
            if (hovered) {
                gui.requestCursor(com.mojang.blaze3d.platform.cursor.CursorTypes.POINTING_HAND);
                Component mod = null;
                if (BetterRecipeBook.config.showModName) {
                    mod = ModNameUtil.getFormattedModName(stack);
                    if (mod != null && mod.getString().isEmpty()) mod = null;
                }
                // Sliding-window markers (only while the window is enabled,
                // i.e. more stations than the viewport rows): the up triangle
                // on the title row and the down triangle in the blank row
                // below it (the same blank row the mod name uses).  ▲ is solid
                // while content remains ABOVE the window (window not at the
                // list top) and hollow △ at the top edge; ▼ is solid while
                // content remains BELOW (window not at the list bottom) and
                // hollow ▽ at the bottom edge — the window starts at the
                // bottom, so it opens with ▲/▽.
                // Both markers are drawn by purpose-built tooltip row
                // components at ONE shared pixel anchor (anchorX): ▼ is not
                // positioned independently (no space padding — a 4px space
                // grid cannot reproduce arbitrary glyph advances, which made
                // ▼ drift by up to a space).  ▲ never sits closer than 4
                // spaces (16px) to the title; when the right edge is farther
                // away it is used instead.
                List<net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent> components =
                        new ArrayList<>();
                if (maxScroll > 0) {
                    String up = stationScroll < maxScroll ? "\u25B2" : "\u25B3";
                    String down = stationScroll > 0 ? "\u25BC" : "\u25BD";
                    net.minecraft.util.FormattedCharSequence titleSeq =
                            stack.getHoverName().getVisualOrderText();
                    net.minecraft.util.FormattedCharSequence upSeq =
                            Component.literal(up).getVisualOrderText();
                    net.minecraft.util.FormattedCharSequence downSeq =
                            Component.literal(down).getVisualOrderText();
                    int spaceW = Math.max(1, mc.font.width(" "));
                    int titleW = mc.font.width(titleSeq);
                    int upW = mc.font.width(upSeq);
                    int modW = mod != null ? mc.font.width(mod.getVisualOrderText()) : 0;
                    // Content width from the base rows (blank line = 0 wide);
                    // the ▲ row at its minimum 4-space gap may widen it.
                    int contentW = Math.max(Math.max(titleW, modW), titleW + 4 * spaceW + upW);
                    // Anchor = the exact pixel where both triangles are drawn:
                    // the content right edge, but never closer than 4 spaces
                    // (16px) to the title text.
                    int anchorX = Math.max(titleW + 4 * spaceW, contentW - upW);
                    components.add(new StationTitleMarkerTooltipComponent(
                            titleSeq, upSeq, anchorX));
                    components.add(new StationMarkerTooltipComponent(downSeq, anchorX));
                } else {
                    components.add(net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent
                            .create(stack.getHoverName().getVisualOrderText()));
                    if (mod != null) components.add(net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent
                            .create(Component.empty().getVisualOrderText()));
                }
                if (mod != null) components.add(net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent
                        .create(mod.getVisualOrderText()));
                // Defer to the end of the overlay's render pass (see the field
                // javadoc) so no later-drawn station cell covers the tooltip.
                pendingStationTooltip = components;
                pendingStationTooltipX = mouseX;
                pendingStationTooltipY = mouseY;
                pendingStationTooltipStyle = stack.get(
                        net.minecraft.core.component.DataComponents.TOOLTIP_STYLE);
            }
        }
    }

    /** Render the deferred station-column tooltip — must run at the very end
     *  of the overlay's render pass (after every cell and the recipe-popup
     *  layer) so nothing drawn later can cover it. */
    private void flushStationTooltip(GuiGraphics gui) {
        if (pendingStationTooltip == null) return;
        gui.renderTooltip(Minecraft.getInstance().font, pendingStationTooltip,
                pendingStationTooltipX, pendingStationTooltipY,
                net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner.INSTANCE,
                ClientCompat.VIEWER_TOOLTIP_STYLE);
        pendingStationTooltip = null;
    }

    /** Clicking a left-column workstation object queries its recipes (opens a
     *  NEW query window for that object, R-key = "view recipe" semantics —
     *  the originating window stays untouched, and a failed station query
     *  opens nothing instead of closing it). */
    private boolean handleStationColumnClick(MouseButtonEvent event) {
        if (event.button() != 0 || stationColumnItems.isEmpty() || ownerScreen == null) return false;
        int mx = Mth.floor(event.x());
        int my = Mth.floor(event.y());
        ItemStack hit = stationCellAt(mx, my);
        if (hit.isEmpty()) return false;
        AbstractContainerScreen<?> screen = ownerScreen;
        Minecraft mc = Minecraft.getInstance();
        if (mc.getSoundManager() != null) {
            AbstractWidget.playButtonClickSound(mc.getSoundManager());
        }
        return RecipeViewerOverlay.openNewViewer(screen, hit, false);
    }

    /** Wheel over the left station column slides its window — only when there
     *  are more stations than visible rows (no empty carriers otherwise). */
    private boolean handleStationColumnScroll(double mouseX, double mouseY, double vertical) {
        if (!isActive() || vertical == 0) return false;
        if (stationColumnItems.size() <= stationViewRows()) return false;
        // The wheel region follows the TRIMMED panel (the same rect the panel
        // background is drawn in — the top edge tracks the topmost cell, so
        // empty space above a short column is not part of the hit area).
        int[] rect = stationColumnPanelRect(
                Math.min(stationColumnItems.size(), stationViewRows()));
        if (!inside(mouseX, mouseY, panelLeft(), rect[0], STATION_COL_WIDTH + 4, rect[1])) {
            return false;
        }
        int maxScroll = Math.max(0, stationColumnItems.size() - stationViewRows());
        // Wheel-up (vertical > 0) slides the window UP the list (toward the
        // topmost content, larger index); wheel-down slides it back DOWN
        // (toward the bottom).  The window starts at the bottom edge
        // (stationScroll = 0, down triangle hollow).
        int next = stationScroll + (vertical > 0 ? 1 : -1);
        if (next < 0 || next > maxScroll) return false;
        stationScroll = next;
        // Slide sound follows the "mouse wheel page-flip sound" toggle and the
        // page-flip volume, the same as the object area's paging.
        ClientCompat.playPageFlipSound(Minecraft.getInstance());
        return true;
    }

    /** Whether the cursor is over the category tab strip. */
    private boolean overTabStrip(double mouseX, double mouseY) {
        int catCount = visibleCategories().size();
        if (catCount == 0) return false;
        int perPage = visibleTabCount();
        int shown = Math.min(perPage, catCount);
        int tabY = tabTop();
        return inside(mouseX, mouseY, boxX, tabY, shown * TAB_WIDTH, TAB_HEIGHT);
    }

    /** 在标签条上滚轮 = 切类别 + REI 式滑窗；**滑窗的"滑动起始位"就是窗口正中**（用户 2026-09-13）：
     *  窗口大小 {@code N} 为**奇数**时就是正中心那一槽（左右方向都是它）；为**偶数**时是中间两槽
     *  ——向右滚用右边那槽（{@code N/2}）、向左滚用左边那槽（{@code (N-1)/2}）。
     *  选中标签一旦到达该槽位，窗口就跟着它一起滑，**选中标签看起来相对静止**。
     *  （改前左侧起始位写死为 {@code N/2 - 1}，奇数窗口下比正中偏左一槽 —— 列上限设成 7 这类
     *  奇数后滑窗会比预期早一步启动。）没有动画，类别立即切换。 */
    public boolean mouseScrolledTabs(double mouseX, double mouseY, double vertical) {
        if (!isActive() || vertical == 0) return false;
        List<RecipeViewerCategory> cats = visibleCategories();
        if (cats.size() <= 1) return false;
        if (!overTabStrip(mouseX, mouseY)) return false;
        int idx = cats.indexOf(currentCategory);
        if (idx < 0) return false;
        int delta = vertical > 0 ? -1 : 1;
        int newIdx = idx + delta;
        if (newIdx < 0 || newIdx >= cats.size()) return false;
        int perPage = visibleTabCount();
        int maxStart = Math.max(0, cats.size() - perPage);
        // 选中标签到达"滑动起始位"后窗口随它一起滑，标签因此钉在该槽位不动：
        // 向右滚用 pinRight、向左滚用 pinLeft —— 奇数窗口两者都是正中那一槽，
        // 偶数窗口是中间两槽（原来写死 6/5，即十列窗口的 5/4 规则）。
        int slot = idx - tabWindowStart;
        int pinRight = perPage / 2;        // 偶数：中间偏右；奇数：正中间
        int pinLeft = (perPage - 1) / 2;   // 偶数：中间偏左；奇数：正中间
        if (delta > 0 && maxStart > 0 && slot >= pinRight) {
            tabWindowStart = Math.min(maxStart, tabWindowStart + 1);
        } else if (delta < 0 && maxStart > 0 && slot <= pinLeft) {
            tabWindowStart = Math.max(0, tabWindowStart - 1);
        }
        // Keep the newly selected tab visible (safety net when the selection
        // arrived at an edge by other means).
        if (newIdx < tabWindowStart) {
            tabWindowStart = newIdx;
        } else if (newIdx >= tabWindowStart + perPage) {
            tabWindowStart = Math.min(maxStart, newIdx - (perPage - 1));
        }
        switchCategory(cats.get(newIdx));
        Minecraft mc = Minecraft.getInstance();
        ClientCompat.playPageFlipSound(mc);
        return true;
    }

    /**
     * Recipe-button tooltip for the viewer, matching the vanilla recipe-book
     * button tooltip plus BRBE's lines (source-mod name, 3x3 warning).  Used by
     * the non-paged path (injected on render RETURN) and drawn here
     * for the paged path.
     */
    // ── Popup (hovered recipe UI) geometry ────────────────────────────────
    /** Whether the cursor is inside {@code widget}'s popup — the enlarged
     *  recipe UI shown while Shift is held.  The hit volume equals the
     *  rendered texture's bounds (shared {@link PopupGeometry}).  Public so
     *  the recipe button mixin can extend its hover area to the whole popup
     *  for adapted synthetic recipes. */
    public boolean isInPopupArea(AbstractWidget widget, int mx, int my) {
        return popupGeometry(widget).contains(mx, my);
    }

    /** Whether the cursor is over {@code widget}'s own UI — the preview
     *  trigger area.  Confined to the object's button rect, uniformly across
     *  every viewer object (including unadapted plugin recipes, whose popup
     *  bounds are larger than — and offset from — the button): a preview opens
     *  only while the object itself is hovered, exactly like the BRBE-adapted
     *  objects.  The open preview's hit volume — its own texture bounds,
     *  {@link #isInPopupArea} — keeps it open while the cursor is inside it. */
    public boolean isTriggerArea(AbstractWidget widget, int mx, int my) {
        return mx >= widget.getX() && mx < widget.getX() + widget.getWidth()
                && my >= widget.getY() && my < widget.getY() + widget.getHeight();
    }

    /** The shared popup geometry for {@code widget}'s recipe, in the layout
     *  mode of the entry's own category (browse-all mixes categories). */
    private PopupGeometry popupGeometry(AbstractWidget widget) {
        OverlayRecipeButtonAccessor oba = (OverlayRecipeButtonAccessor) widget;
        RecipeDisplayId id = oba.brbe$getRecipe();
        return PopupGeometry.of(id, entryFor(id), viewerMode(), oba.brbe$getSlots(),
                widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight());
    }

    /** The viewer's current layout mode (furnace / stonecutter / smithing /
     *  anvil / brewing / grindstone / crafting), shared with the pin overlays
     *  and the popup geometry. */
    public int viewerMode() {
        return modeForCategory(currentCategory);
    }

    /** Layout mode of a single category (the single source of
     *  {@link #viewerMode}). */
    private int modeForCategory(RecipeViewerCategory category) {
        if (category == null) return PinOverlay.MODE_CRAFTING;
        return switch (category.id()) {
            case "furnace", "fuel" -> PinOverlay.MODE_FURNACE;
            case "stonecutting" -> PinOverlay.MODE_STONECUTTING;
            case "smithing" -> PinOverlay.MODE_SMITHING;
            case "anvil" -> PinOverlay.MODE_ANVIL;
            case "brewing" -> PinOverlay.MODE_BREWING;
            case "grindstone" -> PinOverlay.MODE_GRINDSTONE;
            default -> PinOverlay.MODE_CRAFTING;
        };
    }

    /** The query-viewer button whose popup the cursor currently sits in
     *  (top-most of any overlap), set each render while Shift is held; it
     *  drives the independent popup layer and the popup tooltip. */
    private AbstractWidget hoverPopupField;

    /** The viewer button under the cursor regardless of Shift, for the
     *  always-detailed button tooltip when no popup is open. */
    private AbstractWidget hoveredViewerButton;

    /** The item rendered under the cursor inside the popup (its current cycled
     *  variant), or EMPTY when the cursor is on empty space.  For JEI-adapted
     *  popups the item comes from the live JEI drawable (which drives the
     *  visible cycling itself), so the tooltip matches the painted variant. */
    public ItemStack slotStackInPopup(AbstractWidget widget, int mx, int my) {
        OverlayRecipeButtonAccessor oba = (OverlayRecipeButtonAccessor) widget;
        RecipeDisplayId id = oba.brbe$getRecipe();
        PopupGeometry geometry = popupGeometry(widget);
        SyntheticRecipeRenderer renderer = SyntheticRecipeRenderers.get();
        if (renderer != SyntheticRecipeRenderer.NONE && renderer.canRender(id)) {
            ItemStack painted = renderer.itemUnderMouse(id, mx, my,
                    geometry.ox, geometry.oy, geometry.fit);
            if (!painted.isEmpty()) {
                return painted;
            }
        }
        // 指针下那件折叠物品的当前显示下标（冻结时取冻结值，否则自动值）。
        int selIdx = CycleLock.hoveredOr(
                ((OverlayRecipeComponentAccessor) overlay).getSlotSelectTime().currentIndex());
        return geometry.itemAt(mx, my, selIdx);
    }

    /** Full tooltip for a popup slot's item (item name + source-mod line),
     *  at the vanilla default position (no push-away — the mechanism is gone).
     *  The title row carries the item's icon at the same enlarged scale as the
     *  query object's tooltip. */
    private void renderPopupSlotTooltip(GuiGraphics gui, int mx, int my,
                                               ItemStack stack) {
        Minecraft mc = Minecraft.getInstance();
        List<Component> lines = new ArrayList<>(Screen.getTooltipFromItem(mc, stack));
        Component modName = ModNameUtil.getFormattedModName(stack);
        if (modName != null && !modName.getString().isEmpty()) {
            lines.add(Component.empty());
            lines.add(modName);
        }
        List<net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent> components =
                new ArrayList<>(lines.size());
        for (int i = 0; i < lines.size(); i++) {
            if (i == 0) {
                components.add(new com.alonie.brbe.util.TitleWithIconTooltipComponent(
                        lines.get(0).getVisualOrderText(), stack));
            } else {
                components.add(net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent
                        .create(lines.get(i).getVisualOrderText()));
            }
        }
        gui.renderTooltip(mc.font, components, mx, my,
                net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner.INSTANCE,
                ClientCompat.VIEWER_TOOLTIP_STYLE);
    }

    public void renderTooltip(GuiGraphics gui, int mouseX, int mouseY) {
        if (!isActive()) return;
        // A pin under the cursor owns the tooltip; the viewer's is suppressed.
        if (PinOverlayManager.covers(mouseX, mouseY)) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.level == null) return;
        // Grid category (fuel / compost / info) — also the plain grid-item
        // cells of browse-all: a single tooltip — item name + the category's
        // info rows — no shift variation, at the vanilla default position.
        if (isGridMode() && gridHoverStack != null && !gridHoverStack.isEmpty()) {
            List<Component> lines = new ArrayList<>(Screen.getTooltipFromItem(mc, gridHoverStack));
            List<net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent> components =
                    new ArrayList<>(lines.size());
            for (int i = 0; i < lines.size(); i++) {
                if (i == 0) {
                    // The title row also carries the item's icon to the right of
                    // the name, matching the detailed recipe tooltips of the
                    // other categories.
                    components.add(new com.alonie.brbe.util.TitleWithIconTooltipComponent(
                            lines.get(0).getVisualOrderText(), gridHoverStack));
                } else {
                    components.add(net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent
                            .create(lines.get(i).getVisualOrderText()));
                }
            }
            components.addAll(gridTooltipComponents(
                    gridHoverCategory != null ? gridHoverCategory : currentCategory,
                    gridHoverStack));
            if (BetterRecipeBook.config.showModName) {
                Component modName = ModNameUtil.getFormattedModName(gridHoverStack);
                if (modName != null && !modName.getString().isEmpty()) {
                    components.add(net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent
                            .create(Component.empty().getVisualOrderText()));
                    components.add(net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent
                            .create(modName.getVisualOrderText()));
                }
            }
            gui.renderTooltip(mc.font, components, mouseX, mouseY,
                    net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner.INSTANCE,
                    ClientCompat.VIEWER_TOOLTIP_STYLE);
            return;
        }
        // The popup under the cursor (computed at the top of render, topmost of
        // any overlap).  Only the hovered slot's item shows a tooltip — empty
        // popup space shows nothing (the old "recipe result on empty space"
        // behaviour is gone).
        AbstractWidget popupWidget = hoverPopupField;
        if (popupWidget != null && isInPopupArea(popupWidget, mouseX, mouseY)) {
            ItemStack slotStack = slotStackInPopup(popupWidget, mouseX, mouseY);
            if (!slotStack.isEmpty()) {
                renderPopupSlotTooltip(gui, mouseX, mouseY, slotStack);
            }
            return;
        }
        // Popup closed: the hovered viewer button shows its recipe's result
        // tooltip — always the most detailed form, following the cycled
        // variant, with the recipe's full preview UI embedded as a row (no
        // Shift needed) — at the vanilla default position.
        AbstractWidget hovered = hoveredViewerButton;
        if (hovered == null) return;
        RecipeDisplayId id = ((OverlayRecipeButtonAccessor) hovered).brbe$getRecipe();
        RecipeDisplayEntry entry = entryFor(id);
        if (entry == null) return;
        // 指针下那件折叠物品的当前显示下标（冻结时取冻结值，否则自动值）。
        int selIdx = CycleLock.hoveredOr(
                ((OverlayRecipeComponentAccessor) overlay).getSlotSelectTime().currentIndex());
        RecipeCollection collection = overlay.getRecipeCollection();
        boolean craftable = collection != null && collection.isCraftable(id);
        boolean partial = collection != null
                && (RecipeViewerIndex.isViewerPartial(collection, id)
                        || PartialCraftingUtil.isPartiallyCraftableEvenIfStale(collection, id));
        renderDetailedRecipeTooltip(gui, entry, id,
                ((OverlayRecipeButtonAccessor) hovered).brbe$getSlots(),
                craftable, partial, mouseX, mouseY, selIdx);
    }

    /** The recipe's detailed result tooltip (name + BRBE rows + source-mod
     *  line), following the slot-select cycle's current result variant, at the
     *  vanilla default position.  Shared by the viewer's button hover and the
     *  pin overlays' no-shift tooltip (both inherit the query object's
     *  tooltip). */
    public void renderDetailedRecipeTooltip(GuiGraphics gui,
                                                   RecipeDisplayEntry entry, RecipeDisplayId id,
                                                   int mouseX, int mouseY, int selIdx) {
        // The pin overlays share this method; their tooltip carries no
        // embedded preview (the pin itself already shows the full UI).
        renderDetailedRecipeTooltip(gui, entry, id, null, false, false,
                mouseX, mouseY, selIdx, false);
    }

    /** Viewer object hover: the detailed tooltip with the recipe's full
     *  preview UI embedded (no Shift) — the same rendering the popup uses. */
    public void renderDetailedRecipeTooltip(GuiGraphics gui,
                                                   RecipeDisplayEntry entry, RecipeDisplayId id,
                                                   List<?> slots, boolean craftable, boolean partial,
                                                   int mouseX, int mouseY, int selIdx) {
        renderDetailedRecipeTooltip(gui, entry, id, slots, craftable, partial,
                mouseX, mouseY, selIdx, true);
    }

    private void renderDetailedRecipeTooltip(GuiGraphics gui,
                                                    RecipeDisplayEntry entry, RecipeDisplayId id,
                                                    List<?> slots, boolean craftable, boolean partial,
                                                    int mouseX, int mouseY, int selIdx,
                                                    boolean embedPreview) {
        renderDetailedTooltipImpl(gui, entry, id, slots, craftable, partial,
                mouseX, mouseY, selIdx, embedPreview,
                viewerMode(), currentCategory, overlay.getRecipeCollection());
    }

    /** The shared implementation of the detailed recipe tooltip: the viewer
     *  button hover (preview embedded) and the pin overlays' no-shift tooltip
     *  (no preview).  {@code mode} / {@code category} / {@code collection} are
     *  the window contexts; the pins-only (no window) pass supplies a
     *  placeholder mode, null category (station row falls back to
     *  {@link #categoryFor(RecipeDisplayEntry)}) and null collection (the
     *  recipe-book incompatibility warning is skipped). */
    private static void renderDetailedTooltipImpl(GuiGraphics gui,
                                                         RecipeDisplayEntry entry, RecipeDisplayId id,
                                                         List<?> slots, boolean craftable, boolean partial,
                                                         int mouseX, int mouseY, int selIdx,
                                                         boolean embedPreview, int mode,
                                                         RecipeViewerCategory category,
                                                         RecipeCollection collection) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.level == null) return;
        ItemStack output = resolveOutput(entry, mc, selIdx);
        if (output == null || output.isEmpty()) return;

        List<Component> lines = buildTooltipLines(mc, output, id, entry, collection);
        Identifier style = output.get(net.minecraft.core.component.DataComponents.TOOLTIP_STYLE);
        List<net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent> components =
                new ArrayList<>(lines.size());
        for (int i = 0; i < lines.size(); i++) {
            if (i == 0) {
                // The title row also carries the item's icon to the right of
                // the name, vertically centred in the row.
                components.add(new com.alonie.brbe.util.TitleWithIconTooltipComponent(
                        lines.get(0).getVisualOrderText(), output));
            } else {
                components.add(net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent
                        .create(lines.get(i).getVisualOrderText()));
            }
        }
        // The full preview UI embedded as a tooltip row (viewer hover only,
        // no Shift): the same rendering the popup uses — placed ABOVE the
        // workstation rows, which stay above the source-mod line.  Every
        // viewer object gets its preview: the delegated JEI UI (1:1) or the
        // vanilla-style popup (crafting grid / furnace fixed pair).
        if (embedPreview) {
            components.add(new RecipePreviewTooltipComponent(id, entry, mode,
                    slots, selIdx, craftable, partial));
        }
        if (RecipeViewerIndex.asFurnace(entry) != null) {
            components.addAll(furnaceTooltipComponents(entry));
        } else {
            // Crafting / stonecutting / smithing: show the workstations
            // that produce this recipe as icons at the bottom of the
            // tooltip (including mod workstations), no text label.
            components.addAll(stationIconsTooltipComponents(entry, category));
        }
        // Source-mod name always sits at the very bottom.
        if (BetterRecipeBook.config.showModName) {
            Component modName = ModNameUtil.getFormattedModName(output);
            if (modName != null && !modName.getString().isEmpty()) {
                components.add(net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent
                        .create(Component.empty().getVisualOrderText()));
                components.add(net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent
                        .create(modName.getVisualOrderText()));
            }
        }
        gui.renderTooltip(mc.font, components, mouseX, mouseY,
                net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner.INSTANCE,
                ClientCompat.VIEWER_TOOLTIP_STYLE);
    }

    /** Resolve the recipe's result for the current slot-select cycle (the
     *  displayed variant), tolerating context-sensitive displays. */
    private static ItemStack resolveOutput(RecipeDisplayEntry entry, Minecraft mc, int selIdx) {
        try {
            List<ItemStack> results = entry.resultItems(SlotDisplayContext.fromLevel(mc.level));
            if (!results.isEmpty()) return results.get(selIdx % results.size());
        } catch (Exception e) {
            // fall through
        }
        try {
            List<ItemStack> results = entry.resultItems(null);
            if (!results.isEmpty()) return results.get(selIdx % results.size());
        } catch (Exception e) {
            // unresolvable result
        }
        return null;
    }

    /** Reproduces RecipeButton.getTooltipText including BRBE's appended lines.
     *  {@code collection} is the query window's collection (may be null in the
     *  pins-only pass — the recipe-book incompatibility warning is skipped). */
    private static List<Component> buildTooltipLines(Minecraft mc, ItemStack output, RecipeDisplayId id,
                                                     RecipeDisplayEntry entry,
                                                     RecipeCollection collection) {
        List<Component> lines = new ArrayList<>(Screen.getTooltipFromItem(mc, output));

        // 3x3 "cannot craft here" warning comes above the source-mod line.
        if (collection != null
                && BetterRecipeBook.config.showAllRecipesInSurvival
                && !BetterRecipeBook.config.hideIncompatibleMark
                && mc.screen instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen) {
            if (IncompatibleCraftingUtil.checkIncompatible(collection, id)) {
                lines.add(Component.empty());
                lines.add(Component.translatable("brbe.gui.environmentIncompatible")
                        .withStyle(net.minecraft.ChatFormatting.RED));
            }
        }

        return lines;
    }

    /** XP + per-station cook-time rows for a furnace recipe tooltip; each
     *  station row carries its workstation item icons.  Always the expanded
     *  form — one labelled row per station — there is no compact/Shift
     *  variation any more. */
    private static List<net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent>
            furnaceTooltipComponents(RecipeDisplayEntry entry) {
        FurnaceRecipeDisplay display = RecipeViewerIndex.asFurnace(entry);
        List<net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent> components =
                new ArrayList<>();
        components.add(net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent
                .create(Component.empty().getVisualOrderText()));

        float xp = display.experience();
        String xpText = xp % 1.0f == 0f ? String.valueOf((int) xp)
                : String.format(Locale.ROOT, "%.2f", xp);
        components.add(net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent
                .create(Component.literal(xpText + " XP").withStyle(ChatFormatting.GREEN)
                        .getVisualOrderText()));
        components.add(net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent
                .create(Component.empty().getVisualOrderText()));

        int[] ticks = RecipeViewerIndex.furnaceStationTicks(entry);
        boolean furnaceStn = menuIs(FurnaceMenu.class);
        boolean blastStn = menuIs(BlastFurnaceMenu.class);
        boolean smokerStn = menuIs(SmokerMenu.class);
        // One labelled row per station subcategory (each its own component so
        // the rows break lines), colour follows the station, "•" marks the
        // station the open screen matches.  Each row carries that
        // subcategory's workstation icons — vanilla and any mod workstations
        // from the external registry.
        for (int i = 0; i < ticks.length; i++) {
            if (ticks[i] <= 0) continue;
            Component line = stationTimeLine(stationLabel(i), cookSeconds(ticks[i]),
                    stationStyle(i), stationMatches(i, furnaceStn, blastStn, smokerStn));
            List<ItemStack> icons = RecipeViewerIndex.workstationsIconsForPrefix(stationCategoryPrefix(i));
            if (BetterRecipeBook.config.hideNoRecipeBookStationObjects) {
                // This tooltip belongs to a smelting recipe (an object): icons
                // of workstations without a recipe-book system are hidden.
                icons = filterRecipeBookStations(icons);
            }
            components.add(new StationLineTooltipComponent(List.of(
                    new StationLineTooltipComponent.Segment(line.getVisualOrderText(), icons, false))));
        }
        return components;
    }

    private static String stationLabel(int i) {
        return switch (i) {
            case 0 -> "brbe.cooktime.furnace";
            case 1 -> "brbe.cooktime.blast";
            case 2 -> "brbe.cooktime.smoker";
            case 3 -> "brbe.cooktime.campfire";
            default -> "brbe.cooktime.furnace";
        };
    }

    /** Recipe-book category path prefix of the {@code index}-th furnace station,
     *  used to look up the workstations that serve that subcategory. */
    private static String stationCategoryPrefix(int i) {
        return switch (i) {
            case 0 -> "furnace_";
            case 1 -> "blast_furnace_";
            case 2 -> "smoker_";
            case 3 -> "campfire";
            default -> "furnace_";
        };
    }

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

    /** One labelled cook-time line: a leading white bullet (when the open
     *  screen matches this station) followed by {@code <label><value>}. */
    private static Component stationTimeLine(String labelKey, String value,
                                             Style valueStyle, boolean currentStation) {
        MutableComponent line = Component.literal("");
        if (currentStation) {
            line.append(Component.literal("•").withStyle(ChatFormatting.WHITE));
        }
        // Label, separator and value share the station colour; only the bullet
        // stays white.
        line.append(Component.translatable(labelKey).withStyle(valueStyle));
        line.append(Component.literal("：").withStyle(valueStyle));
        line.append(Component.literal(value).withStyle(valueStyle));
        return line;
    }

    /** A tooltip row drawing text segments with their workstation item icons
     *  immediately after each segment (text drawn in {@code extractText}, icons
     *  in {@code extractImage}). */
    private static final class StationLineTooltipComponent
            implements net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent {
        private static final int ICON_SIZE = 16;
        private static final int GAP = 2;

        record Segment(FormattedCharSequence text, List<ItemStack> icons, boolean dotBelow) {}

        private final List<Segment> segments;

        StationLineTooltipComponent(List<Segment> segments) {
            this.segments = segments;
        }

        @Override
        public int getWidth(Font font) {
            int width = 0;
            for (Segment seg : segments) {
                width += font.width(seg.text());
                if (!seg.icons().isEmpty()) {
                    width += GAP + ICON_SIZE * seg.icons().size();
                }
                width += GAP;
            }
            return width;
        }

        @Override
        public int getHeight(Font font) {
            int base = Math.max(font.lineHeight, ICON_SIZE);
            for (Segment seg : segments) {
                if (seg.dotBelow()) {
                    return base + font.lineHeight;
                }
            }
            return base;
        }

        @Override
        public void renderText(GuiGraphics gui, Font font, int x, int y) {
            int cx = x;
            for (Segment seg : segments) {
                gui.drawString(font, seg.text(), cx, y, -1, true);
                cx += font.width(seg.text());
                if (!seg.icons().isEmpty()) {
                    cx += GAP + ICON_SIZE * seg.icons().size();
                }
                cx += GAP;
            }
        }

        @Override
        public void renderImage(Font font, int x, int y, int width, int height,
                                 GuiGraphics gui) {
            int cx = x;
            for (Segment seg : segments) {
                int textW = font.width(seg.text());
                if (seg.dotBelow()) {
                    FormattedCharSequence dot = Component.literal("•")
                            .withStyle(ChatFormatting.WHITE).getVisualOrderText();
                    int dotW = font.width(dot);
                    gui.drawString(font, dot, cx + (textW - dotW) / 2, y + font.lineHeight, -1, true);
                }
                cx += textW + GAP;
                if (!seg.icons().isEmpty()) {
                    // Vertically centre the 16px icon against the text line
                    // (whose height is smaller than the icon), not against the
                    // whole row — the icon centre aligns with the text centre.
                    int iy = y + (font.lineHeight - ICON_SIZE) / 2;
                    for (ItemStack icon : seg.icons()) {
                        gui.renderItem(icon, cx, iy, 0);
                        cx += ICON_SIZE;
                    }
                }
                cx += GAP;
            }
        }
    }

    /** Tooltip title row with the up marker pinned to an EXACT pixel anchor
     *  (no space padding between title and marker — a 4px space grid cannot
     *  reproduce arbitrary glyph advances, which is what made the marker
     *  drift before).  The row width is the title-plus-marker footprint so
     *  the tooltip keeps its current size. */
    private static final class StationTitleMarkerTooltipComponent
            implements net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent {
        private final net.minecraft.util.FormattedCharSequence title;
        private final net.minecraft.util.FormattedCharSequence marker;
        private final int anchorX;

        StationTitleMarkerTooltipComponent(net.minecraft.util.FormattedCharSequence title,
                                            net.minecraft.util.FormattedCharSequence marker,
                                            int anchorX) {
            this.title = title;
            this.marker = marker;
            this.anchorX = anchorX;
        }

        @Override
        public int getWidth(Font font) {
            return anchorX + font.width(marker);
        }

        @Override
        public int getHeight(Font font) {
            return font.lineHeight;
        }

        @Override
        public void renderText(GuiGraphics gui, Font font, int x, int y) {
            gui.drawString(font, title, x, y, -1, true);
            gui.drawString(font, marker, x + anchorX, y, -1, true);
        }
    }

    /** Blank tooltip row holding only the down marker, drawn at the SAME
     *  anchorX as the up marker so ▲ and ▼ share one vertical line. */
    private final class StationMarkerTooltipComponent
            implements net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent {
        private final net.minecraft.util.FormattedCharSequence marker;
        private final int anchorX;

        StationMarkerTooltipComponent(net.minecraft.util.FormattedCharSequence marker, int anchorX) {
            this.marker = marker;
            this.anchorX = anchorX;
        }

        @Override
        public int getWidth(Font font) {
            return anchorX + font.width(marker);
        }

        @Override
        public int getHeight(Font font) {
            return font.lineHeight;
        }

        @Override
        public void renderText(GuiGraphics gui, Font font, int x, int y) {
            gui.drawString(font, marker, x + anchorX, y, -1, true);
        }
    }

    /** Tooltip title row of a category tab carrying the sliding-window
     *  indicators — ◀/◁ (left edge of the window) and ▶/▷ (right edge), each
     *  at an EXACT pixel anchor: the left marker 4 spaces (16px) right of the
     *  title, the right marker 1 space (4px) right of the left marker (no
     *  space padding — a 4px space grid cannot reproduce arbitrary glyph
     *  advances).  The row width is the title-plus-markers footprint so the
     *  tooltip keeps its current size. */
    private final class TabMarkerTitleTooltipComponent
            implements net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent {
        private final net.minecraft.util.FormattedCharSequence title;
        private final net.minecraft.util.FormattedCharSequence leftMarker;
        private final net.minecraft.util.FormattedCharSequence rightMarker;
        private final int leftX;
        private final int rightX;

        TabMarkerTitleTooltipComponent(net.minecraft.util.FormattedCharSequence title,
                                       net.minecraft.util.FormattedCharSequence leftMarker,
                                       net.minecraft.util.FormattedCharSequence rightMarker,
                                       int leftX, int rightX) {
            this.title = title;
            this.leftMarker = leftMarker;
            this.rightMarker = rightMarker;
            this.leftX = leftX;
            this.rightX = rightX;
        }

        @Override
        public int getWidth(Font font) {
            return rightX + font.width(rightMarker);
        }

        @Override
        public int getHeight(Font font) {
            return font.lineHeight;
        }

        @Override
        public void renderText(GuiGraphics gui, Font font, int x, int y) {
            gui.drawString(font, title, x, y, -1, true);
            gui.drawString(font, leftMarker, x + leftX, y, -1, true);
            gui.drawString(font, rightMarker, x + rightX, y, -1, true);
        }
    }

    /** Whether the currently open container menu is of the given type. */
    private static boolean menuIs(Class<?> menuClass) {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && menuClass.isInstance(mc.player.containerMenu);
    }

    /** Cook time in seconds (with unit) — whole seconds when the tick count is
     *  a multiple of 20, otherwise one decimal. */
    private static String cookSeconds(int ticks) {
        String value = ticks % 20 == 0 ? String.valueOf(ticks / 20)
                : String.format(Locale.ROOT, "%.1f", ticks / 20.0f);
        return value + "s";
    }

    /** One tooltip row tagging a non-furnace recipe with its workstation icon
     *  and name (e.g. "Workstation: [crafting table]").  {@code currentCategory}
     *  is the query window's category while one is open; the pins-only (no
     *  window) pass supplies null and the entry's owning category is resolved
     *  via {@link #categoryFor(RecipeDisplayEntry)} instead. */
    private static List<net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent>
            stationIconsTooltipComponents(RecipeDisplayEntry entry,
                                          RecipeViewerCategory currentCategory) {
        // The viewer's own category while it is open; otherwise resolve the
        // entry's owning category.  Pin overlays render this tooltip with the
        // viewer closed, where currentCategory is null — dereferencing it
        // there used to crash (NPE during extractDeferredElements).
        RecipeViewerCategory category = currentCategory != null
                ? currentCategory : categoryFor(entry);
        if (category == null) {
            // No category context (viewer closed, entry unresolvable): omit
            // the workstation row instead of crashing.
            return List.of();
        }
        // BRBE (三十一): only the furnace and fuel categories keep their
        // workstation rows in the recipe tooltip — every other category's
        // tooltip drops them (its workstations now live in the viewer's left
        // station column instead).
        if (!"furnace".equals(category.id()) && !"fuel".equals(category.id())) {
            return List.of();
        }
        List<ItemStack> icons = category.stationIconsFor(entry);
        if (BetterRecipeBook.config.hideNoRecipeBookStationObjects) {
            // Hide the icons of workstations without a recipe-book system on
            // the tooltip; the object itself survives because it has at least
            // one legitimate workstation (the filter guarantees it).
            List<ItemStack> filtered = filterRecipeBookStations(icons);
            if (filtered.isEmpty()) {
                // The legitimate workstation is not one the category registered
                // (e.g. the display declares it) — omit the icon row entirely.
                return List.of();
            }
            icons = filtered;
        }
        if (icons.isEmpty()) {
            icons = List.of(category.icon());
        }
        List<net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent> components =
                new ArrayList<>();
        components.add(net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent
                .create(Component.empty().getVisualOrderText()));
        components.add(new StationLineTooltipComponent(List.of(
                new StationLineTooltipComponent.Segment(Component.empty().getVisualOrderText(), icons, false))));
        return components;
    }

    /** The category owning {@code entry}, for tooltips rendered without the
     *  query viewer open (pin overlays).  Built-in categories are matched by
     *  the entry's display type; plugin (synthetic) entries by asking the
     *  plugin categories.  Returns null when nothing matches — the tooltip
     *  then omits the workstation row. */
    private static RecipeViewerCategory categoryFor(RecipeDisplayEntry entry) {
        Object display = entry.display();
        if (display instanceof StonecutterRecipeDisplay) {
            return byId("stonecutting");
        }
        if (display instanceof SmithingRecipeDisplay) {
            return byId("smithing");
        }
        if (display instanceof ShapedCraftingRecipeDisplay
                || display instanceof ShapelessCraftingRecipeDisplay) {
            return byId("crafting");
        }
        if (RecipeViewerEngine.isSynthetic(entry.id())) {
            Minecraft mc = Minecraft.getInstance();
            ItemStack result = mc == null || mc.level == null
                    ? null : resolveOutput(entry, mc, 0);
            if (result != null && !result.isEmpty()) {
                for (RecipeViewerCategory cat : RecipeViewerCategories.all()) {
                    if (cat.isGridCategory()) continue;
                    for (RecipeDisplayEntry hit : cat.query(result, false)) {
                        if (hit.id().equals(entry.id())) return cat;
                    }
                }
            }
        }
        return null;
    }

    /** The registered category with the given id, or null. */
    private static RecipeViewerCategory byId(String id) {
        for (RecipeViewerCategory cat : RecipeViewerCategories.all()) {
            if (id.equals(cat.id())) return cat;
        }
        return null;
    }

    /** Info rows of a grid category's tooltip: fuel burn rows / compost chance
     *  / JEI info text — one labelled row or text line per entry, exactly like
     *  the fuel category's burn-time rows.  The owning category is explicit:
     *  browse-all cells keep their own category's rows. */
    private List<net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent>
            gridTooltipComponents(RecipeViewerCategory category, ItemStack hovered) {
        if (category instanceof FuelRecipeCategory) {
            return fuelTooltipComponents((FuelRecipeCategory) category, hovered);
        }
        if (category instanceof CompostRecipeCategory compost) {
            return compostTooltipComponents(compost, hovered);
        }
        if (category instanceof InfoRecipeCategory info) {
            return infoTooltipComponents(info, hovered);
        }
        return List.of();
    }

    /** Compost chance rows: one "概率：25%" line (JEI's own percentage —
     *  {@code floor(chance * 100)}). */
    private List<net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent>
            compostTooltipComponents(CompostRecipeCategory category, ItemStack hovered) {
        int percent = (int) Math.floor(category.chanceFor(hovered) * 100);
        List<net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent> components =
                new ArrayList<>();
        components.add(net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent
                .create(Component.empty().getVisualOrderText()));
        components.add(net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent
                .create(Component.translatable("brbe.category.compost.chance", percent)
                        .withStyle(ChatFormatting.GREEN).getVisualOrderText()));
        return components;
    }

    /** JEI info text lines of the hovered item, one tooltip row per line
     *  (each info page's description, joined in registration order). */
    private List<net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent>
            infoTooltipComponents(InfoRecipeCategory category, ItemStack hovered) {
        List<FormattedText> lines = category.descriptionFor(hovered);
        if (lines.isEmpty()) return List.of();
        List<net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent> components =
                new ArrayList<>(lines.size() + 1);
        components.add(net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent
                .create(Component.empty().getVisualOrderText()));
        for (FormattedText line : lines) {
            components.add(net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent
                    .create(net.minecraft.locale.Language.getInstance().getVisualOrder(line)));
        }
        return components;
    }

    /** Burn-time tooltip rows for a fuel: one labelled row per furnace station
     *  (furnace / blast furnace / smoker), each showing how many items the fuel
     *  can smelt plus the station's workstation icon.  No shift variation, no
     *  campfire. */
    private List<net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent>
            fuelTooltipComponents(FuelRecipeCategory category, ItemStack fuel) {
        int burn = category.burnDuration(fuel);
        // JEI-style: report how many standard (furnace 200-tick) items the fuel
        // can smelt, regardless of the station's own cook time.
        int[] cookTimes = { 200, 200, 200 };
        String unit = Component.translatable("brbe.cooktime.unit.items").getString();
        List<net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent> components =
                new ArrayList<>();
        components.add(net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent
                .create(Component.empty().getVisualOrderText()));
        for (int i = 0; i < 3; i++) {
            String value = fuelCount(burn, cookTimes[i]) + unit;
            Component line = stationTimeLine(stationLabel(i), value, stationStyle(i), false);
            // Every workstation serving this furnace subcategory, not just the
            // vanilla representative — the same aggregated lookup the furnace
            // recipe tooltip uses, so JEI-plugin workstations (a mod smelter
            // registered under minecraft:blasting, a skillet under campfire, …)
            // show up on the matching row.
            List<ItemStack> icons = RecipeViewerIndex.workstationsIconsForPrefix(stationCategoryPrefix(i));
            components.add(new StationLineTooltipComponent(List.of(
                    new StationLineTooltipComponent.Segment(line.getVisualOrderText(), icons, false))));
        }
        return components;
    }

    /** How many items {@code burn} ticks smelt at {@code cookTime} ticks each —
     *  whole count when it divides evenly, otherwise one decimal. */
    private String fuelCount(int burn, int cookTime) {
        if (burn <= 0 || cookTime <= 0) return "0";
        return burn % cookTime == 0 ? String.valueOf(burn / cookTime)
                : String.format(Locale.ROOT, "%.1f", burn / (float) cookTime);
    }

    /** Turn-page buttons above the box (left edge aligned with the box left). */
    private void drawPageControls(GuiGraphics gui, int mouseX, int mouseY) {
        if (!isPaged()) return;
        Minecraft mc = Minecraft.getInstance();
        int bx = pageBtnX();
        int by = boxTop();
        int btnY = by - PAGE_BTN_HEIGHT - 2 + PAGE_BTN_SHIFT_Y;
        boolean wrap = scrollWrap();
        boolean prevActive = wrap || viewerPage > 0;
        boolean nextActive = wrap || viewerPage < viewerPageCount - 1;
        drawPageButton(gui, bx, btnY, false, prevActive, mouseX, mouseY);
        drawPageButton(gui, bx + 15, btnY, true, nextActive, mouseX, mouseY);
        if ((!previewOwnsCursor(mouseX, mouseY)
                && ((prevActive && inside(mouseX, mouseY, bx, btnY, PAGE_BTN_WIDTH, PAGE_BTN_HEIGHT))
                || (nextActive && inside(mouseX, mouseY, bx + 15, btnY, PAGE_BTN_WIDTH, PAGE_BTN_HEIGHT))))) {
            gui.setTooltipForNextFrame(mc.font, Component.literal((viewerPage + 1) + "/" + viewerPageCount),
                    mouseX, mouseY, ClientCompat.VIEWER_TOOLTIP_STYLE);
        }
    }

    private void drawPageButton(GuiGraphics gui, int x, int y, boolean next,
                                      boolean active, int mouseX, int mouseY) {
        int u = next ? 14 : 0;
        if (active && !previewOwnsCursor(mouseX, mouseY)
                && inside(mouseX, mouseY, x, y, PAGE_BTN_WIDTH, PAGE_BTN_HEIGHT)) {
            u += 28;
        }
        int v = active ? 0 : 13;
        gui.blit(RenderPipelines.GUI_TEXTURED, LEI_PAGE_BUTTONS, x, y, u, v,
                PAGE_BTN_WIDTH, PAGE_BTN_HEIGHT, 256, 256);
    }

    /** Full-screen dim scrim: visually de-activates the desktop under the
     *  modal window (the dead cursor already makes it inert for input). */
    /** Draw the title bar: the band IS the extended panel background (the
     *  background blits in {@link #render} / {@link #drawItemGrid} already
     *  cover it), so this only paints the selected tab's title (centred) and
     *  the ✕ close button (right end, top edge aligned with the turn-page
     *  button row). */
    /** Draw the title bar: the selected tab's title LEFT-ALIGNED — right
     *  next to the turn-page buttons when paged (otherwise at the band's left
     *  edge) — floating in the turn-page buttons' row.  (The ✕ close button
     *  has been removed; closing = right-click on the window.) */
    /** The title's text x (band left edge + 6px padding). */
    private int titleTextX() {
        return windowChromeRect()[0] + 6;
    }

    /** Whether the turn-page buttons share the title band: this window spans
     *  more than one page, so {@link #drawPageControls} right-aligns the two
     *  buttons inside the band and the title has to make room for them.
     *
     *  <p>LAYOUT-time predicate — deliberately NOT {@link #isPaged()}: the box is
     *  laid out (open / category switch / grid rebuild) BEFORE
     *  {@code setViewerActive(true)}, so inside {@link #computeBoxSize} /
     *  {@link #fitBoxToPage} the viewer's active flag is still false on the
     *  FIRST open.  {@code isPaged()} would then answer "no buttons" and the
     *  title would reserve no room for the buttons it is about to share its row
     *  with (31px short) — the title degrades to "…" even though
     *  {@link #ensureTitleWidth} was supposed to create another column.  The
     *  layout and the drawn bound both use THIS predicate, so the reserved width
     *  and the rendered truncation limit can never disagree. */
    private boolean pageButtonsInBand() {
        return viewerPageCount > 1;
    }

    /** The title's text right bound: while the turn-page buttons share the band
     *  it stops 4px before them, otherwise at the band's right edge (6px
     *  padding). */
    private int titleTextRightBound() {
        int[] r = windowChromeRect();
        return pageButtonsInBand() ? pageBtnX() - 4 : r[0] + r[2] - 6;
    }

    /** The title text as drawn (truncated with "…" when too long). */
    private java.lang.String titleText() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.font == null) return titleBarTitle();
        String title = titleBarTitle();
        if (mc.font.width(title) > titleTextRightBound() - titleTextX()) {
            title = mc.font.plainSubstrByWidth(title,
                    Math.max(8, titleTextRightBound() - titleTextX() - mc.font.width("…")), true) + "…";
        }
        return title;
    }

    /** The title text row's top (centred on the turn-page buttons) minus 1px
     *  of padding — the top of the title's clickable bounds. */
    private int titleTextY() {
        Minecraft mc = Minecraft.getInstance();
        int lineHeight = mc != null && mc.font != null ? mc.font.lineHeight : 9;
        return pageBtnY() + (PAGE_BTN_HEIGHT - lineHeight) / 2 - 1;
    }

    /** Draw the title bar: the selected tab's title LEFT-ALIGNED at the band's
     *  left edge (always — independent of the turn-page buttons, which are
     *  right-aligned), floating in the turn-page buttons' row.  The title is
     *  clickable — a left-click toggles browse-all (show all categories) —
     *  with a grey italic hint tooltip.  (The ✕ close button has been removed;
     *  closing = right-click on the window.) */
    private void drawTitleBar(GuiGraphics gui, int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.font == null) return;
        Font font = mc.font;
        String title = titleText();
        int x = titleTextX();
        // Vertically centred on the turn-page buttons' centreline.
        int textY = titleTextY() + 1;
        gui.drawString(font, title, x, textY, 0xFF000000, false);
        // Title click hint (grey + italic) and pointing-hand cursor while
        // hovering the title's text bounds — the same deferred-tooltip path
        // the category tabs / station column use.
        if (!previewOwnsCursor(mouseX, mouseY)) {
            int w = font.width(title) + 2;
            int h = font.lineHeight + 2;
            if (inside(mouseX, mouseY, x, textY - 1, w, h)) {
                gui.requestCursor(com.mojang.blaze3d.platform.cursor.CursorTypes.POINTING_HAND);
                // State-dependent hint: normal query → "show all recipes",
                // browsing (all recipes shown) → "hide irrelevant recipes".
                net.minecraft.network.chat.Style hintBase =
                        net.minecraft.network.chat.Style.EMPTY
                                .withColor(net.minecraft.ChatFormatting.GRAY)
                                .withItalic(true);
                net.minecraft.network.chat.Component hint =
                        net.minecraft.network.chat.Component.empty()
                                .append(net.minecraft.network.chat.Component
                                        .literal("按“").withStyle(hintBase))
                                .append(net.minecraft.network.chat.Component
                                        .literal("左键")
                                        .withStyle(hintBase.withBold(true)))
                                .append(net.minecraft.network.chat.Component
                                        .literal("”").withStyle(hintBase))
                                .append(net.minecraft.network.chat.Component
                                        .literal(browseAllMode
                                                ? "以隐藏不相关配方。"
                                                : "以显示所有配方。")
                                        .withStyle(hintBase));
                pendingTabTooltip = java.util.List.of(
                        net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent
                                .create(hint.getVisualOrderText()));
                pendingTabTooltipX = mouseX;
                pendingTabTooltipY = mouseY;
                pendingTabTooltipStyle = ClientCompat.VIEWER_TOOLTIP_STYLE;
            }
        }
        // (The ✕ close button has been removed: closing the window is the
        // mouse-right-click anywhere on the window, see mouseClicked.)
    }

    /** Start (title bar / close button) or handle an in-progress window drag.
     *  A RIGHT-click (button 1) anywhere on the window closes it (handled in
     *  {@link #mouseClicked} — the only close action: the ✕ button has been
     *  removed and ESC / outside clicks do nothing); the turn-page buttons
     *  (which live inside the band) fall through to their own handler in the
     *  normal click chain; any other left-press in the band starts the drag
     *  (desktop semantics: grab the title bar and move the window); a
     *  subsequent mouseDragged moves the whole box. */
    private boolean handleWindowChromeClick(MouseButtonEvent event) {
        int[] r = windowChromeRect();
        // (Right-click closing is handled globally in mouseClicked — anywhere
        // in the window region — so the band needs no branch of its own.)
        if (event.button() != 0) return false;
        // Left-press on the band TITLE arms the browse-all toggle ("show all
        // categories"): fired on RELEASE only when the window was NOT dragged
        // (a press that drags the window never triggers it — the press falls
        // through to the drag grab so the title still moves the window).
        net.minecraft.client.Minecraft mc0 = net.minecraft.client.Minecraft.getInstance();
        if (mc0 != null && mc0.font != null) {
            int tx = titleTextX();
            int ty = titleTextY();
            int tw = mc0.font.width(titleText()) + 2;
            int th = mc0.font.lineHeight + 2;
            if (inside(event.x(), event.y(), tx, ty, tw, th)) {
                titlePressActive = true;
                titleDragMoved = false;
            }
        }
        // Page buttons sit inside the band; a press on them is not a drag.
        if (isPaged()) {
            int bx = pageBtnX();
            int btnY = pageBtnY();
            if (inside(event.x(), event.y(), bx, btnY, PAGE_BTN_WIDTH, PAGE_BTN_HEIGHT)
                    || inside(event.x(), event.y(), bx + 15, btnY,
                            PAGE_BTN_WIDTH, PAGE_BTN_HEIGHT)) {
                return false;
            }
        }
        if (inside(event.x(), event.y(), r[0], r[1], r[2], r[3])) {
            windowDragging = true;
            windowDragMoved = false;
            windowDragOffX = Mth.floor(event.x()) - boxLeft();
            windowDragOffY = Mth.floor(event.y()) - boxTop();
            return true;
        }
        return false;
    }

    /** Move the window with the cursor during a title-bar drag.  A pure
     *  translation: the whole geometry (box, anchor, bottom, overlay buttons
     *  and grid cells) shifts by the same delta — no re-fit, so the single
     *  band limit does not fight the drag. */
    private boolean handleWindowDragged(MouseButtonEvent event) {
        if (!windowDragging) return false;
        if (event.button() != 0) {
            windowDragging = false;
            return false;
        }
        int nx = Mth.floor(event.x()) - windowDragOffX;
        int ny = Mth.floor(event.y()) - windowDragOffY;
        // THE ONLY position limit (2026-09-02 redesign): the extension band
        // must stay inside the screen VERTICALLY — its top line never leaves
        // the screen top and its bottom line never leaves the screen bottom.
        // Horizontal is completely free (the window may hang off the screen's
        // left/right edges) and the box below the band may protrude past the
        // screen bottom.
        ny = clampBandTop(ny);
        int dx = nx - boxX;
        int dy = ny - boxY;
        if (dx == 0 && dy == 0) return true;
        windowDragMoved = true;
        if (titlePressActive) titleDragMoved = true;
        boxX += dx;
        boxY += dy;
        anchorScreenX += dx;
        anchorScreenY += dy;
        bottomAnchor += dy;
        if (!isGridMode()) {
            OverlayRecipeComponentAccessor acc = (OverlayRecipeComponentAccessor) overlay;
            acc.setX(acc.getX() + dx);
            acc.setY(acc.getY() + dy);
            for (AbstractWidget w : acc.getRecipeButtons()) {
                w.setX(w.getX() + dx);
                w.setY(w.getY() + dy);
            }
        }
        return true;
    }

    /** End a title-bar drag (consumes the release so nothing falls through).
     *  A press that started on the band TITLE and never moved the window
     *  (release without a drag) fires the browse-all toggle here. */
    private boolean handleWindowReleased() {
        if (!windowDragging) return false;
        boolean fire = titlePressActive && !titleDragMoved;
        titlePressActive = false;
        titleDragMoved = false;
        windowDragMoved = false;
        windowDragging = false;
        if (fire) {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc != null) {
                ClientCompat.playPageFlipSound(mc);
                toggleBrowseAll();
            }
        }
        // The drag (or the browse-all toggle) changed position / page / category.
        syncSpec();
        return true;
    }

    /** Public drag/release entry points for the screen mixins (window drag
     *  hooks).  The mixins route mouseDragged / mouseReleased here BEFORE the
     *  pin handlers, because an active window drag owns the cursor. */
    public boolean mouseDragged(MouseButtonEvent event) {
        return handleWindowDragged(event);
    }

    public boolean mouseReleased(MouseButtonEvent event) {
        return handleWindowReleased();
    }

    /** Whether a title-bar drag is currently in progress (window owns the
     *  cursor: no pin drag, no click-through). */
    public boolean isWindowDragging() {
        return windowDragging;
    }

    private boolean inside(double x, double y, int left, int top, int width, int height) {
        return x >= left && x < left + width && y >= top && y < top + height;
    }

    /** Dismiss the viewer: clear state before hiding so the setVisible guard
     *  does not cancel this sanctioned close.  The persisted spec is KEPT
     *  (materialized flag cleared): the window restores on the next container
     *  screen, exactly like a pin overlay surviving its host screen. */
    public void close() {
        close(false);
    }

    /** Close the window.  {@code removePersisted} = the right-click close
     *  gesture: the entry is removed from {@code brbe.queryviewers.json}
     *  permanently (no future restore). */
    public void close(boolean removePersisted) {
        if (!isActive() && !overlay.isVisible()) return;
        // Close the popup layer too: its active flag must not keep blocking
        // button hover / clicks on the next screen.
        RecipePopupLayer.close();
        RecipeViewerIndex.setViewerActive(false);
        RecipeViewerIndex.clearViewerPartials(currentCollection);
        currentCollection = null;
        ownerScreen = null;
        queryTarget = null;
        currentCategory = null;
        tabWindowStart = 0;
        stationColumnItems = List.of();
        stationScroll = 0;
        bottomAnchor = 0;
        anchorScreenX = 0;
        anchorScreenY = 0;
        viewerRecipes = List.of();
        gridItems = List.of();
        gridHoverStack = null;
        gridHoverCategory = null;
        resetBrowseAllState();
        viewerPage = 0;
        viewerPageCount = 1;
        viewerZ = -1;
        hoverPopupField = null;
        hoveredViewerButton = null;
        windowDragging = false;
        windowDragMoved = false;
        overlay.setVisible(false);
        if (removePersisted) {
            if (spec != null) {
                viewerSpecs.remove(spec);
                saveViewerSpecs();
            }
        } else if (spec != null) {
            // Passive close: keep the spec, allow a new materialization on
            // the next container screen.
            spec.materialized = false;
        }
        RecipeViewerOverlay.onWindowClosedSelf(this);
    }

    /** Create or refresh this window's persistent entry (query identity +
     *  mode + category + page + position) and save the file asynchronously.
     *  Called whenever a user-visible aspect of the window changes. */
    private void syncSpec() {
        // 预览模式：查询窗口不持久化——不写盘，并清掉历史遗留的窗口条目
        // （配置文件曾持久化的旧窗口不会再恢复，文件也随之清空）。
        if (BetterRecipeBook.config.previewMode) {
            spec = null;
            if (!viewerSpecs.isEmpty()) {
                viewerSpecs.clear();
                saveViewerSpecs();
            }
            return;
        }
        if (queryTarget == null || queryTarget.isEmpty()) return;
        if (spec == null) {
            spec = new ViewSpec();
            viewerSpecs.add(spec);
        }
        // A window is bound to the spec right now: the restore pass must not
        // create a SECOND window for it (it only re-creates after a passive
        // close clears this flag).
        spec.materialized = true;
        spec.item = BuiltInRegistries.ITEM.getKey(queryTarget.getItem()).toString();
        spec.usage = queryUsage;
        spec.category = currentCategory == null ? null : currentCategory.id();
        spec.page = viewerPage;
        spec.x = boxX;
        spec.y = boxY;
        saveViewerSpecs();
    }

    /**
     * Open the viewer for {@code target} (from the hovered item / recipe button /
     * ghost slot), anchored around it.  Returns false when there is no target or
     * no matching recipes, leaving the key event for vanilla handling.
     */
    private boolean open(AbstractContainerScreen<?> screen, boolean viewUsage) {
        ItemStack target = PinOverlayManager.captureTarget(screen);
        if (target.isEmpty()) return false;
        return openFor(screen, target, viewUsage);
    }

    /** Open the viewer for an explicit {@code target} — the shared body of
     *  {@link #open} and the left station-column click query. */
    private boolean openFor(AbstractContainerScreen<?> screen, ItemStack target,
                                   boolean viewUsage) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return false;

        anchorOverlayWidget = null;
        anchorBookButton = null;
        resetBrowseAllState();

        // Smart default category for this item, falling back to null (no open)
        // when no category has results.
        queryTarget = target;
        queryUsage = viewUsage;
        currentCategory = RecipeViewerCategories.defaultFor(target, viewUsage, screen.getMenu());
        if (currentCategory == null) {
            // BRBE's engine has no category with results — typically a mod item
            // while JEI is installed (jei-plugins is not deployed with JEI).
            // A re-open (R/U while the viewer is already up) that matches no
            // category must not leave the previous open's state behind (viewer
            // still active, currentCategory null) — that NPEs on the next
            // render's tooltip path.  Reset the viewer, then fall back so the
            // query still opens.
            if (RecipeViewerIndex.isViewerActive()) {
                close();
            }
            // The "hide objects of workstations without a recipe book" mode
            // suppresses the JEI fallback too: nothing BRBE can judge may
            // leak through the external viewer.
            if (BetterRecipeBook.config.hideNoRecipeBookStationObjects) {
                return false;
            }
            return fallbackToViewer(target, viewUsage);
        }
        List<RecipeDisplayEntry> hits = null;
        if (currentCategory.isGridCategory()) {
            computeGridBoxSize();
        } else {
            hits = filterByRecipeBookStations(currentCategory.query(target, viewUsage));
            if (hits.isEmpty()) {
                // The default category's hits were all filtered away (or the
                // default is empty): another category may still show this
                // query (e.g. the fuel tab for a burnable workstation with no
                // unlocked recipes).  Pick it before giving up.
                RecipeViewerCategory alt = bestContentCategory(target, viewUsage, currentCategory);
                if (alt != null) {
                    currentCategory = alt;
                    if (alt.isGridCategory()) {
                        computeGridBoxSize();
                    } else {
                        hits = filterByRecipeBookStations(alt.query(target, viewUsage));
                        if (hits.isEmpty()) {
                            if (BetterRecipeBook.config.hideNoRecipeBookStationObjects) {
                                return false;
                            }
                            return fallbackToViewer(target, viewUsage);
                        }
                        computeBoxSize(hits);
                    }
                } else if (BetterRecipeBook.config.hideNoRecipeBookStationObjects) {
                    // Every hit was hidden by the filter: the viewer stays
                    // closed and the external viewer is not consulted either.
                    return false;
                } else {
                    return fallbackToViewer(target, viewUsage);
                }
            } else {
                computeBoxSize(hits);
            }
        }

        int guiW = mc.getWindow().getGuiScaledWidth();
        int guiH = mc.getWindow().getGuiScaledHeight();
        AbstractContainerScreenAccessor acc = (AbstractContainerScreenAccessor) screen;
        Slot hoveredSlot = acc.brbe$getHoveredSlot();

        int anchorX;
        int anchorY;
        if (anchorOverlayWidget != null) {
            // Querying from a viewer-overlay recipe button: anchor to the button.
            anchorX = anchorOverlayWidget.getX();
            anchorY = anchorOverlayWidget.getY();
        } else if (hoveredSlot != null) {
            anchorX = acc.brbe$getLeftPos() + hoveredSlot.x;
            anchorY = acc.brbe$getTopPos() + hoveredSlot.y;
        } else if (anchorBookButton != null) {
            anchorX = anchorBookButton.getX();
            anchorY = anchorBookButton.getY();
        } else {
            anchorX = (guiW - 147) / 2 + 73;
            anchorY = (guiH - 166) / 2 + 83;
        }
        // The query anchors to the POINTER (falling back to the anchors above
        // when the pointer is outside the window): unless the band limit
        // kicks in, the first object's centre sits on the mouse.
        if (mc.mouseHandler != null && mc.getWindow() != null) {
            int mx = Mth.floor(mc.mouseHandler.getScaledXPos(mc.getWindow()));
            int my = Mth.floor(mc.mouseHandler.getScaledYPos(mc.getWindow()));
            if (mx >= 0 && mx < guiW && my >= 0 && my < guiH) {
                anchorX = mx;
                anchorY = my;
            }
        }

        if (hits != null) computeBoxSize(hits);

        // Align the first row's first object's CENTRE to the anchor: column 0
        // sits at boxX+4 (centre boxX+16), and — rows grow upward — row 0 sits
        // at the box bottom (centre boxY+boxH-16).  The anchor is the box
        // BOTTOM (bottomAnchor = anchorY+16), NOT a full-page-derived boxY:
        // the single band limit ({@link #clampBandTop}) runs
        // later in fitBoxToPage against the ACTUAL post-shrink box size, so
        // the pointer-anchored position survives unless keeping the band on
        // screen requires a shift.
        boxX = anchorX - 16;
        boxY = anchorY - boxH + 16;
        anchorScreenX = anchorX;
        anchorScreenY = anchorY;
        // Anchor the tab strip (box bottom) to the first object's centre.
        bottomAnchor = anchorY + 16;

        ownerScreen = screen;
        viewerPage = 0;
        if (currentCategory.isGridCategory()) {
            // A grid category shows the query's item grid: a usage query of an
            // item shows that item alone (JEI per-item semantics), a usage
            // query of its station shows the whole list.  Grid categories are
            // exempt from the workstation filter, so an illegal station (e.g.
            // BetterEnd's end stone smelter) lands here too and must NOT be
            // treated as the queried item itself.
            rebuildGrid(currentCategory.gridItems(queryTarget, queryUsage));
        } else {
            rebuildWithHits(hits);
        }
        // fitBoxToPage has already settled the anchor to the actual first-
        // object centre (the band limit included).
        repaginateToSelected();
        rebuildStationColumn();
        viewerZ = PinOverlayManager.nextZ();
        RecipeViewerIndex.setViewerActive(true);
        RecipeViewerIndex.setViewerOpenedFromBook(anchorBookButton != null);
        BetterRecipeBook.LOGGER.warn("[VIEWER-DBG] open target={} usage={} cat={} "
                        + "window#{} box=({},{},{}x{}) anchor=({},{}) bottomA={} "
                        + "stationCol={} grid={} gui={}x{} screen={}",
                target.getHoverName().getString(), viewUsage,
                currentCategory == null ? "null" : currentCategory.id(),
                WINDOWS.size(), boxX, boxY, boxW, boxH, anchorScreenX, anchorScreenY,
                bottomAnchor, stationColumnItems.size(),
                gridHoverStack == null ? "-" : gridHoverStack.getHoverName().getString(),
                guiW, guiH, screen.getClass().getSimpleName());
        // Persist the fresh window (identity + mode + category + page + position).
        syncSpec();
        return true;
    }

    /** Restore this window from {@code spec} onto {@code screen}: re-run the
     *  saved query, pin the window at the saved position and re-select the
     *  saved category / page.  Returns false when the spec cannot be resolved
     *  yet (item unknown, category empty, engine building) — the spec stays
     *  pending and is retried on the next render, exactly like a pin spec.
     *  The band limit still applies: the saved position is the starting
     *  point, the clamp only shifts it when the band would leave the screen. */
    private boolean restoreFrom(ViewSpec spec, AbstractContainerScreen<?> screen) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return false;
        if (spec.item == null || spec.item.isEmpty()) return false;
        ItemStack target = BuiltInRegistries.ITEM.getOptional(
                Identifier.tryParse(spec.item)).map(item -> new ItemStack(item, 1))
                .orElse(ItemStack.EMPTY);
        if (target.isEmpty()) return false;
        anchorOverlayWidget = null;
        anchorBookButton = null;
        resetBrowseAllState();
        queryTarget = target;
        queryUsage = spec.usage;
        // Saved category first; fall back to the smart default when the item
        // no longer has content in it.
        currentCategory = categoryForId(spec.category);
        if (currentCategory == null || !categoryHasQueryContent(currentCategory)) {
            currentCategory = RecipeViewerCategories.defaultFor(target, spec.usage,
                    screen.getMenu());
        }
        if (currentCategory == null) return false;
        List<RecipeDisplayEntry> hits = null;
        if (currentCategory.isGridCategory()) {
            computeGridBoxSize();
        } else {
            hits = filterByRecipeBookStations(
                    currentCategory.query(target, spec.usage));
            if (hits.isEmpty()) return false;
            computeBoxSize(hits);
        }
        ownerScreen = screen;
        viewerPage = 0;
        // Pin the window at the saved box position (the anchor is DERIVED from
        // it, so fitBoxToPage's anchor refresh keeps the saved spot).
        boxX = spec.x;
        boxY = spec.y;
        anchorScreenX = boxX + 16;
        anchorScreenY = boxY + boxH - 16;
        bottomAnchor = anchorScreenY + 16;
        if (currentCategory.isGridCategory()) {
            rebuildGrid(currentCategory.gridItems(target, spec.usage));
        } else {
            rebuildWithHits(hits);
        }
        // After the fit re-anchor the box to the saved position (the band
        // clamp may have shifted it vertically) and restore the saved page.
        boxX = spec.x;
        boxY = clampBandTop(spec.y);
        anchorScreenX = boxX + 16;
        anchorScreenY = boxY + boxH - 16;
        bottomAnchor = anchorScreenY + 16;
        viewerPage = Math.min(Math.max(0, spec.page),
                Math.max(0, viewerPageCount - 1));
        if (!currentCategory.isGridCategory()) {
            showPage(ownerScreen, boxX, boxY, boxW, boxH);
        }
        repaginateToSelected();
        rebuildStationColumn();
        viewerZ = PinOverlayManager.nextZ();
        RecipeViewerIndex.setViewerActive(true);
        RecipeViewerIndex.setViewerOpenedFromBook(false);
        // Bind the window to the spec and write the (possibly corrected) page.
        this.spec = spec;
        spec.materialized = true;
        syncSpec();
        return true;
    }

    /** The category with {@code id}, or null. */
    private RecipeViewerCategory categoryForId(String id) {
        if (id == null) return null;
        for (RecipeViewerCategory c : RecipeViewerCategories.all()) {
            if (id.equals(c.id())) return c;
        }
        return null;
    }

    /** BRBE's engine found nothing for this item (e.g. a mod item while JEI is
     *  installed and jei-plugins is not): route the query to the active recipe
     *  viewer (JEI/REI) so mod recipes still open.  Returns whether the event
     *  was consumed. */
    private boolean fallbackToViewer(ItemStack target, boolean viewUsage) {
        if (!ItemViewCompat.isLoaded()) return false;
        return viewUsage ? ItemViewCompat.openUsageView(target)
                         : ItemViewCompat.openRecipeView(target);
    }

    /** Rebuild the overlay contents from a (possibly new category's) query hits,
     *  reusing the fixed box layout. */
    private void rebuildWithHits(List<RecipeDisplayEntry> hits) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || hits.isEmpty()) return;

        StackedItemContents stacked = new StackedItemContents();
        PartialCraftingUtil.fillSearchSpaceStackedContents(stacked);
        RecipeCollection collection = RecipeViewerIndex.toCollection(hits, stacked);

        // Mark partially-craftable recipes (always against the inventory, so
        // the viewer is unaffected by the "only when carrying" toggle) and
        // snapshot the marks against the tagger's generation advances.  The
        // marks use the regular search space (real inventory + craft grid,
        // result slot excluded) plus the carried stack.
        if (ownerScreen != null) {
            PartialCraftingUtil.prepareForViewer(collection,
                    PartialCraftingUtil.searchSpaceSlots(),
                    ownerScreen.getMenu().getCarried());
        }
        RecipeViewerIndex.snapshotPartials(collection);

        // 统一排序（ViewerObjectOrder）：pin 组在前（组内也按种类）→ 普通组
        // 按种类（可合成 → 残缺 → 不可合成），同组同种类稳定。
        List<RecipeDisplayEntry> entries = ViewerObjectOrder.reorder(collection.getRecipes(),
                BetterRecipeBook.pinnedRecipeManager::isPinnedEntry,
                e -> viewerKindRank(collection, e));

        viewerRecipes = new ArrayList<>(entries);
        computeBoxSize(hits);
        // The final box position is clamped inside showPage -> fitBoxToPage
        // with the ACTUAL (post-shrink) box size.  Clamping here with the
        // full-page size would misjudge the band limit AND rewrite the
        // pointer-derived bottomAnchor (a phantom fifth row as a boundary),
        // which is exactly what the caller just anchored to the mouse.
        viewerPage = 0;
        showPage(ownerScreen, boxX, boxY, boxW, boxH);
    }

    /** Compute boxW/boxH and viewerPageCount from the hit count. */
    private void computeBoxSize(List<RecipeDisplayEntry> hits) {
        computeBoxSize(hits.size());
    }

    /** Shared box sizing for the recipe and fuel grids: page count from the
     *  total, box at the FULL page size (行上限 x 列上限 = {@link #pageRows()} x
     *  {@link #pageCols()}).  The box is then shrunk to the current page's actual
     *  rows/columns by {@link #fitBoxToPage} (called from showPage and the grid
     *  paths), which also re-clamps the position; {@link #ensureTabWidth} and
     *  {@link #ensureTitleWidth} may then widen it again with empty columns. */
    private void computeBoxSize(int total) {
        int perPage = pageSize();
        boolean paged = total > perPage;
        viewerPageCount = paged ? (total + perPage - 1) / perPage : 1;
        boxW = pageCols() * 25 + 8;
        boxH = pageRows() * 25 + 8;
        ensureTabWidth();
        // MUST follow viewerPageCount (set just above): the title reserves room
        // for the turn-page buttons whenever this window will be paged.
        ensureTitleWidth();
    }

    /** Shrink the box to {@code pageCount} objects and re-clamp it: columns cap
     *  at the configured 列上限 ({@link #pageCols()} — which is also the tab-count
     *  cap) and empty rows/columns are dropped.  The resulting row count is
     *  always {@code <=} the 行上限 ({@link #pageRows()}) and IS the workstation
     *  column's row count ({@link #stationViewRows()}); the tab strip may then
     *  widen the box via {@link #ensureTabWidth} and — beyond the column cap —
     *  the title bar via {@link #ensureTitleWidth}.  The box re-anchors to the
     *  first-object centre ({@link #anchorScreenX} / {@link #anchorScreenY}).
     *
     *  <p>ESTABLISHED RULE: after the single limit adjustment (band kept on
     *  screen — {@link #clampBandTop}) the anchor is refreshed to the ACTUAL
     *  centre of the first row's first object — the settled position is where
     *  the next rebuild starts from, so the interface never snaps back to a
     *  pre-adjustment spot.  Returns the column count, which the caller uses
     *  to place its objects. */
    private int fitBoxToPage(int pageCount) {
        int columns = Math.max(1, Math.min(pageCols(), pageCount));
        int rows = (pageCount + columns - 1) / columns;
        boxW = columns * 25 + 8;
        boxH = rows * 25 + 8;
        ensureTabWidth();
        ensureTitleWidth();
        boxX = anchorScreenX - 16;
        boxY = anchorScreenY - boxH + 16;
        boxY = clampBandTop(boxY);
        // RULE: refresh the anchor after the limit adjustment.
        anchorScreenX = boxX + 16;
        anchorScreenY = boxY + boxH - 16;
        bottomAnchor = anchorScreenY + 16;
        return columns;
    }

    /** {@link #fitBoxToPage} for a grid category, sized to the current page's
     *  slice of {@link #gridItems}. */
    private void fitGridBoxToPage() {
        int start = viewerPage * pageSize();
        int count = Math.min(start + pageSize(), gridItems.size()) - start;
        fitBoxToPage(count);
    }

    /** Compute boxW/boxH and viewerPageCount for a grid category's item grid —
     *  same paging rule as the recipe grid, so a long list pages too. */
    private void computeGridBoxSize() {
        computeBoxSize(gridItems.size());
    }

    /** Widen the box (with empty columns) so the tab strip can show up to
     *  {@link #maxTabs()} tabs on a page without folding when there are more tabs
     *  than object columns.  Above {@link #maxTabs()} the strip slides (REI-style
     *  window) instead of folding.
     *
     *  <p>{@code maxTabs() = 列上限} — one tab per column — so this can never widen
     *  the box past the column cap's full width ({@code pageCols() * 25 + 8}):
     *  the TAB STRIP is subject to the column cap (the old hard {@code MAX_TABS = 10}
     *  was removed, user 2026-09-13).  The only element allowed to create columns
     *  beyond the cap is the title bar, see {@link #ensureTitleWidth()} — and the
     *  strip then grows into those extra columns too ({@link #visibleTabCount()}). */
    private void ensureTabWidth() {
        int tabCount = Math.min(visibleCategories().size(), maxTabs());
        int tabW = tabCount * TAB_WIDTH + 8;
        if (tabW > boxW) {
            boxW = tabW;
        }
    }

    /** Widen the box with EMPTY object columns so the title bar can draw the
     *  full tab title instead of degrading to "…" — the same mechanism as
     *  {@link #ensureTabWidth}: every extra column is one whole 25px object
     *  pitch (the new cells are filled by {@link #drawEmptyRowFillers} like any
     *  other empty box cell, and the tab strip simply ends before them).
     *
     *  <p>Called right after {@link #ensureTabWidth} in BOTH boxW assignment
     *  sites ({@link #computeBoxSize} / {@link #fitBoxToPage}), so the
     *  truncation branch of {@link #titleText} no longer triggers for any
     *  category name.  The width required is the title's text plus the two pads
     *  {@link #titleTextX} / {@link #titleTextRightBound} impose: 6px at the
     *  band's left edge; on the right either the same 6px (no turn-page buttons)
     *  or the right-aligned turn-page buttons' footprint.  Both pads are
     *  boxW-independent, so this needs no knowledge of the window's position.
     *
     *  <p><b>标题与翻页键绑在一起</b>（2026-09-12）：标题栏整行是"顶部元素"，
     *  其加列优先级高于列上限（{@link #pageCols()}）；而对象放不下（
     *  {@link #pageButtonsInBand()}：窗口不止一页）时两个翻页键就右对齐地占着
     *  同一条标题栏，标题必须给它们让位。于是右内边距在分页时 = 翻页键的占位宽度，
     *  标题连同伴随的翻页键一起放不下时**继续创建列**，而不是把标题截成"…"。
     *
     *  <p>判据用 {@link #pageButtonsInBand()}（只看页数）而**不是**
     *  {@link #isPaged()}（还要求窗口已激活）：布局发生在窗口被标记为激活之前
     *  ——{@code openFor} 先 computeBoxSize/fitBoxToPage、后
     *  {@code setViewerActive(true)}——用 isPaged() 会在**首次打开**时误判"没有
     *  翻页键"，少留一个按钮位的宽度，而绘制时 {@link #titleTextRightBound} 又按
     *  "有翻页键"裁切，标题因此退化成"…"。
     *
     *  <p>Horizontal placement stays free (the window may hang off a screen
     *  edge — {@link #clampBandTop} is the only limit), so a long category
     *  title simply makes the window wider by whole columns. */
    private void ensureTitleWidth() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.font == null) return;
        String title = titleBarTitle();
        if (title.isEmpty()) return;
        int needed = mc.font.width(title)
                + (titleTextX() - boxLeft())                     // 左内边距（6px）
                + (boxLeft() + boxW - titleTextRightBound());    // 右内边距（分页时含翻页按钮占位）
        if (needed <= boxW) return;
        boxW += ((needed - boxW) + 24) / 25 * 25;
    }

    /** Lay out a grid category's item grid from {@code items}: a usage query of
     *  a specific item passes that item alone (JEI per-item semantics), a
     *  usage query of its station passes the whole list.  Keeps the tab strip
     *  anchored. */
    private void rebuildGrid(List<ItemStack> items) {
        // 统一排序（ViewerObjectOrder）：燃料类别"拥有 → 缺失"作为组内种类
        //（无 pin 组），组内保持原排序（燃烧时长升序），稳定。
        if (currentCategory != null && currentCategory.isFuelCategory()) {
            var counts = PartialCraftingUtil.searchSpaceItemCounts();
            items = ViewerObjectOrder.reorder(items,
                    s -> false,
                    s -> counts.containsKey(s.getItem()) ? 0 : 1);
        }
        gridItems = items;
        gridHoverStack = null;
        viewerRecipes = List.of();
        computeGridBoxSize();
        // Shrink the box to the first page (rows/columns without an object are
        // dropped) and re-apply the single band limit; the grid cells are
        // positioned per-frame from boxX/boxY, so they follow automatically.
        viewerPage = 0;
        fitGridBoxToPage();
    }

    /** Switch the viewer to {@code category}, re-querying the stored target.
     *  Repagination happens here, before the next render, so the newly selected
     *  tab always lands on the first visible row of the folded tab strip. */
    private void switchCategory(RecipeViewerCategory category) {
        if (category == null || category == currentCategory) return;
        if (category.isGridCategory()) {
            // Grid tabs (fuel / compost / info): a specific item target shows
            // that item alone (JEI per-item semantics), a station target shows
            // the whole list.  Anything else keeps the current category instead
            // of flooding the overlay.  Browse-all ignores the query entirely
            // and shows the category's complete grid.
            if (!browseAllMode && (queryTarget == null || queryTarget.isEmpty())) {
                return;
            }
            List<ItemStack> items = gridSource(category);
            if (items.isEmpty()) {
                return;
            }
            currentCategory = category;
            rebuildGrid(items);
        } else {
            // The "hide objects of workstations without a recipe book" toggle
            // cuts the station-category connection for an illegal station: a
            // usage query opened from such a station (e.g. BetterEnd's end
            // stone smelter) must not surface its recipes through this tab.
            // Grid categories are exempt and never reach this branch.
            // Browse-all skips the cut (it distributes everything queryable).
            if (!browseAllMode
                    && BetterRecipeBook.config.hideNoRecipeBookStationObjects
                    && queryUsage
                    && queryTarget != null && !queryTarget.isEmpty()
                    && category.appliesToStation(queryTarget)
                    && !RecipeViewerEngine.isRecipeBookStation(queryTarget)) {
                return;
            }
            List<RecipeDisplayEntry> hits = categoryHits(category);
            if (hits.isEmpty()) return;
            currentCategory = category;
            rebuildWithHits(hits);
        }
        boxY = clampBandTop(boxY);
        repaginateToSelected();
        rebuildStationColumn();
        syncSpec();
    }

    /** Filter a query's hits by the "hide objects of workstations without a
     *  recipe book" toggle: objects whose <b>every</b> workstation lacks a
     *  recipe-book system are dropped.  Objects that also have a legitimate
     *  (recipe-book-backed) workstation survive — their tooltip icons are
     *  filtered separately.  No-op when the toggle is off. */
    private List<RecipeDisplayEntry> filterByRecipeBookStations(List<RecipeDisplayEntry> hits) {
        return filterByRecipeBookStations(hits, currentCategory);
    }

    /** Category-aware variant (browse-all): each category's objects are
     *  judged against the category they CAME FROM, not the pre-toggle
     *  currentCategory — the old way would mis-judge every other category's
     *  entries (dropping legitimate ones or leaking illegal ones). */
    private List<RecipeDisplayEntry> filterByRecipeBookStations(
            List<RecipeDisplayEntry> hits, RecipeViewerCategory category) {
        if (!BetterRecipeBook.config.hideNoRecipeBookStationObjects) return hits;
        if (hits == null || hits.isEmpty()) return hits;
        List<RecipeDisplayEntry> out = new ArrayList<>();
        for (RecipeDisplayEntry entry : hits) {
            if (hasRecipeBookStation(entry, category)) out.add(entry);
        }
        return out;
    }

    /** Whether {@code entry} has at least one recipe-book-backed workstation:
     *  its display's declared crafting station, or any station its category
     *  registered.  Built-in categories (furnace / crafting / stonecutting /
     *  smithing / fuel) are themselves recipe-book systems, so their objects
     *  always qualify. */
    private boolean hasRecipeBookStation(RecipeDisplayEntry entry) {
        return hasRecipeBookStation(entry, currentCategory);
    }

    /** Category-aware variant (browse-all) of {@link #hasRecipeBookStation}. */
    private boolean hasRecipeBookStation(RecipeDisplayEntry entry,
                                                RecipeViewerCategory category) {
        if (entry == null) return false;
        if (!browseAllMode
                && queryUsage
                && queryTarget != null && !queryTarget.isEmpty()
                && !RecipeViewerEngine.isRecipeBookStation(queryTarget)
                && category != null && category.appliesToStation(queryTarget)) {
            return false;
        }
        if (category != null && isBuiltinCategory(category)) return true;
        RecipeViewerCategory resolved = category != null ? category : categoryFor(entry);
        return resolved != null && entryHasRecipeBookStation(entry, resolved.stationIconsFor(entry));
    }

    /** Whether {@code entry} has at least one recipe-book-backed workstation
     *  among {@code icons} or its display's declared crafting station. */
    private boolean entryHasRecipeBookStation(RecipeDisplayEntry entry, List<ItemStack> icons) {
        if (icons != null) {
            for (ItemStack station : icons) {
                if (RecipeViewerEngine.isRecipeBookStation(station)) return true;
            }
        }
        ItemStack declared = RecipeViewerIndex.resolveCraftingStation(entry);
        return !declared.isEmpty() && RecipeViewerEngine.isRecipeBookStation(declared);
    }
    /** Keep only recipe-book-backed workstation icons (object tooltips when
     *  the hide toggle is on; the fuel category is exempt and never filtered). */
    private static List<ItemStack> filterRecipeBookStations(List<ItemStack> icons) {
        if (icons == null || icons.isEmpty()) return icons;
        List<ItemStack> out = new ArrayList<>();
        for (ItemStack icon : icons) {
            if (RecipeViewerEngine.isRecipeBookStation(icon)) out.add(icon);
        }
        return out;
    }

    /** Whether {@code category} is one of the built-in vanilla categories,
     *  which map to vanilla recipe-book types and therefore count as
     *  recipe-book systems themselves.  The stonecutting category is NOT
     *  exempt: the stonecutter has no recipe-book UI (vanilla provides none
     *  and BRBE adds none), so it is a no-recipe-book workstation whose
     *  objects the hide toggle filters like any mod station's.  Brewing IS
     *  exempt — BRBE ships a brewing recipe book. */
    private boolean isBuiltinCategory(RecipeViewerCategory category) {
        if (category == null) return false;
        return switch (category.id()) {
            case "furnace", "crafting", "smithing", "brewing", "fuel" -> true;
            default -> false;
        };
    }

    /** THE ONLY position adjustment (2026-09-02 redesign): clamp the box's
     *  top so the extension band (title bar) stays inside the screen
     *  VERTICALLY — the band's top line never leaves the screen top and its
     *  bottom line never leaves the screen bottom.  Horizontal is completely
     *  free (the window may hang off the screen's left/right edges) and the
     *  box below the band may protrude past the screen bottom.  All previous
     *  limits (25px edge margins, crafting-grid avoidance, x clamps) are
     *  scrapped. */
    private int clampBandTop(int boxTop) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getWindow() == null) return boxTop;
        int guiH = mc.getWindow().getGuiScaledHeight();
        // Band top = boxTop - TITLE_BAR_H (≥ 0)  →  boxTop ≥ TITLE_BAR_H.
        // Band bottom = boxTop + PAGE_BTN_SHIFT_Y - 2 (≤ guiH)
        //   →  boxTop ≤ guiH - (PAGE_BTN_SHIFT_Y - 2).
        return Mth.clamp(boxTop, TITLE_BAR_H,
                Math.max(TITLE_BAR_H, guiH - (PAGE_BTN_SHIFT_Y - 2)));
    }

    /** Keep the REI-style tab window valid and the selected category visible
     *  inside it — the window slides instead of paging, and folding never hides
     *  the selected tab.  Call after the box layout has been rebuilt (open /
     *  category switch). */
    private void repaginateToSelected() {
        List<RecipeViewerCategory> cats = visibleCategories();
        if (currentCategory == null || cats.isEmpty()) {
            tabWindowStart = 0;
            return;
        }
        int perPage = visibleTabCount();
        int maxStart = Math.max(0, cats.size() - perPage);
        tabWindowStart = Math.max(0, Math.min(tabWindowStart, maxStart));
        int idx = cats.indexOf(currentCategory);
        if (idx < 0) return;
        if (idx < tabWindowStart) {
            tabWindowStart = idx;
        } else if (idx >= tabWindowStart + perPage) {
            tabWindowStart = Math.min(maxStart, idx - (perPage - 1));
        }
    }

    /** Ctrl+O toggle (monitored only while the cursor is inside the query
     *  interface): gathers ALL queryable objects into the viewer and
     *  distributes them into their correct categories — the "house"
     *  metaphor: querying an item herds its related objects in, Ctrl+O
     *  imports every queryable object into its own category (tab), a second
     *  Ctrl+O drives the newly added ones back out. */
    private void toggleBrowseAll() {
        if (!isActive() || ownerScreen == null) return;
        if (browseAllMode) {
            leaveBrowseAll();
        } else {
            enterBrowseAll();
        }
    }

    /** Enter browse-all: keep the current category, but rebuild it with its
     *  COMPLETE object pool (every other tab does the same once switched
     *  to). */
    private void enterBrowseAll() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || ownerScreen == null) return;
        browseAllReturnPage = viewerPage;
        browseAllReturnCategory = currentCategory;
        browseAllMode = true;
        viewerPage = 0;
        refreshCurrentCategory(false);
    }

    /** Leave browse-all: rebuild the current category from the query again
     *  (driving the imported objects out) and restore the saved page.  A tab
     *  that only exists in browse-all (a category the query never surfaced)
     *  cannot survive the restore — the normal selection flow picks an
     *  existing tab instead (the pre-browse-all tab first, then the
     *  best-content category). */
    private void leaveBrowseAll() {
        resetBrowseAllState();
        RecipeViewerCategory saved = browseAllReturnCategory;
        if (!categoryHasQueryContent(currentCategory)) {
            if (saved != null && saved != currentCategory
                    && categoryHasQueryContent(saved)) {
                switchCategory(saved);
            } else {
                RecipeViewerCategory alt =
                        bestContentCategory(queryTarget, queryUsage, currentCategory);
                if (alt != null && alt != currentCategory) {
                    switchCategory(alt);
                }
            }
        }
        refreshCurrentCategory(true);
        browseAllReturnPage = 0;
        browseAllReturnCategory = null;
    }

    /** Whether {@code category} has ANY content in the non-browse (query)
     *  view — the "existed before browse-all" test: a browse-all-only tab
     *  has none and must not survive a restore. */
    private boolean categoryHasQueryContent(RecipeViewerCategory category) {
        if (category == null) return false;
        return category.isGridCategory()
                ? !gridSource(category).isEmpty()
                : !categoryHits(category).isEmpty();
    }

    /** The category's objects for the current mode: its complete pool while
     *  browsing (Ctrl+O), its query-related subset otherwise. */
    private List<RecipeDisplayEntry> categoryHits(RecipeViewerCategory category) {
        return new ArrayList<>(filterByRecipeBookStations(
                browseAllMode ? category.allEntries()
                        : category.query(queryTarget, queryUsage),
                category));
        // 排序统一交给 ViewerObjectOrder（rebuildWithHits/rebuildGrid 调用）：
        // pin 组在前（组内也按种类）→ 普通组按种类，稳定。
    }

    /** The grid category's item grid for the current mode (same semantics as
     *  {@link #categoryHits}). */
    private List<ItemStack> gridSource(RecipeViewerCategory category) {
        return browseAllMode ? category.allGridItems()
                : category.gridItems(queryTarget, queryUsage);
    }

    /** Rebuild the selected category's own view — the shared body of
     *  {@link #switchCategory}, minus its early return (used when entering /
     *  leaving browse-all). */
    private void refreshCurrentCategory(boolean restorePage) {
        if (currentCategory == null || ownerScreen == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (currentCategory.isGridCategory()) {
            List<ItemStack> items = gridSource(currentCategory);
            if (items.isEmpty()) return;
            rebuildGrid(items);
            boxY = clampBandTop(boxY);
            repaginateToSelected();
            rebuildStationColumn();
        } else {
            List<RecipeDisplayEntry> hits = categoryHits(currentCategory);
            if (hits.isEmpty()) return;
            rebuildWithHits(hits);
            if (restorePage) {
                int maxPage = Math.max(0, viewerPageCount - 1);
                viewerPage = Math.min(browseAllReturnPage, maxPage);
                showPage(ownerScreen, boxX, boxY, boxW, boxH);
            }
            boxY = clampBandTop(boxY);
            // The browse-all tab list inserts categories before the selected
            // one; keep its tab inside the sliding window so the selection
            // stays visible after the mode flip.
            repaginateToSelected();
            rebuildStationColumn();
        }
        syncSpec();
    }

    /** Re-evaluate this window's ordering and states when its inputs changed
     *  while it was open: the pin set (A-pinning in the recipe book behind it)
     *  or the search space (inventory pickup/drop — the fuel grid's owned→
     *  missing order and every recipe's craftable/partial rank and face follow
     *  in real time instead of on the next reopen).  Both are one cheap
     *  comparison when nothing changed (int + long).  Rebuild keeps the
     *  current page (clamped to the new page count); grids keep the fuel
     *  page and get their re-sorted list the same way. */
    private void refreshIfDirty() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        ItemStack carried = mc.player.containerMenu != null
                ? mc.player.containerMenu.getCarried() : ItemStack.EMPTY;
        long spaceHash = PartialCraftingUtil.slotHash(
                PartialCraftingUtil.searchSpaceSlots(), carried);
        int pins = BetterRecipeBook.pinnedRecipeManager.version();
        if (spaceHash == lastSpaceHash && pins == lastPinVersion) return;
        lastSpaceHash = spaceHash;
        lastPinVersion = pins;
        if (currentCategory == null || ownerScreen == null) return;
        if (currentCategory.isGridCategory()) {
            List<ItemStack> items = gridSource(currentCategory);
            if (items.isEmpty()) return;
            int page = viewerPage;
            rebuildGrid(items);
            int maxPage = Math.max(0, viewerPageCount - 1);
            viewerPage = Math.min(page, maxPage);
            fitGridBoxToPage();
            boxY = clampBandTop(boxY);
            repaginateToSelected();
            rebuildStationColumn();
            syncSpec();
            return;
        }
        List<RecipeDisplayEntry> hits = categoryHits(currentCategory);
        if (hits.isEmpty()) return;
        int page = viewerPage;
        rebuildWithHits(hits);
        int maxPage = Math.max(0, viewerPageCount - 1);
        viewerPage = Math.min(page, maxPage);
        showPage(ownerScreen, boxX, boxY, boxW, boxH);
        boxY = clampBandTop(boxY);
        repaginateToSelected();
        rebuildStationColumn();
        syncSpec();
    }

    /** Clear browse-all state (mode; the return page is cleared when the
     *  toggle completes). */
    private void resetBrowseAllState() {
        browseAllMode = false;
    }

    /**
     * Lay out the current page: build a sub-collection of this page's recipes,
     * fit the box to the page (empty rows/columns dropped, first row at the
     * bottom) and re-flow the buttons onto the page's column pitch.
     */
    private void showPage(AbstractContainerScreen<?> screen, int boxX, int boxY,
                                 int boxW, int boxH) {
        if (viewerRecipes.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        int start = viewerPage * pageSize();
        int end = Math.min(start + pageSize(), viewerRecipes.size());
        List<RecipeDisplayEntry> pageEntries = new ArrayList<>(viewerRecipes.subList(start, end));

        StackedItemContents stacked = new StackedItemContents();
        PartialCraftingUtil.fillSearchSpaceStackedContents(stacked);
        RecipeCollection subset = RecipeViewerIndex.toCollection(pageEntries, stacked);
        PartialCraftingUtil.prepareForViewer(subset,
                PartialCraftingUtil.searchSpaceSlots(),
                screen.getMenu().getCarried());
        RecipeViewerIndex.snapshotPartials(subset);

        boolean paged = viewerPageCount > 1;
        // Shrink the box to this page's actual rows/columns (empty rows and
        // columns are dropped; the tab strip may still widen it via
        // ensureTabWidth), then re-clamp: the box bottom stays anchored, so a
        // short last page just makes the box shorter.  The layout below uses
        // the clamped (static) position, not the caller's stale boxX/boxY.
        int columns = fitBoxToPage(pageEntries.size());
        int bx = boxX;
        int by = boxY;
        int bw = boxW;
        int bh = boxH;
        var ctx = SlotDisplayContext.fromLevel(mc.level);
        overlay.init(subset, ctx, false, bx, by, bw, bh, paged ? 1.0f : 0f);
        currentCollection = subset;

        // init lays the buttons out at vanilla 4/5 columns (and, in paged mode,
        // shrink-wraps the box position); pin the box position and re-flow the
        // buttons onto the page's column pitch.  Rows grow upward: row 0 sits
        // at the box bottom (against the tab strip), later rows above it.
        OverlayRecipeComponentAccessor acc = (OverlayRecipeComponentAccessor) overlay;
        acc.setX(bx);
        acc.setY(by);
        List<AbstractWidget> buttons = acc.getRecipeButtons();
        // 原版 init 把按钮按「可合成优先」排序（getSelectedRecipes(CRAFTABLE)
        // 在前、NOT_CRAFTABLE 在后），而 viewerRecipes 已按 pin → 可合成 →
        // 残缺 → 不可合成重排。不重排按钮列表的话：① 不可合成的 pin 对象
        // 视觉上被可合成/残缺对象「挡住」（位置按按钮索引排布）；②
        // drawViewerPinMarkers 的「按钮 i ↔ 第 i 条条目」索引映射错位，pin
        // 贴图会挂到别的按钮上。按 pageEntries 顺序重排后两者归位。
        java.util.Map<RecipeDisplayId, Integer> pageOrder = new java.util.HashMap<>();
        for (int i = 0; i < pageEntries.size(); i++) {
            pageOrder.put(pageEntries.get(i).id(), i);
        }
        buttons.sort(java.util.Comparator.comparingInt(b -> {
            RecipeDisplayId id = ((OverlayRecipeButtonAccessor) b).brbe$getRecipe();
            Integer idx = pageOrder.get(id);
            return idx != null ? idx : Integer.MAX_VALUE;
        }));
        for (int i = 0; i < buttons.size(); i++) {
            int row = i / columns;
            buttons.get(i).setX(bx + 4 + (i % columns) * 25);
            buttons.get(i).setY(by + bh - 28 - row * 25);
        }
    }

    /** Whether the open viewer spans multiple pages. */
    public boolean isPaged() {
        return isActive() && viewerPageCount > 1;
    }

    /** Whether {@code o} is the standalone viewer overlay instance. */
    public boolean isOwnOverlay(OverlayRecipeComponent o) {
        return o == overlay;
    }

    /**
     * Query target: viewer-overlay button first, then hovered container slot,
     * then ghost-preview slot, then a hovered recipe-book button.
     */
    /** Window-only capture: hovered viewer button / popup / grid cell /
     *  workstation column (the screen part is static — see
     *  {@link RecipeViewerOverlay#captureScreenTarget}). */
    public ItemStack captureViewerAnchors(AbstractContainerScreen<?> screen) {
        // Reset the anchor each capture: only a hovered viewer-overlay recipe
        // button re-sets it, so a plain slot / book button / fuel cell capture
        // never leaves a stale recipe behind (pinning keys off it).
        anchorOverlayWidget = null;
        anchorBookButton = null;
        // Hovering a BRBE viewer overlay recipe button: a slot item inside the
        // open popup (a real item object) queries first — JEI-style per-item
        // R/U — then the popup's recipe result.  The popup under the cursor
        // (which extends past the button) anchors first, so R/U over the
        // popup's edge queries the popup recipe, not a container slot behind it.
        if (overlay.isVisible()) {
            AbstractWidget popup = hoverPopupField;
            if (popup != null) {
                Minecraft mc = Minecraft.getInstance();
                int mx = Mth.floor(mc.mouseHandler.getScaledXPos(mc.getWindow()));
                int my = Mth.floor(mc.mouseHandler.getScaledYPos(mc.getWindow()));
                ItemStack slotStack = slotStackInPopup(popup, mx, my);
                if (!slotStack.isEmpty()) {
                    anchorOverlayWidget = popup;
                    return slotStack;
                }
                ItemStack result = overlayButtonResult((OverlayRecipeButtonAccessor) popup);
                if (!result.isEmpty()) {
                    anchorOverlayWidget = popup;
                    return result;
                }
            }
            for (AbstractWidget ow : ((OverlayRecipeComponentAccessor) overlay).getRecipeButtons()) {
                if (ow instanceof OverlayRecipeButtonAccessor oba && ow.isHoveredOrFocused()) {
                    ItemStack result = overlayButtonResult(oba);
                    if (!result.isEmpty()) {
                        anchorOverlayWidget = ow;
                        return result;
                    }
                }
            }
        }



        // Hovering a fuel cell in the query viewer's fuel category.
        // Hovering a grid category's cell (fuel / compost / info) in the query
        // viewer.
        if (isGridMode() && gridHoverStack != null && !gridHoverStack.isEmpty()) {
            return gridHoverStack;
        }
        // Hovering a left workstation column object: part of the viewer, so
        // R/U over it queries that object (R = recipes, U = uses) like a
        // normal item.
        if (isActive()) {
            Minecraft mc = Minecraft.getInstance();
            int mx = Mth.floor(mc.mouseHandler.getScaledXPos(mc.getWindow()));
            int my = Mth.floor(mc.mouseHandler.getScaledYPos(mc.getWindow()));
            ItemStack station = stationCellAt(mx, my);
            if (!station.isEmpty()) return station;
        }
        return ItemStack.EMPTY;
    }

    /** Recipe id of the query-viewer recipe button under the cursor when the
     *  last capture happened, or null.  Lets a pin clone the full recipe button. */
    public RecipeDisplayId capturedOverlayRecipe() {
        if (anchorOverlayWidget instanceof OverlayRecipeButtonAccessor oba) {
            return oba.brbe$getRecipe();
        }
        return null;
    }

    /** The query-viewer overlay's recipe collection (the one holding the
     *  captured recipe button), or null.  Lets a pin clone render with the
     *  source recipe's craftable / partial state instead of recomputing it
     *  against a fresh collection. */
    public RecipeCollection capturedOverlayCollection() {
        if (anchorOverlayWidget == null) return null;
        return overlay.getRecipeCollection();
    }

    /** Centre of the hovered query-viewer recipe button, or null when the last
     *  capture was not an overlay button.  Pins open centred on their source. */
    public int[] capturedOverlayButtonCentre() {
        if (anchorOverlayWidget == null) return null;
        return new int[] { anchorOverlayWidget.getX() + anchorOverlayWidget.getWidth() / 2,
                           anchorOverlayWidget.getY() + anchorOverlayWidget.getHeight() / 2 };
    }

    /**
     * Whether the clicked recipe may be placed into the current station.
     * Crafting recipes go into crafting-table menus; smelting recipes go into
     * furnace-type menus (furnace / blast furnace / smoker).  A recipe must not
     * fill items or ghost previews into a wrong-station menu.
     */
    /** The entry for {@code id}: the engine's registry first (covers synthetic
     *  entries from the companion mod), then the recipe book's known set. */
    public RecipeDisplayEntry entryFor(RecipeDisplayId id) {
        if (id == null) return null;
        RecipeDisplayEntry entry = RecipeViewerEngine.entryFor(id);
        if (entry != null) return entry;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return null;
        return ((ClientRecipeBookAccessor) mc.player.getRecipeBook()).brbe$getKnown().get(id);
    }

    private boolean recipeFitsScreen(RecipeDisplayId id, AbstractContainerScreen<?> screen) {
        if (RecipeViewerEngine.isSynthetic(id)) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        RecipeDisplayEntry entry = entryFor(id);
        if (entry == null) return false;
        Identifier cat = BuiltInRegistries.RECIPE_BOOK_CATEGORY.getKey(entry.category());
        if (cat == null) return false;
        String path = cat.getPath();
        if (path.startsWith("crafting_")) {
            return screen.getMenu() instanceof AbstractCraftingMenu;
        }
        // Smelting recipes place into a furnace-type menu (furnace / blast
        // furnace / smoker); campfire has no menu and is excluded.
        if (path.startsWith("furnace_") || path.startsWith("blast_furnace_")
                || path.startsWith("smoker_")) {
            return screen.getMenu() instanceof AbstractFurnaceMenu;
        }
        if (path.equals("stonecutter")) {
            return screen.getMenu() instanceof net.minecraft.world.inventory.StonecutterMenu;
        }
        if (path.equals("smithing")) {
            return screen.getMenu() instanceof net.minecraft.world.inventory.SmithingMenu;
        }
        return false;
    }

    /** Result item of an overlay recipe button (its recipe's primary output). */
    private ItemStack overlayButtonResult(OverlayRecipeButtonAccessor button) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return ItemStack.EMPTY;
        try {
            RecipeDisplayId id = button.brbe$getRecipe();
            RecipeDisplayEntry entry = entryFor(id);
            if (entry == null) return ItemStack.EMPTY;
            List<ItemStack> results = resultItemsOf(entry);
            if (!results.isEmpty()) return results.get(0);
        } catch (Exception e) {
            // fall through
        }
        return ItemStack.EMPTY;
    }

    /** If {@code slot} currently holds a ghost-preview ingredient, return its item. */


    /** Within-group kind rank for {@link ViewerObjectOrder}: 0 = fully
     *  craftable, 1 = partial (missing materials), 2 = uncraftable.  Pin state
     *  is the module's group predicate, not a kind. */
    private int viewerKindRank(RecipeCollection collection, RecipeDisplayEntry entry) {
        boolean craftable = collection.isCraftable(entry.id());
        boolean partial = PartialCraftingUtil.isPartiallyCraftable(collection, entry.id());
        if (craftable && !partial) return 0;
        if (partial) return 1;
        return 2;
    }

    /** Whether the click lands on the overlay box (buttons + padding) or the
     *  workstation panel. */
    private boolean inBox(MouseButtonEvent event) {
        int mx = Mth.floor(event.x());
        int my = Mth.floor(event.y());
        // Use the current box layout fields (not the overlay's buttons, which
        // may still hold the previous category's after a tab switch) so the
        // click region always matches what is actually drawn.
        if (mx >= boxX && mx < boxX + boxW && my >= boxY && my < boxY + boxH) {
            return true;
        }
        // The left workstation panel is attached outside the box's left edge
        // and is TRIMMED to its actual content: with fewer stations than the
        // object area's rows the panel top sits below the box top, and the
        // empty strip above it is background — clicking there must close the
        // viewer (a click anywhere outside the box / drawn panel does).
        if (!stationColumnItems.isEmpty()
                && mx >= panelLeft() && mx < panelLeft() + STATION_COL_WIDTH) {
            int shown = Math.min(stationColumnItems.size(), stationViewRows());
            int[] rect = stationColumnPanelRect(shown);
            return my >= rect[0] && my < rect[0] + rect[1];
        }
        return false;
    }
    }
}
