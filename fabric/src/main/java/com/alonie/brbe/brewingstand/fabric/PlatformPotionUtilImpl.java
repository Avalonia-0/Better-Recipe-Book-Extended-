package com.alonie.brbe.brewingstand.fabric;

import com.alonie.brbe.brewingstand.PlatformPotionUtil;
import com.alonie.brbe.fabric.Mixins.Accessors.FabricPotionBrewingAccessor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.crafting.Ingredient;

import java.lang.reflect.Method;
import java.util.List;

/**
 * Fabric implementation of PotionUtilProvider.
 *
 * In 26.1.2, PotionBrewing.Mix is a package-private record, so its
 * accessor methods (ingredient(), from(), to()) must be called via
 * reflection from outside the net.minecraft package.
 */
public class PlatformPotionUtilImpl implements PlatformPotionUtil.PotionUtilProvider {

    private static Method METHOD_ingredient;
    private static Method METHOD_from;
    private static Method METHOD_to;

    static {
        try {
            // PotionBrewing$Mix is package-private; its record components are exposed reflectively.
            Class<?> mixClass = Class.forName("net.minecraft.world.item.alchemy.PotionBrewing$Mix");
            METHOD_ingredient = mixClass.getMethod("ingredient");
            METHOD_from = mixClass.getMethod("from");
            METHOD_to = mixClass.getMethod("to");
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to initialize PotionBrewing$Mix reflection", e);
        }
    }

    public static void init() {
        PlatformPotionUtil.setProvider(new PlatformPotionUtilImpl());
    }

    @Override
    public Ingredient getIngredient(Object recipe) {
        try {
            return (Ingredient) METHOD_ingredient.invoke(recipe);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public Potion getTo(Object recipe) {
        try {
            return ((net.minecraft.core.Holder<Potion>) METHOD_to.invoke(recipe)).value();
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public Potion getFrom(Object recipe) {
        try {
            return ((net.minecraft.core.Holder<Potion>) METHOD_from.invoke(recipe)).value();
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    @Override
    public List<?> getPotionMixes(ClientLevel level) {
        PotionBrewing brewing = level.potionBrewing();
        return ((FabricPotionBrewingAccessor) brewing).getPotionMixes();
    }
}
