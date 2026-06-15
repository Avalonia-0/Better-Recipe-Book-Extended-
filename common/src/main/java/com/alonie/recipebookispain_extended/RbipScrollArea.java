package com.alonie.recipebookispain_extended;

/**
 * Simple bounding box used for scroll hit-testing on rotated tab rows.
 * Lives outside the mixin package to avoid Mixin's cross-class reference restriction.
 */
public record RbipScrollArea(int left, int top, int right, int bottom) {
    public int width() { return right - left; }
    public int height() { return bottom - top; }
}
