package com.alonie.brbe.render;

import com.alonie.brbe.layout.BookLayout;
import com.alonie.brbe.util.BRBTextures;
import com.alonie.brbe.util.ClientCompat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;

/**
 * Renders the recipe book background texture.
 *
 * <p>Supports standard mode (single 147×166 sprite blit) and expanded mode
 * (3-slice: left cap 32px, body tiled, right cap 12px).</p>
 *
 * <p>Adapted for 26.2 {@link GuiGraphics} API — the first two
 * blit parameters are the render pipeline and sprite identifier, and UV
 * coordinates are floats.</p>
 */
public final class BookBackgroundRenderer {

    /** Vanilla recipe book texture (256×256 atlas, UV(1,1)-(148,167)). */
    private static final Identifier RECIPE_BOOK_TEXTURE =
            Identifier.withDefaultNamespace("textures/gui/recipe_book.png");

    /**
     * Render the recipe book background at the given position.
     *
     * @param gui       the GuiGraphics context
     * @param left      book left edge (screen coords)
     * @param top       book top edge (screen coords)
     * @param width     book width (147 standard, larger when expanded)
     * @param expanded  whether expanded (wide) mode is active
     */
    public void render(GuiGraphics gui, int left, int top, int width, boolean expanded) {
        int height = BookLayout.TEXTURE_HEIGHT;

        if (!expanded) {
            // Standard rendering — single blit
            gui.blit(ClientCompat.GUI_TEXTURED, BRBTextures.RECIPE_BOOK_BACKGROUND_TEXTURE,
                    left, top, 1.0F, 1.0F, width, height,
                    BookLayout.BG_TEX_SIZE, BookLayout.BG_TEX_SIZE);
        } else {
            // 3-slice rendering for expanded mode
            renderExpanded(gui, left, top, width, height);
        }
    }

    private void renderExpanded(GuiGraphics gui, int left, int top, int width, int height) {
        int texSize = BookLayout.BG_TEX_SIZE;
        int leftCap = BookLayout.BG_LEFT_CAP;
        int rightCap = BookLayout.BG_RIGHT_CAP;
        int bodyWidth = BookLayout.BG_BODY;
        int bookTexW = BookLayout.TEXTURE_WIDTH;

        // Left cap (32px)
        gui.blit(ClientCompat.GUI_TEXTURED, BRBTextures.RECIPE_BOOK_BACKGROUND_TEXTURE,
                left, top, 1.0F, 1.0F, leftCap, height, texSize, texSize);

        // Tiled body from UV 32 to UV 32+103=135
        int bodyX = left + leftCap;
        int remainingWidth = width - leftCap - rightCap;
        float uvLeft = (float) (leftCap + 1);   // UV 33
        float uvRight = uvLeft + bodyWidth;      // UV 136

        int filled = 0;
        while (filled < remainingWidth) {
            int segmentWidth = Math.min(bodyWidth, remainingWidth - filled);
            gui.blit(ClientCompat.GUI_TEXTURED, BRBTextures.RECIPE_BOOK_BACKGROUND_TEXTURE,
                    bodyX + filled, top, uvLeft, 1.0F, segmentWidth, height, texSize, texSize);
            filled += segmentWidth;
        }

        // Right cap (12px) — UV starts at bookTexW - rightCap + 1 = 147 - 12 + 1 = 136
        int rightX = bodyX + remainingWidth;
        float uvRightCap = (float) (bookTexW - rightCap + 1);
        gui.blit(ClientCompat.GUI_TEXTURED, BRBTextures.RECIPE_BOOK_BACKGROUND_TEXTURE,
                rightX, top, uvRightCap, 1.0F, rightCap, height, texSize, texSize);
    }
}
