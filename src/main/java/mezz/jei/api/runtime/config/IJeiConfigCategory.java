/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.jetbrains.annotations.Unmodifiable
 */
package mezz.jei.api.runtime.config;

import java.util.Collection;
import mezz.jei.api.runtime.config.IJeiConfigValue;
import org.jetbrains.annotations.Unmodifiable;

public interface IJeiConfigCategory {
    public String getName();

    public @Unmodifiable Collection<? extends IJeiConfigValue<?>> getConfigValues();
}

