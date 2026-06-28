package com.alonie.brbe.interfaces;

/**
 * Implemented by screen mixins that support the expanded recipe book feature.
 * Lets {@code HideInventoryForExpandedBook} detect whether to hide the inventory.
 */
public interface ExpandedBookScreen {
    /** Whether the recipe book is currently open in expanded mode. */
    boolean brbe$isExpandedBookOpen();
}
