package com.alonie.brbe.compat.recipeviewer;

import net.minecraft.world.item.ItemStack;

/**
 * SPI for external recipe-viewer mods (JEI, REI, EMI).
 *
 * <p>Each implementation is a <strong>thin adapter</strong> that wraps the
 * target mod's API via reflection.  There are zero compile-time dependencies
 * on JEI, REI, or EMI anywhere in the common module.</p>
 *
 * <h3>Adding a new viewer</h3>
 * <ol>
 *   <li>Implement this interface (using reflection to call the target mod)</li>
 *   <li>Call {@code RecipeViewerRegistry.register(new YourViewer())} at startup</li>
 * </ol>
 */
public interface RecipeViewer {

    /** A no-op viewer returned when nothing is available. */
    RecipeViewer NONE = new RecipeViewer() {
        @Override public boolean isAvailable() { return false; }
        @Override public void showRecipe(ItemStack stack) {}
        @Override public void showUses(ItemStack stack) {}
        @Override public boolean matchesShowRecipe(int keyCode, int scanCode) { return false; }
        @Override public boolean matchesShowUses(int keyCode, int scanCode) { return false; }
    };

    /**
     * Whether the target mod is loaded and ready.  Implementations typically
     * check via {@code Class.forName(...)}.
     */
    boolean isAvailable();

    /** Open the recipe view for the given result item. */
    void showRecipe(ItemStack stack);

    /** Open the usage view for the given item. */
    void showUses(ItemStack stack);

    /**
     * Check whether the given key press matches this viewer's
     * "show recipe" binding, as configured in the target mod's own
     * key-bind settings (not a hardcoded BRBE key).
     */
    boolean matchesShowRecipe(int keyCode, int scanCode);

    /**
     * Check whether the given key press matches this viewer's
     * "show uses" binding.
     */
    boolean matchesShowUses(int keyCode, int scanCode);
}
