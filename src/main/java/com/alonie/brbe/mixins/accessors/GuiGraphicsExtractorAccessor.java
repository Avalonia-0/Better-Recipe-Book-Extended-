package com.alonie.brbe.mixins.accessors;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Lets BRBE clear the deferred (single-slot) tooltip of a frame, so a pin
 * overlay or the query viewer can fully suppress the container screen's
 * tooltips that were registered during its own render pass.
 */
@Mixin(GuiGraphicsExtractor.class)
public interface GuiGraphicsExtractorAccessor {

    @Accessor("deferredTooltip")
    void brbe$setDeferredTooltip(Runnable tooltip);
}
