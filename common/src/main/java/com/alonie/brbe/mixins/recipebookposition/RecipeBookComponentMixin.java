package com.alonie.brbe.mixins.recipebookposition;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.mixins.accessors.RecipeBookComponentAccessor;
import com.alonie.brbe.mixins.accessors.RecipeBookPageAccessor;
import com.alonie.recipebookispain_extended.access.RecipeBookScrollAccess;
import com.alonie.brbe.util.RecipeBookPositionMemory;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookPage;
import net.minecraft.client.gui.screens.recipebook.RecipeBookTabButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Remembers the vanilla recipe book's last tab and page (in memory) so the next
 * time the book opens it returns there instead of the first tab/page.  Applied
 * to the vanilla {@link RecipeBookComponent} (crafting table / furnace / smoker /
 * blast furnace screens).  Keyed by concrete class so each screen kind keeps its
 * own position.
 */
@Mixin(RecipeBookComponent.class)
public abstract class RecipeBookComponentMixin {

    /** Remember the current tab + page whenever the book is visible. */
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void brbe$rememberPosition(GuiGraphicsExtractor gui, int mouseX, int mouseY,
                                       float delta, CallbackInfo ci) {
        RecipeBookComponent<?> self = (RecipeBookComponent<?>) (Object) this;
        if (!BetterRecipeBook.config.saveRecipeBookPosition || !self.isVisible()) return;
        RecipeBookComponentAccessor acc = (RecipeBookComponentAccessor) self;
        RecipeBookTabButton tab = acc.getSelectedTab();
        if (tab == null) return;
        RecipeBookPage page = acc.getRecipeBookPage();
        int tabPage = (self instanceof RecipeBookScrollAccess sa) ? sa.rbip$getPage() : -1;
        RecipeBookPositionMemory.save(bookKey(self),
                tab.getCategory().toString(),
                ((RecipeBookPageAccessor) page).getCurrentPage(),
                tabPage);
    }

    /** Restore the remembered tab + page after the book's visuals are rebuilt. */
    @Inject(method = "initVisuals", at = @At("TAIL"))
    private void brbe$restorePosition(CallbackInfo ci) {
        RecipeBookComponent<?> self = (RecipeBookComponent<?>) (Object) this;
        if (!BetterRecipeBook.config.saveRecipeBookPosition) return;
        RecipeBookPositionMemory.Pos pos = RecipeBookPositionMemory.load(bookKey(self));
        if (pos == null) return;
        RecipeBookComponentAccessor acc = (RecipeBookComponentAccessor) self;

        RecipeBookTabButton target = null;
        for (RecipeBookTabButton tab : acc.getTabButtons()) {
            if (tab.getCategory().toString().equals(pos.category())) {
                target = tab;
                break;
            }
        }
        if (target == null) return;

        // Mirror the vanilla replaceSelected chain: unselect the old tab and
        // select the new one so exactly one tab stays highlighted (initVisuals
        // already selected a default tab — the search tab — before this hook).
        RecipeBookTabButton old = acc.getSelectedTab();
        if (old != null && old != target) {
            old.unselect();
        }
        target.select();
        acc.setSelectedTab(target);
        acc.updateTabsInvoker(acc.isFilteringInvoker());
        // RBIP creative-tab paging: jump back to the remembered tab page
        // (updateTabs above re-laid the tabs; override the page with the
        // remembered one and re-slice).
        if (self instanceof RecipeBookScrollAccess sa && pos.tabPage() >= 0) {
            sa.rbip$setPage(pos.tabPage());
        }
        acc.updateCollectionsInvoker(true, acc.isFilteringInvoker());

        RecipeBookPage page = acc.getRecipeBookPage();
        RecipeBookPageAccessor pageAcc = (RecipeBookPageAccessor) page;
        int max = Math.max(0, pageAcc.getTotalPages() - 1);
        pageAcc.setCurrentPage(Math.min(pos.page(), max));
        pageAcc.updateButtonsForPageInvoker();
    }

    @Unique
    private static String bookKey(RecipeBookComponent<?> self) {
        return "vanilla:" + self.getClass().getSimpleName();
    }
}
