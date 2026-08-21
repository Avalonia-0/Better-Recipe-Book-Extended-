package com.alonie.recipebookispain_extended.mixin;

import com.alonie.recipebookispain_extended.access.ItemAccess;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.Optional;

@Mixin(Item.class)
public class ItemMixin implements ItemAccess {
    @Unique
    public CreativeModeTab rbip$possibleGroup;

    @Override
    public Optional<CreativeModeTab> rbip$getPossibleGroup() {
        return Optional.ofNullable(rbip$possibleGroup);
    }

    @Override
    public void rbip$setPossibleGroup(CreativeModeTab group) {
        rbip$possibleGroup = group;
    }
}
