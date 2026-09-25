package com.alonie.brbe.mixins.accessors;

import net.minecraft.client.gui.screens.recipebook.RecipeButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * 暴露 {@link RecipeButton} 的私有配方变体判断，供合成台翻页动画的固定边缘列
 * （方案二：固定格子边框 + 滑动物品图标）使用。
 *
 * <p><b>为什么不 accessor 那 4 个格子贴图常量</b>：{@code SLOT_*_SPRITE} 是
 * <b>static final</b> 字段，实例 accessor 会让 Mixin 在 latest.log 里刷 4 行
 * {@code should be static as its target is}（INFO）。它们本来就是一串固定的
 * {@code Identifier}，直接按 javap 核出的字面量构造更省事——见
 * {@code RecipeBookPageAnimationMixin} 里的 {@code SLOT_*_SPRITE} 常量。</p>
 */
@Mixin(RecipeButton.class)
public interface RecipeButtonAccessor {

    @Invoker("hasMultipleRecipes")
    boolean brbe$hasMultipleRecipes();

    /** 多配方按钮的结果是否完全相同（原版据此叠加渲染两次图标）。 */
    @Accessor("allRecipesHaveSameResultDisplay")
    boolean brbe$allRecipesHaveSameResultDisplay();

    /** 该按钮当前选中的配方条目（空 = 没有可渲染配方，原版 {@code getCurrentRecipe} 会 /0）。 */
    @Accessor("selectedEntries")
    java.util.List<?> brbe$getSelectedEntries();
}
