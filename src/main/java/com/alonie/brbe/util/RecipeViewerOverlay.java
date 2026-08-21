package com.alonie.brbe.util;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.cache.RecipeViewerIndex;
import com.alonie.brbe.compat.ItemViewCompat;
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
import com.alonie.brbe.recipeviewer.FuelRecipeCategory;
import com.alonie.brbe.recipeviewer.RecipeViewerCategories;
import com.alonie.brbe.recipeviewer.RecipeViewerCategory;
import com.alonie.brbe.render.PopupGeometry;
import com.alonie.brbe.recipeviewer.engine.RecipeViewerEngine;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.client.gui.GuiGraphicsExtractor;
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
import java.util.List;
import java.util.Locale;

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
            Identifier.fromNamespaceAndPath("zzzbrbe", "textures/rbip/recipe_book_buttons.png");
    private static final int PAGE_COLS = 10;
    private static final int PAGE_ROWS = 5;
    private static final int PAGE_SIZE = PAGE_COLS * PAGE_ROWS;
    private static final int PAGE_BTN_WIDTH = 14;
    private static final int PAGE_BTN_HEIGHT = 13;

    /** Full ordered recipe list of the open viewer (across all pages). */
    private static List<RecipeDisplayEntry> viewerRecipes = List.of();
    /** Current page index and total page count. */
    private static int viewerPage;
    private static int viewerPageCount = 1;

    // ── Category tabs (BRBE's bottom-tab textures, drawn rotated -90°:
    //    the 35x27 texture displays as a 27x35 tab hanging below the box) ──
    private static final Identifier UNSELECTED_BOTTOM_TAB =
            Identifier.fromNamespaceAndPath("zzzbrbe", "textures/rbip/bottom_tab.png");
    private static final Identifier SELECTED_BOTTOM_TAB =
            Identifier.fromNamespaceAndPath("zzzbrbe", "textures/rbip/bottom_tab_selected.png");
    private static final int TAB_TEX_WIDTH = 35;
    private static final int TAB_TEX_HEIGHT = 27;
    /** The rotated tab is too tall, so the texture's middle 4px (along its
     *  width) is cut out and the right half spliced onto the left half. */
    private static final int TAB_CUT = 6;
    private static final int TAB_LEFT = 16;
    private static final int TAB_RIGHT_START = TAB_LEFT + TAB_CUT;
    /** Displayed size after rotation (-90°) and the middle splice. */
    private static final int TAB_WIDTH = TAB_TEX_HEIGHT;
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

    /** Fuel item under the cursor in the fuel category. */
    private static ItemStack fuelHoverStack;
    /** Fuel items shown by the fuel category (cached on rebuild). */
    private static List<ItemStack> fuelGridItems = List.of();

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

    /** Tab-page index when more categories than columns force folding. */
    private static int tabPage;

    /** Fixed box layout for the open viewer (reused when switching tabs). */
    private static int boxX;
    private static int boxY;
    private static int boxW;
    private static int boxH;
    /** Screen Y of the tab strip (the box bottom), fixed on open so switching
     *  tabs never makes the tabs jump vertically when the box height changes. */
    private static int bottomAnchor;

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
        return new Rect2i(boxX, boxY, boxW, boxH + TAB_OVERHANG);
    }

    /** Whether the point lies on the viewer's own region (box + tabs). */
    public static boolean contains(double mx, double my) {
        Rect2i area = exclusionArea();
        return area != null && mx >= area.getX() && mx < area.getX() + area.getWidth()
                && my >= area.getY() && my < area.getY() + area.getHeight();
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

    /** R/U / ESC handling.  Returns true when the event was consumed. */
    public static boolean keyPressed(KeyEvent event, AbstractContainerScreen<?> screen) {
        if (event.isEscape()) {
            // Esc closes only the top-most layer: a pin if one opened after the
            // viewer, else the viewer itself.
            return PinOverlayManager.handleEscape();
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
        boolean hitButton = isFuelMode() ? false : overlay.mouseClicked(event, doubleClick);
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
                if (BetterRecipeBook.config.scrollPageSound) {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.getSoundManager() != null) {
                        AbstractWidget.playButtonClickSound(mc.getSoundManager());
                    }
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
        return isFuelMode() ? boxX : ((OverlayRecipeComponentAccessor) overlay).getX();
    }

    private static int boxTop() {
        return isFuelMode() ? boxY : ((OverlayRecipeComponentAccessor) overlay).getY();
    }

    /** Whether scroll-around is enabled (turn-page buttons never hit a dead end). */
    private static boolean scrollWrap() {
        return BetterRecipeBook.config.scrolling.scrollAround;
    }

    /** Clicking the viewer's own turn-page buttons flips the page (or wraps when
     *  scroll-around is enabled). */
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
            int prev = wrap
                    ? (viewerPage - 1 + viewerPageCount) % viewerPageCount
                    : Math.max(0, viewerPage - 1);
            if (prev != viewerPage && ownerScreen != null) {
                viewerPage = prev;
                AbstractWidget.playButtonClickSound(mc.getSoundManager());
                showPage(ownerScreen, bx, by, PAGE_COLS * 25 + 8, PAGE_ROWS * 25 + 8);
            }
            return true;
        }
        if (inside(mx, my, bx + 15, btnY, PAGE_BTN_WIDTH, PAGE_BTN_HEIGHT)) {
            int next = wrap
                    ? (viewerPage + 1) % viewerPageCount
                    : Math.min(viewerPageCount - 1, viewerPage + 1);
            if (next != viewerPage && ownerScreen != null) {
                viewerPage = next;
                AbstractWidget.playButtonClickSound(mc.getSoundManager());
                showPage(ownerScreen, bx, by, PAGE_COLS * 25 + 8, PAGE_ROWS * 25 + 8);
            }
            return true;
        }
        return false;
    }

    /** Draw the overlay on the container's top render stratum. */
    public static void render(GuiGraphicsExtractor gui, int mouseX, int mouseY, float delta) {
        if (!isActive()) return;
        // The popup layer opens only while Shift is held (no hover-open, no
        // Shift magnify any more): the popup under the cursor behaves like a
        // modal — while the cursor is inside it, it stays open and blocks every
        // button behind it.  Only when the cursor leaves it (Shift still held)
        // does a button under the cursor trigger its own popup.
        hoveredViewerButton = null;
        hoverPopupField = null;
        boolean shift = ClientCompat.isShiftDown();
        if (!isFuelMode()) {
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
        // The fuel category renders a standalone item grid (fuel is not a
        // recipe, so there are no OverlayRecipeComponent buttons).
        if (isFuelMode()) {
            RecipePopupLayer.close();
            drawCategoryTabs(gui, mouseX, mouseY, true);
            drawFuelGrid(gui, mouseX, mouseY);
            drawPageControls(gui, mouseX, mouseY);
            drawCategoryTabs(gui, mouseX, mouseY, false);
            renderTooltip(gui, mouseX, mouseY);
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
            int bx = acc.getX();
            int by = acc.getY();
            ClientCompat.blitSprite(gui, OVERLAY_RECIPE_SPRITE, bx, by, boxW, boxH);
            List<AbstractWidget> buttons = acc.getRecipeButtons();
            for (AbstractWidget w : buttons) {
                w.extractRenderState(gui, mouseX, mouseY, delta);
            }
            drawPageControls(gui, mouseX, mouseY);
        } else {
            drawCategoryTabs(gui, mouseX, mouseY, true);
            // Draw the background at the widened box width (the extra columns
            // hold the tab strip), then the buttons at their vanilla 4/5-column
            // positions.  vanilla's extractRenderState shrink-wraps the
            // background to the recipe columns, which would leave the widened
            // tabs floating past the box edge.
            OverlayRecipeComponentAccessor acc = (OverlayRecipeComponentAccessor) overlay;
            int bx = acc.getX();
            int by = acc.getY();
            ClientCompat.blitSprite(gui, OVERLAY_RECIPE_SPRITE, bx, by, boxW, boxH);
            for (AbstractWidget w : acc.getRecipeButtons()) {
                w.extractRenderState(gui, mouseX, mouseY, delta);
            }
            drawPageControls(gui, mouseX, mouseY);
        }
        drawCategoryTabs(gui, mouseX, mouseY, false);
        // The independent popup layer paints on top of everything (tabs and the
        // hovered button), then the viewer's tooltip (top-most) — the tooltip
        // is rendered here, not by the extractRenderState RETURN hook which
        // skips the viewer instance.
        RecipePopupLayer.render(gui, delta);
        renderTooltip(gui, mouseX, mouseY);
    }

    /** Whether the currently shown category is the fuel category. */
    private static boolean isFuelMode() {
        return currentCategory != null && currentCategory.isFuelCategory();
    }

    /** Draw the fuel category's standalone item grid: plain-overlay cells with
     *  a 16px item icon each; the hovered cell switches to the highlighted
     *  overlay (no zoom). */
    private static void drawFuelGrid(GuiGraphicsExtractor gui, int mouseX, int mouseY) {
        if (fuelGridItems.isEmpty()) return;
        ClientCompat.blitSprite(gui, OVERLAY_RECIPE_SPRITE, boxX, boxY, boxW, boxH);
        // Paged mode uses the fixed PAGE_COLS grid (matching computeBoxSize);
        // a single page auto-widens to fit the whole list.
        int columns = viewerPageCount > 1 ? PAGE_COLS
                : AlternativeOverlayLayout.columnsFor(fuelGridItems.size());
        fuelHoverStack = null;
        int start = viewerPage * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, fuelGridItems.size());
        for (int i = start; i < end; i++) {
            int idx = i - start;
            int gx = boxX + 4 + (idx % columns) * 25;
            int gy = boxY + 5 + (idx / columns) * 25;
            boolean hovered = inside(mouseX, mouseY, gx, gy, 24, 24);
            // The hovered fuel cell swaps to the highlighted overlay sprite
            // (the query viewer's objects highlight on non-Shift hover).
            Identifier sprite = BRBTextures.RECIPE_BOOK_PLAIN_OVERLAY_SPRITE.get(true, hovered);
            ClientCompat.blitSprite(gui, sprite, gx, gy, 24, 24);
            gui.item(fuelGridItems.get(i), gx + 4, gy + 4);
            if (hovered) {
                fuelHoverStack = fuelGridItems.get(i);
                gui.requestCursor(com.mojang.blaze3d.platform.cursor.CursorTypes.POINTING_HAND);
            }
        }
    }

    /** X of the i-th category tab (i is the tab index within the current tab
     *  page), stepped by the tab width so tabs never overlap. */
    private static int tabX(int i) {
        return boxX + 3 + i * TAB_WIDTH;
    }

    /** Top edge of the category-tab strip (4px above the box bottom, nudged
     *  1px down). */
    private static int tabTop() {
        return boxY + boxH - 4 + 1;
    }

    /** Tabs shown per page.  The box is widened (with empty columns) to hold up
     *  to {@link #MAX_TABS} tabs, so up to ten tabs sit on one page; only above
     *  that do they fold into pages paged with the mouse wheel over the strip. */
    private static int visibleTabCount() {
        return MAX_TABS;
    }

    /** Categories that actually have results for the current query target
     *  (tabs with nothing to show are hidden). */
    private static List<RecipeViewerCategory> visibleCategories() {
        if (queryTarget == null || queryTarget.isEmpty()) return List.of();
        List<RecipeViewerCategory> out = new ArrayList<>();
        for (RecipeViewerCategory cat : RecipeViewerCategories.all()) {
            if (cat.hasContent(queryTarget, queryUsage)) {
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
    private static void drawCategoryTabs(GuiGraphicsExtractor gui, int mouseX, int mouseY,
                                         boolean behind) {
        if (!isActive()) return;
        List<RecipeViewerCategory> cats = visibleCategories();
        if (cats.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        int perPage = visibleTabCount();
        int tabPageCount = Math.max(1, (cats.size() + perPage - 1) / perPage);
        tabPage = Math.max(0, Math.min(tabPage, tabPageCount - 1));
        int start = tabPage * perPage;
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
            gui.blit(RenderPipelines.GUI_TEXTURED, sprite, 0, 0,
                    0, 0, TAB_LEFT, TAB_TEX_HEIGHT, TAB_TEX_WIDTH, TAB_TEX_HEIGHT);
            // Right half spliced onto the left, skipping the middle TAB_CUT px.
            gui.blit(RenderPipelines.GUI_TEXTURED, sprite, TAB_LEFT, 0,
                    TAB_RIGHT_START, 0,
                    TAB_TEX_WIDTH - TAB_RIGHT_START, TAB_TEX_HEIGHT,
                    TAB_TEX_WIDTH, TAB_TEX_HEIGHT);
            gui.pose().popMatrix();
            int iconX = x + (TAB_WIDTH - 16) / 2;
            int iconY = tabY + (selected ? 6 : 4);
            gui.item(cat.icon(), iconX, iconY);
            // Fuel category: overlay the fire sprite on the furnace icon's
            // bottom-right (roughly the lower-right 4/9 region of the 16x16 icon).
            if (cat.isFuelCategory()) {
                ClientCompat.blitSprite(gui, BRBTextures.FURNACE_FIRE_SPRITE,
                        iconX + 10, iconY + 10, 6, 6);
            }
            if (!previewOwnsCursor(mouseX, mouseY)
                    && inside(mouseX, mouseY, x, tabY, TAB_WIDTH, TAB_HEIGHT)) {
                gui.requestCursor(com.mojang.blaze3d.platform.cursor.CursorTypes.POINTING_HAND);
                drawTabTooltip(gui, cat, mouseX, mouseY, tabPageCount);
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

    /** Tooltip for a category tab: the category name, the source-mod line
     *  directly below the title (gated by {@code showModName} like every other
     *  mod-name line, resolved from the category's icon item), and — when the
     *  tab strip is folded — the current page ("n/m") on a separate line. */
    private static void drawTabTooltip(GuiGraphicsExtractor gui, RecipeViewerCategory cat,
                                       int mouseX, int mouseY, int tabPageCount) {
        Minecraft mc = Minecraft.getInstance();
        List<Component> lines = new ArrayList<>();
        lines.add(cat.name());
        if (BetterRecipeBook.config.showModName) {
            Component modName = ModNameUtil.getFormattedModName(cat.icon());
            if (modName != null && !modName.getString().isEmpty()) {
                lines.add(Component.empty());
                lines.add(modName);
            }
        }
        if (tabPageCount > 1) {
            lines.add(Component.empty());
            lines.add(Component.literal((tabPage + 1) + "/" + tabPageCount));
        }
        gui.setComponentTooltipForNextFrame(mc.font, lines, mouseX, mouseY);
    }

    /** Clicking a visible category tab switches the viewer to that category. */
    private static boolean handleCategoryTabClick(MouseButtonEvent event) {
        if (event.button() != 0) return false;
        int mx = Mth.floor(event.x());
        int my = Mth.floor(event.y());
        int tabY = tabTop();
        List<RecipeViewerCategory> cats = visibleCategories();
        int perPage = visibleTabCount();
        int start = tabPage * perPage;
        int end = Math.min(start + perPage, cats.size());
        for (int i = start; i < end; i++) {
            if (inside(mx, my, tabX(i - start), tabY, TAB_WIDTH, TAB_HEIGHT)) {
                RecipeViewerCategory cat = cats.get(i);
                if (cat != currentCategory) {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.getSoundManager() != null) {
                        AbstractWidget.playButtonClickSound(mc.getSoundManager());
                    }
                    switchCategory(cat);
                }
                return true;
            }
        }
        return false;
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

    /** Scroll over the tab strip pages the folded categories. */
    public static boolean mouseScrolledTabs(double mouseX, double mouseY, double vertical) {
        if (!isActive() || vertical == 0) return false;
        List<RecipeViewerCategory> cats = visibleCategories();
        int perPage = visibleTabCount();
        int tabPageCount = Math.max(1, (cats.size() + perPage - 1) / perPage);
        if (tabPageCount <= 1) return false;
        if (!overTabStrip(mouseX, mouseY)) return false;
        int delta = vertical > 0 ? -1 : 1;
        int next = tabPage + delta;
        if (next < 0 || next >= tabPageCount) return false;
        tabPage = next;
        Minecraft mc = Minecraft.getInstance();
        if (BetterRecipeBook.config.scrollPageSound && mc.getSoundManager() != null) {
            AbstractWidget.playButtonClickSound(mc.getSoundManager());
        }
        return true;
    }

    /**
     * Recipe-button tooltip for the viewer, matching the vanilla recipe-book
     * button tooltip plus BRBE's lines (source-mod name, 3x3 warning).  Used by
     * the non-paged path (injected on extractRenderState RETURN) and drawn here
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

    /** The shared popup geometry for {@code widget}'s recipe, in the viewer's
     *  current layout mode. */
    private static PopupGeometry popupGeometry(AbstractWidget widget) {
        OverlayRecipeButtonAccessor oba = (OverlayRecipeButtonAccessor) widget;
        RecipeDisplayId id = oba.brbe$getRecipe();
        return PopupGeometry.of(id, entryFor(id), viewerMode(), oba.brbe$getSlots(),
                widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight());
    }

    /** The viewer's current layout mode (furnace / stonecutter / smithing /
     *  crafting), shared with the pin overlays and the popup geometry. */
    public static int viewerMode() {
        if (isFurnaceMode()) return PinOverlay.MODE_FURNACE;
        if (isStonecuttingMode()) return PinOverlay.MODE_STONECUTTING;
        if (isSmithingMode()) return PinOverlay.MODE_SMITHING;
        return PinOverlay.MODE_CRAFTING;
    }

    /** The query-viewer button whose popup the cursor currently sits in
     *  (top-most of any overlap), set each render while Shift is held; it
     *  drives the independent popup layer and the popup tooltip. */
    private static AbstractWidget hoverPopupField;

    /** The viewer button under the cursor regardless of Shift, for the
     *  always-detailed button tooltip when no popup is open. */
    private static AbstractWidget hoveredViewerButton;

    /** The item rendered under the cursor inside the popup (its current cycled
     *  variant), or EMPTY when the cursor is on empty space. */
    public static ItemStack slotStackInPopup(AbstractWidget widget, int mx, int my) {
        int selIdx = ((OverlayRecipeComponentAccessor) overlay).getSlotSelectTime().currentIndex();
        return popupGeometry(widget).itemAt(mx, my, selIdx);
    }

    /** Full tooltip for a popup slot's item (item name + source-mod line),
     *  at the vanilla default position (no push-away — the mechanism is gone). */
    private static void renderPopupSlotTooltip(GuiGraphicsExtractor gui, int mx, int my,
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
        gui.tooltip(mc.font, components, mx, my,
                net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner.INSTANCE,
                stack.get(net.minecraft.core.component.DataComponents.TOOLTIP_STYLE));
    }

    public static void renderTooltip(GuiGraphicsExtractor gui, int mouseX, int mouseY) {
        if (!isActive()) return;
        // A pin under the cursor owns the tooltip; the viewer's is suppressed.
        if (PinOverlayManager.covers(mouseX, mouseY)) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.level == null) return;
        // Fuel category: a single tooltip (item name + burn-time rows), no
        // shift variation, at the vanilla default position.
        if (isFuelMode() && fuelHoverStack != null && !fuelHoverStack.isEmpty()) {
            List<Component> lines = new ArrayList<>(Screen.getTooltipFromItem(mc, fuelHoverStack));
            List<net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent> components =
                    new ArrayList<>(lines.size());
            for (Component line : lines) {
                components.add(net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent
                        .create(line.getVisualOrderText()));
            }
            components.addAll(fuelTooltipComponents(fuelHoverStack));
            if (BetterRecipeBook.config.showModName) {
                Component modName = ModNameUtil.getFormattedModName(fuelHoverStack);
                if (modName != null && !modName.getString().isEmpty()) {
                    components.add(net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent
                            .create(Component.empty().getVisualOrderText()));
                    components.add(net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent
                            .create(modName.getVisualOrderText()));
                }
            }
            gui.tooltip(mc.font, components, mouseX, mouseY,
                    net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner.INSTANCE,
                    fuelHoverStack.get(net.minecraft.core.component.DataComponents.TOOLTIP_STYLE));
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
        // variant — at the vanilla default position.
        AbstractWidget hovered = hoveredViewerButton;
        if (hovered == null) return;
        RecipeDisplayId id = ((OverlayRecipeButtonAccessor) hovered).brbe$getRecipe();
        RecipeDisplayEntry entry = entryFor(id);
        if (entry == null) return;
        int selIdx = ((OverlayRecipeComponentAccessor) overlay).getSlotSelectTime().currentIndex();
        renderDetailedRecipeTooltip(gui, entry, id, mouseX, mouseY, selIdx);
    }

    /** The recipe's detailed result tooltip (name + BRBE rows + source-mod
     *  line), following the slot-select cycle's current result variant, at the
     *  vanilla default position.  Shared by the viewer's button hover and the
     *  pin overlays' no-shift tooltip (both inherit the query object's
     *  tooltip). */
    public static void renderDetailedRecipeTooltip(GuiGraphicsExtractor gui,
                                                   RecipeDisplayEntry entry, RecipeDisplayId id,
                                                   int mouseX, int mouseY, int selIdx) {
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
        gui.tooltip(mc.font, components, mouseX, mouseY,
                net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner.INSTANCE,
                style);
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
                && mc.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen) {
            if (IncompatibleCraftingUtil.checkIncompatible(overlay.getRecipeCollection(), id)) {
                lines.add(Component.empty());
                lines.add(Component.translatable("zzzbrbe.gui.environmentIncompatible")
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
            components.add(new StationLineTooltipComponent(List.of(
                    new StationLineTooltipComponent.Segment(line.getVisualOrderText(), icons, false))));
        }
        return components;
    }

    private static String stationLabel(int i) {
        return switch (i) {
            case 0 -> "zzzbrbe.cooktime.furnace";
            case 1 -> "zzzbrbe.cooktime.blast";
            case 2 -> "zzzbrbe.cooktime.smoker";
            case 3 -> "zzzbrbe.cooktime.campfire";
            default -> "zzzbrbe.cooktime.furnace";
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
        public void extractText(GuiGraphicsExtractor gui, Font font, int x, int y) {
            int cx = x;
            for (Segment seg : segments) {
                gui.text(font, seg.text(), cx, y, -1, true);
                cx += font.width(seg.text());
                if (!seg.icons().isEmpty()) {
                    cx += GAP + ICON_SIZE * seg.icons().size();
                }
                cx += GAP;
            }
        }

        @Override
        public void extractImage(Font font, int x, int y, int width, int height,
                                 GuiGraphicsExtractor gui) {
            int cx = x;
            for (Segment seg : segments) {
                int textW = font.width(seg.text());
                if (seg.dotBelow()) {
                    FormattedCharSequence dot = Component.literal("•")
                            .withStyle(ChatFormatting.WHITE).getVisualOrderText();
                    int dotW = font.width(dot);
                    gui.text(font, dot, cx + (textW - dotW) / 2, y + font.lineHeight, -1, true);
                }
                cx += textW + GAP;
                if (!seg.icons().isEmpty()) {
                    // Vertically centre the 16px icon against the text line
                    // (whose height is smaller than the icon), not against the
                    // whole row — the icon centre aligns with the text centre.
                    int iy = y + (font.lineHeight - ICON_SIZE) / 2;
                    for (ItemStack icon : seg.icons()) {
                        gui.item(icon, cx, iy, 0);
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
        public void extractText(GuiGraphicsExtractor gui, Font font, int x, int y) {
            // Centre the title text vertically against the 16px row.
            int ty = y + (getHeight(font) - font.lineHeight) / 2;
            gui.text(font, title, x + ICON_SIZE + GAP, ty, -1, true);
        }

        @Override
        public void extractImage(Font font, int x, int y, int width, int height,
                                 GuiGraphicsExtractor gui) {
            int iy = y + (getHeight(font) - ICON_SIZE) / 2;
            gui.item(icon, x, iy, 0);
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
        List<ItemStack> icons = category.stationIconsFor(entry);
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
                    if (cat.isFuelCategory()) continue;
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

    /** Burn-time tooltip rows for a fuel: one labelled row per furnace station
     *  (furnace / blast furnace / smoker), each showing how many items the fuel
     *  can smelt plus the station's workstation icon.  No shift variation, no
     *  campfire. */
    private static List<net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent>
            fuelTooltipComponents(ItemStack fuel) {
        FuelRecipeCategory category = (FuelRecipeCategory) currentCategory;
        int burn = category.burnDuration(fuel);
        // JEI-style: report how many standard (furnace 200-tick) items the fuel
        // can smelt, regardless of the station's own cook time.
        int[] cookTimes = { 200, 200, 200 };
        String unit = Component.translatable("zzzbrbe.cooktime.unit.items").getString();
        List<net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent> components =
                new ArrayList<>();
        components.add(net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent
                .create(Component.empty().getVisualOrderText()));
        for (int i = 0; i < 3; i++) {
            String value = fuelCount(burn, cookTimes[i]) + unit;
            Component line = stationTimeLine(stationLabel(i), value, stationStyle(i), false);
            components.add(new StationLineTooltipComponent(List.of(
                    new StationLineTooltipComponent.Segment(line.getVisualOrderText(),
                            java.util.Arrays.asList(RecipeViewerIndex.stationIcons(i)), false))));
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
    private static void drawPageControls(GuiGraphicsExtractor gui, int mouseX, int mouseY) {
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
                    mouseX, mouseY);
        }
    }

    private static void drawPageButton(GuiGraphicsExtractor gui, int x, int y, boolean next,
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
        tabPage = 0;
        bottomAnchor = 0;
        viewerRecipes = List.of();
        fuelGridItems = List.of();
        fuelHoverStack = null;
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
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return false;

        anchorOverlayWidget = null;
        anchorBookButton = null;
        ItemStack target = PinOverlayManager.captureTarget(screen);
        if (target.isEmpty()) return false;

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
            return fallbackToViewer(target, viewUsage);
        }
        List<RecipeDisplayEntry> hits = null;
        if (currentCategory.isFuelCategory()) {
            computeFuelBoxSize();
        } else {
            hits = currentCategory.query(target, viewUsage);
            if (hits.isEmpty()) {
                return fallbackToViewer(target, viewUsage);
            }
            computeBoxSize(hits);
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

        if (hits != null) computeBoxSize(hits);
        boolean paged = viewerPageCount > 1;

        // Keep >= 30px to every screen edge when the box fits, else fully inside.
        // The box top is the absolute top of the overlay: the page buttons drawn
        // above it do not participate in layout/margins.  The category tabs hang
        // below the box, so vertical clamping accounts for the tab overhang.
        int overlayH = boxH + TAB_OVERHANG;
        if (boxW <= guiW - 60) {
            boxX = Math.max(30, Math.min(anchorX, guiW - boxW - 30));
        } else {
            boxX = Math.max(0, Math.min(anchorX, guiW - boxW));
        }
        if (overlayH <= guiH - 60) {
            boxY = Math.max(30, Math.min(anchorY, guiH - overlayH - 30));
        } else {
            boxY = Math.max(0, Math.min(anchorY, guiH - overlayH));
        }

        // Crafting-grid rule: when the box horizontally overlaps the crafting
        // grid and the box top would cover the grid, push the box below the grid
        // so the grid stays visible.  Only crafting screens have a grid; other
        // containers skip the rule entirely.
        int gridLeft = gridLeftScreenX(screen);
        int gridRight = gridRightScreenX(screen);
        int gridBottom = gridBottomScreenY(screen);
        if (gridLeft >= 0 && boxX < gridRight && boxX + boxW > gridLeft && boxY < gridBottom) {
            boxY = gridBottom;
            if (boxY + overlayH > guiH) {
                boxY = Math.max(0, guiH - overlayH);
            }
        }

        // Anchor the tab strip (box bottom) here; category switches keep it
        // fixed and let the box grow upward.
        bottomAnchor = boxY + boxH;

        ownerScreen = screen;
        viewerPage = 0;
        if (currentCategory.isFuelCategory()) {
            // Usage query of a fuel: show that fuel alone (JEI per-fuel burn
            // semantics).  A workstation usage query never lands here —
            // defaultFor picks the furnace category first for furnace-family
            // workstations, so the fuel tab is reached via switchCategory.
            rebuildFuel(List.of(queryTarget));
        } else {
            rebuildWithHits(hits);
        }
        repaginateToSelected();
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
        // marks are based on the player's REAL inventory: screen-container
        // slots / carried may be virtual (creative tabs, grids).
        if (ownerScreen != null) {
            PartialCraftingUtil.prepareForViewer(collection,
                    PartialCraftingUtil.searchSpaceSlots(),
                    ownerScreen.getMenu().getCarried());
        }
        RecipeViewerIndex.snapshotPartials(collection);

        // Fully-craftable recipes first, then partial, then uncraftable.
        List<RecipeDisplayEntry> entries = collection.getRecipes();
        entries.sort((a, b) -> Integer.compare(recipeRank(collection, b), recipeRank(collection, a)));

        viewerRecipes = new ArrayList<>(entries);
        computeBoxSize(hits);
        // Keep the tab strip (box bottom) anchored; a category switch that
        // changes the row count grows the box upward instead of moving tabs.
        boxY = Math.max(0, bottomAnchor - boxH);
        viewerPage = 0;
        showPage(ownerScreen, boxX, boxY, boxW, boxH);
    }

    /** Compute boxW/boxH and viewerPageCount from the hit count. */
    private static void computeBoxSize(List<RecipeDisplayEntry> hits) {
        computeBoxSize(hits.size());
    }

    /** Shared box sizing for the recipe and fuel grids: pages at PAGE_SIZE
     *  with a fixed PAGE_COLS x PAGE_ROWS grid; a single page auto-widens to
     *  however many columns fit within PAGE_ROWS rows. */
    private static void computeBoxSize(int total) {
        boolean paged = total > PAGE_SIZE;
        viewerPageCount = paged ? (total + PAGE_SIZE - 1) / PAGE_SIZE : 1;
        if (paged) {
            boxW = PAGE_COLS * 25 + 8;
            boxH = PAGE_ROWS * 25 + 8;
        } else {
            int columns = AlternativeOverlayLayout.columnsFor(total);
            int rows = (total + columns - 1) / columns;
            boxW = Math.min(total, columns) * 25 + 8;
            boxH = rows * 25 + 8;
        }
        ensureTabWidth();
    }

    /** Compute boxW/boxH and viewerPageCount for the fuel grid — same paging
     *  rule as the recipe grid, so a long fuel list pages too. */
    private static void computeFuelBoxSize() {
        computeBoxSize(fuelGridItems.size());
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

    /** Lay out the fuel grid from {@code items}.  A usage query of a specific
     *  fuel passes that fuel alone (JEI's per-fuel FuelingRecipe); opening the
     *  fuel tab passes every registered fuel.  Keeps the tab strip anchored. */
    private static void rebuildFuel(List<ItemStack> items) {
        fuelGridItems = items;
        fuelHoverStack = null;
        viewerRecipes = List.of();
        computeFuelBoxSize();
        boxY = Math.max(0, bottomAnchor - boxH);
        viewerPage = 0;
    }

    /** Switch the viewer to {@code category}, re-querying the stored target.
     *  Repagination happens here, before the next render, so the newly selected
     *  tab always lands on the first visible row of the folded tab strip. */
    private static void switchCategory(RecipeViewerCategory category) {
        if (category == null || category == currentCategory) return;
        if (category.isFuelCategory()) {
            // Fuel tab: a fuel target shows that fuel alone (JEI per-fuel burn
            // semantics); a fuel-burning workstation target (furnace / blast
            // furnace / smoker, which share one fuel set) shows every fuel it
            // can take.  Anything else keeps the current category instead of
            // flooding the overlay.
            FuelRecipeCategory fuel = (FuelRecipeCategory) category;
            if (queryTarget == null || queryTarget.isEmpty()) {
                return;
            }
            boolean targetIsFuel = fuel.isFuelItem(queryTarget);
            boolean targetIsStation = fuel.isFuelStation(queryTarget);
            if (!targetIsFuel && !targetIsStation) {
                return;
            }
            currentCategory = category;
            rebuildFuel(targetIsFuel ? List.of(queryTarget) : fuel.allFuelItems());
        } else {
            List<RecipeDisplayEntry> hits = category.query(queryTarget, queryUsage);
            if (hits.isEmpty()) return;
            currentCategory = category;
            rebuildWithHits(hits);
        }
        clampBoxX();
        repaginateToSelected();
    }

    /** Re-clamp boxX so a box widened by a category switch stays on screen. */
    private static void clampBoxX() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getWindow() == null) return;
        int guiW = mc.getWindow().getGuiScaledWidth();
        boxX = Math.max(0, Math.min(boxX, guiW - boxW));
    }

    /** Set {@code tabPage} so the selected category sits on the first visible
     *  tab row — folding must never hide the selected tab.  Call after the box
     *  layout has been rebuilt (open / category switch). */
    private static void repaginateToSelected() {
        if (currentCategory == null) {
            tabPage = 0;
            return;
        }
        List<RecipeViewerCategory> cats = visibleCategories();
        if (cats.isEmpty()) {
            tabPage = 0;
            return;
        }
        int perPage = visibleTabCount();
        int idx = cats.indexOf(currentCategory);
        tabPage = Math.max(0, idx / perPage);
    }

    /**
     * Lay out the current page: build a sub-collection of this page's recipes,
     * (re)init the overlay at the fixed box position and, in paged mode, force
     * the vanilla 4/5-column layout back to the 10 x 5 grid.
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
        // The box width is authoritative (widened by ensureTabWidth to hold the
        // tab strip), so the overlay always lays out at the static width/height
        // — the box parameters are ignored for sizing, paging and switches stay
        // in agreement and the widened strip sits inside the box as empty columns.
        int w = RecipeViewerOverlay.boxW;
        int h = RecipeViewerOverlay.boxH;
        var ctx = SlotDisplayContext.fromLevel(mc.level);
        overlay.init(subset, ctx, false, boxX, boxY, w, h, paged ? 1.0f : 0f);
        currentCollection = subset;

        if (paged) {
            // init's internal shrink-wrap would move the box (it lays out with
            // at most 5 columns); pin the box position and re-flow the buttons
            // onto the fixed 10 x 5 grid.
            OverlayRecipeComponentAccessor acc = (OverlayRecipeComponentAccessor) overlay;
            acc.setX(boxX);
            acc.setY(boxY);
            List<AbstractWidget> buttons = acc.getRecipeButtons();
            for (int i = 0; i < buttons.size(); i++) {
                buttons.get(i).setX(boxX + 4 + (i % PAGE_COLS) * 25);
                buttons.get(i).setY(boxY + 5 + (i / PAGE_COLS) * 25);
            }
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
        if (isFuelMode() && fuelHoverStack != null && !fuelHoverStack.isEmpty()) {
            return fuelHoverStack;
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
            int idx = ghostAcc.getSlotSelectTime().currentIndex();
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

    /** Sort rank: 2 = fully craftable, 1 = partial (missing materials), 0 = uncraftable. */
    private static int recipeRank(RecipeCollection collection, RecipeDisplayEntry entry) {
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

    /** Whether the click lands on the overlay box (buttons + padding). */
    private static boolean inBox(MouseButtonEvent event) {
        int mx = Mth.floor(event.x());
        int my = Mth.floor(event.y());
        // Use the current box layout fields (not the overlay's buttons, which
        // may still hold the previous category's after a tab switch) so the
        // click region always matches what is actually drawn.
        return mx >= boxX && mx < boxX + boxW
                && my >= boxY && my < boxY + boxH;
    }
}
