package com.alonie.brbe.fabric.compat.rei;

import com.alonie.brbe.compat.rei.ReiCompat;

/**
 * Fabric-side REI handler registration.
 * Delegates to common ReiCompat.register() which uses reflection.
 */
public class ReiCompatHandler {

    public static void register() {
        ReiCompat.register();
    }
}
