package com.alonie.brbe.compat;

/**
 * Utility for hiding/showing REI and/or JEI overlays on container screens.
 * <p>
 * Uses reflection to avoid compile-time dependencies on either mod.
 * All state changes are in-memory only — no config files are modified.
 */
public class OverlayHider {

    // --- Class cache (one-time resolution) ---
    private static Class<?> reiConfigClass;
    private static Class<?> jeiToggleStateClass;
    private static boolean classCacheResolved;

    // --- REI state ---
    private static boolean reiHidden = false;

    // --- JEI saved state (snapshot before hiding, restored on unhide) ---
    private static Boolean savedOverlayEnabled;
    private static Boolean savedBookmarkEnabled;
    private static Boolean savedCheatEnabled;

    /**
     * Reads JEI's current IClientToggleState via reflection.
     * Returns null if JEI is not loaded or not ready.
     */
    private static Object getJeiToggleState() {
        try {
            return Class.forName("mezz.jei.common.Internal")
                    .getMethod("getClientToggleState").invoke(null);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Snapshots JEI's current overlay/bookmark/cheat state into saved fields.
     */
    private static void saveJeiState() {
        Class<?> tsClass = getJeiToggleStateClass();
        if (tsClass == null) return;
        Object ts = getJeiToggleState();
        if (ts == null) return;

        try {
            savedOverlayEnabled = (Boolean) tsClass.getMethod("isOverlayEnabled").invoke(ts);
            savedBookmarkEnabled = (Boolean) tsClass.getMethod("isBookmarkOverlayEnabled").invoke(ts);
            savedCheatEnabled = (Boolean) tsClass.getMethod("isCheatItemsEnabled").invoke(ts);
        } catch (Exception e) {
            savedOverlayEnabled = savedBookmarkEnabled = savedCheatEnabled = null;
        }
    }

    /**
     * Call on every tick while a screen is open and config says hide.
     * Reads JEI's actual toggle state each tick and enforces our desired state.
     */
    public static void ensureJeiOverlayHidden() {
        Class<?> tsClass = getJeiToggleStateClass();
        if (tsClass == null) return;

        Object ts = getJeiToggleState();
        if (ts == null) return;

        try {
            if ((Boolean) tsClass.getMethod("isOverlayEnabled").invoke(ts)) {
                tsClass.getMethod("toggleOverlayEnabled").invoke(ts);
            }
            if ((Boolean) tsClass.getMethod("isBookmarkOverlayEnabled").invoke(ts)) {
                tsClass.getMethod("toggleBookmarkEnabled").invoke(ts);
            }
            if ((Boolean) tsClass.getMethod("isCheatItemsEnabled").invoke(ts)) {
                tsClass.getMethod("toggleCheatItemsEnabled").invoke(ts);
            }
        } catch (Exception e) {
            // JEI not ready yet — will retry next tick
        }
    }

    /**
     * Restores JEI's overlay/bookmark/cheat to the saved snapshot.
     */
    private static void restoreJeiState() {
        if (savedOverlayEnabled == null && savedBookmarkEnabled == null && savedCheatEnabled == null) {
            return; // Nothing was ever saved — don't touch JEI
        }

        Class<?> tsClass = getJeiToggleStateClass();
        if (tsClass == null) return;
        Object ts = getJeiToggleState();
        if (ts == null) return;

        try {
            if (savedOverlayEnabled != null) {
                boolean current = (Boolean) tsClass.getMethod("isOverlayEnabled").invoke(ts);
                if (current != savedOverlayEnabled) {
                    tsClass.getMethod("toggleOverlayEnabled").invoke(ts);
                }
            }
            if (savedBookmarkEnabled != null) {
                boolean current = (Boolean) tsClass.getMethod("isBookmarkOverlayEnabled").invoke(ts);
                if (current != savedBookmarkEnabled) {
                    tsClass.getMethod("toggleBookmarkEnabled").invoke(ts);
                }
            }
            if (savedCheatEnabled != null) {
                boolean current = (Boolean) tsClass.getMethod("isCheatItemsEnabled").invoke(ts);
                if (current != savedCheatEnabled) {
                    tsClass.getMethod("toggleCheatItemsEnabled").invoke(ts);
                }
            }
        } catch (Exception e) {
            // Silently ignore
        }

        // Clear saved state so a future hide→unhide cycle re-snapshots
        savedOverlayEnabled = savedBookmarkEnabled = savedCheatEnabled = null;
    }

    // --- REI control ---

    public static void hideReiOverlay() {
        if (!isReiLoaded() || reiHidden) return;
        try {
            Object instance = getReiConfigClass().getMethod("getInstance").invoke(null);
            getReiConfigClass().getMethod("setOverlayVisible", boolean.class).invoke(instance, false);
            reiHidden = true;
        } catch (ReflectiveOperationException e) {
            // Silently ignore
        }
    }

    public static void showReiOverlay() {
        if (!isReiLoaded() || !reiHidden) return;
        try {
            Object instance = getReiConfigClass().getMethod("getInstance").invoke(null);
            getReiConfigClass().getMethod("setOverlayVisible", boolean.class).invoke(instance, true);
            reiHidden = false;
        } catch (ReflectiveOperationException e) {
            // Silently ignore
        }
    }

    // --- Convenience methods for client initializers ---

    public static boolean isApplicable() {
        return isReiLoaded() || (getJeiToggleStateClass() != null);
    }

    public static void setOverlaysHidden(boolean hide) {
        if (hide) {
            saveJeiState();
            hideReiOverlay();
            ensureJeiOverlayHidden();
        } else {
            showReiOverlay();
            restoreJeiState();
        }
    }

    // === Internal: class resolution (lazy, one-time) ===

    private static boolean isReiLoaded() {
        return getReiConfigClass() != null;
    }

    private static Class<?> getReiConfigClass() {
        if (reiConfigClass == null && !classCacheResolved) {
            try {
                reiConfigClass = Class.forName("me.shedaniel.rei.api.client.config.ConfigObject");
            } catch (ClassNotFoundException ignored) {}
        }
        return reiConfigClass;
    }

    private static Class<?> getJeiToggleStateClass() {
        if (jeiToggleStateClass == null) {
            resolveClassCache();
        }
        return jeiToggleStateClass;
    }

    private static synchronized void resolveClassCache() {
        if (classCacheResolved) return;
        classCacheResolved = true;
        try {
            Class.forName("mezz.jei.common.Internal");
            jeiToggleStateClass = Class.forName("mezz.jei.common.config.IClientToggleState");
        } catch (ClassNotFoundException e) {
            jeiToggleStateClass = null;
        }
    }

    /**
     * Reset all tracked state (e.g., on game restart or screen manager reset).
     */
    public static void reset() {
        reiHidden = false;
    }
}
