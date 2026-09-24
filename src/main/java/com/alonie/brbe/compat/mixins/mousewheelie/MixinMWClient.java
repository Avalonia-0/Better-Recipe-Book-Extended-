package com.alonie.brbe.compat.mixins.mousewheelie;

import com.alonie.brbe.compat.MouseWheelieCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "de.siphalor.mousewheelie.client.MWClient")
public class MixinMWClient {

    @Inject(remap = false,
            method = "triggerScroll",
            at = @At(value = "INVOKE", target = "Lde/siphalor/mousewheelie/client/util/inject/IScrollableRecipeBook;mouseWheelie_onMouseScrollRecipeBook(DDD)Lde/siphalor/mousewheelie/client/util/ScrollAction;"),
            cancellable = true)
    private static void onTriggerScroll(double mouseX, double mouseY, double scrollY, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(false);
        cir.cancel();
    }

    /**
     * Same intent as the injection above, but for the path Mouse Wheelie
     * 1.16.x (26.x) actually uses.  There {@code IScrollableRecipeBook} is
     * implemented by nobody — the recipe-book scroll moved to
     * {@code ISpecialScrollableScreen} ({@code MixinAbstractRecipeBookScreen})
     * → {@code IRecipeBookWidget.mouseWheelie_scrollRecipeBook}, which the
     * injection above never sees.  Since mousewheelie's scroll keys are amecs
     * <em>priority</em> bindings, handling a scroll there also cancels vanilla
     * {@code MouseHandler.onScroll} — the method BRBE's page scroll is queued
     * from — so BRBE's flip animation and flip sound disappeared and the wheel
     * switched the selected tab over the tab strip.  Returning {@code false} =
     * "not handled": vanilla scrolling proceeds, BRBE queues and flips itself.
     *
     * <p>{@code require = 0}: on a Mouse Wheelie version without
     * {@code triggerScroll} the compat degrades to the legacy injection.
     */
    @Inject(remap = false, require = 0,
            method = "triggerScroll",
            at = @At("HEAD"),
            cancellable = true)
    private static void brbe$letBrbeHandleRecipeBookScroll(double mouseX, double mouseY, double scrollY, CallbackInfoReturnable<Boolean> cir) {
        if (MouseWheelieCompat.brbeOwnsRecipeBookScroll(mouseX, mouseY)) {
            cir.setReturnValue(false);
        }
    }

}
