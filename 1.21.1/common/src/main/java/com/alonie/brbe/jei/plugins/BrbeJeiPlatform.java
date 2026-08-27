package com.alonie.brbe.jei.plugins;

/**
 * 1.21.1 无头平台探测：反射判断真实 JEI 是否已装载（Fabric
 * {@code FabricLoader.isModLoaded} / NeoForge {@code ModList.isLoaded}），
 * 无平台编译依赖——嵌入式核心只在真实 JEI 缺席时启动。
 */
public final class BrbeJeiPlatform {

    private BrbeJeiPlatform() {}

    /** Whether the real JEI mod is loaded on this platform. */
    public static boolean realJeiLoaded() {
        return tryFabric("jei") || tryNeoForge("jei");
    }

    /** Whether the given mod id is loaded on Fabric (reflectively). */
    public static boolean tryFabric(String modId) {
        try {
            Class<?> loaderClass = Class.forName("net.fabricmc.loader.api.FabricLoader");
            Object loader = loaderClass.getMethod("getInstance").invoke(null);
            return (boolean) loaderClass.getMethod("isModLoaded", String.class).invoke(loader, modId);
        } catch (Throwable t) {
            return false;
        }
    }

    /** Whether the given mod id is loaded on NeoForge (reflectively). */
    public static boolean tryNeoForge(String modId) {
        try {
            Class<?> modListClass = Class.forName("net.neoforged.fml.ModList");
            Object modList = modListClass.getMethod("get").invoke(null);
            return (boolean) modListClass.getMethod("isLoaded", String.class).invoke(modList, modId);
        } catch (Throwable t) {
            return false;
        }
    }
}
