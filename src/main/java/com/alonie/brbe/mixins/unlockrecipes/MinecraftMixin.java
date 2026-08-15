package com.alonie.brbe.mixins.unlockrecipes;

import com.alonie.brbe.cache.VanillaRecipeCache;
import com.alonie.brbe.recipeviewer.engine.RecipeViewerEngine;
import com.alonie.brbe.util.RecipeUnlockUtil;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MinecraftMixin {
    @Inject(method = "clearClientLevel", at = @At("HEAD"))
    private void onClearClientLevel(Screen screen, CallbackInfo ci) {
        RecipeUnlockUtil.restoreRecipes();
        VanillaRecipeCache.clear();
        RecipeViewerEngine.clear();
    }
}
