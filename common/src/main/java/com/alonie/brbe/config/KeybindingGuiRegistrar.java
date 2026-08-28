package com.alonie.brbe.config;

import com.alonie.brbe.BetterRecipeBook;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.gui.registry.GuiRegistry;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.api.ModifierKeyCode;
import net.minecraft.client.Minecraft;
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
            new KeybindingField("pinKey", "text.autoconfig.brbe.option.pinKey",
                    KeybindingCodec.pinDefaultValue(),
                    "text.autoconfig.brbe.option.pinKey.@Tooltip",
                    BetterRecipeBook.PIN_MAPPING),
            new KeybindingField("recipeViewKey", "text.autoconfig.brbe.option.recipeViewKey",
                    KeybindingCodec.recipeViewDefaultValue(),
                    "text.autoconfig.brbe.option.recipeViewKey.@Tooltip",
                    BetterRecipeBook.RECIPE_VIEW_MAPPING),
            new KeybindingField("usageViewKey", "text.autoconfig.brbe.option.usageViewKey",
                    KeybindingCodec.usageViewDefaultValue(),
                    "text.autoconfig.brbe.option.usageViewKey.@Tooltip",
                    BetterRecipeBook.USAGE_VIEW_MAPPING));

    private KeybindingGuiRegistrar() {
    }

    public static void register() {
        try {
            GuiRegistry registry = AutoConfig.getGuiRegistry(BrbeConfig.class);
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
                                        // 立即写入 options.txt：KeyMapping 的值只持久化在
                                        // options.txt，若不同步，重启时 Options.load 会用旧值
                                        // 覆盖配置（固定键回退为 F 的根因）。
                                        Minecraft.getInstance().options.save();
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
