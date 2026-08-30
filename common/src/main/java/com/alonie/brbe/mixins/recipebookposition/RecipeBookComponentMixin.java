package com.alonie.brbe.mixins.recipebookposition;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.mixins.accessors.RecipeBookComponentAccessor;
import com.alonie.brbe.mixins.accessors.RecipeBookPageAccessor;
import com.alonie.brbe.util.RecipeBookPositionMemory;
import com.alonie.recipebookispain_extended.access.RbipTabBridge;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookPage;
import net.minecraft.client.gui.screens.recipebook.RecipeBookTabButton;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.item.CreativeModeTab;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 1.21.1 简化版配方书位置记忆：记住最后激活的标签 + 配方区页码 + 搜索词；
 * 重开配方书时恢复。每个标签页的页码单独保存（RecipeBookPositionMemory 以
 * 稳定键为子键），切回标签时恢复其页码。
 *
 * <p><b>稳定键</b>：vanilla 标签用 {@code RecipeBookCategories.name()}
 * （IExtensibleEnum 稳定）；RBIP 创造标签用 {@code "creative:" +
 * BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab)}——RBIP 所有创造按钮的
 * category 都是 {@code UNKNOWN}（不能按类别区分），且 RBIP 会重排标签，
 * 按下标/类别都会恢复错标签。</p>
 *
 * <p>对照 1.21.11 的 recipebookposition/RecipeBookComponentMixin：本版不含
 * RBIP 创造标签栏页码（RecipeBookScrollAccess 1.21.1 无）；搜索变更页码策略
 * （{@code checkSearchStringUpdate} HEAD/TAIL）已补入（见
 * {@link #brbe$handleSearchChange}），与 1.21.11 一致：首次输入搜索词回首页、
 * 清空搜索恢复搜索前页码。</p>
 */
@Mixin(RecipeBookComponent.class)
public abstract class RecipeBookComponentMixin {

    @SuppressWarnings("rawtypes")
    @Shadow
    protected RecipeBookMenu menu;

    /** 上一次处理的搜索词（vanilla RecipeBookComponent.lastSearch）。 */
    @Shadow
    private String lastSearch;

    /** checkSearchStringUpdate HEAD 捕获的上一次搜索词——TAIL 用以判定"刚被清空"
     *  （退格逐字删除时最后一次按键搜索框已是空值，只有 lastSearch 还保留删除前的词）。 */
    @Unique
    private String brbe$lastSearchAtHead;

    /** 待恢复的 RBIP 创造标签稳定键。RBIP 创造按钮延迟到首个 render 帧构建、
     *  且 initVisuals 会清空 activeCreativeTab——不能在 initVisuals 同步恢复，
     *  改记 pending，由 render HEAD 的重断言消费（见 brbe$reassertAfterRender）。 */
    @Unique
    private String brbe$pendingCreativeKey;

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
        String key = brbe$tabKey(self, tab);
        if (key == null) return;
        RecipeBookPage page = acc.getRecipeBookPage();
        RecipeBookPositionMemory.save(bookKey(),
                key,
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
        String key = RecipeBookPositionMemory.activeTabCategory(bookKey());
        if (key == null) return;
        RecipeBookPositionMemory.Pos pos = RecipeBookPositionMemory.load(bookKey(), key);
        if (pos == null) return;
        RecipeBookComponentAccessor acc = (RecipeBookComponentAccessor) self;

        if (key.startsWith("creative:")) {
            // RBIP 创造标签：按钮延迟到首个 render 帧构建，且 RBIP 在 initVisuals
            // TAIL（同一注入点、跨配置文件、顺序无保证）会清空 activeCreativeTab。
            // 因此不能在 initVisuals 同步恢复（会被 RBIP 擦掉 → 创造标签一闪而过、
            // 配方区空白、无标签选中）。改记 pending，由 render HEAD 的重断言消费：
            // 它必在 initVisuals 之后运行、且自幂等（每帧重断言直到命中），
            // 顺带 forceBuild 确保按钮本帧已存在。
            this.brbe$pendingCreativeKey = key;
            return;
        } else {
            // vanilla 标签：按类别名精确定位（在 RBIP 重排后的列表中）。
            java.util.List<RecipeBookTabButton> tabs = acc.getTabButtons();
            if (tabs.isEmpty()) return;
            RecipeBookTabButton target = null;
            for (RecipeBookTabButton t : tabs) {
                if (t.getCategory().name().equals(key)) { target = t; break; }
            }
            if (target == null) target = tabs.get(0);
            if (target == null) return;
            brbe$applyRestore(self, acc, target, pos, key);
        }
    }

    /** render HEAD：重断言 RBIP 创造标签恢复。RBIP 在 initVisuals（先于我执行）里
     *  清空 activeCreativeTab、且在首个 render 帧 buildCreativeTabs；本消费点在
     *  render HEAD（必在 initVisuals 后），先 forceBuild 确保按钮本帧已存在，再
     *  命中并 applyRestore——把创造标签选中 + activeCreativeTab 同步成立，之后清
     *  pending。自幂等：未命中则不消费、下帧重试。因 forceBuild 置 tabsNeedBuild
     *  =false，RBIP 的 hotReload 不会再重建/擦除，消除了"闪现后空白、无选中"。
     */
    @Inject(method = "render", at = @At("HEAD"))
    private void brbe$reassertAfterRender(GuiGraphics gui, int mouseX, int mouseY,
                                          float delta, CallbackInfo ci) {
        if (this.brbe$pendingCreativeKey == null) return;
        RecipeBookComponent self = (RecipeBookComponent) (Object) this;
        if (BetterRecipeBook.config == null
                || !BetterRecipeBook.config.saveRecipeBookPosition
                || !self.isVisible()) return;
        String key = this.brbe$pendingCreativeKey;
        if (self instanceof RbipTabBridge bridge) {
            bridge.rbip$forceBuildCreativeTabs();
        }
        RecipeBookTabButton target = brbe$findCreativeTab(self, key);
        if (target == null) return; // 尚未构建 → 下帧重试
        RecipeBookPositionMemory.Pos pos = RecipeBookPositionMemory.load(bookKey(), key);
        if (pos == null) { this.brbe$pendingCreativeKey = null; return; }
        this.brbe$pendingCreativeKey = null;
        brbe$applyRestore(self, (RecipeBookComponentAccessor) self, target, pos, key);
    }

    /** 共享的恢复逻辑：复位选中标签 + 重建页面集合 + 钳制页码。 */
    @Unique
    private void brbe$applyRestore(RecipeBookComponent self, RecipeBookComponentAccessor acc,
                                   RecipeBookTabButton target, RecipeBookPositionMemory.Pos pos,
                                   String key) {
        if (target == null) return;

        // RBIP 创造标签：必须同步 activeCreativeTab/activeFurnaceType——否则
        // ClientRecipeBookMixin 的 getCollection(UNKNOWN) @Redirect 看到 null，
        // 页面集合为空（updateCollections 后一片空白），或残留上一会话的
        // 创造标签导致错误过滤。镜像 rbip$handleClick 的选中副作用。
        if (key.startsWith("creative:")) {
            CreativeModeTab group = (self instanceof RbipTabBridge bridge)
                    ? bridge.rbip$tabToGroup(target) : null;
            if (group == null) return;
            com.alonie.recipebookispain_extended.RecipeBookIsPain.activeCreativeTab = group;
            if (menu instanceof net.minecraft.world.inventory.AbstractFurnaceMenu f) {
                com.alonie.recipebookispain_extended.RecipeBookIsPain.activeFurnaceType =
                        com.alonie.recipebookispain_extended.RecipeBookIsPain
                                .detectFurnaceType(f);
            } else {
                com.alonie.recipebookispain_extended.RecipeBookIsPain.activeFurnaceType = null;
            }
        }

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

    /**
     * 搜索栏变化时的页码策略（"保存浏览记录"功能，1.21.11 同款）：
     * <ul>
     *   <li><b>首次输入搜索词</b>（空 → 非空）：回到第 1 页，从结果开头看；</li>
     *   <li><b>清空搜索</b>（非空 → 空）：恢复搜索前浏览的页码（basePage），
     *       而不是 vanilla 清空后跳回第 1 页；</li>
     *   <li>搜索词继续修改（非空 → 非空）：保持原版行为，不干预。</li>
     * </ul>
     *
     * <p>HEAD 捕获 {@link #lastSearch}（上一次处理的搜索词）而非搜索框当前值：
     * 退格键逐字删除时，最后一次按键进入方法时搜索框已是空值，只有
     * {@code lastSearch} 还保留着删除前的词，能可靠判定"刚被清空"。</p>
     */
    @Inject(method = "checkSearchStringUpdate", at = @At("HEAD"))
    private void brbe$captureSearchText(CallbackInfo ci) {
        this.brbe$lastSearchAtHead = this.lastSearch;
    }

    @Inject(method = "checkSearchStringUpdate", at = @At("TAIL"))
    private void brbe$handleSearchChange(CallbackInfo ci) {
        RecipeBookComponent self = (RecipeBookComponent) (Object) this;
        if (BetterRecipeBook.config == null
                || !BetterRecipeBook.config.saveRecipeBookPosition) return;
        RecipeBookComponentAccessor acc = (RecipeBookComponentAccessor) self;
        String now = acc.getSearchBox() != null ? acc.getSearchBox().getValue() : "";
        String old = this.brbe$lastSearchAtHead;
        if (now.isEmpty()) {
            // 清空搜索：恢复搜索前浏览的页码（仅当搜索词确实从非空变为空）
            if (old == null || old.isEmpty()) return;
            RecipeBookTabButton tab = acc.getSelectedTab();
            if (tab == null) return;
            String key = brbe$tabKey(self, tab);
            if (key == null) return;
            RecipeBookPositionMemory.Pos pos = RecipeBookPositionMemory.load(bookKey(), key);
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

    /** 构造选中标签的稳定键：RBIP 创造标签 → {@code creative:<id>}；否则类别名。 */
    @Unique
    private String brbe$tabKey(RecipeBookComponent self, RecipeBookTabButton tab) {
        CreativeModeTab group = (self instanceof RbipTabBridge bridge)
                ? bridge.rbip$tabToGroup(tab) : null;
        if (group != null) {
            ResourceLocation id = BuiltInRegistries.CREATIVE_MODE_TAB.getKey(group);
            if (id != null) {
                return "creative:" + id;
            }
        }
        String category = tab.getCategory().name();
        return (category == null || category.isEmpty()) ? null : category;
    }

    /** 按创造标签 id 在当前已构建的标签按钮里精确定位；未命中返回 null。 */
    @Unique
    private RecipeBookTabButton brbe$findCreativeTab(RecipeBookComponent self, String key) {
        String id = key.substring("creative:".length());
        if (!(self instanceof RbipTabBridge bridge)) return null;
        java.util.List<RecipeBookTabButton> tabs =
                ((RecipeBookComponentAccessor) self).getTabButtons();
        if (tabs == null) return null;
        for (RecipeBookTabButton btn : tabs) {
            CreativeModeTab group = bridge.rbip$tabToGroup(btn);
            if (group == null) continue;
            ResourceLocation gid = BuiltInRegistries.CREATIVE_MODE_TAB.getKey(group);
            if (gid != null && gid.toString().equals(id)) {
                return btn;
            }
        }
        return null;
    }

    @Unique
    private String bookKey() {
        String type = menu != null ? menu.getRecipeBookType().name() : "";
        String screen = menu != null ? menu.getClass().getSimpleName() : "";
        return "vanilla:" + type + ":" + screen;
    }
}
