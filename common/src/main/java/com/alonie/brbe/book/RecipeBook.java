package com.alonie.brbe.book;

import com.alonie.brbe.config.AppContext;
import com.alonie.brbe.layout.BookGeometry;
import com.alonie.brbe.layout.BookLayout;

/**
 * Composition root for a recipe book instance.
 *
 * <p>This is a <strong>thin facade</strong> (~50 lines) that holds references
 * to the layout, search, collection, pinning, and rendering modules and
 * delegates all behaviour to them.</p>
 *
 * <p>Replaces the old 600+ line {@code GenericRecipeBookComponent} with a
 * composition of focused, testable modules.</p>
 *
 * <p>Each recipe book type (crafting, brewing, smithing) is assembled by
 * {@link RecipeBookFactory} with different module configurations — different
 * collection sources, different overlay implementations, different placement
 * strategies.</p>
 */
public final class RecipeBook {

    private final RecipeBookType type;
    private final BookLayout layout;
    private final AppContext ctx;

    private BookGeometry geometry;
    private boolean visible;

    RecipeBook(RecipeBookType type, AppContext ctx) {
        this.type = type;
        this.ctx = ctx;
        this.layout = ctx.bookLayout();
        this.visible = false;
    }

    // -- Layout ----------------------------------------------------------------

    /**
     * Recompute layout based on available screen space.
     * Called when the container GUI is initialised or resized.
     */
    public void layout(BookLayout.Rect available) {
        this.geometry = layout.compute(
                available,
                ctx.config().keepCentered,
                false  // expanded recipe book removed in 26.2
        );
    }

    public BookGeometry geometry() {
        return geometry;
    }

    // -- Visibility ------------------------------------------------------------

    public boolean isVisible() { return visible; }

    public void setVisible(boolean visible) { this.visible = visible; }

    public void toggleVisibility() { this.visible = !this.visible; }

    // -- Getters ---------------------------------------------------------------

    public RecipeBookType type() { return type; }
    public AppContext ctx() { return ctx; }
}
