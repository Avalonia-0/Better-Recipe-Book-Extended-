package com.alonie.brbe.neoforge.jei;

import com.alonie.brbe.jei.plugins.BrbeJeiHeadlessCore;
import com.alonie.brbe.jei.plugins.BrbeJeiPlugins;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.GameShuttingDownEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

/**
 * 1.21.1 NeoForge 无头 JEI 接线：真实 JEI 缺席时启动内嵌 JEI 核心 + 收集
 * mod 插件数据写进轻量桥（{@code JeiRecipeRegistry}）。
 *
 * <p>21.1.21 无 ClientLifecycleEvent——配方同步用 RecipesUpdatedEvent（官方
 * JEI 1.21.1 同源），LevelEvent.Load 兜底（JOIN 后 level 就绪），
 * GameShuttingDownEvent 收尾关 DelayedExecutor。</p>
 */
public final class BrbeJeiPluginsClientNeoForge {

    private BrbeJeiPluginsClientNeoForge() {}

    public static void init(IEventBus modEventBus) {
        // 真实 JEI 存在：跳过图集双注册（真实 JEI 自己注册）；start() 自带
        // real 守卫（BrbeJeiPlatform.realJeiLoaded），收集照常（数据搬运）。
        if (!com.alonie.brbe.jei.plugins.BrbeJeiPlatform.realJeiLoaded()) {
            // JEI GUI 图集（assets/jei 内嵌）：注册为客户端资源重载监听器，
            // 让弹窗渲染完整 JEI 界面（等价官方 RegisterClientReloadListenersEvent 接线）。
            modEventBus.addListener(net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent.class, event -> {
                try {
                    mezz.jei.common.gui.textures.JeiGuiSpriteManager spriteManager =
                            mezz.jei.common.Internal.getTextures().getGuiSpriteManager();
                    event.registerReloadListener(spriteManager);
                } catch (Exception | LinkageError e) {
                    LOGGER.debug("JEI gui sprite manager skipped: {}", e.toString());
                }
            });
        }

        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.client.event.RecipesUpdatedEvent.class, event -> {
            java.util.List<net.minecraft.world.item.crafting.RecipeHolder<?>> recipes =
                    java.util.List.copyOf(event.getRecipeManager().getRecipes());
            if (!recipes.isEmpty()) {
                mezz.jei.common.Internal.setClientSyncedRecipes(recipes);
            }
            BrbeJeiHeadlessCore.start();
            BrbeJeiPlugins.collectAndInject();
        });
        NeoForge.EVENT_BUS.addListener(LevelEvent.Load.class, event -> {
            if (event.getLevel().isClientSide() && event.getLevel() instanceof net.minecraft.client.multiplayer.ClientLevel) {
                BrbeJeiHeadlessCore.start();
                BrbeJeiPlugins.collectAndInject();
            }
        });
        NeoForge.EVENT_BUS.addListener(LevelEvent.Unload.class, event -> {
            if (event.getLevel().isClientSide()) {
                BrbeJeiHeadlessCore.stop();
            }
        });
        NeoForge.EVENT_BUS.addListener(GameShuttingDownEvent.class,
                event -> BrbeJeiHeadlessCore.onClientStopping());
    }

    private static final org.apache.logging.log4j.Logger LOGGER =
            org.apache.logging.log4j.LogManager.getLogger("headless-jei");
}
