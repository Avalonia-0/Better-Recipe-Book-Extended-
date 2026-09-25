package com.alonie.brbe.mixins.hoverghost;

import com.alonie.brbe.util.HoverGhostRecipe;
import net.minecraft.client.gui.screens.recipebook.GhostRecipe;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * 悬停幽灵预览与**原版幽灵流程**的交界（用户 2026-09-25 诉求）。
 *
 * <p>两处会让原版重新成为幽灵的主人，此时必须把所有权交回去——否则玩家点完配方、
 * 把鼠标移向工作区时，{@code HoverGhostRecipe.release()} 会拿"悬停前的旧快照"把点击
 * 留下的缺料引导抹掉：</p>
 * <ul>
 *   <li>{@code setupGhostRecipe} —— 服务端 {@code ClientboundPlaceGhostRecipePacket}
 *       的回包入口（{@code ClientPacketListener.handlePlaceRecipe}）。我们自己写入时
 *       带 {@link HoverGhostRecipe#isSelfFill()} 标记，不会误伤。</li>
 *   <li>{@code mouseClicked} 里的 {@code ghostRecipe.clear()} —— 点击配方：先清幽灵
 *       再发服务端放置包（1.21.1 的点击处理内联在 {@code mouseClicked}，没有
 *       {@code tryPlaceRecipe} 方法）。</li>
 * </ul>
 *
 * <p>另外配方书收起（{@code setVisible(false)}）时释放预览：幽灵物品的渲染不看书是否
 * 可见（屏幕在 {@code render} 里无条件调 {@code renderGhostRecipe}），不释放就会在
 * 书收起后继续显示。</p>
 */
@Mixin(RecipeBookComponent.class)
public abstract class RecipeBookComponentMixin {

    @Inject(method = "setupGhostRecipe", at = @At("HEAD"))
    private void brbe$noteExternalGhost(RecipeHolder<?> recipe, List<Slot> slots, CallbackInfo ci) {
        if (!HoverGhostRecipe.isSelfFill()) {
            HoverGhostRecipe.invalidate();
        }
    }

    @Redirect(method = "mouseClicked",
              at = @At(value = "INVOKE",
                       target = "Lnet/minecraft/client/gui/screens/recipebook/GhostRecipe;clear()V"))
    private void brbe$notePlacedRecipe(GhostRecipe ghost) {
        HoverGhostRecipe.invalidate();
        ghost.clear();
    }

    @Inject(method = "setVisible", at = @At("HEAD"))
    private void brbe$releaseHoverGhostOnHide(boolean visible, CallbackInfo ci) {
        if (!visible) {
            HoverGhostRecipe.release();
        }
    }
}
