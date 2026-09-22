package com.alonie.brbe.fabric.Mixins;

import com.alonie.brbe.BetterRecipeBook;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.main.GameConfig;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.alonie.brbe.util.BrbeLogger;

/**
 * Registers the embedded JEI core's GUI atlas before Minecraft's initial
 * resource reload, exactly like JEI's own {@code MinecraftMixin}.  Without the
 * real JEI installed the embedded core still needs its atlas stitched (the
 * recipe border / slot backgrounds come from it), otherwise
 * {@code createRecipeLayoutDrawable} fails with "atlas is not initialized".
 *
 * <p>When the real JEI is present it registers its own atlas via its own mixin
 * (and loads first on the classpath), so this mixin no-ops through the
 * {@code isModLoaded("jei")} guard.</p>
 *
 * <p><b>26.3：本 mixin 已成为空操作</b> —— 26.3 的 Minecraft 自带
 * {@code AtlasManager}（{@code Minecraft.getAtlasManager()}），JEI 一侧的
 * {@code JeiAtlasManager} 与 {@code Textures.getAtlasManager()} 全被删除，JEI
 * 现在直接从 MC 的 GUI atlas 取 sprite（{@code Internal} 里
 * {@code minecraft.getAtlasManager().getAtlasOrThrow(AtlasIds.GUI)}），GUI atlas
 * 由 MC 自己按命名空间拼贴，无需外部注册 reload listener。保留空的注入点只为
 * 不动 mixin 注册表；如需清理，可连同 {@code mixins.brbe.json} 的条目一起删除。</p>
 */
@Mixin(Minecraft.class)
public class BrbeJeiMinecraftMixin {

    @Shadow
    @Final
    private ReloadableResourceManager resourceManager;

    @Shadow
    @Final
    private TextureManager textureManager;

    @Inject(
            method = "<init>(Lnet/minecraft/client/main/GameConfig;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/ResourceLoadStateTracker;startReload(Lnet/minecraft/client/ResourceLoadStateTracker$ReloadReason;Ljava/util/List;)V",
                    ordinal = 0
            )
    )
    public void brbe$beforeInitialResourceReload(GameConfig gameConfig, CallbackInfo ci) {
        // 26.3: 无需注册（见类 javadoc）。真实 JEI 场景本就走它自己的注册路径。
        if (!FabricLoader.getInstance().isModLoaded("jei")) {
            BrbeLogger.log("BRBE-JEI-PLUGINS", "26.3: MC 自带 GUI AtlasManager，跳过 JEI atlas 注册");
        }
    }
}
