package com.alonie.brbe.fabric;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.compat.OverlayHider;
import com.alonie.brbe.fabric.compat.rei.ReiCompatHandler;
import dev.architectury.event.events.client.ClientGuiEvent;
import dev.architectury.event.events.client.ClientTickEvent;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.gui.screens.Screen;

public class BetterRecipeBookClientFabric implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ReiCompatHandler.register();

        ClientGuiEvent.INIT_POST.register((screen, access) -> {
            if (screen != null) {
                OverlayHider.setOverlaysHidden(BetterRecipeBook.config.hideReiJeiOverlay);
            }
        });

        ClientTickEvent.CLIENT_POST.register(client -> {
            Screen screen = client.screen;
            if (BetterRecipeBook.config.hideReiJeiOverlay && screen != null) {
                OverlayHider.ensureJeiOverlayHidden();
            }
        });
    }
}
