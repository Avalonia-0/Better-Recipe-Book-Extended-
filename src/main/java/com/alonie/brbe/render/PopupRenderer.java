package com.alonie.brbe.render;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.cache.RecipeViewerIndex;
import com.alonie.brbe.compat.SyntheticRecipeRenderer;
import com.alonie.brbe.compat.SyntheticRecipeRenderers;
import com.alonie.brbe.mixins.accessors.OverlayRecipeButtonPosAccessor;
import com.alonie.brbe.pinoverlay.PinOverlay;
import com.alonie.brbe.recipeviewer.engine.RecipeViewerEngine;
import com.alonie.brbe.util.BRBTextures;
import com.alonie.brbe.util.ClientCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders a recipe's enlarged popup UI.  An adapted synthetic (mod) recipe
 * delegates to the companion renderer (the full JEI UI, self-contained in its
 * own coordinate system, wrapped in the shared {@link PopupGeometry} panel);
 * every other recipe is painted as the vanilla hover-scaled layout (sprite
 * backdrop + slot icons, cycled by the slot-select index) at the fixed
 * {@link PopupGeometry#VANILLA_SCALE} (the Shift magnify is gone).  Shared by
 * the query-viewer's popup layer and the pin's frozen rendering, so both stay
 * pixel-identical.
 */
public final class PopupRenderer {

    private PopupRenderer() {}

    private static final java.util.Set<RecipeDisplayId> diagIds = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** Render the recipe as a full popup: the adapted-synthetic JEI UI, or the
     *  vanilla hover-scaled recipe over the button sprite.
     *
     *  @return true if an adapted synthetic renderer painted it (the caller
     *          must not add its own backdrop); false for the vanilla popup.
     */
    public static boolean renderRecipePopup(GuiGraphics gui,
                                            RecipeDisplayId id, RecipeDisplayEntry entry,
                                            int mode, boolean craftable, boolean partial,
                                            List<?> slots, int selIdx,
                                            int x, int y, int w, int h,
                                            boolean hover, float scale) {
        // Adapted entries (synthetic, and recipe-book driven entries matched
        // back to their JEI layout): delegate the full JEI UI to the companion
        // renderer, which receives the fitted content rect from the shared
        // geometry (the panel, hit volume and JEI exclusion all come from the
        // same geometry).
        if (SyntheticRecipeRenderers.get() != SyntheticRecipeRenderer.NONE
                && SyntheticRecipeRenderers.get().canRender(id)) {
            PopupGeometry geometry = PopupGeometry.of(id, entry, mode, slots, x, y, w, h);
            // geometry.of returns the adapted geometry only when the native
            // layout exists; the renderer's own layout check keeps that
            // fallback consistent (it returns false and we paint vanilla).
            boolean painted = SyntheticRecipeRenderers.get().render(id, gui,
                    Math.round(geometry.ox), Math.round(geometry.oy),
                    Math.round(geometry.fit * geometry.contentW),
                    Math.round(geometry.fit * geometry.contentH));
            if (painted) {
                // The delegated JEI UI has no backdrop of its own to tint, so
                // keep the partial-crafting red cover the vanilla popup would
                // draw — only for non-crafting modes (stonecutter / smithing);
                // crafting-mode delegates (cooking etc.) stay untouched.
                if (partial && mode != PinOverlay.MODE_CRAFTING) {
                    gui.fill(geometry.x, geometry.y,
                            geometry.x + geometry.w, geometry.y + geometry.h, 0x60FF3333);
                }
                return true;
            }
        }
        renderVanillaPopup(gui, id, entry, mode, craftable, partial, slots, selIdx,
                x, y, w, h, hover, scale);
        return false;
    }

    /** Render the recipe's base button (no hover magnify): sprite backdrop
     *  (highlighted when hovered), partial marking and the small slot icons —
     *  used by the query-viewer's buttons, whose popup is drawn by the separate
     *  popup layer. */
    public static void renderBaseButton(GuiGraphics gui,
                                        RecipeDisplayId id, RecipeDisplayEntry entry,
                                        int mode, boolean craftable, boolean partial,
                                        List<?> slots, int selIdx,
                                        int x, int y, int w, int h, boolean hover) {
        WidgetSprites sprites = mode == PinOverlay.MODE_FURNACE
                ? BRBTextures.RECIPE_BOOK_PLAIN_OVERLAY_SPRITE
                : BRBTextures.RECIPE_BOOK_CRAFTING_OVERLAY_SPRITE;
        // The hovered (highlighted) state always uses the plain overlay
        // sprites (plain_overlay_highlighted / _disabled_highlighted),
        // regardless of the button's mode; the base texture stays per-mode.
        Identifier sprite = hover
                ? BRBTextures.RECIPE_BOOK_PLAIN_OVERLAY_SPRITE.get(craftable || partial, true)
                : sprites.get(craftable || partial, false);
        new ButtonBackdrop.Sprite(sprite).render(gui, x, y, w, h);
        if (partial) {
            gui.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0x60FF3333);
        }
        renderSlotItems(gui, id, entry, mode, slots, selIdx, x, y, w, h, false);
    }

    private static void renderVanillaPopup(GuiGraphics gui,
                                           RecipeDisplayId id, RecipeDisplayEntry entry,
                                           int mode, boolean craftable, boolean partial,
                                           List<?> slots, int selIdx,
                                           int x, int y, int w, int h,
                                           boolean hover, float scale) {
        if (diagIds.add(id)) {
            RecipeViewerEngine.RecipeLayout dl = RecipeViewerEngine.getLayout(id);
            BetterRecipeBook.LOGGER.info("[BRBE-DIAG-POPUP] id={} mode={} hover={} scale={} btn=({},{},{},{}) layout={} layoutSlots={} slots={}",
                    id, mode, hover, scale, x, y, w, h,
                    dl == null ? "null" : dl.width() + "x" + dl.height() + " bg=" + (dl.background() == null ? "null" : dl.background().width() + "x" + dl.background().height()),
                    dl == null ? "null" : dl.slots().size(),
                    slots == null ? "null" : slots.size());
        }
        if (hover) {
            gui.pose().pushMatrix();
            gui.pose().translate(x + w / 2f, y + h / 2f);
            gui.pose().scale(scale, scale);
            gui.pose().translate(-(x + w / 2f), -(y + h / 2f));
        }
        ButtonBackdrop backdrop = resolveBackdrop(id, mode, craftable, partial, hover);
        backdrop.render(gui, x, y, w, h);
        if (partial && !backdrop.suppressesPartialOverlay()) {
            gui.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0x60FF3333);
        }
        renderSlotItems(gui, id, entry, mode, slots, selIdx, x, y, w, h, hover);
        if (hover) {
            gui.pose().popMatrix();
        }
    }

    /** The single backdrop for this frame: the uniform vanilla sprite, or — only
     *  for a hovered unadapted-synthetic recipe with its own background — the JEI
     *  texture. */
    private static ButtonBackdrop resolveBackdrop(RecipeDisplayId id, int mode,
                                                  boolean craftable, boolean partial, boolean hover) {
        RecipeViewerEngine.RecipeLayout layout = RecipeViewerEngine.getLayout(id);
        if (hover && layout != null && layout.background() != null) {
            RecipeViewerEngine.RecipeBackground bg = layout.background();
            return new ButtonBackdrop.Texture(bg.texture(), bg.u(), bg.v(), bg.width(), bg.height(),
                    bg.textureWidth(), bg.textureHeight());
        }
        WidgetSprites sprites = mode == PinOverlay.MODE_FURNACE
                ? BRBTextures.RECIPE_BOOK_PLAIN_OVERLAY_SPRITE
                : BRBTextures.RECIPE_BOOK_CRAFTING_OVERLAY_SPRITE;
        return new ButtonBackdrop.Sprite(sprites.get(craftable || partial, hover));
    }

    /** The recipe's slot icons, cycled by {@code selIdx}, laid out per mode. */
    private static void renderSlotItems(GuiGraphics gui,
                                        RecipeDisplayId id, RecipeDisplayEntry entry,
                                        int mode, List<?> slots, int selIdx,
                                        int x, int y, int w, int h, boolean hover) {
        gui.pose().pushMatrix();
        if (RecipeViewerEngine.getLayout(id) != null) {
            renderSynthetic(gui, id, entry, selIdx, x, y, hover);
        } else if (mode == PinOverlay.MODE_STONECUTTING) {
            renderFixedPair(gui, entry, PinOverlay.MODE_STONECUTTING, selIdx, x, y, hover);
        } else if (mode == PinOverlay.MODE_SMITHING) {
            renderFixedPair(gui, entry, PinOverlay.MODE_SMITHING, selIdx, x, y, hover);
        } else if (mode == PinOverlay.MODE_FURNACE) {
            renderFurnace(gui, entry, selIdx, x, y, hover);
        } else if (BetterRecipeBook.config.alternativeRecipes.onHover && !hover) {
            // The product shown on the button cycles through every result
            // variant (like the smithing category), so multi-product recipes
            // display all of their products instead of only the first.
            gui.renderItem(select(resultVariants(entry), selIdx), x + 4, y + 4);
        } else if (mode == PinOverlay.MODE_CRAFTING && slots != null && !slots.isEmpty()) {
            // Crafting: vanilla slot positions (16px icon centred on the
            // translate point), materials cycled like ghost ingredients.
            gui.pose().translate(x + 2, y + 2);
            for (Object rawPos : slots) {
                OverlayRecipeButtonPosAccessor pos = (OverlayRecipeButtonPosAccessor) rawPos;
                gui.pose().pushMatrix();
                gui.pose().translate(pos.brbe$getX(), pos.brbe$getY());
                gui.pose().scale(0.375f, 0.375f);
                gui.pose().translate(-8.0F, -8.0F);
                gui.renderItem(pos.brbe$selectIngredient(selIdx), 0, 0);
                gui.pose().popMatrix();
            }
        } else {
            // Displays without vanilla button slots (e.g. Farmer's Delight
            // cooking recipes) fall back to the generic entry layout.
            renderGenericCrafting(gui, entry, selIdx, x, y, hover);
        }
        gui.pose().popMatrix();
    }

    /** Stonecutter / smithing: input (base) top-left at (2,2), result
     *  bottom-right at (12,7), scaled 0.6; "result only" when not hovered.
     *  Entries whose display is not the expected type (e.g. local-cache
     *  fallbacks) render the generic entry layout instead of a blank button. */
    private static void renderFixedPair(GuiGraphics gui, RecipeDisplayEntry entry,
                                        int mode, int selIdx, int x, int y, boolean hover) {
        if (mode == PinOverlay.MODE_STONECUTTING
                && RecipeViewerIndex.asStonecutter(entry) == null) {
            renderGenericCrafting(gui, entry, selIdx, x, y, hover);
            return;
        }
        if (mode == PinOverlay.MODE_SMITHING
                && RecipeViewerIndex.asSmithing(entry) == null) {
            renderGenericCrafting(gui, entry, selIdx, x, y, hover);
            return;
        }
        boolean onHover = BetterRecipeBook.config.alternativeRecipes.onHover;
        ItemStack first;
        ItemStack second;
        if (mode == PinOverlay.MODE_STONECUTTING) {
            var display = RecipeViewerIndex.asStonecutter(entry);
            first = display == null ? ItemStack.EMPTY
                    : select(RecipeViewerIndex.resolveSlotDisplay(display.input()), selIdx);
            second = display == null ? ItemStack.EMPTY
                    : select(RecipeViewerIndex.resolveSlotDisplay(display.result()), selIdx);
        } else {
            var display = RecipeViewerIndex.asSmithing(entry);
            first = display == null ? ItemStack.EMPTY
                    : select(RecipeViewerIndex.resolveSlotDisplay(display.base()), selIdx);
            second = display == null ? ItemStack.EMPTY
                    : select(RecipeViewerIndex.resolveSlotDisplay(display.result()), selIdx);
        }
        if (onHover && !hover) {
            gui.renderItem(second, x + 4, y + 4);
        } else {
            scaledItem(gui, first, x + 2, y + 2);
            scaledItem(gui, second, x + 12, y + 7);
        }
    }

    /** Furnace: ingredient top-left, flame bottom-left, result right half. */
    private static void renderFurnace(GuiGraphics gui, RecipeDisplayEntry entry,
                                      int selIdx, int x, int y, boolean hover) {
        boolean onHover = BetterRecipeBook.config.alternativeRecipes.onHover;
        var display = RecipeViewerIndex.asFurnace(entry);
        ItemStack ingredient = display == null ? ItemStack.EMPTY
                : select(RecipeViewerIndex.resolveSlotDisplay(display.ingredient()), selIdx);
        ItemStack result = display == null ? ItemStack.EMPTY
                : select(RecipeViewerIndex.resolveSlotDisplay(display.result()), selIdx);
        if (onHover && !hover) {
            gui.renderItem(result, x + 4, y + 4);
        } else {
            scaledItem(gui, ingredient, x + 2, y + 2);
            ClientCompat.blitSprite(gui, BRBTextures.FURNACE_FIRE_SPRITE, x + 4, y + 15, 6, 6);
            if (hover) {
                scaledItem(gui, result, x + 12, y + 7);
            }
        }
    }

    private static void scaledItem(GuiGraphics gui, ItemStack stack, int tx, int ty) {
        if (stack.isEmpty()) return;
        gui.pose().pushMatrix();
        gui.pose().translate(tx, ty);
        gui.pose().scale(0.6f, 0.6f);
        gui.renderItem(stack, 0, 0);
        gui.pose().popMatrix();
    }

    /** An unadapted synthetic recipe: the result item when not hovered,
     *  otherwise its fitted native layout slots. */
    private static void renderSynthetic(GuiGraphics gui, RecipeDisplayId id,
                                        RecipeDisplayEntry entry, int selIdx,
                                        int x, int y, boolean hover) {
        if (diagIds.add(id)) {
            BetterRecipeBook.LOGGER.info("[BRBE-DIAG-SYNTH0] id={} onHover={} hover={}",
                    id, BetterRecipeBook.config.alternativeRecipes.onHover, hover);
        }
        if (BetterRecipeBook.config.alternativeRecipes.onHover && !hover) {
            gui.renderItem(select(resultVariants(entry), selIdx), x + 4, y + 4);
            return;
        }
        RecipeViewerEngine.RecipeLayout layout = RecipeViewerEngine.getLayout(id);
        if (diagIds.add(id)) {
            BetterRecipeBook.LOGGER.info("[BRBE-DIAG-SYNTH1] id={} layout={} layoutSlots={}",
                    id, layout == null ? "null" : layout.width() + "x" + layout.height(),
                    layout == null ? -1 : layout.slots().size());
        }
        if (layout == null || layout.slots().isEmpty()) {
            gui.renderItem(select(resultVariants(entry), selIdx), x + 4, y + 4);
            return;
        }
        // Layout-fitted slots at the panel's own scale; the panel (and any
        // painted background texture) is centred on the button, so the slots
        // shift by the same centre offset (pose space: 12 = half of the
        // 24x24 button).
        List<PopupGeometry.UnadaptedSlot> slots = PopupGeometry.backgroundFitPositions(layout);
        float tscale = PopupGeometry.layoutFitScale(layout);
        float offX = 12f - layout.width() * tscale / 2f;
        float offY = 12f - layout.height() * tscale / 2f;
        if (diagIds.add(id)) {
            BetterRecipeBook.LOGGER.info("[BRBE-DIAG-SYNTH] id={} layout={}x{} bg={} slots={} off=({},{}) hover={}",
                    id, layout.width(), layout.height(),
                    layout.background() == null ? "null" : layout.background().width() + "x" + layout.background().height(),
                    slots.size(), offX, offY, hover);
        }
        for (PopupGeometry.UnadaptedSlot slot : slots) {
            gui.pose().pushMatrix();
            gui.pose().translate(x + offX + slot.x(), y + offY + slot.y());
            gui.pose().scale(0.45f, 0.45f);
            gui.pose().translate(-8.0F, -8.0F);
            gui.renderItem(slot.stacks().get(selIdx % slot.stacks().size()), 0, 0);
            gui.pose().popMatrix();
        }
    }

    /** Pick the variant shown for the current slot-select cycle. */
    private static ItemStack select(List<ItemStack> stacks, int selIdx) {
        return stacks.isEmpty() ? ItemStack.EMPTY : stacks.get(selIdx % stacks.size());
    }

    /** Every possible result item of {@code entry} (its product variants), or
     *  empty when unresolvable. */
    private static List<ItemStack> resultVariants(RecipeDisplayEntry entry) {
        if (entry == null) return List.of();
        Minecraft mc = Minecraft.getInstance();
        try {
            List<ItemStack> results = entry.resultItems(SlotDisplayContext.fromLevel(mc.level));
            if (!results.isEmpty()) return results;
        } catch (Exception ignored) {
            // fall through
        }
        return List.of();
    }

    /** Generic entry layout for displays without vanilla button slots (e.g.
     *  Farmer's Delight cooking recipes): the craftingRequirements inputs on a
     *  3x2 grid plus the result top-right, cycled like ghost ingredients. */
    private static void renderGenericCrafting(GuiGraphics gui, RecipeDisplayEntry entry,
                                              int selIdx, int x, int y, boolean hover) {
        boolean onHover = BetterRecipeBook.config.alternativeRecipes.onHover;
        if (onHover && !hover) {
            gui.renderItem(select(resultVariants(entry), selIdx), x + 4, y + 4);
            return;
        }
        if (entry == null) return;
        try {
            java.util.Optional<List<net.minecraft.world.item.crafting.Ingredient>> reqs =
                    entry.craftingRequirements();
            if (reqs.isPresent()) {
                List<net.minecraft.world.item.crafting.Ingredient> list = reqs.get();
                for (int i = 0; i < Math.min(list.size(), 6); i++) {
                    ItemStack stack = selectIngredient(list.get(i), selIdx);
                    if (!stack.isEmpty()) {
                        scaledItem(gui, stack, x + 2 + (i % 3) * 5, y + 2 + (i / 3) * 5);
                    }
                }
            }
        } catch (Exception ignored) {
            // one broken ingredient must not blank the whole popup
        }
        ItemStack result = select(resultVariants(entry), selIdx);
        if (!result.isEmpty()) {
            scaledItem(gui, result, x + 17, y + 2);
        }
    }

    /** One material variant of {@code ingredient} for the current cycle. */
    private static ItemStack selectIngredient(net.minecraft.world.item.crafting.Ingredient ingredient,
                                              int selIdx) {
        if (ingredient == null) return ItemStack.EMPTY;
        List<ItemStack> stacks = new ArrayList<>();
        ingredient.items().forEach(holder -> stacks.add(new ItemStack(holder.value())));
        return select(stacks, selIdx);
    }
}
