package com.alonie.brbe.render;

import com.alonie.brbe.util.ClientCompat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;

/**
 * A recipe-book button's backdrop — the single thing painted behind a button's
 * recipe items.  It is a closed union of two mutually exclusive sources:
 *
 * <ul>
 *   <li>{@link Sprite} — a vanilla/BRBE overlay sprite, the uniform backdrop for
 *       every non-hover button (and for hovered vanilla recipes);</li>
 *   <li>{@link Texture} — a mod recipe's JEI-category background texture region,
 *       used only when a hovered synthetic recipe declares one.</li>
 * </ul>
 *
 * <p>Exactly one is chosen per frame by the caller's {@code resolveBackdrop}, so
 * the two can never be drawn together — mutual exclusion is structural (a single
 * {@link #render} call), not a set of conditional paint branches that could
 * accidentally stack.</p>
 */
public sealed interface ButtonBackdrop {

    /** Paint this backdrop with its top-left at {@code (x, y)}, sized {@code w x h}.
     *  Called inside the caller's hover pose, so it scales with the button's
     *  2x/4x pop. */
    void render(GuiGraphics gui, int x, int y, int w, int h);

    /** Whether this backdrop fully covers the button's slot sprite and therefore
     *  should suppress the partial-crafting red overlay.  Only {@link Texture}
     *  (an opaque mod background) does. */
    default boolean suppressesPartialOverlay() {
        return false;
    }

    /** A vanilla/BRBE overlay sprite, drawn via {@code blitSprite}. */
    record Sprite(Identifier sprite) implements ButtonBackdrop {
        @Override
        public void render(GuiGraphics gui, int x, int y, int w, int h) {
            ClientCompat.blitSprite(gui, sprite, x, y, w, h);
        }
    }

    /**
     * A mod recipe's JEI-category background texture region, drawn keeping the
     * category's native aspect ratio and scaled a bit larger than the button (fit
     * factor x 1.5) so the recipe reads clearly.
     *
     * <p>Carries raw texture fields rather than the engine's {@code RecipeBackground}
     * record so this renderer stays engine-agnostic.  Assumes the background is
     * opaque, hence {@link #suppressesPartialOverlay()} returns true.</p>
     */
    record Texture(Identifier texture, int u, int v, int width, int height,
                   int textureWidth, int textureHeight) implements ButtonBackdrop {
        @Override
        public void render(GuiGraphics gui, int x, int y, int w, int h) {
            // blit draws the source region 1:1, so the pose is scaled by the fit
            // factor instead of passing scaled draw dimensions.
            float fit = Math.min(w / (float) width, h / (float) height);
            float scale = fit * 1.5f;
            gui.pose().pushMatrix();
            // Centre the texture on the BUTTON centre (pose-space offset), so
            // arbitrary aspect ratios stay centred like the 48x48 vanilla box.
            gui.pose().translate(x + (w - width * scale) / 2f, y + (h - height * scale) / 2f);
            gui.pose().scale(scale, scale);
            gui.blit(ClientCompat.GUI_TEXTURED, texture, 0, 0,
                    (float) u, (float) v, width, height, textureWidth, textureHeight);
            gui.pose().popMatrix();
        }

        @Override
        public boolean suppressesPartialOverlay() {
            return true;
        }
    }
}
