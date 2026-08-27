package com.alonie.brbe.mixins.pins;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.generic.pins.PinnableRecipeCollection;
import com.alonie.brbe.mixins.accessors.OverlayRecipeButtonAccessor;
import com.alonie.brbe.mixins.accessors.OverlayRecipeComponentAccessor;
import com.alonie.brbe.mixins.accessors.RecipeBookComponentAccessor;
import com.alonie.brbe.mixins.accessors.RecipeBookPageAccessor;
import com.alonie.brbe.pin.TabPinManager;
import com.alonie.brbe.util.ClientCompat;
import com.alonie.recipebookispain_extended.RecipeBookIsPain;
import com.alonie.recipebookispain_extended.access.RecipeBookScrollAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookPage;
import net.minecraft.client.gui.screens.recipebook.RecipeBookTabButton;
import net.minecraft.client.gui.screens.recipebook.RecipeButton;
import net.minecraft.client.gui.screens.recipebook.OverlayRecipeComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.crafting.ExtendedRecipeBookCategory;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(AbstractRecipeBookScreen.class)
public abstract class AbstractContainerScreenMixin {
    @Shadow @Final private RecipeBookComponent<?> recipeBookComponent;

    @Inject(method = "mouseClicked", at = @At(value = "HEAD"), cancellable = true)
    public void brbe$clickVisibleOverlayFirst(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        RecipeBookComponent<?> book = this.recipeBookComponent;
        if (!book.isVisible()) {
            return;
        }

        OverlayRecipeComponent alternatesWidget = ((RecipeBookPageAccessor) ((RecipeBookComponentAccessor) book).getRecipeBookPage()).getOverlay();
        if (alternatesWidget.isVisible() && book.mouseClicked(event, doubleClick)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "extractRenderState", at = @At(value = "RETURN"))
    public void brbe$renderVisibleOverlayOnTop(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        // During the BRBE R/U viewer, skip the second overlay draw so tooltips
        // rendered from extractTooltip (and the deferred channel) paint above
        // the box.  The overlay is already drawn once inside the recipe book
        // page; redrawing it on a higher stratum would cover the tooltip.
        if (com.alonie.brbe.cache.RecipeViewerIndex.isViewerActive()) {
            return;
        }

        RecipeBookComponent<?> book = this.recipeBookComponent;
        if (!book.isVisible()) {
            return;
        }

        OverlayRecipeComponent alternatesWidget = ((RecipeBookPageAccessor) ((RecipeBookComponentAccessor) book).getRecipeBookPage()).getOverlay();
        if (alternatesWidget.isVisible()) {
            guiGraphics.nextStratum();
            alternatesWidget.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
        }
    }

    @Inject(method = "keyPressed", at = @At(value = "HEAD"), cancellable = true)
    public void onKeyPressed(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        Minecraft minecraft = Minecraft.getInstance();
        RecipeBookComponent<?> book = this.recipeBookComponent;

        if (!book.isVisible()) return;

        RecipeBookPage page = ((RecipeBookComponentAccessor) book).getRecipeBookPage();
        OverlayRecipeComponent alternatesWidget = ((RecipeBookPageAccessor) page).getOverlay();
        EditBox searchBox = ((RecipeBookComponentAccessor) book).getSearchBox();

        // when the pin (固定) key is pressed, handle pinning/unpinning of recipes except when searchBox is consuming input
        if (ClientCompat.matchesPinKey(event.key(), event.scancode(), event.modifiers()) && (searchBox == null || !searchBox.canConsumeInput())) {
            if (alternatesWidget.isVisible()) {
                // 替代配方组不能直接 pin：固定键只作用于悬停的具体变体按钮
                // （打开组后逐个 pin 组内配方）。未悬停变体时吞掉按键，防止
                // 误 pin 下层（整个配方组）。
                for (AbstractWidget button : ((OverlayRecipeComponentAccessor) alternatesWidget).getRecipeButtons()) {
                    // 仅处理可见按钮：隐藏按钮的 isHovered 字段是陈旧的
                    // （不可见时 extractRenderState 直接返回，不刷新悬停状态）。
                    if (button.visible && button.isHoveredOrFocused()) {
                        RecipeDisplayEntry entry = entryForId(alternatesWidget.getRecipeCollection(),
                                ((OverlayRecipeButtonAccessor) button).brbe$getRecipe());
                        if (entry != null) {
                            BetterRecipeBook.pinnedRecipeManager.toggleFavourite(entry);
                            RecipeBookComponentAccessor accessor = (RecipeBookComponentAccessor) book;
                            boolean filtering = accessor.isFilteringInvoker();
                            accessor.updateCollectionsInvoker(false, filtering);
                            if (minecraft.getSoundManager() != null) {
                                AbstractWidget.playButtonClickSound(minecraft.getSoundManager());
                            }
                        }
                        cir.setReturnValue(true);
                        return;
                    }
                }
                cir.setReturnValue(true);
                return;
            }

            // 网格按钮：普通配方（单配方组）直接固定/取消固定（老行为）；
            // 替代配方组（多变体组）不能直接 pin——组只能在打开后 pin 其中的
            // 单个变体（规则 1），悬停时吞掉固定键（无副作用）。
            for (RecipeButton button : ((RecipeBookPageAccessor) page).getButtons()) {
                // 同上：翻页后隐藏按钮的 isHovered 不会刷新。
                if (!button.visible || !button.isHoveredOrFocused()) continue;
                RecipeCollection collection = button.getCollection();
                if (collection != null && collection.getRecipes().size() == 1) {
                    RecipeDisplayEntry entry = collection.getRecipes().get(0);
                    if (entry != null) {
                        BetterRecipeBook.pinnedRecipeManager.toggleFavourite(entry);
                        RecipeBookComponentAccessor accessor = (RecipeBookComponentAccessor) book;
                        boolean filtering = accessor.isFilteringInvoker();
                        accessor.updateCollectionsInvoker(false, filtering);
                        if (minecraft.getSoundManager() != null) {
                            AbstractWidget.playButtonClickSound(minecraft.getSoundManager());
                        }
                    }
                }
                cir.setReturnValue(true);
                return;
            }

            // RBIP 创造标签：固定键悬停在标签上时固定/取消固定该标签（固定标签排在
            // 首页搜索标签之下）。搜索标签等无创造组映射的标签不可固定。
            for (RecipeBookTabButton tab : ((RecipeBookComponentAccessor) book).getTabButtons()) {
                // 只处理当前页可见的标签：分页后隐藏标签的 isHovered 字段停留在
                // 最后一次可见时的值（stale），否则会固定到"同位置的第一页标签"。
                if (!tab.visible || !tab.isHoveredOrFocused()) continue;
                CreativeModeTab group = RecipeBookIsPain.toItemGroup(tab.getCategory());
                if (group == null) continue;
                Identifier tabId = BuiltInRegistries.CREATIVE_MODE_TAB.getKey(group);
                if (tabId == null) continue;
                TabPinManager.toggle(tabId);
                if (minecraft.getSoundManager() != null) {
                    AbstractWidget.playButtonClickSound(minecraft.getSoundManager());
                }
                RecipeBookComponentAccessor accessor = (RecipeBookComponentAccessor) book;
                // 固定/取消固定标签不跳页：保留当前标签页码（updateTabs 的
                // 分页跟随选中标签，重排后会跳到别的页）。
                int pageBefore = book instanceof RecipeBookScrollAccess scrollAccess
                        ? scrollAccess.rbip$getPage() : 0;
                // updateTabs 的 RBIP HEAD 注入按新的固定顺序重建 tabInfos。
                accessor.updateTabsInvoker(false);
                // vanilla 的 updateTabs 只重排已有按钮、不会从 tabInfos 重建按钮列表，
                // 因此先把现有按钮重排到与（已重建的）tabInfos 一致，再调用一次生效，
                // 固定标签立即移动到首页（无需重开配方书）。
                java.util.Map<ExtendedRecipeBookCategory, RecipeBookTabButton> byCategory =
                        new java.util.LinkedHashMap<>();
                for (RecipeBookTabButton b : accessor.getTabButtons()) {
                    byCategory.putIfAbsent(b.getCategory(), b);
                }
                List<RecipeBookTabButton> buttons = accessor.getTabButtons();
                buttons.clear();
                for (RecipeBookComponent.TabInfo info : accessor.getTabInfos()) {
                    RecipeBookTabButton b = byCategory.get(info.category());
                    if (b != null) buttons.add(b);
                }
                accessor.updateTabsInvoker(false);
                // 恢复固定前的页码（clamp 到有效范围）。
                if (book instanceof RecipeBookScrollAccess scrollAccess) {
                    scrollAccess.rbip$setPage(pageBefore);
                }
                cir.setReturnValue(true);
                return;
            }
        }

        // when <chat key> is pressed, focus recipes component for searchBox
        // this also works for BrewingRecipeBookComponent as the super's searchBox is set to the same object
        if (ClientCompat.matches(minecraft.options.keyChat, event.key(), event.scancode(), event.modifiers())) {
            minecraft.gui.screen().setFocused(book);
        }
    }

    /** The {@link RecipeDisplayEntry} of one overlay variant button inside the
     *  group collection ({@code recipeId} equals {@code entry.id()}), or null. */
    private static RecipeDisplayEntry entryForId(RecipeCollection collection, RecipeDisplayId recipeId) {
        if (collection == null || recipeId == null) return null;
        for (RecipeDisplayEntry entry : collection.getRecipes()) {
            if (entry != null && recipeId.equals(entry.id())) return entry;
        }
        return null;
    }

}
