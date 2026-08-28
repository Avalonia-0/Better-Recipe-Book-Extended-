package com.alonie.brbe.mixins.search;

import com.alonie.brbe.mixins.accessors.RecipeBookComponentAccessor;
import com.alonie.brbe.search.SearchCache;
import com.alonie.brbe.search.SearchQuery;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles advanced search for the vanilla {@link RecipeBookComponent}.
 * Uses {@code @ModifyArg} (not {@code @Redirect}) so it can coexist
 * with other mixins targeting the same {@code page.updateCollections} call.
 */
@Mixin(RecipeBookComponent.class)
public class RecipeBookComponentMixin {

    @Shadow @Final protected Minecraft minecraft;

    @Shadow protected EditBox searchBox;

    @Unique
    private String betterRecipeBook$savedSearchText;

    @Unique
    private SearchQuery betterRecipeBook$parsedQuery;

    /**
     * 右键点击搜索框时清空搜索文字并刷新。
     * 清空后取消聚焦（与 1.21.11 语义一致）：聚焦状态保留会让后续 R/U/A 等
     * 按键被 RecipeBookComponent.keyPressed 的「聚焦搜索框吞键」分支拦截
     * （vanilla 行为），查询系统打不开；且点击别处也无法取消聚焦。
     */
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void betterRecipeBook$rightClickClearSearch(double mouseX, double mouseY, int button,
                                                        CallbackInfoReturnable<Boolean> cir) {
        if (button != 1 || searchBox == null) return;
        if (!searchBox.isMouseOver(mouseX, mouseY)) return;
        searchBox.setValue("");
        searchBox.setFocused(false);
        ((RecipeBookComponentAccessor) this).updateCollectionsInvoker(true);
        cir.setReturnValue(true);
    }

    /**
     * HEAD: Save search text.  If the query is advanced, clear the search
     * box so vanilla's substring filter becomes a no-op.
     */
    @Inject(method = "updateCollections", at = @At("HEAD"))
    private void betterRecipeBook$onUpdateCollections(boolean resetPageNumber, CallbackInfo ci) {
        betterRecipeBook$savedSearchText = null;
        betterRecipeBook$parsedQuery = null;

        if (searchBox == null) return;

        String text = searchBox.getValue();
        if (text == null || text.isEmpty()) return;

        SearchQuery query = SearchQuery.parse(text);
        if (!query.isAdvanced()) return;

        betterRecipeBook$savedSearchText = text;
        betterRecipeBook$parsedQuery = query;
        searchBox.setValue("");
    }

    /**
     * ModifyArg on index 0 of {@code page.updateCollections(List, boolean)}.
     * Filters the list by the advanced search query before it reaches the page.
     * Multiple {@code @ModifyArg} handlers on the same target are allowed by
     * Mixin — they chain (each one transforms the output of the previous).
     */
    @ModifyArg(method = "updateCollections",
               at = @At(value = "INVOKE",
                        target = "Lnet/minecraft/client/gui/screens/recipebook/RecipeBookPage;updateCollections(Ljava/util/List;Z)V"),
               index = 0)
    private List<RecipeCollection> betterRecipeBook$applyAdvancedSearch(List<RecipeCollection> collections) {
        if (betterRecipeBook$parsedQuery == null || minecraft.level == null) {
            return collections;
        }

        SearchCache cache = new SearchCache();
        RegistryAccess registryAccess = minecraft.level.registryAccess();
        List<RecipeCollection> filtered = new ArrayList<>();

        for (RecipeCollection collection : collections) {
            for (RecipeHolder<?> recipe : collection.getRecipes()) {
                ItemStack result = recipe.value().getResultItem(registryAccess);
                if (result != null && !result.isEmpty()
                        && betterRecipeBook$parsedQuery.matches(result, cache)) {
                    filtered.add(collection);
                    break;
                }
            }
        }

        return filtered;
    }

    /**
     * TAIL: Restore the search box text if we cleared it.
     */
    @Inject(method = "updateCollections", at = @At("TAIL"))
    private void betterRecipeBook$restoreSearchText(boolean resetPageNumber, CallbackInfo ci) {
        if (betterRecipeBook$savedSearchText != null && searchBox != null) {
            searchBox.setValue(betterRecipeBook$savedSearchText);
            betterRecipeBook$savedSearchText = null;
            betterRecipeBook$parsedQuery = null;
        }
    }
}
