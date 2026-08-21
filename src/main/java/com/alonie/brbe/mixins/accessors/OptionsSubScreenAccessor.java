package com.alonie.brbe.mixins.accessors;

import net.minecraft.client.gui.components.OptionsList;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(OptionsSubScreen.class)
public interface OptionsSubScreenAccessor {

    /** {@code OptionsSubScreen.list}（protected 父类字段，SoundOptionsScreen mixin 无法直接 @Shadow）。 */
    @Accessor("list")
    OptionsList brbe$getList();
}
