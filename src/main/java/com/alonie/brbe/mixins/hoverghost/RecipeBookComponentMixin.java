package com.alonie.brbe.mixins.hoverghost;

import com.alonie.brbe.util.HoverGhostRecipe;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 悬停幽灵预览与**原版幽灵流程**的交界（用户 2026-09-25 诉求）。
 *
 * <p>两处会让原版重新成为幽灵槽位的主人，此时必须把所有权交回去——否则玩家点完配方、
 * 把鼠标移向工作区时，{@code HoverGhostRecipe.release()} 会拿"悬停前的旧快照"把点击留下
 * 的缺料引导抹掉：</p>
 * <ul>
 *   <li>{@code fillGhostRecipe(RecipeDisplay)} —— 服务端 {@code ClientboundPlaceGhostRecipePacket}
 *       的回包入口（{@code ClientPacketListener.handlePlaceRecipe}）。我们自己写入时带
 *       {@link HoverGhostRecipe#isSelfFill()} 标记，不会误伤。</li>
 *   <li>{@code tryPlaceRecipe} —— 点击放置：先 {@code ghostSlots.clear()} 再发服务端包。</li>
 * </ul>
 *
 * <p>另外配方书收起（{@code setVisible(false)}）时释放预览：幽灵物品的渲染不看书是否
 * 可见（{@code AbstractRecipeBookScreen.extractSlots} 无条件调用
 * {@code extractGhostRecipe}），不释放就会在书收起后继续显示。</p>
 */
@Mixin(RecipeBookComponent.class)
public abstract class RecipeBookComponentMixin {

    @Inject(method = "fillGhostRecipe(Lnet/minecraft/world/item/crafting/display/RecipeDisplay;)V",
            at = @At("HEAD"))
    private void brbe$noteExternalGhost(RecipeDisplay display, CallbackInfo ci) {
        if (!HoverGhostRecipe.isSelfFill()) {
            HoverGhostRecipe.invalidate();
        }
    }

    @Inject(method = "tryPlaceRecipe", at = @At("HEAD"))
    private void brbe$notePlacedRecipe(RecipeCollection collection, RecipeDisplayId recipe,
                                       boolean useMaxItems, CallbackInfoReturnable<Boolean> cir) {
        HoverGhostRecipe.invalidate();
    }

    @Inject(method = "setVisible", at = @At("HEAD"))
    private void brbe$releaseHoverGhostOnHide(boolean visible, CallbackInfo ci) {
        if (!visible) {
            HoverGhostRecipe.release();
        }
    }
}
