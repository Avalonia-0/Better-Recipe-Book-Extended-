package com.alonie.recipebookispain_extended.access;

import net.minecraft.world.item.CreativeModeTab;

import java.util.Optional;

public interface ItemAccess {
    Optional<CreativeModeTab> rbip$getPossibleGroup();
    void rbip$setPossibleGroup(CreativeModeTab group);
}
