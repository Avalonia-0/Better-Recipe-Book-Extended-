package com.alonie.brbe.config;

import com.mojang.blaze3d.platform.InputConstants;
import me.shedaniel.clothconfig2.api.Modifier;
import me.shedaniel.clothconfig2.api.ModifierKeyCode;

/**
 * Encodes/decodes a {@link ModifierKeyCode} into a plain TOML-serializable
 * string like {@code key.keyboard.tab;ctrl}.  Cloth Config's own
 * {@code ModifierKeyCode} is an interface that Toml4j cannot serialize, so the
 * config field stores this string while the GUI entry works on the decoded key.
 */
public final class KeybindingCodec {

    public static final String PIN_DEFAULT_RAW = "key.keyboard.f";
    public static final String RECIPE_VIEW_DEFAULT_RAW = "key.keyboard.r";
    public static final String USAGE_VIEW_DEFAULT_RAW = "key.keyboard.u";

    private KeybindingCodec() {
    }

    public static ModifierKeyCode pinDefaultValue() {
        return ModifierKeyCode.of(InputConstants.getKey("key.keyboard.f"), Modifier.none());
    }

    public static String recipeViewDefaultRaw() {
        return RECIPE_VIEW_DEFAULT_RAW;
    }

    public static String usageViewDefaultRaw() {
        return USAGE_VIEW_DEFAULT_RAW;
    }

    public static ModifierKeyCode recipeViewDefaultValue() {
        return ModifierKeyCode.of(InputConstants.getKey("key.keyboard.r"), Modifier.none());
    }

    public static ModifierKeyCode usageViewDefaultValue() {
        return ModifierKeyCode.of(InputConstants.getKey("key.keyboard.u"), Modifier.none());
    }

    public static ModifierKeyCode decode(String raw) {
        // 空串 / 无法解析的键名 =「未指定」，而非回退默认，保证保存的 unknown 状态能还原。
        if (raw == null || raw.isBlank()) return ModifierKeyCode.unknown();
        String[] parts = raw.trim().split(";");
        InputConstants.Key key = InputConstants.getKey(parts[0].trim());
        if (key == InputConstants.UNKNOWN) return ModifierKeyCode.unknown();
        boolean alt = false, shift = false, control = false;
        if (parts.length > 1) {
            for (String m : parts[1].trim().split("\\+")) {
                switch (m.trim()) {
                    case "alt" -> alt = true;
                    case "shift" -> shift = true;
                    case "ctrl" -> control = true;
                    default -> { }
                }
            }
        }
        // Modifier.of 参数顺序为 (alt, control, shift)，不是直觉上的 (alt, shift, control)。
        return ModifierKeyCode.of(key, Modifier.of(alt, control, shift));
    }

    public static String encode(ModifierKeyCode mkc) {
        // 未指定（unknown）用空串哨兵持久化，与 decode 的 unknown() 还原对应。
        if (mkc == null || mkc.isUnknown()) return "";
        StringBuilder sb = new StringBuilder(mkc.getKeyCode().getName());
        Modifier modifier = mkc.getModifier();
        boolean hasAny = modifier.hasControl() || modifier.hasShift() || modifier.hasAlt();
        if (hasAny) {
            StringBuilder mods = new StringBuilder();
            if (modifier.hasControl()) mods.append("ctrl+");
            if (modifier.hasShift()) mods.append("shift+");
            if (modifier.hasAlt()) mods.append("alt+");
            mods.setLength(mods.length() - 1);
            sb.append(';').append(mods);
        }
        return sb.toString();
    }
}
