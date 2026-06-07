package com.alonie.brbe.compat;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.compat.jei.JeiCompat;
import com.alonie.brbe.compat.rei.ReiCompat;

/**
 * Utility for hiding/showing REI and/or JEI overlays on container screens.
 * <p>
 * Uses reflection to avoid compile-time dependencies on either mod.
 * All state changes are in-memory only — no config files are modified.
 */
public class OverlayHider {

    private static boolean shouldHide = false;
    private static boolean reiHidden = false;
    private static boolean jeiOverlayToggled = false;
    private static boolean jeiBookmarkToggled = false;

    /**
     * Returns true if either REI or JEI is loaded and can be controlled.
     */
    public static boolean isApplicable() {
        return ReiCompat.isLoaded() || JeiCompat.isLoaded();
    }

    /**
     * Sets whether overlays should be hidden. Tracks internal state to avoid
     * redundant API calls. Safe to call on every screen init.
     */
    public static void setOverlaysHidden(boolean hide) {
        if (hide == shouldHide) return;
        shouldHide = hide;
        if (hide) {
            hideOverlays();
        } else {
            showOverlays();
        }
    }

    /**
     * Hides REI and/or JEI overlays. Safe to call repeatedly.
     * Uses reflection and in-memory state only.
     */
    private static void hideOverlays() {
        hideReiOverlay();
        hideJeiOverlay();
    }

    /**
     * Shows (restores) REI and/or JEI overlays. Safe to call repeatedly.
     * Reverses the effects of {@link #hideOverlays()}.
     */
    private static void showOverlays() {
        showReiOverlay();
        showJeiOverlay();
    }

    private static void hideReiOverlay() {
        if (!ReiCompat.isLoaded() || reiHidden) return;
        try {
            Class<?> configObjectClass = Class.forName("me.shedaniel.rei.api.client.config.ConfigObject");
            Object instance = configObjectClass.getMethod("getInstance").invoke(null);
            configObjectClass.getMethod("setOverlayVisible", boolean.class).invoke(instance, false);
            reiHidden = true;
            BetterRecipeBook.LOGGER.debug("REI overlay hidden via ConfigObject.setOverlayVisible(false)");
        } catch (ReflectiveOperationException e) {
            BetterRecipeBook.LOGGER.warn("Failed to hide REI overlay", e);
        }
    }

    private static void showReiOverlay() {
        if (!ReiCompat.isLoaded() || !reiHidden) return;
        try {
            Class<?> configObjectClass = Class.forName("me.shedaniel.rei.api.client.config.ConfigObject");
            Object instance = configObjectClass.getMethod("getInstance").invoke(null);
            configObjectClass.getMethod("setOverlayVisible", boolean.class).invoke(instance, true);
            reiHidden = false;
            BetterRecipeBook.LOGGER.debug("REI overlay restored via ConfigObject.setOverlayVisible(true)");
        } catch (ReflectiveOperationException e) {
            BetterRecipeBook.LOGGER.warn("Failed to show REI overlay", e);
        }
    }

    private static void hideJeiOverlay() {
        if (!JeiCompat.isLoaded()) return;
        try {
            Class<?> internalClass = Class.forName("mezz.jei.common.Internal");
            Object toggleState = internalClass.getMethod("getClientToggleState").invoke(null);

            if (!jeiOverlayToggled) {
                toggleState.getClass().getMethod("toggleOverlayEnabled").invoke(toggleState);
                jeiOverlayToggled = true;
                BetterRecipeBook.LOGGER.debug("JEI overlay hidden via toggleOverlayEnabled()");
            }
            if (!jeiBookmarkToggled) {
                toggleState.getClass().getMethod("toggleBookmarkEnabled").invoke(toggleState);
                jeiBookmarkToggled = true;
                BetterRecipeBook.LOGGER.debug("JEI bookmarks hidden via toggleBookmarkEnabled()");
            }
        } catch (ReflectiveOperationException e) {
            BetterRecipeBook.LOGGER.warn("Failed to hide JEI overlay", e);
        }
    }

    private static void showJeiOverlay() {
        if (!JeiCompat.isLoaded()) return;
        try {
            Class<?> internalClass = Class.forName("mezz.jei.common.Internal");
            Object toggleState = internalClass.getMethod("getClientToggleState").invoke(null);

            if (jeiOverlayToggled) {
                toggleState.getClass().getMethod("toggleOverlayEnabled").invoke(toggleState);
                jeiOverlayToggled = false;
                BetterRecipeBook.LOGGER.debug("JEI overlay restored via toggleOverlayEnabled()");
            }
            if (jeiBookmarkToggled) {
                toggleState.getClass().getMethod("toggleBookmarkEnabled").invoke(toggleState);
                jeiBookmarkToggled = false;
                BetterRecipeBook.LOGGER.debug("JEI bookmarks restored via toggleBookmarkEnabled()");
            }
        } catch (ReflectiveOperationException e) {
            BetterRecipeBook.LOGGER.warn("Failed to show JEI overlay", e);
        }
    }

    /**
     * Reset all tracked state (e.g., on game restart or screen manager reset).
     */
    public static void reset() {
        reiHidden = false;
        jeiOverlayToggled = false;
        jeiBookmarkToggled = false;
    }
}
