package com.alonie.brbe.jei.plugins.engine;

import com.alonie.brbe.compat.SyntheticRecipeRenderer;
import com.alonie.brbe.recipeviewer.engine.RecipeViewerEngine;
import com.alonie.brbe.util.ClientCompat;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;

/**
 * The real {@link SyntheticRecipeRenderer}: runs a plugin category's
 * {@code draw} (background + animated icons) and paints the slot items, all in
 * the category's own coordinate system, scaled to fit the button.
 */
public final class SyntheticRecipeRendererImpl implements SyntheticRecipeRenderer {

    /** The vanilla alternative-recipe-group background sprite (32x32, 9-slice),
     *  reused as the popped-up recipe's container panel. */
    private static final Identifier OVERLAY_RECIPE_SPRITE =
            Identifier.withDefaultNamespace("recipe_book/overlay_recipe");
    /** Border sliced off each side and stitched un-stretched (the corner radius
     *  lives here); the middle is stretched to fill the panel. */
    private static final int SPRITE_BORDER = 4;
    /** Content zoom relative to the button (2.2x base × 2x overall = the popup's
     *  resting size, applied only to the content scale).  The container panel is
     *  drawn at its own larger target size (9-sliced, so the corners stay at the
     *  sprite's original 1:1) rather than by scaling the whole popup, matching
     *  vanilla's OverlayRecipeComponent. */
    private static final float CONTENT_ZOOM = 2.2f * 2f;
    /** Extra zoom while Shift is held (like vanilla's hover magnify); only this
     *  optional step changes the resting {@link #CONTENT_ZOOM}. */
    private static final float SHIFT_ZOOM = 1.5f;
    /** Extra space around the recipe inside the container panel. */
    private static final int CONTAINER_PADDING = 4;

    @Override
    public boolean render(RecipeDisplayId id, GuiGraphicsExtractor gui, int x, int y, int w, int h) {
        PluginRecipeIndexer.RenderEntry entry = PluginRecipeIndexer.renderEntryFor(id);
        RecipeViewerEngine.RecipeLayout layout = RecipeViewerEngine.getLayout(id);
        if (entry == null || layout == null) {
            return false;
        }

        // Fit the category's native aspect ratio into the button, then scale up
        // so the recipe reads clearly (it may spill past the button edges).  The
        // resting zoom is CONTENT_ZOOM; Shift adds a magnify step on top — the
        // panel target size follows, keeping the 9-sliced corners at 1:1.  (This
        // renderer runs only while hovered.)
        float zoom = ClientCompat.isShiftDown() ? CONTENT_ZOOM * SHIFT_ZOOM : CONTENT_ZOOM;
        float fit = Math.min(w / (float) layout.width(), h / (float) layout.height()) * zoom;
        int contentW = (int) (layout.width() * fit);
        int contentH = (int) (layout.height() * fit);

        // Content centred on the button; renderContainer wraps it in the panel
        // with CONTAINER_PADDING on every side, so the content sits centred in
        // the panel (no double-counted padding).
        int cx = x + (w - contentW) / 2;
        int cy = y + (h - contentH) / 2;
        renderContainer(gui, cx, cy, contentW, contentH);

        gui.pose().pushMatrix();
        gui.pose().translate(cx, cy);
        gui.pose().scale(fit, fit);

        // background + animated drawables (arrow / flame / icons)
        drawRecipe(entry.category(), entry.recipe(), gui);

        // slot items, in the category coordinate system
        for (RecipeViewerEngine.RecipeSlotLayout slot : layout.slots()) {
            if (slot.stacks().isEmpty()) continue;
            gui.item(slot.stacks().get(0), slot.x(), slot.y());
        }

        gui.pose().popMatrix();
        return true;
    }

    /** Draw the vanilla alternative-recipe-group background around the popped-up
     *  recipe by 9-slicing the sprite: the four corners (with their radius) are
     *  stitched un-stretched and only the middle is stretched to span the panel.
     *  {@code w}/{@code h} are the content area; the sprite's own border and the
     *  {@link #CONTAINER_PADDING} are added here, exactly as vanilla's
     *  OverlayRecipeComponent sizes its panel to the button grid. */
    private static void renderContainer(GuiGraphicsExtractor gui, int x, int y, int w, int h) {
        TextureAtlasSprite sprite = Minecraft.getInstance().getAtlasManager()
                .getAtlasOrThrow(AtlasIds.GUI)
                .getSprite(OVERLAY_RECIPE_SPRITE);
        Identifier atlas = sprite.atlasLocation();
        int sw = sprite.contents().width();
        int sh = sprite.contents().height();
        int b = SPRITE_BORDER;

        int x1 = x - CONTAINER_PADDING;
        int y1 = y - CONTAINER_PADDING;
        int w1 = w + CONTAINER_PADDING * 2;
        int h1 = h + CONTAINER_PADDING * 2;
        int mw = w1 - 2 * b;
        int mh = h1 - 2 * b;

        // corners (un-stretched)
        blit9(gui, atlas, sprite, x1, y1, b, b, 0, 0, b, b, sw, sh);
        blit9(gui, atlas, sprite, x1 + w1 - b, y1, b, b, sw - b, 0, sw, b, sw, sh);
        blit9(gui, atlas, sprite, x1, y1 + h1 - b, b, b, 0, sh - b, b, sh, sw, sh);
        blit9(gui, atlas, sprite, x1 + w1 - b, y1 + h1 - b, b, b, sw - b, sh - b, sw, sh, sw, sh);
        // edges (stretch on one axis)
        blit9(gui, atlas, sprite, x1 + b, y1, mw, b, b, 0, sw - b, b, sw, sh);
        blit9(gui, atlas, sprite, x1 + b, y1 + h1 - b, mw, b, b, sh - b, sw - b, sh, sw, sh);
        blit9(gui, atlas, sprite, x1, y1 + b, b, mh, 0, b, b, sh - b, sw, sh);
        blit9(gui, atlas, sprite, x1 + w1 - b, y1 + b, b, mh, sw - b, b, sw, sh - b, sw, sh);
        // middle (stretch on both axes)
        blit9(gui, atlas, sprite, x1 + b, y1 + b, mw, mh, b, b, sw - b, sh - b, sw, sh);
    }

    /** One 9-slice segment: the sprite's pixel region (su0,sv0)-(su1,sv1) mapped to
     *  the destination rect (dx,dy,dw,dh) via atlas-normalised UV coordinates. */
    private static void blit9(GuiGraphicsExtractor gui, Identifier atlas, TextureAtlasSprite sprite,
                              int dx, int dy, int dw, int dh,
                              int su0, int sv0, int su1, int sv1, int sw, int sh) {
        float u0 = sprite.getU(su0 / (float) sw);
        float u1 = sprite.getU(su1 / (float) sw);
        float v0 = sprite.getV(sv0 / (float) sh);
        float v1 = sprite.getV(sv1 / (float) sh);
        gui.blit(atlas, dx, dy, dx + dw, dy + dh, u0, u1, v0, v1);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void drawRecipe(IRecipeCategory category, Object recipe, GuiGraphicsExtractor gui) {
        category.draw(recipe, EmptySlotsView.INSTANCE, gui, 0, 0);
    }
}
