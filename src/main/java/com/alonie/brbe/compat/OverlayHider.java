package com.alonie.brbe.compat;

import com.alonie.brbe.api.hud.HudHider;
import com.alonie.brbe.impl.hud.JeiHudHider;
import com.alonie.brbe.impl.hud.ReiHudHider;

import java.util.ArrayList;
import java.util.List;

/**
 * Thin registry over {@link HudHider} implementations.
 *
 * <p>Callers (Fabric / NeoForge client initialisers) register the hiders they
 * need; this class just iterates over them.  All JEI / REI specific logic
 * lives in {@code com.alonie.brbe.impl.hud}.
 */
public final class OverlayHider {

    private static final List<HudHider> HIDERS = new ArrayList<>();
    private static boolean currentlyHidden;

    private OverlayHider() {}

    /** Register a hider.  Safe to call more than once (duplicates are ok). */
    public static void register(HudHider hider) {
        HIDERS.add(hider);
    }

    public static boolean isApplicable() {
        return ReiHudHider.isReiLoaded() || JeiHudHider.getJeiToggleStateClass() != null;
    }

    public static void setOverlaysHidden(boolean hide) {
        if (hide && !currentlyHidden) {
            currentlyHidden = true;
            HIDERS.forEach(HudHider::saveState);
            HIDERS.forEach(HudHider::ensureHidden);
        } else if (!hide && currentlyHidden) {
            currentlyHidden = false;
            HIDERS.forEach(HudHider::restoreState);
        }
    }

    /** Per-tick enforcement — called when a screen is open and config says hide. */
    public static void ensureJeiOverlayHidden() {
        HIDERS.forEach(HudHider::ensureHidden);
    }

    /** Reset all tracked state (e.g. world unload). */
    public static void reset() {
        currentlyHidden = false;
        HIDERS.forEach(HudHider::reset);
    }
}
