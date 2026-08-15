package com.alonie.brbe.mixins.accessors;

import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.IMEPreeditOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(EditBox.class)
public interface EditBoxPreeditAccessor {

    /** IME 组合（preedit）覆盖层；为 null 表示当前无组合输入。 */
    @Accessor("preeditOverlay")
    IMEPreeditOverlay getPreeditOverlay();
}
