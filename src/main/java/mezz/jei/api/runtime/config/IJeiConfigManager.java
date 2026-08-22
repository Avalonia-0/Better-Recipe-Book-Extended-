/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.jetbrains.annotations.Unmodifiable
 */
package mezz.jei.api.runtime.config;

import java.util.Collection;
import mezz.jei.api.runtime.config.IJeiConfigFile;
import org.jetbrains.annotations.Unmodifiable;

public interface IJeiConfigManager {
    public @Unmodifiable Collection<IJeiConfigFile> getConfigFiles();
}

