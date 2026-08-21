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
import com.alonie.brbe.util.PartialCraftingUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    /** 缺失材料/结果的红色遮罩色（与原整块 partial 红罩同色）。 */
    private static final int MISSING_MASK_COLOR = 0x60FF3333;
    /** JEI {@code RecipeIngredientRole} 序号：0=INPUT（材料）、1=OUTPUT（结果）。
     *  仅用于区分合成配方的材料/结果槽位，避免依赖 JEI API。 */
    private static final int ROLE_INPUT = 0;
    private static final int ROLE_OUTPUT = 1;

    /** 扣减检索空间内某物品的拥有数量；返回该槽位是否"材料已满足"
     *  （与合成台幽灵槽一致：同种材料多个槽位按渲染顺序依次扣减）。 */
    private static boolean consumeOwned(Map<Item, Integer> remaining, ItemStack stack) {
        if (remaining == null || stack == null || stack.isEmpty()) return false;
        int have = remaining.getOrDefault(stack.getItem(), 0);
        if (have <= 0) return false;
        remaining.put(stack.getItem(), have - 1);
        return true;
    }

    /** 整块红色遮罩挖掉已拥有物品的槽位格后绘制：界面本体仍先覆盖红罩，
     *  已拥有材料所占据的一小格（16×16 槽位判定区域）不超出物品区域地露出。 */
    private static void maskWithHoles(GuiGraphicsExtractor gui, int x1, int y1, int x2, int y2,
                                      List<int[]> holes) {
        List<int[]> rects = new ArrayList<>();
        rects.add(new int[]{x1, y1, x2, y2});
        for (int[] hole : holes) {
            List<int[]> next = new ArrayList<>();
            for (int[] r : rects) {
                if (hole[2] <= r[0] || hole[0] >= r[2] || hole[3] <= r[1] || hole[1] >= r[3]) {
                    next.add(r);
                    continue;
                }
                if (hole[1] > r[1]) next.add(new int[]{r[0], r[1], r[2], hole[1]});   // 上
                if (hole[3] < r[3]) next.add(new int[]{r[0], hole[3], r[2], r[3]});   // 下
                if (hole[0] > r[0]) next.add(new int[]{r[0], Math.max(r[1], hole[1]), hole[0], Math.min(r[3], hole[3])}); // 左
                if (hole[2] < r[2]) next.add(new int[]{hole[2], Math.max(r[1], hole[1]), r[2], Math.min(r[3], hole[3])}); // 右
            }
            rects = next;
        }
        for (int[] r : rects) {
            gui.fill(r[0], r[1], r[2], r[3], MISSING_MASK_COLOR);
        }
    }

    /** 收集"已拥有材料"的槽位格（16×16，屏幕坐标），供 {@link #maskWithHoles}
     *  挖洞。只处理材料槽（synthetic 的 INPUT / 固定对的 input / 熔炉
     *  ingredient / crafting 格位）；结果槽不参与。 */
    private static void collectOwnedSlots(RecipeDisplayId id, RecipeDisplayEntry entry,
                                          int mode, List<?> slots, int selIdx,
                                          int x, int y, List<int[]> holes) {
        Map<Item, Integer> remaining = new HashMap<>(PartialCraftingUtil.searchSpaceCounts());
        if (RecipeViewerEngine.isSynthetic(id)) {
            RecipeViewerEngine.RecipeLayout layout = RecipeViewerEngine.getLayout(id);
            if (layout == null || layout.slots().isEmpty()) return;
            List<PopupGeometry.UnadaptedSlot> fitted = PopupGeometry.slotFitPositions(layout);
            for (PopupGeometry.UnadaptedSlot slot : fitted) {
                if (slot.role() != ROLE_INPUT) continue;
                ItemStack stack = slot.stacks().get(selIdx % slot.stacks().size());
                if (consumeOwned(remaining, stack)) {
                    holes.add(new int[]{Math.round(x + slot.x()) - 8, Math.round(y + slot.y()) - 8,
                            Math.round(x + slot.x()) + 8, Math.round(y + slot.y()) + 8});
                }
            }
        } else if (mode == PinOverlay.MODE_STONECUTTING || mode == PinOverlay.MODE_SMITHING) {
            ItemStack first;
            if (mode == PinOverlay.MODE_STONECUTTING) {
                var display = RecipeViewerIndex.asStonecutter(entry);
                first = display == null ? ItemStack.EMPTY
                        : select(RecipeViewerIndex.resolveSlotDisplay(display.input()), selIdx);
            } else {
                var display = RecipeViewerIndex.asSmithing(entry);
                first = display == null ? ItemStack.EMPTY
                        : select(RecipeViewerIndex.resolveSlotDisplay(display.base()), selIdx);
            }
            if (consumeOwned(remaining, first)) {
                holes.add(new int[]{x + 2, y + 2, x + 18, y + 18});
            }
        } else if (mode == PinOverlay.MODE_FURNACE) {
            var display = RecipeViewerIndex.asFurnace(entry);
            ItemStack ingredient = display == null ? ItemStack.EMPTY
                    : select(RecipeViewerIndex.resolveSlotDisplay(display.ingredient()), selIdx);
            if (consumeOwned(remaining, ingredient)) {
                holes.add(new int[]{x + 2, y + 2, x + 18, y + 18});
            }
        } else if (!(BetterRecipeBook.config.alternativeRecipes.onHover)) {
            for (Object rawPos : slots) {
                OverlayRecipeButtonPosAccessor pos = (OverlayRecipeButtonPosAccessor) rawPos;
                ItemStack ingredient = pos.brbe$selectIngredient(selIdx);
                if (consumeOwned(remaining, ingredient)) {
                    int hx = x + 2 + pos.brbe$getX();
                    int hy = y + 2 + pos.brbe$getY();
                    holes.add(new int[]{hx, hy, hx + 16, hy + 16});
                }
            }
        }
    }

    /** Render the recipe as a full popup: the adapted-synthetic JEI UI, or the
     *  vanilla hover-scaled recipe over the button sprite.
     *
     *  @return true if an adapted synthetic renderer painted it (the caller
     *          must not add its own backdrop); false for the vanilla popup.
     */
    public static boolean renderRecipePopup(GuiGraphicsExtractor gui,
                                            RecipeDisplayId id, RecipeDisplayEntry entry,
                                            int mode, boolean craftable, boolean partial,
                                            List<?> slots, int selIdx,
                                            int x, int y, int w, int h,
                                            boolean hover, float scale) {
        // Adapted synthetic: delegate the full JEI UI to the companion renderer,
        // which receives the fitted content rect from the shared geometry (the
        // panel, hit volume and JEI exclusion all come from the same geometry).
        if (RecipeViewerEngine.isSynthetic(id)
                && SyntheticRecipeRenderers.get() != SyntheticRecipeRenderer.NONE
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
    public static void renderBaseButton(GuiGraphicsExtractor gui,
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
        // 界面本体先盖整块红罩，再挖掉已拥有材料的槽位格。
        if (partial || !craftable) {
            List<int[]> holes = new ArrayList<>();
            collectOwnedSlots(id, entry, mode, slots, selIdx, x, y, holes);
            maskWithHoles(gui, x + 1, y + 1, x + w - 1, y + h - 1, holes);
        }
        renderSlotItems(gui, id, entry, mode, slots, selIdx, x, y, w, h, false);
    }

    private static void renderVanillaPopup(GuiGraphicsExtractor gui,
                                           RecipeDisplayId id, RecipeDisplayEntry entry,
                                           int mode, boolean craftable, boolean partial,
                                           List<?> slots, int selIdx,
                                           int x, int y, int w, int h,
                                           boolean hover, float scale) {
        if (hover) {
            gui.pose().pushMatrix();
            gui.pose().translate(x + w / 2f, y + h / 2f);
            gui.pose().scale(scale, scale);
            gui.pose().translate(-(x + w / 2f), -(y + h / 2f));
        }
        ButtonBackdrop backdrop = resolveBackdrop(id, mode, craftable, partial, hover);
        backdrop.render(gui, x, y, w, h);
        // 界面本体先盖整块红罩（synthetic 自带背景时抑制），再挖掉已拥有材料的槽位格。
        if ((partial || !craftable) && !backdrop.suppressesPartialOverlay()) {
            List<int[]> holes = new ArrayList<>();
            collectOwnedSlots(id, entry, mode, slots, selIdx, x, y, holes);
            maskWithHoles(gui, x + 1, y + 1, x + w - 1, y + h - 1, holes);
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
    private static void renderSlotItems(GuiGraphicsExtractor gui,
                                        RecipeDisplayId id, RecipeDisplayEntry entry,
                                        int mode, List<?> slots, int selIdx,
                                        int x, int y, int w, int h, boolean hover) {
        gui.pose().pushMatrix();
        if (RecipeViewerEngine.isSynthetic(id)) {
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
            gui.item(select(resultVariants(entry), selIdx), x + 4, y + 4);
        } else {
            // Crafting: vanilla slot positions (16px icon centred on the
            // translate point), materials cycled like ghost ingredients.
            gui.pose().translate(x + 2, y + 2);
            for (Object rawPos : slots) {
                OverlayRecipeButtonPosAccessor pos = (OverlayRecipeButtonPosAccessor) rawPos;
                gui.pose().pushMatrix();
                gui.pose().translate(pos.brbe$getX(), pos.brbe$getY());
                gui.pose().scale(0.375f, 0.375f);
                gui.pose().translate(-8.0F, -8.0F);
                gui.item(pos.brbe$selectIngredient(selIdx), 0, 0);
                gui.pose().popMatrix();
            }
        }
        gui.pose().popMatrix();
    }

    /** Stonecutter / smithing: input (base) top-left at (2,2), result
     *  bottom-right at (12,7), scaled 0.6; "result only" when not hovered. */
    private static void renderFixedPair(GuiGraphicsExtractor gui, RecipeDisplayEntry entry,
                                        int mode, int selIdx, int x, int y, boolean hover) {
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
            gui.item(second, x + 4, y + 4);
        } else {
            scaledItem(gui, first, x + 2, y + 2);
            scaledItem(gui, second, x + 12, y + 7);
        }
    }

    /** Furnace: ingredient top-left, flame bottom-left, result right half. */
    private static void renderFurnace(GuiGraphicsExtractor gui, RecipeDisplayEntry entry,
                                      int selIdx, int x, int y, boolean hover) {
        boolean onHover = BetterRecipeBook.config.alternativeRecipes.onHover;
        var display = RecipeViewerIndex.asFurnace(entry);
        ItemStack ingredient = display == null ? ItemStack.EMPTY
                : select(RecipeViewerIndex.resolveSlotDisplay(display.ingredient()), selIdx);
        ItemStack result = display == null ? ItemStack.EMPTY
                : select(RecipeViewerIndex.resolveSlotDisplay(display.result()), selIdx);
        if (onHover && !hover) {
            gui.item(result, x + 4, y + 4);
        } else {
            scaledItem(gui, ingredient, x + 2, y + 2);
            ClientCompat.blitSprite(gui, BRBTextures.FURNACE_FIRE_SPRITE, x + 4, y + 15, 6, 6);
            if (hover) {
                scaledItem(gui, result, x + 12, y + 7);
            }
        }
    }

    private static void scaledItem(GuiGraphicsExtractor gui, ItemStack stack, int tx, int ty) {
        if (stack.isEmpty()) return;
        gui.pose().pushMatrix();
        gui.pose().translate(tx, ty);
        gui.pose().scale(0.6f, 0.6f);
        gui.item(stack, 0, 0);
        gui.pose().popMatrix();
    }

    /** An unadapted synthetic recipe: the result item when not hovered,
     *  otherwise its fitted native layout slots. */
    private static void renderSynthetic(GuiGraphicsExtractor gui, RecipeDisplayId id,
                                        RecipeDisplayEntry entry, int selIdx,
                                        int x, int y, boolean hover) {
        if (BetterRecipeBook.config.alternativeRecipes.onHover && !hover) {
            gui.item(select(resultVariants(entry), selIdx), x + 4, y + 4);
            return;
        }
        RecipeViewerEngine.RecipeLayout layout = RecipeViewerEngine.getLayout(id);
        if (layout == null || layout.slots().isEmpty()) {
            gui.item(select(resultVariants(entry), selIdx), x + 4, y + 4);
            return;
        }
        List<PopupGeometry.UnadaptedSlot> slots = hover && layout.background() != null
                ? PopupGeometry.backgroundFitPositions(layout)
                : PopupGeometry.slotFitPositions(layout);
        for (PopupGeometry.UnadaptedSlot slot : slots) {
            gui.pose().pushMatrix();
            gui.pose().translate(x + slot.x(), y + slot.y());
            gui.pose().scale(0.45f, 0.45f);
            gui.pose().translate(-8.0F, -8.0F);
            gui.item(slot.stacks().get(selIdx % slot.stacks().size()), 0, 0);
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
}
