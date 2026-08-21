package com.alonie.brbe.mixins.recipebookposition;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.generic.BRBGroupButtonWidget;
import com.alonie.brbe.generic.GenericRecipeBookComponent;
import com.alonie.brbe.generic.GenericRecipePage;
import com.alonie.brbe.mixins.accessors.GenericRecipePageAccessor;
import com.alonie.brbe.util.RecipeBookPositionMemory;
import net.minecraft.client.gui.GuiGraphics;
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
 * <p>每个标签页单独保存自己的页码（{@link RecipeBookPositionMemory} 以标签为子键）：
 * 切换到其他标签再切回来，会恢复该标签上一次的页码；重新打开配方书则恢复最近
 * 激活的标签及其页码。搜索词保持全局共享，切换标签不会改动搜索框内容。</p>
 *
 * <p>The remembered page is applied lazily on the first visible frame: the
 * component's {@code doubleRefresh} flag triggers an {@code updateCollections(true)}
 * at the start of {@code render} which resets the current page to 0,
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

    /** 上一帧的选中标签：用于在 updateCollections(true) 时区分"标签切换"与其他重置刷新。 */
    @Unique
    private BRBGroupButtonWidget brbe$lastFrameTab;

    /** 标签切换标记：本次 updateCollections 完成后恢复目标标签的页码。 */
    @Unique
    private boolean brbe$pendingTabRestore;

    /** Apply a deferred page restore, then remember the current state. */
    @Inject(method = "render", at = @At("TAIL"))
    private void brbe$rememberPosition(GuiGraphics gui, int mouseX, int mouseY,
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
        int tabIndex = RecipeBookPositionMemory.activeTabIndex(bookKey());
        if (tabIndex < 0) return;
        RecipeBookPositionMemory.Pos pos = RecipeBookPositionMemory.load(bookKey(), tabIndex);
        if (pos == null) return;

        if (this.tabButtons.isEmpty()) return;
        int index = Math.min(Math.max(tabIndex, 0), this.tabButtons.size() - 1);
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

        // 重开/重布局后标签是新实例：同步基准，避免被误判为"标签切换"
        this.brbe$lastFrameTab = target;

        // Deferred: applied on the first visible frame after doubleRefresh.
        this.brbe$pendingPage = pos.page();
    }

    /**
     * 标签切换检测：{@code updateCollections(true)} 且选中标签发生变化即为
     * 用户切换标签（区别于开书 doubleRefresh、右键清空搜索、页码跳转命令等
     * 同样走 reset 路径的刷新）。标记后在本次刷新完成时恢复目标标签的页码。
     */
    @Inject(method = "updateCollections", at = @At("HEAD"))
    private void brbe$detectTabSwitch(boolean resetCurrentPage, CallbackInfo ci) {
        if (!resetCurrentPage) return;
        GenericRecipeBookComponent<?, ?, ?> self = (GenericRecipeBookComponent<?, ?, ?>)(Object) this;
        if (this.brbe$lastFrameTab == self.selectedTab) return;
        this.brbe$lastFrameTab = self.selectedTab;
        this.brbe$pendingTabRestore = true;
    }

    /** 标签切换完成：恢复目标标签自己记住的页码（此时 totalPages 已定稿）。 */
    @Inject(method = "updateCollections", at = @At("TAIL"))
    private void brbe$applyTabRestore(boolean resetCurrentPage, CallbackInfo ci) {
        if (!this.brbe$pendingTabRestore) return;
        this.brbe$pendingTabRestore = false;
        if (!BetterRecipeBook.config.saveRecipeBookPosition) return;
        GenericRecipeBookComponent<?, ?, ?> self = (GenericRecipeBookComponent<?, ?, ?>)(Object) this;
        if (self.selectedTab == null) return;
        int tabIndex = this.tabButtons.indexOf(self.selectedTab);
        if (tabIndex < 0) return;
        RecipeBookPositionMemory.Pos pos = RecipeBookPositionMemory.load(bookKey(), tabIndex);
        if (pos == null) return;
        GenericRecipePage<?, ?, ?> page = self.recipesPage;
        int max = Math.max(0, ((GenericRecipePageAccessor) page).getTotalPages() - 1);
        ((GenericRecipePageAccessor) page).setCurrentPage(Math.min(pos.page(), max));
        // 恢复页码是程序行为，不应触发翻页动画；同步视觉页到当前页
        page.resetVisualPosition();
        page.updateButtonsForPage();
    }

    @Unique
    private String bookKey() {
        GenericRecipeBookComponent<?, ?, ?> self = (GenericRecipeBookComponent<?, ?, ?>)(Object) this;
        return self.getRecipeBookType().Identifier.toString();
    }
}
