package com.alonie.brbe.brewingstand;

import com.alonie.brbe.api.BRBBookCategories;
import com.alonie.brbe.generic.GenericRecipe;
import com.alonie.brbe.util.ClientCompat;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;

import java.util.List;

import static com.alonie.brbe.brewingstand.PlatformPotionUtil.*;

public class BrewableResult implements GenericRecipe {
    public Object recipe;
    public Identifier input;

    public BrewableResult(Object recipe) {
        this.recipe = recipe;
        this.input = BuiltInRegistries.POTION.getKey(getFrom(recipe));
    }

    public boolean hasIngredient(List<Slot> slots, ItemStack carried) {
        for (ItemStack itemStack : ClientCompat.ingredientItems(getIngredient(recipe))) {
            if (!carried.isEmpty() && itemStack.getItem().equals(carried.getItem())) return true;
            for (Slot slot : slots) {
                if (itemStack.getItem().equals(slot.getItem().getItem())) return true;
            }
        }
        return false;
    }

    /** 配方消耗的<b>基底物品</b>（26.3 配方自带 input.item）；旧版数据无形态信息时为 null。 */
    public Item inputItem() {
        return getInputItem(recipe);
    }

    /** 配方产出的物品形态（26.3 的"火药→喷溅""龙息→滞留"会与输入形态不同）。 */
    public Item outputItem() {
        return getOutputItem(recipe);
    }

    /**
     * 该配方是否属于给定标签页。
     *
     * <p>26.3 的酿造配方表把三种物品形态（普通/喷溅/滞留药水）放在同一份
     * {@code minecraft:brewing} 配方里：同一个"药水→药水"转换会有三条内容完全
     * 相同、只有 {@code input.item}/{@code output.id} 不同的配方。旧版
     * {@code PotionBrewing.Mix} 不含物品形态，形态完全由标签页决定，所以列出
     * 全部条目是对的；26.3 若不按形态过滤，同一个标签页就会把三种形态全部列出，
     * 而结果图标又统一按标签页物品绘制 → <b>三个一模一样的配方</b>。因此这里按
     * <b>基底物品</b>（设计蓝图 §2.8「根据基底物品类型区分」）归属标签页。</p>
     *
     * <p>形态未知（{@link #inputItem()} 为 null，即 26.2/1.21.11 的数据）时恒
     * true，旧分支行为不变。</p>
     */
    public boolean belongsToTab(BRBBookCategories.Category category) {
        Item input = inputItem();
        return input == null || input == category.getItemIcons().getFirst().getItem();
    }

    /** 输入物品形态：优先配方自带（26.3），否则用标签页物品（旧版语义）。 */
    public Item inputFormItem(BRBBookCategories.Category category) {
        return formItem(inputItem(), category);
    }

    /** 输出物品形态：优先配方自带（26.3），否则用标签页物品（旧版语义）。 */
    public Item outputFormItem(BRBBookCategories.Category category) {
        return formItem(outputItem(), category);
    }

    private static Item formItem(Item recipeItem, BRBBookCategories.Category category) {
        return recipeItem != null ? recipeItem : category.getItemIcons().getFirst().getItem();
    }

    public ItemStack inputAsItemStack(BRBBookCategories.Category category) {
        Potion inputPotion = getFrom(recipe);
        return potionStackFromPotion(inputFormItem(category), inputPotion);
    }

    public boolean hasInput(BRBBookCategories.Category category, List<Slot> slots, ItemStack carried) {
        ItemStack inputStack = inputAsItemStack(category);

        if (!carried.isEmpty() && ItemStack.isSameItemSameComponents(inputStack, carried))
            return true;

        for (Slot slot : slots) {
            ItemStack itemStack = slot.getItem();

            if (ItemStack.isSameItemSameComponents(inputStack, itemStack))
                return true;
        }

        return false;
    }

    public boolean hasMaterials(BRBBookCategories.Category category, List<Slot> slots, ItemStack carried) {
        boolean hasIngredient = hasIngredient(slots, carried);
        boolean hasInput = hasInput(category, slots, carried);

        return hasIngredient && hasInput;
    }

    public boolean hasPartialMaterials(BRBBookCategories.Category category, List<Slot> slots, ItemStack carried) {
        return hasIngredient(slots, carried) || hasInput(category, slots, carried);
    }

    @Override
    public Identifier id() {
        return BuiltInRegistries.POTION.getKey(getTo(recipe));
    }

    public Component getHoverName(BRBBookCategories.Category category) {
        var resultPotion = getTo(recipe);
        return potionStackFromPotion(outputFormItem(category), resultPotion).getHoverName();
    }

    @Override
    public ItemStack getResult(RegistryAccess registryAccess, BRBBookCategories.Category category) {
        var resultPotion = getTo(recipe);
        return potionStackFromPotion(outputFormItem(category), resultPotion);
    }

    @Override
    public String getSearchString(BRBBookCategories.Category category) {
        return getHoverName(category).getString();
    }

    public static ItemStack potionStackFromPotion(Item item, Potion pot) {
        return PotionContents.createItemStack(item, BuiltInRegistries.POTION.wrapAsHolder(pot));
    }
}
