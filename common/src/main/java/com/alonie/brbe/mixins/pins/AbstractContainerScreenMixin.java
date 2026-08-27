package com.alonie.brbe.mixins.pins;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.PinnedRecipeManager;
import com.alonie.brbe.mixins.accessors.OverlayRecipeButtonAccessor;
import com.alonie.brbe.mixins.accessors.OverlayRecipeComponentAccessor;
import com.alonie.brbe.mixins.accessors.RecipeBookComponentAccessor;
import com.alonie.brbe.mixins.accessors.RecipeBookPageAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.recipebook.*;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {

    @Inject(method = "keyPressed", at = @At(value = "HEAD"), cancellable = true)
    public void onKeyPressed(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        if (!(this instanceof RecipeUpdateListener rul)) return;

        Minecraft minecraft = Minecraft.getInstance();
        RecipeBookComponent book = rul.getRecipeBookComponent();

        if (!book.isVisible()) return;

        RecipeBookPage page = ((RecipeBookComponentAccessor) book).getRecipeBookPage();
        OverlayRecipeComponent alternatesWidget = ((RecipeBookPageAccessor) page).getOverlay();

        EditBox searchBox = ((RecipeBookComponentAccessor) book).getSearchBox();

        // when pin key is pressed, handle pinning/unpinning of recipes except when searchBox is consuming input
        if (BetterRecipeBook.PIN_MAPPING.matches(keyCode, scanCode) && (searchBox == null || !searchBox.canConsumeInput())) {
            if (alternatesWidget.isVisible()) {
                // 替代配方组不能直接 pin：固定键只作用于悬停的具体变体按钮
                // （打开组后逐个 pin 组内配方）。未悬停变体时吞掉按键，防止
                // 误 pin 下层（整个配方组）。
                for (AbstractWidget button : ((OverlayRecipeComponentAccessor) alternatesWidget).getRecipeButtons()) {
                    if (button.visible && button.isHoveredOrFocused()) {
                        RecipeHolder<?> holder = ((OverlayRecipeButtonAccessor) button).getRecipe();
                        if (holder != null) {
                            BetterRecipeBook.pinnedRecipeManager.toggleFavourite(holder);
                            ((RecipeBookComponentAccessor) book).updateCollectionsInvoker(false);
                            if (minecraft.getSoundManager() != null) {
                                button.playDownSound(minecraft.getSoundManager());
                            }
                        }
                        cir.setReturnValue(true);
                        return;
                    }
                }
                cir.setReturnValue(true);
                return;
            }

            // 网格按钮：普通配方（单配方组）直接固定/取消固定；替代配方组
            // （多变体组）不能直接 pin——组只能在打开后 pin 其中的单个变体，
            // 悬停时吞掉固定键（无副作用）。
            for (RecipeButton button : ((RecipeBookPageAccessor) page).getButtons()) {
                if (!button.visible || !button.isHoveredOrFocused()) continue;
                RecipeCollection collection = button.getCollection();
                if (collection != null && collection.getRecipes().size() == 1) {
                    RecipeHolder<?> holder = collection.getRecipes().get(0);
                    if (holder != null) {
                        BetterRecipeBook.pinnedRecipeManager.toggleFavourite(holder);
                        ((RecipeBookComponentAccessor) book).updateCollectionsInvoker(false);
                        if (minecraft.getSoundManager() != null) {
                            button.playDownSound(minecraft.getSoundManager());
                        }
                    }
                }
                cir.setReturnValue(true);
                return;
            }

            // RBIP 创造标签：固定键悬停在标签上时固定/取消固定该标签（固定标签排在
            // 首页搜索标签之下）。无创造组映射的标签（搜索标签等）不可固定。
            for (RecipeBookTabButton tab : ((RecipeBookComponentAccessor) book).getTabButtons()) {
                if (!tab.visible || !tab.isHoveredOrFocused()) continue;
                net.minecraft.world.item.CreativeModeTab group =
                        ((com.alonie.recipebookispain_extended.access.RbipTabBridge) book)
                                .rbip$tabToGroup(tab);
                if (group == null) continue;
                net.minecraft.resources.ResourceLocation tabId =
                        net.minecraft.core.registries.BuiltInRegistries.CREATIVE_MODE_TAB
                                .getKey(group);
                if (tabId == null) continue;
                com.alonie.brbe.pin.TabPinManager.toggle(tabId);
                if (minecraft.getSoundManager() != null) {
                    tab.playDownSound(minecraft.getSoundManager());
                }
                // 重建标签列表：固定标签置顶（rebuildTabList 按 pinnedTabs 排序）
                ((RecipeBookComponentAccessor) book).updateTabsInvoker();
                cir.setReturnValue(true);
                return;
            }
        }

        // when <chat key> is pressed, focus recipes component for searchBox
        // this also works for BrewingRecipeBookComponent as the super's searchBox is set to the same object
        if (minecraft.options.keyChat.matches(keyCode, scanCode)) {
            minecraft.screen.setFocused(book);
        }

    }

}
