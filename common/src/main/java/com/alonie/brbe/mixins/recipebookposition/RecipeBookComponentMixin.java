package com.alonie.brbe.mixins.recipebookposition;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.mixins.accessors.RecipeBookComponentAccessor;
import com.alonie.brbe.mixins.accessors.RecipeBookPageAccessor;
import com.alonie.brbe.util.RecipeBookPositionMemory;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookPage;
import net.minecraft.client.gui.screens.recipebook.RecipeBookTabButton;
import net.minecraft.world.inventory.RecipeBookMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 1.21.1 简化版配方书位置记忆：记住最后激活的标签 + 配方区页码 + 搜索词；
 * 重开配方书时恢复。每个标签页的页码单独保存（RecipeBookPositionMemory 以
 * 标签为子键），切回标签时恢复其页码。
 *
 * <p>对照 1.21.11 的 recipebookposition/RecipeBookComponentMixin：本版不含
 * RBIP 创造标签栏页码（RecipeBookScrollAccess 1.21.1 无）与搜索变更页码策略
 * （checkSearchStringUpdate 的 HEAD/TAIL 注入——1.21.1 的 search mixin 已有
 * search 处理，避免冲突）。</p>
 */
@Mixin(RecipeBookComponent.class)
public abstract class RecipeBookComponentMixin {

    @SuppressWarnings("rawtypes")
    @Shadow
    protected RecipeBookMenu menu;

    /** Remember the current tab + page + search whenever the book is visible. */
    @Inject(method = "render", at = @At("TAIL"))
    private void brbe$rememberPosition(GuiGraphics gui, int mouseX, int mouseY,
                                       float delta, CallbackInfo ci) {
        RecipeBookComponent self = (RecipeBookComponent) (Object) this;
        if (BetterRecipeBook.config == null
                || !BetterRecipeBook.config.saveRecipeBookPosition
                || !self.isVisible()) return;
        RecipeBookComponentAccessor acc = (RecipeBookComponentAccessor) self;
        RecipeBookTabButton tab = acc.getSelectedTab();
        if (tab == null) return;
        // 按类别名保存/恢复（稳定，不受 RBIP 标签重排影响——见
        // RecipeBookPositionMemory 契约）。下拉下标方案会在保存/恢复之间
        // 因 RBIP 重排/分页漂移，导致恢复错标签。
        String category = tab.getCategory().name();
        if (category == null || category.isEmpty()) return;
        RecipeBookPage page = acc.getRecipeBookPage();
        RecipeBookPositionMemory.save(bookKey(),
                category,
                ((RecipeBookPageAccessor) page).getCurrentPage(),
                -1, // 无 RBIP 标签栏页码（1.21.1 简化版）
                acc.getSearchBox() != null ? acc.getSearchBox().getValue() : "");
    }

    /** Restore the remembered tab + page + search after the book's visuals are rebuilt. */
    @Inject(method = "initVisuals", at = @At("TAIL"))
    private void brbe$restorePosition(CallbackInfo ci) {
        RecipeBookComponent self = (RecipeBookComponent) (Object) this;
        if (BetterRecipeBook.config == null
                || !BetterRecipeBook.config.saveRecipeBookPosition) return;
        String category = RecipeBookPositionMemory.activeTabCategory(bookKey());
        if (category == null) return;
        RecipeBookPositionMemory.Pos pos = RecipeBookPositionMemory.load(bookKey(), category);
        if (pos == null) return;
        RecipeBookComponentAccessor acc = (RecipeBookComponentAccessor) self;

        java.util.List<RecipeBookTabButton> tabs = acc.getTabButtons();
        if (tabs.isEmpty()) return;
        // 按类别名精确定位标签（在 RBIP 重排后的列表中）；找不到则回退首标签，
        // 不再按下标猜（下标在重排后会指向错误标签）。
        RecipeBookTabButton target = null;
        for (RecipeBookTabButton t : tabs) {
            if (t.getCategory().name().equals(category)) { target = t; break; }
        }
        if (target == null) target = tabs.get(0);
        if (target == null) return;

        // Restore search first so the remembered page stays meaningful
        if (pos.search() != null && acc.getSearchBox() != null) {
            acc.getSearchBox().setValue(pos.search());
        }

        RecipeBookTabButton old = acc.getSelectedTab();
        if (old != null && old != target) {
            old.setStateTriggered(false);
        }
        target.setStateTriggered(true);
        acc.setSelectedTab(target);
        acc.updateTabsInvoker();

        // 关键：恢复选中标签后必须重新填充页面集合。initVisuals 里 vanilla 只为
        // 默认标签（搜索标签）填了 recipeBookPage.recipeCollections；若不重建，
        // 这里设置的页码作用在旧（默认）标签的集合上 → 跳到错误的类别页面。
        // 镜像 1.21.11：setSelectedTab 后调 updateCollections(true)，用恢复标签的
        // 类别重新生成集合（会重跑 incompletecrafting 的 @Redirect forEach，
        // 幂等无副作用）。
        acc.updateCollectionsInvoker(true);

        RecipeBookPage page = acc.getRecipeBookPage();
        RecipeBookPageAccessor pageAcc = (RecipeBookPageAccessor) page;
        int max = Math.max(0, pageAcc.getTotalPages() - 1);
        pageAcc.setCurrentPage(Math.min(pos.page(), max));
        pageAcc.updateButtonsForPageInvoker();
    }

    @Unique
    private String bookKey() {
        String type = menu != null ? menu.getRecipeBookType().name() : "";
        String screen = menu != null ? menu.getClass().getSimpleName() : "";
        return "vanilla:" + type + ":" + screen;
    }
}
