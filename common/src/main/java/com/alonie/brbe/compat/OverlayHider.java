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

    /**
     * Call on every tick while a screen is open and config says hide.
     * Reads JEI's actual toggle state each tick and enforces our desired state.
     * Stateless — no internal tracking for JEI, just reads reality.
     */
    public static void ensureJeiOverlayHidden() {
        Class<?> tsClass = getJeiToggleStateClass();
        if (tsClass == null) return;

        try {
            Object ts = Class.forName("mezz.jei.common.Internal")
                    .getMethod("getClientToggleState").invoke(null);
            if (ts == null) return;

            // Force overlayEnabled = false
            if ((Boolean) tsClass.getMethod("isOverlayEnabled").invoke(ts)) {
                tsClass.getMethod("toggleOverlayEnabled").invoke(ts);
            }
            // Force bookmarkOverlayEnabled = false
            if ((Boolean) tsClass.getMethod("isBookmarkOverlayEnabled").invoke(ts)) {
                tsClass.getMethod("toggleBookmarkEnabled").invoke(ts);
            }
            // Force cheatItemsEnabled = false
            if ((Boolean) tsClass.getMethod("isCheatItemsEnabled").invoke(ts)) {
                tsClass.getMethod("toggleCheatItemsEnabled").invoke(ts);
            }

        } catch (Exception e) {
            // JEI not ready yet — will retry next tick
        }
    }

    // --- REI control (unchanged) ---

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
            hideReiOverlay();
            ensureJeiOverlayHidden();
        }
        // When hide=false we do NOT restore anything — JEI manages its own state.
        // BRBE should never toggle cheat mode on just because a screen opened.
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
