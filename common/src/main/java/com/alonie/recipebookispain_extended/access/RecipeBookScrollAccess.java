package com.alonie.recipebookispain_extended.access;

public interface RecipeBookScrollAccess {
    boolean rbip$scrollPages(double mouseX, double mouseY, double verticalAmount);

    /** Current creative-tab page index (0-based). */
    int rbip$getPage();

    /** Jump to a tab page, re-laying out the visible tabs (clamped). */
    void rbip$setPage(int page);
}
