package com.alonie.brbe.mixins.incompletecrafting;

import com.alonie.brbe.pipeline.UpdateCollectionsPipeline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.world.inventory.RecipeBookMenu;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.function.Consumer;

/**
 * Thin adapter that bridges vanilla {@code RecipeBookComponent.updateCollections}
 * into {@link UpdateCollectionsPipeline}.
 *
 * <p>The pipeline owns all data-processing logic (caching, RBIP filtering,
 * incremental forEach, partial/incompatible marking, search, sorting, page
 * update).  This mixin only captures parameters from the vanilla method and
 * forwards them to the pipeline.
 */
@Mixin(RecipeBookComponent.class)
public abstract class RecipeBookComponentMixin {

    @Shadow @Final
    protected RecipeBookMenu<?, ?> menu;

    @Shadow @Final
    protected Minecraft minecraft;

    @Unique
    private static boolean brbe$capturedResetPage;

    /** Capture the resetPage parameter so the redirect can forward it. */
    @Inject(method = "updateCollections", at = @At("HEAD"))
    private void betterRecipeBook$captureResetPage(boolean resetPage, CallbackInfo ci) {
        brbe$capturedResetPage = resetPage;
    }

    /**
     * Intercepts the vanilla {@code List.forEach(Consumer)} call inside
     * {@code updateCollections}.  This is the point where the vanilla Consumer
     * (which calls {@code canCraft()} on each collection) is available.
     *
     * <p>We capture it, run the full pipeline, then let vanilla continue
     * with the original list.  The pipeline already called
     * {@code page.updateCollections} with the processed results, and the
     * downstream page-update redirect is a no-op — so vanilla's filter/sort
     * work on the list harmlessly.
     */
    @Redirect(method = "updateCollections",
            at = @At(value = "INVOKE",
                     target = "Ljava/util/List;forEach(Ljava/util/function/Consumer;)V"))
    private void betterRecipeBook$runPipeline(
            List<RecipeCollection> collections, Consumer<? super RecipeCollection> consumer) {

        UpdateCollectionsPipeline.run(
                (RecipeBookComponent) (Object) this,
                this.menu, this.minecraft, collections,
                brbe$capturedResetPage, consumer);

        // Do NOT clear or mutate collections — vanilla's downstream code
        // (filter, sort, page.updateCollections) continues with the
        // original list, but the page-update redirect is a no-op so the
        // pipeline's results are preserved.
    }

    /**
     * No-op: the pipeline already called {@code page.updateCollections}.
     * We skip vanilla's call to avoid overwriting pipeline results.
     */
    @Redirect(method = "updateCollections",
            at = @At(value = "INVOKE",
                     target = "Lnet/minecraft/client/gui/screens/recipebook/RecipeBookPage;updateCollections(Ljava/util/List;Z)V"))
    private void betterRecipeBook$skipPageUpdate(
            net.minecraft.client.gui.screens.recipebook.RecipeBookPage page,
            List<RecipeCollection> list, boolean resetPageNumber) {
        // Pipeline already handled this — nothing to do.
    }
}
