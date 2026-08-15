package com.alonie.brbe.util;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.cache.RecipeViewerIndex;
import com.alonie.brbe.mixins.accessors.ClientRecipeBookAccessor;
import com.alonie.brbe.util.ModNameUtil;
import com.alonie.brbe.util.IncompatibleCraftingUtil;
import com.alonie.brbe.mixins.accessors.AbstractContainerScreenAccessor;
import com.alonie.brbe.mixins.accessors.AbstractRecipeBookScreenAccessor;
import com.alonie.brbe.mixins.accessors.ClientRecipeBookAccessor;
import com.alonie.brbe.mixins.accessors.GhostSlotsAccessor;
import com.alonie.brbe.mixins.accessors.OverlayRecipeButtonAccessor;
import com.alonie.brbe.mixins.accessors.OverlayRecipeComponentAccessor;
import com.alonie.brbe.mixins.accessors.RecipeBookComponentAccessor;
import com.alonie.brbe.mixins.accessors.RecipeBookPageAccessor;
import com.alonie.brbe.recipeviewer.FuelRecipeCategory;
import com.alonie.brbe.recipeviewer.RecipeViewerCategories;
import com.alonie.brbe.recipeviewer.RecipeViewerCategory;
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
import net.minecraft.world.item.crafting.display.SlotDisplayContext;

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
            Identifier.fromNamespaceAndPath("brbe", "textures/rbip/recipe_book_buttons.png");
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

    public static boolean isActive() {
        return RecipeViewerIndex.isViewerActive();
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
            if (isActive()) {
                close();
                return true;
            }
            return false;
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
            if (screen instanceof AbstractRecipeBookScreen<?> rbs) {
                RecipeBookComponent<?> book = ((AbstractRecipeBookScreenAccessor) rbs)
                        .brbe$getRecipeBookComponent();
                RecipeDisplayId id = overlay.getLastRecipeClicked();
                RecipeCollection collection = overlay.getRecipeCollection();
                if (book != null && id != null && collection != null
                        && recipeFitsScreen(id, screen)) {
                    try {
                        ((RecipeBookComponentAccessor) book)
                                .tryPlaceRecipeInvoker(collection, id, event.hasShiftDown());
                        // The vanilla ghost-overlay optimisation (PartialGhostOverlayUtil,
                        // fed from lastRecipe/lastRecipeCollection in extractGhostRecipe)
                        // must see the viewer's placed recipe too, otherwise every ghost
                        // slot keeps its red mask.
                        ((RecipeBookComponentAccessor) book).setLastRecipe(id);
                        ((RecipeBookComponentAccessor) book).setLastRecipeCollection(collection);
                    } catch (Exception e) {
                        // Non-fatal: the placement already fired or is invalid.
                    }
                }
            }
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
        return false;
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
        return ((OverlayRecipeComponentAccessor) overlay).getX();
    }

    private static int boxTop() {
        return ((OverlayRecipeComponentAccessor) overlay).getY();
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
        // The fuel category renders a standalone item grid (fuel is not a
        // recipe, so there are no OverlayRecipeComponent buttons).
        if (isFuelMode()) {
            drawCategoryTabs(gui, mouseX, mouseY, true);
            drawFuelGrid(gui, mouseX, mouseY);
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
        // Tabs hang below the box; a zoomed button overlapping them must paint
        // on top, and the tooltip must not be covered by the tabs — so draw the
        // tabs first, then the hovered button, then the tooltip (top-most).
        // The viewer's tooltip is rendered here (not by the extractRenderState
        // RETURN hook, which skips the viewer instance) so it stays on top.
        OverlayRecipeComponentAccessor acc = (OverlayRecipeComponentAccessor) overlay;
        for (AbstractWidget w : acc.getRecipeButtons()) {
            if (w.isHoveredOrFocused()) {
                w.extractRenderState(gui, mouseX, mouseY, delta);
                break;
            }
        }
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
        int columns = AlternativeOverlayLayout.columnsFor(fuelGridItems.size());
        fuelHoverStack = null;
        for (int i = 0; i < fuelGridItems.size(); i++) {
            int gx = boxX + 4 + (i % columns) * 25;
            int gy = boxY + 5 + (i / columns) * 25;
            boolean hovered = inside(mouseX, mouseY, gx, gy, 24, 24);
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
        return boxX + 4 + i * TAB_WIDTH;
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
            if (inside(mouseX, mouseY, x, tabY, TAB_WIDTH, TAB_HEIGHT)) {
                gui.requestCursor(com.mojang.blaze3d.platform.cursor.CursorTypes.POINTING_HAND);
                drawTabTooltip(gui, cat, mouseX, mouseY, tabPageCount);
            }
        }
    }

    /** Tooltip for a category tab: the category name, and — when the tab strip
     *  is folded — the current page ("n/m") on a separate line below it. */
    private static void drawTabTooltip(GuiGraphicsExtractor gui, RecipeViewerCategory cat,
                                       int mouseX, int mouseY, int tabPageCount) {
        Minecraft mc = Minecraft.getInstance();
        List<Component> lines = new ArrayList<>();
        lines.add(cat.name());
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
    public static void renderTooltip(GuiGraphicsExtractor gui, int mouseX, int mouseY) {
        if (!isActive()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.level == null) return;
        // Fuel category: a single tooltip (item name + burn-time rows), no
        // shift variation, positioned off the hovered fuel cell.
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
            // No hover zoom in the fuel category, so use the vanilla default
            // position (no button avoidance / push-away).
            gui.tooltip(mc.font, components, mouseX, mouseY,
                    net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner.INSTANCE,
                    fuelHoverStack.get(net.minecraft.core.component.DataComponents.TOOLTIP_STYLE));
            return;
        }
        for (AbstractWidget widget : ((OverlayRecipeComponentAccessor) overlay).getRecipeButtons()) {
            if (!(widget instanceof OverlayRecipeButtonAccessor oba) || !widget.isHoveredOrFocused()) continue;
            RecipeDisplayId id = oba.brbe$getRecipe();
            RecipeDisplayEntry entry = entryFor(id);
            if (entry == null) return;

            ItemStack output = resolveOutput(entry, mc);
            if (output == null || output.isEmpty()) return;

            List<Component> lines = buildTooltipLines(mc, output, id, entry);
            Identifier style = output.get(net.minecraft.core.component.DataComponents.TOOLTIP_STYLE);
            List<net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent> components =
                    new ArrayList<>(lines.size());
            for (Component line : lines) {
                components.add(net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent
                        .create(line.getVisualOrderText()));
            }
            if (isFurnaceMode() && RecipeViewerIndex.asFurnace(entry) != null) {
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
                    // In the compact (non-Shift) furnace view the white dot
                    // below the time line already adds vertical space, so drop
                    // the blank separator line here.
                    boolean dotBelowTime = isFurnaceMode() && !ClientCompat.isShiftDown()
                            && (menuIs(FurnaceMenu.class) || menuIs(BlastFurnaceMenu.class)
                                || menuIs(SmokerMenu.class));
                    if (!dotBelowTime) {
                        components.add(net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent
                                .create(Component.empty().getVisualOrderText()));
                    }
                    components.add(net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent
                            .create(modName.getVisualOrderText()));
                }
            }
            // With Shift the hovered button enlarges 4x about its centre; use
            // the enlarged footprint (plus a larger pad) so the tooltip clears
            // it.  Without Shift keep the original 24x24 footprint + default pad
            // so the tooltip distance is unchanged.
            boolean shift = ClientCompat.isShiftDown();
            int bx;
            int by;
            int bw;
            int bh;
            int pad;
            if (shift) {
                float cx = widget.getX() + widget.getWidth() / 2f;
                float cy = widget.getY() + widget.getHeight() / 2f;
                int scaledW = Math.round(widget.getWidth() * 4f);
                int scaledH = Math.round(widget.getHeight() * 4f);
                bx = Math.round(cx - scaledW / 2f);
                by = Math.round(cy - scaledH / 2f);
                bw = scaledW;
                bh = scaledH;
                pad = 0;
            } else {
                bx = widget.getX();
                by = widget.getY();
                bw = widget.getWidth();
                bh = widget.getHeight();
                pad = 14;
            }
            gui.tooltip(mc.font, components, mouseX, mouseY,
                    new AvoidButtonTooltipPositioner(bx, by, bw, bh, pad),
                    style);
            return;
        }
    }

    /** Resolve the recipe's primary result, tolerating context-sensitive displays. */
    private static ItemStack resolveOutput(RecipeDisplayEntry entry, Minecraft mc) {
        try {
            List<ItemStack> results = entry.resultItems(SlotDisplayContext.fromLevel(mc.level));
            if (!results.isEmpty()) return results.get(0);
        } catch (Exception e) {
            // fall through
        }
        try {
            List<ItemStack> results = entry.resultItems(null);
            if (!results.isEmpty()) return results.get(0);
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
                lines.add(Component.translatable("brbe.gui.environmentIncompatible")
                        .withStyle(net.minecraft.ChatFormatting.RED));
            }
        }

        return lines;
    }

    /** XP + per-station cook-time rows for a furnace recipe tooltip; each
     *  station row carries its workstation item icons.  With Shift the cook
     *  time expands to one labelled row per station; otherwise it is a single
     *  compact row. */
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
        if (ClientCompat.isShiftDown()) {
            // Expanded: one labelled row per station subcategory (each its own
            // component so the rows break lines), colour follows the compact
            // variant, "•" marks the station the open screen matches.  Each row
            // carries that subcategory's workstation icons — vanilla and any mod
            // workstations from the external registry.
            for (int i = 0; i < ticks.length; i++) {
                if (ticks[i] <= 0) continue;
                Component line = stationTimeLine(stationLabel(i), cookSeconds(ticks[i]),
                        stationStyle(i), stationMatches(i, furnaceStn, blastStn, smokerStn));
                List<ItemStack> icons = RecipeViewerIndex.workstationsIconsForPrefix(stationCategoryPrefix(i));
                components.add(new StationLineTooltipComponent(List.of(
                        new StationLineTooltipComponent.Segment(line.getVisualOrderText(), icons, false))));
            }
        } else {
            // Compact: one row of cook times (workstation icons only appear in
            // the Shift view); a station with no recipe for this content is
            // omitted (with its separating space).
            List<StationLineTooltipComponent.Segment> segments = new ArrayList<>();
            for (int i = 0; i < ticks.length; i++) {
                if (ticks[i] <= 0) continue;
                Component t = Component.literal(cookSeconds(ticks[i])).withStyle(stationStyle(i));
                // The white dot marking the open screen's station sits below
                // the time (centred) in this compact view.
                segments.add(new StationLineTooltipComponent.Segment(t.getVisualOrderText(), List.of(),
                        stationMatches(i, furnaceStn, blastStn, smokerStn)));
            }
            if (!segments.isEmpty()) {
                components.add(new StationLineTooltipComponent(segments));
            }
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
        List<net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent> components =
                new ArrayList<>();
        components.add(net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent
                .create(Component.empty().getVisualOrderText()));
        List<ItemStack> icons = currentCategory.stationIconsFor(entry);
        if (icons.isEmpty()) {
            icons = List.of(currentCategory.icon());
        }
        components.add(new StationLineTooltipComponent(List.of(
                new StationLineTooltipComponent.Segment(Component.empty().getVisualOrderText(), icons, false))));
        return components;
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
        String unit = Component.translatable("brbe.cooktime.unit.items").getString();
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

    /** Positions the tooltip away from the hovered (possibly enlarged) button. */
    private static final class AvoidButtonTooltipPositioner
            implements net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner {
        private final int btnX;
        private final int btnY;
        private final int btnW;
        private final int btnH;
        private final int pad;

        AvoidButtonTooltipPositioner(int btnX, int btnY, int btnW, int btnH, int pad) {
            this.btnX = btnX;
            this.btnY = btnY;
            this.btnW = btnW;
            this.btnH = btnH;
            this.pad = pad;
        }

        @Override
        public org.joml.Vector2ic positionTooltip(int screenWidth, int screenHeight,
                                                  int mouseX, int mouseY,
                                                  int tooltipWidth, int tooltipHeight) {
            int x = Math.max(0, Math.min(mouseX + 12, screenWidth - tooltipWidth));
            int y = Math.max(0, Math.min(mouseY - 12, screenHeight - tooltipHeight));

            int padLeft = btnX - pad;
            int padRight = btnX + btnW + pad;
            int padTop = btnY - pad;
            int padBot = btnY + btnH + pad;

            boolean overlaps = x < padRight && x + tooltipWidth > padLeft
                    && y < padBot && y + tooltipHeight > padTop;
            if (overlaps) {
                if (padBot + tooltipHeight <= screenHeight) {
                    y = padBot + 2;
                } else {
                    y = Math.max(0, padTop - tooltipHeight - 2);
                }
                x = Math.max(0, Math.min(mouseX + 12, screenWidth - tooltipWidth));
            }
            return new org.joml.Vector2i(x, y);
        }
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
        if ((prevActive && inside(mouseX, mouseY, bx, btnY, PAGE_BTN_WIDTH, PAGE_BTN_HEIGHT))
                || (nextActive && inside(mouseX, mouseY, bx + 15, btnY, PAGE_BTN_WIDTH, PAGE_BTN_HEIGHT))) {
            gui.setTooltipForNextFrame(mc.font, Component.literal((viewerPage + 1) + "/" + viewerPageCount),
                    mouseX, mouseY);
        }
    }

    private static void drawPageButton(GuiGraphicsExtractor gui, int x, int y, boolean next,
                                      boolean active, int mouseX, int mouseY) {
        int u = next ? 14 : 0;
        if (active && inside(mouseX, mouseY, x, y, PAGE_BTN_WIDTH, PAGE_BTN_HEIGHT)) {
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
        ItemStack target = captureTarget(screen);
        if (target.isEmpty()) return false;

        // Smart default category for this item, falling back to null (no open)
        // when no category has results.
        queryTarget = target;
        queryUsage = viewUsage;
        currentCategory = RecipeViewerCategories.defaultFor(target, viewUsage, screen.getMenu());
        if (currentCategory == null) return false;
        List<RecipeDisplayEntry> hits = null;
        if (currentCategory.isFuelCategory()) {
            computeFuelBoxSize();
        } else {
            hits = currentCategory.query(target, viewUsage);
            if (hits.isEmpty()) return false;
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
            rebuildFuel();
        } else {
            rebuildWithHits(hits);
        }
        repaginateToSelected();
        RecipeViewerIndex.setViewerActive(true);
        RecipeViewerIndex.setViewerOpenedFromBook(anchorBookButton != null);
        return true;
    }

    /** Rebuild the overlay contents from a (possibly new category's) query hits,
     *  reusing the fixed box layout. */
    private static void rebuildWithHits(List<RecipeDisplayEntry> hits) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || hits.isEmpty()) return;

        StackedItemContents stacked = new StackedItemContents();
        mc.player.getInventory().fillStackedContents(stacked);
        RecipeCollection collection = RecipeViewerIndex.toCollection(hits, stacked);

        // Mark partially-craftable recipes (always against the inventory, so
        // the viewer is unaffected by the "only when carrying" toggle) and
        // snapshot the marks against the tagger's generation advances.
        if (ownerScreen != null) {
            PartialCraftingUtil.prepareForViewer(collection, ownerScreen.getMenu().slots,
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
        int total = hits.size();
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

    /** Compute boxW/boxH for the fuel grid (no paging — the fuel list is short). */
    private static void computeFuelBoxSize() {
        int total = fuelGridItems.size();
        viewerPageCount = 1;
        int columns = AlternativeOverlayLayout.columnsFor(total);
        int rows = (total + columns - 1) / columns;
        boxW = Math.min(total, columns) * 25 + 8;
        boxH = rows * 25 + 8;
        ensureTabWidth();
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

    /** Lay out the fuel cell for the queried item alone (fuel is an ingredient
     *  role: a usage query shows only the queried fuel's burn time, mirroring
     *  JEI's per-fuel FuelingRecipe).  Keeps the tab strip anchored. */
    private static void rebuildFuel() {
        fuelGridItems = List.of(queryTarget);
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
            currentCategory = category;
            computeFuelBoxSize();
            boxY = Math.max(0, bottomAnchor - boxH);
            rebuildFuel();
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
        mc.player.getInventory().fillStackedContents(stacked);
        RecipeCollection subset = RecipeViewerIndex.toCollection(pageEntries, stacked);
        PartialCraftingUtil.prepareForViewer(subset, screen.getMenu().slots,
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
    private static ItemStack captureTarget(AbstractContainerScreen<?> screen) {
        // Hovering a BRBE viewer overlay recipe button: query its result item.
        if (overlay.isVisible()) {
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
        return ItemStack.EMPTY;
    }

    /**
     * Whether the clicked recipe may be placed into the current station.
     * Crafting recipes go into crafting-table menus; smelting recipes go into
     * furnace-type menus (furnace / blast furnace / smoker).  A recipe must not
     * fill items or ghost previews into a wrong-station menu.
     */
    /** The entry for {@code id}: the engine's registry first (covers synthetic
     *  entries from the companion mod), then the recipe book's known set. */
    private static RecipeDisplayEntry entryFor(RecipeDisplayId id) {
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
