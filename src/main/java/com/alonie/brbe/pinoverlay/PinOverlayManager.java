package com.alonie.brbe.pinoverlay;

import com.alonie.brbe.mixins.accessors.GuiGraphicsExtractorAccessor;
import com.alonie.brbe.recipeviewer.RecipeViewerCategories;
import com.alonie.brbe.recipeviewer.RecipeViewerCategory;
import com.alonie.brbe.util.ClientCompat;
import com.alonie.brbe.util.ModNameUtil;
import com.alonie.brbe.util.PartialCraftingUtil;
import com.alonie.brbe.util.RecipeViewerOverlay;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Registry of every open pin overlay, plus the merged z-order rendering and the
 * input routing.  Pins are orthogonal to the query viewer ({@code isViewerActive}
 * stays untouched): they stack by opening order against the viewer via a shared
 * monotonic z sequence, and Esc closes only the top-most layer.
 *
 * <p>Creating: pressing the configured preview key (default A) over a
 * query-viewer recipe button pins it.  Pressing it again over an open pin
 * closes it; Esc closes only the top-most layer.  Dragging moves a pin
 * (RESIZE_ALL cursor); a press that never moved is a click and inherits the
 * recipe button's click (placing the recipe when the station matches).</p>
 *
 * <p>Pins are <b>passive windows</b>: while the query viewer is closed they do
 * not block the container behind them (clicks fall through, container tooltips
 * show normally); only direct pin interaction (drag, click, Shift-hover for the
 * slot item's tooltip, the preview key, Esc) is intercepted.  Pins persist to
 * disk ({@code zzzbrbe.pinoverlays.json}: recipe identity + mode + position +
 * z-order) and restore on the next container screen.</p>
 */
public final class PinOverlayManager {

    /** All open pins, in creation (z) order. */
    private static final List<PinOverlay> pins = new ArrayList<>();
    private static int zCounter;

    /** Press state: the pin pressed down.  Pressing captures it for dragging;
     *  a release without movement is a click (place recipe); the configured
     *  preview key or Esc closes it. */
    private static PinOverlay pressPin;
    private static double grabDX;
    private static double grabDY;
    private static double pressX;
    private static double pressY;
    private static boolean dragMoved;

    // ── Persistence ───────────────────────────────────────────────────────
    private static final Gson GSON = new Gson();
    private static final Type SPECS_TYPE = new TypeToken<ArrayList<PinOverlay.PinSpec>>() {}.getType();
    private static Path pinFile;
    /** Specs that could not be resolved yet (their recipe is not known this
     *  session); re-attempted while a container screen is open. */
    private static final List<PinOverlay.PinSpec> pendingSpecs = new ArrayList<>();
    private static long lastResolveAttempt;
    private static boolean initialized;

    private PinOverlayManager() {}

    /** Load the persisted pin overlays (called lazily on first render; the
     *  game directory is only available once Minecraft exists). */
    public static void init() {
        if (initialized) return;
        initialized = true;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameDirectory == null) return;
        pinFile = mc.gameDirectory.toPath().resolve("zzzbrbe.pinoverlays.json");
        load();
    }

    private static void load() {
        if (pinFile == null || !Files.exists(pinFile)) return;
        try {
            String json = Files.readString(pinFile, StandardCharsets.UTF_8);
            List<PinOverlay.PinSpec> specs = GSON.fromJson(json, SPECS_TYPE);
            if (specs != null) {
                pendingSpecs.clear();
                for (PinOverlay.PinSpec spec : specs) {
                    if (spec != null) pendingSpecs.add(spec);
                }
            }
        } catch (Exception e) {
            System.err.println("[BRBE] Failed to read pin overlays: " + e.getMessage());
        }
    }

    /** Persist every pin (and any unresolved spec) asynchronously. */
    private static void save() {
        if (pinFile == null) return;
        List<PinOverlay.PinSpec> snapshot = new ArrayList<>(pendingSpecs.size() + pins.size());
        for (PinOverlay pin : pins) {
            snapshot.add(pin.toSpec());
        }
        snapshot.addAll(pendingSpecs);
        CompletableFuture.runAsync(() -> {
            try {
                String json = GSON.toJson(snapshot);
                Files.createDirectories(pinFile.getParent());
                Files.writeString(pinFile, json, StandardCharsets.UTF_8);
            } catch (IOException e) {
                System.err.println("[BRBE] Failed to write pin overlays: " + e.getMessage());
            }
        });
    }

    /** Next opening-order value, shared with the query viewer. */
    public static int nextZ() {
        return ++zCounter;
    }

    /** Whether any pin overlay is open. */
    public static boolean hasPins() {
        return !pins.isEmpty();
    }

    // ── Rendering ─────────────────────────────────────────────────────────

    /** Draw every open pin interleaved with the query viewer by z-order: pins
     *  opened before the viewer, then the viewer, then pins opened after.  With
     *  no pins this is exactly the plain viewer render.  The top-most pin under
     *  the cursor gets its slot-item tooltip last (only while Shift is held —
     *  pins are otherwise passive). */
    public static void render(GuiGraphicsExtractor gui, int mouseX, int mouseY, float delta) {
        init();
        resolvePending();
        // Like the recipe book, pins re-evaluate their craftable / partial
        // state when the player's inventory changes (Inventory.getTimesChanged).
        refreshRecipeStates();
        // The open query viewer hides the container screen's tooltips: the
        // deferred tooltip slot may already hold one registered by the screen
        // during its own render pass, so clear it before we register ours.
        // Pins alone do NOT clear it — they are passive and the container's
        // tooltips show normally.
        if (RecipeViewerOverlay.isActive()) {
            ((GuiGraphicsExtractorAccessor) gui).brbe$setDeferredTooltip(null);
        }
        if (pins.isEmpty()) {
            RecipeViewerOverlay.render(gui, mouseX, mouseY, delta);
            return;
        }
        int qz = RecipeViewerOverlay.isActive() ? RecipeViewerOverlay.viewerZ() : -1;
        for (PinOverlay pin : pins) {
            if (pin.z() < qz) pin.render(gui, mouseX, mouseY, delta);
        }
        if (qz >= 0) {
            // A pin opened after the viewer that covers the cursor intercepts
            // the viewer's hover: render the viewer with a far-away cursor so
            // its buttons do not pop up or show tooltips under the pin.
            int vmx = mouseX;
            int vmy = mouseY;
            if (topInteractivePin(mouseX, mouseY) != null) {
                vmx = -1;
                vmy = -1;
            }
            RecipeViewerOverlay.render(gui, vmx, vmy, delta);
        }
        for (PinOverlay pin : pins) {
            if (pin.z() > qz) pin.render(gui, mouseX, mouseY, delta);
        }
        PinOverlay top = topInteractivePin(mouseX, mouseY);
        // Re-arm every pin the cursor is not over: a pin created by the pin
        // hotkey starts disarmed so its tooltip does not flash at the cursor
        // the moment it appears (the cursor sits on the freshly created pin).
        if (top != null) {
            for (PinOverlay pin : pins) {
                if (pin != top) pin.armTooltip();
            }
            // No Shift: the pin shows its pinned recipe's (query object's)
            // detailed result tooltip.  With Shift: the item under the cursor
            // inside the pin.
            if (top.tooltipArmed()) {
                if (ClientCompat.isShiftDown()) {
                    renderPinItemTooltip(gui, top, mouseX, mouseY);
                } else {
                    renderPinRecipeTooltip(gui, top, mouseX, mouseY);
                }
            }
        } else {
            for (PinOverlay pin : pins) {
                pin.armTooltip();
            }
        }
    }

    /** The top-most open pin under the cursor, or null.  A pin overlapped by the
     *  query viewer (which opened after it) is covered: the viewer is the
     *  top-most layer there and intercepts the point, so the pin yields. */
    public static PinOverlay topInteractivePin(double mx, double my) {
        PinOverlay best = null;
        for (PinOverlay pin : pins) {
            if (pin.contains(mx, my) && (best == null || pin.z() > best.z())) {
                best = pin;
            }
        }
        if (best == null) return null;
        int qz = RecipeViewerOverlay.viewerZ();
        if (qz > best.z() && RecipeViewerOverlay.contains(mx, my)) {
            return null;
        }
        return best;
    }

    /** Whether a pin is under the cursor (shadows the query viewer's tooltip). */
    public static boolean covers(double mx, double my) {
        return topInteractivePin(mx, my) != null;
    }

    /** Whether {@code pin} is the one currently being dragged (for the cursor). */
    public static boolean isDragging(PinOverlay pin) {
        return dragMoved && pressPin == pin;
    }

    /** Screen regions of all open pins, for JEI to keep out of the way.  The
     *  region matches each pin's full rendered UI (the shared popup geometry's
     *  bounds, which can exceed the box for synthetic recipes). */
    public static List<Rect2i> exclusionAreas() {
        if (pins.isEmpty()) return List.of();
        List<Rect2i> areas = new ArrayList<>();
        for (PinOverlay pin : pins) {
            areas.add(pin.interactiveBounds());
        }
        return areas;
    }

    // ── Input ─────────────────────────────────────────────────────────────

    /** Pressing an open pin captures it for dragging and brings it to the top
     *  (system-window behaviour); any other click falls through to the
     *  container.  Returns true when consumed. */
    public static boolean handleMouseClicked(MouseButtonEvent event, boolean doubleClick,
                                             AbstractContainerScreen<?> screen) {
        double mx = event.x();
        double my = event.y();
        PinOverlay top = topInteractivePin(mx, my);
        if (top != null) {
            bringToFront(top);
            pressPin = top;
            grabDX = mx - top.cx();
            grabDY = my - top.cy();
            pressX = mx;
            pressY = my;
            dragMoved = false;
            save();
            return true;
        }
        return false;
    }

    /** The configured preview key toggles the pin overlay: pressing it over an
     *  open pin closes it, otherwise it pins the hovered query-viewer recipe.
     *  Returns true when consumed. */
    public static boolean handleKeyPressed(KeyEvent event, AbstractContainerScreen<?> screen) {
        if (!ClientCompat.matchesPinKey(event.key(), event.scancode(), event.modifiers())) return false;
        Minecraft mc = Minecraft.getInstance();
        double mx = mc.mouseHandler.getScaledXPos(mc.getWindow());
        double my = mc.mouseHandler.getScaledYPos(mc.getWindow());
        PinOverlay top = topInteractivePin(mx, my);
        if (top != null) {
            pins.remove(top);
            if (pressPin == top) {
                pressPin = null;
                dragMoved = false;
            }
            save();
            return true;
        }
        // Only a query-viewer recipe button can be pinned: capture keys off the
        // hovered overlay button's recipe id, so pressing the key over a plain
        // slot keeps vanilla behaviour.  The pin opens centred on the button.
        ItemStack target = captureTarget(screen);
        if (!target.isEmpty() && RecipeViewerOverlay.capturedOverlayRecipe() != null) {
            int[] centre = RecipeViewerOverlay.capturedOverlayButtonCentre();
            int px = centre != null ? centre[0] : (int) mx;
            int py = centre != null ? centre[1] : (int) my;
            createPin(screen, px, py);
            return true;
        }
        return false;
    }

    /** Drag the pressed pin to the cursor (absolute position + grab offset).
     *  A press only becomes a drag once the cursor moves more than a small
     *  threshold, so a still press can be released as a click. */
    public static boolean handleMouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (pressPin == null) return false;
        bringToFront(pressPin);
        if (!dragMoved) {
            double distX = event.x() - pressX;
            double distY = event.y() - pressY;
            if (distX * distX + distY * distY > 9.0) {
                dragMoved = true;
            }
        }
        if (dragMoved) {
            pressPin.setCenter((int) Math.round(event.x() - grabDX),
                    (int) Math.round(event.y() - grabDY));
        }
        return true;
    }

    /** Release ends a press: a press that never moved is a click that inherits
     *  the recipe button's click (placing the recipe when the station matches);
     *  otherwise the drag ends (and the new position is persisted).  Returns
     *  true when a pin press was in flight. */
    public static boolean handleMouseReleased(MouseButtonEvent event,
                                              AbstractContainerScreen<?> screen) {
        if (pressPin == null) return false;
        boolean click = !dragMoved;
        PinOverlay pin = pressPin;
        pressPin = null;
        dragMoved = false;
        if (click && screen != null) {
            RecipeViewerOverlay.placeRecipe(event, screen, pin.id(), pin.collection());
        } else {
            save();
        }
        return true;
    }

    /** Esc closes only the top-most layer: a pin if the top-most pin opened
     *  after the query viewer, else the query viewer itself.  Returns false when
     *  there is nothing to close. */
    public static boolean handleEscape() {
        PinOverlay top = topmostPin();
        int pinZ = top == null ? -1 : top.z();
        int qz = RecipeViewerOverlay.isActive() ? RecipeViewerOverlay.viewerZ() : -1;
        if (pinZ < 0 && qz < 0) return false;
        if (pinZ >= qz) {
            pins.remove(top);
            if (pressPin == top) {
                pressPin = null;
                dragMoved = false;
            }
            save();
        } else {
            RecipeViewerOverlay.close();
        }
        return true;
    }

    /** Consume scroll while over a pin so it does not flip the query viewer's
     *  page.  Returns true when a pin is under the cursor. */
    public static boolean handleMouseScrolled(double mx, double my, double vertical) {
        return topInteractivePin(mx, my) != null;
    }

    /** Capture the target for R/U and pinning: a pin under the cursor first
     *  (its item is a normal item object), else the query viewer's capture. */
    public static ItemStack captureTarget(AbstractContainerScreen<?> screen) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getWindow() != null && mc.mouseHandler != null) {
            double mx = mc.mouseHandler.getScaledXPos(mc.getWindow());
            double my = mc.mouseHandler.getScaledYPos(mc.getWindow());
            PinOverlay top = topInteractivePin(mx, my);
            if (top != null) return top.itemAt(mx, my);
        }
        return RecipeViewerOverlay.captureTarget(screen);
    }

    /** Clear every pin (host screen removed). */
    public static void clearAll() {
        pins.clear();
        pressPin = null;
        dragMoved = false;
        pendingSpecs.clear();
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    /** Last observed search-space hash (menu slots + carried + offhand): the
     *  pin recipe-state refresh trigger.  A per-frame slot hash mirrors the
     *  recipe book's inventory-change detection but also covers container /
     *  craft-grid changes, which never touch {@code Inventory.getTimesChanged()}. */
    private static long lastSearchSpaceHash = -1;

    /** Refresh every pin's recipe state (craftable / partial) when the search
     *  space changed since the last render — like the recipe book's
     *  updateCollections, so pins keep showing whether the recipe is craftable
     *  / partially craftable with the current inventory (+ offhand).  The hash
     *  is over the player's REAL inventory: screen-container slots and the
     *  carried stack may be virtual (creative tabs, grids) and must not count
     *  as materials. */
    private static void refreshRecipeStates() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        long hash = PartialCraftingUtil.slotHash(
                PartialCraftingUtil.realInventorySlots(), ItemStack.EMPTY);
        if (pins.isEmpty()) {
            lastSearchSpaceHash = hash;
            return;
        }
        if (hash == lastSearchSpaceHash) return;
        lastSearchSpaceHash = hash;
        for (PinOverlay pin : pins) {
            try {
                pin.refreshRecipeState(mc);
            } catch (Exception e) {
                // a broken state refresh must not break the pin rendering
            }
        }
    }

    private static PinOverlay topmostPin() {
        PinOverlay best = null;
        for (PinOverlay pin : pins) {
            if (best == null || pin.z() > best.z()) best = pin;
        }
        return best;
    }

    /** Bring {@code pin} to the top of the z-order (system-window behaviour). */
    private static void bringToFront(PinOverlay pin) {
        pin.setZ(++zCounter);
        pins.remove(pin);
        pins.add(pin);
    }

    private static void createPin(AbstractContainerScreen<?> screen, int mx, int my) {
        // The pin matches the hover preview's fixed size (PopupGeometry).
        int z = nextZ();
        RecipeDisplayId recipeId = RecipeViewerOverlay.capturedOverlayRecipe();
        if (recipeId == null) return;
        RecipeDisplayEntry entry = RecipeViewerOverlay.entryFor(recipeId);
        if (entry == null) return;
        // Freeze the query viewer's layout mode so the container keeps mirroring
        // the furnace / stonecutter / smithing / crafting button.
        int mode = RecipeViewerOverlay.isFurnaceMode() ? PinOverlay.MODE_FURNACE
                : RecipeViewerOverlay.isStonecuttingMode() ? PinOverlay.MODE_STONECUTTING
                : RecipeViewerOverlay.isSmithingMode() ? PinOverlay.MODE_SMITHING
                : PinOverlay.MODE_CRAFTING;
        PinOverlay pin = PinOverlay.create(entry, mode, z, mx, my);
        if (pin != null) {
            pins.add(pin);
            save();
        }
    }

    /** Try to materialize pending specs (throttled to 1 Hz while a container
     *  screen is open); a spec resolves once its recipe is known again. */
    private static void resolvePending() {
        if (pendingSpecs.isEmpty()) return;
        long now = net.minecraft.util.Util.getMillis();
        if (now - lastResolveAttempt < 1000) return;
        lastResolveAttempt = now;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        Iterator<PinOverlay.PinSpec> it = pendingSpecs.iterator();
        while (it.hasNext()) {
            PinOverlay.PinSpec spec = it.next();
            PinOverlay pin = materialize(spec);
            if (pin != null) {
                it.remove();
                pins.add(pin);
                if (pin.z() >= zCounter) {
                    zCounter = pin.z();
                }
            }
        }
    }

    /** Find the recipe matching {@code spec} (result item + slot fingerprint)
     *  across every viewer category and rebuild the pin at its saved position. */
    private static PinOverlay materialize(PinOverlay.PinSpec spec) {
        ItemStack target = PinOverlay.itemFromKey(spec.resultItem());
        if (target.isEmpty()) return null;
        for (RecipeViewerCategory cat : RecipeViewerCategories.all()) {
            if (cat.isFuelCategory()) continue;
            List<RecipeDisplayEntry> hits = cat.query(target, false);
            for (RecipeDisplayEntry hit : hits) {
                if (PinOverlay.fingerprint(hit).equals(spec.inputs())) {
                    return PinOverlay.create(hit, modeFor(cat), spec.z(), spec.x(), spec.y());
                }
            }
        }
        return null;
    }

    private static int modeFor(RecipeViewerCategory cat) {
        return switch (cat.id()) {
            case "furnace" -> PinOverlay.MODE_FURNACE;
            case "stonecutting" -> PinOverlay.MODE_STONECUTTING;
            case "smithing" -> PinOverlay.MODE_SMITHING;
            default -> PinOverlay.MODE_CRAFTING;
        };
    }

    /** The top-most pin's recipe (query object) tooltip, shown while Shift is
     *  NOT held: the pinned recipe's detailed result tooltip, following the
     *  pin's slot-select cycle.  With the query viewer open (which cancels the
     *  screen's end-of-frame tooltip pass) it is drawn directly; with the
     *  viewer closed it replaces the deferred tooltip slot, so the container
     *  tooltip under the pin does not overdraw it. */
    private static void renderPinRecipeTooltip(GuiGraphicsExtractor gui, PinOverlay pin,
                                               int mx, int my) {
        RecipeDisplayEntry entry = pin.entry();
        if (entry == null) return;
        if (RecipeViewerOverlay.isActive()) {
            RecipeViewerOverlay.renderDetailedRecipeTooltip(gui, entry, pin.id(),
                    mx, my, pin.slotSelectIndex());
        } else {
            ((GuiGraphicsExtractorAccessor) gui).brbe$setDeferredTooltip(() ->
                    RecipeViewerOverlay.renderDetailedRecipeTooltip(gui, entry, pin.id(),
                            mx, my, pin.slotSelectIndex()));
        }
    }

    /** The top-most pin's slot-item tooltip, shown only while Shift is held
     *  (the embedded item under the cursor), at the vanilla default position.
     *  With the query viewer open (which cancels the screen's end-of-frame
     *  tooltip pass) it is drawn directly; with the viewer closed it replaces
     *  the deferred tooltip slot. */
    private static void renderPinItemTooltip(GuiGraphicsExtractor gui, PinOverlay pin,
                                             int mx, int my) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.font == null) return;
        // Every item in the container is a normal item object: a full tooltip
        // (plus the source-mod line) for the item under the cursor.
        ItemStack hovered = pin.itemAt(mx, my);
        if (hovered.isEmpty()) return;
        List<Component> lines = new ArrayList<>(Screen.getTooltipFromItem(mc, hovered));
        Component modName = ModNameUtil.getFormattedModName(hovered);
        if (modName != null && !modName.getString().isEmpty()) {
            lines.add(Component.empty());
            lines.add(modName);
        }
        List<net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent> components =
                new ArrayList<>(lines.size());
        for (Component line : lines) {
            components.add(net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent
                    .create(line.getVisualOrderText()));
        }
        Identifier style = hovered.get(net.minecraft.core.component.DataComponents.TOOLTIP_STYLE);
        net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner positioner =
                net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner.INSTANCE;
        if (RecipeViewerOverlay.isActive()) {
            // The viewer cancels the end-of-frame tooltip pass, so draw now.
            gui.tooltip(mc.font, components, mx, my, positioner, style);
        } else {
            // Pins alone: replace the container's deferred tooltip with ours
            // (rendered at the end of the frame).
            ((GuiGraphicsExtractorAccessor) gui).brbe$setDeferredTooltip(() ->
                    gui.tooltip(mc.font, components, mx, my, positioner, style));
        }
    }
}
