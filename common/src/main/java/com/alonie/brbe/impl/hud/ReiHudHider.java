package com.alonie.brbe.impl.hud;

import com.alonie.brbe.api.hud.HudHider;

/**
 * REI overlay hider — bridges into {@code me.shedaniel.rei.api.client.config.ConfigObject}
 * via reflection so no compile-time dependency is needed.
 */
public final class ReiHudHider implements HudHider {

    private static Class<?> reiConfigClass;
    private static boolean classCacheResolved;

    private boolean hidden;
    private boolean saved;

    // --- HudHider ---

    @Override
    public void saveState() {
        saved = true;      // REI has no separate state to snapshot — just track that we hid it
    }

    @Override
    public void ensureHidden() {
        if (!isReiLoaded() || hidden) return;
        try {
            Object instance = getReiConfigClass().getMethod("getInstance").invoke(null);
            getReiConfigClass().getMethod("setOverlayVisible", boolean.class).invoke(instance, false);
            hidden = true;
        } catch (ReflectiveOperationException ignored) {}
    }

    @Override
    public void restoreState() {
        if (!saved) return;
        saved = false;
        if (!isReiLoaded() || !hidden) return;
        try {
            Object instance = getReiConfigClass().getMethod("getInstance").invoke(null);
            getReiConfigClass().getMethod("setOverlayVisible", boolean.class).invoke(instance, true);
        } catch (ReflectiveOperationException ignored) {}
        hidden = false;
    }

    @Override
    public void forceShow() {
        if (!isReiLoaded()) return;
        try {
            Object instance = getReiConfigClass().getMethod("getInstance").invoke(null);
            getReiConfigClass().getMethod("setOverlayVisible", boolean.class).invoke(instance, true);
        } catch (ReflectiveOperationException ignored) {}
    }

    @Override
    public void reset() {
        hidden = false;
        saved = false;
    }

    // --- internal helpers ---

    public static boolean isReiLoaded() {
        return getReiConfigClass() != null;
    }

    static Class<?> getReiConfigClass() {
        if (reiConfigClass == null && !classCacheResolved) {
            try {
                reiConfigClass = Class.forName("me.shedaniel.rei.api.client.config.ConfigObject");
            } catch (ClassNotFoundException ignored) {}
        }
        return reiConfigClass;
    }
}
