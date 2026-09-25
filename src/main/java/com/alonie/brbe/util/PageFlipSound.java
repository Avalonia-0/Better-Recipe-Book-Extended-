package com.alonie.brbe.util;

import com.alonie.brbe.BetterRecipeBook;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

/**
 * 翻页音效解析：把配置项 {@code pageFlipSound}（brbe.toml，可由配置界面或
 * {@code /brbe set pagesound <声音ID>} 修改）解析成实际播放的 {@link SoundEvent}。
 *
 * <p>BRBE 的全部翻页音效都经 {@link ClientCompat#playPageFlipSound} 播放，
 * 因此这里是"自定义翻页音效"的唯一解析点（数据存配置、解析在这里）。</p>
 */
public final class PageFlipSound {

    /** 默认音效：原版按钮点击声（BRBE 一直使用的"哒"声）。 */
    public static final String DEFAULT_ID = "minecraft:ui.button.click";

    private PageFlipSound() {
    }

    /**
     * 解析当前配置里的声音 ID。
     *
     * <p>配置值非法（空 / 不是合法资源位置）或该声音未注册时<b>回退默认音效</b>——
     * 宁可用默认声，也不要静音（配置界面接受任意字符串，不在这里报错）。</p>
     */
    public static SoundEvent resolve() {
        SoundEvent configured = lookup(BetterRecipeBook.config == null
                ? null : BetterRecipeBook.config.pageFlipSound);
        return configured != null ? configured : SoundEvents.UI_BUTTON_CLICK.value();
    }

    /** 按 ID 取已注册的声音；未注册 / ID 非法 / 为空时返回 null。 */
    public static SoundEvent lookup(String raw) {
        if (raw == null || raw.isBlank()) return null;
        Identifier id = Identifier.tryParse(raw.trim());
        if (id == null) return null;
        return BuiltInRegistries.SOUND_EVENT.getOptional(id).orElse(null);
    }

    /** 该 ID 是否指向一个已注册的声音（{@code /brbe set pagesound} 的校验）。 */
    public static boolean exists(String raw) {
        return lookup(raw) != null;
    }
}
