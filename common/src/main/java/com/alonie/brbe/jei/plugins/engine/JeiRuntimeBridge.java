package com.alonie.brbe.jei.plugins.engine;

import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.runtime.IJeiRuntime;
import mezz.jei.common.Internal;

/**
 * 1.21.1 运行时桥：从 {@code Internal#getJeiRuntime()} 取当前 JEI 运行时
 * （真实 JEI 或内嵌无头核心），无真实 JEI 时返回 null——所有调用方必须先判空。
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
