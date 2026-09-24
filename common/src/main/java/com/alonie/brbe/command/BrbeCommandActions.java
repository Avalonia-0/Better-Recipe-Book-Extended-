package com.alonie.brbe.command;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.config.BrbeConfig;
import com.alonie.brbe.config.KeybindingGuiRegistrar;
import com.alonie.brbe.pin.TabPinManager;
import com.alonie.brbe.pinoverlay.PinOverlayManager;
import me.shedaniel.autoconfig.ConfigHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.recipebook.RecipeUpdateListener;

/**
 * {@code /brbe clear ...} 四个动作的实现（与指令树、加载器解耦）。
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
     * <p>Cloth 的 {@code resetToDefault()} 只替换配置对象（不落盘、不通知监听器），
     * 因此这里补一次 {@code save()} —— 它会触发 BRBE 注册的保存监听器
     * （{@code ConfigChanged} → 管线/引擎/UI 刷新）。键位字段还要写回原版
     * {@code KeyMapping} 并落盘 options.txt，否则运行中的按键仍是旧绑定、
     * 且下次改键会把旧值写回配置。</p>
     */
    public static Result resetConfig() {
        ConfigHolder<BrbeConfig> holder = BetterRecipeBook.configHolder;
        if (holder == null) {
            return Result.fail("brbe.command.failed", "config holder unavailable");
        }
        holder.resetToDefault();
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
     * 让当前打开的配方书立即重建（固定顺序/固定标记不等到重开界面才生效）。
     * 1.21.1 没有 {@code AbstractRecipeBookScreen}，只走 {@code RecipeUpdateListener}
     * （与 {@code RecipeUnlockUtil} 的刷新路径一致）。
     */
    private static void refreshOpenBook() {
        try {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft == null) return;
            if (minecraft.screen instanceof RecipeUpdateListener listener) {
                listener.recipesUpdated();
            }
        } catch (Throwable ignored) {
            // 刷新失败不影响指令结果（下次打开界面自然生效）
        }
    }
}
