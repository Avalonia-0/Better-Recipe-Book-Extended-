/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.gui.screens.Screen
 *  org.jspecify.annotations.Nullable
 */
package mezz.jei.api.gui.handlers;

import java.util.function.Function;
import mezz.jei.api.gui.handlers.IGuiProperties;
import net.minecraft.client.gui.screens.Screen;
import org.jspecify.annotations.Nullable;

@FunctionalInterface
public interface IScreenHandler<T extends Screen>
extends Function<T, IGuiProperties> {
    @Override
    public @Nullable IGuiProperties apply(T var1);
}

