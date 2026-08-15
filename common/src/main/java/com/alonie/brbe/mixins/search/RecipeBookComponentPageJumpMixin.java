package com.alonie.brbe.mixins.search;

import com.alonie.brbe.mixins.accessors.RecipeBookComponentAccessor;
import com.alonie.brbe.mixins.accessors.RecipeBookPageAccessor;
import com.alonie.brbe.util.RecipeBookPageAnimBridge;
import com.alonie.brbe.util.SearchPageJump;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookTabButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RecipeBookComponent.class)
public abstract class RecipeBookComponentPageJumpMixin {

    /**
     * 原版合成台搜索栏页码跳转命令（^N^ / ……N^ / ^N…… / ……N……）。
     * 命中时清空搜索（含 IME 组合残留）、取消聚焦、恢复完整列表并跳到第 N 页，
     * 然后取消原 checkSearchStringUpdate 的后续逻辑。页码超出总页数时不触发，
     * 保留输入和聚焦走普通搜索（无结果自然显示空页）。
     */
    @Inject(method = "checkSearchStringUpdate", at = @At("HEAD"), cancellable = true)
    private void brbe$pageJumpCommand(CallbackInfo ci) {
        RecipeBookComponentAccessor accessor = (RecipeBookComponentAccessor) this;
        EditBox searchBox = accessor.getSearchBox();
        int page = SearchPageJump.parse(searchBox);
        if (page <= 0) return;

        // 用完整类别列表的总页数判断页码合法性。不能用过滤后的 RecipeBookPage.totalPages：
        // 输入命令过程中间态的搜索词会把列表过滤空，合法页码会被误判为超范围。
        RecipeBookTabButton tab = accessor.getSelectedTab();
        int fullTotalPages = 0;
        if (tab != null && accessor.getBook() != null) {
            fullTotalPages = (int) Math.ceil(accessor.getBook().getCollection(tab.getCategory()).size() / 20.0D);
        }
        if (page > fullTotalPages) {
            // 页码不存在：保留输入和聚焦，走普通搜索（无结果自然显示空页）
            return;
        }

        // 先清空搜索（含 IME 组合残留）并恢复完整列表（页码重置到第 0 页），再跳转目标页
        searchBox.preeditUpdated(null);
        searchBox.setValue("");
        searchBox.setFocused(false);
        accessor.updateCollectionsInvoker(true, false);

        // 标记用户翻页：相邻页跳转走滑动动画，跨多页由动画 mixin 降级为直接切换
        RecipeBookPageAnimBridge.markUserFlip();
        RecipeBookPageAccessor pageAccessor = (RecipeBookPageAccessor) accessor.getRecipeBookPage();
        pageAccessor.setCurrentPage(page - 1);
        pageAccessor.updateButtonsForPageInvoker();
        ci.cancel();
    }
}
