package com.alonie.brbe.compat;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.compat.jei.JeiCompat;
import com.alonie.brbe.compat.rei.ReiCompat;

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
        try {
            Class.forName("me.shedaniel.rei.api.client.config.ConfigObject");
            return true;
        } catch (ClassNotFoundException ignored) {}
        try {
            Class.forName("mezz.jei.common.Internal");
            return true;
        } catch (ClassNotFoundException ignored) {}
        return false;
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

            if (!jeiCheatToggled) {
                toggleState.getClass().getMethod("toggleCheatItemsEnabled").invoke(toggleState);
                jeiCheatToggled = true;
            }

            // Hide JEI's config button (gear icon) — it's drawn unconditionally
            hideJeiConfigButton();
        } catch (ReflectiveOperationException e) {
            BetterRecipeBook.LOGGER.warn("Failed to hide JEI overlay", e);
        }
    }

    private static void hideJeiConfigButton() {
        try {
            Class<?> internalClass = Class.forName("mezz.jei.common.Internal");
            Object runtime = internalClass.getMethod("getJeiRuntime").invoke(null);
            if (runtime == null) return;

            Object overlay = runtime.getClass().getMethod("getIngredientListOverlay").invoke(runtime);
            if (overlay == null) return;

            Field configButtonField = overlay.getClass().getDeclaredField("configButton");
            configButtonField.setAccessible(true);
            Object iconButton = configButtonField.get(overlay);
            if (iconButton == null) return;

            Field internalButtonField = iconButton.getClass().getDeclaredField("button");
            internalButtonField.setAccessible(true);
            Object internalButton = internalButtonField.get(iconButton);
            if (internalButton == null) return;

            internalButton.getClass().getMethod("setVisible", boolean.class).invoke(internalButton, false);
        } catch (Exception e) {
            // Silently ignore — the config button is non-critical
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
            }
            if (jeiCheatToggled) {
                toggleState.getClass().getMethod("toggleCheatItemsEnabled").invoke(toggleState);
                jeiCheatToggled = false;
            }

            // Restore JEI config button visibility
            showJeiConfigButton();
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

            Field configButtonField = overlay.getClass().getDeclaredField("configButton");
            configButtonField.setAccessible(true);
            Object iconButton = configButtonField.get(overlay);
            if (iconButton == null) return;

            Field internalButtonField = iconButton.getClass().getDeclaredField("button");
            internalButtonField.setAccessible(true);
            Object internalButton = internalButtonField.get(iconButton);
            if (internalButton == null) return;

            internalButton.getClass().getMethod("setVisible", boolean.class).invoke(internalButton, true);
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
