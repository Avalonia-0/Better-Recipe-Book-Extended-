package com.alonie.brbe.pinoverlay;

import com.alonie.brbe.cache.RecipeViewerIndex;
import com.alonie.brbe.mixins.accessors.OverlayRecipeButtonAccessor;
import com.alonie.brbe.mixins.accessors.OverlayRecipeComponentAccessor;
import com.alonie.brbe.recipeviewer.engine.RecipeViewerEngine;
import com.alonie.brbe.render.PopupGeometry;
import com.alonie.brbe.util.BRBTextures;
import com.alonie.brbe.util.ClientCompat;
import com.alonie.brbe.util.PartialCraftingUtil;
import com.alonie.brbe.util.RecipeViewerOverlay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.recipebook.OverlayRecipeComponent;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.display.FurnaceRecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.minecraft.world.item.crafting.display.SmithingRecipeDisplay;
import net.minecraft.world.item.crafting.display.StonecutterRecipeDisplay;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * A single pin overlay: a <b>cloned recipe button</b> whose front-end is exactly
 * the live hover preview (the shared {@link PopupGeometry} — fixed 2x for
 * vanilla recipes, the JEI panel for adapted mod recipes), with every item it
 * renders tracked through the shared geometry as an independent, interactive
 * object — {@link #itemAt} answers per-item tooltips (Shift-gated) and R/U
 * queries, and a click on the pin inherits the recipe button's click (placing
 * the recipe when the station matches).  A pin icon ({@code overlay_pin},
 * 32x32) sits at the rendered UI's top-left corner.
 */
public final class PinOverlay {

    public static final int MODE_CRAFTING = 0;
    public static final int MODE_FURNACE = 1;
    public static final int MODE_STONECUTTING = 2;
    public static final int MODE_SMITHING = 3;

    /** Pin icon footprint; window minimum edge. */
    private static final int MIN_EDGE = 32;
    /** Cloned button footprint before zoom. */
    private static final int CELL = 24;

    private final RecipeDisplayEntry entry;
    private final RecipeDisplayId id;
    private final int mode;
    private int z;
    private int cx;
    private int cy;
    /** Whether the pin may show its hover tooltip (always true; the pin shows
     *  its tooltip as soon as the cursor is over it). */
    private boolean tooltipArmed = true;
    /** Cloned button (renders the full recipe layout at the frozen zoom). */
    private final AbstractWidget button;
    /** The clone's own overlay component (its slot-select clock and the
     *  collection backing the button). */
    private final OverlayRecipeComponent component;
    /** The collection backing the cloned button, so a click on the pin can
     *  place the recipe exactly like the viewer's popup click. */
    private final RecipeCollection collection;

    private PinOverlay(RecipeDisplayEntry entry, int mode, int z,
                       AbstractWidget button, OverlayRecipeComponent component,
                       RecipeCollection collection) {
        this.entry = entry;
        this.id = entry.id();
        this.mode = mode;
        this.z = z;
        this.button = button;
        this.component = component;
        this.collection = collection;
    }

    /** Build a pin from a pinned recipe: clone the button and keep the
     *  collection it was cloned from. */
    public static PinOverlay create(RecipeDisplayEntry entry, int mode, int z,
                                    int anchorX, int anchorY) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return null;
        // Reuse the query-viewer's recipe collection when pinning from it, so
        // the clone renders the source recipe's craftable / partial state
        // instead of recomputing it against a fresh single-recipe collection.
        RecipeCollection collection = RecipeViewerOverlay.capturedOverlayCollection();
        if (collection == null) {
            StackedItemContents stacked = new StackedItemContents();
            PartialCraftingUtil.fillSearchSpaceStackedContents(stacked);
            collection = RecipeViewerIndex.toCollection(List.of(entry), stacked);
        }
        OverlayRecipeComponent component = new OverlayRecipeComponent(
                () -> Mth.floor(net.minecraft.util.Util.getMillis() / 1500.0D), false);
        component.init(collection, SlotDisplayContext.fromLevel(mc.level),
                false, anchorX - CELL / 2, anchorY - CELL / 2, CELL, CELL, 0f);
        List<AbstractWidget> buttons = ((OverlayRecipeComponentAccessor) component).getRecipeButtons();
        // The source collection may hold many recipes; pick the exact button
        // for the pinned entry rather than assuming it is the first one.
        AbstractWidget target = null;
        for (AbstractWidget b : buttons) {
            if (b instanceof OverlayRecipeButtonAccessor oba
                    && oba.brbe$getRecipe().equals(entry.id())) {
                target = b;
                break;
            }
        }
        if (target == null) target = buttons.isEmpty() ? null : buttons.get(0);
        if (target == null) return null;
        PinOverlay pin = new PinOverlay(entry, mode, z, target, component, collection);
        pin.setCenter(anchorX, anchorY);
        return pin;
    }

    // ── Geometry (shared with the hover UI) ──────────────────────────────

    private int btnX() {
        return cx - CELL / 2;
    }

    private int btnY() {
        return cy - CELL / 2;
    }

    private PopupGeometry geometry() {
        List<?> slots = button instanceof OverlayRecipeButtonAccessor oba
                ? oba.brbe$getSlots() : List.of();
        return PopupGeometry.of(id, entry, mode, slots, btnX(), btnY(), CELL, CELL);
    }

    /** The pin's full interactive / rendered region on screen — the shared
     *  popup geometry's bounds (hit volume = texture size).  Used by
     *  {@link #contains} and (via {@code PinOverlayManager}) by JEI's
     *  exclusion areas, so both match the actual UI size. */
    Rect2i interactiveBounds() {
        PopupGeometry g = geometry();
        return new Rect2i(g.x, g.y, g.w, g.h);
    }

    int boxW() {
        return Math.max(CELL * 2, MIN_EDGE);
    }

    int boxH() {
        return boxW();
    }

    int boxX() {
        return cx - boxW() / 2;
    }

    int boxY() {
        return cy - boxH() / 2;
    }

    boolean contains(double mx, double my) {
        Rect2i b = interactiveBounds();
        return mx >= b.getX() && mx < b.getX() + b.getWidth()
                && my >= b.getY() && my < b.getY() + b.getHeight();
    }

    /** Move the window centre, clamped so the WHOLE rendered UI stays on
     *  screen.  The clamp uses the shared popup geometry (the actual UI size —
     *  adapted plugin panels and unadapted background textures are far larger
     *  than the 48px box), not the box: a box-sized clamp let those UIs slide
     *  off-screen with only the central region pinned inside. */
    void setCenter(int nc, int nr) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getWindow() != null) {
            int guiW = mc.getWindow().getGuiScaledWidth();
            int guiH = mc.getWindow().getGuiScaledHeight();
            // The geometry translates 1:1 with the centre (g.x = cx + kx), so
            // clamp the centre so the whole geometry stays inside the screen.
            PopupGeometry g = geometry();
            int kx = g.x - cx;
            int ky = g.y - cy;
            int minX = -kx;
            int maxX = Math.max(minX, guiW - kx - g.w);
            int minY = -ky;
            int maxY = Math.max(minY, guiH - ky - g.h);
            nc = (int) Mth.clamp(nc, minX, maxX);
            nr = (int) Mth.clamp(nr, minY, maxY);
        }
        cx = nc;
        cy = nr;
    }

    int z() {
        return z;
    }

    void setZ(int newZ) {
        this.z = newZ;
    }

    /** Whether the pin may show its hover tooltip (see {@link #tooltipArmed}). */
    boolean tooltipArmed() {
        return tooltipArmed;
    }

    /** Re-enable the pin's hover tooltip (called while the cursor is not over
     *  the pin). */
    void armTooltip() {
        tooltipArmed = true;
    }

    int cx() {
        return cx;
    }

    int cy() {
        return cy;
    }

    /** The pinned recipe's display entry. */
    public RecipeDisplayEntry entry() {
        return entry;
    }

    /** The pin's slot-select cycle index (its clone's own clock). */
    public int slotSelectIndex() {
        return ((OverlayRecipeComponentAccessor) component).getSlotSelectTime().currentIndex();
    }

    /** The pinned recipe id. */
    public RecipeDisplayId id() {
        return id;
    }

    /** The collection backing the cloned button (for click-to-place). */
    public RecipeCollection collection() {
        return collection;
    }

    /** The item under the cursor (its rotated variant), or empty — through
     *  the shared popup geometry, so the hit test matches the render exactly. */
    ItemStack itemAt(double mx, double my) {
        int selIdx = ((OverlayRecipeComponentAccessor) component).getSlotSelectTime().currentIndex();
        return geometry().itemAt(mx, my, selIdx);
    }

    // ── Persistence ──────────────────────────────────────────────────────

    /**
     * A pin's stable identity for disk persistence: the primary result item
     * (registry id + count, the restore search key) plus a fingerprint of the
     * recipe's slot items (registry ids, sorted, deduplicated) so recipes with
     * the same result can be told apart.  Recipe ids are session-bound
     * ({@link RecipeDisplayId} is an index), so pins restore by re-querying
     * the category with this identity.
     */
    public record PinSpec(String resultItem, int resultCount, List<String> inputs,
                          int mode, int x, int y, int z) {}

    /** The primary result of {@code entry}, or EMPTY. */
    public static ItemStack resultOf(RecipeDisplayEntry entry) {
        if (entry == null) return ItemStack.EMPTY;
        Minecraft mc = Minecraft.getInstance();
        try {
            List<ItemStack> results = entry.resultItems(SlotDisplayContext.fromLevel(mc.level));
            if (!results.isEmpty()) return results.get(0);
        } catch (Exception ignored) {
            // fall through
        }
        return ItemStack.EMPTY;
    }

    /** The pin's persistent spec (identity + position + z-order). */
    public PinSpec toSpec() {
        ItemStack result = resultOf(entry);
        String resultKey = result.isEmpty() ? "" : resultKey(result);
        int count = result.isEmpty() ? 0 : result.getCount();
        return new PinSpec(resultKey, count, fingerprint(entry), mode, cx, cy, z);
    }

    /** "itemid:count" search key of a stack. */
    public static String resultKey(ItemStack stack) {
        if (stack.isEmpty()) return "";
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem())
                + ":" + stack.getCount();
    }

    /** Build the search key stack from a spec (count capped at 1 — recipe
     *  queries match by item, not count). */
    public static ItemStack itemFromKey(String key) {
        if (key == null || key.isEmpty()) return ItemStack.EMPTY;
        int colon = key.lastIndexOf(':');
        if (colon <= 0) return ItemStack.EMPTY;
        String id = key.substring(0, colon);
        var opt = net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(
                net.minecraft.resources.Identifier.tryParse(id));
        return opt.map(item -> new ItemStack(item, 1)).orElse(ItemStack.EMPTY);
    }

    /** Stable slot fingerprint of a recipe (registry ids of every tracked
     *  slot's variants, sorted, deduplicated) — the restore match key. */
    public static List<String> fingerprint(RecipeDisplayEntry entry) {
        TreeSet<String> ids = new TreeSet<>();
        if (entry == null) return List.of();
        if (RecipeViewerEngine.isSynthetic(entry.id())) {
            RecipeViewerEngine.RecipeLayout layout = RecipeViewerEngine.getLayout(entry.id());
            if (layout != null) {
                for (RecipeViewerEngine.RecipeSlotLayout slot : layout.slots()) {
                    addAll(ids, slot.stacks());
                }
            }
        } else if (entry.display() instanceof ShapelessCraftingRecipeDisplay sl) {
            for (SlotDisplay d : sl.ingredients()) addAll(ids, RecipeViewerIndex.resolveSlotDisplay(d));
        } else if (entry.display() instanceof ShapedCraftingRecipeDisplay sd) {
            for (SlotDisplay d : sd.ingredients()) addAll(ids, RecipeViewerIndex.resolveSlotDisplay(d));
        } else if (entry.display() instanceof FurnaceRecipeDisplay furnace) {
            addAll(ids, RecipeViewerIndex.resolveSlotDisplay(furnace.ingredient()));
            addAll(ids, RecipeViewerIndex.resolveSlotDisplay(furnace.result()));
        } else if (entry.display() instanceof StonecutterRecipeDisplay stonecutter) {
            addAll(ids, RecipeViewerIndex.resolveSlotDisplay(stonecutter.input()));
            addAll(ids, RecipeViewerIndex.resolveSlotDisplay(stonecutter.result()));
        } else if (entry.display() instanceof SmithingRecipeDisplay smithing) {
            addAll(ids, RecipeViewerIndex.resolveSlotDisplay(smithing.base()));
            addAll(ids, RecipeViewerIndex.resolveSlotDisplay(smithing.result()));
        }
        return new ArrayList<>(ids);
    }

    private static void addAll(TreeSet<String> ids, List<ItemStack> stacks) {
        for (ItemStack stack : stacks) {
            if (!stack.isEmpty()) {
                ids.add(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
            }
        }
    }

    // ── Rendering ─────────────────────────────────────────────────────────

    /** Recompute the pin's recipe state (craftable / partial) against the
     *  current search space (inventory + offhand + craft grid), mirroring the
     *  recipe book's updateCollections: re-select the collection, force the
     *  partial marks to re-evaluate (the tagger caches per generation) and
     *  refresh the partial snapshot.  The pin's render reads the state
     *  dynamically from this collection (the cloned button's own isCraftable
     *  field is final and stays frozen), so the refresh takes effect
     *  immediately.  Recipes without a meaningful state (fuel, etc.) are
     *  unaffected. */
    void refreshRecipeState(Minecraft mc) {
        if (mc.player == null || mc.level == null) return;
        // Search space = the player's REAL inventory (+ offhand): screen
        // container slots and the carried stack may be virtual (creative tabs,
        // grids) and must not count as materials.
        StackedItemContents stacked = new StackedItemContents();
        mc.player.getInventory().fillStackedContents(stacked);
        ItemStack offhand = PartialCraftingUtil.offhandStack();
        if (!offhand.isEmpty()) {
            stacked.accountSimpleStack(offhand);
        }
        // selectRecipes rebuilds the craftable set (dropping stale partial
        // injections from a previous prepareForViewer pass).
        collection.selectRecipes(stacked, display -> true);
        // The tagger only marks a collection once per generation; force a
        // re-evaluation so the partial state follows the new search space.
        PartialCraftingUtil.forceReevaluate(collection);
        ItemStack carried = mc.player.containerMenu != null
                ? mc.player.containerMenu.getCarried() : ItemStack.EMPTY;
        PartialCraftingUtil.prepareForViewer(
                collection, PartialCraftingUtil.searchSpaceSlots(), carried);
        RecipeViewerIndex.snapshotPartials(collection);
    }

    void render(GuiGraphicsExtractor gui, int mouseX, int mouseY, float delta) {
        // The cloned button renders its full recipe layout at the frozen zoom.
        button.setX(btnX());
        button.setY(btnY());
        PinButtonRenderOverride.push(PopupGeometry.VANILLA_SCALE, mode);
        try {
            button.extractRenderState(gui, mouseX, mouseY, delta);
        } finally {
            PinButtonRenderOverride.pop();
        }

        // Pin icon at the rendered UI's top-left corner: the adapted synthetic
        // content can extend far beyond the box, so anchor to the shared
        // geometry's bounds (the panel) rather than the box (which would land
        // mid-UI).
        PopupGeometry g = geometry();
        ClientCompat.blitSprite(gui, BRBTextures.RECIPE_BOOK_OVERLAY_PIN_SPRITE,
                g.x, g.y, MIN_EDGE, MIN_EDGE);

        if (PinOverlayManager.isDragging(this)) {
            gui.requestCursor(com.mojang.blaze3d.platform.cursor.CursorTypes.RESIZE_ALL);
        }
    }
}
