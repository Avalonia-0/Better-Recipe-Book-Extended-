package com.alonie.brbe.mixins.accessors;

import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.world.item.crafting.ExtendedRecipeBookCategory;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;
import java.util.Map;

@Mixin(ClientRecipeBook.class)
public interface ClientRecipeBookAccessor {
    @Accessor("known")
    Map<RecipeDisplayId, RecipeDisplayEntry> brbe$getKnown();

    /** 按标签页分类的集合表 —— RBIP 会把它换成自己新建的集合对象
     *  （{@code getCollections()} 返回的 {@code allCollections} 看不到这些对象）。 */
    @Accessor("collectionsByTab")
    Map<ExtendedRecipeBookCategory, List<RecipeCollection>> brbe$getCollectionsByTab();
}
