package com.alonie.brbe.util;

import dev.architectury.platform.Platform;
import net.minecraft.ChatFormatting;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

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

    private static String resolveModName(String namespace) {
        String jadeKey = "jade.modName." + namespace;
        if (I18n.exists(jadeKey)) {
            return I18n.get(jadeKey);
        }

        try {
            if (Platform.isModLoaded(namespace)) {
                String modName = Platform.getMod(namespace).getName();
                if (modName != null && !modName.isEmpty()) {
                    return modName;
                }
            }
        } catch (Exception ignored) {
        }

        return namespace;
    }
}
