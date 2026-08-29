package com.alonie.brbe.mixins.accessors;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.stats.RecipeBook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Set;

/**
 * Exposes the recipe book's known recipe-id set.
 *
 * <p>⚠️ 必须 mixin 到字段的<b>声明类</b> {@link RecipeBook}（{@code known} 定义在
 * 此处），不能 mixin ClientRecipeBook——Mixin 的 {@link Accessor} 不解析继承字段，
 * 曾以 ClientRecipeBook 为目标导致启动崩溃
 * 「No candidates were found matching known:Ljava/util/Set」(2026-08-29 实测)。</p>
 */
@Mixin(RecipeBook.class)
public interface RecipeBookAccessor {
    @Accessor("known")
    Set<ResourceLocation> brbe$getKnown();
}
