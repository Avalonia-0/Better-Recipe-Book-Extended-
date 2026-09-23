package com.alonie.brbe.brewingstand;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;

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

    public static List<?> getPotionMixes(Level level) {
        if (provider == null) throw new IllegalStateException("PlatformPotionUtil provider not set");
        return provider.getPotionMixes(level);
    }

    /** 配方消耗的<b>基底物品</b>（普通/喷溅/滞留药水中的哪一种）；数据不含物品
     *  形态时返回 null（旧分支的 {@code PotionBrewing.Mix} 只有药水→药水）。 */
    public static Item getInputItem(Object recipe) {
        return provider == null ? null : provider.getInputItem(recipe);
    }

    /** 配方产出的物品形态；数据不含时返回 null（旧分支）。 */
    public static Item getOutputItem(Object recipe) {
        return provider == null ? null : provider.getOutputItem(recipe);
    }

    public interface PotionUtilProvider {
        Ingredient getIngredient(Object recipe);
        Potion getTo(Object recipe);
        Potion getFrom(Object recipe);
        List<?> getPotionMixes(Level level);

        /**
         * 配方输入（基底）物品——26.3 起酿造配方自带 {@code input.item}，而
         * 26.2/1.21.11 的 {@code PotionBrewing.Mix} 只有"药水→药水"，**没有**
         * 物品形态（形态由配方书的标签页决定）。默认返回 null = 未知，调用方
         * 回退到标签页物品，保持旧分支行为不变。
         */
        default Item getInputItem(Object recipe) {
            return null;
        }

        /** 配方输出物品（同上；26.3 的"火药→喷溅""龙息→滞留"转换会改变形态）。 */
        default Item getOutputItem(Object recipe) {
            return null;
        }
    }
}
