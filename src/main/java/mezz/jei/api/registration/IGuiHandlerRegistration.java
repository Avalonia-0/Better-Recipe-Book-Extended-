/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.gui.screens.Screen
 *  net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
 */
package mezz.jei.api.registration;

import java.util.Collection;
import java.util.List;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.gui.handlers.IGlobalGuiHandler;
import mezz.jei.api.gui.handlers.IGuiClickableArea;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.gui.handlers.IScreenHandler;
import mezz.jei.api.helpers.IJeiHelpers;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

public interface IGuiHandlerRegistration {
    public IJeiHelpers getJeiHelpers();

    public <T extends AbstractContainerScreen<?>> void addGuiContainerHandler(Class<? extends T> var1, IGuiContainerHandler<T> var2);

    public <T extends AbstractContainerScreen<?>> void addGenericGuiContainerHandler(Class<? extends T> var1, IGuiContainerHandler<?> var2);

    public <T extends Screen> void addGuiScreenHandler(Class<T> var1, IScreenHandler<T> var2);

    public <T extends Screen> void addGenericGuiScreenHandler(Class<T> var1, IScreenHandler<?> var2);

    public void addGlobalGuiHandler(IGlobalGuiHandler var1);

    default public <T extends AbstractContainerScreen<?>> void addRecipeClickArea(Class<? extends T> containerScreenClass, final int xPos, final int yPos, final int width, final int height, final IRecipeType<?> ... recipeTypes) {
        this.addGuiContainerHandler(containerScreenClass, new IGuiContainerHandler<T>(){

            @Override
            public Collection<IGuiClickableArea> getGuiClickableAreas(T containerScreen, double mouseX, double mouseY) {
                IGuiClickableArea clickableArea = IGuiClickableArea.createBasic(xPos, yPos, width, height, recipeTypes);
                return List.of(clickableArea);
            }
        });
    }

    public <T extends Screen> void addGhostIngredientHandler(Class<T> var1, IGhostIngredientHandler<T> var2);
}

