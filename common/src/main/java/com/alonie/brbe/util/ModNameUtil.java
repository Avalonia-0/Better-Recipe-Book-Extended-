package com.alonie.brbe.util;

import dev.architectury.platform.Platform;
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

    private static String resolveModName(String namespace) {
        // Priority 1: i18n via Jade's translation key format (jade.modName.{MOD_ID})
        // Works with Jade installed or any resource pack providing these keys
        String jadeKey = "jade.modName." + namespace;
        if (I18n.exists(jadeKey)) {
            return I18n.get(jadeKey);
        }

        // Priority 2: Mod metadata display name via Architectury's cross-loader API
        try {
            if (Platform.isModLoaded(namespace)) {
                String modName = Platform.getMod(namespace).getName();
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
