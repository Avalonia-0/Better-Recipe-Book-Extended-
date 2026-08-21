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
            try {
                Class<?> fabricLoader = Class.forName("net.fabricmc.loader.api.FabricLoader");
                Object instance = fabricLoader.getMethod("getInstance").invoke(null);
                return (boolean) instance.getClass().getMethod("isModLoaded", String.class)
                        .invoke(instance, "mousewheelie");
            } catch (Throwable e) {
                return false;
            }
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
