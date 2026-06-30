package com.alonie.brbe.layout;

import com.alonie.brbe.layout.BookLayout.TabPosition;
import com.alonie.brbe.layout.BookLayout.Zone;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Immutable snapshot of all sub-element positions within a recipe book.
 *
 * <p>Returned by {@link BookLayout#compute} and consumed by renderers,
 * input handlers, and mixins that need to position widgets.</p>
 *
 * <p>All coordinates are absolute screen coordinates.</p>
 */
public final class BookGeometry {

    // -- Book ---------------------------------------------------------
    private final int bookLeft, bookTop, bookWidth, bookHeight;

    // -- Search box ---------------------------------------------------
    private final int searchX, searchY, searchWidth, searchHeight;

    // -- Filter button ------------------------------------------------
    private final int filterX, filterY, filterWidth, filterHeight;

    // -- Settings button ----------------------------------------------
    private final int settingsX, settingsY, settingsSize;

    // -- Recipe grid --------------------------------------------------
    private final int gridX, gridY, gridColumns, gridRows, buttonSize;

    // -- Page arrows --------------------------------------------------
    private final int arrowBackX, arrowForwardX, arrowY;

    // -- Tab strip ----------------------------------------------------
    private final int tabX, tabY, tabSpacing;

    // -- Zones (Level 2) ----------------------------------------------
    private final Map<TabPosition, Zone> tabZones;
    private final Zone gridZone;

    // -- Instant craft button (Level 3) --------------------------------
    private final int instantCraftX, instantCraftY;

    public BookGeometry(int bookLeft, int bookTop, int bookWidth, int bookHeight,
                        int searchX, int searchY, int searchWidth, int searchHeight,
                        int filterX, int filterY, int filterWidth, int filterHeight,
                        int settingsX, int settingsY, int settingsSize,
                        int gridX, int gridY, int gridColumns, int gridRows, int buttonSize,
                        int arrowBackX, int arrowForwardX, int arrowY,
                        int tabX, int tabY, int tabSpacing,
                        Map<TabPosition, Zone> tabZones,
                        int instantCraftX, int instantCraftY,
                        Zone gridZone) {
        this.bookLeft = bookLeft;
        this.bookTop = bookTop;
        this.bookWidth = bookWidth;
        this.bookHeight = bookHeight;
        this.searchX = searchX;
        this.searchY = searchY;
        this.searchWidth = searchWidth;
        this.searchHeight = searchHeight;
        this.filterX = filterX;
        this.filterY = filterY;
        this.filterWidth = filterWidth;
        this.filterHeight = filterHeight;
        this.settingsX = settingsX;
        this.settingsY = settingsY;
        this.settingsSize = settingsSize;
        this.gridX = gridX;
        this.gridY = gridY;
        this.gridColumns = gridColumns;
        this.gridRows = gridRows;
        this.buttonSize = buttonSize;
        this.arrowBackX = arrowBackX;
        this.arrowForwardX = arrowForwardX;
        this.arrowY = arrowY;
        this.tabX = tabX;
        this.tabY = tabY;
        this.tabSpacing = tabSpacing;
        this.tabZones = Collections.unmodifiableMap(new EnumMap<>(tabZones));
        this.instantCraftX = instantCraftX;
        this.instantCraftY = instantCraftY;
        this.gridZone = gridZone;
    }

    // -- Getters: Book --------------------------------------------------------

    public int bookLeft() { return bookLeft; }
    public int bookTop() { return bookTop; }
    public int bookWidth() { return bookWidth; }
    public int bookHeight() { return bookHeight; }

    // -- Getters: Search box --------------------------------------------------

    public int searchX() { return searchX; }
    public int searchY() { return searchY; }
    public int searchWidth() { return searchWidth; }
    public int searchHeight() { return searchHeight; }

    // -- Getters: Filter button -----------------------------------------------

    public int filterX() { return filterX; }
    public int filterY() { return filterY; }
    public int filterWidth() { return filterWidth; }
    public int filterHeight() { return filterHeight; }

    // -- Getters: Settings button ---------------------------------------------

    public int settingsX() { return settingsX; }
    public int settingsY() { return settingsY; }
    public int settingsSize() { return settingsSize; }

    // -- Getters: Recipe grid -------------------------------------------------

    public int gridX() { return gridX; }
    public int gridY() { return gridY; }
    public int gridColumns() { return gridColumns; }
    public int gridRows() { return gridRows; }
    public int buttonSize() { return buttonSize; }
    public int buttonsPerPage() { return gridColumns * gridRows; }

    public int buttonX(int col) { return gridX + (buttonSize + BookLayout.GRID_GAP) * col; }
    public int buttonY(int row) { return gridY + (buttonSize + BookLayout.GRID_GAP) * row; }

    // -- Getters: Page arrows -------------------------------------------------

    public int arrowBackX() { return arrowBackX; }
    public int arrowForwardX() { return arrowForwardX; }
    public int arrowY() { return arrowY; }

    // -- Getters: Tab strip ---------------------------------------------------

    public int tabX() { return tabX; }
    public int tabY() { return tabY; }
    public int tabSpacing() { return tabSpacing; }
    public int tabY(int index) { return tabY + tabSpacing * index; }

    // -- Getters: Zones (Level 2) ---------------------------------------------

    /** All four tab zones (LEFT/RIGHT/TOP/BOTTOM). Some may be zero-size. */
    public Map<TabPosition, Zone> tabZones() { return tabZones; }

    /** Zone for a specific tab position. */
    public Zone tabZone(TabPosition pos) { return tabZones.getOrDefault(pos, Zone.empty()); }

    /** The recipe grid zone (inner area between top bar and bottom controls). */
    public Zone gridZone() { return gridZone; }

    // -- Getters: Instant craft button (Level 3) ------------------------------

    /** Right edge of the instant craft button (aligned with grid right edge). */
    public int instantCraftX() { return instantCraftX; }
    /** Top edge of the instant craft button (aligned with bottom controls). */
    public int instantCraftY() { return instantCraftY; }
}
