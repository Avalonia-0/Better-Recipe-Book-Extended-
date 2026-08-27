package com.alonie.brbe.fabric.jei;

import com.alonie.brbe.jei.plugins.BrbeJeiHeadlessCore;
import com.alonie.brbe.jei.plugins.BrbeJeiPlugins;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

/**
 * 1.21.1 Fabric 无头 JEI 接线：真实 JEI 缺席时启动内嵌 JEI 核心 + 收集
 * mod 插件数据索引进查询 viewer。对应 1.21.11 的 BrbeJeiPluginsClientFabric。
 *
 * <p>配方同步：fabric-recipe-api 5.0.16（1.21.1）无
 * {@code ClientRecipeSynchronizedEvent}（9.x 才有）——同步配方由
 * JOIN 时本地 RecipeManager 提供，无需显式注入
 * {@code Internal.setClientSyncedRecipes}（JeiStarter 无同步配方时自动
 * 回退 vanilla 配方）。</p>
 */
public final class BrbeJeiPluginsClientFabric implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            // 内嵌核心在 JOIN/配方同步后启动（start 内部等 level），
            // 收集紧随其后（插件仅依赖 mezz.jei.api，无需运行时）。
            BrbeJeiHeadlessCore.start();
            BrbeJeiPlugins.collectAndInject();
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> BrbeJeiHeadlessCore.stop());
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> BrbeJeiHeadlessCore.onClientStopping());
    }
}
