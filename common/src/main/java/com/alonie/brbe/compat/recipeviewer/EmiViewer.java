package com.alonie.brbe.compat.recipeviewer;

import net.minecraft.world.item.ItemStack;

/**
 * EMI adapter for the {@link RecipeViewer} SPI.
 *
 * <p>Uses pure reflection to call EMI's {@code EmiApi.displayRecipes()} /
 * {@code EmiApi.displayUses()} — zero compile-time dependency on EMI.
 * Follows the same pattern as {@link ReiViewer}.</p>
 */
public final class EmiViewer implements RecipeViewer {

    private static volatile boolean available;

    /** Called from platform init after EMI detection. */
    public static void markAvailable(boolean avail) {
        available = avail;
    }

    @Override
    public boolean isAvailable() {
        if (!available) return false;
        // Double-check that EMI classes are actually loadable
        try {
            Class.forName("dev.emi.emi.api.EmiApi");
            return true;
        } catch (ClassNotFoundException e) {
            available = false;
            return false;
        }
    }

    @Override
    public void showRecipe(ItemStack stack) {
        openView("displayRecipes", stack);
    }

    @Override
    public void showUses(ItemStack stack) {
        openView("displayUses", stack);
    }

    @Override
    public boolean matchesShowRecipe(int keyCode, int scanCode) {
        if (!available) return false;
        try {
            Class<?> emiConfig = Class.forName("dev.emi.emi.config.EmiConfig");
            Object viewRecipes = emiConfig.getField("viewRecipes").get(null);
            return (boolean) viewRecipes.getClass().getMethod("matchesKey", int.class, int.class)
                    .invoke(viewRecipes, keyCode, scanCode);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean matchesShowUses(int keyCode, int scanCode) {
        if (!available) return false;
        try {
            Class<?> emiConfig = Class.forName("dev.emi.emi.config.EmiConfig");
            Object viewUses = emiConfig.getField("viewUses").get(null);
            return (boolean) viewUses.getClass().getMethod("matchesKey", int.class, int.class)
                    .invoke(viewUses, keyCode, scanCode);
        } catch (Exception e) {
            return false;
        }
    }

    private static void openView(String methodName, ItemStack stack) {
        if (stack.isEmpty()) return;
        try {
            Class<?> emiStackClass = Class.forName("dev.emi.emi.api.stack.EmiStack");
            Object emiStack = emiStackClass.getMethod("of", ItemStack.class).invoke(null, stack);
            Class<?> emiApiClass = Class.forName("dev.emi.emi.api.EmiApi");
            emiApiClass.getMethod(methodName, Class.forName("dev.emi.emi.api.stack.EmiIngredient"))
                    .invoke(null, emiStack);
        } catch (Exception e) {
            // Silently ignore — EMI may not be loaded or API may have changed
        }
    }

    /**
     * Cross-loader mod-detection using reflection.
     * Tries NeoForge first (ModList), then Fabric (FabricLoader).
     */
    public static boolean isEmiLoaded() {
        // NeoForge / Forge
        try {
            Class<?> modList = Class.forName("net.neoforged.fml.ModList");
            Object instance = modList.getMethod("get").invoke(null);
            return (boolean) instance.getClass().getMethod("isLoaded", String.class)
                    .invoke(instance, "emi");
        } catch (Throwable e1) {
            // Fabric
            try {
                Class<?> fabricLoader = Class.forName("net.fabricmc.loader.api.FabricLoader");
                Object instance = fabricLoader.getMethod("getInstance").invoke(null);
                return (boolean) instance.getClass().getMethod("isModLoaded", String.class)
                        .invoke(instance, "emi");
            } catch (Throwable e2) {
                return false;
            }
        }
    }
}
