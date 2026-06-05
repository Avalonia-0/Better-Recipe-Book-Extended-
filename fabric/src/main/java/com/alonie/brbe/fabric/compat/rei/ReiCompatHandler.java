package com.alonie.brbe.fabric.compat.rei;

import dev.architectury.platform.Platform;
import com.alonie.brbe.compat.rei.ReiCompat;
import net.minecraft.world.item.ItemStack;

/**
 * Fabric-side REI handler registration.
 * Uses reflection to avoid compile-time dependency on REI.
 */
public class ReiCompatHandler {

    public static void register() {
        if (!Platform.isModLoaded("roughlyenoughitems")) {
            return;
        }

        ReiCompat.setHandler(new ReiCompat.ReiHandler() {
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
}
