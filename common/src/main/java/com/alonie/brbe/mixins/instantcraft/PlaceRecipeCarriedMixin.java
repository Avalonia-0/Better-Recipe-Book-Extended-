package com.alonie.brbe.mixins.instantcraft;

import com.alonie.brbe.mixins.accessors.RecipeBookComponentAccessor;
import com.alonie.brbe.util.CarriedPlaceHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.item.crafting.RecipeHolder;
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
 * 先进物品栏、物品栏满时失败）。1.21.1 版：recipe 用 {@link RecipeHolder}。
 *
 * <p>普通点击（craftAll=false）且 carried 非空时：成功则 {@code ci.cancel()} 取消
 * 服务端放置包。失败走原版兜底。Shift 点击（craftAll=true）走原版 + instantCraft。</p>
 */
@Mixin(MultiPlayerGameMode.class)
public class PlaceRecipeCarriedMixin {
    @Shadow @Final private Minecraft minecraft;

    @Inject(method = "handlePlaceRecipe", at = @At("HEAD"), cancellable = true)
    private void brbe$placeGridFromCarried(int containerId, RecipeHolder<?> recipe, boolean craftAll, CallbackInfo ci) {
        if (craftAll) return;
        if (minecraft.screen == null) return;

        // 1.21.1 没有 AbstractRecipeBookScreen；合成台（CraftingScreen）/ 背包（InventoryScreen）
        // 直接暴露 getRecipeBookComponent()。
        RecipeBookComponent component = null;
        if (minecraft.screen instanceof InventoryScreen inv) {
            component = inv.getRecipeBookComponent();
        } else if (minecraft.screen instanceof CraftingScreen cs) {
            component = cs.getRecipeBookComponent();
        }
        if (component == null) return;

        RecipeCollection collection = ((RecipeBookComponentAccessor) component)
                .getRecipeBookPage().getLastClickedRecipeCollection();
        if (collection == null) return;

        if (!(minecraft.player.containerMenu instanceof RecipeBookMenu<?, ?> menu)) return;

        List<RecipeHolder<?>> holders = collection.getRecipes();
        boolean placed = CarriedPlaceHelper.placeGridFromCarried(
                containerId, recipe.id(), holders, menu,
                menu.getGridWidth(), menu.getGridHeight(), menu.getResultSlotIndex());
        if (placed) {
            ci.cancel();
        }
    }
}
