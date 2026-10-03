package com.alonie.brbe.smithingtable;

import com.alonie.brbe.api.BRBBookCategories;
import com.alonie.brbe.generic.GenericRecipeButton;
import com.alonie.brbe.recipe.BRBSmithingRecipe;
import com.alonie.brbe.recipe.smithing.BRBSmithingTrimRecipe;
import com.alonie.brbe.util.ClientCompat;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Supplier;

/**
 * 锻造台配方书的单元格按钮：**纹饰组显示"这一组用的纹饰模板"**（用户 2026-09-26 诉求）。
 *
 * <p>原版一条 {@code minecraft:smithing_trim} 配方的 {@code base} 是
 * {@code #minecraft:trimmable_armor} 标签 → {@link BRBSmithingTrimRecipe#from} 把它展开成
 * "每种可纹饰装备一件"的一整组（头盔/胸甲/护腿/靴子/马铠…）。折叠状态下的单元格原本按
 * {@code time} 轮循这些产物——一眼看不出这一格是什么，而整组唯一共用的东西是**纹饰模板**
 * （如 {@code minecraft:bolt_armor_trim_smithing_template}）。</p>
 *
 * <p>只改**展示**（单元格物品 + tooltip）：内部"当前配方"照旧轮循，所以</p>
 * <ul>
 *   <li>左键点击放置的仍是轮循到的那件（与原版"组按钮 = 当前变体"同义）；</li>
 *   <li>悬停的幽灵预览照旧显示当前变体（模板 + 基底 + 材料）；</li>
 *   <li>右键展开的组浮层里，每个变体仍各自显示自己的产物（选择装备部位的地方）。</li>
 * </ul>
 *
 * <p>"升级组"（{@link com.alonie.brbe.recipe.smithing.BRBSmithingTransformRecipe}，如下界合金升级）
 * 的 base 是具体物品、组内只有一条 → 不满足下面的判定，行为不变。</p>
 */
public class SmithingRecipeButton extends GenericRecipeButton<SmithingRecipeCollection, BRBSmithingRecipe, SmithingMenu> {

    /** 本格所属纹饰组共用的模板物品；{@code null} = 不是纹饰组（照旧显示产物）。 */
    @Nullable
    private ItemStack brbe$trimTemplate;

    public SmithingRecipeButton(RegistryAccess registryAccess, Supplier<Boolean> filteringSupplier) {
        super(registryAccess, filteringSupplier);
    }

    @Override
    public void showCollection(SmithingRecipeCollection collection, SmithingMenu menu, BRBBookCategories.Category category) {
        // showCollection 每帧都会被页面调用（renderButtonGrid）→ 只在**换了集合**时重算模板
        boolean collectionChanged = collection != this.getCollection();
        super.showCollection(collection, menu, category);
        if (collectionChanged) {
            this.brbe$trimTemplate = brbe$resolveTrimTemplate(collection);
        }
    }

    @Override
    protected ItemStack getDisplayedStack(BRBBookCategories.Category category) {
        if (brbe$isTrimGroup(this.brbe$trimTemplate)) {
            return this.brbe$trimTemplate;
        }
        return super.getDisplayedStack(category);
    }

    /**
     * 纹饰组**不轮循**：整组共用一个模板展示，内部"当前配方"也固定成第一条
     * （用户 2026-09-26 观察：展示虽然换成了模板，逻辑上仍在轮循组内配方——
     * 合成状态/边框/幽灵会随轮循抖动）。左键已被屏蔽，这里只让状态稳定。
     */
    @Override
    public BRBSmithingRecipe getCurrentDisplayedRecipe() {
        if (brbe$isTrimGroup(this.brbe$trimTemplate)) {
            List<BRBSmithingRecipe> list = this.getOrderedRecipes();
            return list.isEmpty() ? null : list.get(0);
        }
        return super.getCurrentDisplayedRecipe();
    }

    /** 纹饰组不参与「悬停即预览幽灵配方」（用户 2026-09-26 诉求）。 */
    @Override
    public boolean providesHoverPreview() {
        return !brbe$isTrimGroup(this.brbe$trimTemplate);
    }

    /**
     * 纹饰组的**左键无效**（用户 2026-09-26 诉求）：不放置、也不放点击音效
     * （{@code AbstractWidget.mouseClicked} 在音效之前判定，返回 false 就什么都不发生）。
     * 组内变体只能在右键展开的组浮层里点选。
     */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && brbe$isTrimGroup(this.brbe$trimTemplate)) {
            return false;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public List<Component> getTooltipText() {
        // tooltip 跟着展示物品走（否则会出现"画着模板、写的是某件装备"）
        if (brbe$isTrimGroup(this.brbe$trimTemplate)) {
            return this.getTooltipFor(this.brbe$trimTemplate);
        }
        return super.getTooltipText();
    }

    /** 本格是不是"同模板纹饰组"（缓存里的模板非空即成立）。 */
    private static boolean brbe$isTrimGroup(@Nullable ItemStack template) {
        return template != null && !template.isEmpty();
    }

    /**
     * 整组都是纹饰配方、且共用**同一个**模板物品时返回该模板，否则 {@code null}
     * （升级组、混合组、datapack 自定义组一律照旧显示产物）。
     */
    @Nullable
    private static ItemStack brbe$resolveTrimTemplate(@Nullable SmithingRecipeCollection collection) {
        if (collection == null) {
            return null;
        }

        ItemStack template = ItemStack.EMPTY;
        for (BRBSmithingRecipe recipe : collection.getRecipes()) {
            // 1.21.1 的 BRBSmithingRecipe 没有 requiresTemplate()（模板恒在；空模板由下面的
            // firstIngredientItem 返回空栈兜住）
            if (!(recipe instanceof BRBSmithingTrimRecipe trim)) {
                return null;
            }
            ItemStack stack = ClientCompat.firstIngredientItem(trim.getTemplate());
            if (stack.isEmpty()) {
                return null;
            }
            if (template.isEmpty()) {
                template = stack;
            } else if (!ItemStack.isSameItemSameComponents(template, stack)) {
                // 一组里混了不同模板（理论上不会发生）→ 不替换展示，避免给出错误信息
                return null;
            }
        }

        return template.isEmpty() ? null : template;
    }
}
