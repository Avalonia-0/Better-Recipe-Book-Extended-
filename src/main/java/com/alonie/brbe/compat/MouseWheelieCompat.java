package com.alonie.brbe.compat;

import com.alonie.brbe.mixins.accessors.AbstractRecipeBookScreenAccessor;
import com.alonie.brbe.mixins.accessors.RecipeBookComponentAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;

/**
 * Geometry helper for the Mouse Wheelie compat mixin
 * ({@code compat.mixins.mousewheelie.MixinMWClient}).
 *
 * <p>Mouse Wheelie (1.16.x, 26.x) implements its own recipe-book wheel
 * handling in {@code MixinRecipeBookWidget.mouseWheelie_scrollRecipeBook}:
 * inside the book rectangle it flips the recipe page itself, and on the 30px
 * strip left of the book it switches the <em>selected tab</em>.  Its scroll
 * keys are amecs priority key bindings, so handling a scroll also makes amecs
 * cancel vanilla {@code MouseHandler.onScroll} — which is where BRBE queues
 * its own page scroll ({@code MouseScrollHandler}, RETURN injection).  Net
 * effect in a modpack: the page flips instantly (no BRBE slide animation, no
 * flip sound) and scrolling over the tab strip silently selects another tab.
 *
 * <p>BRBE implements the recipe-book scroll itself, so it claims the gesture
 * back for the book area and the tab strip, and leaves every other mouse
 * position (container slots, creative inventory, …) to Mouse Wheelie.
 */
public final class MouseWheelieCompat {

    /** Vanilla recipe-book panel size — the same rectangle Mouse Wheelie uses. */
    private static final int BOOK_WIDTH = 147;
    private static final int BOOK_HEIGHT = 166;
    /** Tab strip left of the book (RBIP's own tab column lives here too). */
    private static final int TAB_STRIP_WIDTH = 30;

    private MouseWheelieCompat() {}

    /**
     * Whether BRBE owns the recipe-book scroll at this cursor position: an open
     * recipe-book screen with a visible book, cursor inside the book panel or
     * on the tab strip left of it.
     */
    public static boolean brbeOwnsRecipeBookScroll(double mouseX, double mouseY) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.gui == null) {
            return false;
        }
        Screen screen = minecraft.gui.screen();
        if (!(screen instanceof AbstractRecipeBookScreen<?> recipeBookScreen)) {
            return false;
        }
        RecipeBookComponent<?> component =
                ((AbstractRecipeBookScreenAccessor) recipeBookScreen).brbe$getRecipeBookComponent();
        if (component == null || !component.isVisible()) {
            return false;
        }
        RecipeBookComponentAccessor accessor = (RecipeBookComponentAccessor) component;
        int left = accessor.brbe$invokeGetXOrigin();
        int top = accessor.brbe$invokeGetYOrigin();
        if (mouseY < top || mouseY >= top + BOOK_HEIGHT) {
            return false;
        }
        return mouseX >= left - TAB_STRIP_WIDTH && mouseX < left + BOOK_WIDTH;
    }
}
