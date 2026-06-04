package com.alonie.brbe.brewingstand.neoforge;

import com.alonie.brbe.brewingstand.PlatformPotionUtil;
import com.alonie.brbe.neoforge.Mixins.Accessors.NeoForgePotionBrewingAccessor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Holder;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.crafting.Ingredient;

import java.lang.reflect.Field;
import java.util.List;

public class PlatformPotionUtilImpl implements PlatformPotionUtil.PotionUtilProvider {
    private static final Field FIELD_INGREDIENT;
    private static final Field FIELD_TO;
    private static final Field FIELD_FROM;

    static {
        try {
            var mixClass = Class.forName("net.minecraft.world.item.alchemy.PotionBrewing$Mix");
            FIELD_INGREDIENT = mixClass.getDeclaredField("ingredient");
            FIELD_INGREDIENT.setAccessible(true);
            FIELD_TO = mixClass.getDeclaredField("to");
            FIELD_TO.setAccessible(true);
            FIELD_FROM = mixClass.getDeclaredField("from");
            FIELD_FROM.setAccessible(true);
        } catch (Exception e) {
            throw new RuntimeException("Failed to init PotionBrewing.Mix reflection", e);
        }
    }

    public static void init() {
        PlatformPotionUtil.setProvider(new PlatformPotionUtilImpl());
    }

    @Override
    public Ingredient getIngredient(Object recipe) {
        try { return (Ingredient) FIELD_INGREDIENT.get(recipe); }
        catch (Exception e) { throw new RuntimeException(e); }
    }

    @Override
    @SuppressWarnings("unchecked")
    public Potion getTo(Object recipe) {
        try { return ((Holder<Potion>) FIELD_TO.get(recipe)).value(); }
        catch (Exception e) { throw new RuntimeException(e); }
    }

    @Override
    @SuppressWarnings("unchecked")
    public Potion getFrom(Object recipe) {
        try { return ((Holder<Potion>) FIELD_FROM.get(recipe)).value(); }
        catch (Exception e) { throw new RuntimeException(e); }
    }

    @Override
    public List<?> getPotionMixes(ClientLevel level) {
        PotionBrewing brewing = level.potionBrewing();
        return ((NeoForgePotionBrewingAccessor) brewing).getPotionMixes();
    }
}
