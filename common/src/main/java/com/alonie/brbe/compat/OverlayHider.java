package com.alonie.brbe.compat;

import com.alonie.brbe.BetterRecipeBook;

/**
 * Utility for hiding/showing REI and/or JEI overlays on container screens.
 * <p>
 * Uses reflection to avoid compile-time dependencies on either mod.
 * All state changes are in-memory only — no config files are modified.
 * Self-contained: detects REI/JEI by checking for their runtime classes.
 */
public class OverlayHider {

    private static final String REI_CONFIG_CLASS = "me.shedaniel.rei.api.client.config.ConfigObject";
    private static final String JEI_INTERNAL_CLASS = "mezz.jei.common.Internal";

    private static boolean shouldHide = false;
    private static boolean reiHidden = false;
    private static Boolean reiChecked;
    private static boolean jeiOverlayToggled = false;
    private static boolean jeiBookmarkToggled = false;
    private static Boolean jeiChecked;

    /**
     * Returns true if either REI or JEI is loaded and can be controlled.
     */
    public static boolean isApplicable() {
        return isReiLoaded() || isJeiLoaded();
    }

    private static boolean isReiLoaded() {
        if (reiChecked == null) {
            try {
                Class.forName(REI_CONFIG_CLASS);
                reiChecked = true;
            } catch (ClassNotFoundException e) {
                reiChecked = false;
            }
        }
        return reiChecked;
    }

    private static boolean isJeiLoaded() {
        if (jeiChecked == null) {
            try {
                Class.forName(JEI_INTERNAL_CLASS);
                jeiChecked = true;
            } catch (ClassNotFoundException e) {
                jeiChecked = false;
            }
        }
        return jeiChecked;
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

    private static void hideOverlays() {
        hideReiOverlay();
        hideJeiOverlay();
    }

    private static void showOverlays() {
        showReiOverlay();
        showJeiOverlay();
    }

    private static void hideReiOverlay() {
        if (!isReiLoaded() || reiHidden) return;
        try {
            Class<?> configObjectClass = Class.forName(REI_CONFIG_CLASS);
            Object instance = configObjectClass.getMethod("getInstance").invoke(null);
            configObjectClass.getMethod("setOverlayVisible", boolean.class).invoke(instance, false);
            reiHidden = true;
            BetterRecipeBook.LOGGER.debug("REI overlay hidden via ConfigObject.setOverlayVisible(false)");
        } catch (ReflectiveOperationException e) {
            BetterRecipeBook.LOGGER.warn("Failed to hide REI overlay", e);
        }
    }

    private static void showReiOverlay() {
        if (!isReiLoaded() || !reiHidden) return;
        try {
            Class<?> configObjectClass = Class.forName(REI_CONFIG_CLASS);
            Object instance = configObjectClass.getMethod("getInstance").invoke(null);
            configObjectClass.getMethod("setOverlayVisible", boolean.class).invoke(instance, true);
            reiHidden = false;
            BetterRecipeBook.LOGGER.debug("REI overlay restored via ConfigObject.setOverlayVisible(true)");
        } catch (ReflectiveOperationException e) {
            BetterRecipeBook.LOGGER.warn("Failed to show REI overlay", e);
        }
    }

    private static void hideJeiOverlay() {
        if (!isJeiLoaded()) return;
        try {
            Class<?> internalClass = Class.forName(JEI_INTERNAL_CLASS);
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
        if (!isJeiLoaded()) return;
        try {
            Class<?> internalClass = Class.forName(JEI_INTERNAL_CLASS);
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
