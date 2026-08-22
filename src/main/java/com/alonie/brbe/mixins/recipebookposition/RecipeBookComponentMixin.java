package com.alonie.brbe.mixins.recipebookposition;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.mixins.accessors.RecipeBookComponentAccessor;
import com.alonie.brbe.mixins.accessors.RecipeBookPageAccessor;
import com.alonie.recipebookispain_extended.access.RecipeBookScrollAccess;
import com.alonie.brbe.util.RecipeBookPositionMemory;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
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
 * Remembers the vanilla recipe book's last tab and page (in memory) so the next
 * time the book opens it returns there instead of the first tab/page.  Applied
 * to the vanilla {@link RecipeBookComponent} (crafting table / furnace / smoker /
 * blast furnace screens).  Keyed by the container's recipe book type + menu
 * class so each screen kind keeps its own position.
 *
 * <p>每个标签页单独保存自己的配方区页码（{@link RecipeBookPositionMemory} 以标签为子键）：
 * 切换到其他标签再切回来，会恢复该标签上一次的配方区页码；重新打开配方书则恢复最近
 * 激活的标签、配方区页码及 RBIP 创造标签栏页码。搜索词保持全局共享，切换标签不会改动
 * 搜索框内容。RBIP 创造标签栏页码只随翻页操作与重开配方书变化，点击标签不会翻页。</p>
 */
@Mixin(RecipeBookComponent.class)
public abstract class RecipeBookComponentMixin {

    @SuppressWarnings("rawtypes")
    @Shadow
    protected RecipeBookMenu menu;

    @Shadow
    private String lastSearch;

    /** Remember the current tab + page + search whenever the book is visible. */
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void brbe$rememberPosition(GuiGraphicsExtractor gui, int mouseX, int mouseY,
                                       float delta, CallbackInfo ci) {
        RecipeBookComponent<?> self = (RecipeBookComponent<?>) (Object) this;
        if (!BetterRecipeBook.config.saveRecipeBookPosition || !self.isVisible()) return;
        RecipeBookComponentAccessor acc = (RecipeBookComponentAccessor) self;
        RecipeBookTabButton tab = acc.getSelectedTab();
        if (tab == null) return;
        int tabIndex = acc.getTabButtons().indexOf(tab);
        if (tabIndex < 0) return;
        RecipeBookPage page = acc.getRecipeBookPage();
        int tabPage = (self instanceof RecipeBookScrollAccess sa) ? sa.rbip$getPage() : -1;
        RecipeBookPositionMemory.save(bookKey(),
                tabIndex,
                ((RecipeBookPageAccessor) page).getCurrentPage(),
                tabPage,
                acc.getSearchBox() != null ? acc.getSearchBox().getValue() : "");
    }

    /** Restore the remembered tab + page + search after the book's visuals are rebuilt. */
    @Inject(method = "initVisuals", at = @At("TAIL"))
    private void brbe$restorePosition(CallbackInfo ci) {
        RecipeBookComponent<?> self = (RecipeBookComponent<?>) (Object) this;
        if (!BetterRecipeBook.config.saveRecipeBookPosition) return;
        int tabIndex = RecipeBookPositionMemory.activeTabIndex(bookKey());
        if (tabIndex < 0) return;
        RecipeBookPositionMemory.Pos pos = RecipeBookPositionMemory.load(bookKey(), tabIndex);
        if (pos == null) return;
        RecipeBookComponentAccessor acc = (RecipeBookComponentAccessor) self;

        java.util.List<RecipeBookTabButton> tabs = acc.getTabButtons();
        if (tabs.isEmpty()) return;
        int index = Math.min(Math.max(tabIndex, 0), tabs.size() - 1);
        RecipeBookTabButton target = tabs.get(index);
        if (target == null) return;

        // Restore search before any collection rebuild so the remembered page
        // stays meaningful (search box was recreated empty by initVisuals).
        acc.getSearchBox().setValue(pos.search());

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

    /**
     * 切换标签时恢复目标标签自己记住的配方区页码。
     * 注入 {@code onTabButtonPress} 尾部：此时 {@code updateCollections(true)}
     * 已用新标签重建列表，totalPages 已定稿，直接钳制并设置页码即可。
     *
     * <p>注意：<b>不</b>恢复 RBIP 创造标签栏页码。被点击的标签必然在当前页可见，
     * 恢复它记住的旧页码会把标签栏翻到别页、甚至隐藏刚点击的标签（记忆在标签
     * 选中期间每帧跟随当前页码，翻页后再点回该标签就会翻走）。标签栏页码只由
     * 翻页按钮/滚轮（用户意图）与重开配方书（{@link #brbe$restorePosition}）改变。</p>
     */
    @Inject(method = "onTabButtonPress", at = @At("TAIL"))
    private void brbe$restoreTabPosition(Button button, CallbackInfo ci) {
        RecipeBookComponent<?> self = (RecipeBookComponent<?>) (Object) this;
        if (!BetterRecipeBook.config.saveRecipeBookPosition) return;
        if (button == null) return;
        RecipeBookComponentAccessor acc = (RecipeBookComponentAccessor) self;
        int tabIndex = acc.getTabButtons().indexOf(button);
        if (tabIndex < 0) return;
        RecipeBookPositionMemory.Pos pos = RecipeBookPositionMemory.load(bookKey(), tabIndex);
        if (pos == null) return;
        // 不恢复 RBIP 创造标签栏页码：原因见方法 javadoc。tabPage 记忆仍保留，
        // 供重开配方书时（brbe$restorePosition）恢复标签栏页码使用。
        RecipeBookPage page = acc.getRecipeBookPage();
        RecipeBookPageAccessor pageAcc = (RecipeBookPageAccessor) page;
        int max = Math.max(0, pageAcc.getTotalPages() - 1);
        pageAcc.setCurrentPage(Math.min(pos.page(), max));
        pageAcc.updateButtonsForPageInvoker();
    }

    /**
     * 搜索栏变化时的页码策略（"保存浏览记录"功能）：
     * <ul>
     *   <li><b>首次输入搜索词</b>（空 → 非空）：回到第 1 页，从结果开头看；</li>
     *   <li><b>清空搜索</b>（非空 → 空）：恢复搜索前浏览的页码（basePage）；</li>
     *   <li>搜索词继续修改（非空 → 非空）：保持原版行为，不干预。</li>
     * </ul>
     *
     * <p>HEAD 捕获 {@code lastSearch}（上一次处理的搜索词）而非搜索框当前值：
     * 退格键逐字删除时，最后一次按键进入方法时搜索框已是空值，只有
     * {@code lastSearch} 还保留着删除前的词，能可靠判定"刚被清空"
     * （同帧内输入+清空同样覆盖）。</p>
     */
    @Unique
    private String brbe$lastSearchAtHead;

    @Inject(method = "checkSearchStringUpdate", at = @At("HEAD"))
    private void brbe$captureSearchText(CallbackInfo ci) {
        this.brbe$lastSearchAtHead = this.lastSearch;
    }

    @Inject(method = "checkSearchStringUpdate", at = @At("TAIL"))
    private void brbe$handleSearchChange(CallbackInfo ci) {
        RecipeBookComponent<?> self = (RecipeBookComponent<?>) (Object) this;
        if (!BetterRecipeBook.config.saveRecipeBookPosition) return;
        RecipeBookComponentAccessor acc = (RecipeBookComponentAccessor) self;
        String now = acc.getSearchBox() != null ? acc.getSearchBox().getValue() : "";
        String old = this.brbe$lastSearchAtHead;
        if (now.isEmpty()) {
            // 清空搜索：恢复搜索前浏览的页码（仅当搜索词确实从非空变为空）
            if (old == null || old.isEmpty()) return;
            RecipeBookTabButton tab = acc.getSelectedTab();
            if (tab == null) return;
            int tabIndex = acc.getTabButtons().indexOf(tab);
            if (tabIndex < 0) return;
            RecipeBookPositionMemory.Pos pos = RecipeBookPositionMemory.load(bookKey(), tabIndex);
            if (pos == null) return;
            RecipeBookPage page = acc.getRecipeBookPage();
            RecipeBookPageAccessor pageAcc = (RecipeBookPageAccessor) page;
            int max = Math.max(0, pageAcc.getTotalPages() - 1);
            pageAcc.setCurrentPage(Math.min(pos.basePage(), max));
            pageAcc.updateButtonsForPageInvoker();
        } else if (old == null || old.isEmpty()) {
            // 首次输入搜索词：回到第 1 页，从结果开头看
            RecipeBookPage page = acc.getRecipeBookPage();
            RecipeBookPageAccessor pageAcc = (RecipeBookPageAccessor) page;
            pageAcc.setCurrentPage(0);
            pageAcc.updateButtonsForPageInvoker();
        }
    }

    @Unique
    private String bookKey() {
        String type = menu != null ? menu.getRecipeBookType().name() : "";
        String screen = menu != null ? menu.getClass().getSimpleName() : "";
        return "vanilla:" + type + ":" + screen;
    }
}
