/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.gui.screens.Screen
 *  net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
 *  net.minecraft.client.renderer.Rect2i
 *  org.jetbrains.annotations.ApiStatus$NonExtendable
 */
package mezz.jei.api.runtime;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.gui.handlers.IGuiClickableArea;
import mezz.jei.api.gui.handlers.IGuiProperties;
import mezz.jei.api.runtime.IClickableIngredient;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.NonExtendable
public interface IScreenHelper {
    public Stream<IClickableIngredient<?>> getClickableIngredientUnderMouse(Screen var1, double var2, double var4);

    public <T extends Screen> Optional<IGuiProperties> getGuiProperties(T var1);

    public Stream<IGuiClickableArea> getGuiClickableArea(AbstractContainerScreen<?> var1, double var2, double var4);

    public Stream<Rect2i> getGuiExclusionAreas(Screen var1);

    public <T extends Screen> List<IGhostIngredientHandler<T>> getGhostIngredientHandlers(T var1);
}

