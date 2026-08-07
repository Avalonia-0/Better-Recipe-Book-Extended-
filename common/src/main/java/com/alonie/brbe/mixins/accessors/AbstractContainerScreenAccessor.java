package com.alonie.brbe.mixins.accessors;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {

    @Accessor("leftPos")
    int getLeftPos();

    @Accessor("leftPos")
    void setLeftPos(int value);

    @Accessor("imageWidth")
    int getImageWidth();

    @Invoker("renderFloatingItem")
    void brbe$renderFloatingItem(GuiGraphics guiGraphics, ItemStack itemStack, int x, int y, String string);
}
