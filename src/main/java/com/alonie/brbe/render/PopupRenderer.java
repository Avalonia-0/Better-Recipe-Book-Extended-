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
import com.alonie.brbe.util.PartialGhostOverlayUtil;
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
        return renderRecipePopup(gui, id, entry, mode, craftable, partial, slots, selIdx,
                x, y, w, h, hover, scale, null, true);
    }

    /** {@code counts} = 检索空间物品数量表（非 null 时，残缺/不可合成对象的
     *  预览/pin 复刻工作站幽灵物品摆放：缺料**输入**槽位画红罩，已有材料的槽位
     *  显示完整不透明物品；产物槽位与烧炼类别不加任何标记）。可合成对象传
     *  null 不画任何遮罩。
     *  {@code highlighted} = 是否用高亮纹理面（配方书 hover 放大用 true；
     *  查询预览/pin 用 false——无高亮版本，让可合成/不可合成面清晰可辨）。 */
    public static boolean renderRecipePopup(GuiGraphicsExtractor gui,
                                            RecipeDisplayId id, RecipeDisplayEntry entry,
                                            int mode, boolean craftable, boolean partial,
                                            List<?> slots, int selIdx,
                                            int x, int y, int w, int h,
                                            boolean hover, float scale,
                                            Map<Item, Integer> counts,
                                            boolean highlighted) {
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
                // The delegated JEI UI keeps its own look: the whole-popup
                // partial cover is intentionally omitted — the per-slot
                // workstation-style ghost masks (red overlay per missing
                // input slot cell) are overlaid on the drawable instead.
                if (counts != null) drawDelegatedGhostMasksAt(gui, id,
                        Math.round(geometry.ox), Math.round(geometry.oy), geometry.fit, counts);
                return true;
            }
        }
        renderVanillaPopup(gui, id, entry, mode, craftable, partial, slots, selIdx,
                x, y, w, h, hover, scale, counts, highlighted);
        return false;
    }

    /** Render the recipe's base button (no hover magnify): sprite backdrop
     *  (highlighted when hovered), partial marking and the small slot icons —
     *  used by the query-viewer's buttons, whose popup is drawn by the separate
     *  popup layer.  {@code lockReveal}: the query viewer LOCKS the "只在悬停
     *  时显示替代配方" hover-reveal design (product icon until hovered, full
     *  recipe layout on hover) — it is not governed by the toggle's current
     *  value (the toggle only steers the recipe book's buttons). */
    public static void renderBaseButton(GuiGraphicsExtractor gui,
                                        RecipeDisplayId id, RecipeDisplayEntry entry,
                                        int mode, boolean craftable, boolean partial,
                                        List<?> slots, int selIdx,
                                        int x, int y, int w, int h, boolean hover,
                                        boolean lockReveal) {
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
        renderSlotItems(gui, id, entry, mode, slots, selIdx, x, y, w, h, false, null, lockReveal);
    }

    private static void renderVanillaPopup(GuiGraphicsExtractor gui,
                                           RecipeDisplayId id, RecipeDisplayEntry entry,
                                           int mode, boolean craftable, boolean partial,
                                           List<?> slots, int selIdx,
                                           int x, int y, int w, int h,
                                           boolean hover, float scale,
                                           Map<Item, Integer> counts,
                                           boolean highlighted) {
        if (hover) {
            gui.pose().pushMatrix();
            gui.pose().translate(x + w / 2f, y + h / 2f);
            gui.pose().scale(scale, scale);
            gui.pose().translate(-(x + w / 2f), -(y + h / 2f));
        }
        ButtonBackdrop backdrop = resolveBackdrop(id, mode, craftable, partial, hover, highlighted);
        backdrop.render(gui, x, y, w, h);
        // 残缺/不可合成对象的整块红罩已移除——缺料状态改由逐槽位的工作站幽灵
        // 红罩表达（与原版工作站幽灵物品一致），不再整块盖红。
        renderSlotItems(gui, id, entry, mode, slots, selIdx, x, y, w, h, hover, counts, false);
        if (hover) {
            gui.pose().popMatrix();
        }
    }

    /** The single backdrop for this frame: the uniform vanilla sprite, or — only
     *  for a hovered unadapted-synthetic recipe with its own background — the JEI
     *  texture.  Preview/pin 的**三种状态统一使用可合成纹理**（用户要求：不可合成
     *  对象与残缺对象的预览/pin 背景与可合成对象相同）——状态差异只由逐槽位的
     *  工作站幽灵红罩表达（缺料输入槽红罩），不再用 disabled 纹理面区分。 */
    private static ButtonBackdrop resolveBackdrop(RecipeDisplayId id, int mode,
                                                  boolean craftable, boolean partial, boolean hover,
                                                  boolean highlighted) {
        RecipeViewerEngine.RecipeLayout layout = RecipeViewerEngine.getLayout(id);
        if (hover && layout != null && layout.background() != null) {
            RecipeViewerEngine.RecipeBackground bg = layout.background();
            return new ButtonBackdrop.Texture(bg.texture(), bg.u(), bg.v(), bg.width(), bg.height(),
                    bg.textureWidth(), bg.textureHeight());
        }
        WidgetSprites sprites = mode == PinOverlay.MODE_FURNACE
                ? BRBTextures.RECIPE_BOOK_PLAIN_OVERLAY_SPRITE
                : BRBTextures.RECIPE_BOOK_CRAFTING_OVERLAY_SPRITE;
        // 合成类别（非熔炉系）：三种状态统一 vanilla crafting_overlay.png（可合成面）
        // ——不可合成/残缺对象不再使用 crafting_overlay_disabled.png（缺料状态由
        // 逐槽位幽灵遮罩表达）。
        if (mode != PinOverlay.MODE_FURNACE) {
            return new ButtonBackdrop.Sprite(
                    Identifier.withDefaultNamespace("recipe_book/crafting_overlay"));
        }
        // 熔炉系：BRBE plain 系列（预览/pin 无高亮面），三种状态统一启用面。
        return new ButtonBackdrop.Sprite(sprites.get(true, highlighted));
    }

    /** The recipe's slot icons, cycled by {@code selIdx}, laid out per mode.
     *  {@code counts} (nullable) = 检索空间数量表：非 null 时逐槽位补画工作站
     *  幽灵红罩——缺料**输入**槽 = 红罩底(0x66FF0000) + 物品（无白罩）；已有材料
     *  槽 = 完整不透明物品；产物槽不标记；烧炼类别完全不使用幽灵遮罩。
     *  {@code lockReveal}: 查询界面按钮锁定"悬停揭示"设计（不计 onHover 开关：
     *  未悬停只画产物、悬停画完整配方布局）。 */
    private static void renderSlotItems(GuiGraphicsExtractor gui,
                                        RecipeDisplayId id, RecipeDisplayEntry entry,
                                        int mode, List<?> slots, int selIdx,
                                        int x, int y, int w, int h, boolean hover,
                                        Map<Item, Integer> counts, boolean lockReveal) {
        gui.pose().pushMatrix();
        if (RecipeViewerEngine.getLayout(id) != null) {
            renderSynthetic(gui, id, entry, selIdx, x, y, hover, counts, lockReveal);
        } else if (mode == PinOverlay.MODE_STONECUTTING) {
            renderFixedPair(gui, entry, PinOverlay.MODE_STONECUTTING, selIdx, x, y, hover, counts, lockReveal);
        } else if (mode == PinOverlay.MODE_SMITHING) {
            renderFixedPair(gui, entry, PinOverlay.MODE_SMITHING, selIdx, x, y, hover, counts, lockReveal);
        } else if (mode == PinOverlay.MODE_FURNACE) {
            // 烧炼类别一概不用幽灵遮罩（用户要求）。
            renderFurnace(gui, entry, selIdx, x, y, hover, lockReveal);
        } else if ((BetterRecipeBook.config.alternativeRecipes.onHover || lockReveal) && !hover) {
            // The product shown on the button cycles through every result
            // variant (like the smithing category), so multi-product recipes
            // display all of their products instead of only the first.
            ItemStack result = select(resultVariants(entry), selIdx);
            gui.item(result, x + 4, y + 4);
        } else if (mode == PinOverlay.MODE_CRAFTING && slots != null && !slots.isEmpty()) {
            // Crafting: vanilla slot positions (16px icon centred on the
            // translate point), materials cycled like ghost ingredients.
            List<OverlayRecipeButtonPosAccessor> poss = new ArrayList<>();
            for (Object rawPos : slots) {
                poss.add((OverlayRecipeButtonPosAccessor) rawPos);
            }
            List<PartialGhostOverlayUtil.GhostSlotSample> samples = new ArrayList<>();
            for (OverlayRecipeButtonPosAccessor pos : poss) {
                samples.add(new PartialGhostOverlayUtil.GhostSlotSample(
                        pos.brbe$getX(), pos.brbe$getY(), pos.brbe$getIngredients()));
            }
            boolean[] missing = PartialGhostOverlayUtil.computeMissing(samples, counts);
            gui.pose().translate(x + 2, y + 2);
            for (int i = 0; i < poss.size(); i++) {
                OverlayRecipeButtonPosAccessor pos = poss.get(i);
                gui.pose().pushMatrix();
                gui.pose().translate(pos.brbe$getX(), pos.brbe$getY());
                gui.pose().scale(0.375f, 0.375f);
                gui.pose().translate(-8.0F, -8.0F);
                ItemStack stack = pos.brbe$selectIngredient(selIdx);
                drawGhostItem(gui, stack, missing[i]);
                gui.pose().popMatrix();
            }
        } else {
            // Displays without vanilla button slots (e.g. Farmer's Delight
            // cooking recipes) fall back to the generic entry layout.
            renderGenericCrafting(gui, entry, selIdx, x, y, hover, counts, lockReveal);
        }
        gui.pose().popMatrix();
    }

    /** Stonecutter / smithing: input (base) top-left at (2,2), result
     *  bottom-right at (12,7), scaled 0.6; "result only" when not hovered.
     *  Entries whose display is not the expected type (e.g. local-cache
     *  fallbacks) render the generic entry layout instead of a blank button.
     *  {@code lockReveal}: 查询界面按钮锁定"悬停揭示"设计（不计 onHover 开关）。 */
    private static void renderFixedPair(GuiGraphicsExtractor gui, RecipeDisplayEntry entry,
                                        int mode, int selIdx, int x, int y, boolean hover,
                                        Map<Item, Integer> counts, boolean lockReveal) {
        if (mode == PinOverlay.MODE_STONECUTTING
                && RecipeViewerIndex.asStonecutter(entry) == null) {
            renderGenericCrafting(gui, entry, selIdx, x, y, hover, counts, lockReveal);
            return;
        }
        if (mode == PinOverlay.MODE_SMITHING
                && RecipeViewerIndex.asSmithing(entry) == null) {
            renderGenericCrafting(gui, entry, selIdx, x, y, hover, counts, lockReveal);
            return;
        }
        boolean onHover = BetterRecipeBook.config.alternativeRecipes.onHover;
        ItemStack first;
        ItemStack second;
        List<ItemStack> inputVariants;
        if (mode == PinOverlay.MODE_STONECUTTING) {
            var display = RecipeViewerIndex.asStonecutter(entry);
            inputVariants = display == null ? List.of()
                    : RecipeViewerIndex.resolveSlotDisplay(display.input());
            first = display == null ? ItemStack.EMPTY : select(inputVariants, selIdx);
            second = display == null ? ItemStack.EMPTY
                    : select(RecipeViewerIndex.resolveSlotDisplay(display.result()), selIdx);
        } else {
            var display = RecipeViewerIndex.asSmithing(entry);
            inputVariants = display == null ? List.of()
                    : RecipeViewerIndex.resolveSlotDisplay(display.base());
            first = display == null ? ItemStack.EMPTY : select(inputVariants, selIdx);
            second = display == null ? ItemStack.EMPTY
                    : select(RecipeViewerIndex.resolveSlotDisplay(display.result()), selIdx);
        }
        if ((onHover || lockReveal) && !hover) {
            gui.item(second, x + 4, y + 4);
            return;
        }
        boolean inputMissing = counts != null && PartialGhostOverlayUtil.computeMissing(
                List.of(new PartialGhostOverlayUtil.GhostSlotSample(2, 2, inputVariants)),
                counts)[0];
        scaledItem(gui, first, x + 2, y + 2, inputMissing);
        scaledItem(gui, second, x + 12, y + 7, false); // 产物不标记
    }

    /** Furnace: ingredient top-left, flame bottom-left, result right half.
     *  烧炼类别一概不用幽灵遮罩（用户要求）：材料槽与产物都完整不透明显示。
     *  {@code lockReveal}: 查询界面按钮锁定"悬停揭示"设计（不计 onHover 开关）。 */
    private static void renderFurnace(GuiGraphicsExtractor gui, RecipeDisplayEntry entry,
                                      int selIdx, int x, int y, boolean hover, boolean lockReveal) {
        boolean onHover = BetterRecipeBook.config.alternativeRecipes.onHover;
        var display = RecipeViewerIndex.asFurnace(entry);
        ItemStack ingredient = display == null ? ItemStack.EMPTY
                : select(RecipeViewerIndex.resolveSlotDisplay(display.ingredient()), selIdx);
        ItemStack result = display == null ? ItemStack.EMPTY
                : select(RecipeViewerIndex.resolveSlotDisplay(display.result()), selIdx);
        if ((onHover || lockReveal) && !hover) {
            gui.item(result, x + 4, y + 4);
            return;
        }
        scaledItem(gui, ingredient, x + 2, y + 2, false);
        ClientCompat.blitSprite(gui, BRBTextures.FURNACE_FIRE_SPRITE, x + 4, y + 15, 6, 6);
        if (hover) {
            scaledItem(gui, result, x + 12, y + 7, false);
        }
    }

    /** 物品（0.6 缩放）＋（缺料时）工作站幽灵红罩。 */
    private static void scaledItem(GuiGraphicsExtractor gui, ItemStack stack, int tx, int ty,
                                   boolean missing) {
        if (stack.isEmpty()) return;
        gui.pose().pushMatrix();
        gui.pose().translate(tx, ty);
        gui.pose().scale(0.6f, 0.6f);
        drawGhostItem(gui, stack, missing);
        gui.pose().popMatrix();
    }

    /** An unadapted synthetic recipe: the result item when not hovered,
     *  otherwise its fitted native layout slots.  {@code lockReveal}: 查询界面
     *  按钮锁定"悬停揭示"设计（不计 onHover 开关）。 */
    private static void renderSynthetic(GuiGraphicsExtractor gui, RecipeDisplayId id,
                                        RecipeDisplayEntry entry, int selIdx,
                                        int x, int y, boolean hover,
                                        Map<Item, Integer> counts, boolean lockReveal) {
        if ((BetterRecipeBook.config.alternativeRecipes.onHover || lockReveal) && !hover) {
            ItemStack result = select(resultVariants(entry), selIdx);
            gui.item(result, x + 4, y + 4);
            return;
        }
        RecipeViewerEngine.RecipeLayout layout = RecipeViewerEngine.getLayout(id);
        if (layout == null || layout.slots().isEmpty()) {
            ItemStack result = select(resultVariants(entry), selIdx);
            gui.item(result, x + 4, y + 4);
            return;
        }
        // 逐槽位幽灵判定：role 0 = 输入（参与材料扣除），role 1 = 输出（产物，
        // 不加遮罩），role 2/3 = 工作站/仅渲染槽（不参与、不遮罩）。
        List<RecipeViewerEngine.RecipeSlotLayout> slotLayouts = layout.slots();
        List<PartialGhostOverlayUtil.GhostSlotSample> samples = new ArrayList<>();
        int[] sampleIdx = new int[slotLayouts.size()];
        java.util.Arrays.fill(sampleIdx, -1);
        for (int i = 0; i < slotLayouts.size(); i++) {
            RecipeViewerEngine.RecipeSlotLayout slot = slotLayouts.get(i);
            if (slot.role() != 0 || slot.stacks().isEmpty()) continue;
            sampleIdx[i] = samples.size();
            samples.add(new PartialGhostOverlayUtil.GhostSlotSample(
                    slot.x(), slot.y(), slot.stacks()));
        }
        boolean[] missing = PartialGhostOverlayUtil.computeMissing(samples, counts);
        // Layout-fitted slots at the panel's own scale; the panel (and any
        // painted background texture) is centred on the button, so the slots
        // shift by the same centre offset (pose space: 12 = half of the
        // 24x24 button).
        float tscale = PopupGeometry.layoutFitScale(layout);
        float offX = 12f - layout.width() * tscale / 2f;
        float offY = 12f - layout.height() * tscale / 2f;
        for (int i = 0; i < slotLayouts.size(); i++) {
            RecipeViewerEngine.RecipeSlotLayout slot = slotLayouts.get(i);
            if (slot.stacks().isEmpty()) continue;
            gui.pose().pushMatrix();
            gui.pose().translate(x + offX + slot.x() * tscale, y + offY + slot.y() * tscale);
            gui.pose().scale(0.45f, 0.45f);
            gui.pose().translate(-8.0F, -8.0F);
            ItemStack stack = slot.stacks().get(selIdx % slot.stacks().size());
            boolean missingSlot;
            if (slot.role() == 1) {
                missingSlot = false; // 产物不标记
            } else if (sampleIdx[i] >= 0) {
                missingSlot = missing[sampleIdx[i]];
            } else {
                missingSlot = false; // 工作站/仅渲染槽：不遮罩
            }
            drawGhostItem(gui, stack, missingSlot);
            gui.pose().popMatrix();
        }
    }

    // ── 工作站幽灵物品摆放复刻（红罩）────────────────────────────────────
    /** 缺料槽红罩（与 {@code GhostSlotsMixin} 调高后的工作站红罩同值：
     *  原版 0x30FF0000 → 0x66FF0000）。 */
    private static final int GHOST_RED = 0x66FF0000;

    /** 在当前物品坐标系（物品 16x16 单元原点已就位）画一个幽灵物品：
     *  缺料（missing=true）→ 红罩底 + 物品（无白罩——用户要求只留红罩）；
     *  已有 → 普通完整物品。 */
    private static void drawGhostItem(GuiGraphicsExtractor gui, ItemStack stack, boolean missing) {
        if (stack.isEmpty()) return;
        if (missing) gui.fill(0, 0, 16, 16, GHOST_RED);
        gui.item(stack, 0, 0);
    }

    /** 委托渲染（完整 JEI UI）的幽灵遮罩：按布局槽位覆盖——仅缺料输入槽
     *  在 16px 单元格区域上叠红罩（drawable 已完成物品绘制，遮罩只能在
     *  其上）；产物槽与工作站/仅渲染槽不遮罩。红罩画完后把物品**重画在
     *  红罩之上**（从 live drawable 取当前变体，与底层绘制/tooltip 完全一致
     *  ——不按 BRBE 的 selIdx 猜测，轮循时不会错位）。tooltip 内嵌预览同用：
     *  contentX/Y + fit。 */
    public static void drawDelegatedGhostMasksAt(GuiGraphicsExtractor gui, RecipeDisplayId id,
                                                 int contentX, int contentY, float fit,
                                                 Map<Item, Integer> counts) {
        if (counts == null) return;
        RecipeViewerEngine.RecipeLayout layout = RecipeViewerEngine.getLayout(id);
        if (layout == null || layout.slots().isEmpty()) return;
        List<RecipeViewerEngine.RecipeSlotLayout> slotLayouts = layout.slots();
        List<PartialGhostOverlayUtil.GhostSlotSample> samples = new ArrayList<>();
        int[] sampleIdx = new int[slotLayouts.size()];
        java.util.Arrays.fill(sampleIdx, -1);
        for (int i = 0; i < slotLayouts.size(); i++) {
            RecipeViewerEngine.RecipeSlotLayout slot = slotLayouts.get(i);
            if (slot.role() != 0 || slot.stacks().isEmpty()) continue;
            sampleIdx[i] = samples.size();
            samples.add(new PartialGhostOverlayUtil.GhostSlotSample(
                    slot.x(), slot.y(), slot.stacks()));
        }
        boolean[] missing = PartialGhostOverlayUtil.computeMissing(samples, counts);
        int cell = Math.max(1, Math.round(16 * fit)); // 16px 单元格按 fit 缩放
        SyntheticRecipeRenderer renderer = SyntheticRecipeRenderers.get();
        for (int i = 0; i < slotLayouts.size(); i++) {
            RecipeViewerEngine.RecipeSlotLayout slot = slotLayouts.get(i);
            if (slot.role() != 0 || slot.stacks().isEmpty()) continue; // 产物/工作站/仅渲染槽不遮罩
            if (!missing[sampleIdx[i]]) continue;
            int sx = Math.round(contentX + slot.x() * fit);
            int sy = Math.round(contentY + slot.y() * fit);
            gui.fill(sx, sy, sx + cell, sy + cell, GHOST_RED);
            // 物品盖在红罩之上：从 live drawable 取该槽当前变体（= 底层已绘制
            // 的变体，tooltip/Alt 暂停/轮循全部一致）；查询失败时退到第一个变体。
            ItemStack shown = renderer.itemUnderMouse(id,
                    contentX + (slot.x() + 8) * fit, contentY + (slot.y() + 8) * fit,
                    contentX, contentY, fit);
            if (shown.isEmpty() && !slot.stacks().isEmpty()) {
                shown = slot.stacks().get(0);
            }
            if (!shown.isEmpty()) {
                gui.pose().pushMatrix();
                gui.pose().translate(sx, sy);
                gui.pose().scale(fit, fit);
                gui.item(shown, 0, 0);
                gui.pose().popMatrix();
            }
            // JEI 的候选标识（轮循槽位右下角的 tag/list 角标，标记 tag 式
            // 划分或预设式划分）由 drawable 在红罩之前绘制——与物品一样
            // 重画到红罩之上（仅缺料槽；渲染器对无角标功能的 JEI 版本
            // 空操作）。
            renderer.drawSlotBadge(id, gui,
                    contentX + (slot.x() + 8) * fit, contentY + (slot.y() + 8) * fit,
                    contentX, contentY, fit);
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
    private static void renderGenericCrafting(GuiGraphicsExtractor gui, RecipeDisplayEntry entry,
                                              int selIdx, int x, int y, boolean hover,
                                              Map<Item, Integer> counts, boolean lockReveal) {
        boolean onHover = BetterRecipeBook.config.alternativeRecipes.onHover;
        if ((onHover || lockReveal) && !hover) {
            ItemStack result = select(resultVariants(entry), selIdx);
            gui.item(result, x + 4, y + 4);
            return;
        }
        if (entry == null) return;
        try {
            java.util.Optional<List<net.minecraft.world.item.crafting.Ingredient>> reqs =
                    entry.craftingRequirements();
            if (reqs.isPresent()) {
                List<net.minecraft.world.item.crafting.Ingredient> list = reqs.get();
                int count = Math.min(list.size(), 6);
                List<PartialGhostOverlayUtil.GhostSlotSample> samples = new ArrayList<>();
                for (int i = 0; i < count; i++) {
                    List<ItemStack> variants = new ArrayList<>();
                    list.get(i).items().forEach(holder -> variants.add(new ItemStack(holder.value())));
                    samples.add(new PartialGhostOverlayUtil.GhostSlotSample(
                            i % 3, i / 3, variants));
                }
                boolean[] missing = PartialGhostOverlayUtil.computeMissing(samples, counts);
                for (int i = 0; i < count; i++) {
                    ItemStack stack = selectIngredient(list.get(i), selIdx);
                    if (!stack.isEmpty()) {
                        scaledItem(gui, stack, x + 2 + (i % 3) * 5, y + 2 + (i / 3) * 5,
                                counts != null && missing[i]);
                    }
                }
            }
        } catch (Exception ignored) {
            // one broken ingredient must not blank the whole popup
        }
        ItemStack result = select(resultVariants(entry), selIdx);
        if (!result.isEmpty()) {
            scaledItem(gui, result, x + 17, y + 2, false); // 产物不标记
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
