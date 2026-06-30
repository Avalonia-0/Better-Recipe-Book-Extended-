package com.alonie.brbe.layout;

import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;

/**
 * Constraint-driven layout engine for recipe books.
 *
 * <p>Replaces the 30+ hardcoded magic numbers ({@code 147}, {@code 166},
 * {@code 137}, {@code 86}, {@code 162}, {@code 25}, etc.) scattered across
 * 7+ files with a single source of truth.</p>
 *
 * <p>All coordinates are computed from the available screen space and
 * configuration, supporting dynamic column counts and future layout modes
 * (expanded book, centered mode) without code changes.</p>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 *   BookGeometry geo = BookLayout.compute(availableRect, config);
 *   // geo.searchBox(), geo.grid(), geo.tabs(), geo.filterButton(), ...
 * }</pre>
 */
public final class BookLayout {

    // -- Layout constants (named, not scattered) ------------------------------

    /** Vanilla recipe book texture width (sprite sheet). */
    public static final int TEXTURE_WIDTH = 147;
    /** Vanilla recipe book texture height (sprite sheet). */
    public static final int TEXTURE_HEIGHT = 166;

    /** Recipe button size (square). */
    public static final int BUTTON_SIZE = 25;

    /** Grid padding from book left edge. */
    public static final int GRID_LEFT_PADDING = 11;
    /** Grid padding from book top edge. */
    public static final int GRID_TOP_PADDING = 31;

    /** Tab button width (vertical strip along left edge). */
    public static final int TAB_BUTTON_WIDTH = 30;
    /** Vertical spacing between tab buttons. */
    public static final int TAB_BUTTON_SPACING = 27;
    /** Tab button offset from book top. */
    public static final int TAB_TOP_OFFSET = 3;

    /** Search box offset from book corner. */
    public static final int SEARCH_X_OFFSET = 25;
    public static final int SEARCH_Y_OFFSET = 13;
    /** Search box width in standard mode. */
    public static final int SEARCH_WIDTH = 81;

    /** Filter button offset. */
    public static final int FILTER_X_OFFSET = 110;
    public static final int FILTER_Y_OFFSET = 12;
    public static final int FILTER_WIDTH = 26;
    public static final int FILTER_HEIGHT = 16;

    /** Settings button offset. */
    public static final int SETTINGS_X_OFFSET = 11;
    public static final int SETTINGS_Y_OFFSET = 137;
    public static final int SETTINGS_SIZE = 18;

    /** Page arrow offsets. */
    public static final int ARROW_Y_OFFSET = 137;
    public static final int ARROW_FORWARD_X = 93;
    public static final int ARROW_BACK_X = 38;

    /** X-offsets for centered vs standard mode. */
    public static final int X_OFFSET_CENTERED = 162;
    public static final int X_OFFSET_STANDARD = 86;

    /** Expanded book 3-slice background caps. */
    public static final int BG_LEFT_CAP = 32;
    public static final int BG_RIGHT_CAP = 12;
    public static final int BG_BODY = 103;
    public static final int BG_TEX_SIZE = 256;

    /** Pin sprite offset (rendered outside the button bounds). */
    public static final int PIN_SPRITE_OFFSET = 4;
    public static final int PIN_SPRITE_SIZE = 32;

    // -- Computation ----------------------------------------------------------

    /**
     * Compute the full geometry for a recipe book given the available screen
     * rectangle and current configuration.
     *
     * @param available  the rectangle representing screen space available
     *                   for the recipe book (usually depends on container GUI
     *                   position and screen dimensions)
     * @param keepCentered  whether centered mode is enabled
     * @param expanded   whether expanded (wide) mode is enabled
     * @return fully computed geometry for all sub-elements
     */
    public BookGeometry compute(Rect available, boolean keepCentered, boolean expanded) {
        int bookWidth = expanded ? computeExpandedWidth(available) : TEXTURE_WIDTH;
        int bookHeight = TEXTURE_HEIGHT;

        int xOffset = keepCentered ? X_OFFSET_CENTERED : X_OFFSET_STANDARD;
        int bookLeft = available.x() + xOffset;
        int bookTop = available.y() + (available.height() - bookHeight) / 2;

        // Dynamic grid computation
        int gridColumns = (bookWidth - GRID_LEFT_PADDING * 2) / BUTTON_SIZE;
        int gridRows = 4; // default row count; could also be dynamic

        return new BookGeometry(
                bookLeft, bookTop, bookWidth, bookHeight,
                // search box
                bookLeft + SEARCH_X_OFFSET, bookTop + SEARCH_Y_OFFSET,
                expanded ? bookWidth - 140 : SEARCH_WIDTH, 12,
                // filter button
                bookLeft + (expanded ? bookWidth - 37 : FILTER_X_OFFSET),
                bookTop + FILTER_Y_OFFSET,
                FILTER_WIDTH, FILTER_HEIGHT,
                // settings button (square)
                bookLeft + SETTINGS_X_OFFSET, bookTop + SETTINGS_Y_OFFSET,
                SETTINGS_SIZE,
                // grid
                bookLeft + GRID_LEFT_PADDING, bookTop + GRID_TOP_PADDING,
                gridColumns, gridRows, BUTTON_SIZE,
                // page arrows (both share same Y)
                bookLeft + ARROW_BACK_X, bookLeft + ARROW_FORWARD_X,
                bookTop + ARROW_Y_OFFSET,
                // tabs
                bookLeft - TAB_BUTTON_WIDTH, bookTop + TAB_TOP_OFFSET,
                TAB_BUTTON_SPACING
        );
    }

    private int computeExpandedWidth(Rect available) {
        // Expanded mode: book stretches to fill more horizontal space.
        // The exact calculation mirrors the existing ExpandedBookMixin logic.
        int baseWidth = available.width() - X_OFFSET_STANDARD - 20;
        return Math.max(TEXTURE_WIDTH, baseWidth);
    }

    // -- Simple rectangle -----------------------------------------------------

    public record Rect(int x, int y, int width, int height) {
        public static Rect of(int x, int y, int width, int height) {
            return new Rect(x, y, width, height);
        }
    }
}
