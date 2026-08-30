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
 * RBIP 创造标签栏页码（RecipeBookScrollAccess 1.21.1 无）与搜索变更页码策略
 * （checkSearchStringUpdate 的 HEAD/TAIL 注入——1.21.1 的 search mixin 已有
 * search 处理，避免冲突）。</p>
 */
@Mixin(RecipeBookComponent.class)
public abstract class RecipeBookComponentMixin {

    @SuppressWarnings("rawtypes")
    @Shadow
    protected RecipeBookMenu menu;

    /** 创造标签在 initVisuals 时可能尚未构建（RBIP 延迟到首个 render 帧），
     *  此处缓存待恢复的创造标签 id，待其构建后在首个 render 帧消费。 */
    @Unique
    private String brbe$pendingCreativeRestore;

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
            // RBIP 创造标签：其按钮在 initVisuals 时可能尚未构建（延迟到首个
            // render 帧）。先尝试命中；不中则记住 id，待首帧构建后消费。
            RecipeBookTabButton target = brbe$findCreativeTab(self, key);
            if (target == null) {
                this.brbe$pendingCreativeRestore = key;
                return;
            }
            brbe$applyRestore(self, acc, target, pos, key);
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

    /** 首个 render 帧：若待恢复的是 RBIP 创造标签（其按钮已随 buildCreativeTabs
     *  构建完毕），此时消费 pending 恢复。在 render HEAD 运行；避免与
     *  brbe$rememberPosition（render TAIL）同帧竞争。 */
    @Inject(method = "render", at = @At("HEAD"))
    private void brbe$consumePendingCreativeRestore(GuiGraphics gui, int mouseX, int mouseY,
                                                    float delta, CallbackInfo ci) {
        if (this.brbe$pendingCreativeRestore == null) return;
        RecipeBookComponent self = (RecipeBookComponent) (Object) this;
        if (BetterRecipeBook.config == null
                || !BetterRecipeBook.config.saveRecipeBookPosition
                || !self.isVisible()) return;
        String key = this.brbe$pendingCreativeRestore;
        RecipeBookTabButton target = brbe$findCreativeTab(self, key);
        if (target == null) return; // 还没构建 → 下一帧再试
        this.brbe$pendingCreativeRestore = null;
        RecipeBookPositionMemory.Pos pos = RecipeBookPositionMemory.load(bookKey(), key);
        if (pos == null) return;
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
