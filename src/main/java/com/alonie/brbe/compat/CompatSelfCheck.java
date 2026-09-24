package com.alonie.brbe.compat;

import com.alonie.brbe.util.BrbeLogger;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import com.alonie.recipebookispain_extended.access.RecipeBookScrollAccess;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 兼容自检（2026-09-25 新增）：把每个<b>条件兼容</b>的实际状态写成启动日志。
 *
 * <h3>为什么需要它</h3>
 * 兼容代码最贵的不是写，而是<b>静默失效</b>：{@code @Inject(require = 0)} 在目标方法改名/搬家后
 * 只是安静地不生效，编译通过、日志干净，最后是用户发现"某个功能悄悄不对了"。
 * 2026-09-25 的滚轮问题就是活例——兼容注入挂在 {@code IScrollableRecipeBook} 上，
 * 而 26.x 里那个接口<b>没有任何实现类</b>，等于白挂了几个月，谁都没发现。
 *
 * <p>因此凡是有条件兼容的模组，启动时都打一行状态；任何目标缺失一律 WARN（进
 * {@code latest.log}），把"用户报 bug"提前成"启动日志里就有"。</p>
 */
public final class CompatSelfCheck {

    private static final Logger LOG = LogManager.getLogger("brbe-compat");

    /** 首次接缝认领是否已记录（每次会话一次）。 */
    private static boolean seamClaimLogged;

    private CompatSelfCheck() {}

    /** 客户端启动时调用一次（entrypoint 的 CLIENT_STARTED）。 */
    public static void run() {
        try {
            checkMouseWheelie();
            checkRecipeBookScrollSeam();
        } catch (Throwable t) {
            // 自检自身绝不能影响启动
            LOG.warn("[BRBE-COMPAT] self-check failed", t);
        }
    }

    /** 滚轮类条件兼容是否在场（在场才值得记录接缝日志，纯净实例保持安静）。 */
    public static boolean wheelCompatPresent() {
        return ModPresence.isLoaded("mousewheelie");
    }

    /**
     * 配方书滚轮接缝首次认领时记一行（每次会话最多一行，且只在与其它滚轮模组共存时记）。
     * 这行是"接缝真的生效了"的运行时证据：认领成功 = 下游实现（mousewheelie/amecs priority
     * 键位）拿不到这次事件。
     */
    public static void noteSeamClaim(String what) {
        if (seamClaimLogged || !wheelCompatPresent()) {
            return;
        }
        seamClaimLogged = true;
        BrbeLogger.log("BRBE-COMPAT",
                "滚轮接缝生效：{} 已由 BRBE 认领（mousewheelie: {}，其配方书滚轮实现被绕过）",
                what, ModPresence.version("mousewheelie"));
    }

    private static void checkMouseWheelie() {
        if (!ModPresence.isLoaded("mousewheelie")) {
            BrbeLogger.log("BRBE-COMPAT", "mousewheelie: 未安装（滚轮兼容不参与）");
            return;
        }
        String version = ModPresence.version("mousewheelie");
        boolean triggerScroll = hasMethod("de.siphalor.mousewheelie.client.MWClient",
                "triggerScroll", double.class, double.class, double.class);
        boolean legacyTarget = hasMethod(
                "de.siphalor.mousewheelie.client.util.inject.IScrollableRecipeBook",
                "mouseWheelie_onMouseScrollRecipeBook", double.class, double.class, double.class);

        if (!triggerScroll) {
            LOG.warn("[BRBE-COMPAT] mousewheelie {} 缺少 MWClient.triggerScroll(DDD)Z —— "
                    + "滚轮兼容注入落空（该模组改了 API？）；配方书滚轮仍由 BRBE 接缝认领，"
                    + "但其配方书实现是否被绕过需实测", version);
        } else if (!legacyTarget) {
            BrbeLogger.log("BRBE-COMPAT", "mousewheelie {}: 旧接口 IScrollableRecipeBook 已不存在"
                    + "（旧兼容注入本就失效，26.x 起由接缝接管）", version);
        } else {
            BrbeLogger.log("BRBE-COMPAT", "mousewheelie {}: triggerScroll ✓ legacyTarget ✓ "
                    + "→ 配方书滚轮由 BRBE 接缝认领", version);
        }
    }

    private static void checkRecipeBookScrollSeam() {
        // RBIP 的标签栏滚轮是接缝的第 ① 步：它的实现挂在 RecipeBookComponent 上，
        // mixin 没应用（配置/加载顺序问题）时这里会立刻暴露。
        boolean applied;
        try {
            applied = RecipeBookScrollAccess.class.isAssignableFrom(RecipeBookComponent.class);
        } catch (Throwable t) {
            applied = false;
        }
        if (applied) {
            BrbeLogger.log("BRBE-COMPAT", "接缝: RecipeBookComponent 已实现 RecipeBookScrollAccess"
                    + "（RBIP 标签栏滚轮翻页可用）");
        } else {
            LOG.warn("[BRBE-COMPAT] 接缝: RecipeBookScrollAccess 未被 RecipeBookComponent 实现 —— "
                    + "RBIP mixin 未应用，标签栏滚轮翻页失效（配方页面翻页不受影响）");
        }
    }

    private static boolean hasMethod(String className, String method, Class<?>... params) {
        try {
            // initialize = false：只加载不初始化——目标模组的静态初始化（键位注册等）
            // 绝不能因为自检而被提前触发。
            Class<?> target = Class.forName(className, false, CompatSelfCheck.class.getClassLoader());
            target.getDeclaredMethod(method, params);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
