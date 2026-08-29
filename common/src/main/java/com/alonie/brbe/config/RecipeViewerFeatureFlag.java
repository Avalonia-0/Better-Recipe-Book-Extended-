package com.alonie.brbe.config;

/**
 * JVM-level kill switch for the R/U query-viewer feature ("查询功能").
 *
 * <p>Controlled by the system property {@code brbe.disableRecipeViewer}.  When
 * {@code true} (the default), the query viewer is hidden entirely — it cannot
 * be opened via R/U, does not render, and its config entries are hidden from
 * the Cloth Config GUI.  Set {@code -Dbrbe.disableRecipeViewer=false} to
 * re-enable it.</p>
 *
 * <p>This is a temporary shield for a feature that is mid-refactor (the
 * build is being reworked to line up 1.21.1 with the higher versions).  It is
 * deliberately independent of {@link BrbeConfig#recipeViewerEnabled} so the
 * shield cannot be toggled back on from the config GUI while active.</p>
 *
 * <p>Evaluated once at class load (never changes mid-session), matching the
 * {@code com.alonie.brbe.util.BrbeLogger} convention for JVM flags.</p>
 */
public final class RecipeViewerFeatureFlag {

    /** JVM property name: {@code brbe.disableRecipeViewer}.  Default = true. */
    public static final String PROPERTY = "brbe.disableRecipeViewer";

    /** Whether the query viewer is disabled (hidden) for this session. */
    private static final boolean DISABLED =
            !"false".equalsIgnoreCase(System.getProperty(PROPERTY));

    private RecipeViewerFeatureFlag() {}

    /** Whether the query viewer is hidden (default true). */
    public static boolean isDisabled() {
        return DISABLED;
    }

    /** Whether the query viewer is visible/enabled. */
    public static boolean isEnabled() {
        return !DISABLED;
    }
}
