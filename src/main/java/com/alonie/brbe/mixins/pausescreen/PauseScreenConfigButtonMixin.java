package com.alonie.brbe.mixins.pausescreen;

import com.alonie.brbe.config.BrbeConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 暂停菜单图标行（bug 反馈/社交/好友/举报/Mod Menu 一行）左端插入一个 20×20
 * 方形按钮，点击打开 BRBE 配置界面。
 *
 * <p>实现：{@code @Redirect} 拦截 {@code createPauseMenu} 中
 * {@code LinearLayout.horizontal()} 静态调用——直接在重定向工厂里创建图标行并
 * 先挂入 BRBE 按钮（成为第 0 个子部件），原版随后在同一布局上挂 bug/社交/好友/
 * 举报按钮；原版的 {@code spacing(4)} 与新按钮一起被
 * {@code GridLayout.arrangeElements()}/{@code visitWidgets} 正常收编。完全不用
 * 局部变量捕获（Mod Menu 等 Mixin 会改写该方法 LVT，LocalCapture 会失败）。
 */
@Mixin(PauseScreen.class)
public abstract class PauseScreenConfigButtonMixin extends Screen {

    protected PauseScreenConfigButtonMixin(Component title) {
        super(title);
    }

    @Redirect(method = "createPauseMenu",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/layouts/LinearLayout;horizontal()Lnet/minecraft/client/gui/layouts/LinearLayout;"))
    private LinearLayout brbe$createIconRowWithConfigButton() {
        if (com.alonie.brbe.BetterRecipeBook.config.hidePauseMenuConfigEntry) {
            // 配置项开启：隐藏暂停菜单的 BRBE 配置入口——仍返回空白行（原版
            // 后续调用 spacing(4) 并挂载自身按钮，流程一致）。
            return LinearLayout.horizontal();
        }
        LinearLayout row = LinearLayout.horizontal();
        Component message = Component.translatable("text.autoconfig.brbe.title");
        SpriteIconButton button = SpriteIconButton.builder(
                        message,
                        b -> Minecraft.getInstance().gui.setScreen(createConfigScreen(PauseScreenConfigButtonMixin.this)),
                        true)
                .size(20, 20)
                .sprite(Identifier.fromNamespaceAndPath("brbe", "pause_menu/brbe"), 20, 18)
                .spriteOffset(0, 0)
                .build();
        button.setTooltip(Tooltip.create(message));
        row.addChild(button);
        return row;
    }

    /** 构建 Cloth Config 配置屏 —— **必须走 ConfigTipsHelper**（与书内设置按钮、ModMenu 同源）：
     *  直接调 AutoConfigClient 只会拿到未整理的界面（没有轮循行/分节行、条目是字段声明顺序）。 */
    private static Screen createConfigScreen(Screen parent) {
        return com.alonie.brbe.util.ConfigTipsHelper.buildConfigScreen(BrbeConfig.class, parent);
    }
}
