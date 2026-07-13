package com.alonie.brbe.neoforge.compat.jei;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.compat.jei.JeiCompat;
import com.mojang.blaze3d.platform.InputConstants;
import mezz.jei.api.IModPlugin;
import net.minecraft.client.input.KeyEvent;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.IFocusFactory;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.runtime.IJeiKeyMappings;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;

@JeiPlugin
public final class BetterRecipeBookJEIPlugin implements IModPlugin {
    private static final Identifier PLUGIN_UID = Identifier.fromNamespaceAndPath(BetterRecipeBook.MOD_ID, "jei");

    @Override
    public Identifier getPluginUid() {
        return PLUGIN_UID;
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        IJeiKeyMappings km = jeiRuntime.getKeyMappings();

        JeiCompat.setHandler(new JeiCompat.JeiHandler() {
            @Override
            public boolean openRecipeView(ItemStack stack) {
                return open(jeiRuntime, RecipeIngredientRole.OUTPUT, stack);
            }

            @Override
            public boolean openUsageView(ItemStack stack) {
                return open(jeiRuntime, RecipeIngredientRole.INPUT, stack);
            }

            @Override
            public boolean matchesShowRecipe(int keyCode, int scanCode) {
                InputConstants.Key key = InputConstants.getKey(new KeyEvent(keyCode, scanCode, 0));
                return km.getShowRecipe().isActiveAndMatches(key);
            }

            @Override
            public boolean matchesShowUses(int keyCode, int scanCode) {
                InputConstants.Key key = InputConstants.getKey(new KeyEvent(keyCode, scanCode, 0));
                return km.getShowUses().isActiveAndMatches(key);
            }
        });
    }

    @Override
    public void onRuntimeUnavailable() {
        JeiCompat.setHandler(null);
    }

    private static boolean open(IJeiRuntime jeiRuntime, RecipeIngredientRole role, ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }

        Optional<ITypedIngredient<ItemStack>> typedIngredient = jeiRuntime.getIngredientManager()
                .createTypedIngredient(VanillaTypes.ITEM_STACK, stack, false);
        if (typedIngredient.isEmpty()) {
            return false;
        }

        IFocusFactory focusFactory = jeiRuntime.getJeiHelpers().getFocusFactory();
        IFocus<ItemStack> focus = focusFactory.createFocus(role, typedIngredient.get());
        jeiRuntime.getRecipesGui().show(List.of(focus));
        return true;
    }
}
