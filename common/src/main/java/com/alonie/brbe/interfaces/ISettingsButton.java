package com.alonie.brbe.interfaces;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.config.BrbeConfig;
import com.alonie.brbe.layout.BookLayout;
import com.alonie.brbe.util.BRBTextures;
import com.alonie.brbe.util.ConfigTipsHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jetbrains.annotations.Nullable;

public interface ISettingsButton {
    MutableComponent OPEN_SETTINGS_TOOLTIP = Component.translatable("brbe.gui.settings.open");


    default ImageButton createSettingsButton(int i, int j) {
        if (BetterRecipeBook.ctx().config().settingsButton) {
            return new ImageButton(i + BookLayout.SETTINGS_X_OFFSET, j + BookLayout.settingsY(),
                    BookLayout.settingsSize(), BookLayout.settingsSize(), BRBTextures.SETTINGS_BUTTON_SPRITES, button ->
                    ConfigTipsHelper.openConfigScreen(BrbeConfig.class, Minecraft.getInstance().screen));
        }
        return null;
    }

    default void renderSettingsButton(@Nullable ImageButton settingsButton, GuiGraphics gui, int mouseX, int mouseY, float delta) {
        if (settingsButton != null && BetterRecipeBook.ctx().config().settingsButton) {
            settingsButton.render(gui, mouseX, mouseY, delta);
        }
    }

    default boolean settingsButtonMouseClicked(@Nullable ImageButton settingsButton, double mouseX, double mouseY, int button) {
        if (settingsButton == null || !BetterRecipeBook.ctx().config().settingsButton) return false;

        return settingsButton.mouseClicked(mouseX, mouseY, button);
    }

    // TODO: Remove this and use .setTooltip and render it automatically
    default void renderSettingsButtonTooltip(@Nullable ImageButton settingsButton, GuiGraphics gui, int mouseX, int mouseY) {
        if (settingsButton != null && settingsButton.isHoveredOrFocused() && BetterRecipeBook.ctx().config().settingsButton
                && Minecraft.getInstance().screen != null) {
            gui.renderTooltip(Minecraft.getInstance().font, OPEN_SETTINGS_TOOLTIP, mouseX, mouseY);
        }
    }
}
