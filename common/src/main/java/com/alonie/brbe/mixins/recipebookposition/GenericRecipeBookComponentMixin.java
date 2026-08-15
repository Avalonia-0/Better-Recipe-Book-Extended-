package com.alonie.brbe.mixins.recipebookposition;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.generic.BRBGroupButtonWidget;
import com.alonie.brbe.generic.GenericRecipeBookComponent;
import com.alonie.brbe.generic.GenericRecipePage;
import com.alonie.brbe.mixins.accessors.GenericRecipePageAccessor;
import com.alonie.brbe.util.RecipeBookPositionMemory;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Remembers the BRBE self-built recipe books' (brewing stand, smithing table)
 * last tab, page and search (in memory), restoring it on the next open.
 *
 * <p>The remembered page is applied lazily on the first visible frame: the
 * component's {@code doubleRefresh} flag triggers an {@code updateCollections(true)}
 * at the start of {@code extractRenderState} which resets the current page to 0,
 * so an immediate restore inside {@code initVisuals} would be wiped.</p>
 */
@Mixin(GenericRecipeBookComponent.class)
public abstract class GenericRecipeBookComponentMixin {

    @Shadow
    protected java.util.List<BRBGroupButtonWidget> tabButtons;

    @Shadow
    protected EditBox searchBox;

    @Shadow
    protected abstract void updateCollections(boolean resetCurrentPage);

    @Unique
    private int brbe$pendingPage = -1;

    /** Apply a deferred page restore, then remember the current state. */
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void brbe$rememberPosition(GuiGraphicsExtractor gui, int mouseX, int mouseY,
                                       float delta, CallbackInfo ci) {
        GenericRecipeBookComponent<?, ?, ?> self = (GenericRecipeBookComponent<?, ?, ?>) (Object) this;
        // doubleRefresh already refreshed the list at the top of this method,
        // so totalPages is final here — safe to apply the deferred restore.
        if (brbe$pendingPage >= 0) {
            GenericRecipePage<?, ?, ?> page = self.recipesPage;
            int max = Math.max(0, ((GenericRecipePageAccessor) page).getTotalPages() - 1);
            ((GenericRecipePageAccessor) page).setCurrentPage(Math.min(brbe$pendingPage, max));
            // 恢复页码是程序行为，不应触发翻页动画；同步视觉页到当前页
            page.resetVisualPosition();
            page.updateButtonsForPage();
            brbe$pendingPage = -1;
        }

        if (!BetterRecipeBook.config.saveRecipeBookPosition || !self.isVisible()) return;
        if (self.selectedTab == null) return;
        int tabIndex = this.tabButtons.indexOf(self.selectedTab);
        if (tabIndex < 0) return;
        RecipeBookPositionMemory.save(bookKey(),
                tabIndex,
                ((GenericRecipePageAccessor) self.recipesPage).getCurrentPage(),
                -1,
                this.searchBox != null ? this.searchBox.getValue() : "");
    }

    /** Restore the remembered tab + search, deferring the page to the first frame. */
    @Inject(method = "initVisuals", at = @At("TAIL"))
    private void brbe$restorePosition(CallbackInfo ci) {
        GenericRecipeBookComponent<?, ?, ?> self = (GenericRecipeBookComponent<?, ?, ?>)(Object) this;
        if (!BetterRecipeBook.config.saveRecipeBookPosition) return;
        RecipeBookPositionMemory.Pos pos = RecipeBookPositionMemory.load(bookKey());
        if (pos == null) return;

        if (this.tabButtons.isEmpty()) return;
        int index = Math.min(Math.max(pos.tabIndex(), 0), this.tabButtons.size() - 1);
        BRBGroupButtonWidget target = this.tabButtons.get(index);
        if (target == null) return;

        // Restore search before the collection rebuild below reads searchBox.
        if (this.searchBox != null) this.searchBox.setValue(pos.search());

        // Highlight exactly one tab: the restored one selected, all others
        // deselected (initVisuals already triggered a default tab beforehand).
        self.selectedTab = target;
        for (BRBGroupButtonWidget button : this.tabButtons) {
            button.setStateTriggered(button == target);
        }
        this.updateCollections(false);

        // Deferred: applied on the first visible frame after doubleRefresh.
        this.brbe$pendingPage = pos.page();
    }

    @Unique
    private String bookKey() {
        GenericRecipeBookComponent<?, ?, ?> self = (GenericRecipeBookComponent<?, ?, ?>)(Object) this;
        return self.getRecipeBookType().Identifier.toString();
    }
}
