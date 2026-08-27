package com.alonie.brbe.jei.plugins.engine;

import com.mojang.blaze3d.platform.InputConstants;
import mezz.jei.api.runtime.IJeiKeyMapping;
import mezz.jei.common.input.IInternalKeyMappings;
import mezz.jei.common.input.keys.IJeiKeyMappingInternal;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/**
 * A headless {@link IInternalKeyMappings} used when BRBE initializes the
 * embedded full JEI core without the real JEI installed: every key mapping
 * reports "not bound" and never activates, so no JEI keybinding feature is
 * exposed to the player.  Keeps {@link mezz.jei.library.runtime.JeiRuntime}
 * construction happy (it requires a non-null mappings object).
 */
public final class HeadlessKeyMappings implements IInternalKeyMappings {

    private static final IJeiKeyMappingInternal NONE = new IJeiKeyMappingInternal() {
        @Override
        public boolean isActiveAndMatches(InputConstants.Key key) {
            return false;
        }

        @Override
        public boolean isUnbound() {
            return true;
        }

        @Override
        public Component getTranslatedKeyMessage() {
            return Component.translatable("key.keyboard.unknown");
        }

        @Override
        public boolean isDown() {
            return false;
        }

        @Override
        public IJeiKeyMappingInternal register(Consumer<KeyMapping> registerMethod) {
            return this;
        }
    };

    @Override
    public IJeiKeyMapping getToggleOverlay() { return NONE; }

    @Override
    public IJeiKeyMapping getFocusSearch() { return NONE; }

    @Override
    public IJeiKeyMapping getToggleCheatMode() { return NONE; }

    @Override
    public IJeiKeyMapping getToggleEditMode() { return NONE; }

    @Override
    public IJeiKeyMapping getToggleCheatModeConfigButton() { return NONE; }

    @Override
    public IJeiKeyMapping getRecipeBack() { return NONE; }

    @Override
    public IJeiKeyMapping getRecipeForward() { return NONE; }

    @Override
    public IJeiKeyMapping getPreviousCategory() { return NONE; }

    @Override
    public IJeiKeyMapping getNextCategory() { return NONE; }

    @Override
    public IJeiKeyMapping getPreviousRecipePage() { return NONE; }

    @Override
    public IJeiKeyMapping getNextRecipePage() { return NONE; }

    @Override
    public IJeiKeyMappingInternal getPauseRecipeCycling() { return NONE; }

    @Override
    public IJeiKeyMapping getPreviousPage() { return NONE; }

    @Override
    public IJeiKeyMapping getNextPage() { return NONE; }

    @Override
    public IJeiKeyMapping getCloseRecipeGui() { return NONE; }

    @Override
    public IJeiKeyMapping getBookmark() { return NONE; }

    @Override
    public IJeiKeyMapping getToggleBookmarkOverlay() { return NONE; }

    @Override
    public IJeiKeyMapping getShowRecipe() { return NONE; }

    @Override
    public IJeiKeyMapping getShowUses() { return NONE; }

    @Override
    public IJeiKeyMapping getTransferRecipeBookmark() { return NONE; }

    @Override
    public IJeiKeyMapping getMaxTransferRecipeBookmark() { return NONE; }

    @Override
    public IJeiKeyMappingInternal getShowBookmarkTooltipFeatures() { return NONE; }

    @Override
    public IJeiKeyMapping getQuickMove() { return NONE; }

    @Override
    public IJeiKeyMapping getShareToChat() { return NONE; }

    @Override
    public IJeiKeyMapping getCheatOneItem() { return NONE; }

    @Override
    public IJeiKeyMapping getCheatItemStack() { return NONE; }

    @Override
    public IJeiKeyMapping getToggleHideIngredient() { return NONE; }

    @Override
    public IJeiKeyMapping getToggleWildcardHideIngredient() { return NONE; }

    @Override
    public IJeiKeyMapping getHoveredClearSearchBar() { return NONE; }

    @Override
    public IJeiKeyMapping getPreviousSearch() { return NONE; }

    @Override
    public IJeiKeyMapping getNextSearch() { return NONE; }

    @Override
    public IJeiKeyMapping getCopyRecipeId() { return NONE; }

    @Override
    public IJeiKeyMapping getEscapeKey() { return NONE; }

    @Override
    public IJeiKeyMapping getLeftClick() { return NONE; }

    @Override
    public IJeiKeyMapping getRightClick() { return NONE; }

    @Override
    public IJeiKeyMapping getEnterKey() { return NONE; }
}
