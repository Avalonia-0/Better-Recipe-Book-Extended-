package com.alonie.brbe.fabric.Mixins;

import com.alonie.brbe.BetterRecipeBook;
import mezz.jei.common.Internal;
import mezz.jei.common.gui.textures.JeiAtlasManager;
import mezz.jei.common.gui.textures.Textures;
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
        if (FabricLoader.getInstance().isModLoaded("jei")) {
            return;
        }
        try {
            Textures textures = Internal.getTextures();
            JeiAtlasManager atlasManager = textures.getAtlasManager();
            resourceManager.registerReloadListener(atlasManager);
            BetterRecipeBook.LOGGER.info("[BRBE-JEI-Plugins] embedded JEI core atlas registered before initial resource reload (headless mode)");
        } catch (Exception | LinkageError e) {
            BetterRecipeBook.LOGGER.warn("[BRBE-JEI-Plugins] failed to register embedded JEI atlas: {}", e.toString());
        }
    }
}
