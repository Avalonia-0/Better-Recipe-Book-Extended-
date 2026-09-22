package com.alonie.brbe.mixins.pausescreen;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.config.BrbeConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
/**
 * 暂停菜单「中间那一行按钮」的右侧插入一个 20×20 方形图标按钮，点击打开 BRBE 配置界面。
 *
 * <p><b>落点（1.21.11）</b>：原版 {@code createPauseMenu} 用 {@code GridLayout}（2 列）摆出 5 行按钮 ——
 * ① 返回游戏（整行 204）② 进度 / 统计 ③ 反馈 / 报告错误 ④ 选项… / 对局域网开放
 * ⑤ 保存并退出（整行 204）；本按钮挂在第 3 行（**中间那一行**）右端：该行最右端按钮的右缘再右移 4px，
 * 与该行按钮同 y。该位置原版不占用（Mod Menu 仅在 {@code game_menu_button_style=icon} 时才会把它的
 * 图标按钮放在这里；本测试实例是 {@code replace}，该行是整行 "Mods" 按钮，右缘 +4px 同样不与其重叠）。</p>
 *
 * <p><b>坐标是运行时量出来的</b>，不写死屏幕坐标：{@code @Inject(TAIL)} 执行时
 * {@code gridLayout.arrangeElements()} 与 {@code gridLayout.visitWidgets(...)} 都已经跑完，
 * 因此 {@code children()} 里的按钮已带最终位置 —— 按 y 分行、取中间一行、取该行最右缘。
 * 这样不受 GUI 缩放 / Mod Menu 改写 / 将来原版增删按钮行的影响；若一个可见按钮都拿不到
 * （原版结构大改），回退到屏幕右上角，按钮仍然可用。</p>
 *
 * <p><b>与 26.2 的布局差异（版本 API 所致，非功能缺失）</b>：26.2 的暂停菜单多了一条
 * "图标行"（bug 反馈/社交/好友/举报/Mod Menu 那一行，{@code createPauseMenu} 里调用
 * {@code LinearLayout.horizontal()}），26.2 的 {@code PauseScreenConfigButtonMixin} 用
 * {@code @Redirect} 拦下该行并把本按钮挂到行首；**1.21.11 的 {@code createPauseMenu} 仍是经典
 * {@code GridLayout + RowHelper} 两列布局，没有图标行**（{@code addFeedbackButtons(Screen, RowHelper)}
 * 直接把反馈按钮塞进网格行），因此这里改为在方法 TAIL 追加一个独立部件。</p>
 *
 * <p>刻意<b>不用</b> {@code LocalCapture} 拿 {@code RowHelper}/{@code GridLayout}（那样能把按钮
 * 直接塞进网格行）：Mod Menu 等 Mixin 会改写该方法的作用域，{@code LocalCapture} 会失败——
 * 与 26.2 实现同源的取舍。</p>
 *
 * <p>配置项 {@code hidePauseMenuConfigEntry} 开启时完全不添加该按钮。</p>
 */
@Mixin(PauseScreen.class)
public abstract class PauseScreenConfigButtonMixin extends Screen {

    /** 按钮边长（与原版图标按钮同规格）。 */
    private static final int BRBE_BUTTON_SIZE = 20;
    /** 与所在行最右端按钮之间的间距。 */
    private static final int BRBE_BUTTON_GAP = 4;

    protected PauseScreenConfigButtonMixin(Component title) {
        super(title);
    }

    @Inject(method = "createPauseMenu", at = @At("TAIL"))
    private void brbe$addConfigButton(CallbackInfo ci) {
        if (BetterRecipeBook.config.hidePauseMenuConfigEntry) return;
        Component message = Component.translatable("text.autoconfig.brbe.title");
        SpriteIconButton button = SpriteIconButton.builder(
                        message,
                        this::brbe$openConfigFromPauseMenu,
                        true)
                .size(BRBE_BUTTON_SIZE, BRBE_BUTTON_SIZE)
                .sprite(Identifier.fromNamespaceAndPath("brbe", "pause_menu/brbe"), 20, 18)
                .withTootip()
                .build();
        int[] anchor = brbe$middleRowAnchor();
        if (anchor == null) {
            // 兜底：量不到网格行时退回屏幕右上角（原版结构大改时才可能发生）。
            button.setPosition(this.width - BRBE_BUTTON_SIZE - 4, 4);
        } else {
            button.setPosition(anchor[0] + BRBE_BUTTON_GAP, anchor[1]);
        }
        this.addRenderableWidget(button);
    }

    /**
     * 量出「中间那一行按钮」的锚点。
     *
     * <p>同一 {@code GridLayout} 行内的按钮 y 完全相同，故按 y 分行即可；行按 y 升序排列后取
     * 第 {@code size/2} 行（5 行 → 第 3 行 = 正中间那一行）。</p>
     *
     * @return {@code {该行最右缘 x, 该行 y}}；列表里没有任何可见按钮时返回 {@code null}
     */
    private int[] brbe$middleRowAnchor() {
        TreeMap<Integer, Integer> rowRightEdges = new TreeMap<>();
        for (GuiEventListener child : this.children()) {
            if (!(child instanceof AbstractWidget widget) || !widget.visible) continue;
            int right = widget.getX() + widget.getWidth();
            Integer previous = rowRightEdges.get(widget.getY());
            if (previous == null || right > previous) {
                rowRightEdges.put(widget.getY(), right);
            }
        }
        if (rowRightEdges.isEmpty()) return null;
        List<Integer> rowYs = new ArrayList<>(rowRightEdges.keySet());
        int middleY = rowYs.get(rowYs.size() / 2);
        return new int[]{rowRightEdges.get(middleY), middleY};
    }

    /**
     * 暂停菜单配置按钮回调。
     *
     * <p><b>为什么拆成方法 + 方法引用</b>：mixin 类里的 lambda 会被编译成合成方法，
     * Mixin 必须重命名它们（否则与目标类同名合成方法冲突）并在 latest.log 打一行
     * {@code Renaming synthetic method ...}；方法引用走 invokedynamic 的
     * {@code MethodHandle}，与直接调用走同一套重映射（{@code transformMethodRef}），
     * 不产生合成方法、不刷日志。</p>
     */
    @Unique
    private void brbe$openConfigFromPauseMenu(Button button) {
        Minecraft.getInstance().setScreen(createConfigScreen(this));
    }

    /** 构建 Cloth Config 配置屏 —— **必须走 ConfigTipsHelper**（与书内设置按钮、ModMenu 同源）：
     *  直接调 AutoConfig 只会拿到未整理的界面（没有轮循行/分节行、条目是字段声明顺序）。 */
    private static Screen createConfigScreen(Screen parent) {
        return com.alonie.brbe.util.ConfigTipsHelper.buildConfigScreen(BrbeConfig.class, parent);
    }
}
