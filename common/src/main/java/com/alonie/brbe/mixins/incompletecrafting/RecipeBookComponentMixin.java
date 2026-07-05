package com.alonie.brbe.mixins.incompletecrafting;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.mixins.accessors.RecipeCollectionAccessor;
import com.alonie.brbe.util.BrbeLogger;
import com.alonie.brbe.util.IncompatibleCraftingUtil;
import com.alonie.brbe.util.PartialCraftingUtil;
import com.alonie.brbe.util.PerfTimer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Handles partial-craftable marking, incompatible-recipe detection,
 * and sorting for the vanilla {@link RecipeBookComponent}.
 *
 * <p>Two redirects:
 * <ol>
 *   <li>{@code List.forEach} — intercepts vanilla data marking to
 *       inject partial-material detection</li>
 *   <li>{@code page.updateCollections} — re-sorts collections by
 *       craftable/partial status before the page renders them</li>
 * </ol>
 */
@Mixin(RecipeBookComponent.class)
public abstract class RecipeBookComponentMixin {

    @Shadow @Final protected RecipeBookMenu<?, ?> menu;

    @Shadow @Final protected Minecraft minecraft;

    @Unique
    private static long brbe$lastSlotHash;

    // ══════════════════════════════════════════════════════════
    // REDIRECT List.forEach — data marking
    // ══════════════════════════════════════════════════════════

    @Redirect(method = "updateCollections",
              at = @At(value = "INVOKE",
                       target = "Ljava/util/List;forEach(Ljava/util/function/Consumer;)V"))
    private void betterRecipeBook$injectIntoDataSets(
            List<RecipeCollection> collections,
            Consumer<? super RecipeCollection> vanillaConsumer) {

        PerfTimer.begin();

        long slotHash = PartialCraftingUtil.slotHash(menu.slots);
        // Force a full rebuild when populatePage() has run (config change,
        // reopen) — it can't call vanilla canCraft, so the craftable set
        // may be stale.  consumeForceFullRefresh() is atomic: read+clear.
        boolean forceRefresh = PartialCraftingUtil.consumeForceFullRefresh();
        boolean inventoryChanged = slotHash != brbe$lastSlotHash || forceRefresh;

        if (forceRefresh) {
            BrbeLogger.log(BrbeLogger.Category.PIPELINE,
                    "@Redirect forceRefresh consumed — forcing full rebuild, hashChanged=%s",
                    slotHash != brbe$lastSlotHash);
        }

        // Run vanilla forEach only when inventory changed
        if (inventoryChanged) {
            PerfTimer.start("vanilla.forEach");
            collections.forEach(vanillaConsumer);
            PerfTimer.end("vanilla.forEach");
            brbe$lastSlotHash = slotHash;
        }

        boolean onInventory = minecraft != null
                && minecraft.screen instanceof InventoryScreen;
        boolean retainPartial = BetterRecipeBook.ctx().config().partialMarkingEnabled;
        boolean retainIncompatible = onInventory
                && BetterRecipeBook.ctx().config().showAllRecipesInSurvival;

        // ── Cleanup path ──────────────────────────────────────────
        // When partialMarkingEnabled is OFF, previously-injected
        // partial recipes must be removed from the craftable set.
        // Uses *Raw methods that bypass the enabled() guard — regular
        // methods return false when the feature is disabled, which is
        // exactly when we need them to work hardest.
        if (!retainPartial) {
            for (RecipeCollection coll : collections) {
                if (!PartialCraftingUtil.hasPartialMaterialsRaw(coll)) continue;
                RecipeCollectionAccessor ca = (RecipeCollectionAccessor) coll;
                for (RecipeHolder<?> holder : coll.getRecipes()) {
                    if (PartialCraftingUtil.isPartiallyCraftableRaw(coll, holder.id())) {
                        ca.brbe$getCraftable().remove(holder);
                    }
                }
            }
            // Now that cleanup is done, clear the raw partial data so the
            // next frame doesn't re-inject stale entries.
            PartialCraftingUtil.invalidateCaches();
            if (!retainIncompatible) {
                PerfTimer.logAndReset("updateCollections (disabled+cleaned)");
                return;
            }
            // Fall through: retainIncompatible is true, do incompatible
            // marking below but skip partial work
        }

        if (!retainPartial && !retainIncompatible) {
            PerfTimer.logAndReset("updateCollections (no-op)");
            return;
        }

        // Step 1: Incompatible marking (always when on inventory screen)
        if (retainIncompatible) {
            PerfTimer.start("incompatible.mark");
            for (RecipeCollection collection : collections) {
                IncompatibleCraftingUtil.markIncompatibleRecipes(collection);
            }
            PerfTimer.end("incompatible.mark");
        }

        if (!retainPartial) {
            PerfTimer.logAndReset("updateCollections (incompatible-only)");
            return;
        }

        // Step 2: Skip partial marking if inventory hasn't changed
        if (!inventoryChanged) {
            PerfTimer.logAndReset("updateCollections (cache-hit, fully skipped)");
            return;
        }

        // Step 3: Clear previously-injected partial recipes
        PartialCraftingUtil.beginFilteringUpdate(true);
        Set<Item> inventoryItems = PartialCraftingUtil.hashInventory(menu.slots);

        PerfTimer.start("partial.step0-clear");
        for (RecipeCollection coll : collections) {
            if (!PartialCraftingUtil.hasPartialMaterials(coll)) continue;
            RecipeCollectionAccessor ca = (RecipeCollectionAccessor) coll;
            for (RecipeHolder<?> holder : coll.getRecipes()) {
                if (PartialCraftingUtil.isPartiallyCraftable(coll, holder.id())) {
                    ca.brbe$getCraftable().remove(holder);
                }
            }
        }
        PerfTimer.end("partial.step0-clear");

        // Step 4: Atomic partial marking + craftable-set injection
        PerfTimer.start("partial.markAndInject");
        int collCount = 0;
        for (RecipeCollection coll : collections) {
            collCount++;
            PartialCraftingUtil.markAndInject(coll, inventoryItems);
        }
        PerfTimer.end("partial.markAndInject");
        PartialCraftingUtil.beginFilteringUpdate(false);

        // Step 5: Root-cause cleanup — purge 3×3 recipes from the partial set
        // when showAllRecipesInSurvival is off.  markAndInject can be
        // over-aggressive, marking recipes that need a 3×3 grid.  Leaving them
        // in the craftable set causes "air placeholder" ghost slots on the
        // 2×2 inventory screen.
        if (!BetterRecipeBook.ctx().config().showAllRecipesInSurvival) {
            for (RecipeCollection coll : collections) {
                if (!PartialCraftingUtil.hasPartialMaterials(coll)) continue;
                RecipeCollectionAccessor ca = (RecipeCollectionAccessor) coll;
                for (RecipeHolder<?> holder : coll.getRecipes()) {
                    if (PartialCraftingUtil.isPartiallyCraftable(coll, holder.id())
                            && brbe$needsLargerGrid(holder)) {
                        ca.brbe$getCraftable().remove(holder);
                    }
                }
            }
        }

        PerfTimer.logAndReset("updateCollections (" + collCount + " coll)");
    }

    @Unique
    private static boolean brbe$needsLargerGrid(RecipeHolder<?> holder) {
        var recipe = holder.value();
        if (recipe instanceof net.minecraft.world.item.crafting.ShapedRecipe shaped) {
            return shaped.getWidth() > 2 || shaped.getHeight() > 2;
        }
        if (recipe instanceof net.minecraft.world.item.crafting.ShapelessRecipe shapeless) {
            return shapeless.getIngredients().size() > 4;
        }
        return false;
    }

    // ══════════════════════════════════════════════════════════
    // Sorting is handled by pipeline/RecipeBookComponentMixin
    // via @Redirect on page.updateCollections.
    // ══════════════════════════════════════════════════════════
}
