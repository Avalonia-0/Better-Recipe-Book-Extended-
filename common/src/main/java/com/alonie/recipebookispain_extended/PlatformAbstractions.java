package com.alonie.recipebookispain_extended;

import java.nio.file.Path;

public interface PlatformAbstractions {
    boolean isModLoaded(String modId);
    Path getConfigDir();
}
