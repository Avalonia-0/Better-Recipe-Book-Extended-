package com.alonie.brbe.fabric;

import com.alonie.brbe.BetterRecipeBook;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

public class BetterRecipeBookFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        BetterRecipeBook.init();
        // 酿造/锻造进度触发器数据包：服务器启动（含集成服务器/LAN/多机）时按
        // 服务器权威世界路径写入当前世界 datapacks（客户端 JOIN 时机路径不可靠；
        // 服务器侧写入同时覆盖多机场景——服务器装 BRBE 即 per-save 进度）。
        ServerLifecycleEvents.SERVER_STARTED.register(server ->
                com.alonie.brbe.brewingstand.RecipeUnlockTracker.writeProgressPacksForServer(server));
    }
}
