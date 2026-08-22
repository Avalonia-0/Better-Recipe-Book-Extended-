/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.jetbrains.annotations.Unmodifiable
 */
package mezz.jei.api.runtime.config;

import java.nio.file.Path;
import java.util.List;
import mezz.jei.api.runtime.config.IJeiConfigCategory;
import org.jetbrains.annotations.Unmodifiable;

public interface IJeiConfigFile {
    public Path getPath();

    public @Unmodifiable List<? extends IJeiConfigCategory> getCategories();
}

