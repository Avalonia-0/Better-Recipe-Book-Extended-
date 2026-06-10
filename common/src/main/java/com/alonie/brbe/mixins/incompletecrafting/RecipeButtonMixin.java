package com.alonie.brbe.mixins.incompletecrafting;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.util.PartialCraftingUtil;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.recipebook.RecipeButton;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.stats.RecipeBook;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Mixin(RecipeButton.class)
public abstract class RecipeButtonMixin extends AbstractWidget {

    protected RecipeButtonMixin(int x, int y, int width, int height, Component message) {
        super(x, y, width, height, message);
    }
    @Shadow
    private RecipeCollection collection;

    @Shadow
    private int currentIndex;

    /**
     * Disables the crafting filter so all recipes are always visible.
     * 1.21.1 checks filtering via book.isFiltering(menu) on RecipeBook,
     * not via a method on RecipeBookComponent.
     */
    @Redirect(
        method = "getOrderedRecipes",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/stats/RecipeBook;isFiltering(Lnet/minecraft/world/inventory/RecipeBookMenu;)Z")
    )
    private boolean betterRecipeBook$disableFilteringInOrdered(RecipeBook book, RecipeBookMenu<?, ?> menu) {
        return false;
    }

    @Redirect(
        method = "renderWidget",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/stats/RecipeBook;isFiltering(Lnet/minecraft/world/inventory/RecipeBookMenu;)Z")
    )
    private boolean betterRecipeBook$disableFilteringInRender(RecipeBook book, RecipeBookMenu<?, ?> menu) {
        return false;
    }

    @Redirect(method = "renderWidget", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/recipebook/RecipeCollection;hasCraftable()Z"))
    private boolean betterRecipeBook$renderPartiallyCraftableAsCraftable(RecipeCollection collection, GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        return collection.hasCraftable() || PartialCraftingUtil.hasPartialMaterials(collection);
    }

    @Inject(method = "renderWidget", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;renderFakeItem(Lnet/minecraft/world/item/ItemStack;II)V", shift = At.Shift.BEFORE))
    private void betterRecipeBook$renderPartialOverlay(GuiGraphics gui, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        List<RecipeHolder<?>> recipes = this.collection.getRecipes();
        if (recipes.isEmpty()) return;

        RecipeHolder<?> current = recipes.get(this.currentIndex % recipes.size());
        if (PartialCraftingUtil.isPartiallyCraftable(this.collection, current)) {
            gui.fill(getX() + 1, getY() + 1, getX() + width - 1, getY() + height - 1, 0x60FF3333);
        }
    }

    @Inject(method = "getOrderedRecipes", at = @At("RETURN"), cancellable = true)
    private void betterRecipeBook$filterOrderedRecipes(CallbackInfoReturnable<List<RecipeHolder<?>>> cir) {
        List<RecipeHolder<?>> result = new ArrayList<>(cir.getReturnValue());

        // Step 1: add partially-craftable recipes.
        {
            List<RecipeHolder<?>> partials = PartialCraftingUtil.getPartiallyCraftableRecipes(this.collection);
            if (!partials.isEmpty()) {
                Set<ResourceLocation> existing = new HashSet<>();
                for (RecipeHolder<?> r : result) {
                    existing.add(r.id());
                }
                for (RecipeHolder<?> p : partials) {
                    if (!existing.contains(p.id())) {
                        result.add(p);
                    }
                }
            }
        }

        // Step 2: if any craftable or partially-craftable recipes exist,
        // drop the completely uncraftable ones from cycling.
        boolean hasCraftable = this.collection.hasCraftable();
        boolean hasPartial = PartialCraftingUtil.hasPartialMaterials(this.collection);

        if ((hasCraftable || hasPartial) && result.size() > 1) {
            List<RecipeHolder<?>> filtered = new ArrayList<>(result.size());
            for (RecipeHolder<?> r : result) {
                if (this.collection.isCraftable(r) && !PartialCraftingUtil.isPartiallyCraftable(this.collection, r)) {
                    filtered.add(r);
                }
            }
            for (RecipeHolder<?> r : result) {
                if (PartialCraftingUtil.isPartiallyCraftable(this.collection, r)) {
                    filtered.add(r);
                }
            }
            cir.setReturnValue(filtered);
        } else if (!result.equals(cir.getReturnValue())) {
            cir.setReturnValue(result);
        }
    }

    @Inject(method = "isOnlyOption", at = @At("RETURN"), cancellable = true)
    private void betterRecipeBook$allowNestedAlternativeOverlay(CallbackInfoReturnable<Boolean> cir) {
        // Already returning false (multi-option) — nothing to fix.
        if (!cir.getReturnValue()) {
            return;
        }

        // Our filter may have reduced getOrderedRecipes to a single entry,
        // but the collection still contains multiple alternatives.
        // Force isOnlyOption=false so the overlay can show them all.
        if (this.collection.getRecipes().size() > 1
                && (this.collection.hasCraftable() || PartialCraftingUtil.hasPartialMaterials(this.collection))) {
            cir.setReturnValue(false);
        }
    }
}
