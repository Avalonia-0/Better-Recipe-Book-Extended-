package com.alonie.brbe.fabric.jei;

import com.alonie.brbe.jei.plugins.BrbeJeiHeadlessCore;
import com.alonie.brbe.jei.plugins.BrbeJeiPlugins;
import com.alonie.brbe.jei.plugins.HeadlessJeiLog;
import mezz.jei.common.Internal;
import mezz.jei.common.gui.textures.JeiGuiSpriteManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
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

    /** 真实 JEI 场景：数据搬运收集是否已完成（本次 world join）。 */
    private static boolean realJeiCollected;

    @Override
    public void onInitializeClient() {
        boolean realJei = net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("jei");
        // 日志恒写 <gameDir>/logs/brbe-debug.log（无开关）；没有真实 JEI 时顺带把官方
        // mezz.jei 的 log4j 输出也路由到该文件（INFO 不再刷 latest.log，WARN+ 仍进）。
        // 时机：fabric-loader 0.15.11 的 EntrypointPatch 把 Hooks.startClient 注入在
        // Minecraft.<init> 内（javap 核实：instance 静态字段在 offset 154 赋值、
        // gameDirectory 在 offset 172 赋值，注入点在 window/GL 初始化之后）——
        // 此处 getInstance() 非 null 且 gameDirectory 已就绪。
        HeadlessJeiLog.init(net.minecraft.client.Minecraft.getInstance().gameDirectory.toPath(),
                !realJei);

        // 真实 JEI 存在：无头不启动 runtime（真实 JEI 自己运行），只做数据
        // 搬运——插件收集读入 JeiRecipeRegistry（BRBE 桥走同一 registry
        // 数据流）；不注册图集监听器（真实 JEI 自己注册）。
        if (realJei) {
            ClientTickEvents.END_CLIENT_TICK.register(client -> {
                if (client.level == null) {
                    // 离开世界/重进：重置，下一 join 重新收集。
                    realJeiCollected = false;
                    return;
                }
                if (realJeiCollected) {
                    return;
                }
                realJeiCollected = true;
                BrbeJeiPlugins.collectAndInject();
            });
            return;
        }

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
            HeadlessJeiLog.log("BRBE-JEI-PLUGINS", "JEI gui sprite manager skipped: {}", e.toString());
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
