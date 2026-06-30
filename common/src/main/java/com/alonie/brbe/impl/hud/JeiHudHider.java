package com.alonie.brbe.impl.hud;

import com.alonie.brbe.api.hud.HudHider;

/**
 * JEI overlay hider — bridges into {@code mezz.jei.common.config.IClientToggleState}
 * via reflection so no compile-time dependency is needed.
 */
public final class JeiHudHider implements HudHider {

    private static Class<?> jeiToggleStateClass;
    private static boolean classCacheResolved;

    private boolean saved;
    private Boolean savedOverlayEnabled;
    private Boolean savedBookmarkEnabled;
    private Boolean savedCheatEnabled;

    // --- HudHider ---

    @Override
    public void saveState() {
        if (saved) return; // already saved this cycle — don't overwrite
        Class<?> tsClass = getJeiToggleStateClass();
        if (tsClass == null) return;
        Object ts = getJeiToggleState();
        if (ts == null) return;

        try {
            savedOverlayEnabled = (Boolean) tsClass.getMethod("isOverlayEnabled").invoke(ts);
            savedBookmarkEnabled  = (Boolean) tsClass.getMethod("isBookmarkOverlayEnabled").invoke(ts);
            savedCheatEnabled     = (Boolean) tsClass.getMethod("isCheatItemsEnabled").invoke(ts);
            saved = true;
        } catch (Exception e) {
            savedOverlayEnabled = savedBookmarkEnabled = savedCheatEnabled = null;
        }
    }

    @Override
    public void ensureHidden() {
        Class<?> tsClass = getJeiToggleStateClass();
        if (tsClass == null) return;
        Object ts = getJeiToggleState();
        if (ts == null) return;

        try {
            if ((Boolean) tsClass.getMethod("isOverlayEnabled").invoke(ts))
                tsClass.getMethod("toggleOverlayEnabled").invoke(ts);
            if ((Boolean) tsClass.getMethod("isBookmarkOverlayEnabled").invoke(ts))
                tsClass.getMethod("toggleBookmarkEnabled").invoke(ts);
            if ((Boolean) tsClass.getMethod("isCheatItemsEnabled").invoke(ts))
                tsClass.getMethod("toggleCheatItemsEnabled").invoke(ts);
        } catch (Exception e) {
            // JEI not ready yet — retry next tick
        }
    }

    @Override
    public void restoreState() {
        if (!saved) return;
        saved = false;

        if (savedOverlayEnabled == null && savedBookmarkEnabled == null && savedCheatEnabled == null)
            return;

        Class<?> tsClass = getJeiToggleStateClass();
        if (tsClass == null) return;
        Object ts = getJeiToggleState();
        if (ts == null) return;

        try {
            if (savedOverlayEnabled != null) {
                boolean cur = (Boolean) tsClass.getMethod("isOverlayEnabled").invoke(ts);
                if (cur != savedOverlayEnabled) tsClass.getMethod("toggleOverlayEnabled").invoke(ts);
            }
            if (savedBookmarkEnabled != null) {
                boolean cur = (Boolean) tsClass.getMethod("isBookmarkOverlayEnabled").invoke(ts);
                if (cur != savedBookmarkEnabled) tsClass.getMethod("toggleBookmarkEnabled").invoke(ts);
            }
            if (savedCheatEnabled != null) {
                boolean cur = (Boolean) tsClass.getMethod("isCheatItemsEnabled").invoke(ts);
                if (cur != savedCheatEnabled) tsClass.getMethod("toggleCheatItemsEnabled").invoke(ts);
            }
        } catch (Exception e) {
            // silently ignore
        }

        savedOverlayEnabled = savedBookmarkEnabled = savedCheatEnabled = null;
    }

    @Override
    public void reset() {
        saved = false;
        savedOverlayEnabled = savedBookmarkEnabled = savedCheatEnabled = null;
    }

    // --- internal helpers ---

    private static Object getJeiToggleState() {
        try {
            return Class.forName("mezz.jei.common.Internal")
                    .getMethod("getClientToggleState").invoke(null);
        } catch (Exception e) { return null; }
    }

    public static Class<?> getJeiToggleStateClass() {
        if (jeiToggleStateClass == null) resolveClassCache();
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

    @Override
    public boolean isAvailable() {
        return getJeiToggleStateClass() != null;
    }
}
