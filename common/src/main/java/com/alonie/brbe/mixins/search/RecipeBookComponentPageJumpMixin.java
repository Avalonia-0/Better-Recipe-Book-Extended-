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

/**
 * 1.21.1 版搜索栏页码跳转命令（^N^ / ……N^ / ^N…… / ……N……）。
 * 命中时清空搜索、取消聚焦、恢复完整列表并跳到第 N 页。
 * 适配：1.21.1 的 updateCollectionsInvoker(boolean)（无 isFiltering 参数）。
 */
@Mixin(RecipeBookComponent.class)
public abstract class RecipeBookComponentPageJumpMixin {

    @Inject(method = "checkSearchStringUpdate", at = @At("HEAD"), cancellable = true)
    private void brbe$pageJumpCommand(CallbackInfo ci) {
        RecipeBookComponentAccessor accessor = (RecipeBookComponentAccessor) this;
        EditBox searchBox = accessor.getSearchBox();
        int page = SearchPageJump.parse(searchBox);
        if (page <= 0) return;

        // 用完整类别列表的总页数判断页码合法性。
        RecipeBookTabButton tab = accessor.getSelectedTab();
        int fullTotalPages = 0;
        if (tab != null && accessor.getRecipeBook() != null) {
            fullTotalPages = (int) Math.ceil(accessor.getRecipeBook().getCollection(tab.getCategory()).size() / 20.0D);
        }
        if (page > fullTotalPages) {
            // 页码不存在：保留输入和聚焦，走普通搜索
            return;
        }

        // 先清空搜索并恢复完整列表（页码重置到第 0 页），再跳转目标页
        searchBox.setValue("");
        searchBox.setFocused(false);
        accessor.updateCollectionsInvoker(true);

        // 标记用户翻页：相邻页跳转走滑动动画，跨多页直接切换
        RecipeBookPageAnimBridge.markUserFlip();
        RecipeBookPageAccessor pageAccessor = (RecipeBookPageAccessor) accessor.getRecipeBookPage();
        pageAccessor.setCurrentPage(page - 1);
        pageAccessor.updateButtonsForPageInvoker();
        ci.cancel();
    }
}
