package com.alonie.brbe.api.hud;

/**
 * Abstraction over a HUD overlay that can be hidden/restored by BRBE's
 * "Hide REI/JEI Interface" config toggle.
 *
 * <p>Implementations use reflection so they don't require compile-time
 * dependencies on JEI or REI.  Each hider owns its own snapshot / guard
 * state — no shared static fields between JEI and REI paths.
 */
public interface HudHider {

    /** Take a snapshot of the current overlay state before hiding. */
    void saveState();

    /** Force the overlay hidden. Called every tick while hiding is active. */
    void ensureHidden();

    /** Restore the overlay to the saved snapshot. */
    void restoreState();

    /** Reset all tracked state (e.g. on world unload). */
    default void reset() {}
}
