package com.alonie.brbe.pin;

import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/**
 * Storage abstraction for pinned recipe IDs.
 *
 * <p>Separates the "what" (pin storage) from the "how" (JSON file I/O),
 * enabling testing with an in-memory implementation and future migration
 * to other storage formats.</p>
 */
public interface PinStore {

    /** Load all pinned recipe IDs from persistent storage. */
    Set<ResourceLocation> load();

    /** Save pinned recipe IDs to persistent storage (may be async). */
    void save(Set<ResourceLocation> pinned);
}
