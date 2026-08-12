package com.alonie.brbe.mixins.accessors;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {
    @Accessor("hoveredSlot")
    Slot brbe$getHoveredSlot();

    @Accessor("leftPos")
    int brbe$getLeftPos();

    @Accessor("leftPos")
    void brbe$setLeftPos(int leftPos);

    @Accessor("topPos")
    int brbe$getTopPos();

    @Accessor("imageWidth")
    int brbe$getImageWidth();
}
