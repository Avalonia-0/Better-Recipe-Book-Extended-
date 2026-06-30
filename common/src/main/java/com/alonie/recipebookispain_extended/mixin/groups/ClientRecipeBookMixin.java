package com.alonie.recipebookispain_extended.mixin.groups;

import com.alonie.recipebookispain_extended.RecipeBookIsPain;
import com.alonie.recipebookispain_extended.RecipeBookIsPainExtendedConfig;
import com.alonie.brbe.util.RecipeBookDebugLogger;
import net.minecraft.client.RecipeBookCategories;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.BlastFurnaceMenu;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.inventory.SmokerMenu;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.ArrayList;
import java.util.List;

/**
 * 1.21.1 RBIP: Intercepts {@code ClientRecipeBook.getCollection()} inside
 * {@code RecipeBookComponent.updateCollections()} so that when a creative tab
 * is active (category == UNKNOWN), the returned collections are filtered to
 * only include collections whose recipes have results in that creative tab.
 * <p>
 * For furnace screens, detects the furnace type and uses the correct search
 * category (FURNACE_SEARCH / SMOKER_SEARCH / BLAST_FURNACE_SEARCH) as the
 * base recipe pool.
 */
@Mixin(RecipeBookComponent.class)
public abstract class ClientRecipeBookMixin {

    @Shadow
    protected RecipeBookMenu menu;

    @Redirect(
            method = "updateCollections",
            require = 0,
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/ClientRecipeBook;getCollection(Lnet/minecraft/client/RecipeBookCategories;)Ljava/util/List;"
            )
    )
    private List<RecipeCollection> rbip$getFilteredCollections(
            net.minecraft.client.ClientRecipeBook book, RecipeBookCategories category) {

        if (!RecipeBookIsPainExtendedConfig.enabled()
                || RecipeBookIsPain.activeCreativeTab == null
                || category != RecipeBookCategories.UNKNOWN) {
            if (RecipeBookDebugLogger.enabled && category == RecipeBookCategories.UNKNOWN) {
                RecipeBookDebugLogger.onRbipFilterCollections(
                        category.name(), 0, 0, RecipeBookIsPain.activeCreativeTab != null);
            }
            return book.getCollection(category);
        }

        CreativeModeTab activeTab = RecipeBookIsPain.activeCreativeTab;

        // Determine the correct search category: crafting vs. furnace type
        RecipeBookCategories searchCategory;
        if (this.menu instanceof AbstractFurnaceMenu furnaceMenu) {
            if (furnaceMenu instanceof SmokerMenu) {
                searchCategory = RecipeBookCategories.SMOKER_SEARCH;
            } else if (furnaceMenu instanceof BlastFurnaceMenu) {
                searchCategory = RecipeBookCategories.BLAST_FURNACE_SEARCH;
            } else {
                searchCategory = RecipeBookCategories.FURNACE_SEARCH;
            }
        } else {
            searchCategory = RecipeBookCategories.CRAFTING_SEARCH;
        }

        // Get all recipes for the search category, then filter by creative tab
        List<RecipeCollection> allBase = book.getCollection(searchCategory);

        List<RecipeCollection> matching = new ArrayList<>();
        for (RecipeCollection collection : allBase) {
            if (rbip$anyRecipeInTab(collection, activeTab)) {
                matching.add(collection);
            }
        }

        RecipeBookDebugLogger.onRbipFilterCollections(
                searchCategory.name(), allBase.size(), matching.size(), true);

        // When no recipes match the creative tab (e.g. furnace recipes vs.
        // a building-blocks tab), fall back to all recipes for this search
        // category instead of showing an empty book.
        if (matching.isEmpty() && !allBase.isEmpty()) {
            RecipeBookIsPain.LOGGER.warn("[RBIP] No recipes match tab '{}' in {}, showing all {}",
                    activeTab.getDisplayName().getString(), searchCategory.name(), allBase.size());
            return allBase;
        }

        return matching;
    }

    @Unique
    private static boolean rbip$anyRecipeInTab(RecipeCollection collection, CreativeModeTab tab) {
        for (RecipeHolder<?> holder : collection.getRecipes()) {
            ItemStack result = holder.value().getResultItem(
                    net.minecraft.client.Minecraft.getInstance().level.registryAccess());
            if (!result.isEmpty() && RecipeBookIsPain.isItemInTab(result, tab)) {
                return true;
            }
        }
        return false;
    }
}
