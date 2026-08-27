package com.alonie.brbe.jei.plugins;

import com.mojang.blaze3d.platform.InputConstants;
import mezz.jei.api.runtime.IJeiKeyMapping;
import mezz.jei.common.input.IInternalKeyMappings;
import mezz.jei.common.input.keys.IJeiKeyMappingInternal;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/**
 * 1.21.1 无头键位映射：headless 模式无真实 JEI 键位——全部返回空映射
 * （isUnbound=true、isActiveAndMatches=false、isDown=false）。
 * 对应 1.21.11 的 HeadlessKeyMappings（其依赖 27.4 的 fabric 按键注册，1.21.1 自研实现）。
 */
public final class HeadlessKeyMappings implements IInternalKeyMappings {

    private static final IJeiKeyMappingInternal UNBOUND = new UnboundKeyMapping();

    private static final class UnboundKeyMapping implements IJeiKeyMappingInternal {
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
            return Component.literal("");
        }

        @Override
        public boolean isDown() {
            return false;
        }

        @Override
        public IJeiKeyMappingInternal register(Consumer<KeyMapping> registerMethod) {
            return this;
        }
    }

    @Override public IJeiKeyMapping getToggleOverlay() { return UNBOUND; }
    @Override public IJeiKeyMapping getFocusSearch() { return UNBOUND; }
    @Override public IJeiKeyMapping getToggleCheatMode() { return UNBOUND; }
    @Override public IJeiKeyMapping getToggleEditMode() { return UNBOUND; }
    @Override public IJeiKeyMapping getToggleCheatModeConfigButton() { return UNBOUND; }
    @Override public IJeiKeyMapping getRecipeBack() { return UNBOUND; }
    @Override public IJeiKeyMapping getPreviousCategory() { return UNBOUND; }
    @Override public IJeiKeyMapping getNextCategory() { return UNBOUND; }
    @Override public IJeiKeyMapping getPreviousRecipePage() { return UNBOUND; }
    @Override public IJeiKeyMapping getNextRecipePage() { return UNBOUND; }
    @Override public IJeiKeyMappingInternal getPauseRecipeCycling() { return UNBOUND; }
    @Override public IJeiKeyMapping getPreviousPage() { return UNBOUND; }
    @Override public IJeiKeyMapping getNextPage() { return UNBOUND; }
    @Override public IJeiKeyMapping getCloseRecipeGui() { return UNBOUND; }
    @Override public IJeiKeyMapping getBookmark() { return UNBOUND; }
    @Override public IJeiKeyMapping getToggleBookmarkOverlay() { return UNBOUND; }
    @Override public IJeiKeyMapping getShowRecipe() { return UNBOUND; }
    @Override public IJeiKeyMapping getShowUses() { return UNBOUND; }
    @Override public IJeiKeyMapping getTransferRecipeBookmark() { return UNBOUND; }
    @Override public IJeiKeyMapping getMaxTransferRecipeBookmark() { return UNBOUND; }
    @Override public IJeiKeyMappingInternal getShowBookmarkTooltipFeatures() { return UNBOUND; }
    @Override public IJeiKeyMapping getQuickMove() { return UNBOUND; }
    @Override public IJeiKeyMapping getShareToChat() { return UNBOUND; }
    @Override public IJeiKeyMapping getCheatOneItem() { return UNBOUND; }
    @Override public IJeiKeyMapping getCheatItemStack() { return UNBOUND; }
    @Override public IJeiKeyMapping getToggleHideIngredient() { return UNBOUND; }
    @Override public IJeiKeyMapping getToggleWildcardHideIngredient() { return UNBOUND; }
    @Override public IJeiKeyMapping getHoveredClearSearchBar() { return UNBOUND; }
    @Override public IJeiKeyMapping getPreviousSearch() { return UNBOUND; }
    @Override public IJeiKeyMapping getNextSearch() { return UNBOUND; }
    @Override public IJeiKeyMapping getCopyRecipeId() { return UNBOUND; }
    @Override public IJeiKeyMapping getEscapeKey() { return UNBOUND; }
    @Override public IJeiKeyMapping getLeftClick() { return UNBOUND; }
    @Override public IJeiKeyMapping getRightClick() { return UNBOUND; }
    @Override public IJeiKeyMapping getEnterKey() { return UNBOUND; }
}
