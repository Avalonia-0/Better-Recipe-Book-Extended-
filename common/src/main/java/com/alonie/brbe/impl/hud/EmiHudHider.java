package com.alonie.brbe.impl.hud;

import com.alonie.brbe.api.hud.HudHider;

/**
 * EMI overlay hider — bridges into {@code dev.emi.emi.config.EmiConfig}
 * via reflection so no compile-time dependency is needed.
 *
 * <p>EMI uses a single {@code EmiConfig.enabled} static boolean to control
 * overall visibility — simpler than JEI's multi-toggle approach and similar
 * to REI's {@code ConfigObject.setOverlayVisible()}.</p>
 */
public final class EmiHudHider implements HudHider {

    private static Class<?> emiConfigClass;
    private static boolean classCacheResolved;

    private boolean hidden;
    private boolean saved;
    private boolean savedEnabled;

    // --- HudHider ---

    @Override
    public void saveState() {
        if (saved) return; // already saved this cycle
        saved = true;

        Class<?> configClass = getEmiConfigClass();
        if (configClass == null) return;

        try {
            savedEnabled = configClass.getField("enabled").getBoolean(null);
        } catch (Exception e) {
            savedEnabled = true; // default: assume enabled
        }
    }

    @Override
    public void ensureHidden() {
        if (!isEmiLoaded() || hidden) return;
        Class<?> configClass = getEmiConfigClass();
        if (configClass == null) return;

        try {
            configClass.getField("enabled").setBoolean(null, false);
            hidden = true;
        } catch (ReflectiveOperationException ignored) {
            // EMI not ready yet — retry next tick
        }
    }

    @Override
    public void restoreState() {
        if (!saved) return;
        saved = false;

        if (!isEmiLoaded() || !hidden) return;
        hidden = false;

        Class<?> configClass = getEmiConfigClass();
        if (configClass == null) return;

        try {
            // Only restore if the saved state was enabled (don't force-enable)
            if (savedEnabled) {
                configClass.getField("enabled").setBoolean(null, true);
            }
        } catch (ReflectiveOperationException ignored) {
            // silently ignore
        }
    }

    @Override
    public void reset() {
        hidden = false;
        saved = false;
        savedEnabled = false;
    }

    // --- internal helpers ---

    @Override
    public boolean isAvailable() {
        return isEmiLoaded();
    }

    public static boolean isEmiLoaded() {
        return getEmiConfigClass() != null;
    }

    static Class<?> getEmiConfigClass() {
        if (emiConfigClass == null && !classCacheResolved) {
            classCacheResolved = true;
            try {
                emiConfigClass = Class.forName("dev.emi.emi.config.EmiConfig");
            } catch (ClassNotFoundException ignored) {
                emiConfigClass = null;
            }
        }
        return emiConfigClass;
    }
}
