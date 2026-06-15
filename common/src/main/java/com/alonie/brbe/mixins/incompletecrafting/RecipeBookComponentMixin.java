package com.alonie.brbe.mixins.incompletecrafting;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.mixins.accessors.RecipeCollectionAccessor;
import com.alonie.brbe.util.CollectionCategory;
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
        // ── Gate variables: single point of truth for each concern ──
        boolean onInventoryScreen = this.minecraft != null
                && this.minecraft.screen instanceof InventoryScreen;
        boolean retainPartial = BetterRecipeBook.config.partialMarkingEnabled;
        boolean retainIncompatible = onInventoryScreen
                && BetterRecipeBook.config.showAllRecipesInSurvival;

        // Only skip everything when BOTH features are off.
        if (!retainPartial && !retainIncompatible) {
            return collections.removeIf(predicate);
        }

        // ── Partial material marking (gated inside PartialCraftingUtil) ──
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

        // ── Incompatible recipe marking ──
        if (retainIncompatible) {
            for (RecipeCollection collection : collections) {
                IncompatibleCraftingUtil.markIncompatibleRecipes(collection);
            }
        }

        // ── Retention flags for the removeIf predicate ──
        boolean hasSearchActive = searchBox != null && !searchBox.getValue().isEmpty();
        boolean keepPartial = retainPartial && !hasSearchActive;
        boolean keepIncompatible = retainIncompatible
                && IncompatibleCraftingUtil.isActive()
                && !hasSearchActive;

        if (!keepPartial && !keepIncompatible) {
            return collections.removeIf(predicate);
        }

        boolean removed = collections.removeIf(collection -> {
            if (!predicate.test(collection)) return false;
            if (keepPartial && PartialCraftingUtil.hasPartialMaterials(collection)) return false;
            if (keepIncompatible && IncompatibleCraftingUtil.hasIncompatibleRecipes(collection)) return false;
            return true;
        });

        return removed;
    }

    @Redirect(method = "updateCollections", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/recipebook/RecipeBookPage;updateCollections(Ljava/util/List;ZZ)V"))
    private void betterRecipeBook$sortCraftableBeforePartial(
            RecipeBookPage page, List<RecipeCollection> list, boolean resetPageNumber, boolean isFiltering) {
        // Sort when partialCraftingEnabled is active, or when
        // partialMarkingEnabled is active AND the vanilla filter is on.
        boolean shouldSort = BetterRecipeBook.config.partialCraftingEnabled
                || (BetterRecipeBook.config.partialMarkingEnabled && isFiltering);
        if (!shouldSort) {
            page.updateCollections(list, resetPageNumber, isFiltering);
            return;
        }

        List<RecipeCollection> front = new ArrayList<>();
        List<RecipeCollection> middle = new ArrayList<>();
        List<RecipeCollection> back = new ArrayList<>();

        for (RecipeCollection c : list) {
            CollectionCategory cat = PartialCraftingUtil.categorize(c);
            if (isFiltering) {
                // Filter ON → 3 groups: TRULY_CRAFTABLE > PARTIAL > UNASSIGNED
                switch (cat) {
                    case TRULY_CRAFTABLE -> front.add(c);
                    case PARTIAL -> middle.add(c);
                    case UNASSIGNED -> back.add(c);
                }
            } else {
                // Filter OFF → 2 groups: (TRULY_CRAFTABLE + PARTIAL) > UNASSIGNED
                if (cat != CollectionCategory.UNASSIGNED) front.add(c);
                else back.add(c);
            }
        }

        list.clear();
        list.addAll(front);
        list.addAll(middle);
        list.addAll(back);
        page.updateCollections(list, resetPageNumber, isFiltering);
    }
}
