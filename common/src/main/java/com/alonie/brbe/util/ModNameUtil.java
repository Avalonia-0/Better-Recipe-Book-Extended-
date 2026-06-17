package com.alonie.brbe.util;

import net.fabricmc.loader.api.FabricLoader;
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

        // Priority 2: Mod metadata display name via Architectury's cross-loader API
        try {
            var modOpt = FabricLoader.getInstance().getModContainer(namespace);
            if (modOpt.isPresent()) {
                String modName = modOpt.get().getMetadata().getName();
                if (modName != null && !modName.isEmpty()) {
                    return modName;
                }
            }
        } catch (Exception ignored) {
        }

        // Priority 3: Raw namespace as last resort
        return namespace;
    }
}
