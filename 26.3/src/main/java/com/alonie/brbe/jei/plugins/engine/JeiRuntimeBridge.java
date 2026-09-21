package com.alonie.brbe.jei.plugins.engine;

import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.runtime.IJeiRuntime;
import mezz.jei.common.Internal;

/**
 * 运行时桥：从 {@code Internal#getJeiRuntime()} 取当前 JEI 运行时（真实 JEI
 * 或内嵌无头核心），无运行时返回 null——所有调用方必须先判空。
 *
 * <p>读取制（与 1.21.11/1.21.1 一致）：真实 JEI 场景下 {@code mezz.jei.*}
 * 类由真实 JEI 提供（headless 入口在真实 JEI 时完全跳过，不抢先注册），
 * 此引用解析到真实 JEI 的 Internal → 返回真实 runtime；无头场景下无头核心
 * 启动时 {@code JeiStarter} 会 Internal.setRuntime → 返回无头 runtime。
 * 两种场景都自动可用，无需手动 set。</p>
 */
public final class JeiRuntimeBridge {

    private JeiRuntimeBridge() {}

    public static IRecipeManager recipeManager() {
        IJeiRuntime runtime = Internal.getJeiRuntime();
        return runtime == null ? null : runtime.getRecipeManager();
    }

    public static IJeiRuntime runtime() {
        return Internal.getJeiRuntime();
    }

    public static boolean runtimeAvailable() {
        return Internal.getJeiRuntime() != null;
    }
}
