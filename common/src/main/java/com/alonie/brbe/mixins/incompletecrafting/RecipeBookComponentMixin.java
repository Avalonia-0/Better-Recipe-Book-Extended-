package com.alonie.brbe.mixins.incompletecrafting;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.mixins.accessors.RecipeCollectionAccessor;
import com.alonie.brbe.util.IncompatibleCraftingUtil;
import com.alonie.brbe.util.PartialCraftingUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookPage;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

@Mixin(RecipeBookComponent.class)
public abstract class RecipeBookComponentMixin {
    @Shadow @Final
    protected RecipeBookMenu menu;

    @Shadow @Final
    protected Minecraft minecraft;

    @Shadow
    private net.minecraft.client.gui.components.EditBox searchBox;

    @Inject(method = "updateCollections", at = @At("HEAD"))
    private void betterRecipeBook$trackPartialFilteringUpdate(boolean resetPageNumber, boolean isFiltering, CallbackInfo ci) {
        boolean retainIncompatible = BetterRecipeBook.config.showAllRecipesInSurvival
                && !isFiltering
                && this.minecraft != null
                && this.minecraft.screen instanceof InventoryScreen;
        IncompatibleCraftingUtil.beginFiltering(retainIncompatible);
    }

    @Redirect(method = "updateCollections", at = @At(value = "INVOKE", target = "Ljava/util/List;removeIf(Ljava/util/function/Predicate;)Z"))
    private boolean betterRecipeBook$keepPartiallyCraftable(List<RecipeCollection> collections, Predicate<? super RecipeCollection> predicate) {
        // Skip incompatible logic when showAllRecipesInSurvival is off.
        // (Partial material injection always runs — it is gated separately.)
        if (!BetterRecipeBook.config.showAllRecipesInSurvival) {
            return collections.removeIf(predicate);
        }

        boolean onInventoryScreen = this.minecraft != null
                && this.minecraft.screen instanceof InventoryScreen;

        // Partial material marking — gated inside PartialCraftingUtil
        // Step 0: Clear previously-injected partial IDs from craftable set
        for (RecipeCollection collection : collections) {
            if (PartialCraftingUtil.hasPartialMaterials(collection)) {
                RecipeCollectionAccessor accessor = (RecipeCollectionAccessor) collection;
                for (RecipeDisplayEntry entry : collection.getRecipes()) {
                    RecipeDisplayId id = entry.id();
                    if (PartialCraftingUtil.isPartiallyCraftable(collection, id)) {
                        accessor.betterRecipeBook$getCraftable().remove(id);
                    }
                }
            }
        }

        for (RecipeCollection collection : collections) {
            PartialCraftingUtil.markPartialMaterials(collection, this.menu.slots);

            // Inject partial recipes into craftable set
            if (PartialCraftingUtil.hasPartialMaterials(collection)) {
                RecipeCollectionAccessor accessor = (RecipeCollectionAccessor) collection;
                for (RecipeDisplayEntry entry : collection.getRecipes()) {
                    RecipeDisplayId id = entry.id();
                    if (PartialCraftingUtil.isPartiallyCraftable(collection, id)) {
                        accessor.betterRecipeBook$getCraftable().add(id);
                    }
                }
            }
        }

        // Mark incompatible recipes — gated by showAllRecipesInSurvival (outer guard),
        // NOT by partialMarkingEnabled, so "当前无法合成" recipes don't become air
        // placeholders when partialMarkingEnabled is off.
        if (onInventoryScreen) {
            for (RecipeCollection collection : collections) {
                IncompatibleCraftingUtil.markIncompatibleRecipes(collection);
            }
        }

        boolean hasSearchActive = searchBox != null && !searchBox.getValue().isEmpty();
        boolean hasPartial = !hasSearchActive;
        boolean retainIncompatible = onInventoryScreen
                && IncompatibleCraftingUtil.isActive()
                && !hasSearchActive;

        if (!hasPartial && !retainIncompatible) {
            return collections.removeIf(predicate);
        }

        boolean removed = collections.removeIf(collection -> {
            if (!predicate.test(collection)) return false;
            if (hasPartial && PartialCraftingUtil.hasPartialMaterials(collection)) return false;
            if (retainIncompatible && IncompatibleCraftingUtil.hasIncompatibleRecipes(collection)) return false;
            return true;
        });

        return removed;
    }

    @Redirect(method = "updateCollections", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/recipebook/RecipeBookPage;updateCollections(Ljava/util/List;ZZ)V"))
    private void betterRecipeBook$sortCraftableBeforePartial(
            RecipeBookPage page, List<RecipeCollection> list, boolean resetPageNumber, boolean isFiltering) {
        if (!BetterRecipeBook.config.partialCraftingEnabled) {
            page.updateCollections(list, resetPageNumber, isFiltering);
            return;
        }
        List<RecipeCollection> craftable = new ArrayList<>();
        List<RecipeCollection> partial = new ArrayList<>();
        List<RecipeCollection> other = new ArrayList<>();
        for (RecipeCollection c : list) {
            boolean hasCraftable = c.hasCraftable();
            boolean hasPartial = PartialCraftingUtil.hasPartialMaterials(c);
            if (hasCraftable && !hasPartial) {
                craftable.add(c);
            } else if (hasPartial) {
                partial.add(c);
            } else {
                other.add(c);
            }
        }
        list.clear();
        list.addAll(craftable);
        list.addAll(partial);
        list.addAll(other);
        page.updateCollections(list, resetPageNumber, isFiltering);
    }
}
