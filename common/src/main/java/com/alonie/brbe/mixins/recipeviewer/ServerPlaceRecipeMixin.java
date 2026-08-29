package com.alonie.brbe.mixins.recipeviewer;

import net.minecraft.recipebook.ServerPlaceRecipe;
import net.minecraft.stats.ServerRecipeBook;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 1.21.1 版服务端幽灵预览放行（1.21.11 ServerGamePacketListenerMixin 的移植——注入
 * 点不同，见下）。
 *
 * <p>1.21.1 的 {@code ServerRecipeBook.contains(RecipeHolder)} 检查不在
 * {@code ServerGamePacketListenerImpl.handlePlaceRecipe}（那是 1.21.11 的结构），
 * 而在 {@link ServerPlaceRecipe#recipeClicked}（bytecode 偏移 0-15：null 检查 +
 * contains 检查都返回）。viewer 显示本地缓存注入的未解锁配方时，contains=false →
 * 直接 return → 无幽灵包 → 点击无预览。放宽为恒 true：后续
 * {@code StackedContents.canCraft} 仍校验配方可放置性，安全（1.21.11 同语义）。</p>
 */
@Mixin(ServerPlaceRecipe.class)
public abstract class ServerPlaceRecipeMixin {

    @Redirect(method = "recipeClicked",
              at = @At(value = "INVOKE",
                       target = "Lnet/minecraft/stats/ServerRecipeBook;contains(Lnet/minecraft/world/item/crafting/RecipeHolder;)Z"))
    private boolean brbe$allowViewerGhost(ServerRecipeBook book, RecipeHolder<?> recipe) {
        return true;
    }
}
