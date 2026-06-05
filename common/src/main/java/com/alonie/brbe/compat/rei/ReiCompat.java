package com.alonie.brbe.compat.rei;

import dev.architectury.platform.Platform;
import net.minecraft.world.item.ItemStack;

/**
 * Bridge for REI (Roughly Enough Items) integration.
 * Uses a handler pattern so common code can call REI API without REI being on the classpath.
 * The handler is registered via reflection at runtime if REI is loaded.
 */
public class ReiCompat {
    private static ReiHandler handler;

    /**
     * Register the REI handler using reflection. No-op if REI is not loaded.
     * Safe to call early (deferred by platform until mods are initialized).
     */
    public static void register() {
        if (!Platform.isModLoaded("roughlyenoughitems")) return;

        setHandler(new ReiHandler() {
            @Override
            public boolean openRecipeView(ItemStack stack) {
                try {
                    Class<?> clientHelperClass = Class.forName("me.shedaniel.rei.api.client.ClientHelper");
                    Object instance = clientHelperClass.getMethod("getInstance").invoke(null);
                    Class<?> builderClass = Class.forName("me.shedaniel.rei.api.client.view.ViewSearchBuilder");
                    Object builder = builderClass.getMethod("builder").invoke(null);
                    Class<?> entryStacksClass = Class.forName("me.shedaniel.rei.api.common.util.EntryStacks");
                    Object entryStack = entryStacksClass.getMethod("of", ItemStack.class).invoke(null, stack);
                    builderClass.getMethod("addRecipesFor", entryStack.getClass()).invoke(builder, entryStack);
                    return (Boolean) clientHelperClass.getMethod("openView", builderClass).invoke(instance, builder);
                } catch (Exception e) {
                    return false;
                }
            }

            @Override
            public boolean openUsageView(ItemStack stack) {
                try {
                    Class<?> clientHelperClass = Class.forName("me.shedaniel.rei.api.client.ClientHelper");
                    Object instance = clientHelperClass.getMethod("getInstance").invoke(null);
                    Class<?> builderClass = Class.forName("me.shedaniel.rei.api.client.view.ViewSearchBuilder");
                    Object builder = builderClass.getMethod("builder").invoke(null);
                    Class<?> entryStacksClass = Class.forName("me.shedaniel.rei.api.common.util.EntryStacks");
                    Object entryStack = entryStacksClass.getMethod("of", ItemStack.class).invoke(null, stack);
                    builderClass.getMethod("addUsagesFor", entryStack.getClass()).invoke(builder, entryStack);
                    return (Boolean) clientHelperClass.getMethod("openView", builderClass).invoke(instance, builder);
                } catch (Exception e) {
                    return false;
                }
            }
        });
    }

    public static void setHandler(ReiHandler h) {
        handler = h;
    }

    public static boolean isLoaded() {
        return handler != null;
    }

    public static boolean openRecipeView(ItemStack stack) {
        if (handler != null) {
            return handler.openRecipeView(stack);
        }
        return false;
    }

    public static boolean openUsageView(ItemStack stack) {
        if (handler != null) {
            return handler.openUsageView(stack);
        }
        return false;
    }

    public interface ReiHandler {
        boolean openRecipeView(ItemStack stack);
        boolean openUsageView(ItemStack stack);
    }
}
