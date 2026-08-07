package com.alonie.brbe.mixins.instantcraft;

import com.alonie.brbe.mixins.accessors.AbstractRecipeBookScreenAccessor;
import com.alonie.brbe.mixins.accessors.RecipeBookComponentAccessor;
import com.alonie.brbe.util.CarriedPlaceHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.inventory.AbstractCraftingMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * 点击配方放置（handlePlaceRecipe）时把 carried（鼠标拿起物）直接摆进合成格，
 * 取代服务端放置（后者无条件 clearGrid + 只从物品栏取料，导致 carried 料必须
 * 先进物品栏、物品栏满时失败）。
 *
 * <p>普通点击（craftAll=false）且 carried 非空时：调用
 * {@link CarriedPlaceHelper#placeGridFromCarried}，成功则 {@code ci.cancel()} 取消
 * 服务端放置包——网格摆好后结果槽自动出产物。失败则走原版服务端放置兜底。</p>
 *
 * <p>Shift 点击（craftAll=true）保持原版 + instantCraft（TAIL 注入不受 cancel 影响）。</p>
 */
@Mixin(MultiPlayerGameMode.class)
public class PlaceRecipeCarriedMixin {
    @Shadow @Final private Minecraft minecraft;

    @Inject(method = "handlePlaceRecipe", at = @At("HEAD"), cancellable = true)
    private void brbe$placeGridFromCarried(int containerId, RecipeDisplayId recipeId, boolean craftAll, CallbackInfo ci) {
        if (craftAll) return;
        if (minecraft.screen == null || !(minecraft.screen instanceof AbstractRecipeBookScreen<?> screen)) return;
        if (!(screen.getMenu() instanceof RecipeBookMenu menu)) return;

        RecipeBookComponent<?> component = ((AbstractRecipeBookScreenAccessor) screen).brbe$getRecipeBookComponent();
        RecipeCollection collection = ((RecipeBookComponentAccessor) component).getRecipeBookPage().getLastClickedRecipeCollection();
        if (collection == null) return;

        List<RecipeDisplayEntry> entries = collection.getRecipes();
        if (menu instanceof AbstractCraftingMenu craftingMenu) {
            boolean placed = CarriedPlaceHelper.placeGridFromCarried(
                    containerId, recipeId, entries,
                    craftingMenu.getInputGridSlots(), craftingMenu.getGridWidth(),
                    CraftingMenu.RESULT_SLOT);
            if (placed) {
                ci.cancel();
            }
        }
    }
}
