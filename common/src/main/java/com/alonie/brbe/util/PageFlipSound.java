package com.alonie.brbe.util;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.command.BrbeCommandTree;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
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
        ResourceLocation id = ResourceLocation.tryParse(raw.trim());
        if (id == null) return null;
        return BuiltInRegistries.SOUND_EVENT.getOptional(id).orElse(null);
    }

    /** 该 ID 是否指向一个已注册的声音（{@code /brbe set pagesound} 的校验）。 */
    public static boolean exists(String raw) {
        return lookup(raw) != null;
    }

    // -- 播放 / 试听 -----------------------------------------------------------

    /** 试听音量倍率：与自动翻页音效同一档（0.25 = 原版按钮点击音量）。 */
    private static final float PREVIEW_VOLUME_SCALE = 0.25f;

    /**
     * 播放一个翻页音效（pitch 1.0 = 原版按钮点击原声，音量由调用方给出）。
     *
     * <p>音量 ≤ 0（玩家把「音效音量」拉到 0）或音频引擎缺失时不播。</p>
     */
    public static void play(Minecraft mc, SoundEvent sound, float volume) {
        if (sound == null || mc == null || mc.getSoundManager() == null) return;
        if (volume <= 0.0f) return;
        mc.getSoundManager().play(SimpleSoundInstance.forUI(sound, 1.0f, volume));
    }

    /** 试听音量：0.25 × 「音效音量」（0 = 静音）。 */
    public static float previewVolume() {
        return BetterRecipeBook.config == null
                ? 0.0f : PREVIEW_VOLUME_SCALE * BetterRecipeBook.config.pageFlipVolume;
    }

    /** 当前试听中的实例：点下一条目 / 再试听时先掐掉，避免长音效叠在一起。 */
    private static SoundInstance previewInstance;

    /**
     * 试听指定声音（指令补全条目点击预览）。
     *
     * <p><b>不受「鼠标滚轮翻页音效」开关影响</b>——那个开关管的是自动翻页声；试听是
     * 玩家显式动作，关掉开关也应该听得到。只受「音效音量」控制（0 = 静音）。</p>
     *
     * @return 是否真的发声（ID 未注册 / 音量为 0 时为 false）
     */
    public static boolean playPreview(String rawId) {
        SoundEvent sound = lookup(rawId);
        if (sound == null) return false;
        playPreviewSound(sound);
        return true;
    }

    /** 试听当前配置的音效（{@code /brbe set pagesound} 成功后）。 */
    public static void playConfiguredPreview() {
        playPreviewSound(resolve());
    }

    /** 试听播放：先停上一次试听（连续点选时不会叠音），音量 0 或音频引擎缺失则不播。 */
    private static void playPreviewSound(SoundEvent sound) {
        Minecraft mc = Minecraft.getInstance();
        if (sound == null || mc == null || mc.getSoundManager() == null) return;
        float volume = previewVolume();
        if (volume <= 0.0f) return;
        stopPreviewSound();
        SoundInstance instance = SimpleSoundInstance.forUI(sound, 1.0f, volume);
        previewInstance = instance;
        mc.getSoundManager().play(instance);
    }

    /** 停掉上一次试听（没有则什么也不做）。 */
    private static void stopPreviewSound() {
        if (previewInstance == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.getSoundManager() != null) {
            mc.getSoundManager().stop(previewInstance);
        }
        previewInstance = null;
    }

    /**
     * 指令补全条目被点击时的试听：只有输入确实停在
     * {@code /brbe set pagesound <声音ID>} 的参数位、且该建议是已注册声音时才发声。
     *
     * @param typedInput     点击前的输入框文本（{@code SuggestionsList.originalContents}）
     * @param suggestionText 被点击的补全条目文本
     */
    public static void previewSuggestion(String typedInput, String suggestionText) {
        if (suggestionText == null) return;
        if (!BrbeCommandTree.isPageSoundArgumentInput(typedInput)) return;
        playPreview(suggestionText.trim());
    }
}
