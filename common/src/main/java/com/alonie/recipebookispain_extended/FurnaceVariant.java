package com.alonie.recipebookispain_extended;

/**
 * Identifies which furnace variant is active on the current screen.
 * Extracted as a top-level enum to avoid Fabric Knot classloader
 * issues with inner enum classes loaded lazily from the mod JAR.
 */
public enum FurnaceVariant {
    FURNACE,
    SMOKER,
    BLAST_FURNACE
}
