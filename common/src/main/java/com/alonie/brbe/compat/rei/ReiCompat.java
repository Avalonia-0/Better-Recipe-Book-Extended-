package com.alonie.brbe.compat.rei;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.compat.ItemViewCompat;

import net.minecraft.world.item.ItemStack;

public class ReiCompat {
    private static volatile boolean registered;

    public static void register() {
        if (!isModLoaded("roughlyenoughitems")) return;

        ItemViewCompat.setHandler(new ReiHandler() {
            @Override
            public boolean openRecipeView(ItemStack stack) {
                return openView("addRecipesFor", stack);
            }
            @Override
            public boolean openUsageView(ItemStack stack) {
                return openView("addUsagesFor", stack);
            }
        });
        registered = true;
    }

    private static void ensureRegistered() {
        if (registered) return;
        registered = true;
        register();
    }

    private static boolean openView(String methodName, ItemStack stack) {
        try {
            Class<?> clientHelperClass = Class.forName("me.shedaniel.rei.api.client.ClientHelper");
            Object instance = clientHelperClass.getMethod("getInstance").invoke(null);
            Class<?> builderClass = Class.forName("me.shedaniel.rei.api.client.view.ViewSearchBuilder");
            Object builder = builderClass.getMethod("builder").invoke(null);
            Class<?> entryStacksClass = Class.forName("me.shedaniel.rei.api.common.util.EntryStacks");
            Object entryStack = entryStacksClass.getMethod("of", ItemStack.class).invoke(null, stack);
            Class<?> entryStackClass = Class.forName("me.shedaniel.rei.api.common.entry.EntryStack");
            builderClass.getMethod(methodName, entryStackClass).invoke(builder, entryStack);
            return (Boolean) clientHelperClass.getMethod("openView", builderClass).invoke(instance, builder);
        } catch (ReflectiveOperationException e) {
            BetterRecipeBook.LOGGER.warn("Failed to open REI view via {} for stack {}", methodName, stack, e);
            return false;
        }
    }

    public static void setHandler(ReiHandler h) {
        ItemViewCompat.setHandler(h);
    }

    public static boolean isLoaded() {
        ensureRegistered();
        return ItemViewCompat.isLoaded();
    }

    public static boolean openRecipeView(ItemStack stack) {
        return ItemViewCompat.openRecipeView(stack);
    }

    public static boolean openUsageView(ItemStack stack) {
        return ItemViewCompat.openUsageView(stack);
    }

    public interface ReiHandler extends ItemViewCompat.Handler {
        // inherits openRecipeView(ItemStack) and openUsageView(ItemStack)
    }

    private static boolean isModLoaded(String modId) {
        // Try Fabric Loader first (Fabric)
        try {
            Class<?> fabricLoader = Class.forName("net.fabricmc.loader.api.FabricLoader");
            Object instance = fabricLoader.getMethod("getInstance").invoke(null);
            return (boolean) instance.getClass().getMethod("isModLoaded", String.class)
                    .invoke(instance, modId);
        } catch (Throwable ignored) { }

        // Try NeoForge ModList (NeoForge)
        try {
            Class<?> modList = Class.forName("net.neoforged.fml.ModList");
            Object instance = modList.getMethod("get").invoke(null);
            return (boolean) instance.getClass().getMethod("isLoaded", String.class)
                    .invoke(instance, modId);
        } catch (Throwable ignored) { }

        // Try legacy FMLLoader (older NeoForge / Forge)
        try {
            Class<?> fmlLoader = Class.forName("net.neoforged.fml.loading.FMLLoader");
            return (boolean) fmlLoader.getMethod("isModLoaded", String.class)
                    .invoke(null, modId);
        } catch (Throwable ignored) { }

        return false;
    }
}
