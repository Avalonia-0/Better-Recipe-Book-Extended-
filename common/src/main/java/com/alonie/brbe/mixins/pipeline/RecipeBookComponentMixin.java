package com.alonie.brbe.mixins.pipeline;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.mixins.accessors.RecipeBookComponentAccessor;
import com.alonie.brbe.mixins.accessors.RecipeBookPageAccessor;
import com.alonie.brbe.mixins.accessors.RecipeCollectionAccessor;
import com.alonie.brbe.util.BrbeLogger;
import com.alonie.brbe.util.CollectionCategory;
import com.alonie.brbe.util.PartialCraftingUtil;
import com.alonie.brbe.util.VanillaPipelineCollection;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookPage;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.world.inventory.RecipeBookMenu;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

@Mixin(RecipeBookComponent.class)
public abstract class RecipeBookComponentMixin implements RecipeBookComponentAccessor {

    @Shadow @Final protected Minecraft minecraft;
    @Shadow @Final protected RecipeBookMenu<?, ?> menu;
    @Shadow private ClientRecipeBook book;
    @Shadow @Final private RecipeBookPage recipeBookPage;

    // ═══════════════════════════════════════════════════════════════
    // Config-change refresh (HEAD of render)
    // ═══════════════════════════════════════════════════════════════

    @Inject(method = "render", at = @At("HEAD"))
    private void brbe$refreshOnConfigChange(GuiGraphics gui, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!BetterRecipeBook.configChanged) return;
        if (!this.getVisible()) return;

        BrbeLogger.log(BrbeLogger.Category.RENDER, "configChanged — full rebuild");

        // Reset vanilla filter.
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.getRecipeBook().setFiltering(
                    menu.getRecipeBookType(), false);
        }

        // Request that the next updateCollections call forces a full
        // rebuild (vanilla forEach + partial marking).  Must be set
        // BEFORE initVisuals because initVisuals calls updateTabs() →
        // updateCollections() internally, and the @Redirect needs to
        // see the flag during that call.
        PartialCraftingUtil.requestForceFullRefresh();

        // Rebuild UI.  The TAIL inject on initVisuals handles
        // populating the page (partial marking + craftable injection
        // + sort + page.updateCollections) — no other calls needed.
        this.initVisualsInvoker();
        BetterRecipeBook.configChanged = false;
    }

    // ═══════════════════════════════════════════════════════════════
    // Populate page after initVisuals (handles re-open + config)
    // ═══════════════════════════════════════════════════════════════

    @Inject(method = "initVisuals", at = @At("TAIL"))
    private void brbe$populateAfterInitVisuals(CallbackInfo ci) {
        BrbeLogger.log(BrbeLogger.Category.STATE, "initVisuals TAIL");
        populatePage(true);
    }

    /**
     * Sort collections, populate fitsDimensions, and push to the page.
     * Called from the TAIL of {@code initVisuals()} every time it runs.
     *
     * <p><b>Craftable-set management is handled by the
     * {@code @Redirect} in {@code incompletecrafting/RecipeBookComponentMixin}
     * which runs during {@code initVisuals → updateTabs → updateCollections}.</b>
     * This method only sorts and pushes — it does NOT touch the
     * craftable set or partial markings.  For config changes, the
     * render hook sets {@code requestForceFullRefresh()} before
     * {@code initVisuals()} so the Redirect forces a full rebuild.
     *
     * @param resetPage true when initVisuals is a fresh start (re-open,
     *                  config change); false for mid-session updates.
     */
    private void populatePage(boolean resetPage) {
        var tab = this.getSelectedTab();
        if (tab == null) return;

        List<RecipeCollection> collections =
                new ArrayList<>(book.getCollection(tab.getCategory()));

        if (collections.isEmpty()) return;

        BrbeLogger.log(BrbeLogger.Category.STATE,
                "populatePage — %d collections, reset=%s", collections.size(), resetPage);

        // Sort uses PARTIAL_RECIPES + craftable sets from the @Redirect
        // that already ran during initVisuals → updateTabs → updateCollections.
        sort(collections);

        // Ensure every collection has fitsDimensions populated.
        for (RecipeCollection c : collections) {
            var ca = (RecipeCollectionAccessor) c;
            if (ca.getFitsDimensions().isEmpty()) {
                ca.getFitsDimensions().addAll(c.getRecipes());
            }
        }

        BrbeLogger.log(BrbeLogger.Category.STATE,
                "populatePage — recipeBookPage=%s, calling updateCollections",
                recipeBookPage != null ? "ok" : "NULL");

        recipeBookPage.updateCollections(collections, resetPage);

        // Verify: check how many buttons the page actually has after.
        var buttons = ((RecipeBookPageAccessor)(Object) recipeBookPage).getButtons();
        int visible = 0;
        for (var b : buttons) {
            if (b.visible) visible++;
        }
        BrbeLogger.log(BrbeLogger.Category.STATE,
                "populatePage — after push: %d visible buttons", visible);
    }

    // ═══════════════════════════════════════════════════════════════
    // @ModifyArg (normal path)
    // ═══════════════════════════════════════════════════════════════

    @ModifyArg(method = "updateCollections",
               at = @At(value = "INVOKE",
                        target = "Lnet/minecraft/client/gui/screens/recipebook/RecipeBookPage;updateCollections(Ljava/util/List;Z)V"),
               index = 0)
    private List<RecipeCollection> brbe$applySortPipeline(List<RecipeCollection> collections) {
        sort(collections);
        return collections;
    }

    // ═══════════════════════════════════════════════════════════════
    // Sort
    // ═══════════════════════════════════════════════════════════════

    private void sort(List<RecipeCollection> collections) {
        if (collections == null || collections.isEmpty()) return;

        if (BetterRecipeBook.config.enablePinning) {
            List<RecipeCollection> snap = new ArrayList<>(collections);
            for (RecipeCollection c : snap) {
                if (BetterRecipeBook.pinnedRecipeManager.has(VanillaPipelineCollection.of(c))) {
                    collections.remove(c);
                    collections.add(0, c);
                }
            }
        }

        boolean brbe = BetterRecipeBook.config.partialCraftingEnabled;
        boolean vanilla = minecraft != null && minecraft.player != null
                && minecraft.player.getRecipeBook().isFiltering(menu);
        if (!brbe && !vanilla) return;

        boolean hasP = BetterRecipeBook.config.partialMarkingEnabled;
        List<RecipeCollection> pC = new ArrayList<>(), pP = new ArrayList<>(), pU = new ArrayList<>();
        List<RecipeCollection> uC = new ArrayList<>(), uP = new ArrayList<>(), uU = new ArrayList<>();

        for (RecipeCollection c : collections) {
            boolean pin = BetterRecipeBook.config.enablePinning
                    && BetterRecipeBook.pinnedRecipeManager.has(VanillaPipelineCollection.of(c));
            if (hasP) {
                switch (PartialCraftingUtil.categorize(c)) {
                    case TRULY_CRAFTABLE -> { if (pin) pC.add(c); else uC.add(c); }
                    case PARTIAL         -> { if (pin) pP.add(c); else uP.add(c); }
                    case UNASSIGNED      -> { if (pin) pU.add(c); else uU.add(c); }
                }
            } else {
                if (c.hasCraftable()) { if (pin) pC.add(c); else uC.add(c); }
                else                 { if (pin) pU.add(c); else uU.add(c); }
            }
        }

        collections.clear();
        collections.addAll(pC); collections.addAll(pP); collections.addAll(pU);
        collections.addAll(uC); collections.addAll(uP); collections.addAll(uU);
    }
}
