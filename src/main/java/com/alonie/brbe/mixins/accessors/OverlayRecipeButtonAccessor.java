package com.alonie.brbe.mixins.accessors;

import net.minecraft.client.gui.screens.recipebook.OverlayRecipeComponent;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(targets = "net.minecraft.client.gui.screens.recipebook.OverlayRecipeComponent$OverlayRecipeButton")
public interface OverlayRecipeButtonAccessor {

    // 1.21.11 是 remap 构建：内部类指向外类的合成字段 this$0 经映射后名为
    // field_3113（26.2 no-remap 下仍是 this$0）。用 @Accessor("this$0")
    // 会在 mixin 应用期抛 InvalidAccessorException，导致 R/U 查看浮层无法打开。
    @Accessor("field_3113")
    OverlayRecipeComponent brbe$getOuterComponent();

    @Accessor("recipe")
    RecipeDisplayId brbe$getRecipe();

    @Accessor("isCraftable")
    boolean brbe$getCraftable();

    @Accessor("slots")
    List<?> brbe$getSlots();
}
