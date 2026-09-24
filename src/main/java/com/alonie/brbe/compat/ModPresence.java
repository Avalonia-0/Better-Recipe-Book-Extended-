package com.alonie.brbe.compat;

/**
 * 模组在场检测（纯反射，<b>不引用任何 Minecraft 类</b>）。
 *
 * <p>独立成类是为了能在 Mixin 引导阶段（{@link CompatMixinPlugin}）安全调用——
 * 那个阶段加载 Minecraft 类会引发过早类初始化。</p>
 */
public final class ModPresence {

    private ModPresence() {}

    /** 模组是否加载（无 FabricLoader 的加载器——如 NeoForge——返回 false）。 */
    public static boolean isLoaded(String modId) {
        Object container = container(modId);
        return container != null;
    }

    /** 模组版本（拿不到时返回 {@code "?"}）。 */
    public static String version(String modId) {
        Object container = container(modId);
        if (container == null) {
            return "?";
        }
        try {
            Object metadata = container.getClass().getMethod("getMetadata").invoke(container);
            Object version = metadata.getClass().getMethod("getVersion").invoke(metadata);
            Object friendly = version.getClass().getMethod("getFriendlyString").invoke(version);
            return String.valueOf(friendly);
        } catch (Throwable t) {
            return "?";
        }
    }

    private static Object container(String modId) {
        try {
            Class<?> loaderClass = Class.forName("net.fabricmc.loader.api.FabricLoader");
            Object loader = loaderClass.getMethod("getInstance").invoke(null);
            Object optional = loaderClass.getMethod("getModContainer", String.class).invoke(loader, modId);
            if (optional == null) {
                return null;
            }
            Object present = optional.getClass().getMethod("isPresent").invoke(optional);
            if (!Boolean.TRUE.equals(present)) {
                return null;
            }
            return optional.getClass().getMethod("get").invoke(optional);
        } catch (Throwable t) {
            return null;
        }
    }
}
