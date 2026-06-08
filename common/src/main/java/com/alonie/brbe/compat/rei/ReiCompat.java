package com.alonie.brbe.compat.rei;

import com.alonie.brbe.BetterRecipeBook;
import dev.architectury.platform.Platform;
import net.minecraft.world.item.ItemStack;

public class ReiCompat {
    private static ReiHandler handler;

    public static void register() {
        if (!Platform.isModLoaded("roughlyenoughitems")) return;

        setHandler(new ReiHandler() {
            @Override
            public boolean openRecipeView(ItemStack stack) {
                return openView("addRecipesFor", stack);
            }
            @Override
            public boolean openUsageView(ItemStack stack) {
                return openView("addUsagesFor", stack);
            }
        });
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
