package com.alonie.brbe.mixins.accessors;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.recipebook.OverlayRecipeComponent;
import net.minecraft.client.gui.screens.recipebook.SlotSelectTime;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(OverlayRecipeComponent.class)
public interface OverlayRecipeComponentAccessor {
    @Accessor("recipeButtons")
    List<AbstractWidget> getRecipeButtons();

    @Accessor("x")
    int getX();

    @Accessor("y")
    int getY();

    @Accessor("x")
    void setX(int x);

    @Accessor("y")
    void setY(int y);

    @Accessor("isFurnaceMenu")
    boolean isFurnaceMenu();

    @Accessor("slotSelectTime")
    SlotSelectTime getSlotSelectTime();

    /** 清掉"上次点击的配方"：翻页键点击走的是 {@code mouseClicked → true} 那条路，
     *  调用方会据此去放置配方——不清就会拿旧配方再放一次。 */
    @Accessor("lastRecipeClicked")
    void brbe$setLastRecipeClicked(RecipeDisplayId id);
}
