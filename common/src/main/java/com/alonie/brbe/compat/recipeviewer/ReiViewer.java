package com.alonie.brbe.compat.recipeviewer;

import net.minecraft.world.item.ItemStack;

/**
 * REI adapter for the {@link RecipeViewer} SPI.
 *
 * <p>Uses reflection to call REI's {@code ViewSearchBuilder} API —
 * zero compile-time dependency on REI.  The same approach as the
 * existing {@code ReiCompat.openView()}.</p>
 */
public final class ReiViewer implements RecipeViewer {

    private static volatile boolean available;

    /** Called from platform init after REI detection. */
    public static void markAvailable(boolean avail) {
        available = avail;
    }

    @Override
    public boolean isAvailable() {
        if (!available) return false;
        // Double-check that REI classes are actually loadable
        try {
            Class.forName("me.shedaniel.rei.api.client.ClientHelper");
            return true;
        } catch (ClassNotFoundException e) {
            available = false;
            return false;
        }
    }

    @Override
    public void showRecipe(ItemStack stack) {
        openView("addRecipesFor", stack);
    }

    @Override
    public void showUses(ItemStack stack) {
        openView("addUsagesFor", stack);
    }

    private static void openView(String methodName, ItemStack stack) {
        if (stack.isEmpty()) return;
        try {
            Class<?> clientHelper = Class.forName("me.shedaniel.rei.api.client.ClientHelper");
            Object instance = clientHelper.getMethod("getInstance").invoke(null);
            Class<?> builderClass = Class.forName("me.shedaniel.rei.api.client.view.ViewSearchBuilder");
            Object builder = builderClass.getMethod("builder").invoke(null);
            Class<?> entryStacks = Class.forName("me.shedaniel.rei.api.common.util.EntryStacks");
            Object entryStack = entryStacks.getMethod("of", ItemStack.class).invoke(null, stack);
            Class<?> entryStackClass = Class.forName("me.shedaniel.rei.api.common.entry.EntryStack");
            builderClass.getMethod(methodName, entryStackClass).invoke(builder, entryStack);
            clientHelper.getMethod("openView", builderClass).invoke(instance, builder);
        } catch (Exception e) {
            // Silently ignore — REI may not be loaded or API may have changed
        }
    }

    /**
     * Cross-loader mod-detection using reflection.
     * Tries Fabric (FabricLoader) first, then NeoForge (ModList).
     */
    public static boolean isReiLoaded() {
        try {
            Class<?> fabricLoader = Class.forName("net.fabricmc.loader.api.FabricLoader");
            Object instance = fabricLoader.getMethod("getInstance").invoke(null);
            return (boolean) instance.getClass().getMethod("isModLoaded", String.class)
                    .invoke(instance, "roughlyenoughitems");
        } catch (Throwable e1) {
            try {
                Class<?> modList = Class.forName("net.neoforged.fml.ModList");
                Object instance = modList.getMethod("get").invoke(null);
                return (boolean) instance.getClass().getMethod("isLoaded", String.class)
                        .invoke(instance, "roughlyenoughitems");
            } catch (Throwable e2) {
                return false;
            }
        }
    }
}
