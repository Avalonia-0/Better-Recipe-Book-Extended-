package com.alonie.brbe.jei.api;

import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.Identifier;

import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

/**
 * 无头 JEI 弹窗渲染（轻量桥）：把一条 {@link JeiRecipeRegistry.Entry} 的完整
 * JEI 界面（类别背景/槽位背景/drawable/动画）经
 * {@code IRecipeManager#createRecipeLayoutDrawable} 渲染，缩放到调用方给的
 * 弹窗区域。渲染器持有 JEI 运行时引用（嵌入式核心或真实 JEI），无运行时
 * 时返回 false/空——调用方退回默认渲染。
 *
 * <p>对应 BRBE 主分支 PopupRenderer.renderJeiPopup 的独立项目版（缓存 +
 * 20Hz tick 保持一致）。1.21.11 版：类型 uid 用 {@link Identifier}；
 * {@code IRecipeLayoutDrawable} API 与 1.21.1 相同。</p>
 */
public final class JeiPopupRenderer {

    /** 弹窗面板尺寸（加大版预览），与 BRBE PopupRenderer 常量一致。 */
    public static final int POPUP_W = 60;
    public static final int POPUP_H = 60;

    private JeiPopupRenderer() {}

    /** JEI 布局缓存：typeUid → (配方对象 → drawable)，每个配方一个。
     *  tick 持续推进（火焰/循环动画），索引重建时整体失效。 */
    private static final Map<Identifier, Map<Object, IRecipeLayoutDrawable<?>>> JEI_CACHE =
            new WeakHashMap<>();
    private static long lastJeiTick = -1;

    /** 清空 JEI 布局缓存（索引重建时调用）。 */
    public static void invalidate() {
        JEI_CACHE.clear();
        lastJeiTick = -1;
    }

    /**
     * Render a JEI entry's full UI scaled to fit the popup area; returns the
     * popup's top-left origin / size, or null when the JEI runtime or the
     * entry's category is unavailable.
     */
    public static int[] render(JeiRecipeRegistry.Entry entry, GuiGraphics gui,
                               int x, int y, int w, int h, float scale) {
        IRecipeManager manager = com.alonie.brbe.jei.plugins.engine.JeiRuntimeBridge.recipeManager();
        IRecipeCategory<?> category =
                com.alonie.brbe.jei.plugins.engine.PluginRecipeIndexer.categoryFor(entry.typeUid());
        if (manager == null || category == null) {
            return null;
        }
        IRecipeLayoutDrawable<?> drawable = jeiDrawable(manager, category, entry);
        if (drawable == null) {
            return null;
        }
        // tick at JEI's 20 Hz rate (once per game tick batch)
        long now = System.currentTimeMillis();
        if (lastJeiTick < 0 || now - lastJeiTick >= 50) {
            lastJeiTick = now;
            drawable.tick();
        }
        Rect2i rect = drawable.getRect();
        int rw = Math.max(1, rect.getWidth());
        int rh = Math.max(1, rect.getHeight());
        float fit = Math.min((POPUP_W * scale) / rw, (POPUP_H * scale) / rh);
        fit = Math.min(fit, 2.0F * scale);
        int ox = (int) (x + w / 2f - rw * fit / 2f);
        int oy = (int) (y + h / 2f - rh * fit / 2f);
        gui.pose().pushMatrix();
        gui.pose().translate(ox, oy);
        gui.pose().scale(fit, fit);
        drawable.setPosition(0, 0);
        try {
            Minecraft mc = Minecraft.getInstance();
            drawable.drawRecipe(gui, (int) mc.mouseHandler.getScaledXPos(mc.getWindow()),
                    (int) mc.mouseHandler.getScaledYPos(mc.getWindow()));
        } catch (Exception | LinkageError e) {
            // a broken category must not kill the frame
        }
        gui.pose().popMatrix();
        return new int[] {ox, oy, (int) (rw * fit), (int) (rh * fit)};
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static IRecipeLayoutDrawable<?> jeiDrawable(IRecipeManager manager,
                                                        IRecipeCategory<?> category,
                                                        JeiRecipeRegistry.Entry entry) {
        Map<Object, IRecipeLayoutDrawable<?>> byRecipe =
                JEI_CACHE.computeIfAbsent(entry.typeUid(), k -> new WeakHashMap<>());
        IRecipeLayoutDrawable<?> drawable = byRecipe.get(entry.recipe());
        if (drawable != null) {
            return drawable;
        }
        try {
            Optional<?> optional = ((IRecipeManager) manager).createRecipeLayoutDrawable(
                    (IRecipeCategory) category, entry.recipe(),
                    com.alonie.brbe.jei.plugins.engine.EmptyFocusGroup.INSTANCE);
            drawable = (IRecipeLayoutDrawable<?>) optional.orElse(null);
        } catch (Exception | LinkageError e) {
            drawable = null;
        }
        if (drawable != null) {
            byRecipe.put(entry.recipe(), drawable);
        }
        return drawable;
    }
}
