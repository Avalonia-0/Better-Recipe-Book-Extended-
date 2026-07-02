package com.alonie.brbe.layout;

/**
 * Dynamic grid specification computed from available space.
 *
 * <p>Columns: {@code floor((zoneWidth + gap) / (buttonSize + gap))},
 * clamped to ≥1. Rows: {@code max(1, zoneHeight / (buttonSize + gap))}.</p>
 *
 * <p>The grid is centered within the available zone by the caller
 * ({@link BookLayout#compute}).</p>
 */
public final class GridSpec {

    private final int columns;
    private final int rows;
    private final int buttonSize;
    private final int gap;
    private final int gridWidth;
    private final int gridHeight;

    private GridSpec(int columns, int rows, int buttonSize, int gap) {
        this.columns = columns;
        this.rows = rows;
        this.buttonSize = buttonSize;
        this.gap = gap;
        int step = buttonSize + gap;
        this.gridWidth = columns * step - gap;
        this.gridHeight = rows * step - gap;
    }

    /** Cached standard-mode spec: 5 columns × 4 rows, 25px buttons, 2px gap. */
    private static final GridSpec STANDARD = new GridSpec(5, 4, BookLayout.BUTTON_SIZE, BookLayout.GRID_GAP);

    /**
     * Compute grid spec from available zone dimensions.
     *
     * @param zoneWidth   available horizontal space
     * @param zoneHeight  available vertical space
     * @param buttonSize  recipe button size (square)
     * @param gap         inter-button gap
     */
    public static GridSpec compute(int zoneWidth, int zoneHeight, int buttonSize, int gap) {
        int step = buttonSize + gap;
        int columns = (zoneWidth + gap) / step;
        if (columns < 1) columns = 1;
        int rows = Math.max(1, zoneHeight / step);
        return new GridSpec(columns, rows, buttonSize, gap);
    }

    /** Standard spec for backward compatibility (5×4, 25px, 2px gap). */
    public static GridSpec standard() { return STANDARD; }

    // -- Getters --------------------------------------------------------------

    public int columns() { return columns; }
    public int rows() { return rows; }
    public int buttonSize() { return buttonSize; }
    public int gap() { return gap; }
    public int gridWidth() { return gridWidth; }
    public int gridHeight() { return gridHeight; }
    public int totalButtons() { return columns * rows; }
    public int step() { return buttonSize + gap; }

    /** Position of button at (col, row) relative to grid origin. */
    public int buttonX(int col) { return step() * col; }
    public int buttonY(int row) { return step() * row; }
}
