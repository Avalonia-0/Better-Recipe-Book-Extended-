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
public final class ModNameUtil {
    private ModNameUtil() {
    }

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

        // Priority 3: Raw namespace as last resort
        return namespace;
    }

    private static String resolveViaFabricLoader(String namespace) {
        try {
            Class<?> loaderClass = Class.forName("net.fabricmc.loader.api.FabricLoader");
            Object loader = loaderClass.getMethod("getInstance").invoke(null);
            Object container = loaderClass.getMethod("getModContainer", String.class)
                    .invoke(loader, namespace);
            if (container != null) {
                java.util.Optional<?> opt = (java.util.Optional<?>) container;
                if (opt.isPresent()) {
                    Object meta = opt.get().getClass().getMethod("getMetadata").invoke(opt.get());
                    Object name = meta.getClass().getMethod("getName").invoke(meta);
                    if (name instanceof String s && !s.isEmpty()) {
                        return s;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }
}
