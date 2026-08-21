package com.alonie.brbe.mixins.keybinding;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.config.KeybindingCodec;
import com.mojang.blaze3d.platform.InputConstants;
import me.shedaniel.clothconfig2.api.Modifier;
import me.shedaniel.clothconfig2.api.ModifierKeyCode;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 原版按键绑定 ↔ Cloth Config 配置字符串双向同步（固定 / 查询合成 / 查询用途）。
 *
 * <p>原版按键绑定界面改键时调用 {@link KeyMapping#setKey}；这里把新键写回对应的
 * 配置字符串并保存，使 Cloth Config 界面显示同一份配置（{@code Options.load}
 * 启动恢复时同样经过 setKey，配置会跟随 options.txt 收敛）。反方向由
 * {@code KeybindingGuiRegistrar} 的保存回调在 Cloth Config 改键时 setKey 完成。</p>
 */
@Mixin(KeyMapping.class)
public abstract class KeyMappingSyncMixin {

    @Inject(method = "setKey", at = @At("HEAD"))
    private void brbe$syncConfigKey(InputConstants.Key key, CallbackInfo ci) {
        KeyMapping self = (KeyMapping) (Object) this;
        if (self != BetterRecipeBook.RECIPE_VIEW_MAPPING
                && self != BetterRecipeBook.USAGE_VIEW_MAPPING
                && self != BetterRecipeBook.PIN_MAPPING) {
            return;
        }
        if (BetterRecipeBook.config == null || BetterRecipeBook.configHolder == null) return;
        try {
            String raw = KeybindingCodec.encode(
                    ModifierKeyCode.of(key, Modifier.none()));
            if (self == BetterRecipeBook.RECIPE_VIEW_MAPPING) {
                BetterRecipeBook.config.recipeViewKey = raw;
            } else if (self == BetterRecipeBook.USAGE_VIEW_MAPPING) {
                BetterRecipeBook.config.usageViewKey = raw;
            } else {
                BetterRecipeBook.config.pinKey = raw;
            }
            BetterRecipeBook.configHolder.save();
        } catch (Throwable ignored) {
            // 同步失败不影响改键本身
        }
    }
}
