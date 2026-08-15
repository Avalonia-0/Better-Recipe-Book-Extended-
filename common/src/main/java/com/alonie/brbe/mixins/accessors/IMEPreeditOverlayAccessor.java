package com.alonie.brbe.mixins.accessors;

import net.minecraft.client.gui.components.IMEPreeditOverlay;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(IMEPreeditOverlay.class)
public interface IMEPreeditOverlayAccessor {

    /** IME 组合中的文本（尚未 commit 进 EditBox.value）。 */
    @Accessor("preEditText")
    Component getPreEditText();
}
