package com.alonie.brbe.layout;

import com.alonie.brbe.util.BRBTextures;
import com.alonie.brbe.util.ClientCompat;

import java.util.EnumMap;
import java.util.Map;

/**
 * Constraint-driven layout engine for recipe books.
 *
 * <p>Uses a <strong>three-level element hierarchy</strong> where each element's
 * position is derived from its container zone or adjacent siblings — no
 * isolated magic numbers.</p>
 *
 * <pre>
 * Level 1: BookRect          — the recipe book background
 * Level 2: TabZones (4 sides) + InnerRect + TopBar + BottomBar + GridZone
 * Level 3: SearchBox, FilterButton, SettingsBtn, PageArrows, InstantCraftBtn
 * </pre>
 */
public final class BookLayout {

    // -- Fundamental constants (visual asset sizes) ---------------------------

    /** Vanilla recipe book texture dimensions (sprite sheet). */
    public static final int TEXTURE_WIDTH = 147;
    public static final int TEXTURE_HEIGHT = 166;

    /** Recipe button size (square). */
    public static final int BUTTON_SIZE = 25;

    /** Gap between recipe buttons in the grid. */
    public static final int GRID_GAP = 2;

    /** Grid padding from book inner edge. */
    public static final int GRID_PAD = 11;

    /** Tab button strip width. */
    public static final int TAB_WIDTH = 30;
    /** Vertical spacing between tab buttons. */
    public static final int TAB_SPACING = 27;
    /** Tab button offset from book top edge. */
    public static final int TAB_TOP_PAD = 3;

    /** Book X-offsets for centered / standard mode. */
    public static final int X_OFFSET_CENTERED = 162;
    public static final int X_OFFSET_STANDARD = 86;

    /** Filter button natural size. */
    public static final int FILTER_WIDTH = 26;
    public static final int FILTER_HEIGHT = 16;

    /** Settings button (square). */
    public static final int SETTINGS_SIZE = 18;

    /** Settings button size — 20 when the unique-dark compat pack provides 20×20 sprites. */
    public static int settingsSize() {
        return ClientCompat.hasSpriteResource(BRBTextures.RECIPE_BOOK_BUTTON_SLOT_PARTIAL_SPRITE) ? 20 : SETTINGS_SIZE;
    }

    /** Settings button Y offset — 1px higher when the unique-dark compat pack is active. */
    public static int settingsY() {
        return ClientCompat.hasSpriteResource(BRBTextures.RECIPE_BOOK_BUTTON_SLOT_PARTIAL_SPRITE) ? SETTINGS_Y_OFFSET - 1 : SETTINGS_Y_OFFSET;
    }

    /** Page arrow button width. */
    public static final int ARROW_WIDTH = 12;

    /** Expanded book 3-slice background caps. */
    public static final int BG_LEFT_CAP = 32;
    public static final int BG_RIGHT_CAP = 12;
    public static final int BG_BODY = 103;
    public static final int BG_TEX_SIZE = 256;

    /** Pin sprite offset from button corner. */
    public static final int PIN_SPRITE_OFFSET = 4;
    public static final int PIN_SPRITE_SIZE = 32;

    // -- Backward-compatible aliases (deprecated: use the names above) --------

    /** @deprecated Use {@link #GRID_PAD} */
    @Deprecated public static final int GRID_LEFT_PADDING = GRID_PAD;
    /** @deprecated Use {@link #GRID_PAD} (gridY is now computed via GridSpec) */
    @Deprecated public static final int GRID_TOP_PADDING = 31;

    /** @deprecated Use constraint model: search box left = bookLeft + 25 */
    @Deprecated public static final int SEARCH_X_OFFSET = 25;
    /** @deprecated Use constraint model: search box top = topBar.top + 1 */
    @Deprecated public static final int SEARCH_Y_OFFSET = 13;
    /** @deprecated Use constraint model: search width = filterLeft - searchLeft */
    @Deprecated public static final int SEARCH_WIDTH = 81;

    /** @deprecated Use constraint model: filter right-aligned in topBar */
    @Deprecated public static final int FILTER_X_OFFSET = 110;
    /** @deprecated Use constraint model: filter top = topBar.top */
    @Deprecated public static final int FILTER_Y_OFFSET = 12;

    /** @deprecated Use constraint model: settings left-aligned in bottomControls */
    @Deprecated public static final int SETTINGS_X_OFFSET = GRID_PAD;
    /** @deprecated Use constraint model: settings top = bottomControls.top */
    @Deprecated public static final int SETTINGS_Y_OFFSET = 137;

    /** @deprecated Use constraint model */
    @Deprecated public static final int ARROW_Y_OFFSET = 137;
    /** @deprecated Use constraint model */
    @Deprecated public static final int ARROW_FORWARD_X = 93;
    /** @deprecated Use constraint model */
    @Deprecated public static final int ARROW_BACK_X = 38;

    /** @deprecated Use {@link #TAB_SPACING} */
    @Deprecated public static final int TAB_BUTTON_SPACING = TAB_SPACING;
    /** @deprecated Use {@link #TAB_TOP_PAD} */
    @Deprecated public static final int TAB_TOP_OFFSET = TAB_TOP_PAD;
    /** @deprecated Use {@link #TAB_WIDTH} */
    @Deprecated public static final int TAB_BUTTON_WIDTH = TAB_WIDTH;

    // -- Zone -----------------------------------------------------------------

    /** A rectangular zone with pre-computed edge helpers. */
    public static final class Zone {
        public final int left, top, width, height;

        public Zone(int left, int top, int width, int height) {
            this.left = left;
            this.top = top;
            this.width = width;
            this.height = height;
        }

        public int right()  { return left + width; }
        public int bottom() { return top + height; }
        public int centerX() { return left + width / 2; }
        public int centerY() { return top + height / 2; }

        /** Create a zero-size zone at the given anchor edge. */
        public static Zone empty() { return new Zone(0, 0, 0, 0); }
    }

    /** Which side of the book a tab strip is attached to. */
    public enum TabPosition { LEFT, RIGHT, TOP, BOTTOM }

    // -- Input ----------------------------------------------------------------

    public record Rect(int x, int y, int width, int height) {
        public static Rect of(int x, int y, int width, int height) {
            return new Rect(x, y, width, height);
        }
    }

    // -- Computation ----------------------------------------------------------

    /**
     * Compute full geometry for a recipe book.
     *
     * @param available    screen space for the book
     * @param keepCentered whether centered mode is active
     * @param expanded     whether expanded (wide) mode is active
     */
    public BookGeometry compute(Rect available, boolean keepCentered, boolean expanded) {
        // ── Level 1: Book container ──────────────────────────────
        int bookWidth = expanded ? computeExpandedWidth(available) : TEXTURE_WIDTH;
        int bookHeight = TEXTURE_HEIGHT;
        int xOffset = keepCentered ? X_OFFSET_CENTERED : X_OFFSET_STANDARD;
        int bookLeft = available.x() + xOffset;
        int bookTop = available.y() + (available.height() - bookHeight) / 2;

        Zone bookZone = new Zone(bookLeft, bookTop, bookWidth, bookHeight);

        // ── Level 2a: Four tab zones (attach to book edges) ─────
        Map<TabPosition, Zone> tabZones = new EnumMap<>(TabPosition.class);

        // LEFT tabs — recipe type tabs (outside book, left side)
        tabZones.put(TabPosition.LEFT, new Zone(
                bookLeft - TAB_WIDTH, bookTop + TAB_TOP_PAD,
                TAB_WIDTH, bookHeight - TAB_TOP_PAD));

        // RIGHT tabs — reserved, zero-width for now
        tabZones.put(TabPosition.RIGHT, new Zone(
                bookLeft + bookWidth, bookTop + TAB_TOP_PAD,
                0, bookHeight - TAB_TOP_PAD));

        // TOP tabs — RBIP creative tabs (outside book, above)
        tabZones.put(TabPosition.TOP, new Zone(
                bookLeft, bookTop - TAB_WIDTH,
                bookWidth, TAB_WIDTH));

        // BOTTOM tabs — RBIP creative tabs (outside book, below)
        tabZones.put(TabPosition.BOTTOM, new Zone(
                bookLeft, bookTop + bookHeight,
                bookWidth, TAB_WIDTH));

        // ── Level 2b: Inner zones within the book ────────────────
        int innerLeft = bookLeft + GRID_PAD;
        int innerRight = bookLeft + bookWidth - GRID_PAD;

        // Top bar: search + filter row
        int topBarTop = bookTop + 12;
        int topBarHeight = FILTER_HEIGHT;
        Zone topBar = new Zone(innerLeft, topBarTop,
                innerRight - innerLeft, topBarHeight);

        // Bottom controls: settings + page arrows
        int bottomControlsTop = bookTop + 137;
        int bottomControlsHeight = SETTINGS_SIZE; // max of settings, arrows
        Zone bottomControls = new Zone(innerLeft, bottomControlsTop,
                innerRight - innerLeft, bottomControlsHeight);

        // Grid zone: fills the space between top bar and bottom controls
        int gridTop = bookTop + 31; // topBar.bottom + natural gap
        Zone gridZone = new Zone(innerLeft, gridTop,
                innerRight - innerLeft, bottomControlsTop - gridTop);

        // ── Level 3: Individual controls ─────────────────────────

        // Search box: fills from grid-left to filter-left
        int searchLeft = bookLeft + 25;  // indented past tab highlight overlap
        int searchRight = bookLeft + 110 - 2; // filterLeft - gap
        int searchWidth = searchRight - searchLeft;
        int searchHeight = FILTER_HEIGHT;

        // Filter button: right-aligned in top bar
        int filterLeft = bookLeft + 110;
        int filterTop = bookTop + 12;

        // Settings button: left-aligned in bottom controls
        int settingsLeft = innerLeft;
        int settingsTop = bottomControlsTop;

        // Page arrows: centered in the space between settings-right and book-right
        int forwardArrowLeft = bookLeft + 93;
        int backArrowLeft = bookLeft + 38;
        int arrowY = bottomControlsTop;

        // Instant craft button: right-aligned with grid, bottom-aligned with bottom controls
        int instantCraftRight = gridZone.right();
        int instantCraftTop = bottomControlsTop;

        // ── Grid specification ───────────────────────────────────
        GridSpec gridSpec = GridSpec.compute(
                gridZone.width, gridZone.height, BUTTON_SIZE, GRID_GAP);
        int gridX = gridZone.left + (gridZone.width - gridSpec.gridWidth()) / 2;
        int gridY = gridZone.top + (gridZone.height - gridSpec.gridHeight()) / 2;

        // ── Assemble ─────────────────────────────────────────────
        return new BookGeometry(
                bookLeft, bookTop, bookWidth, bookHeight,
                searchLeft, topBar.top + 1, searchWidth, searchHeight,  // y=13
                filterLeft, filterTop, FILTER_WIDTH, FILTER_HEIGHT,
                settingsLeft, settingsTop, SETTINGS_SIZE,
                gridX, gridY, gridSpec.columns(), gridSpec.rows(), BUTTON_SIZE,
                backArrowLeft, forwardArrowLeft, arrowY,
                tabZones.get(TabPosition.LEFT).left, tabZones.get(TabPosition.LEFT).top, TAB_SPACING,
                tabZones,
                instantCraftRight, instantCraftTop,
                gridZone
        );
    }

    private int computeExpandedWidth(Rect available) {
        int baseWidth = available.width() - X_OFFSET_STANDARD - 20;
        return Math.max(TEXTURE_WIDTH, baseWidth);
    }
}
