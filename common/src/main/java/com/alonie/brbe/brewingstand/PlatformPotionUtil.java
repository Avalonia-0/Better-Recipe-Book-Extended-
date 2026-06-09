package com.alonie.brbe.brewingstand;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.List;

public class PlatformPotionUtil {
    private static PotionUtilProvider provider;

    public static void setProvider(PotionUtilProvider p) {
        provider = p;
    }

    public static Ingredient getIngredient(Object recipe) {
        if (provider == null) throw new IllegalStateException("PlatformPotionUtil provider not set");
        return provider.getIngredient(recipe);
    }

    public static Potion getTo(Object recipe) {
        if (provider == null) throw new IllegalStateException("PlatformPotionUtil provider not set");
        return provider.getTo(recipe);
    }

    public static Potion getFrom(Object recipe) {
        if (provider == null) throw new IllegalStateException("PlatformPotionUtil provider not set");
        return provider.getFrom(recipe);
    }

    public static List<?> getPotionMixes(ClientLevel level) {
        if (provider == null) throw new IllegalStateException("PlatformPotionUtil provider not set");
        return provider.getPotionMixes(level);
    }

    public interface PotionUtilProvider {
        Ingredient getIngredient(Object recipe);
        Potion getTo(Object recipe);
        Potion getFrom(Object recipe);
        List<?> getPotionMixes(ClientLevel level);
    }
}
