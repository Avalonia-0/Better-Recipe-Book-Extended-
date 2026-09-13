package com.alonie.brbe.util;

import com.alonie.brbe.cache.RecipeViewerIndex;
import com.alonie.brbe.compat.SyntheticRecipeRenderer;
import com.alonie.brbe.compat.SyntheticRecipeRenderers;
import com.alonie.brbe.mixins.accessors.OverlayRecipeButtonAccessor;
import com.alonie.brbe.mixins.accessors.OverlayRecipeComponentAccessor;
import com.alonie.brbe.pinoverlay.PinOverlay;
import com.alonie.brbe.recipeviewer.engine.RecipeViewerEngine;
import com.alonie.brbe.render.PopupGeometry;
import com.alonie.brbe.render.PopupRenderer;
import com.alonie.brbe.util.PartialCraftingUtil;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.recipebook.OverlayRecipeComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;

import java.util.List;

/**
 * The query-viewer's hover popup as an independent layer (not a button-hover
 * variant): it holds the hovered recipe's button and renders the enlarged popup
 * over the viewer, with its own hit area ({@link #contains}, the shared
 * {@link PopupGeometry} — equal to the rendered texture) and modal click
 * interception.  Triggered and kept alive by {@code RecipeViewerOverlay}'s
 * hover-popup signal (only while Shift is held); closed when the cursor leaves
 * the popup or Shift is released.
 */
public final class RecipePopupLayer {

    private RecipePopupLayer() {}

    private static AbstractWidget button;
    private static RecipeDisplayId id;
    private static RecipeDisplayEntry entry;
    private static int mode = PinOverlay.MODE_CRAFTING;
    private static boolean craftable;
    private static boolean partial;
    private static List<?> slots;
    private static boolean active;

    /** Set the popup to {@code btn}'s recipe, or close it when null. */
    public static void update(AbstractWidget btn) {
        if (!(btn instanceof OverlayRecipeButtonAccessor oba)) {
            close();
            return;
        }
        button = btn;
        id = oba.brbe$getRecipe();
        entry = RecipeViewerEngine.entryFor(id);
        slots = oba.brbe$getSlots();
        craftable = oba.brbe$getCraftable();
        OverlayRecipeComponent outer = oba.brbe$getOuterComponent();
        RecipeCollection collection = outer.getRecipeCollection();
        partial = computePartial(outer, collection);
        mode = computeMode();
        active = true;
    }

    public static void close() {
        active = false;
        button = null;
        id = null;
        entry = null;
        slots = null;
    }

    public static boolean isActive() {
        return active;
    }

    public static AbstractWidget button() {
        return button;
    }

    public static RecipeDisplayId recipeId() {
        return id;
    }

    /** Render the enlarged popup over the viewer (no button magnify; the popup
     *  is its own layer) at the fixed {@link PopupGeometry#VANILLA_SCALE} — the
     *  Shift magnify is gone. */
    public static void render(GuiGraphics gui, float delta) {
        if (!active || button == null || id == null) return;
        // 面板里的每个槽位各自做折叠锁判定（PopupRenderer 的 SlotCycle），这里
        // 只给自动下标（用户 2026-09-13 诉求 1/2：指着预览里的哪件物品，锁定键
        // 与滚轮就只作用于那一件）。弹窗是**最上层**：指针在它上面时先清掉本帧
        // 的登记，铺在它下面的界面物品不再抢占滚轮。
        if (contains(CycleLock.cursorX(), CycleLock.cursorY())) {
            CycleLock.clearHovered();
        }
        int selIdx = autoSlotSelectIndex();
        PopupRenderer.renderRecipePopup(gui, id, entry, mode, craftable, partial,
                slots, selIdx, button.getX(), button.getY(), button.getWidth(), button.getHeight(),
                true, PopupGeometry.VANILLA_SCALE,
                // 缺料遮罩只由实时库存逐槽判定（见 RecipePreviewTooltipComponent 同款注释）：
                // 不再用 (craftable && !partial) 决定是否整块跳过遮罩。
                PartialCraftingUtil.searchSpaceItemCounts(), false);
    }

    /** Whether the cursor is inside the popup (its modal area = hit volume). */
    public static boolean contains(double mx, double my) {
        return active && button != null
                && geometry().contains(mx, my);
    }

    /** The item rendered under the cursor inside the popup (cycled variant).
     *  For JEI-adapted popups the item comes from the live JEI drawable (which
     *  drives the visible cycling itself), so the tooltip matches the painted
     *  variant. */
    public static ItemStack itemAt(int mx, int my) {
        if (!active || button == null) return ItemStack.EMPTY;
        PopupGeometry geometry = geometry();
        SyntheticRecipeRenderer renderer = SyntheticRecipeRenderers.get();
        if (renderer != SyntheticRecipeRenderer.NONE && renderer.canRender(id)) {
            ItemStack painted = renderer.itemUnderMouse(id, mx, my,
                    geometry.ox, geometry.oy, geometry.fit);
            if (!painted.isEmpty()) {
                return painted;
            }
        }
        return geometry.itemAt(mx, my, CycleLock.hoveredOr(autoSlotSelectIndex()));
    }

    private static PopupGeometry geometry() {
        return PopupGeometry.of(id, entry, mode, slots,
                button.getX(), button.getY(), button.getWidth(), button.getHeight());
    }

    /** 原版 SlotSelectTime 的自动下标（逐物品的冻结下标由 PopupRenderer 的
     *  SlotCycle / {@link CycleLock} 逐槽位处理）。 */
    private static int autoSlotSelectIndex() {
        OverlayRecipeComponent outer = ((OverlayRecipeButtonAccessor) button).brbe$getOuterComponent();
        return ((OverlayRecipeComponentAccessor) outer).getSlotSelectTime().currentIndex();
    }

    private static boolean computePartial(OverlayRecipeComponent outer, RecipeCollection collection) {
        if (RecipeViewerOverlay.isFurnaceMode()
                || ((OverlayRecipeComponentAccessor) outer).isFurnaceMenu()) {
            return false;
        }
        if (RecipeViewerIndex.isViewerCollection(collection)) {
            return RecipeViewerIndex.isViewerPartial(collection, id)
                    || PartialCraftingUtil.isPartiallyCraftableEvenIfStale(collection, id);
        }
        return PartialCraftingUtil.isPartiallyCraftable(collection, id);
    }

    private static int computeMode() {
        return RecipeViewerOverlay.viewerMode();
    }
}
