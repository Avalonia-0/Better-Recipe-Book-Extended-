package com.alonie.brbe.jei.plugins;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public final class BrbeJeiPluginsClientFabric implements ClientModInitializer {

    /** 真实 JEI 场景：数据搬运收集是否已完成（本次 world join）。 */
    private static boolean realJeiCollected;

    @Override
    public void onInitializeClient() {
        // 真实 JEI 存在：无头不启动 runtime（真实 JEI 自己运行），只做数据
        // 搬运——插件收集 + VanillaPlugin 运行时类型（anvil/brewing/grindstone）
        // 从真实 JEI 的 manager 读入 JeiRecipeRegistry，BRBE 桥走同一 registry
        // 数据流；渲染经读取制 JeiRuntimeBridge 委托真实 JEI。
        if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("jei")) {
            ClientTickEvents.END_CLIENT_TICK.register(client -> {
                if (client.level == null) {
                    // 离开世界/重进：重置，下一 join 重新收集。
                    realJeiCollected = false;
                    return;
                }
                if (realJeiCollected) {
                    return;
                }
                // 真实 JEI 的 runtime 尚未就绪时 manager 为 null（跳过，
                // 下一 tick 重试）；就绪后只收集一次（collectAndInject 幂等）。
                if (com.alonie.brbe.jei.plugins.engine.JeiRuntimeBridge.recipeManager() == null) {
                    return;
                }
                realJeiCollected = true;
                BrbeJeiPlugins.collectAndInject();
            });
            return;
        }
        // When the real JEI is absent, boot the embedded full JEI core so the
        // preview/pin popups render the real JEI front-end
        // (createRecipeLayoutDrawable).  No JEI GUI is registered.
        BrbeJeiHeadlessCore.init();

        // Collect after a level loads, matching JEI's AFTER_RECIPES_UPDATED
        // timing — data components are bound by then, so plugins like
        // Farmer's Delight that build ItemStacks in registerRecipeCatalysts work.
        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register((client, level) -> BrbeJeiPlugins.collectAndInject());
    }
}
