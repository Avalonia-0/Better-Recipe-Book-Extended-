/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
 *  net.minecraft.client.renderer.Rect2i
 */
package mezz.jei.api.gui.handlers;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import mezz.jei.api.gui.builder.IClickableIngredientFactory;
import mezz.jei.api.gui.handlers.IGuiClickableArea;
import mezz.jei.api.runtime.IClickableIngredient;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;

public interface IGuiContainerHandler<T extends AbstractContainerScreen<?>> {
    default public List<Rect2i> getGuiExtraAreas(T containerScreen) {
        return Collections.emptyList();
    }

    default public Optional<? extends IClickableIngredient<?>> getClickableIngredientUnderMouse(IClickableIngredientFactory builder, T containerScreen, double mouseX, double mouseY) {
        return this.getClickableIngredientUnderMouse(containerScreen, mouseX, mouseY);
    }

    @Deprecated(forRemoval=true, since="21.2.0")
    default public Optional<IClickableIngredient<?>> getClickableIngredientUnderMouse(T containerScreen, double mouseX, double mouseY) {
        return Optional.empty();
    }

    default public Collection<IGuiClickableArea> getGuiClickableAreas(T containerScreen, double guiMouseX, double guiMouseY) {
        return Collections.emptyList();
    }
}

