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
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.inventory.AbstractCraftingMenu;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.BlastFurnaceMenu;
import net.minecraft.world.inventory.FurnaceMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.SmokerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.display.FurnaceRecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.minecraft.world.item.crafting.display.SmithingRecipeDisplay;
import net.minecraft.world.item.crafting.display.StonecutterRecipeDisplay;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

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

    /** The standalone overlay box.  SlotSelectTime drives the ingredient
     *  rotation animation: an index that advances every ~1.5s (same cadence as
     *  the vanilla recipe book's time/30), so the recipe previews rotate through
     *  interchangeable materials like the ghost ingredients do. */
    private static final OverlayRecipeComponent overlay =
            new OverlayRecipeComponent(
                    () -> Mth.floor(net.minecraft.util.Util.getMillis() / 1500.0D), false);

    /** Collection backing the open overlay (for partial snapshot cleanup). */
    private static RecipeCollection currentCollection;

    /** Viewer-overlay recipe button hovered when R/U was pressed (anchor). */
    private static AbstractWidget anchorOverlayWidget;

    /** Recipe-book button hovered when R/U was pressed (anchor + fromBook flag). */
    private static RecipeButton anchorBookButton;

    /** Screen the open overlay belongs to; the overlay closes when it is removed. */
    private static AbstractContainerScreen<?> ownerScreen;

    // ── Paging ─────────────────────────────────────────────────────────────
    // Over 50 hits the overlay shows PAGE_SIZE (10 x 5) recipes per page with
    // the RBIP turn-page buttons above the box.
    /** The vanilla alternative-group background sprite (also used by the paged box). */
    private static final Identifier OVERLAY_RECIPE_SPRITE =
            Identifier.withDefaultNamespace("recipe_book/overlay_recipe");
    private static final Identifier RBIP_PAGE_BUTTONS =
            Identifier.fromNamespaceAndPath("brbe", "textures/rbip/recipe_book_buttons.png");
    private static final int PAGE_COLS = 10;
    private static final int PAGE_ROWS = 5;
    private static final int PAGE_SIZE = PAGE_COLS * PAGE_ROWS;
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

    /** Full ordered recipe list of the open viewer (across all pages). */
    private static List<RecipeDisplayEntry> viewerRecipes = List.of();
    /** Current page index and total page count. */
    private static int viewerPage;
    private static int viewerPageCount = 1;

    // ── Category tabs (BRBE's bottom-tab textures, drawn rotated -90°:
    //    the 35x27 texture displays as a 27x35 tab hanging below the box) ──
    private static final Identifier UNSELECTED_BOTTOM_TAB =
            Identifier.fromNamespaceAndPath("brbe", "textures/rbip/bottom_tab.png");
    private static final Identifier SELECTED_BOTTOM_TAB =
            Identifier.fromNamespaceAndPath("brbe", "textures/rbip/bottom_tab_selected.png");
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
    /** Tabs only fold into pages once there are more than this many. */
    private static final int MAX_TABS = 10;

    // ── Category ────────────────────────────────────────────────────────────
    /** The item queried when R/U opened the viewer (re-queried on tab switch). */
    private static ItemStack queryTarget;
    /** Whether the open query was "usage" (U) rather than "result" (R). */
    private static boolean queryUsage;
    /** Category whose results are currently shown. */
    private static RecipeViewerCategory currentCategory;

    /** Item under the cursor in a grid category (fuel / compost / info). */
    private static ItemStack gridHoverStack;
    /** The grid category owning {@link #gridHoverStack} (differs from
     *  {@code currentCategory} for browse-all's plain cells). */
    private static RecipeViewerCategory gridHoverCategory;
    /** Items shown by a grid category (cached on rebuild). */
    private static List<ItemStack> gridItems = List.of();

    /** Whether the currently shown category is the furnace category. */
    public static boolean isFurnaceMode() {
        return currentCategory != null && "furnace".equals(currentCategory.id());
    }

    /** Whether the currently shown category is the stonecutter category. */
    public static boolean isStonecuttingMode() {
        return currentCategory != null && "stonecutting".equals(currentCategory.id());
    }

    /** Whether the currently shown category is the smithing category. */
    public static boolean isSmithingMode() {
        return currentCategory != null && "smithing".equals(currentCategory.id());
    }

    /** Whether the currently shown category is the anvil category. */
    public static boolean isAnvilMode() {
        return currentCategory != null && "anvil".equals(currentCategory.id());
    }

    /** Whether the currently shown category is the brewing category. */
    public static boolean isBrewingMode() {
        return currentCategory != null && "brewing".equals(currentCategory.id());
    }

    /** Whether the currently shown category is the grindstone category. */
    public static boolean isGrindstoneMode() {
        return currentCategory != null && "grindstone".equals(currentCategory.id());
    }

    /** First visible category index of the REI-style sliding tab window (window
     *  size = {@link #MAX_TABS}); {@code 0} when every tab fits.  The wheel over
     *  the tab strip switches the selected category and slides the window when
     *  the selection reaches an edge. */
    private static int tabWindowStart;

    /** Workstation objects of the open category, shown in the viewer's left
     *  column (bottom-up; a sliding window when there are more than the object
     *  area's rows). */
    private static List<ItemStack> stationColumnItems = List.of();
    /** Left-column scroll: 0 = the bottom-most window, each step slides the
     *  window one cell up. */
    private static int stationScroll;

    /** Ctrl+O browse-all mode: every category tab shows its COMPLETE object
     *  pool ({@code allEntries()} / {@code allGridItems()}) instead of the
     *  query-related subset — the "house" metaphor: the query herds the
     *  related objects into the viewer's categories, Ctrl+O gathers ALL
     *  queryable objects and distributes them into their correct categories
     *  (the tabs), a second Ctrl+O drives the newly added objects back out. */
    private static boolean browseAllMode;
    /** Page of the selected category before browse-all was entered. */
    private static int browseAllReturnPage;
    /** The category selected before browse-all was entered (a tab that existed
     *  pre-toggle): a restore re-selects it when the current tab only exists
     *  in browse-all. */
    private static RecipeViewerCategory browseAllReturnCategory;

    /** The left-column hover tooltip, deferred until the END of the overlay's
     *  own render pass: the GUI paints in call order, and
     *  {@code gui.renderTooltip(...)} renders in place — a tooltip built
     *  inside the station cell loop was covered by the cells drawn after it. */
    private static List<net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent>
            pendingStationTooltip;
    private static int pendingStationTooltipX;
    private static int pendingStationTooltipY;
    private static Identifier pendingStationTooltipStyle;

    /** The category-tab hover tooltip, deferred to the end of the overlay's
     *  render pass exactly like the station column's (the hover tooltip is
     *  built in the tabs-behind pass, while the box itself is painted after
     *  it — an in-place render would be covered). */
    private static List<net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent>
            pendingTabTooltip;
    private static int pendingTabTooltipX;
    private static int pendingTabTooltipY;
    private static Identifier pendingTabTooltipStyle;

    /** Fixed box layout for the open viewer (reused when switching tabs). */
    private static int boxX;
    private static int boxY;
    private static int boxW;
    private static int boxH;
    /** Screen Y of the tab strip (the box bottom), fixed on open so switching
     *  tabs never makes the tabs jump vertically when the box height changes. */
    private static int bottomAnchor;
    /** Pinned CENTRE of the first row's first object.  Initialised from the
     *  pointer on open, then FOLLOWS the actual centre after every layout
     *  (limit-level adjustments included): rebuilds start from the settled
     *  position, so the interface never snaps back to a pre-adjustment spot. */
    private static int anchorScreenX;
    private static int anchorScreenY;

    /** Alt-pause state for cycled variants (shared by every BRBE front-end):
     *  while Alt is held the rotation freezes (locked on Alt-press), Alt+wheel
     *  steps {@link #manualCycleIndex}, releasing Alt resumes the automatic
     *  cycle.  The vendored CycleTicker/CycleTimer pause on the same Alt keys
     *  and honour the same manual index via reflection. */
    private static boolean cyclePaused;
    private static int manualCycleIndex;

    /** The slot-select cycle index used by every BRBE front-end (popup,
     *  tooltip preview, pin, book-button variants, ghost slots): while Alt is
     *  held the rotation freezes on the Alt-press index and Alt+wheel steps
     *  it; on release the automatic cycle resumes. */
    public static int currentSlotSelectIndex(int autoIndex) {
        boolean alt = ClientCompat.isAltDown();
        if (alt) {
            if (!cyclePaused) {
                cyclePaused = true;
                manualCycleIndex = autoIndex;
            }
        } else if (cyclePaused) {
            cyclePaused = false;
            setForkManualIndex(-1);
        }
        return cyclePaused ? manualCycleIndex : autoIndex;
    }

    /** Alt+wheel: step the paused variant index (both the BRBE front-end and,
     *  via reflection, the vendored JEI cyclers). */
    private static boolean stepCycledVariants(double vertical) {
        cyclePaused = true;
        manualCycleIndex += vertical > 0 ? -1 : 1;
        setForkManualIndex(manualCycleIndex);
        return true;
    }

    /** Mirror the manual index into the vendored JEI cyclers (reflection: with
     *  the real JEI runtime the vendored classes are shadowed and the field
     *  does not exist — those drawables only pause, they cannot be stepped). */
    private static void setForkManualIndex(int index) {
        try {
            Class.forName("mezz.jei.library.gui.ingredients.CycleTicker")
                    .getField("manualIndexOverride").setInt(null, index);
            Class.forName("mezz.jei.library.gui.ingredients.CycleTimer")
                    .getField("manualIndexOverride").setInt(null, index);
        } catch (Throwable ignored) {
        }
    }

    /** Whether the cycle-pause key (Alt) is currently held — shared with the
     *  JEI-delegated drawable renderer so EVERY delegated UI's variant cycling
     *  (preview popup, pin, embedded tooltip preview) freezes too, without
     *  relying on JEI's own pause key mapping. */
    public static boolean isCycleAltDown() {
        return ClientCompat.isAltDown();
    }

    /** Opening-order value of the open viewer, shared with pin overlays for
     *  z-order stacking (-1 while closed). */
    private static int viewerZ = -1;

    public static boolean isActive() {
        return RecipeViewerIndex.isViewerActive();
    }

    /** The viewer's z (opening order) while open, or -1 when closed. */
    public static int viewerZ() {
        return isActive() ? viewerZ : -1;
    }

    /** The viewer's full on-screen region (box plus the category tabs hanging
     *  below it) in screen coordinates, or null when the viewer is closed.
     *  JEI's {@code IGlobalGuiHandler.getGuiExtraAreas} keeps its ingredient
     *  list / recipe area out of this region. */
    public static Rect2i exclusionArea() {
        if (!isActive()) return null;
        return new Rect2i(panelLeft(), boxY, boxW + STATION_COL_WIDTH, boxH + TAB_OVERHANG);
    }

    /** Whether the point lies on the viewer's own region — everything the
     *  viewer actually draws: the box at full size, the category tabs below
     *  it, and the left workstation panel TRIMMED to its content (see
     *  {@link #stationColumnPanelRect}).  The empty strip above a trimmed
     *  panel (fewer stations than the object area's rows) is background: it
     *  is not part of the viewer, so clicking there closes the viewer
     *  ({@link #inBox}) and hovering falls through to the underlying screen.
     *  {@link #exclusionArea()} stays over-inclusive (a single rect for JEI
     *  to keep out of — avoiding slightly more is harmless). */
    public static boolean contains(double mx, double my) {
        if (!isActive()) return false;
        // Left workstation panel, attached outside the box's left edge.
        if (mx >= panelLeft() && mx < panelLeft() + STATION_COL_WIDTH) {
            if (stationColumnItems.isEmpty()) return false;
            int shown = Math.min(stationColumnItems.size(), stationViewRows());
            int[] rect = stationColumnPanelRect(shown);
            return my >= rect[0] && my < rect[0] + rect[1];
        }
        return mx >= boxX && mx < boxX + boxW
                && my >= boxY && my < boxY + boxH + TAB_OVERHANG;
    }

    /** The open popup's on-screen region (its hit volume = texture bounds),
     *  or null when no popup is open.  JEI keeps its ingredient list / recipe
     *  area out of the exact same rect the popup's hit test uses. */
    public static Rect2i popupExclusionArea() {
        if (!isActive() || hoverPopupField == null) return null;
        PopupGeometry geometry = popupGeometry(hoverPopupField);
        return new Rect2i(geometry.x, geometry.y, geometry.w, geometry.h);
    }

    /** Close the viewer when its host screen is being removed. */
    public static void onScreenClosed(AbstractContainerScreen<?> screen) {
        if (ownerScreen == screen) {
            close();
        }
    }

    /** R/U / ESC / O handling.  Returns true when the event was consumed. */
    public static boolean keyPressed(KeyEvent event, AbstractContainerScreen<?> screen) {
        if (event.isEscape()) {
            // Esc closes only the top-most layer: a pin if one opened after the
            // viewer, else the viewer itself.
            return PinOverlayManager.handleEscape();
        }

        // O while the viewer is up toggles browse-all: every visible
        // category's objects at once, second press restores the selected
        // category.  Only monitored while the cursor is INSIDE the query
        // interface (its drawn region — box, workstation panel, tabs — or
        // the open popup), so the key stays free outside it.  Checked before
        // the R/U gate — it only ever acts on the open viewer.
        if (isActive() && event.key() == InputConstants.KEY_O) {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.mouseHandler != null && mc.getWindow() != null) {
                int mx = Mth.floor(mc.mouseHandler.getScaledXPos(mc.getWindow()));
                int my = Mth.floor(mc.mouseHandler.getScaledYPos(mc.getWindow()));
                if (contains(mx, my) || previewOwnsCursor(mx, my)) {
                    toggleBrowseAll();
                    return true;
                }
            }
        }

        if (!BetterRecipeBook.config.recipeViewerEnabled) return false;

        boolean viewRecipe = ClientCompat.matches(BetterRecipeBook.RECIPE_VIEW_MAPPING,
                event.key(), event.scancode(), event.modifiers());
        boolean viewUsage = ClientCompat.matches(BetterRecipeBook.USAGE_VIEW_MAPPING,
                event.key(), event.scancode(), event.modifiers());
        if (!viewRecipe && !viewUsage) return false;

        return open(screen, viewUsage);
    }

    /** Click handling while the viewer is up.  Returns true when consumed. */
    public static boolean mouseClicked(MouseButtonEvent event, boolean doubleClick,
                                       AbstractContainerScreen<?> screen) {
        if (!isActive()) return false;
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

        // The viewer's own turn-page buttons (above the box) flip the page.
        if (handlePageButtonClick(event)) {
            return true;
        }

        // Clicking the recipe book's turn-page buttons while the viewer is up:
        // keep the viewer open and swallow the click (the page turn itself is
        // blocked separately in the scrollable-pages mixin).
        if (isPageTurnButton(event, screen)) {
            return true;
        }

        // Click outside the box: dismiss the viewer, keep the container open.
        close();
        return true;
    }

    /** Place {@code id} from {@code collection} into the open recipe-book
     *  screen (with a ghost preview for missing materials), guarded by station
     *  matching — a crafting recipe clicked inside a furnace must not fill
     *  items or ghost slots of the wrong station.  Public so pin overlays can
     *  inherit the same click behaviour. */
    public static boolean placeRecipe(MouseButtonEvent event, AbstractContainerScreen<?> screen,
                                      RecipeDisplayId id, RecipeCollection collection) {
        // Click feedback plays for every consumed click — including one that
        // cannot place anything (e.g. a smelting recipe clicked inside a
        // crafting table, or any recipe clicked on a non-recipe-book screen):
        // the invalid click must still sound.
        Minecraft mc = Minecraft.getInstance();
        if (mc.getSoundManager() != null) {
            AbstractWidget.playButtonClickSound(mc.getSoundManager());
        }
        if (!(screen instanceof AbstractRecipeBookScreen<?> rbs)) return false;
        RecipeBookComponent<?> book = ((AbstractRecipeBookScreenAccessor) rbs)
                .brbe$getRecipeBookComponent();
        if (book == null || id == null || collection == null || !recipeFitsScreen(id, screen)) {
            return false;
        }
        try {
            RecipeBookComponentAccessor accessor = (RecipeBookComponentAccessor) book;
            boolean placed = accessor.tryPlaceRecipeInvoker(collection, id, event.hasShiftDown());
            if (!placed) {
                // Vanilla refuses the FIRST placement of a not-fully-craftable
                // recipe (tryPlaceRecipe returns early when
                // !collection.isCraftable(id) && id != lastPlacedRecipe), so
                // the first click of a partial / uncraftable recipe does
                // nothing — no ghost preview either.  Prime the repeat-click
                // path so the first click places the available materials (the
                // ghost flow) exactly like vanilla's second click.
                accessor.setLastPlacedRecipe(id);
                accessor.tryPlaceRecipeInvoker(collection, id, event.hasShiftDown());
            }
            // The vanilla ghost-overlay optimisation (PartialGhostOverlayUtil,
            // fed from lastRecipe/lastRecipeCollection in extractGhostRecipe)
            // must see the viewer's placed recipe too, otherwise every ghost
            // slot keeps its red mask.
            accessor.setLastRecipe(id);
            accessor.setLastRecipeCollection(collection);
        } catch (Exception e) {
            // Non-fatal: the placement already fired or is invalid.
        }
        return true;
    }

    /** Whether the click lands on the open recipe book's turn-page buttons. */
    private static boolean isPageTurnButton(MouseButtonEvent event, AbstractContainerScreen<?> screen) {
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
    public static boolean mouseScrolled(double mouseX, double mouseY, double vertical) {
        // Alt+wheel: step the cycled variants (Alt freezes the rotation) —
        // highest priority.  The trigger is the interface under the pointer
        // (viewer / preview popup / pin): wheel over plain background keeps
        // its normal behaviour instead of stepping variants.
        if (vertical != 0 && ClientCompat.isAltDown()
                && (isActive() || PinOverlayManager.hasPins())) {
            if (RecipePopupLayer.contains(mouseX, mouseY)
                    || PinOverlayManager.topInteractivePin(mouseX, mouseY) != null
                    || contains(mouseX, mouseY)) {
                stepCycledVariants(vertical);
                // The JEI-delegated drawables (preview popup / pin / embedded
                // tooltip preview) step through their display overrides — the
                // vendored-fork reflection above is shadowed under the real
                // JEI runtime, so this is what actually flips their items.
                SyntheticRecipeRenderers.get().stepVariants(vertical > 0 ? -1 : 1);
                return true;
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
        if (!isPaged()) return false;
        if (vertical == 0) return false;
        // Scroll zone: the box plus the turn-page button strip above it.
        if (overScrollZone(mouseX, mouseY)) {
            int delta = vertical > 0 ? -1 : 1;
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
                        PAGE_COLS * 25 + 8, PAGE_ROWS * 25 + 8);
            }
            return true;
        }
        // The open viewer is a modal layer: while it is up, the scroll goes
        // nowhere else (nothing underneath may page or scroll).
        return isActive();
    }

    /** Whether the cursor is over the box or the page-button strip above it. */
    private static boolean overScrollZone(double mouseX, double mouseY) {
        int bx = boxLeft();
        int by = boxTop();
        if (inside(mouseX, mouseY, bx, by, PAGE_COLS * 25 + 8, PAGE_ROWS * 25 + 8)) {
            return true;
        }
        int btnY = by - PAGE_BTN_HEIGHT - 2;
        int btnW = PAGE_BTN_WIDTH * 2 + 15;
        return inside(mouseX, mouseY, bx, btnY, btnW, PAGE_BTN_HEIGHT);
    }

    private static int boxLeft() {
        // The fuel grid has no OverlayRecipeComponent (no recipe buttons), so
        // its box lives in the static boxX/boxY fields, not the overlay.
        return isGridMode() ? boxX : ((OverlayRecipeComponentAccessor) overlay).getX();
    }

    /** The panel's left edge: one object-grid pitch left of the box — the box
     *  plus the left workstation column ("grid column -1") attached OUTSIDE it
     *  (the object area / tabs / page buttons keep their layout; the station
     *  column is a grid column appended on the box's left). */
    private static int panelLeft() {
        return boxLeft() - STATION_COL_WIDTH;
    }

    private static int boxTop() {
        return isGridMode() ? boxY : ((OverlayRecipeComponentAccessor) overlay).getY();
    }

    /** Whether scroll-around is enabled (turn-page buttons never hit a dead end). */
    private static boolean scrollWrap() {
        return BetterRecipeBook.config.scrolling.scrollAround;
    }

    /** Clicking the viewer's own turn-page buttons flips the page (or wraps when
     *  scroll-around is enabled); Ctrl+click jumps straight to the first / last
     *  page (the same edge-jump the recipe book's own turn buttons do). */
    private static boolean handlePageButtonClick(MouseButtonEvent event) {
        if (!isPaged() || event.button() != 0) return false;
        int bx = boxLeft();
        int by = boxTop();
        int btnY = by - PAGE_BTN_HEIGHT - 2;
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
                showPage(ownerScreen, bx, by, PAGE_COLS * 25 + 8, PAGE_ROWS * 25 + 8);
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
                showPage(ownerScreen, bx, by, PAGE_COLS * 25 + 8, PAGE_ROWS * 25 + 8);
            }
            return true;
        }
        return false;
    }

    /** Draw the overlay on the container's top render stratum. */
    public static void render(GuiGraphics gui, int mouseX, int mouseY, float delta) {
        if (!isActive()) return;
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
        // Drive the independent popup layer: the popup under the cursor opens /
        // keeps it, and leaving it (or releasing Shift) closes it.
        RecipePopupLayer.update(hoverPopupField);
        // The grid categories (fuel / compost / info) render a standalone item
        // grid (they are info sheets, not recipes — no buttons).
        if (isGridMode()) {
            RecipePopupLayer.close();
            drawCategoryTabs(gui, mouseX, mouseY, true);
            drawItemGrid(gui, mouseX, mouseY);
            drawPageControls(gui, mouseX, mouseY);
            drawCategoryTabs(gui, mouseX, mouseY, false);
            // The left workstation column (bottom-up, queryable objects) is
            // attached OUTSIDE the box's left edge and draws above the panel
            // background.
            drawStationColumn(gui, mouseX, mouseY);
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
            ClientCompat.blitSprite(gui, OVERLAY_RECIPE_SPRITE, bx, by,
                    boxW, boxH);
            List<AbstractWidget> buttons = acc.getRecipeButtons();
            for (AbstractWidget w : buttons) {
                w.render(gui, mouseX, mouseY, delta);
            }
            drawViewerPinMarkers(gui, buttons);
            drawPageControls(gui, mouseX, mouseY);
        } else {
            drawCategoryTabs(gui, mouseX, mouseY, true);
            // Draw the background at the widened box width (the extra columns
            // hold the tab strip), then the buttons at their re-flowed
            // 10-column positions (see showPage).  vanilla's render
            // shrink-wraps the background to the recipe columns, which would
            // leave the widened tabs floating past the box edge.
            OverlayRecipeComponentAccessor acc = (OverlayRecipeComponentAccessor) overlay;
            int bx = boxLeft();
            int by = acc.getY();
            ClientCompat.blitSprite(gui, OVERLAY_RECIPE_SPRITE, bx, by,
                    boxW, boxH);
            List<AbstractWidget> buttons = acc.getRecipeButtons();
            for (AbstractWidget w : buttons) {
                w.render(gui, mouseX, mouseY, delta);
            }
            drawViewerPinMarkers(gui, buttons);
            drawPageControls(gui, mouseX, mouseY);
        }
        drawCategoryTabs(gui, mouseX, mouseY, false);
        // The left workstation column (bottom-up, queryable objects) is
        // attached OUTSIDE the box's left edge and draws above the panel
        // background.
        drawStationColumn(gui, mouseX, mouseY);
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
    private static void drawViewerPinMarkers(GuiGraphics gui, List<AbstractWidget> buttons) {
        if (buttons.isEmpty() || viewerRecipes.isEmpty()) return;
        int pageStart = viewerPage * PAGE_SIZE;
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

    /** Whether the currently shown category is a standalone grid category
     *  (fuel / compost / info): no recipe buttons, a cell grid instead. */
    private static boolean isGridMode() {
        return currentCategory != null && currentCategory.isGridCategory();
    }

    /** Draw a grid category's standalone item grid: plain-overlay cells with
     *  a 16px item icon each; the hovered cell switches to the highlighted
     *  overlay (no zoom). */
    private static void drawItemGrid(GuiGraphics gui, int mouseX, int mouseY) {
        if (gridItems.isEmpty()) return;
        ClientCompat.blitSprite(gui, OVERLAY_RECIPE_SPRITE, boxLeft(), boxY,
                boxW, boxH);
        // Rows grow upward: row 0 sits at the box bottom (against the tab
        // strip); the box was sized to this page's rows/columns by
        // fitGridBoxToPage, so empty rows/columns are already dropped.
        int start = viewerPage * PAGE_SIZE;
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
            // The hovered cell swaps to the highlighted overlay sprite
            // (the query viewer's objects highlight on non-Shift hover).
            Identifier sprite = BRBTextures.RECIPE_BOOK_PLAIN_OVERLAY_SPRITE.get(true, hovered);
            ClientCompat.blitSprite(gui, sprite, gx, gy, 24, 24);
            gui.renderItem(gridItems.get(i), gx + 4, gy + 4);
            if (hovered) {
                gridHoverStack = gridItems.get(i);
                gui.requestCursor(com.mojang.blaze3d.platform.cursor.CursorTypes.POINTING_HAND);
            }
        }
    }

    /** X of the i-th category tab (i is the tab index within the current tab
     *  page).  The tab's icon center lands on the i-th column's center line
     *  (boxX + 16 + i*25): the icon sits (TAB_DRAW_WIDTH-16)/2 + 8 = 12px
     *  from the tab's left edge, so the tab starts at boxX + 4 + i*25. */
    private static int tabX(int i) {
        return boxX + 4 + i * TAB_WIDTH;
    }

    /** Top edge of the category-tab strip (4px above the box bottom, nudged
     *  1px down). */
    private static int tabTop() {
        return boxY + boxH - 4 + 1;
    }

    /** Tabs shown per page — the size of the REI-style sliding tab window
     *  ({@link #tabWindowStart}).  The box is widened (with empty columns) to
     *  hold up to {@link #MAX_TABS} tabs, so up to ten tabs are visible at once;
     *  with more, the window slides instead of folding into pages. */
    private static int visibleTabCount() {
        return MAX_TABS;
    }

    /** Categories that actually have results for the current query target
     *  (tabs with nothing to show are hidden).  With the "hide objects of
     *  workstations without a recipe book" toggle on, categories whose
     *  <b>every</b> object is hidden by the filter hide their tab too. */
    private static List<RecipeViewerCategory> visibleCategories() {
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
    private static RecipeViewerCategory bestContentCategory(ItemStack target, boolean usage,
                                                            RecipeViewerCategory exclude) {
        RecipeViewerCategory best = null;
        int bestPriority = -1;
        for (RecipeViewerCategory category : RecipeViewerCategories.all()) {
            if (category == exclude) continue;
            if (BetterRecipeBook.config.hideNoRecipeBookStationObjects
                    && !category.isGridCategory()
                    && category.appliesToStation(target)
                    && !RecipeViewerEngine.isRecipeBookStation(target)) {
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

    /** Cached ids of categories whose objects are ALL hidden by the filter
     *  (their tab is hidden too).  Rebuilt when the toggle state changes or
     *  after a plugin re-collection. */
    private static Set<String> cachedHiddenCategoryIds;
    private static boolean cachedHiddenConfigState;
    /** Browse-mode category list cache (all categories with a non-empty
     *  complete pool, hidden set applied); invalidated with the hidden set
     *  and on every mode flip. */
    private static List<RecipeViewerCategory> cachedBrowseCategories;
    private static boolean cachedBrowseState;

    private static Set<String> hiddenCategoryIds() {
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
    private static List<RecipeViewerCategory> browseCategories() {
        if (cachedBrowseCategories == null || cachedBrowseState != browseAllMode) {
            cachedBrowseState = browseAllMode;
            cachedBrowseCategories = computeBrowseCategories();
        }
        return cachedBrowseCategories;
    }

    private static List<RecipeViewerCategory> computeBrowseCategories() {
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

    /** Category ids whose every object has no recipe-book-backed workstation
     *  (built-in categories and the fuel category are exempt). */
    private static Set<String> computeHiddenCategoryIds() {
        Set<String> hidden = new HashSet<>();
        for (RecipeViewerCategory cat : RecipeViewerCategories.all()) {
            if (cat.isFuelCategory()) continue;
            if (!(cat instanceof PluginRecipeViewerCategory plugin)) continue;
            boolean anyVisible = false;
            for (String uid : plugin.uids()) {
                for (RecipeDisplayEntry entry : RecipeViewerEngine.allRecipes(uid)) {
                    if (entryHasRecipeBookStation(entry, cat.stationIconsFor(entry))) {
                        anyVisible = true;
                        break;
                    }
                }
                if (anyVisible) break;
            }
            if (!anyVisible) hidden.add(cat.id());
        }
        return hidden;
    }

    /** Category tabs along the box bottom (vanilla creative-inventory look).
     *  When more categories than columns, they are folded into pages of
     *  {@code visibleTabCount} and paged with the mouse wheel over the tab strip.
     *  {@code behind} selects the pass: {@code true} draws only the unselected
     *  tabs (painted before the box so its container UI covers their top edge);
     *  {@code false} draws only the selected tab, on top of the box. */
    private static void drawCategoryTabs(GuiGraphics gui, int mouseX, int mouseY,
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
    private static boolean previewOwnsCursor(int mx, int my) {
        return RecipePopupLayer.isActive() && RecipePopupLayer.contains(mx, my);
    }

    /** Whether the query UI owns the cursor: the open viewer's box, its open
     *  preview, or a pin overlay covers the point.  Underlying screen widgets
     *  (creative-inventory tabs, recipe-book tabs, …) must not hover or show
     *  their tooltips while the cursor is inside one of these modal regions. */
    public static boolean modalMaskOwnsCursor(int mx, int my) {
        if (PinOverlayManager.covers(mx, my)) return true;
        if (!isActive()) return false;
        return contains(mx, my) || previewOwnsCursor(mx, my);
    }

    /** Tooltip for a category tab: the category name with the sliding-window
     *  indicators, and the source-mod line directly below the title (gated by
     *  {@code showModName} like every other mod-name line, resolved from the
     *  category's icon item).  The indicators appear only while the strip
     *  actually slides (more categories than {@link #MAX_TABS}), like the
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
    private static void drawTabTooltip(GuiGraphics gui, RecipeViewerCategory cat,
                                       int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getInstance();
        List<net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent> components =
                new ArrayList<>();
        net.minecraft.util.FormattedCharSequence title = cat.name().getVisualOrderText();
        int maxStart = Math.max(0, visibleCategories().size() - MAX_TABS);
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
    private static Component selfModName() {
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
    private static void flushTabTooltip(GuiGraphics gui) {
        if (pendingTabTooltip == null) return;
        gui.renderTooltip(Minecraft.getInstance().font, pendingTabTooltip,
                pendingTabTooltipX, pendingTabTooltipY,
                net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner.INSTANCE,
                ClientCompat.VIEWER_TOOLTIP_STYLE);
        pendingTabTooltip = null;
    }

    /** Clicking a visible category tab switches the viewer to that category. */
    private static boolean handleCategoryTabClick(MouseButtonEvent event) {
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
                } else {
                    // Clicking the already-selected tab toggles browse-all:
                    // enters the "show all objects" view while querying,
                    // restores (same as pressing O) while browsing.
                    ClientCompat.playPageFlipSound(mc);
                    toggleBrowseAll();
                }
                return true;
            }
        }
        return false;
    }

    /** The built-in family backing {@code category}, for the left station
     *  column ({@code null} for categories without stations, e.g. info). */
    private static RecipeViewerIndex.Family familyForCategory(RecipeViewerCategory category) {
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
    private static void rebuildStationColumn() {
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
            stationColumnItems = RecipeViewerIndex.furnaceStationColumnItems();
            return;
        }
        stationColumnItems = RecipeViewerIndex.workstationItems(family);
    }

    /** How many station cells fit in the object area's height (the box's row
     *  count): the station window's viewport. */
    private static int stationViewRows() {
        return Math.max(1, (RecipeViewerOverlay.boxH - 8) / STATION_PITCH);
    }

    /** The workstation object in the column cell under (mx,my), or empty.
     *  Shared by the column click, R/U capture and the hover state. */
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

    /** Vertical span of the trimmed column panel (top..height).  The top
     *  border sits 5px above the topmost cell — the same inset the main
     *  box uses (its cells start at boxY+5), so a full column panel is exactly
     *  as tall as the main box (no off-by-one).  {@code shown} = visible rows. */
    private static int[] stationColumnPanelRect(int shown) {
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
    private static final Identifier COLUMN_PANEL_SPRITE =
            Identifier.fromNamespaceAndPath("brbe", "recipe_book/column_panel");

    /** Variant used when the column fills the whole object area: the trimmed
     *  top then lands exactly on the main box's top border (colTop == boxTop),
     *  so the top border must run to the panel's right edge — the panel's top
     *  border continues the box's top border as one straight line (the normal
     *  sprite's TR T-junction would cut it).  Same open right side / bottom
     *  band as {@link #COLUMN_PANEL_SPRITE}. */
    private static final Identifier COLUMN_PANEL_TOP_SPRITE =
            Identifier.fromNamespaceAndPath("brbe", "recipe_book/column_panel_top");

    private static void drawStationColumnSurfaces(GuiGraphics gui) {
        if (stationColumnItems.isEmpty()) return;
        int rows = stationViewRows();
        int shown = Math.min(stationColumnItems.size(), rows);
        if (shown <= 0) return;
        int[] rect = stationColumnPanelRect(shown);
        Identifier sprite = rect[0] == boxTop() ? COLUMN_PANEL_TOP_SPRITE : COLUMN_PANEL_SPRITE;
        ClientCompat.blitSprite(gui, sprite, panelLeft(), rect[0],
                STATION_COL_WIDTH + 4, rect[1]);
    }

    /** Draw the viewer's left workstation column: the open category's
     *  workstation objects as plain 24px cells (same look as the fuel grid,
     *  no info lines, queryable by click), bottom-aligned and laid out from
     *  the bottom up.  More stations than the object area's rows slide as a
     *  wheel-driven window; fewer show no empty carriers — the column's panel
     *  background is trimmed to the actual content (top edge follows the
     *  topmost cell; see {@link #drawStationColumnSurfaces}). */
    private static void drawStationColumn(GuiGraphics gui, int mouseX, int mouseY) {
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
    private static void flushStationTooltip(GuiGraphics gui) {
        if (pendingStationTooltip == null) return;
        gui.renderTooltip(Minecraft.getInstance().font, pendingStationTooltip,
                pendingStationTooltipX, pendingStationTooltipY,
                net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner.INSTANCE,
                ClientCompat.VIEWER_TOOLTIP_STYLE);
        pendingStationTooltip = null;
    }

    /** Clicking a left-column workstation object queries its recipes (re-opens
     *  the viewer for that object, R-key = "view recipe" semantics). */
    private static boolean handleStationColumnClick(MouseButtonEvent event) {
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
        return openFor(screen, hit, false);
    }

    /** Wheel over the left station column slides its window — only when there
     *  are more stations than visible rows (no empty carriers otherwise). */
    private static boolean handleStationColumnScroll(double mouseX, double mouseY, double vertical) {
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
    private static boolean overTabStrip(double mouseX, double mouseY) {
        int catCount = visibleCategories().size();
        if (catCount == 0) return false;
        int perPage = visibleTabCount();
        int shown = Math.min(perPage, catCount);
        int tabY = tabTop();
        return inside(mouseX, mouseY, boxX, tabY, shown * TAB_WIDTH, TAB_HEIGHT);
    }

    /** Scroll over the tab strip switches the selected category; as soon as
     *  the selection reaches the SIXTH slot counted from the edge it moves
     *  toward (6th from the left when scrolling right, 6th from the right
     *  when scrolling left), the REI-style tab window slides WITH the
     *  selection — the selected tab and the window move simultaneously, the
     *  highlight staying visually on that slot.  No animation — the tab
     *  switches immediately. */
    public static boolean mouseScrolledTabs(double mouseX, double mouseY, double vertical) {
        if (!isActive() || vertical == 0) return false;
        List<RecipeViewerCategory> cats = visibleCategories();
        if (cats.size() <= 1) return false;
        if (!overTabStrip(mouseX, mouseY)) return false;
        int idx = cats.indexOf(currentCategory);
        if (idx < 0) return false;
        int delta = vertical > 0 ? -1 : 1;
        int newIdx = idx + delta;
        if (newIdx < 0 || newIdx >= cats.size()) return false;
        int maxStart = Math.max(0, cats.size() - MAX_TABS);
        // Slide the window a step WITH the selection as soon as the selection
        // is at or past the 6th slot from the edge it moves toward (right:
        // slot >= 5, left: slot <= 4 in a MAX_TABS-wide window); the old rule
        // only slid once the selection ran off an edge.
        int slot = idx - tabWindowStart;
        if (delta > 0 && maxStart > 0 && slot >= 5) {
            tabWindowStart = Math.min(maxStart, tabWindowStart + 1);
        } else if (delta < 0 && maxStart > 0 && slot <= 4) {
            tabWindowStart = Math.max(0, tabWindowStart - 1);
        }
        // Keep the newly selected tab visible (safety net when the selection
        // arrived at an edge by other means).
        if (newIdx < tabWindowStart) {
            tabWindowStart = newIdx;
        } else if (newIdx >= tabWindowStart + MAX_TABS) {
            tabWindowStart = Math.min(maxStart, newIdx - (MAX_TABS - 1));
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
    public static boolean isInPopupArea(AbstractWidget widget, int mx, int my) {
        return popupGeometry(widget).contains(mx, my);
    }

    /** Whether the cursor is over {@code widget}'s own UI — the preview
     *  trigger area.  Confined to the object's button rect, uniformly across
     *  every viewer object (including unadapted plugin recipes, whose popup
     *  bounds are larger than — and offset from — the button): a preview opens
     *  only while the object itself is hovered, exactly like the BRBE-adapted
     *  objects.  The open preview's hit volume — its own texture bounds,
     *  {@link #isInPopupArea} — keeps it open while the cursor is inside it. */
    public static boolean isTriggerArea(AbstractWidget widget, int mx, int my) {
        return mx >= widget.getX() && mx < widget.getX() + widget.getWidth()
                && my >= widget.getY() && my < widget.getY() + widget.getHeight();
    }

    /** The shared popup geometry for {@code widget}'s recipe, in the layout
     *  mode of the entry's own category (browse-all mixes categories). */
    private static PopupGeometry popupGeometry(AbstractWidget widget) {
        OverlayRecipeButtonAccessor oba = (OverlayRecipeButtonAccessor) widget;
        RecipeDisplayId id = oba.brbe$getRecipe();
        return PopupGeometry.of(id, entryFor(id), viewerMode(), oba.brbe$getSlots(),
                widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight());
    }

    /** The viewer's current layout mode (furnace / stonecutter / smithing /
     *  anvil / brewing / grindstone / crafting), shared with the pin overlays
     *  and the popup geometry. */
    public static int viewerMode() {
        return modeForCategory(currentCategory);
    }

    /** Layout mode of a single category (the single source of
     *  {@link #viewerMode}). */
    private static int modeForCategory(RecipeViewerCategory category) {
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
    private static AbstractWidget hoverPopupField;

    /** The viewer button under the cursor regardless of Shift, for the
     *  always-detailed button tooltip when no popup is open. */
    private static AbstractWidget hoveredViewerButton;

    /** The item rendered under the cursor inside the popup (its current cycled
     *  variant), or EMPTY when the cursor is on empty space.  For JEI-adapted
     *  popups the item comes from the live JEI drawable (which drives the
     *  visible cycling itself), so the tooltip matches the painted variant. */
    public static ItemStack slotStackInPopup(AbstractWidget widget, int mx, int my) {
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
        int selIdx = currentSlotSelectIndex(
                ((OverlayRecipeComponentAccessor) overlay).getSlotSelectTime().currentIndex());
        return geometry.itemAt(mx, my, selIdx);
    }

    /** Full tooltip for a popup slot's item (item name + source-mod line),
     *  at the vanilla default position (no push-away — the mechanism is gone). */
    private static void renderPopupSlotTooltip(GuiGraphics gui, int mx, int my,
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
        for (Component line : lines) {
            components.add(net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent
                    .create(line.getVisualOrderText()));
        }
        gui.renderTooltip(mc.font, components, mx, my,
                net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner.INSTANCE,
                ClientCompat.VIEWER_TOOLTIP_STYLE);
    }

    public static void renderTooltip(GuiGraphics gui, int mouseX, int mouseY) {
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
                    components.add(new TitleWithIconTooltipComponent(
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
        int selIdx = currentSlotSelectIndex(
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
    public static void renderDetailedRecipeTooltip(GuiGraphics gui,
                                                   RecipeDisplayEntry entry, RecipeDisplayId id,
                                                   int mouseX, int mouseY, int selIdx) {
        // The pin overlays share this method; their tooltip carries no
        // embedded preview (the pin itself already shows the full UI).
        renderDetailedRecipeTooltip(gui, entry, id, null, false, false,
                mouseX, mouseY, selIdx, false);
    }

    /** Viewer object hover: the detailed tooltip with the recipe's full
     *  preview UI embedded (no Shift) — the same rendering the popup uses. */
    public static void renderDetailedRecipeTooltip(GuiGraphics gui,
                                                   RecipeDisplayEntry entry, RecipeDisplayId id,
                                                   List<?> slots, boolean craftable, boolean partial,
                                                   int mouseX, int mouseY, int selIdx) {
        renderDetailedRecipeTooltip(gui, entry, id, slots, craftable, partial,
                mouseX, mouseY, selIdx, true);
    }

    private static void renderDetailedRecipeTooltip(GuiGraphics gui,
                                                    RecipeDisplayEntry entry, RecipeDisplayId id,
                                                    List<?> slots, boolean craftable, boolean partial,
                                                    int mouseX, int mouseY, int selIdx,
                                                    boolean embedPreview) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.level == null) return;
        ItemStack output = resolveOutput(entry, mc, selIdx);
        if (output == null || output.isEmpty()) return;

        List<Component> lines = buildTooltipLines(mc, output, id, entry);
        Identifier style = output.get(net.minecraft.core.component.DataComponents.TOOLTIP_STYLE);
        List<net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent> components =
                new ArrayList<>(lines.size());
        for (int i = 0; i < lines.size(); i++) {
            if (i == 0) {
                // The title row also carries the item's icon to the right of
                // the name, vertically centred in the row.
                components.add(new TitleWithIconTooltipComponent(
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
            components.add(new RecipePreviewTooltipComponent(id, entry, viewerMode(),
                    slots, selIdx, craftable, partial));
        }
        if (RecipeViewerIndex.asFurnace(entry) != null) {
            components.addAll(furnaceTooltipComponents(entry));
        } else {
            // Crafting / stonecutting / smithing: show the workstations
            // that produce this recipe as icons at the bottom of the
            // tooltip (including mod workstations), no text label.
            components.addAll(stationIconsTooltipComponents(entry));
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

    /** Reproduces RecipeButton.getTooltipText including BRBE's appended lines. */
    private static List<Component> buildTooltipLines(Minecraft mc, ItemStack output, RecipeDisplayId id,
                                                     RecipeDisplayEntry entry) {
        List<Component> lines = new ArrayList<>(Screen.getTooltipFromItem(mc, output));

        // 3x3 "cannot craft here" warning comes above the source-mod line.
        if (BetterRecipeBook.config.showAllRecipesInSurvival
                && !BetterRecipeBook.config.hideIncompatibleMark
                && mc.screen instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen) {
            if (IncompatibleCraftingUtil.checkIncompatible(overlay.getRecipeCollection(), id)) {
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

    /** The tooltip's title row: the item's icon to the LEFT of the title
     *  text, both vertically centred in the row (the row height is the icon's
     *  16px). */
    private static final class TitleWithIconTooltipComponent
            implements net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent {
        private static final int ICON_SIZE = 16;
        private static final int GAP = 3;

        private final FormattedCharSequence title;
        private final ItemStack icon;

        TitleWithIconTooltipComponent(FormattedCharSequence title, ItemStack icon) {
            this.title = title;
            this.icon = icon;
        }

        @Override
        public int getWidth(Font font) {
            return ICON_SIZE + GAP + font.width(title);
        }

        @Override
        public int getHeight(Font font) {
            return Math.max(font.lineHeight, ICON_SIZE);
        }

        @Override
        public void renderText(GuiGraphics gui, Font font, int x, int y) {
            // Centre the title text vertically against the 16px row.
            int ty = y + (getHeight(font) - font.lineHeight) / 2;
            gui.drawString(font, title, x + ICON_SIZE + GAP, ty, -1, true);
        }

        @Override
        public void renderImage(Font font, int x, int y, int width, int height,
                                 GuiGraphics gui) {
            int iy = y + (getHeight(font) - ICON_SIZE) / 2;
            gui.renderItem(icon, x, iy, 0);
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
    private static final class StationMarkerTooltipComponent
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
    private static final class TabMarkerTitleTooltipComponent
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
     *  and name (e.g. "Workstation: [crafting table]"). */
    private static List<net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent>
            stationIconsTooltipComponents(RecipeDisplayEntry entry) {
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
    private static List<net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent>
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
    private static List<net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent>
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
    private static List<net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent>
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
    private static List<net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent>
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
    private static String fuelCount(int burn, int cookTime) {
        if (burn <= 0 || cookTime <= 0) return "0";
        return burn % cookTime == 0 ? String.valueOf(burn / cookTime)
                : String.format(Locale.ROOT, "%.1f", burn / (float) cookTime);
    }

    /** Turn-page buttons above the box (left edge aligned with the box left). */
    private static void drawPageControls(GuiGraphics gui, int mouseX, int mouseY) {
        if (!isPaged()) return;
        Minecraft mc = Minecraft.getInstance();
        int bx = boxLeft();
        int by = boxTop();
        int btnY = by - PAGE_BTN_HEIGHT - 2;
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

    private static void drawPageButton(GuiGraphics gui, int x, int y, boolean next,
                                      boolean active, int mouseX, int mouseY) {
        int u = next ? 14 : 0;
        if (active && !previewOwnsCursor(mouseX, mouseY)
                && inside(mouseX, mouseY, x, y, PAGE_BTN_WIDTH, PAGE_BTN_HEIGHT)) {
            u += 28;
        }
        int v = active ? 0 : 13;
        gui.blit(RenderPipelines.GUI_TEXTURED, RBIP_PAGE_BUTTONS, x, y, u, v,
                PAGE_BTN_WIDTH, PAGE_BTN_HEIGHT, 256, 256);
    }

    private static boolean inside(double x, double y, int left, int top, int width, int height) {
        return x >= left && x < left + width && y >= top && y < top + height;
    }

    /** Dismiss the viewer: clear state before hiding so the setVisible guard
     *  does not cancel this sanctioned close. */
    public static void close() {
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
        overlay.setVisible(false);
    }

    /**
     * Open the viewer for {@code target} (from the hovered item / recipe button /
     * ghost slot), anchored around it.  Returns false when there is no target or
     * no matching recipes, leaving the key event for vanilla handling.
     */
    private static boolean open(AbstractContainerScreen<?> screen, boolean viewUsage) {
        ItemStack target = PinOverlayManager.captureTarget(screen);
        if (target.isEmpty()) return false;
        return openFor(screen, target, viewUsage);
    }

    /** Open the viewer for an explicit {@code target} — the shared body of
     *  {@link #open} and the left station-column click query. */
    private static boolean openFor(AbstractContainerScreen<?> screen, ItemStack target,
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
        // when the pointer is outside the window): unless a limit-level
        // position adjustment kicks in, the first object's centre sits on the
        // mouse.
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
        // the limit-level clamps (25px edges, crafting-grid avoidance) run
        // later in fitBoxToPage against the ACTUAL post-shrink box size, so a
        // short box keeps its first row centred on the pointer even near the
        // screen edges — only when the box really cannot fit (or would cover
        // the crafting grid) does a position adjustment fire.
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
        // object centre (limit-level adjustments included).
        repaginateToSelected();
        rebuildStationColumn();
        viewerZ = PinOverlayManager.nextZ();
        RecipeViewerIndex.setViewerActive(true);
        RecipeViewerIndex.setViewerOpenedFromBook(anchorBookButton != null);
        return true;
    }

    /** BRBE's engine found nothing for this item (e.g. a mod item while JEI is
     *  installed and jei-plugins is not): route the query to the active recipe
     *  viewer (JEI/REI) so mod recipes still open.  Returns whether the event
     *  was consumed. */
    private static boolean fallbackToViewer(ItemStack target, boolean viewUsage) {
        if (!ItemViewCompat.isLoaded()) return false;
        return viewUsage ? ItemViewCompat.openUsageView(target)
                         : ItemViewCompat.openRecipeView(target);
    }

    /** Rebuild the overlay contents from a (possibly new category's) query hits,
     *  reusing the fixed box layout. */
    private static void rebuildWithHits(List<RecipeDisplayEntry> hits) {
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

        // Pinned (recipe-book pin) first, then fully-craftable, partial, uncraftable.
        List<RecipeDisplayEntry> entries = collection.getRecipes();
        entries.sort((a, b) -> Integer.compare(recipeRank(collection, b), recipeRank(collection, a)));

        viewerRecipes = new ArrayList<>(entries);
        computeBoxSize(hits);
        // The final box position is clamped inside showPage -> fitBoxToPage
        // with the ACTUAL (post-shrink) box size.  Clamping here with the
        // full-page size would misjudge the 25px edges AND rewrite the
        // pointer-derived bottomAnchor (a phantom fifth row as a boundary),
        // which is exactly what the caller just anchored to the mouse.
        viewerPage = 0;
        showPage(ownerScreen, boxX, boxY, boxW, boxH);
    }

    /** Compute boxW/boxH and viewerPageCount from the hit count. */
    private static void computeBoxSize(List<RecipeDisplayEntry> hits) {
        computeBoxSize(hits.size());
    }

    /** Shared box sizing for the recipe and fuel grids: page count from the
     *  total, box at the full PAGE_COLS x PAGE_ROWS size.  The box is then
     *  shrunk to the current page's actual rows/columns by
     *  {@link #fitBoxToPage} (called from showPage and the grid paths), which
     *  also re-clamps the position. */
    private static void computeBoxSize(int total) {
        boolean paged = total > PAGE_SIZE;
        viewerPageCount = paged ? (total + PAGE_SIZE - 1) / PAGE_SIZE : 1;
        boxW = PAGE_COLS * 25 + 8;
        boxH = PAGE_ROWS * 25 + 8;
        ensureTabWidth();
    }

    /** Shrink the box to {@code pageCount} objects and re-clamp it: columns
     *  cap at PAGE_COLS and empty rows/columns are dropped (the tab strip can
     *  still widen the box via {@link #ensureTabWidth}), and the box re-anchors
     *  to the first-object centre ({@link #anchorScreenX} / {@link
     *  #anchorScreenY}).
     *
     *  <p>ESTABLISHED RULE: every limit-level position adjustment (25px edge
     *  clamps, crafting-grid avoidance) is followed by refreshing the anchor
     *  to the ACTUAL centre of the first row's first object — the settled
     *  position is where the next rebuild starts from, so the interface never
     *  snaps back to a pre-adjustment spot.  Returns the column count, which
     *  the caller uses to place its objects. */
    private static int fitBoxToPage(int pageCount) {
        int columns = Math.max(1, Math.min(PAGE_COLS, pageCount));
        int rows = (pageCount + columns - 1) / columns;
        boxW = columns * 25 + 8;
        boxH = rows * 25 + 8;
        ensureTabWidth();
        boxX = anchorScreenX - 16;
        boxY = anchorScreenY - boxH + 16;
        clampBoxToAnchor();
        clampBoxX();
        avoidCraftingGrid();
        // RULE: refresh the anchor after every limit-level adjustment.
        anchorScreenX = boxX + 16;
        anchorScreenY = boxY + boxH - 16;
        bottomAnchor = anchorScreenY + 16;
        return columns;
    }

    /** Push the box below the crafting grid when it would actually cover the
     *  grid — judged with the REAL (post-shrink) box size, so a short box
     *  that already clears the grid is left anchored to the pointer.  This is
     *  a limit-level position adjustment: when it fires, the first row no
     *  longer centres on the mouse. */
    private static void avoidCraftingGrid() {
        if (ownerScreen == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.getWindow() == null) return;
        int guiH = mc.getWindow().getGuiScaledHeight();
        int gridLeft = gridLeftScreenX(ownerScreen);
        int gridRight = gridRightScreenX(ownerScreen);
        int gridBottom = gridBottomScreenY(ownerScreen);
        if (gridLeft < 0 || gridRight < 0 || gridBottom < 0) return;
        if (boxX >= gridRight || boxX + boxW <= gridLeft || boxY >= gridBottom) return;
        boxY = gridBottom;
        int overlayH = boxH + TAB_OVERHANG;
        if (boxY + overlayH > guiH) {
            boxY = Math.max(0, guiH - overlayH);
        }
        // bottomAnchor is left untouched: the pinned first-object centre
        // survives the avoidance, so paging away from a grid-covering page
        // returns the first object to the anchor.
    }

    /** {@link #fitBoxToPage} for a grid category, sized to the current page's
     *  slice of {@link #gridItems}. */
    private static void fitGridBoxToPage() {
        int start = viewerPage * PAGE_SIZE;
        int count = Math.min(start + PAGE_SIZE, gridItems.size()) - start;
        fitBoxToPage(count);
    }

    /** Compute boxW/boxH and viewerPageCount for a grid category's item grid —
     *  same paging rule as the recipe grid, so a long list pages too. */
    private static void computeGridBoxSize() {
        computeBoxSize(gridItems.size());
    }

    /** Widen the box (with empty columns) so the tab strip can show up to
     *  {@link #MAX_TABS} tabs on a page without folding when there are more tabs
     *  than recipe columns.  Only above {@link #MAX_TABS} do tabs fold into
     *  pages. */
    private static void ensureTabWidth() {
        int tabCount = Math.min(visibleCategories().size(), MAX_TABS);
        int tabW = tabCount * TAB_WIDTH + 8;
        if (tabW > boxW) {
            boxW = tabW;
        }
    }

    /** Lay out a grid category's item grid from {@code items}: a usage query of
     *  a specific item passes that item alone (JEI per-item semantics), a
     *  usage query of its station passes the whole list.  Keeps the tab strip
     *  anchored. */
    private static void rebuildGrid(List<ItemStack> items) {
        gridItems = items;
        gridHoverStack = null;
        viewerRecipes = List.of();
        computeGridBoxSize();
        // Shrink the box to the first page (rows/columns without an object are
        // dropped) and re-apply the 25px edge margins; the grid cells are
        // positioned per-frame from boxX/boxY, so they follow automatically.
        viewerPage = 0;
        fitGridBoxToPage();
    }

    /** Switch the viewer to {@code category}, re-querying the stored target.
     *  Repagination happens here, before the next render, so the newly selected
     *  tab always lands on the first visible row of the folded tab strip. */
    private static void switchCategory(RecipeViewerCategory category) {
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
        clampBoxX();
        repaginateToSelected();
        rebuildStationColumn();
    }

    /** Filter a query's hits by the "hide objects of workstations without a
     *  recipe book" toggle: objects whose <b>every</b> workstation lacks a
     *  recipe-book system are dropped.  Objects that also have a legitimate
     *  (recipe-book-backed) workstation survive — their tooltip icons are
     *  filtered separately.  No-op when the toggle is off. */
    private static List<RecipeDisplayEntry> filterByRecipeBookStations(List<RecipeDisplayEntry> hits) {
        return filterByRecipeBookStations(hits, currentCategory);
    }

    /** Category-aware variant (browse-all): each category's objects are
     *  judged against the category they CAME FROM, not the pre-toggle
     *  currentCategory — the old way would mis-judge every other category's
     *  entries (dropping legitimate ones or leaking illegal ones). */
    private static List<RecipeDisplayEntry> filterByRecipeBookStations(
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
    private static boolean hasRecipeBookStation(RecipeDisplayEntry entry) {
        return hasRecipeBookStation(entry, currentCategory);
    }

    /** Category-aware variant (browse-all) of {@link #hasRecipeBookStation}. */
    private static boolean hasRecipeBookStation(RecipeDisplayEntry entry,
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
    private static boolean entryHasRecipeBookStation(RecipeDisplayEntry entry, List<ItemStack> icons) {
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
     *  objects the hide toggle filters like any mod station's. */
    private static boolean isBuiltinCategory(RecipeViewerCategory category) {
        if (category == null) return false;
        return switch (category.id()) {
            case "furnace", "crafting", "smithing", "fuel" -> true;
            default -> false;
        };
    }

    /** Re-clamp boxX so a box widened by a category switch stays on screen —
     *  the same >= 25px edge margin {@code open()} applies when the box fits,
     *  else fully inside. */
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

    /** Re-clamp boxY after a rebuild (category switch / browse toggle) grew or
     *  shrank the box: the tab strip stays anchored to {@link #bottomAnchor}
     *  (pinned to the first object's centre on open — it is NEVER rewritten
     *  here, so a limit-level adjustment does not permanently move the
     *  anchor), the box grows upward from it, and the top keeps the >= 25px
     *  edge margin (bottom too when the box fits; otherwise fully inside). */
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

    /** Keep the REI-style tab window valid and the selected category visible
     *  inside it — the window slides instead of paging, and folding never hides
     *  the selected tab.  Call after the box layout has been rebuilt (open /
     *  category switch). */
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

    /** Ctrl+O toggle (monitored only while the cursor is inside the query
     *  interface): gathers ALL queryable objects into the viewer and
     *  distributes them into their correct categories — the "house"
     *  metaphor: querying an item herds its related objects in, Ctrl+O
     *  imports every queryable object into its own category (tab), a second
     *  Ctrl+O drives the newly added ones back out. */
    private static void toggleBrowseAll() {
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
    private static void enterBrowseAll() {
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
    private static void leaveBrowseAll() {
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
    private static boolean categoryHasQueryContent(RecipeViewerCategory category) {
        if (category == null) return false;
        return category.isGridCategory()
                ? !gridSource(category).isEmpty()
                : !categoryHits(category).isEmpty();
    }

    /** The category's objects for the current mode: its complete pool while
     *  browsing (Ctrl+O), its query-related subset otherwise. */
    private static List<RecipeDisplayEntry> categoryHits(RecipeViewerCategory category) {
        List<RecipeDisplayEntry> hits = new ArrayList<>(filterByRecipeBookStations(
                browseAllMode ? category.allEntries()
                        : category.query(queryTarget, queryUsage),
                category));
        // 配方书 pin 的配方对象置顶：pin 状态经稳定 key（idFor）匹配，只在
        // 命中数 >1 时重排（单条无需）。
        if (hits.size() > 1) {
            List<RecipeDisplayEntry> pinned = new ArrayList<>();
            List<RecipeDisplayEntry> rest = new ArrayList<>(hits.size());
            for (RecipeDisplayEntry entry : hits) {
                (BetterRecipeBook.pinnedRecipeManager.isPinnedEntry(entry) ? pinned : rest).add(entry);
            }
            if (!pinned.isEmpty()) {
                hits.clear();
                hits.addAll(pinned);
                hits.addAll(rest);
            }
        }
        return hits;
    }

    /** The grid category's item grid for the current mode (same semantics as
     *  {@link #categoryHits}). */
    private static List<ItemStack> gridSource(RecipeViewerCategory category) {
        return browseAllMode ? category.allGridItems()
                : category.gridItems(queryTarget, queryUsage);
    }

    /** Rebuild the selected category's own view — the shared body of
     *  {@link #switchCategory}, minus its early return (used when entering /
     *  leaving browse-all). */
    private static void refreshCurrentCategory(boolean restorePage) {
        if (currentCategory == null || ownerScreen == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (currentCategory.isGridCategory()) {
            List<ItemStack> items = gridSource(currentCategory);
            if (items.isEmpty()) return;
            rebuildGrid(items);
            clampBoxX();
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
            clampBoxX();
            // The browse-all tab list inserts categories before the selected
            // one; keep its tab inside the sliding window so the selection
            // stays visible after the mode flip.
            repaginateToSelected();
            rebuildStationColumn();
        }
    }

    /** Clear browse-all state (mode; the return page is cleared when the
     *  toggle completes). */
    private static void resetBrowseAllState() {
        browseAllMode = false;
    }

    /**
     * Lay out the current page: build a sub-collection of this page's recipes,
     * fit the box to the page (empty rows/columns dropped, first row at the
     * bottom) and re-flow the buttons onto the page's column pitch.
     */
    private static void showPage(AbstractContainerScreen<?> screen, int boxX, int boxY,
                                 int boxW, int boxH) {
        if (viewerRecipes.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        int start = viewerPage * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, viewerRecipes.size());
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
        int bx = RecipeViewerOverlay.boxX;
        int by = RecipeViewerOverlay.boxY;
        int bw = RecipeViewerOverlay.boxW;
        int bh = RecipeViewerOverlay.boxH;
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
    public static boolean isPaged() {
        return isActive() && viewerPageCount > 1;
    }

    /** Whether {@code o} is the standalone viewer overlay instance. */
    public static boolean isOwnOverlay(OverlayRecipeComponent o) {
        return o == overlay;
    }

    /**
     * Query target: viewer-overlay button first, then hovered container slot,
     * then ghost-preview slot, then a hovered recipe-book button.
     */
    public static ItemStack captureTarget(AbstractContainerScreen<?> screen) {
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
                                anchorBookButton = button;
                                return stack;
                            }
                        }
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
    public static RecipeDisplayId capturedOverlayRecipe() {
        if (anchorOverlayWidget instanceof OverlayRecipeButtonAccessor oba) {
            return oba.brbe$getRecipe();
        }
        return null;
    }

    /** The query-viewer overlay's recipe collection (the one holding the
     *  captured recipe button), or null.  Lets a pin clone render with the
     *  source recipe's craftable / partial state instead of recomputing it
     *  against a fresh collection. */
    public static RecipeCollection capturedOverlayCollection() {
        if (anchorOverlayWidget == null) return null;
        return overlay.getRecipeCollection();
    }

    /** Centre of the hovered query-viewer recipe button, or null when the last
     *  capture was not an overlay button.  Pins open centred on their source. */
    public static int[] capturedOverlayButtonCentre() {
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
    public static RecipeDisplayEntry entryFor(RecipeDisplayId id) {
        if (id == null) return null;
        RecipeDisplayEntry entry = RecipeViewerEngine.entryFor(id);
        if (entry != null) return entry;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return null;
        return ((ClientRecipeBookAccessor) mc.player.getRecipeBook()).brbe$getKnown().get(id);
    }

    private static boolean recipeFitsScreen(RecipeDisplayId id, AbstractContainerScreen<?> screen) {
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
    private static ItemStack overlayButtonResult(OverlayRecipeButtonAccessor button) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return ItemStack.EMPTY;
        try {
            RecipeDisplayId id = button.brbe$getRecipe();
            RecipeDisplayEntry entry = entryFor(id);
            if (entry == null) return ItemStack.EMPTY;
            List<ItemStack> results;
            try {
                results = entry.resultItems(SlotDisplayContext.fromLevel(mc.level));
            } catch (Exception e) {
                results = entry.resultItems(null);
            }
            if (results != null && !results.isEmpty()) return results.get(0);
        } catch (Exception e) {
            // fall through
        }
        return ItemStack.EMPTY;
    }

    /** If {@code slot} currently holds a ghost-preview ingredient, return its item. */
    private static ItemStack captureGhostItem(AbstractRecipeBookScreen<?> screen, Slot slot) {
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
            int idx = currentSlotSelectIndex(ghostAcc.getSlotSelectTime().currentIndex());
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

    /** Sort rank: 3 = pinned (recipe-book pin state), 2 = fully craftable,
     *  1 = partial (missing materials), 0 = uncraftable.  Pinned objects lead
     *  even the fully-craftable ones. */
    private static int recipeRank(RecipeCollection collection, RecipeDisplayEntry entry) {
        if (BetterRecipeBook.pinnedRecipeManager.isPinnedEntry(entry)) return 3;
        boolean craftable = collection.isCraftable(entry.id());
        boolean partial = PartialCraftingUtil.isPartiallyCraftable(collection, entry.id());
        if (craftable && !partial) return 2;
        if (partial) return 1;
        return 0;
    }

    /** Left edge (screen X) of the crafting grid, or -1 if the screen has none. */
    private static int gridLeftScreenX(Screen screen) {
        if (!(screen instanceof AbstractRecipeBookScreen<?> rs)) return -1;
        AbstractContainerScreenAccessor acc = (AbstractContainerScreenAccessor) rs;
        if (!(rs.getMenu() instanceof AbstractCraftingMenu menu)) return -1;
        int left = acc.brbe$getLeftPos();
        int gridLeft = Integer.MAX_VALUE;
        for (Slot slot : menu.getInputGridSlots()) {
            gridLeft = Math.min(gridLeft, left + slot.x);
        }
        return gridLeft == Integer.MAX_VALUE ? -1 : gridLeft;
    }

    /** Right edge (screen X) of the crafting grid, or -1 if the screen has none. */
    private static int gridRightScreenX(Screen screen) {
        if (!(screen instanceof AbstractRecipeBookScreen<?> rs)) return -1;
        AbstractContainerScreenAccessor acc = (AbstractContainerScreenAccessor) rs;
        if (!(rs.getMenu() instanceof AbstractCraftingMenu menu)) return -1;
        int left = acc.brbe$getLeftPos();
        int right = -1;
        for (Slot slot : menu.getInputGridSlots()) {
            right = Math.max(right, left + slot.x + 18);
        }
        return right;
    }

    /** Bottom Y (screen space) of the crafting grid, or -1 if the screen has none. */
    private static int gridBottomScreenY(Screen screen) {
        if (!(screen instanceof AbstractRecipeBookScreen<?> rs)) return -1;
        AbstractContainerScreenAccessor acc = (AbstractContainerScreenAccessor) rs;
        if (!(rs.getMenu() instanceof AbstractCraftingMenu menu)) return -1;
        int top = acc.brbe$getTopPos();
        int bottom = -1;
        for (Slot slot : menu.getInputGridSlots()) {
            bottom = Math.max(bottom, top + slot.y + 18);
        }
        return bottom;
    }

    /** Whether the click lands on the overlay box (buttons + padding) or the
     *  workstation panel. */
    private static boolean inBox(MouseButtonEvent event) {
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
