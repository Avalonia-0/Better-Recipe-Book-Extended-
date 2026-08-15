package com.alonie.brbe.mixins;

import com.alonie.brbe.interfaces.RecipeBookTabButtonIconOffset;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookTabButton;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(RecipeBookComponent.class)
public abstract class RecipeBookComponentTabIconOffsetMixin {
    @Shadow
    @Final
    private List<RecipeBookTabButton> tabButtons;

    @Inject(method = "updateTabs", at = @At("RETURN"))
    private void brbe$offsetTopAndBottomTabIcons(boolean filtering, CallbackInfo ci) {
        RecipeBookTabButton firstVisibleButton = null;
        RecipeBookTabButton lastVisibleButton = null;

        for (RecipeBookTabButton button : this.tabButtons) {
            RecipeBookTabButtonIconOffset offset = (RecipeBookTabButtonIconOffset) button;
            offset.brbe$setIconYOffset(0);
            if (!button.visible) {
                continue;
            }

            if (firstVisibleButton == null) {
                firstVisibleButton = button;
            }
            lastVisibleButton = button;
        }

        if (firstVisibleButton != null) {
            ((RecipeBookTabButtonIconOffset) firstVisibleButton).brbe$setIconYOffset(-1);
        }
        if (lastVisibleButton != null && lastVisibleButton != firstVisibleButton) {
            ((RecipeBookTabButtonIconOffset) lastVisibleButton).brbe$setIconYOffset(1);
        }
    }
}
