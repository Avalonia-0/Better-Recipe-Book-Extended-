package com.alonie.brbe.compat.emi;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.compat.ItemViewCompat;
import net.minecraft.world.item.ItemStack;

/**
 * EMI compat handler — bridges EMI recipe/usage lookups into the
 * {@link ItemViewCompat} handler system.
 *
 * <p>Uses pure reflection (zero compile-time dependency on EMI).
 * Registered from platform client initialisers (Fabric/NeoForge).
 * Follows the same pattern as {@code ReiCompat}.</p>
 */
public final class EmiCompat {

    private static volatile boolean registered;

    private EmiCompat() {}

    /** Called from platform-specific init. */
    public static void register() {
        if (!isModLoaded("emi")) return;

        ItemViewCompat.setHandler(new EmiHandler() {
            @Override
            public boolean openRecipeView(ItemStack stack) {
                return openView("displayRecipes", stack);
            }
            @Override
            public boolean openUsageView(ItemStack stack) {
                return openView("displayUses", stack);
            }
            @Override
            public boolean matchesShowRecipe(int keyCode, int scanCode) {
                return EmiCompat.matchesEmiBind("viewRecipes", keyCode, scanCode);
            }
            @Override
            public boolean matchesShowUses(int keyCode, int scanCode) {
                return EmiCompat.matchesEmiBind("viewUses", keyCode, scanCode);
            }
        });
        registered = true;
    }

    /** Lazy fallback: called on first isLoaded() check if register() wasn't called yet. */
    private static void ensureRegistered() {
        if (registered) return;
        registered = true;
        register();
    }

    private static boolean openView(String methodName, ItemStack stack) {
        if (stack.isEmpty()) return false;
        try {
            Class<?> emiStackClass = Class.forName("dev.emi.emi.api.stack.EmiStack");
            Object emiStack = emiStackClass.getMethod("of", ItemStack.class).invoke(null, stack);
            Class<?> emiApiClass = Class.forName("dev.emi.emi.api.EmiApi");
            emiApiClass.getMethod(methodName, Class.forName("dev.emi.emi.api.stack.EmiIngredient"))
                    .invoke(null, emiStack);
            return true;
        } catch (ReflectiveOperationException e) {
            BetterRecipeBook.LOGGER.debug("[BRBE] EMI view failed via {}: {}", methodName, e.getMessage());
            return false;
        }
    }

    public static boolean isLoaded() {
        ensureRegistered();
        return ItemViewCompat.isLoaded();
    }

    // -- Handler interface (mirrors JeiCompat.JeiHandler / ReiCompat.ReiHandler) --

    public interface EmiHandler extends ItemViewCompat.Handler {
        // inherits openRecipeView(ItemStack) and openUsageView(ItemStack)
    }

    /**
     * Query EMI's configured key binding via reflection.
     *
     * @param fieldName {@code "viewRecipes"} or {@code "viewUses"} in {@code EmiConfig}
     * @return true if the key press matches EMI's configured binding
     */
    static boolean matchesEmiBind(String fieldName, int keyCode, int scanCode) {
        try {
            Class<?> emiConfig = Class.forName("dev.emi.emi.config.EmiConfig");
            Object emiBind = emiConfig.getField(fieldName).get(null);
            return (boolean) emiBind.getClass().getMethod("matchesKey", int.class, int.class)
                    .invoke(emiBind, keyCode, scanCode);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Cross-loader mod-detection using reflection.
     * Tries NeoForge first (ModList), then Fabric (FabricLoader).
     *
     * <p>NeoForge MUST come first: on NeoForge with Sinytra Connector (or any
     * mod that bundles Fabric Loader classes), Class.forName("FabricLoader")
     * succeeds but reports EMI as not loaded (it's a NeoForge mod), and the
     * false return short-circuits before reaching the NeoForge check.</p>
     */
    private static boolean isModLoaded(String modId) {
        // NeoForge / Forge
        try {
            Class<?> modList = Class.forName("net.neoforged.fml.ModList");
            Object instance = modList.getMethod("get").invoke(null);
            return (boolean) instance.getClass().getMethod("isLoaded", String.class)
                    .invoke(instance, modId);
        } catch (Throwable e1) {
            // Fabric
            try {
                Class<?> fabricLoader = Class.forName("net.fabricmc.loader.api.FabricLoader");
                Object instance = fabricLoader.getMethod("getInstance").invoke(null);
                return (boolean) instance.getClass().getMethod("isModLoaded", String.class)
                        .invoke(instance, modId);
            } catch (Throwable e2) {
                return false;
            }
        }
    }
}
