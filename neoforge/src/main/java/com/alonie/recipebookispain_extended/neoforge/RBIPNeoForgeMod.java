package com.alonie.recipebookispain_extended.neoforge;

import com.alonie.recipebookispain_extended.RecipeBookIsPain;
import net.neoforged.fml.common.Mod;

@Mod("recipe_book_is_pain_extended")
public class RBIPNeoForgeMod {

    public RBIPNeoForgeMod() {
        RecipeBookIsPain.PLATFORM = new NeoForgePlatform();
        RecipeBookIsPain.isOwOLoaded = RecipeBookIsPain.PLATFORM.isModLoaded("owo");
        RecipeBookIsPain.LOGGER.info("[RBIP] NeoForge mod initialized, isOwOLoaded={}", RecipeBookIsPain.isOwOLoaded);
    }
}
