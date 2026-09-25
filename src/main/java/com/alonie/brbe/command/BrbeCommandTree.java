package com.alonie.brbe.command;

import net.minecraft.network.chat.Component;

/**
 * BRBE 的客户端指令树（{@code /brbe ...}）。
 *
 * <p>与加载器无关：树的形状只有一份，<b>源类型</b>由 {@link Feedback} 适配
 * （Fabric 用 {@code FabricClientCommandSource.sendFeedback}，NeoForge 用
 * {@code CommandSourceStack.sendSuccess}），因此四个分支共用同一套指令语义与文案键。</p>
 *
 * <pre>
 * /brbe                              → 用法
 * /brbe clear                        → 列出 clear 的全部子命令与说明
 * /brbe clear configchange           → 全部配置项恢复默认值
 * /brbe clear rbippin                → 清除所有 RBIP 标签的固定
 * /brbe clear recipepin              → 清除配方书配方的固定
 * /brbe clear leipin                 → 清除查询界面（LEI）对象的固定
 * /brbe set                          → 列出 set 的全部子命令与说明
 * /brbe set pagesound &lt;声音ID&gt;      → 设置 BRBE 全部界面的翻页音效
 * </pre>
 */
public final class BrbeCommandTree {

    /** 反馈适配器：把"发一条消息"按各加载器的源类型实现。 */
    public interface Feedback<S> {
        void success(S source, String langKey, Object... args);

        void failure(S source, String langKey, Object... args);
    }

    private BrbeCommandTree() {}

    public static <S> com.mojang.brigadier.builder.LiteralArgumentBuilder<S> build(Feedback<S> fb) {
        return com.mojang.brigadier.builder.LiteralArgumentBuilder.<S>literal("brbe")
                .executes(ctx -> {
                    fb.success(ctx.getSource(), "brbe.command.usage");
                    return 1;
                })
                .then(com.mojang.brigadier.builder.LiteralArgumentBuilder.<S>literal("clear")
                        .executes(ctx -> {
                            S source = ctx.getSource();
                            fb.success(source, "brbe.command.clear.header");
                            fb.success(source, "brbe.command.clear.configchange");
                            fb.success(source, "brbe.command.clear.rbippin");
                            fb.success(source, "brbe.command.clear.recipepin");
                            fb.success(source, "brbe.command.clear.leipin");
                            return 1;
                        })
                        .then(com.mojang.brigadier.builder.LiteralArgumentBuilder.<S>literal("configchange")
                                .executes(ctx -> brbe$run(fb, ctx.getSource(),
                                        BrbeCommandActions::resetConfig)))
                        .then(com.mojang.brigadier.builder.LiteralArgumentBuilder.<S>literal("rbippin")
                                .executes(ctx -> brbe$run(fb, ctx.getSource(),
                                        BrbeCommandActions::clearRbipPins)))
                        .then(com.mojang.brigadier.builder.LiteralArgumentBuilder.<S>literal("recipepin")
                                .executes(ctx -> brbe$run(fb, ctx.getSource(),
                                        BrbeCommandActions::clearRecipePins)))
                        .then(com.mojang.brigadier.builder.LiteralArgumentBuilder.<S>literal("leipin")
                                .executes(ctx -> brbe$run(fb, ctx.getSource(),
                                        BrbeCommandActions::clearViewerPins))))
                .then(com.mojang.brigadier.builder.LiteralArgumentBuilder.<S>literal("set")
                        .executes(ctx -> {
                            S source = ctx.getSource();
                            fb.success(source, "brbe.command.set.header");
                            fb.success(source, "brbe.command.set.pagesound.usage");
                            return 1;
                        })
                        .then(com.mojang.brigadier.builder.LiteralArgumentBuilder.<S>literal("pagesound")
                                .then(com.mojang.brigadier.builder.RequiredArgumentBuilder
                                        .<S, net.minecraft.resources.Identifier>argument(
                                                "sound", net.minecraft.commands.arguments.IdentifierArgument.id())
                                        // 声音 ID 补全：列出全部已注册声音（前缀过滤）。
                                        .suggests((ctx, builder) -> {
                                            String remaining = builder.getRemainingLowerCase();
                                            for (net.minecraft.resources.Identifier id
                                                    : net.minecraft.core.registries.BuiltInRegistries
                                                            .SOUND_EVENT.keySet()) {
                                                String value = id.toString();
                                                if (value.startsWith(remaining)) builder.suggest(value);
                                            }
                                            return builder.buildFuture();
                                        })
                                        .executes(ctx -> brbe$run(fb, ctx.getSource(),
                                                // 不能用 IdentifierArgument.getId(ctx, …)：它固定收
                                                // CommandContext<CommandSourceStack>，而本树对源类型泛型。
                                                () -> BrbeCommandActions.setPageFlipSound(
                                                        ctx.getArgument("sound",
                                                                net.minecraft.resources.Identifier.class)
                                                                .toString()))))));
    }

    /** 跑一个动作并把结果翻成聊天反馈；动作抛异常时也只反馈错误、不冒泡。 */
    private static <S> int brbe$run(Feedback<S> fb, S source,
                                    java.util.function.Supplier<BrbeCommandActions.Result> action) {        try {
            BrbeCommandActions.Result result = action.get();
            if (result.ok()) {
                fb.success(source, result.langKey(), result.args());
                return 1;
            }
            fb.failure(source, result.langKey(), result.args());
            return 0;
        } catch (Throwable t) {
            fb.failure(source, "brbe.command.failed", Component.literal(String.valueOf(t)));
            return 0;
        }
    }

    // -- 指令输入识别（补全条目点击试听用）--------------------------------------

    /** {@code /brbe set pagesound <声音ID>} 的参数位（字面量之后允许空参数或空格分隔）。 */
    private static final java.util.regex.Pattern PAGE_SOUND_ARGUMENT_INPUT =
            java.util.regex.Pattern.compile(
                    "^/\\s*brbe\\s+set\\s+pagesound(\\s|$)",
                    java.util.regex.Pattern.CASE_INSENSITIVE);

    /**
     * 输入框文本是否正停在 {@code /brbe set pagesound <声音ID>} 的参数位。
     *
     * <p>补全条目的点击试听靠它把"我们的指令"和其它也用声音 ID 的指令（如
     * {@code /playsound}）区分开——指令字面量只在这里定义，判据也跟着放这里。</p>
     */
    public static boolean isPageSoundArgumentInput(String input) {
        if (input == null) return false;
        return PAGE_SOUND_ARGUMENT_INPUT.matcher(input.trim()).find();
    }
}
