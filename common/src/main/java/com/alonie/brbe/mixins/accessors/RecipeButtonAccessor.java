package com.alonie.brbe.mixins.accessors;

import net.minecraft.client.gui.screens.recipebook.RecipeButton;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * 暴露 {@link RecipeButton} 的私有格子贴图与配方变体判断，供合成台翻页动画的
 * 固定边缘列（方案二：固定格子边框 + 滑动物品图标）使用。
 */
@Mixin(RecipeButton.class)
public interface RecipeButtonAccessor {

    @Accessor("SLOT_CRAFTABLE_SPRITE")
    Identifier brbe$getCraftableSprite();

    @Accessor("SLOT_UNCRAFTABLE_SPRITE")
    Identifier brbe$getUncraftableSprite();

    @Accessor("SLOT_MANY_CRAFTABLE_SPRITE")
    Identifier brbe$getManyCraftableSprite();

    @Accessor("SLOT_MANY_UNCRAFTABLE_SPRITE")
    Identifier brbe$getManyUncraftableSprite();

    @Invoker("hasMultipleRecipes")
    boolean brbe$hasMultipleRecipes();
}
