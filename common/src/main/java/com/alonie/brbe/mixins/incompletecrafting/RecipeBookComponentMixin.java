package com.alonie.brbe.mixins.incompletecrafting;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.util.PartialCraftingUtil;
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

    @Inject(method = "updateCollections", at = @At("HEAD"))
    private void betterRecipeBook$trackPartialFilteringUpdate(boolean resetPageNumber, boolean isFiltering, CallbackInfo ci) {
        PartialCraftingUtil.beginFilteringUpdate(isFiltering);
    }

    @Redirect(method = "updateCollections", at = @At(value = "INVOKE", target = "Ljava/util/List;removeIf(Ljava/util/function/Predicate;)Z", ordinal = 2))
    private boolean betterRecipeBook$keepPartiallyCraftable(List<RecipeCollection> collections, Predicate<? super RecipeCollection> predicate) {
        for (RecipeCollection collection : collections) {
            PartialCraftingUtil.markPartialMaterials(collection, this.menu.slots);
        }

        if (!BetterRecipeBook.config.partialCraftableEqualsCraftable) {
            return collections.removeIf(predicate);
        }

        boolean removed = collections.removeIf(collection ->
                predicate.test(collection) && !PartialCraftingUtil.hasPartialMaterials(collection));
        PartialCraftingUtil.sortCraftableBeforePartial(collections);
        return removed;
    }
}
