package com.alonie.brbe.compat;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public class CompatMixinPlugin implements IMixinConfigPlugin {

    @Override
    public void onLoad(String mixinPackage) {}

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        // mousewheelie compat — use reflection to avoid compile-time coupling to FabricLoader
        // (NeoForge does not ship fabric-loader at runtime)
        if (mixinClassName.equals("com.alonie.brbe.compat.mixins.mousewheelie.MixinMWClient")) {
            // 纯反射实现抽到 ModPresence（不引用任何 Minecraft 类，Mixin 引导阶段可安全调用）；
            // 同一份判定也被 CompatSelfCheck 用于启动自检，避免两处各写一套。
            return ModPresence.isLoaded("mousewheelie");
        }
        return false;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

}
