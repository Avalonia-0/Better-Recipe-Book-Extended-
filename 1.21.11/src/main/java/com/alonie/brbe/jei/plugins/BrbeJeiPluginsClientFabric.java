package com.alonie.brbe.jei.plugins;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import mezz.jei.common.Internal;
import mezz.jei.common.gui.textures.JeiAtlasManager;
import mezz.jei.common.gui.textures.Textures;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.PreparableReloadListener;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * 1.21.11 Fabric 无头 JEI 接线：真实 JEI 缺席时启动内嵌 JEI 核心 + 收集
 * mod 插件数据索引进轻量桥注册表。对应 1.21.1 的
 * BrbeJeiPluginsClientFabric——1.21.11 为 fabric 单模块，类在
 * {@code com.alonie.brbe.jei.plugins} 包内。
 *
 * <p>JEI GUI 图集（assets/jei 内嵌）：1.21.11 的对应类是
 * {@link JeiAtlasManager}（实现 {@link PreparableReloadListener}，等价 1.21.1
 * 的 JeiGuiSpriteManager）——注册为 fabric 资源重载监听器，让弹窗渲染完整
 * JEI 界面（槽位背景/箭头/火焰/背景板），等价官方
 * JeiLifecycleEvents.REGISTER_RESOURCE_RELOAD_LISTENER 的接线。</p>
 *
 * <p>配方同步/生命周期：JOIN/DISCONNECT/CLIENT_STOPPING 与
 * {@code ClientRecipeSynchronizedEvent}（fabric-recipe-api 9.x）由
 * {@link BrbeJeiHeadlessCore#init()} 内部接线，本入口只负责图集监听器与
 * JOIN 后收集。</p>
 */
public final class BrbeJeiPluginsClientFabric implements ClientModInitializer {

    private static final Logger LOGGER = LogManager.getLogger("headless-jei");

    @Override
    public void onInitializeClient() {
        // JEI GUI 图集：注册为资源重载监听器（在初始资源重载之前完成注册，
        // 与真实 JEI / 主分支 BrbeJeiMinecraftMixin 等价）。
        try {
            Textures textures = Internal.getTextures();
            JeiAtlasManager atlasManager = textures.getAtlasManager();
            ResourceManagerHelper.get(PackType.CLIENT_RESOURCES)
                    .registerReloadListener(new IdentifiableResourceReloadListener() {
                        @Override
                        public Identifier getFabricId() {
                            return Identifier.fromNamespaceAndPath("headlessjei", "jei_gui_atlas");
                        }

                        @Override
                        public void prepareSharedState(PreparableReloadListener.SharedState state) {
                            atlasManager.prepareSharedState(state);
                        }

                        @Override
                        public CompletableFuture<Void> reload(
                                PreparableReloadListener.SharedState state,
                                Executor loadAndStitchExecutor,
                                PreparableReloadListener.PreparationBarrier preparationBarrier,
                                Executor joinAndUploadExecutor) {
                            return atlasManager.reload(state, loadAndStitchExecutor,
                                    preparationBarrier, joinAndUploadExecutor);
                        }
                    });
        } catch (Exception | LinkageError e) {
            LOGGER.debug("[BRBE-JEI-Plugins] JEI gui atlas listener skipped: {}", e.toString());
        }

        // 内嵌核心 + 生命周期接线（JOIN 启动/DISCONNECT/CLIENT_STOPPING/
        // ClientRecipeSynchronizedEvent 均在 init 内注册）。
        BrbeJeiHeadlessCore.init();

        // Collect after a level loads, matching JEI's AFTER_RECIPES_UPDATED
        // timing — data components are bound by then, so plugins like
        // Farmer's Delight that build ItemStacks in registerRecipeCatalysts work.
        // mezz.jei.api is vendored (embedded full core), so collection is safe
        // even without the real JEI installed.
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> BrbeJeiPlugins.collectAndInject());
    }
}
