package com.alonie.brbe.fabric.jei;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.jei.plugins.BrbeJeiHeadlessCore;
import com.alonie.brbe.jei.plugins.BrbeJeiPlugins;
import mezz.jei.common.Internal;
import mezz.jei.common.gui.textures.JeiGuiSpriteManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

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
        // JEI GUI 图集（assets/jei 内嵌）：注册为资源重载监听器，让弹窗
        // 渲染完整 JEI 界面（槽位背景/箭头/火焰/背景板）。等价官方
        // JeiLifecycleEvents.REGISTER_RESOURCE_RELOAD_LISTENER 的接线。
        try {
            JeiGuiSpriteManager spriteManager = Internal.getTextures().getGuiSpriteManager();
            ResourceManagerHelper.get(PackType.CLIENT_RESOURCES)
                    .registerReloadListener(new IdentifiableResourceReloadListener() {
                        @Override
                        public ResourceLocation getFabricId() {
                            return ResourceLocation.fromNamespaceAndPath("zzzbrbe", "jei_gui_sprites");
                        }

                        @Override
                        public CompletableFuture<Void> reload(
                                PreparableReloadListener.PreparationBarrier preparationBarrier,
                                ResourceManager resourceManager,
                                ProfilerFiller profilerFiller,
                                ProfilerFiller profilerFiller2,
                                Executor executor,
                                Executor executor2) {
                            return spriteManager.reload(preparationBarrier, resourceManager,
                                    profilerFiller, profilerFiller2, executor, executor2);
                        }
                    });
        } catch (Exception | LinkageError e) {
            BetterRecipeBook.LOGGER.debug("[BRBE-JEI-Plugins] JEI gui sprite manager skipped: {}", e.toString());
        }

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
