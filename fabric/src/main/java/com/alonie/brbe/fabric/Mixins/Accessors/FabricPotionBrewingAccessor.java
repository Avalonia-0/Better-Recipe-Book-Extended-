package com.alonie.brbe.fabric.Mixins.Accessors;

import net.minecraft.world.item.alchemy.PotionBrewing;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(PotionBrewing.class)
public interface FabricPotionBrewingAccessor {
    @Accessor("potionMixes")
    List<?> getPotionMixes();
}
