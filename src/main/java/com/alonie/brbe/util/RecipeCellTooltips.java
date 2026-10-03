package com.alonie.brbe.util;

import com.alonie.brbe.BetterRecipeBook;
import net.minecraft.client.Minecraft;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 一个**配方格**的 tooltip 行（普通配方格与替代配方组浮层里的格子共用同一套格式，
 * 用户 2026-09-27 诉求：组内格子的 tooltip「就像普通配方那样」）。
 *
 * <p>行序与原版 {@code RecipeButton#getTooltipText} 一致：
 * 物品行 → （可选）「单击鼠标右键获取更多信息」→ 空行 + 模组名。</p>
 *
 * <p>唯一的差别是那行 moreRecipes：它表示**这一格右键能展开更多变体**，
 * 只有组按钮（{@link com.alonie.brbe.generic.GenericRecipeButton}）才有；替代配方组浮层里的
 * 格子本身就是展开后的单个配方，右键不再展开任何东西，故传 {@code moreRecipes=false}。</p>
 */
public final class RecipeCellTooltips {

    private RecipeCellTooltips() {
    }

    /**
     * 由**展示物品**构建单元格 tooltip。
     *
     * @param registryAccess 该配方书书的注册表访问（浮层等场合可传 {@code null}，回退到当前世界）
     * @param result         这一格画出来的那件物品
     * @param moreRecipes    是否追加「单击鼠标右键获取更多信息」（仅组按钮为 true）
     */
    public static List<Component> forStack(@Nullable RegistryAccess registryAccess, ItemStack result,
                                           boolean moreRecipes) {
        List<Component> list = new ArrayList<>();
        if (result.isEmpty()) {
            return list;
        }

        Minecraft minecraft = Minecraft.getInstance();
        Item.TooltipContext tipCtx = registryAccess != null
                ? Item.TooltipContext.of(registryAccess)
                : (minecraft.level != null ? Item.TooltipContext.of(minecraft.level)
                        : Item.TooltipContext.EMPTY);
        list.addAll(result.getTooltipLines(tipCtx, minecraft.player, TooltipFlag.NORMAL));

        if (moreRecipes) {
            list.add(Component.translatable("gui.recipebook.moreRecipes"));
        }

        // Add source mod name (Jade-compatible format: jade.modName.<MOD_ID>)
        if (BetterRecipeBook.config.showModName) {
            Component modName = ModNameUtil.getFormattedModName(result);
            if (modName != null && !modName.getString().isEmpty()) {
                list.add(Component.empty());
                list.add(modName);
            }
        }

        return list;
    }
}
