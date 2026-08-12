package com.alonie.brbe.mixins.modname;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.util.ModNameUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.recipebook.GhostSlots;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

/**
 * Adds the source mod name to ghost recipe item tooltips (the missing-material
 * preview shown when hovering a recipe). Mirrors {@code modname.RecipeButtonMixin}
 * for the recipe book's ghost slots.
 */
@Mixin(GhostSlots.class)
public abstract class GhostRecipeTooltipMixin {

    @Redirect(
            method = "renderTooltip",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screens/Screen;getTooltipFromItem(Lnet/minecraft/client/Minecraft;Lnet/minecraft/world/item/ItemStack;)Ljava/util/List;"
            )
    )
    private List<Component> brbe$appendModName(Minecraft minecraft, ItemStack itemStack) {
        List<Component> tooltip = Screen.getTooltipFromItem(minecraft, itemStack);
        if (!BetterRecipeBook.config.showModName) {
            return tooltip;
        }

        Component modName = ModNameUtil.getFormattedModName(itemStack);
        if (modName == null || modName.getString().isEmpty()) {
            return tooltip;
        }

        tooltip.add(Component.empty());
        tooltip.add(modName);
        return tooltip;
    }
}
