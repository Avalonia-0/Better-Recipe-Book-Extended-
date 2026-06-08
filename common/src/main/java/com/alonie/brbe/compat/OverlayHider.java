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

    private static boolean reiHidden = false;
    private static boolean jeiOverlayToggled = false;
    private static boolean jeiBookmarkToggled = false;
    private static boolean jeiCheatToggled = false;

    /**
     * Returns true if either REI or JEI is loaded and can be controlled.
     */
    public static boolean isApplicable() {
        return isReiLoaded() || isJeiLoaded();
    }

    private static boolean isReiLoaded() {
        try {
            Class.forName("me.shedaniel.rei.api.client.config.ConfigObject");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private static boolean isJeiLoaded() {
        try {
            Class.forName("mezz.jei.common.Internal");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * Applies the configured overlay hide state. Called on every screen init.
     * Does NOT use a global shouldHide guard — each mod's own state tracking
     * (reiHidden/jeiOverlayToggled) prevents redundant API calls. This ensures
     * hides are re-attempted until the target mod is fully loaded.
     */
    public static void setOverlaysHidden(boolean hide) {
        if (hide) {
            hideReiOverlay();
            hideJeiOverlay();
        } else {
            showReiOverlay();
            showJeiOverlay();
        }
    }

    private static void hideReiOverlay() {
        if (!isReiLoaded() || reiHidden) return;
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
        if (!isReiLoaded() || !reiHidden) return;
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
        if (!isJeiLoaded()) return;
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

            if (!jeiCheatToggled) {
                toggleState.getClass().getMethod("toggleCheatItemsEnabled").invoke(toggleState);
                jeiCheatToggled = true;
            }

            // Hide JEI's config button (gear icon) — it's drawn unconditionally
            hideJeiConfigButton();
            // Hide JEI's bookmark and history bottom buttons (bookmark star, history clock)
            hideJeiBookmarkButtons();
        } catch (ReflectiveOperationException e) {
            BetterRecipeBook.LOGGER.warn("Failed to hide JEI overlay", e);
        }
    }

    private static void hideJeiBookmarkButtons() {
        try {
            Class<?> internalClass = Class.forName("mezz.jei.common.Internal");
            Object runtime = internalClass.getMethod("getJeiRuntime").invoke(null);
            if (runtime == null) return;

            Object bookmarkOv = runtime.getClass().getMethod("getBookmarkOverlay").invoke(runtime);
            if (bookmarkOv == null) return;

            setJeiIconButtonVisible(bookmarkOv, "bookmarkButton", false);
            setJeiIconButtonVisible(bookmarkOv, "historyButton", false);
        } catch (Exception e) {
            // Silently ignore
        }
    }

    private static void showJeiBookmarkButtons() {
        try {
            Class<?> internalClass = Class.forName("mezz.jei.common.Internal");
            Object runtime = internalClass.getMethod("getJeiRuntime").invoke(null);
            if (runtime == null) return;

            Object bookmarkOv = runtime.getClass().getMethod("getBookmarkOverlay").invoke(runtime);
            if (bookmarkOv == null) return;

            setJeiIconButtonVisible(bookmarkOv, "bookmarkButton", true);
            setJeiIconButtonVisible(bookmarkOv, "historyButton", true);
        } catch (Exception e) {
            // Silently ignore
        }
    }

    private static void setJeiIconButtonVisible(Object owningObject, String fieldName, boolean visible) {
        try {
            Field iconField = owningObject.getClass().getDeclaredField(fieldName);
            iconField.setAccessible(true);
            Object iconButton = iconField.get(owningObject);
            if (iconButton == null) return;

            Field internalBtnField = iconButton.getClass().getDeclaredField("button");
            internalBtnField.setAccessible(true);
            Object internalButton = internalBtnField.get(iconButton);
            if (internalButton == null) return;

            internalButton.getClass().getMethod("setVisible", boolean.class).invoke(internalButton, visible);
        } catch (Exception e) {
            // Silently ignore
        }
    }

    private static void hideJeiConfigButton() {
        try {
            Class<?> internalClass = Class.forName("mezz.jei.common.Internal");
            Object runtime = internalClass.getMethod("getJeiRuntime").invoke(null);
            if (runtime == null) return;

            Object overlay = runtime.getClass().getMethod("getIngredientListOverlay").invoke(runtime);
            if (overlay == null) return;

            setJeiIconButtonVisible(overlay, "configButton", false);
        } catch (Exception e) {
            // Silently ignore
        }
    }

    private static void showJeiOverlay() {
        if (!isJeiLoaded()) return;
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
            }
            if (jeiCheatToggled) {
                toggleState.getClass().getMethod("toggleCheatItemsEnabled").invoke(toggleState);
                jeiCheatToggled = false;
            }

            // Restore JEI config button visibility
            showJeiConfigButton();
            // Restore JEI bookmark and history buttons
            showJeiBookmarkButtons();
        } catch (ReflectiveOperationException e) {
            BetterRecipeBook.LOGGER.warn("Failed to show JEI overlay", e);
        }
    }

    private static void showJeiConfigButton() {
        try {
            Class<?> internalClass = Class.forName("mezz.jei.common.Internal");
            Object runtime = internalClass.getMethod("getJeiRuntime").invoke(null);
            if (runtime == null) return;

            Object overlay = runtime.getClass().getMethod("getIngredientListOverlay").invoke(runtime);
            if (overlay == null) return;

            setJeiIconButtonVisible(overlay, "configButton", true);
        } catch (Exception e) {
            // Silently ignore
        }
    }

    /**
     * Reset all tracked state (e.g., on game restart or screen manager reset).
     */
    public static void reset() {
        reiHidden = false;
        jeiOverlayToggled = false;
        jeiBookmarkToggled = false;
        jeiCheatToggled = false;
    }
}
