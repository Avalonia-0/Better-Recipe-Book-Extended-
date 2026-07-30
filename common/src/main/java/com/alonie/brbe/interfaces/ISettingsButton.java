package com.alonie.brbe.interfaces;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.config.BrbeConfig;
import com.alonie.brbe.util.BRBTextures;
import com.alonie.brbe.util.ClientCompat;
import me.shedaniel.autoconfig.AutoConfigClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jetbrains.annotations.Nullable;

public interface ISettingsButton {
    MutableComponent OPEN_SETTINGS_TOOLTIP = Component.translatable("brbe.gui.settings.open");


    default ImageButton createSettingsButton(int i, int j) {
        if (BetterRecipeBook.config.settingsButton) {
            return new ImageButton(i + 11, j + 137, 18, 18, BRBTextures.SETTINGS_BUTTON_SPRITES, button -> {
                try {
                    var screen = AutoConfigClient.getConfigScreen(BrbeConfig.class, Minecraft.getInstance().gui.screen()).get();
                    Minecraft.getInstance().gui.setScreen(screen);
                } catch (NoClassDefFoundError e) {
                    // Cloth Config not available
                }
            });
        }
        return null;
    }

    default void renderSettingsButton(@Nullable ImageButton settingsButton, GuiGraphicsExtractor gui, int mouseX, int mouseY, float delta) {
        if (settingsButton != null && BetterRecipeBook.config.settingsButton) {
            settingsButton.extractRenderState(gui, mouseX, mouseY, delta);
        }
    }

    default boolean settingsButtonMouseClicked(@Nullable ImageButton settingsButton, double mouseX, double mouseY, int button) {
        if (settingsButton == null || !BetterRecipeBook.config.settingsButton) return false;

        return ClientCompat.mouseClicked(settingsButton, mouseX, mouseY, button);
    }

    // TODO: Remove this and use .setTooltip and render it automatically
    default void renderSettingsButtonTooltip(@Nullable ImageButton settingsButton, GuiGraphicsExtractor gui, int mouseX, int mouseY) {
        if (settingsButton != null && settingsButton.isHoveredOrFocused() && BetterRecipeBook.config.settingsButton
                && Minecraft.getInstance().gui.screen() != null) {
            ClientCompat.setComponentTooltipForNextFrame(gui, java.util.List.of(OPEN_SETTINGS_TOOLTIP), mouseX, mouseY);
        }
    }
}
