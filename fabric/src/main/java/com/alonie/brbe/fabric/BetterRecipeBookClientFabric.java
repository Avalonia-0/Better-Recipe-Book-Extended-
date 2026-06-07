package com.alonie.brbe.fabric;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.compat.OverlayHider;
import com.alonie.brbe.fabric.compat.rei.ReiCompatHandler;
import dev.architectury.event.events.client.ClientGuiEvent;
import net.fabricmc.api.ClientModInitializer;

public class BetterRecipeBookClientFabric implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ReiCompatHandler.register();

        ClientGuiEvent.INIT_POST.register((screen, access) -> {
            if (screen != null) {
                // Apply overlay hide state immediately when screen opens (no flash)
                OverlayHider.setOverlaysHidden(BetterRecipeBook.config.hideReiJeiOverlay);
            }
        });
    }
}
