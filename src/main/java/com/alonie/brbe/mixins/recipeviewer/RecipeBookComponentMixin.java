package com.alonie.brbe.mixins.recipeviewer;

import com.alonie.brbe.cache.RecipeViewerIndex;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Ghost-preview fix for the BRBE R/U recipe-viewer overlay.
 *
 * <p>Vanilla {@link RecipeBookComponent#tryPlaceRecipe} returns early for
 * recipes that are not craftable ({@code !collection.isCraftable(id)}), so
 * clicking a fully-uncraftable recipe in the viewer overlay shows no ghost
 * preview.  The viewer shows every result, so we let the placement packet
 * through for viewer collections — the server answers with a ghost packet
 * when materials are missing, which is exactly the "grey missing materials"
 * preview the recipe book shows for partial recipes.</p>
 */
@Mixin(RecipeBookComponent.class)
public abstract class RecipeBookComponentMixin {

    @Shadow
    private RecipeCollection lastRecipeCollection;

    /** Let viewer collections past the craftability gate so uncraftable recipes
     *  still show a ghost preview. */
    @Redirect(method = "tryPlaceRecipe",
              at = @At(value = "INVOKE",
                       target = "Lnet/minecraft/client/gui/screens/recipebook/RecipeCollection;isCraftable(Lnet/minecraft/world/item/crafting/display/RecipeDisplayId;)Z"))
    private boolean brbe$viewerAlwaysCraftable(RecipeCollection collection, RecipeDisplayId id) {
        if (RecipeViewerIndex.isViewerCollection(collection)) return true;
        return collection.isCraftable(id);
    }
}
