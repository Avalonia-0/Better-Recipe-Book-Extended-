package com.alonie.brbe.util;

import net.minecraft.ChatFormatting;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Utility for displaying the source mod name of items in recipe book tooltips.
 *
 * Resolution priority:
 * 1. i18n translation key {@code jade.modName.<namespace>} (works with Jade or resource packs)
 * 2. FabricLoader mod metadata display name (via reflection)
 * 3. Raw namespace as last resort
 */
public class ModNameUtil {

    /** {@link #warnFailureOnce(Throwable)} 的一次性闸门。 */
    private static volatile boolean warnFailureLogged;

    public static Component getFormattedModName(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return Component.empty();
        }

        String namespace = BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace();
        String modName = resolveModName(namespace);

        return Component.literal(modName).withStyle(ChatFormatting.BLUE, ChatFormatting.ITALIC);
    }

    public static String resolveModName(String namespace) {
        // Priority 1: i18n via Jade's translation key format (jade.modName.{MOD_ID})
        String jadeKey = "jade.modName." + namespace;
        if (I18n.exists(jadeKey)) {
            return I18n.get(jadeKey);
        }

        // Priority 2: Mod metadata display name via FabricLoader (reflection)
        String modName = resolveViaFabricLoader(namespace);
        if (modName != null) return modName;

        // Priority 3: Raw namespace as last resort (capitalize first letter)
        return namespace.substring(0, 1).toUpperCase() + namespace.substring(1);
    }

    /**
     * 模组**声明**里的显示名 —— {@code fabric.mod.json} 的 {@code name}，也就是模组菜单
     * （Mods）里显示的那一行；没有同名模组（纯数据包的命名空间、包 id 之类）时返回 {@code null}。
     *
     * <p>RBIP 的标签 tooltip 用它而不是 {@link #resolveModName(String)} 的 jade 优先级
     * （用户 2026-10-01：「直接取 mod 声明里的模组名」）。同时它也是「这个 id 是不是模组」的判定
     * ——数据包档据此决定「数据包归属于 Mod」。</p>
     */
    public static String metadataModName(String modId) {
        if (modId == null || modId.isBlank()) return null;
        try {
            Class<?> loaderClass = Class.forName("net.fabricmc.loader.api.FabricLoader");
            Object loader = loaderClass.getMethod("getInstance").invoke(null);
            Object container = loaderClass.getMethod("getModContainer", String.class)
                    .invoke(loader, modId);
            if (!(container instanceof java.util.Optional<?> opt) || opt.isEmpty()) return null;
            Object meta = invokeApi("net.fabricmc.loader.api.ModContainer", opt.get(), "getMetadata");
            Object name = invokeApi("net.fabricmc.loader.api.metadata.ModMetadata", meta, "getName");
            if (name instanceof String s && !s.isEmpty()) {
                return s;
            }
        } catch (Throwable t) {
            warnFailureOnce(t);
        }
        return null;
    }

    /**
     * 通过**公开 API 接口**取方法再 {@code invoke}（与 {@code compat/ModPresence.invokeOn} 同款）。
     *
     * <p>⚠️ 不能写成 {@code impl.getClass().getMethod("getName").invoke(impl)}：Fabric 的实现类
     * 多半不是 public —— fabric-loader 0.19.5 反编译实测，{@code ModContainerImpl.getMetadata()}
     * 返回的 {@code net.fabricmc.loader.impl.metadata.V1ModMetadata} 是**包私有 final class**，
     * 跨包反射 invoke 抛
     * {@code IllegalAccessException: class …ModNameUtil cannot access a member of class
     * …V1ModMetadata with modifiers "public"}（已用最小复现验证机制）。</p>
     *
     * <p>旧代码正是从实现类取方法 + {@code catch (Throwable ignored)}，所以异常被静默吞掉 →
     * 表现为「查不到模组」→ tooltip 退回"命名空间首字母大写"的兜底：用户 2026-10-01 实测
     * Nature's Compass（模组 id = 命名空间 = {@code naturescompass}）显示成「Naturescompass」。</p>
     */
    private static Object invokeApi(String apiClassName, Object target, String method) throws Exception {
        return Class.forName(apiClassName).getMethod(method).invoke(target);
    }

    /** 反射链出异常时**记一次**日志 —— 老代码的静默 catch 正是这个 bug 藏了几个月的原因。 */
    private static void warnFailureOnce(Throwable t) {
        if (warnFailureLogged) return;
        warnFailureLogged = true;
        BrbeLogger.log("ModName", "mod metadata lookup failed ({}); falling back to namespace", t.toString());
    }

    /** 该 id 是不是一个已加载模组的 id（Fabric 自带数据包的包 id 就是模组 id）。 */
    public static boolean isModId(String id) {
        return metadataModName(id) != null;
    }

    /**
     * RBIP 标签 tooltip 的名字：**直接取模组声明里的模组名**（模组菜单里那个），
     * 没有该模组 → jade 翻译键 → 命名空间首字母大写（{@link #resolveModName(String)} 的兜底）。
     */
    public static String resolveModDisplayName(String namespace) {
        String declared = metadataModName(namespace);
        if (declared != null) return declared;
        return resolveModName(namespace);
    }

    private static String resolveViaFabricLoader(String namespace) {
        return metadataModName(namespace);
    }
}
