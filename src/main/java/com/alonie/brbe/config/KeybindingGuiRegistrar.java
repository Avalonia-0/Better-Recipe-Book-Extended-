package com.alonie.brbe.config;

import com.alonie.brbe.BetterRecipeBook;
import me.shedaniel.autoconfig.AutoConfigClient;
import me.shedaniel.autoconfig.gui.registry.GuiRegistry;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.api.ModifierKeyCode;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Field;
import java.util.List;

/**
 * Renders the string key-binding fields of {@link BrbeConfig} as Cloth Config
 * key-binding entries.  AutoConfig's default GUI providers do not map
 * {@code ModifierKeyCode}, so a predicate provider is registered against the
 * global per-config registry (the same one AutoConfig uses to build screens).
 */
public final class KeybindingGuiRegistrar {

    private record KeybindingField(String fieldName, String langKey, ModifierKeyCode defaultValue,
                                   String tooltipKey, net.minecraft.client.KeyMapping keyMapping) {
    }

    private static final List<KeybindingField> FIELDS = List.of(
            new KeybindingField("pinKey", "text.autoconfig.zzzbrbe.option.pinKey",
                    KeybindingCodec.pinDefaultValue(),
                    "text.autoconfig.zzzbrbe.option.pinKey.@Tooltip",
                    BetterRecipeBook.PIN_MAPPING),
            new KeybindingField("recipeViewKey", "text.autoconfig.zzzbrbe.option.recipeViewKey",
                    KeybindingCodec.recipeViewDefaultValue(),
                    "text.autoconfig.zzzbrbe.option.recipeViewKey.@Tooltip",
                    BetterRecipeBook.RECIPE_VIEW_MAPPING),
            new KeybindingField("usageViewKey", "text.autoconfig.zzzbrbe.option.usageViewKey",
                    KeybindingCodec.usageViewDefaultValue(),
                    "text.autoconfig.zzzbrbe.option.usageViewKey.@Tooltip",
                    BetterRecipeBook.USAGE_VIEW_MAPPING));

    private KeybindingGuiRegistrar() {
    }

    public static void register() {
        try {
            GuiRegistry registry = AutoConfigClient.getGuiRegistry(BrbeConfig.class);
            registry.registerPredicateProvider((i18n, field, config, defaults, registryAccess) -> {
                KeybindingField kb = FIELDS.stream()
                        .filter(f -> f.fieldName().equals(field.getName()))
                        .findFirst().orElse(null);
                if (kb == null) return List.of();
                try {
                    field.setAccessible(true);
                    ModifierKeyCode current = KeybindingCodec.decode((String) field.get(config));
                    var entry = ConfigEntryBuilder.create()
                            .startModifierKeyCodeField(
                                    Component.translatable(kb.langKey()),
                                    current)
                            .setModifierSaveConsumer(mkc -> {
                                try {
                                    field.set(config, KeybindingCodec.encode(mkc));
                                    // 与对应的原版 KeyMapping 同步（两处改同一配置）。
                                    if (kb.keyMapping() != null) {
                                        kb.keyMapping().setKey(mkc.getKeyCode());
                                    }
                                } catch (IllegalAccessException e) {
                                    throw new RuntimeException(e);
                                }
                            })
                            .setDefaultValue(() -> kb.defaultValue().getKeyCode())
                            .setModifierDefaultValue(() -> kb.defaultValue());
                    if (kb.tooltipKey() != null) {
                        entry.setTooltip(Component.translatable(kb.tooltipKey()));
                    }
                    return List.of(entry.build());
                } catch (IllegalAccessException e) {
                    return List.of();
                }
            }, field -> FIELDS.stream().anyMatch(f -> f.fieldName().equals(field.getName())));
        } catch (Throwable t) {
            // Cloth Config absent, or the registry could not be reached — GUI-only feature, skip.
            BetterRecipeBook.LOGGER.warn("[BRBE] Keybinding GUI registration skipped: {}", t.toString());
        }
    }
}
