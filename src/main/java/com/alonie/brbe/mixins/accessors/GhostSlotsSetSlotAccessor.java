package com.alonie.brbe.mixins.accessors;

import net.minecraft.client.gui.screens.recipebook.GhostSlots;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * BRBE fuel-fill ghost: pushes a SlotDisplay straight into the vanilla
 * {@code GhostSlots} map (its private {@code setSlot}), so the interface's own
 * ghost rendering paints the fuel preview with the generic ghost look (red
 * wash + item + white overlay) — the same look as the recipe book's ghost
 * ingredients.
 */
@Mixin(GhostSlots.class)
public interface GhostSlotsSetSlotAccessor {

    @Invoker("setSlot")
    void brbe$setSlot(Slot slot, ContextMap context, SlotDisplay contents, boolean isResult);
}
