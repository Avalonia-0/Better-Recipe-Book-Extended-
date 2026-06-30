package com.alonie.brbe.mixins.ungroup;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.util.BrbeLogger;
import com.alonie.brbe.util.CollectionPipeline;
import com.alonie.brbe.util.PartialCraftingUtil;
import com.alonie.brbe.util.VanillaPipelineCollection;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookPage;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Handles recipe ungrouping, filtering, and sorting for the vanilla
 * {@link RecipeBookComponent}.  Uses {@code @Inject} with local capture
 * and {@code ci.cancel()} — not {@code @Redirect} — to avoid conflicts
 * with other mixins on the same {@code page.updateCollections} call.
 *
 * <p>When {@code noGrouped} is true, this mixin replaces the vanilla
 * search/filter/sort logic entirely.  When false, it does nothing and
 * vanilla proceeds normally.
 */
@Mixin(RecipeBookComponent.class)
public class RecipeBookComponentMixin {

    @Shadow private String lastSearch;

    @Shadow private ClientRecipeBook book;

    @Shadow protected RecipeBookMenu<?, ?> menu;

    @Shadow @Final private RecipeBookPage recipeBookPage;

    /**
     * Inject right after the vanilla search-text filter has built
     * {@code list2} but before {@code ObjectLinkedOpenHashSet} is
     * constructed from it.  We capture the locals and replace the
     * rest of the method when ungrouping is enabled.
     *
     * <p>Captured locals (same names as vanilla bytecode):
     * <ul>
     *   <li>{@code boolean bl} — resetPageNumber (param 0)</li>
     *   <li>{@code List list}  — all collections</li>
     *   <li>{@code List list2} — collections filtered by search text</li>
     *   <li>{@code String string} — search text (lowercased)</li>
     * </ul>
     */
    @Inject(method = "updateCollections",
            locals = LocalCapture.CAPTURE_FAILSOFT,
            at = @At(value = "INVOKE",
                     target = "Lit/unimi/dsi/fastutil/objects/ObjectLinkedOpenHashSet;<init>(Ljava/util/Collection;)V"),
            cancellable = true)
    private void refreshSearchResults(boolean bl, CallbackInfo ci,
                                      List<RecipeCollection> list,
                                      List<RecipeCollection> list2,
                                      String string) {
        if (!BetterRecipeBook.ctx().config().alternativeRecipes.noGrouped) {
            return;
        }

        BrbeLogger.log(BrbeLogger.Category.PIPELINE,
                "ungroup ENTER — n=%d pCE=%s isFiltering=%s",
                list2.size(),
                BetterRecipeBook.ctx().config().partialCraftingEnabled,
                book.isFiltering(menu));

        // Stage 1: Remove collections whose first recipe name doesn't
        // match the vanilla search text (replaces vanilla's substring filter)
        list2.removeIf(collection -> {
            var it = collection.getRecipes().iterator();
            if (!it.hasNext()) return false;
            var recipe = it.next();
            return !recipe.value()
                    .getResultItem(collection.registryAccess())
                    .getHoverName()
                    .getString()
                    .toLowerCase(Locale.ROOT)
                    .contains(lastSearch.toLowerCase(Locale.ROOT));
        });

        // Stage 2: Partial marking + filter toggle.
        // When partialCraftingEnabled is ON, BRBE manages filtering —
        // skip the removal so all recipes reach the pipeline.  Partial
        // marking still runs so the pipeline can sort by craftability.
        // When OFF, respect the vanilla filter toggle.
        // Note: sorting (pin + craftable/partial order) is handled by
        // pipeline/RecipeBookComponentMixin @ModifyArg which intercepts
        // the page.updateCollections call below.
        boolean brbeManagesFilter = BetterRecipeBook.ctx().config().partialCraftingEnabled;
        if (brbeManagesFilter || book.isFiltering(menu)) {
            PartialCraftingUtil.beginFilteringUpdate(true);
            Set<Item> inventoryItems = PartialCraftingUtil.hashInventory(menu.slots);

            if (brbeManagesFilter) {
                // BRBE mode: mark partials for sorting, but don't remove
                // anything — let all recipes through to the pipeline.
                for (RecipeCollection collection : list2) {
                    PartialCraftingUtil.markPartialMaterials(collection, inventoryItems);
                }
            } else {
                // Vanilla mode: mark partials AND remove non-craftable.
                list2.removeIf(collection -> {
                    PartialCraftingUtil.markPartialMaterials(collection, inventoryItems);
                    return !collection.hasCraftable()
                            && !PartialCraftingUtil.hasPartialMaterials(collection);
                });
            }
        }

        // Stage 3: Pin sort — delegate to CollectionPipeline
        if (BetterRecipeBook.ctx().config().enablePinning) {
            CollectionPipeline.applyPins(list2);
        }

        BrbeLogger.log(BrbeLogger.Category.PIPELINE,
                "ungroup EXIT — final n=%d calling page.updateCollections", list2.size());

        recipeBookPage.updateCollections(list2, bl);
        ci.cancel();
    }
}
