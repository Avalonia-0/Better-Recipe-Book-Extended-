package com.alonie.brbe.command;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.config.BrbeConfig;
import com.alonie.brbe.config.KeybindingGuiRegistrar;
import com.alonie.brbe.config.PinyinSearchDefaults;
import com.alonie.brbe.mixins.accessors.AbstractRecipeBookScreenAccessor;
import com.alonie.brbe.mixins.accessors.RecipeBookComponentAccessor;
import com.alonie.brbe.pin.TabPinManager;
import com.alonie.brbe.pinoverlay.PinOverlayManager;
import com.alonie.brbe.util.PageFlipSound;
import me.shedaniel.autoconfig.ConfigHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeUpdateListener;

/**
 * {@code /brbe clear ...} 四个动作的实现（与指令树、加载器解耦，便于单测与复用）。
 *
 * <p>每个动作返回 {@link Result}：成功/失败 + 文案键 + 参数，由指令树翻成聊天消息。</p>
 */
public final class BrbeCommandActions {

    /** 动作结果。{@code ok=false} 时 {@code langKey} 是失败文案键。 */
    public record Result(boolean ok, String langKey, Object[] args) {
        public static Result ok(String langKey, Object... args) {
            return new Result(true, langKey, args);
        }

        public static Result fail(String langKey, Object... args) {
            return new Result(false, langKey, args);
        }
    }

    private BrbeCommandActions() {}

    /**
     * {@code /brbe clear configchange}：把配置界面的所有配置项恢复为默认值。
     *
     * <p>Cloth 的 {@code resetToDefault()} 只把 holder 里的配置对象换成
     * {@code serializer.createDefault()} 的新实例（不落盘、不通知监听器），因此这里：</p>
     * <ol>
     *   <li>先对 {@code holder.getConfig()}（<b>新</b>对象，不是启动时缓存的
     *       {@code BetterRecipeBook.config}）做语言相关默认值收敛——{@code pinyinSearch}
     *       是语言条件项（中文 = 开、其他语言 = 关，见 {@link PinyinSearchDefaults}），
     *       只认 POJO 常量默认值会让中文会话"恢复默认"后拼音搜索变成关；</li>
     *   <li>再 {@code save()} —— 它才会落盘，并以 holder 里的新对象触发保存监听器
     *       （{@code ConfigChanged} → 管线/引擎/UI 刷新，同时把静态引用换到新对象上）。</li>
     * </ol>
     *
     * <p>另外键位字段还要写回原版 {@code KeyMapping} 并落盘 options.txt，否则运行中的
     * 按键仍是旧绑定、且下次改键会把旧值写回配置。</p>
     */
    public static Result resetConfig() {
        ConfigHolder<BrbeConfig> holder = BetterRecipeBook.configHolder;
        if (holder == null) {
            return Result.fail("brbe.command.failed", "config holder unavailable");
        }
        holder.resetToDefault();
        PinyinSearchDefaults.applyLanguageDefault(holder.getConfig(), Minecraft.getInstance());
        holder.save();
        KeybindingGuiRegistrar.applyConfigToKeyMappings();
        return Result.ok("brbe.command.done.configchange");
    }

    /** {@code /brbe clear rbippin}：清除所有 RBIP 标签的固定。 */
    public static Result clearRbipPins() {
        int cleared = TabPinManager.clearAll();
        refreshOpenBook();
        return Result.ok("brbe.command.done.rbippin", cleared);
    }

    /** {@code /brbe clear recipepin}：清除配方书配方的固定。 */
    public static Result clearRecipePins() {
        int cleared = BetterRecipeBook.pinnedRecipeManager.clearAll();
        refreshOpenBook();
        return Result.ok("brbe.command.done.recipepin", cleared);
    }

    /** {@code /brbe clear leipin}：清除查询界面（LEI）对象的固定（含落盘）。 */
    public static Result clearViewerPins() {
        int cleared = PinOverlayManager.clearAllAndSave();
        return Result.ok("brbe.command.done.leipin", cleared);
    }

    /**
     * {@code /brbe set pagesound <声音ID>}：设置 BRBE 全部界面的翻页音效。
     *
     * <p>只接受<b>已注册</b>的声音 ID（{@code minecraft:ui.button.click} 这类；命名空间
     * 可省略，由指令参数补成 {@code minecraft:}），写入配置后落盘并<b>立即试听</b>一声——
     * 试听走 {@link PageFlipSound#playConfiguredPreview()}：不受「鼠标滚轮翻页音效」开关
     * 影响（试听是显式动作），但受「音效音量」控制。</p>
     */
    public static Result setPageFlipSound(String rawId) {
        ConfigHolder<BrbeConfig> holder = BetterRecipeBook.configHolder;
        if (holder == null) {
            return Result.fail("brbe.command.failed", "config holder unavailable");
        }
        String id = rawId == null ? "" : rawId.trim();
        if (!PageFlipSound.exists(id)) {
            return Result.fail("brbe.command.set.pagesound.unknown", id);
        }
        holder.getConfig().pageFlipSound = id;
        holder.save();
        PageFlipSound.playConfiguredPreview();
        return Result.ok("brbe.command.set.pagesound.done", id);
    }

    /**
     * 让当前打开的配方书立即重建：固定顺序/固定标记不等到重开界面才生效。
     * 与配方书内固定键的刷新路径同源（{@code updateTabs} + {@code recipesUpdated}）。
     */
    private static void refreshOpenBook() {
        try {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft == null || minecraft.gui == null) return;
            Screen screen = minecraft.gui.screen();
            if (screen instanceof AbstractRecipeBookScreen<?> recipeBookScreen) {
                RecipeBookComponent<?> component =
                        ((AbstractRecipeBookScreenAccessor) recipeBookScreen).brbe$getRecipeBookComponent();
                if (component != null) {
                    ((RecipeBookComponentAccessor) component).updateTabsInvoker(false);
                }
            }
            if (screen instanceof RecipeUpdateListener listener) {
                listener.recipesUpdated();
            }
        } catch (Throwable ignored) {
            // 刷新失败不影响指令结果（下次打开界面自然生效）
        }
    }
}
