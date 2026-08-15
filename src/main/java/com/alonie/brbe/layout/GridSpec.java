package com.alonie.brbe.layout;

/**
 * Immutable specification of the recipe button grid dimensions.
 *
 * <p>Columns are computed dynamically from available width rather than
 * hardcoded to 5.  Rows default to 4 but can be adjusted.</p>
 */
public final class GridSpec {

    private final int columns;
    private final int rows;
    private final int buttonSize;
    private final int totalButtons;

    private GridSpec(int columns, int rows, int buttonSize) {
        this.columns = columns;
        this.rows = rows;
        this.buttonSize = buttonSize;
        this.totalButtons = columns * rows;
    }

    /**
     * Compute grid dimensions from available width.
     *
     * @param availableWidth  horizontal space for the button grid (bookWidth - 2*padding)
     * @param buttonSize      size of each button (typically {@link BookLayout#BUTTON_SIZE})
     * @param maxRows         maximum rows (typically 4)
     */
    public static GridSpec compute(int availableWidth, int buttonSize, int maxRows) {
        int columns = Math.max(1, availableWidth / buttonSize);
        int rows = maxRows;
        return new GridSpec(columns, rows, buttonSize);
    }

    /** Fixed grid for the standard layout (5 columns × 4 rows). */
    public static GridSpec standard() {
        return new GridSpec(5, 4, BookLayout.BUTTON_SIZE);
    }

    public int columns() { return columns; }
    public int rows() { return rows; }
    public int buttonSize() { return buttonSize; }
    public int totalButtons() { return totalButtons; }

    /** Position of button at (col, row) relative to grid origin. */
    public int buttonX(int col) { return buttonSize * col; }
    public int buttonY(int row) { return buttonSize * row; }
}
