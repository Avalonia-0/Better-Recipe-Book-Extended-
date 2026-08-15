package com.alonie.brbe.util;

import net.minecraft.util.Mth;

public final class AlternativeOverlayLayout {
    private static final int DEFAULT_SMALL_COLUMNS = 4;
    private static final int DEFAULT_LARGE_COLUMNS = 5;
    private static final int SMALL_LAYOUT_LIMIT = 16;
    private static final int MAX_ROWS_BEFORE_EXPANDING = 5;

    private AlternativeOverlayLayout() {
    }

    public static int columnsFor(int recipeCount) {
        if (recipeCount <= 0) {
            return DEFAULT_SMALL_COLUMNS;
        }

        int columns = recipeCount <= SMALL_LAYOUT_LIMIT ? DEFAULT_SMALL_COLUMNS : DEFAULT_LARGE_COLUMNS;
        if (Mth.ceil((float) recipeCount / (float) columns) <= MAX_ROWS_BEFORE_EXPANDING) {
            return columns;
        }

        return Mth.ceil((float) recipeCount / (float) MAX_ROWS_BEFORE_EXPANDING);
    }
}
