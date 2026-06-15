package com.alonie.recipebookispain_extended.mixin.groups;

import com.alonie.recipebookispain_extended.RecipeBookIsPain;
import com.alonie.recipebookispain_extended.RecipeBookIsPainExtendedConfig;
import net.minecraft.client.RecipeBookCategories;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Mixin;
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
 * We keep the ORIGINAL RecipeCollection objects so internal state
 * (craftable set, fitsDimensions, etc.) is preserved.
 */
@Mixin(RecipeBookComponent.class)
public abstract class ClientRecipeBookMixin {

    @Redirect(
            method = "updateCollections",
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
            return book.getCollection(category);
        }

        // Get the full crafting list, then keep only collections that have
        // at least one recipe in the active creative tab.
        List<RecipeCollection> allCrafting = book.getCollection(RecipeBookCategories.CRAFTING_SEARCH);
        CreativeModeTab activeTab = RecipeBookIsPain.activeCreativeTab;

        List<RecipeCollection> matching = new ArrayList<>();
        for (RecipeCollection collection : allCrafting) {
            if (rbip$anyRecipeInTab(collection, activeTab)) {
                matching.add(collection);
            }
        }

        RecipeBookIsPain.LOGGER.debug("[RBIP] Filtered: {}/{} collections match tab '{}'",
                matching.size(), allCrafting.size(),
                activeTab.getDisplayName().getString());

        return matching;
    }

    @Unique
    private static boolean rbip$anyRecipeInTab(RecipeCollection collection, CreativeModeTab tab) {
        for (RecipeHolder<?> holder : collection.getRecipes()) {
            // Use the level's registry access to get the result item
            ItemStack result = holder.value().getResultItem(
                    net.minecraft.client.Minecraft.getInstance().level.registryAccess());
            if (!result.isEmpty() && RecipeBookIsPain.isItemInTab(result, tab)) {
                return true;
            }
        }
        return false;
    }
}
