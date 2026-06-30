package com.alonie.brbe.compat.recipeviewer;

import net.minecraft.world.item.ItemStack;

/**
 * JEI adapter for the {@link RecipeViewer} SPI.
 *
 * <p>Uses the existing {@code ItemViewCompat} bridge — the JEI runtime is
 * injected at startup by the Fabric JEI plugin
 * ({@code BetterRecipeBookJEIPlugin}).  This adapter simply checks whether
 * the bridge has been wired and delegates to it.</p>
 */
public final class JeiViewer implements RecipeViewer {

    private static volatile boolean available;

    /** Called from the JEI plugin when the runtime is ready. */
    public static void markAvailable() {
        available = true;
    }

    /** Called from the JEI plugin when the runtime shuts down. */
    public static void markUnavailable() {
        available = false;
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
    public void showRecipe(ItemStack stack) {
        if (stack.isEmpty()) return;
        try {
            // Delegate through ItemViewCompat — the handler is set by the JEI plugin
            com.alonie.brbe.compat.ItemViewCompat.openRecipeView(stack);
        } catch (Exception e) {
            // Silently ignore — JEI may not be loaded
        }
    }

    @Override
    public void showUses(ItemStack stack) {
        if (stack.isEmpty()) return;
        try {
            com.alonie.brbe.compat.ItemViewCompat.openUsageView(stack);
        } catch (Exception e) {
            // Silently ignore
        }
    }
}
