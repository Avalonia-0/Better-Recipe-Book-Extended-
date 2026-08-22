/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.renderer.Rect2i
 */
package mezz.jei.api.gui.handlers;

import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import mezz.jei.api.gui.builder.IClickableIngredientFactory;
import mezz.jei.api.runtime.IClickableIngredient;
import net.minecraft.client.renderer.Rect2i;

public interface IGlobalGuiHandler {
    default public Collection<Rect2i> getGuiExtraAreas() {
        return Collections.emptyList();
    }

    default public Optional<? extends IClickableIngredient<?>> getClickableIngredientUnderMouse(IClickableIngredientFactory builder, double mouseX, double mouseY) {
        return this.getClickableIngredientUnderMouse(mouseX, mouseY);
    }

    @Deprecated(forRemoval=true, since="21.2.0")
    default public Optional<IClickableIngredient<?>> getClickableIngredientUnderMouse(double mouseX, double mouseY) {
        return Optional.empty();
    }
}

