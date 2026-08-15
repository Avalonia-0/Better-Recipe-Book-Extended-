package com.alonie.brbe.mixins.alternativerecipes;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.cache.RecipeViewerIndex;
import com.alonie.brbe.compat.SyntheticRecipeRenderers;
import com.alonie.brbe.mixins.accessors.ClientRecipeBookAccessor;
import com.alonie.brbe.recipeviewer.engine.RecipeViewerEngine;
import com.alonie.brbe.render.ButtonBackdrop;
import com.alonie.brbe.mixins.accessors.OverlayRecipeButtonAccessor;
import com.alonie.brbe.mixins.accessors.OverlayRecipeButtonPosAccessor;
import com.alonie.brbe.mixins.accessors.OverlayRecipeComponentAccessor;
import com.alonie.brbe.util.BRBTextures;
import com.alonie.brbe.util.ClientCompat;
import com.alonie.brbe.util.PartialCraftingUtil;
import com.alonie.brbe.util.RecipeViewerOverlay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.screens.recipebook.OverlayRecipeComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Map;


@Mixin(targets = "net.minecraft.client.gui.screens.recipebook.OverlayRecipeComponent$OverlayRecipeButton")
public abstract class OverlayRecipeButtonMixin extends AbstractWidget {

    @Final
    @Shadow
    private boolean isCraftable;
    @Final
    @Shadow
    private RecipeDisplayId recipe;

    @Shadow
    @Final
    private List<Object> slots;

    public OverlayRecipeButtonMixin(int x, int y, int width, int height, Component message) {
        super(x, y, width, height, message);
    }

    @Inject(at = @At("HEAD"), method = "extractWidgetRenderState", cancellable = true)
    public void extractWidgetRenderState(GuiGraphicsExtractor gui, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        // Partial recipes keep the craftable sprite so they get the light-coloured
        // border (red overlay is drawn below).  Alternative-recipe groups stay
        // vanilla-style — the compat pack's red check is not applied here.
        // Viewer collections use the strong snapshot (immune to the tagger's
        // generation advances); recipe-book collections keep the generation-aware query.
        OverlayRecipeComponent outer = ((OverlayRecipeButtonAccessor) this).brbe$getOuterComponent();
        RecipeCollection collection = outer.getRecipeCollection();
        boolean furnaceBook = ((OverlayRecipeComponentAccessor) outer).isFurnaceMenu();
        boolean partial;
        if (RecipeViewerOverlay.isFurnaceMode() || furnaceBook) {
            // Furnace books (furnace / blast furnace / smoker) and the viewer's
            // furnace category have no partial-crafting state.
            partial = false;
        } else if (RecipeViewerIndex.isViewerCollection(collection)) {
            partial = RecipeViewerIndex.isViewerPartial(collection, this.recipe)
                    || PartialCraftingUtil.isPartiallyCraftableEvenIfStale(collection, this.recipe);
        } else {
            partial = PartialCraftingUtil.isPartiallyCraftable(collection, this.recipe);
        }

        // Furnace buttons use the plain overlay sprites (hover = plain_overlay_highlighted)
        // rather than the crafting-table highlighted overlay.
        WidgetSprites sprites = (RecipeViewerOverlay.isFurnaceMode() || furnaceBook)
                ? BRBTextures.RECIPE_BOOK_PLAIN_OVERLAY_SPRITE
                : BRBTextures.RECIPE_BOOK_CRAFTING_OVERLAY_SPRITE;
        Identifier sprite = sprites.get(this.isCraftable || partial, isHoveredOrFocused());

        // Hovering scales the button (and its recipe items) up about its centre
        // so the highlighted sprite pops above its neighbours — 4x while Shift
        // is held, otherwise 2x.
        boolean hover = isHoveredOrFocused();
        // The synthetic (mod) recipe's native layout, fetched once and shared by
        // the backdrop resolver and the item renderer.
        RecipeViewerEngine.RecipeLayout syntheticLayout = RecipeViewerEngine.isSynthetic(this.recipe)
                ? RecipeViewerEngine.getLayout(this.recipe) : null;
        // Exactly one backdrop per frame: the uniform vanilla sprite, or — only
        // for a hovered synthetic recipe with its own background — the JEI texture.
        ButtonBackdrop backdrop = resolveBackdrop(sprite, hover, syntheticLayout);

        // A hovered synthetic recipe with the companion mod's renderer gets its
        // full JEI UI (background + animation + slot items) drawn here at the
        // vanilla alternative-recipe-group's own 1:1 sprite scale — the container
        // 9-slices and the button is NOT given the hover magnify below — so
        // return early before the hover-scale push.
        if (hover && RecipeViewerEngine.isSynthetic(this.recipe)
                && SyntheticRecipeRenderers.get().render(this.recipe, gui, getX(), getY(), width, height)) {
            ci.cancel();
            return;
        }

        if (hover) {
            float scale = ClientCompat.isShiftDown() ? 4f : 2f;
            gui.pose().pushMatrix();
            gui.pose().translate(getX() + width / 2f, getY() + height / 2f);
            gui.pose().scale(scale, scale);
            gui.pose().translate(-(getX() + width / 2f), -(getY() + height / 2f));
        }

        // The single backdrop for this frame (sprite xor JEI background).
        backdrop.render(gui, getX(), getY(), width, height);

        // Red overlay for partially-craftable recipes.  Only suppressed when the
        // backdrop is an opaque JEI background; every non-hover button keeps the
        // uniform partial marking.
        if (partial && !backdrop.suppressesPartialOverlay()) {
            gui.fill(getX() + 1, getY() + 1, getX() + width - 1, getY() + height - 1, 0x60FF3333);
        }

        gui.pose().pushMatrix();
        if (RecipeViewerEngine.isSynthetic(this.recipe)) {
            renderSynthetic(gui, hover, syntheticLayout);
        } else if (RecipeViewerOverlay.isStonecuttingMode()) {
            // Stonecutter: input top-left, result bottom-right; with "show
            // alternatives only on hover" the result alone is shown when not
            // hovered.
            boolean onHover = BetterRecipeBook.config.alternativeRecipes.onHover;
            var display = RecipeViewerIndex.asStonecutter(this.recipeEntry());
            ItemStack input = display == null ? ItemStack.EMPTY
                    : first(RecipeViewerIndex.resolveSlotDisplay(display.input()));
            ItemStack result = display == null ? ItemStack.EMPTY
                    : first(RecipeViewerIndex.resolveSlotDisplay(display.result()));
            if (onHover && !hover) {
                gui.item(result, getX() + 4, getY() + 4);
            } else {
                gui.pose().pushMatrix();
                gui.pose().translate(getX() + 2, getY() + 2);
                gui.pose().scale(0.6f, 0.6f);
                gui.item(input, 0, 0);
                gui.pose().popMatrix();
                gui.pose().pushMatrix();
                gui.pose().translate(getX() + 12, getY() + 7);
                gui.pose().scale(0.6f, 0.6f);
                gui.item(result, 0, 0);
                gui.pose().popMatrix();
            }
        } else if (RecipeViewerOverlay.isSmithingMode()) {
            // Smithing: base top-left, result bottom-right; with "show
            // alternatives only on hover" the result alone is shown when not
            // hovered.  Template/addition are omitted (the button is too small).
            boolean onHover = BetterRecipeBook.config.alternativeRecipes.onHover;
            var display = RecipeViewerIndex.asSmithing(this.recipeEntry());
            ItemStack base = display == null ? ItemStack.EMPTY
                    : first(RecipeViewerIndex.resolveSlotDisplay(display.base()));
            ItemStack result = display == null ? ItemStack.EMPTY
                    : first(RecipeViewerIndex.resolveSlotDisplay(display.result()));
            if (onHover && !hover) {
                gui.item(result, getX() + 4, getY() + 4);
            } else {
                gui.pose().pushMatrix();
                gui.pose().translate(getX() + 2, getY() + 2);
                gui.pose().scale(0.6f, 0.6f);
                gui.item(base, 0, 0);
                gui.pose().popMatrix();
                gui.pose().pushMatrix();
                gui.pose().translate(getX() + 12, getY() + 7);
                gui.pose().scale(0.6f, 0.6f);
                gui.item(result, 0, 0);
                gui.pose().popMatrix();
            }
        } else if (RecipeViewerOverlay.isFurnaceMode()) {
            // Furnace buttons split into quadrants: ingredient top-left, flame
            // bottom-left, result centred in the right half.  With "show
            // alternatives only on hover" the result alone is shown when not
            // hovered (mirroring the crafting button); otherwise the ingredient.
            boolean onHover = BetterRecipeBook.config.alternativeRecipes.onHover;
            var display = RecipeViewerIndex.asFurnace(this.recipeEntry());
            ItemStack ingredient = display == null ? ItemStack.EMPTY
                    : first(RecipeViewerIndex.resolveSlotDisplay(display.ingredient()));
            ItemStack result = display == null ? ItemStack.EMPTY
                    : first(RecipeViewerIndex.resolveSlotDisplay(display.result()));
            if (onHover && !hover) {
                // Mirror the crafting button: the result item alone.
                gui.item(result, getX() + 4, getY() + 4);
            } else {
                gui.pose().pushMatrix();
                gui.pose().translate(getX() + 2, getY() + 2);
                gui.pose().scale(0.6f, 0.6f);
                gui.item(ingredient, 0, 0);
                gui.pose().popMatrix();
                ClientCompat.blitSprite(gui, BRBTextures.FURNACE_FIRE_SPRITE,
                        getX() + 4, getY() + 15, 6, 6);
                if (hover) {
                    gui.pose().pushMatrix();
                    gui.pose().translate(getX() + 12, getY() + 7);
                    gui.pose().scale(0.6f, 0.6f);
                    gui.item(result, 0, 0);
                    gui.pose().popMatrix();
                }
            }
        } else if (BetterRecipeBook.config.alternativeRecipes.onHover && !hover) { // if show alternatives recipe is enabled and recipe is not hovered, show the result item
            ItemStack recipeOutput = brbe$getRecipeOutput();
            gui.item(recipeOutput, getX() + 4, getY() + 4);
        } else { // otherwise display the crafting recipe
            // Rotate interchangeable materials like the ghost ingredients do:
            // use the overlay's slot-select index (advances every ~1.5s) instead
            // of always the first variant.  Pos.selectIngredient mods the index,
            // so this is safe for single-item ingredients too.
            int selIdx = ((OverlayRecipeComponentAccessor) outer)
                    .getSlotSelectTime().currentIndex();
            gui.pose().translate(this.getX() + 2, this.getY() + 2);
            for (Object rawPos : this.slots) {
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

        if (hover) {
            gui.pose().popMatrix();
        }

        ci.cancel();
    }

    /** The RecipeDisplayEntry backing this button (or null). */
    private RecipeDisplayEntry recipeEntry() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return null;
        RecipeDisplayEntry entry = RecipeViewerEngine.entryFor(this.recipe);
        if (entry != null) return entry;
        return ((ClientRecipeBookAccessor) mc.player.getRecipeBook())
                .brbe$getKnown().get(this.recipe);
    }

    private static ItemStack first(List<ItemStack> stacks) {
        return stacks.isEmpty() ? ItemStack.EMPTY : stacks.get(0);
    }

    /** Render a mod recipe (synthetic entry): the result item when not hovered,
     *  otherwise its native layout (as declared by its JEI category's setRecipe). */
    private void renderSynthetic(GuiGraphicsExtractor gui, boolean hover, RecipeViewerEngine.RecipeLayout layout) {
        if (BetterRecipeBook.config.alternativeRecipes.onHover && !hover) {
            gui.item(brbe$getRecipeOutput(), getX() + 4, getY() + 4);
            return;
        }
        if (layout == null || layout.slots().isEmpty()) {
            renderCraftingGrid(gui);
            return;
        }
        // Fit the slots' bounding box into the button's inner area (22x22),
        // keeping the aspect ratio so the native relative arrangement survives.
        renderSyntheticBoundingBox(gui, layout);
    }

    /** Resolve the single backdrop to paint this frame.  Only a hovered synthetic
     *  recipe with a registered background yields a {@link ButtonBackdrop.Texture};
     *  every other case — including every non-hover button — yields the uniform
     *  vanilla {@link ButtonBackdrop.Sprite}. */
    private ButtonBackdrop resolveBackdrop(Identifier sprite, boolean hover,
                                           RecipeViewerEngine.RecipeLayout syntheticLayout) {
        if (hover && syntheticLayout != null && syntheticLayout.background() != null) {
            RecipeViewerEngine.RecipeBackground bg = syntheticLayout.background();
            return new ButtonBackdrop.Texture(bg.texture(), bg.u(), bg.v(), bg.width(), bg.height(),
                    bg.textureWidth(), bg.textureHeight());
        }
        return new ButtonBackdrop.Sprite(sprite);
    }

    /** Render slots fit to their bounding box (fallback when a mod recipe has no
     *  background texture). */
    private void renderSyntheticBoundingBox(GuiGraphicsExtractor gui, RecipeViewerEngine.RecipeLayout layout) {
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        for (RecipeViewerEngine.RecipeSlotLayout slot : layout.slots()) {
            minX = Math.min(minX, slot.x());
            maxX = Math.max(maxX, slot.x());
            minY = Math.min(minY, slot.y());
            maxY = Math.max(maxY, slot.y());
        }
        int spanX = Math.max(1, maxX - minX);
        int spanY = Math.max(1, maxY - minY);
        float scale = Math.min(22f / spanX, 22f / spanY);
        float offX = (22f - spanX * scale) / 2f;
        float offY = (22f - spanY * scale) / 2f;
        for (RecipeViewerEngine.RecipeSlotLayout slot : layout.slots()) {
            if (slot.stacks().isEmpty()) continue;
            float sx = getX() + 1 + offX + (slot.x() - minX) * scale;
            float sy = getY() + 1 + offY + (slot.y() - minY) * scale;
            gui.pose().pushMatrix();
            gui.pose().translate(sx, sy);
            gui.pose().scale(0.45f, 0.45f);
            gui.item(slot.stacks().get(0), 0, 0);
            gui.pose().popMatrix();
        }
    }

    /** The vanilla crafting-grid rendering (fallback when a mod recipe has no
     *  registered layout). */
    private void renderCraftingGrid(GuiGraphicsExtractor gui) {
        OverlayRecipeComponent outer = ((OverlayRecipeButtonAccessor) this).brbe$getOuterComponent();
        int selIdx = ((OverlayRecipeComponentAccessor) outer).getSlotSelectTime().currentIndex();
        gui.pose().translate(this.getX() + 2, this.getY() + 2);
        for (Object rawPos : this.slots) {
            OverlayRecipeButtonPosAccessor pos = (OverlayRecipeButtonPosAccessor) rawPos;
            gui.pose().pushMatrix();
            gui.pose().translate(pos.brbe$getX(), pos.brbe$getY());
            gui.pose().scale(0.375f, 0.375f);
            gui.pose().translate(-8.0F, -8.0F);
            gui.item(pos.brbe$selectIngredient(selIdx), 0, 0);
            gui.pose().popMatrix();
        }
    }

    private ItemStack brbe$getRecipeOutput() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return ItemStack.EMPTY;
        }

        // The engine's registry first (covers synthetic entries from the
        // companion mod, which are not in ClientRecipeBook.known), then the
        // recipe book's known set as a fallback.
        RecipeDisplayEntry entry = RecipeViewerEngine.entryFor(this.recipe);
        if (entry == null) {
            Map<RecipeDisplayId, RecipeDisplayEntry> known =
                    ((ClientRecipeBookAccessor) minecraft.player.getRecipeBook()).brbe$getKnown();
            entry = known.get(this.recipe);
        }
        if (entry == null) {
            return ItemStack.EMPTY;
        }

        List<ItemStack> results = entry.resultItems(SlotDisplayContext.fromLevel(minecraft.level));
        return results.isEmpty() ? ItemStack.EMPTY : results.get(0);
    }
}
