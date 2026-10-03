package com.alonie.brbe.smithingtable;

import com.alonie.brbe.api.BRBBookCategories;
import com.alonie.brbe.generic.GenericRecipeButton;
import com.alonie.brbe.recipe.BRBSmithingRecipe;
import com.alonie.brbe.recipe.smithing.BRBSmithingTrimRecipe;
import com.alonie.brbe.util.ClientCompat;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Supplier;

/**
 * 锻造台配方书的单元格按钮：**折叠的替代配方组显示"整组共用的那件物品"**，
 * 而不是轮循组内各条配方的产物。
 *
 * <p>两种折叠组：</p>
 * <ul>
 *   <li><b>纹饰组</b>（用户 2026-09-26 诉求）：原版一条 {@code minecraft:smithing_trim} 配方的
 *       {@code base} 是 {@code #minecraft:trimmable_armor} 标签 → {@link BRBSmithingTrimRecipe#from}
 *       把它展开成"每种可纹饰装备一件"的一整组（头盔/胸甲/护腿/靴子/马铠…）。折叠状态下的单元格
 *       原本按 {@code time} 轮循这些产物——一眼看不出这一格是什么，而整组唯一共用的东西是
 *       **纹饰模板**（如 {@code minecraft:bolt_armor_trim_smithing_template}）。</li>
 *   <li><b>升级组</b>（用户 2026-09-27 诉求）：凡是用**同一份加成材料**的升级配方
 *       （{@code #minecraft:netherite_tool_materials} = 下界合金锭，含 BetterEnd 锻锤那种
 *       用自家升级模板的配方）都由 {@link SmithingRecipeBookComponent#brbe$groupKey} 折叠成一格，
 *       代表物品取**加成材料（锭）而不是模板**——升级模板不被消耗，玩家真正要备的是锭。</li>
 * </ul>
 *
 * <p>只改**展示**（单元格物品 + tooltip）：内部"当前配方"照旧取组内第一条，所以</p>
 * <ul>
 *   <li>可合成/残缺边框、幽灵预览的状态稳定（不再随轮循抖动）；</li>
 *   <li>右键展开的组浮层里，每个变体仍各自显示自己的产物（选择部位的地方）；</li>
 *   <li>左键被屏蔽（同纹饰组，用户 2026-09-26 诉求）：格子上画的不是产物，点它等于随机放置组内
 *       第一条，容易误操作——要放哪一条请在右键展开的组浮层里点。</li>
 * </ul>
 *
 * <p>组内只有一条配方时不折叠、也不换展示：照旧显示产物（没得选，产物信息量更大）。
 * <b>排序原因剥离出来的子组同样不折叠</b>（{@link SmithingRecipeCollection#isExtractionSubgroup()}，
 * 用户 2026-09-27 三次反馈）——pin 出来的那一格要画自己那条配方的产物。</p>
 */
public class SmithingRecipeButton extends GenericRecipeButton<SmithingRecipeCollection, BRBSmithingRecipe, SmithingMenu> {

    /** 本格所属折叠组的代表物品；{@code null} = 不是折叠组（照旧显示产物）。 */
    @Nullable
    private ItemStack brbe$groupRep;

    public SmithingRecipeButton(RegistryAccess registryAccess, Supplier<Boolean> filteringSupplier) {
        super(registryAccess, filteringSupplier);
    }

    @Override
    public void showCollection(SmithingRecipeCollection collection, SmithingMenu menu, BRBBookCategories.Category category) {
        // showCollection 每帧都会被页面调用（renderButtonGrid）→ 只在**换了集合**时重算代表物品
        boolean collectionChanged = collection != this.getCollection();
        super.showCollection(collection, menu, category);
        if (collectionChanged) {
            this.brbe$groupRep = brbe$resolveGroupRep(collection);
        }
    }

    @Override
    protected ItemStack getDisplayedStack(BRBBookCategories.Category category) {
        if (brbe$isFoldedGroup(this.brbe$groupRep)) {
            return this.brbe$groupRep;
        }
        return super.getDisplayedStack(category);
    }

    /**
     * 折叠组**不轮循**：整组共用一个代表物品展示，内部"当前配方"也固定成第一条
     * （用户 2026-09-26 观察：展示虽然换成了模板，逻辑上仍在轮循组内配方——
     * 合成状态/边框/幽灵会随轮循抖动）。左键已被屏蔽，这里只让状态稳定。
     */
    @Override
    public BRBSmithingRecipe getCurrentDisplayedRecipe() {
        if (brbe$isFoldedGroup(this.brbe$groupRep)) {
            List<BRBSmithingRecipe> list = this.getOrderedRecipes();
            return list.isEmpty() ? null : list.get(0);
        }
        return super.getCurrentDisplayedRecipe();
    }

    /** 折叠组不参与「悬停即预览幽灵配方」（用户 2026-09-26 诉求）。 */
    @Override
    public boolean providesHoverPreview() {
        return !brbe$isFoldedGroup(this.brbe$groupRep);
    }

    /**
     * 折叠组的**左键无效**（用户 2026-09-26 诉求）：不放置、也不放点击音效
     * （{@code AbstractWidget.mouseClicked} 在音效之前判定，返回 false 就什么都不发生）。
     * 组内变体只能在右键展开的组浮层里点选。
     */
    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == ClientCompat.MOUSE_LEFT && brbe$isFoldedGroup(this.brbe$groupRep)) {
            return false;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public List<Component> getTooltipText(BRBSmithingRecipe recipe, BRBBookCategories.Category category) {
        // tooltip 跟着展示物品走（否则会出现"画着模板/锭、写的是某件装备"）
        if (brbe$isFoldedGroup(this.brbe$groupRep)) {
            return this.getTooltipFor(this.brbe$groupRep);
        }
        return super.getTooltipText(recipe, category);
    }

    /** 本格是不是折叠组（缓存里的代表物品非空即成立）。 */
    private static boolean brbe$isFoldedGroup(@Nullable ItemStack rep) {
        return rep != null && !rep.isEmpty();
    }

    /**
     * 折叠组的代表物品，{@code null} = 不是折叠组（升级组、混合组、单条组、
     * datapack 自定义组一律照旧显示产物）。
     *
     * <p>⚠️ <b>剥离子组不折叠</b>（用户 2026-09-27 三次反馈）：排序原因剥离（pin / 可合成 /
     * 残缺 / 搜索）会把组内的变体剥出来单独成格（见 {@link com.alonie.brbe.util.RecipeExtraction}）。
     * 子格是"我挑出来的那几条"——它必须画**自己那几条配方的产物**。此前一律按"整组都是纹饰配方、
     * 共用一个模板"判定，于是从纹饰组里 pin 出来的那一条画的是**纹饰模板**（= 原组那一格的图），
     * 玩家看不出 pin 的是哪件装备。折叠展示只属于**原组那一格**（含剥离后重打包的原组）。</p>
     */
    @Nullable
    private static ItemStack brbe$resolveGroupRep(@Nullable SmithingRecipeCollection collection) {
        if (collection == null) {
            return null;
        }
        if (collection.isExtractionSubgroup()) {
            return null;
        }

        // ① 纹饰组：整组都是纹饰配方、且共用同一个模板 → 展示模板
        ItemStack template = ItemStack.EMPTY;
        boolean allTrim = true;
        for (BRBSmithingRecipe recipe : collection.getRecipes()) {
            if (!(recipe instanceof BRBSmithingTrimRecipe trim) || !trim.requiresTemplate()) {
                allTrim = false;
                break;
            }
            ItemStack stack = ClientCompat.firstIngredientItem(trim.getTemplate());
            if (stack.isEmpty()) {
                allTrim = false;
                break;
            }
            if (template.isEmpty()) {
                template = stack;
            } else if (!ItemStack.isSameItemSameComponents(template, stack)) {
                // 一组里混了不同模板（理论上不会发生）→ 不替换展示，避免给出错误信息
                allTrim = false;
                break;
            }
        }
        if (allTrim) {
            return template.isEmpty() ? null : template;
        }

        // ② 升级组（≥2 条才折叠）：整组共用同一份加成材料 → 展示**加成材料（锭）**。
        //    ⚠️ 升级模板不参与判定（用户 2026-09-27 二次反馈：只看锭相同就同组）——
        //    BetterEnd 的下界合金锻锤用自家升级模板 + 下界合金锭，仍要并进这一组。
        if (collection.getRecipes().size() < 2) {
            return null;
        }
        ItemStack sharedAddition = ItemStack.EMPTY;
        for (BRBSmithingRecipe recipe : collection.getRecipes()) {
            if (recipe instanceof BRBSmithingTrimRecipe || !recipe.requiresTemplate() || !recipe.requiresAddition()) {
                return null;
            }
            ItemStack recipeAddition = ClientCompat.firstIngredientItem(recipe.getAddition());
            if (recipeAddition.isEmpty()) {
                return null;
            }
            if (sharedAddition.isEmpty()) {
                sharedAddition = recipeAddition;
            } else if (!ItemStack.isSameItemSameComponents(sharedAddition, recipeAddition)) {
                return null;
            }
        }
        return sharedAddition.isEmpty() ? null : sharedAddition;
    }
}
