package com.alonie.brbe.neoforge;

import com.alonie.brbe.BetterRecipeBook;
import net.neoforged.fml.common.Mod;

@Mod(BetterRecipeBook.MOD_ID)
public final class BetterRecipeBookNeoForge {
    public BetterRecipeBookNeoForge() {
        BetterRecipeBook.init();
        BetterRecipeBookClientNeoForge.init();
    }
}
