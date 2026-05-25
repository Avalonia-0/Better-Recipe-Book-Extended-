package com.alonie.brbe.fabric.compat.rei;

import dev.architectury.platform.Platform;
import com.alonie.brbe.compat.rei.ReiCompat;
import me.shedaniel.rei.api.client.ClientHelper;
import me.shedaniel.rei.api.client.view.ViewSearchBuilder;
import me.shedaniel.rei.api.common.util.EntryStacks;
import net.minecraft.world.item.ItemStack;

/**
 * Fabric-side REI handler registration.
 * Checks if REI is loaded at runtime and registers the handler if so.
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
                    return ClientHelper.getInstance().openView(
                            ViewSearchBuilder.builder().addRecipesFor(EntryStacks.of(stack))
                    );
                } catch (Exception e) {
                    return false;
                }
            }

            @Override
            public boolean openUsageView(ItemStack stack) {
                try {
                    return ClientHelper.getInstance().openView(
                            ViewSearchBuilder.builder().addUsagesFor(EntryStacks.of(stack))
                    );
                } catch (Exception e) {
                    return false;
                }
            }
        });
    }
}
