package com.alonie.brbe.mixins.accessors;

import net.minecraft.client.gui.screens.recipebook.RecipeButton;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

/**
 * 1.21.1 {@link RecipeButton} 私有成员访问器（翻页动画快照渲染 + 导航状态刷新用）。
 *
 * <p>对应 1.21.11 的 RecipeButtonAccessor：{@code getOrderedRecipes} 是私有方法
 * （@Invoker），{@code time}/{@code currentIndex} 是私有字段（@Accessor）。</p>
 */
@Mixin(RecipeButton.class)
public interface RecipeButtonAccessor {

    @Invoker("getOrderedRecipes")
    List<RecipeHolder<?>> brbe$getOrderedRecipes();

    @Accessor("time")
    float brbe$getTime();

    @Accessor("time")
    void brbe$setTime(float time);

    @Accessor("currentIndex")
    int brbe$getCurrentIndex();

    @Accessor("currentIndex")
    void brbe$setCurrentIndex(int currentIndex);
}
