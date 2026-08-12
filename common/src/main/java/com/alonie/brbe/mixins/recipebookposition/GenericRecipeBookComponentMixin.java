package com.alonie.brbe.mixins.recipebookposition;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.api.BRBBookCategories;
import com.alonie.brbe.generic.BRBGroupButtonWidget;
import com.alonie.brbe.generic.GenericRecipeBookComponent;
import com.alonie.brbe.generic.GenericRecipePage;
import com.alonie.brbe.mixins.accessors.GenericRecipePageAccessor;
import com.alonie.brbe.util.RecipeBookPositionMemory;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Remembers the BRBE self-built recipe books' (brewing stand, smithing table)
 * last tab and page (in memory), restoring it on the next open.
 */
@Mixin(GenericRecipeBookComponent.class)
public abstract class GenericRecipeBookComponentMixin {

    @Shadow
    protected java.util.List<BRBGroupButtonWidget> tabButtons;

    @Shadow
    protected abstract void updateCollections(boolean resetCurrentPage);

    /** Remember the current tab + page whenever the book is visible. */
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void brbe$rememberPosition(GuiGraphicsExtractor gui, int mouseX, int mouseY,
                                       float delta, CallbackInfo ci) {
        GenericRecipeBookComponent<?, ?, ?> self = (GenericRecipeBookComponent<?, ?, ?>) (Object) this;
        if (!BetterRecipeBook.config.saveRecipeBookPosition || !self.isVisible()) return;
        if (self.selectedTab == null) return;
        RecipeBookPositionMemory.save(self.getRecipeBookType().toString(),
                categoryId(self.selectedTab.getCategory()),
                ((GenericRecipePageAccessor) self.recipesPage).getCurrentPage());
    }

    /** Restore the remembered tab + page after the book's visuals are rebuilt. */
    @Inject(method = "initVisuals", at = @At("TAIL"))
    private void brbe$restorePosition(CallbackInfo ci) {
        GenericRecipeBookComponent<?, ?, ?> self = (GenericRecipeBookComponent<?, ?, ?>)(Object) this;
        if (!BetterRecipeBook.config.saveRecipeBookPosition) return;
        RecipeBookPositionMemory.Pos pos =
                RecipeBookPositionMemory.load(self.getRecipeBookType().toString());
        if (pos == null) return;

        BRBGroupButtonWidget target = null;
        for (BRBGroupButtonWidget button : this.tabButtons) {
            if (categoryId(button.getCategory()).equals(pos.category())) {
                target = button;
                break;
            }
        }
        if (target == null) return;

        // Highlight exactly one tab: the restored one selected, all others
        // deselected (initVisuals already triggered a default tab beforehand).
        self.selectedTab = target;
        for (BRBGroupButtonWidget button : this.tabButtons) {
            button.setStateTriggered(button == target);
        }
        this.updateCollections(false);

        GenericRecipePage<?, ?, ?> page = self.recipesPage;
        int max = Math.max(0, ((GenericRecipePageAccessor) page).getTotalPages() - 1);
        ((GenericRecipePageAccessor) page).setCurrentPage(Math.min(pos.page(), max));
        // 恢复页码是程序行为，不应触发翻页动画；同步视觉页到当前页
        page.resetVisualPosition();
        page.updateButtonsForPage();
    }

    @Unique
    private static String categoryId(BRBBookCategories.Category category) {
        String type = category.getType().name();
        String item = "";
        java.util.List<ItemStack> icons = category.getItemIcons();
        if (!icons.isEmpty()) {
            item = BuiltInRegistries.ITEM.getKey(icons.get(0).getItem()).toString();
        }
        return type + "|" + item;
    }
}
