package com.alonie.brbe.mixins.alternativerecipes;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.cache.RecipeViewerIndex;
import com.alonie.brbe.mixins.accessors.ClientRecipeBookAccessor;
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
        Identifier Identifier = sprites.get(this.isCraftable || partial, isHoveredOrFocused());

        // Hovering scales the button (and its recipe items) up about its centre
        // so the highlighted sprite pops above its neighbours — 4x while Shift
        // is held, otherwise 2x.
        boolean hover = isHoveredOrFocused();
        if (hover) {
            float scale = ClientCompat.isShiftDown() ? 4f : 2f;
            gui.pose().pushMatrix();
            gui.pose().translate(getX() + width / 2f, getY() + height / 2f);
            gui.pose().scale(scale, scale);
            gui.pose().translate(-(getX() + width / 2f), -(getY() + height / 2f));
        }

        ClientCompat.blitSprite(gui, Identifier, getX(), getY(), this.width, this.height);

        // Red overlay for partially-craftable recipes
        if (partial) {
            gui.fill(getX() + 1, getY() + 1, getX() + width - 1, getY() + height - 1, 0x60FF3333);
        }

        gui.pose().pushMatrix();
        if (RecipeViewerOverlay.isFurnaceMode()) {
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
        return ((ClientRecipeBookAccessor) mc.player.getRecipeBook())
                .brbe$getKnown().get(this.recipe);
    }

    private static ItemStack first(List<ItemStack> stacks) {
        return stacks.isEmpty() ? ItemStack.EMPTY : stacks.get(0);
    }

    private ItemStack brbe$getRecipeOutput() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return ItemStack.EMPTY;
        }

        Map<RecipeDisplayId, RecipeDisplayEntry> known = ((ClientRecipeBookAccessor) minecraft.player.getRecipeBook()).brbe$getKnown();
        RecipeDisplayEntry entry = known.get(this.recipe);
        if (entry == null) {
            return ItemStack.EMPTY;
        }

        List<ItemStack> results = entry.resultItems(SlotDisplayContext.fromLevel(minecraft.level));
        return results.isEmpty() ? ItemStack.EMPTY : results.get(0);
    }
}
