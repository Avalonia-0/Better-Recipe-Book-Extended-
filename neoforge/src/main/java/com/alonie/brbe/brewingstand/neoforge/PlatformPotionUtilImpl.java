package com.alonie.brbe.brewingstand.neoforge;

import com.alonie.brbe.brewingstand.PlatformPotionUtil;
import com.alonie.brbe.neoforge.Mixins.Accessors.NeoForgePotionBrewingAccessor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.List;

public class PlatformPotionUtilImpl implements PlatformPotionUtil.PotionUtilProvider {

    public static void init() {
        PlatformPotionUtil.setProvider(new PlatformPotionUtilImpl());
    }

    @Override
    public Ingredient getIngredient(Object recipe) {
        PotionBrewing.Mix<?> mix = (PotionBrewing.Mix<?>) recipe;
        return mix.ingredient();
    }

    @Override
    public Potion getTo(Object recipe) {
        PotionBrewing.Mix<Potion> mix = (PotionBrewing.Mix<Potion>) recipe;
        return mix.to().value();
    }

    @Override
    public Potion getFrom(Object recipe) {
        PotionBrewing.Mix<Potion> mix = (PotionBrewing.Mix<Potion>) recipe;
        return mix.from().value();
    }

    @Override
    public List<?> getPotionMixes(ClientLevel level) {
        PotionBrewing brewing = level.potionBrewing();
        return ((NeoForgePotionBrewingAccessor) brewing).getPotionMixes();
    }
}
