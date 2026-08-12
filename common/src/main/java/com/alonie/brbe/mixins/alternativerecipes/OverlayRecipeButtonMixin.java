package com.alonie.brbe.mixins.alternativerecipes;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.mixins.accessors.ClientRecipeBookAccessor;
import com.alonie.brbe.mixins.accessors.OverlayRecipeButtonAccessor;
import com.alonie.brbe.mixins.accessors.OverlayRecipeButtonPosAccessor;
import com.alonie.brbe.mixins.accessors.OverlayRecipeComponentAccessor;
import com.alonie.brbe.util.BRBTextures;
import com.alonie.brbe.util.ClientCompat;
import com.alonie.brbe.util.PartialCraftingUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.recipebook.OverlayRecipeComponent;
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

    @Inject(at = @At("HEAD"), method = "renderWidget", cancellable = true)
    public void renderWidget(GuiGraphics gui, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        OverlayRecipeComponent outer = ((OverlayRecipeButtonAccessor) this).brbe$getOuterComponent();
        boolean furnaceBook = ((OverlayRecipeComponentAccessor) outer).isFurnaceMenu();
        boolean partial = PartialCraftingUtil.isPartiallyCraftable(outer.getRecipeCollection(), this.recipe);
        if (furnaceBook) {
            // Furnace books (furnace / blast furnace / smoker) have no partial state.
            partial = false;
        }

        // Partial recipes keep the craftable sprite so they get the light-coloured
        // border (red overlay is drawn below).  Alternative-recipe groups stay
        // vanilla-style — the compat pack's red check is not applied here.
        // Furnace books use the plain overlay sprites.
        Identifier Identifier = (furnaceBook
                ? BRBTextures.RECIPE_BOOK_PLAIN_OVERLAY_SPRITE
                : BRBTextures.RECIPE_BOOK_CRAFTING_OVERLAY_SPRITE)
                .get(this.isCraftable || partial, isHoveredOrFocused());

        ClientCompat.blitSprite(gui, Identifier, getX(), getY(), this.width, this.height);

        // Red overlay for partially-craftable recipes
        if (partial) {
            gui.fill(getX() + 1, getY() + 1, getX() + width - 1, getY() + height - 1, 0x60FF3333);
        }

        gui.pose().pushMatrix();
        if (BetterRecipeBook.config.alternativeRecipes.onHover && !this.isHoveredOrFocused()) { // if show alternatives recipe is enabled and recipe is not hovered, show the result item
            ItemStack recipeOutput = brbe$getRecipeOutput();
            gui.renderItem(recipeOutput, getX() + 4, getY() + 4);
        } else { // otherwise display the crafting recipe
            gui.pose().translate(this.getX() + 2, this.getY() + 2);
            for (Object rawPos : this.slots) {
                OverlayRecipeButtonPosAccessor pos = (OverlayRecipeButtonPosAccessor) rawPos;
                gui.pose().pushMatrix();
                gui.pose().translate(pos.brbe$getX(), pos.brbe$getY());
                gui.pose().scale(0.375f, 0.375f);
                gui.pose().translate(-8.0F, -8.0F);
                gui.renderItem(pos.brbe$selectIngredient(0), 0, 0);
                gui.pose().popMatrix();
            }
        }
        gui.pose().popMatrix();

        ci.cancel();
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
