package com.alonie.brbe.layout;

/**
 * Immutable snapshot of all sub-element positions within a recipe book.
 *
 * <p>Returned by {@link BookLayout#compute} and consumed by renderers,
 * input handlers, and mixins that need to position widgets.</p>
 *
 * <p>All coordinates are absolute screen coordinates.</p>
 */
public final class BookGeometry {

    private final int bookLeft, bookTop, bookWidth, bookHeight;
    private final int searchX, searchY, searchWidth, searchHeight;
    private final int filterX, filterY, filterWidth, filterHeight;
    private final int settingsX, settingsY, settingsSize;
    private final int gridX, gridY, gridColumns, gridRows, buttonSize;
    private final int arrowBackX, arrowForwardX, arrowY;
    private final int tabX, tabY, tabSpacing;

    public BookGeometry(int bookLeft, int bookTop, int bookWidth, int bookHeight,
                        int searchX, int searchY, int searchWidth, int searchHeight,
                        int filterX, int filterY, int filterWidth, int filterHeight,
                        int settingsX, int settingsY, int settingsSize,
                        int gridX, int gridY, int gridColumns, int gridRows, int buttonSize,
                        int arrowBackX, int arrowForwardX, int arrowY,
                        int tabX, int tabY, int tabSpacing) {
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
    }

    // -- Getters --------------------------------------------------------------

    public int bookLeft() { return bookLeft; }
    public int bookTop() { return bookTop; }
    public int bookWidth() { return bookWidth; }
    public int bookHeight() { return bookHeight; }

    /** Search box bounding box. */
    public int searchX() { return searchX; }
    public int searchY() { return searchY; }
    public int searchWidth() { return searchWidth; }
    public int searchHeight() { return searchHeight; }

    /** Filter toggle button position and size. */
    public int filterX() { return filterX; }
    public int filterY() { return filterY; }
    public int filterWidth() { return filterWidth; }
    public int filterHeight() { return filterHeight; }

    /** Settings gear button position and size. */
    public int settingsX() { return settingsX; }
    public int settingsY() { return settingsY; }
    public int settingsSize() { return settingsSize; }

    /** Recipe button grid origin and specification. */
    public int gridX() { return gridX; }
    public int gridY() { return gridY; }
    public int gridColumns() { return gridColumns; }
    public int gridRows() { return gridRows; }
    public int buttonSize() { return buttonSize; }

    /** Total buttons per page. */
    public int buttonsPerPage() { return gridColumns * gridRows; }

    /** Page navigation arrow positions. */
    public int arrowBackX() { return arrowBackX; }
    public int arrowForwardX() { return arrowForwardX; }
    public int arrowY() { return arrowY; }

    /** Tab button strip origin and vertical spacing. */
    public int tabX() { return tabX; }
    public int tabY() { return tabY; }
    public int tabSpacing() { return tabSpacing; }

    /** Position of the n-th tab button. */
    public int tabY(int index) { return tabY + tabSpacing * index; }

    /** Position of a recipe button at (col, row) in the grid. */
    public int buttonX(int col) { return gridX + buttonSize * col; }
    public int buttonY(int row) { return gridY + buttonSize * row; }
}
