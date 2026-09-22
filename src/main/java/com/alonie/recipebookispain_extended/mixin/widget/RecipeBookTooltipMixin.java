package com.alonie.recipebookispain_extended.mixin.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookTabButton;
import net.minecraft.client.gui.screens.recipebook.SearchRecipeBookCategory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Optional;

import static com.alonie.recipebookispain_extended.RecipeBookIsPain.toItemGroup;

@Mixin(value = RecipeBookComponent.class, priority = 1001)
public class RecipeBookTooltipMixin {
    @Shadow protected Minecraft minecraft;
    @Shadow @Final private List<RecipeBookTabButton> tabButtons;

    @Inject(at = @At("TAIL"), method = "extractTooltip")
    private void rbip$renderTooltip(GuiGraphicsExtractor context, int mouseX, int mouseY, Slot hoveredSlot, CallbackInfo ci) {
        if (minecraft.gui.screen() != null) {
            // The BRBE query UI (viewer box / preview / pin) owns the cursor:
            // the tab under it must not show its tooltip (no leak-through).
            boolean masked = com.alonie.brbe.util.RecipeViewerOverlay.modalMaskOwnsCursor(mouseX, mouseY);
            // 显式循环而不是 stream+lambda：mixin 内每个 lambda 都会让 Mixin 在 latest.log
            // 打一行 "Renaming synthetic method ... from mod brbe"（本方法原先 3 行）。
            for (RecipeBookTabButton widget : this.tabButtons) {
                if (!widget.visible || !widget.isHovered() || masked) continue;
                if (widget.getCategory() instanceof SearchRecipeBookCategory) {
                    context.setTooltipForNextFrame(minecraft.font,
                            CreativeModeTabs.searchTab().getDisplayName(), mouseX, mouseY);
                } else {
                    CreativeModeTab group = toItemGroup(widget.getCategory());
                    if (group != null) {
                        context.setTooltipForNextFrame(minecraft.font,
                                group.getDisplayName(), mouseX, mouseY);
                    }
                }
            }
        }
    }
}
