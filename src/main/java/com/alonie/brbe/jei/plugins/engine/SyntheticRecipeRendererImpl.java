package com.alonie.brbe.jei.plugins.engine;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.compat.SyntheticRecipeRenderer;
import com.alonie.brbe.recipeviewer.engine.RecipeViewerEngine;
import com.alonie.brbe.render.PopupGeometry;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import net.minecraft.world.item.ItemStack;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The real {@link SyntheticRecipeRenderer}: delegates the recipe's full JEI UI
 * to JEI itself via {@link IRecipeManager#createRecipeLayoutDrawable}, scaled to
 * fit the content area passed by the caller.  JEI runs the category's
 * {@code setRecipe} and draws its own background, slot backgrounds
 * ({@code slot.setBackground}), drawables and animated icons, so mod categories
 * that JEI renders correctly (Better Archaeology, Better End, …) now show their
 * textures without BRBE re-implementing JEI's category rendering.
 *
 * <p>The {@code (x, y, w, h)} rect is the fitted <b>content area</b> (computed
 * by the caller's shared {@link PopupGeometry}); this renderer wraps it in the
 * 9-sliced container panel and scales the drawable to fill it, so the panel,
 * the hit volume and JEI's exclusion area all agree.  Requires the real JEI
 * runtime ({@link JeiRuntimeBridge}); without it {@link #render} returns false
 * and the caller falls back to its vanilla-style rendering.  The cached
 * drawable's {@code tick()} advances at a 20 Hz tick rate so JEI's per-tick
 * variant cycling and animations keep moving.
 */
public final class SyntheticRecipeRendererImpl implements SyntheticRecipeRenderer {

    /** The vanilla alternative-recipe-group background sprite (32x32, 9-slice),
     *  reused as the popped-up recipe's container panel. */
    private static final Identifier OVERLAY_RECIPE_SPRITE =
            Identifier.withDefaultNamespace("recipe_book/overlay_recipe");
    /** Border sliced off each side and stitched un-stretched (the corner radius
     *  lives here); the middle is stretched to fill the panel. */
    private static final int SPRITE_BORDER = 4;

    /** One drawable layout per synthetic recipe, cached so JEI's per-tick
     *  {@code tick()} (variant cycling and animated drawables) keeps advancing
     *  across frames.  Invalidated on every re-index. */
    private static final Map<RecipeDisplayId, IRecipeLayoutDrawable<?>> LAYOUT_CACHE = new HashMap<>();
    /** Last tick id this frame batch advanced, so {@code tick()} runs at 20 Hz
     *  (JEI's own tick rate) instead of once per rendered frame. */
    private static long lastTick = -1;
    /** Logged once on the first successful delegation, as a runtime proof that
     *  the renderer actually reached the real JEI (createRecipeLayoutDrawable). */
    private static boolean delegationLogged = false;

    public static void invalidate() {
        LAYOUT_CACHE.clear();
    }

    /** Diagnostics: logged once each for the render-skip cause and a failed
     *  drawable creation, to tell an absent JEI runtime apart from a broken
     *  category. */
    private static boolean renderSkipLogged = false;
    private static boolean drawableFailLogged = false;

    @Override
    public boolean canRender(RecipeDisplayId id) {
        // Only when the real JEI runtime is present AND this recipe has a
        // usable native layout does the delegated JEI UI actually paint.  The
        // shared PopupGeometry relies on this to pick the adapted coordinate
        // model; without it the geometry must match the vanilla fallback.
        return JeiRuntimeBridge.recipeManager() != null
                && PluginRecipeIndexer.renderEntryFor(id) != null
                && RecipeViewerEngine.getLayout(id) != null;
    }

    @Override
    public boolean render(RecipeDisplayId id, GuiGraphicsExtractor gui, int x, int y, int w, int h) {
        PluginRecipeIndexer.RenderEntry entry = PluginRecipeIndexer.renderEntryFor(id);
        RecipeViewerEngine.RecipeLayout layout = RecipeViewerEngine.getLayout(id);
        IRecipeManager recipeManager = JeiRuntimeBridge.recipeManager();
        if (entry == null || layout == null || recipeManager == null) {
            if (!renderSkipLogged) {
                renderSkipLogged = true;
                BetterRecipeBook.LOGGER.info("[BRBE-POPUP] synthetic render skipped entry={} layout={} jei={}",
                        entry != null, layout != null, recipeManager != null);
            }
            return false;
        }

        IRecipeLayoutDrawable<?> drawable = LAYOUT_CACHE.get(id);
        if (drawable == null) {
            drawable = createDrawable(recipeManager, entry);
            if (drawable == null) {
                if (!drawableFailLogged) {
                    drawableFailLogged = true;
                    BetterRecipeBook.LOGGER.info("[BRBE-POPUP] createRecipeLayoutDrawable failed for {}", id);
                }
                return false;
            }
            LAYOUT_CACHE.put(id, drawable);
            if (!delegationLogged) {
                delegationLogged = true;
                BetterRecipeBook.LOGGER.info("[BRBE-JEI-Plugins] delegating synthetic recipe UI to JEI (createRecipeLayoutDrawable)");
            }
        }

        long tick = net.minecraft.util.Util.getMillis() / 50;
        if (tick != lastTick) {
            lastTick = tick;
            drawable.tick();
        }

        // The caller already fitted the category's aspect ratio into the
        // button and scaled it up (PopupGeometry.CONTENT_ZOOM) so the recipe
        // reads clearly; fit the drawable into the given content rect and wrap
        // it in the 9-sliced panel (corners stay at 1:1, the middle stretches).
        float fit = w / (float) layout.width();
        renderContainer(gui, x, y, w, h);

        gui.pose().pushMatrix();
        gui.pose().translate(x, y);
        gui.pose().scale(fit, fit);

        // Delegate the whole recipe UI to JEI: it runs setRecipe, then draws the
        // category background, slot backgrounds, drawables and slot items in the
        // category's own coordinate system, so every mod category renders exactly
        // as it does inside JEI (backgrounds bound via slot.setBackground
        // included).
        drawable.setPosition(0, 0);
        drawable.drawRecipe(gui, 0, 0);

        gui.pose().popMatrix();
        return true;
    }

    @Override
    public ItemStack itemUnderMouse(RecipeDisplayId id, double contentX, double contentY,
                                    float ox, float oy, float fit) {
        // The live drawable knows which slot variant it painted last (its own
        // CycleTimer-driven cycling), so the tooltip matches the rendered item
        // exactly instead of BRBE's independent SlotSelectTime.
        IRecipeLayoutDrawable<?> drawable = LAYOUT_CACHE.get(id);
        if (drawable == null || fit <= 0) {
            return ItemStack.EMPTY;
        }
        try {
            // contentX/contentY are in content coordinates (cursor transformed
            // by the same ox/oy/fit the renderer drew at); JEI's slot lookup
            // works in its own local layout coordinates, so map back.
            double localX = (contentX - ox) / fit;
            double localY = (contentY - oy) / fit;
            return drawable.getItemStackUnderMouse((int) Math.floor(localX), (int) Math.floor(localY))
                    .orElse(ItemStack.EMPTY);
        } catch (Exception | LinkageError e) {
            return ItemStack.EMPTY;
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static IRecipeLayoutDrawable<?> createDrawable(IRecipeManager recipeManager,
                                                           PluginRecipeIndexer.RenderEntry entry) {
        try {
            Optional<IRecipeLayoutDrawable<?>> result = recipeManager.createRecipeLayoutDrawable(
                    (IRecipeCategory) entry.category(), entry.recipe(), EmptyFocusGroup.INSTANCE);
            return result.orElse(null);
        } catch (Exception | LinkageError e) {
            BetterRecipeBook.LOGGER.debug("[BRBE-JEI-Plugins] createRecipeLayoutDrawable failed: {}", e.toString());
            return null;
        }
    }

    /** Draw the vanilla alternative-recipe-group background around the popped-up
     *  recipe by 9-slicing the sprite: the four corners (with their radius) are
     *  stitched un-stretched and only the middle is stretched to span the panel.
     *  {@code w}/{@code h} are the content area; the sprite's own border and the
     *  shared {@link PopupGeometry#CONTAINER_PADDING} are added here, exactly as
     *  vanilla's OverlayRecipeComponent sizes its panel to the button grid. */
    private static void renderContainer(GuiGraphicsExtractor gui, int x, int y, int w, int h) {
        TextureAtlasSprite sprite = Minecraft.getInstance().getAtlasManager()
                .getAtlasOrThrow(AtlasIds.GUI)
                .getSprite(OVERLAY_RECIPE_SPRITE);
        Identifier atlas = sprite.atlasLocation();
        int sw = sprite.contents().width();
        int sh = sprite.contents().height();
        int b = SPRITE_BORDER;
        int pad = PopupGeometry.CONTAINER_PADDING;

        int x1 = x - pad;
        int y1 = y - pad;
        int w1 = w + pad * 2;
        int h1 = h + pad * 2;
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
}
