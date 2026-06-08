package com.alonie.brbe.compat;

import com.alonie.brbe.BetterRecipeBook;

import java.lang.reflect.Field;

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
    private static Class<?> jeiRuntimeClass;
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

            // Also deeply hide the three IconButton instances via setVisible(false)
            hideJeiIconButtons();
        } catch (Exception e) {
            // JEI not ready yet — will retry next tick
        }
    }

    /**
     * Call once when config says show. Restores JEI state.
     */
    public static void restoreJeiOverlay() {
        Class<?> tsClass = getJeiToggleStateClass();
        if (tsClass == null) return;

        try {
            Object ts = Class.forName("mezz.jei.common.Internal")
                    .getMethod("getClientToggleState").invoke(null);
            if (ts == null) return;

            // Restore overlayEnabled = true
            if (!(Boolean) tsClass.getMethod("isOverlayEnabled").invoke(ts)) {
                tsClass.getMethod("toggleOverlayEnabled").invoke(ts);
            }
            // Restore bookmarkOverlayEnabled = true
            if (!(Boolean) tsClass.getMethod("isBookmarkOverlayEnabled").invoke(ts)) {
                tsClass.getMethod("toggleBookmarkEnabled").invoke(ts);
            }
            // Restore cheatItemsEnabled = true
            if (!(Boolean) tsClass.getMethod("isCheatItemsEnabled").invoke(ts)) {
                tsClass.getMethod("toggleCheatItemsEnabled").invoke(ts);
            }

            showJeiIconButtons();
        } catch (Exception e) {
            // Silently ignore
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
        } else {
            showReiOverlay();
            restoreJeiOverlay();
        }
    }

    // === Internal: JEI button hiding via reflection ===

    private static void hideJeiIconButtons() {
        try {
            Class<?> internalClass = Class.forName("mezz.jei.common.Internal");
            Object runtime = internalClass.getMethod("getJeiRuntime").invoke(null);
            if (runtime == null) return;

            Object overlay = runtime.getClass().getMethod("getIngredientListOverlay").invoke(runtime);
            if (overlay != null) {
                setIconButtonVisible(overlay, "configButton", false);
            }

            Object bookmarkOv = runtime.getClass().getMethod("getBookmarkOverlay").invoke(runtime);
            if (bookmarkOv != null) {
                setIconButtonVisible(bookmarkOv, "bookmarkButton", false);
                setIconButtonVisible(bookmarkOv, "historyButton", false);
            }
        } catch (Exception e) {
            // Runtime not ready yet — will retry next tick
        }
    }

    private static void showJeiIconButtons() {
        try {
            Class<?> internalClass = Class.forName("mezz.jei.common.Internal");
            Object runtime = internalClass.getMethod("getJeiRuntime").invoke(null);
            if (runtime == null) return;

            Object overlay = runtime.getClass().getMethod("getIngredientListOverlay").invoke(runtime);
            if (overlay != null) {
                setIconButtonVisible(overlay, "configButton", true);
            }

            Object bookmarkOv = runtime.getClass().getMethod("getBookmarkOverlay").invoke(runtime);
            if (bookmarkOv != null) {
                setIconButtonVisible(bookmarkOv, "bookmarkButton", true);
                setIconButtonVisible(bookmarkOv, "historyButton", true);
            }
        } catch (Exception e) {
            // Silently ignore
        }
    }

    /**
     * Reflectively calls InternalIconButton.setVisible(boolean).
     */
    private static void setIconButtonVisible(Object owningObject, String fieldName, boolean visible) {
        try {
            Field f = owningObject.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            Object iconButton = f.get(owningObject);
            if (iconButton == null) return;

            Field btnField = iconButton.getClass().getDeclaredField("button");
            btnField.setAccessible(true);
            Object internalBtn = btnField.get(iconButton);
            if (internalBtn == null) return;

            internalBtn.getClass().getMethod("setVisible", boolean.class).invoke(internalBtn, visible);
        } catch (Exception e) {
            // Silently ignore
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
            jeiRuntimeClass = Class.forName("mezz.jei.api.runtime.IJeiRuntime");
        } catch (ClassNotFoundException e) {
            jeiToggleStateClass = null;
            jeiRuntimeClass = null;
        }
    }

    /**
     * Reset all tracked state (e.g., on game restart or screen manager reset).
     */
    public static void reset() {
        reiHidden = false;
    }
}
