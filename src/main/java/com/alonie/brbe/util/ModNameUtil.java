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
 * 2. Architectury Platform API reading mod metadata name
 * 3. Raw namespace as last resort
 */
public class ModNameUtil {

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
        // Works with Jade installed or any resource pack providing these keys
        String jadeKey = "jade.modName." + namespace;
        String translated = I18n.get(jadeKey);
        if (!translated.equals(jadeKey)) {
            return translated;
        }

        // Priority 2: Mod metadata display name via FabricLoader or NeoForge mod list
        // Use reflection to avoid compile-time coupling to FabricLoader (NeoForge doesn't ship it)
        String modName = resolveViaFabricLoader(namespace);
        if (modName != null) return modName;

        // Priority 3: Raw namespace as last resort (capitalize first letter)
        return namespace.substring(0, 1).toUpperCase() + namespace.substring(1);
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
