package com.alonie.brbe.mixins.alternativerecipes;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.mixins.accessors.OverlayRecipeButtonPosAccessor;
import com.alonie.brbe.mixins.accessors.OverlayRecipeComponentAccessor;
import com.alonie.brbe.util.BRBTextures;
import com.alonie.brbe.util.PartialCraftingUtil;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.recipebook.OverlayRecipeComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;


@Mixin(targets = "net.minecraft.client.gui.screens.recipebook.OverlayRecipeComponent$OverlayRecipeButton")
public abstract class OverlayRecipeButtonMixin extends AbstractWidget {

    @Final
    @Shadow
    private boolean isCraftable;
    @Final
    @Shadow
    RecipeHolder<?> recipe;

    @Shadow
    public abstract void renderWidget(GuiGraphics gui, int mouseX, int mouseY, float delta);

    @Shadow
    @Final
    protected List<?> ingredientPos;
    @Shadow
    @Final
    OverlayRecipeComponent field_3113;

    public OverlayRecipeButtonMixin(int x, int y, int width, int height, Component message) {
        super(x, y, width, height, message);
    }

    @Inject(at = @At("HEAD"), method = "renderWidget", cancellable = true)
    public void renderWidget(GuiGraphics gui, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        boolean effectiveCraftable = this.isCraftable
                || PartialCraftingUtil.isPartiallyCraftable(field_3113.getRecipeCollection(), this.recipe);
        ResourceLocation resourceLocation;

        if (((OverlayRecipeComponentAccessor) field_3113).isFurnaceMenu()) {
            resourceLocation = BRBTextures.RECIPE_BOOK_PLAIN_OVERLAY_SPRITE.get(effectiveCraftable, isHoveredOrFocused());
        } else {
            resourceLocation = BRBTextures.RECIPE_BOOK_CRAFTING_OVERLAY_SPRITE.get(effectiveCraftable, isHoveredOrFocused());
        }

        gui.blitSprite(resourceLocation, getX(), getY(), this.width, this.height);

        // Red overlay for partially-craftable recipes
        if (PartialCraftingUtil.isPartiallyCraftable(field_3113.getRecipeCollection(), this.recipe)) {
            gui.fill(getX() + 1, getY() + 1, getX() + width - 1, getY() + height - 1, 0x60FF3333);
        }

        gui.pose().pushPose();
        if (BetterRecipeBook.ctx().config().alternativeRecipes.onHover && !this.isHoveredOrFocused()) { // if show alternatives recipe is enabled and recipe is not hovered, show the result item
            ItemStack recipeOutput = this.recipe.value().getResultItem(field_3113.getRecipeCollection().registryAccess());
            gui.renderItem(recipeOutput, getX() + 4, getY() + 4);
        } else { // otherwise display the crafting recipe
            gui.pose().translate(this.getX() + 2, this.getY() + 2, 150.0);
            for (Object rawPos : this.ingredientPos) {
                OverlayRecipeButtonPosAccessor pos = (OverlayRecipeButtonPosAccessor) rawPos;
                gui.pose().pushPose();
                gui.pose().translate(pos.brbe$getX(), pos.brbe$getY(), 0.0);
                // if furnace menu, keep items at default scale, so it isn't tiny
                if (!((OverlayRecipeComponentAccessor) field_3113).isFurnaceMenu()) {
                    gui.pose().scale(0.375f, 0.375f, 1.0f);
                }
                gui.pose().translate(-8.0, -8.0, 0.0);
                ItemStack[] ingredients = pos.brbe$getIngredients();
                if (ingredients.length > 0) {
                    gui.renderItem(ingredients[Mth.floor(((OverlayRecipeComponentAccessor) field_3113).getTime() / 30.0f) % ingredients.length], 0, 0);
                }
                gui.pose().popPose();
            }
        }
        gui.pose().popPose();

        // blit pin for pinned recipes
        if (BetterRecipeBook.pinnedRecipeManager.pinned.contains(recipe.id())) {
            gui.pose().pushPose();
            // make sure pin is drawn over the crafting items
            gui.pose().mulPose(gui.pose().last().pose());
            gui.blitSprite(BRBTextures.RECIPE_BOOK_OVERLAY_PIN_SPRITE, getX() - 4, getY() - 4, this.width + 8, this.height + 8);
            gui.pose().popPose();
        }

        ci.cancel();
    }

}
