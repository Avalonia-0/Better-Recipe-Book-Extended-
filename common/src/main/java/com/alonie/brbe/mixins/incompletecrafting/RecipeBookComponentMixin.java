package com.alonie.brbe.mixins.incompletecrafting;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.util.IncompatibleCraftingUtil;
import com.alonie.brbe.util.PartialCraftingUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.world.inventory.RecipeBookMenu;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.function.Predicate;

@Mixin(RecipeBookComponent.class)
public abstract class RecipeBookComponentMixin {
    @Shadow
    @Final
    protected RecipeBookMenu menu;

    @Shadow
    @Final
    protected Minecraft minecraft;

    @Inject(method = "updateCollections", at = @At("HEAD"))
    private void betterRecipeBook$trackPartialFilteringUpdate(boolean resetPageNumber, boolean isFiltering, CallbackInfo ci) {
        PartialCraftingUtil.beginFilteringUpdate(BetterRecipeBook.config.partialCraftableEqualsCraftable && isFiltering);

        boolean inInventory = isFiltering
                && this.minecraft != null
                && this.minecraft.screen instanceof InventoryScreen;
        IncompatibleCraftingUtil.beginFiltering(inInventory);
    }

    @Redirect(method = "updateCollections", at = @At(value = "INVOKE", target = "Ljava/util/List;removeIf(Ljava/util/function/Predicate;)Z"))
    private boolean betterRecipeBook$keepPartiallyCraftable(List<RecipeCollection> collections, Predicate<? super RecipeCollection> predicate) {
        boolean inInventory = this.minecraft != null
                && this.minecraft.screen instanceof InventoryScreen;

        for (RecipeCollection collection : collections) {
            PartialCraftingUtil.markPartialMaterials(collection, this.menu.slots);
            if (inInventory) {
                IncompatibleCraftingUtil.markIncompatibleRecipes(collection);
            }
        }

        boolean hasPartial = BetterRecipeBook.config.partialCraftableEqualsCraftable;

        if (!hasPartial && !inInventory) {
            return collections.removeIf(predicate);
        }

        boolean removed = collections.removeIf(collection -> {
            if (!predicate.test(collection)) return false;
            if (hasPartial && PartialCraftingUtil.hasPartialMaterials(collection)) return false;
            if (inInventory && IncompatibleCraftingUtil.hasIncompatibleRecipes(collection)) return false;
            return true;
        });

        if (hasPartial) {
            PartialCraftingUtil.sortCraftableBeforePartial(collections);
        }
        return removed;
    }
}
