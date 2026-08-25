package com.alonie.brbe.fabric.compat.jei;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.compat.jei.JeiCompat;
import com.alonie.brbe.jei.plugins.engine.JeiRuntimeBridge;
import com.alonie.brbe.pinoverlay.PinOverlayManager;
import com.alonie.brbe.util.RecipeViewerOverlay;
import com.mojang.blaze3d.platform.InputConstants;
import mezz.jei.api.IModPlugin;
import net.minecraft.client.input.KeyEvent;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.handlers.IGlobalGuiHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.IFocusFactory;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.runtime.IJeiKeyMappings;
import mezz.jei.api.runtime.IJeiRuntime;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
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
        // Expose JEI's recipe manager so the synthetic recipe renderer can
        // delegate the full JEI recipe UI to JEI itself.
        JeiRuntimeBridge.set(jeiRuntime.getRecipeManager());

        // Only bridge R/U fallback queries to the real JEI.  With the real JEI
        // absent, the embedded headless core's RecipesGui is a no-op dummy, so
        // routing fallback keys to it would swallow them without showing
        // anything (a regression vs. the pre-embedding behavior where the keys
        // passed through to vanilla).
        if (!FabricLoader.getInstance().isModLoaded("jei")) {
            return;
        }

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
        JeiRuntimeBridge.clear();
        JeiCompat.setHandler(null);
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        // When the BRBE R/U viewer is open, tell JEI to keep its ingredient
        // list and recipe area out of the viewer's on-screen region.
        registration.addGlobalGuiHandler(new IGlobalGuiHandler() {
            @Override
            public Collection<Rect2i> getGuiExtraAreas() {
                List<Rect2i> areas = new ArrayList<>();
                Rect2i area = RecipeViewerOverlay.exclusionArea();
                if (area != null) areas.add(area);
                // The open hover popup's region (its hit volume = texture
                // bounds) keeps JEI out of the exact rect the popup owns.
                Rect2i popup = RecipeViewerOverlay.popupExclusionArea();
                if (popup != null) areas.add(popup);
                areas.addAll(PinOverlayManager.exclusionAreas());
                return areas;
            }
        });
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
