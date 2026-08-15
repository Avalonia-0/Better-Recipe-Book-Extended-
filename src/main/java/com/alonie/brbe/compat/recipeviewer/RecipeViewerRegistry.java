package com.alonie.brbe.compat.recipeviewer;

import java.util.ArrayList;
import java.util.List;

/**
 * Registry of {@link RecipeViewer} adapters (JEI, REI, EMI, etc.).
 *
 * <p>Each viewer registers itself during mod initialisation via
 * {@link #register(RecipeViewer)}.  Code that needs to open a recipe or usage
 * view queries the registry via {@link #findFirst()} or iterates all viewers.</p>
 *
 * <p>This replaces the old {@code ItemViewCompat} + {@code JeiCompat} +
 * {@code ReiCompat} pattern with a unified, self-describing SPI.</p>
 */
public final class RecipeViewerRegistry {

    private final List<RecipeViewer> viewers = new ArrayList<>();

    public void register(RecipeViewer viewer) {
        viewers.add(viewer);
    }

    /** Return all registered viewers (may include unavailable ones). */
    public List<RecipeViewer> all() {
        return List.copyOf(viewers);
    }

    /** Find the first viewer that reports itself as available. */
    public RecipeViewer findFirst() {
        for (RecipeViewer v : viewers) {
            if (v.isAvailable()) return v;
        }
        return RecipeViewer.NONE;
    }

    /** True if at least one viewer is available. */
    public boolean anyAvailable() {
        return findFirst() != RecipeViewer.NONE;
    }
}
