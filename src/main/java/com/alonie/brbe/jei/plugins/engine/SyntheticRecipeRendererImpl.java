package com.alonie.brbe.jei.plugins.engine;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.compat.SyntheticRecipeRenderer;
import com.alonie.brbe.recipeviewer.engine.RecipeViewerEngine;
import com.alonie.brbe.render.PopupGeometry;
import com.alonie.brbe.util.CycleLock;
import com.alonie.brbe.util.RecipeViewerOverlay;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.inputs.RecipeSlotUnderMouse;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.world.item.ItemStack;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.data.AtlasIds;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import com.alonie.brbe.util.BrbeLogger;

/**
 * The real {@link SyntheticRecipeRenderer}: delegates the recipe's full JEI UI
 * to JEI itself via {@link IRecipeManager#createRecipeLayoutDrawable}, scaled to
 * fit the content area passed by the caller.  JEI runs the category's
 * {@code setRecipe} and draws its own background, slot backgrounds
 * ({@code slot.setBackground}), drawables and animated icons, so mod categories
 * that JEI renders correctly (Better Archaeology, Better End, …) now show their
 * textures without BRBE re-implementing JEI's category rendering.
 *
 * <p>The {@code (x, y, w, h)} rect is the fitted <b>content area</b> (computed
 * by the caller's shared {@link PopupGeometry}); this renderer wraps it in the
 * 9-sliced container panel and scales the drawable to fill it, so the panel,
 * the hit volume and JEI's exclusion area all agree.  Requires the real JEI
 * runtime ({@link JeiRuntimeBridge}); without it {@link #render} returns false
 * and the caller falls back to its vanilla-style rendering.  The cached
 * drawable's {@code tick()} advances at a 20 Hz tick rate so JEI's per-tick
 * variant cycling and animations keep moving.
 */
public final class SyntheticRecipeRendererImpl implements SyntheticRecipeRenderer {

    /** The vanilla alternative-recipe-group background sprite (32x32, 9-slice),
     *  reused as the popped-up recipe's container panel. */
    private static final Identifier OVERLAY_RECIPE_SPRITE =
            Identifier.withDefaultNamespace("recipe_book/overlay_recipe");
    /** Border sliced off each side and stitched un-stretched (the corner radius
     *  lives here); the middle is stretched to fill the panel. */
    private static final int SPRITE_BORDER = 4;

    /** One drawable layout per synthetic recipe, cached so JEI's per-tick
     *  {@code tick()} (variant cycling and animated drawables) keeps advancing
     *  across frames.  Invalidated on every re-index. */
    private static final Map<RecipeDisplayId, IRecipeLayoutDrawable<?>> LAYOUT_CACHE = new HashMap<>();
    /** Last tick id this frame batch advanced, so {@code tick()} runs at 20 Hz
     *  (JEI's own tick rate) instead of once per rendered frame. */
    private static long lastTick = -1;
    /** Logged once on the first successful delegation, as a runtime proof that
     *  the renderer actually reached the real JEI (createRecipeLayoutDrawable). */
    private static boolean delegationLogged = false;

    public static void invalidate() {
        LAYOUT_CACHE.clear();
        SLOT_COUNTERS.clear();
        FROZEN_SLOTS.clear();
    }

    /** Diagnostics: logged once each for the render-skip cause and a failed
     *  drawable creation, to tell an absent JEI runtime apart from a broken
     *  category. */
    private static boolean renderSkipLogged = false;
    private static boolean drawableFailLogged = false;

    @Override
    public boolean canRender(RecipeDisplayId id) {
        // Only when the real JEI runtime is present AND this recipe has a
        // usable native layout does the delegated JEI UI actually paint.  The
        // shared PopupGeometry relies on this to pick the adapted coordinate
        // model; without it the geometry must match the vanilla fallback.
        return RecipeViewerEngine.getLayout(id) != null
                && com.alonie.brbe.cache.BrbeJeiBridge.jeiAvailable()
                && com.alonie.brbe.cache.BrbeJeiBridge.recipeFor(id) != null;
    }

    @Override
    public boolean render(RecipeDisplayId id, GuiGraphics gui, int x, int y, int w, int h) {
        RecipeViewerEngine.RecipeLayout layout = RecipeViewerEngine.getLayout(id);
        java.lang.Object recipe = com.alonie.brbe.cache.BrbeJeiBridge.recipeFor(id);
        if (recipe == null || layout == null) {
            if (!renderSkipLogged) {
                renderSkipLogged = true;
                BrbeLogger.log("BRBE-POPUP", "synthetic render skipped recipe={} layout={}",
                        recipe != null, layout != null);
            }
            return false;
        }

        IRecipeLayoutDrawable<?> drawable = LAYOUT_CACHE.get(id);
        if (drawable == null) {
            drawable = createDrawable(id, recipe, layout);
            if (drawable == null) {
                if (!drawableFailLogged) {
                    drawableFailLogged = true;
                    BrbeLogger.log("BRBE-POPUP", "createRecipeLayoutDrawable failed for {}", id);
                }
                return false;
            }
            LAYOUT_CACHE.put(id, drawable);
            if (!delegationLogged) {
                delegationLogged = true;
                BrbeLogger.log("BRBE-JEI-PLUGINS", "delegating synthetic recipe UI to JEI (createRecipeLayoutDrawable)");
            }
        }

        // The caller already fitted the category's aspect ratio into the
        // button and scaled it up (PopupGeometry.CONTENT_ZOOM) so the recipe
        // reads clearly; fit the drawable into the given content rect and wrap
        // it in the 9-sliced panel (corners stay at 1:1, the middle stretches).
        float fit = w / (float) layout.width();

        // 逐槽位的折叠锁（用户 2026-09-13 诉求 1/2/3）：锁定键按住时**只冻结
        // 指针下那个槽位**（用 display override 把它钉在当前变体上），其余槽位
        // 继续由 JEI 自己的轮循器推进；锁定键+滚轮由 CycleLock 逐格翻动被冻结的
        // 那一个。旧实现是"按住 Alt 就不 tick 整个 drawable"（整块界面一起冻），
        // 与用户要求相反。
        applyCycleLock(drawable, x, y, fit);

        long tick = net.minecraft.util.Util.getMillis() / 50;
        if (tick != lastTick) {
            lastTick = tick;
            // 始终 tick：没被指着的槽位必须继续自动轮换。
            drawable.tick();
        }

        renderContainer(gui, x, y, w, h);

        gui.pose().pushMatrix();
        gui.pose().translate(x, y);
        gui.pose().scale(fit, fit);

        // Delegate the whole recipe UI to JEI: it runs setRecipe, then draws the
        // category background, slot backgrounds, drawables and slot items in the
        // category's own coordinate system, so every mod category renders exactly
        // as it does inside JEI (backgrounds bound via slot.setBackground
        // included).
        drawable.setPosition(0, 0);
        drawable.drawRecipe(gui, 0, 0);

        gui.pose().popMatrix();

        // 被冻结的槽位：JEI 自己的候选角标（右下角 tag/list 标记）是按**当前
        // 显示分组的可见成员数**画的，而 display override 把分组缩成了 1 个成员
        // → 翻动折叠物品时角标会消失（用户 2026-09-13 诉求 3）。这里用 BRBE
        // 自己的角标重画（判定取的是槽位的原始候选列表，与 override 无关），
        // 位置与 JEI 原画完全一致。
        drawFrozenBadges(gui, drawable, x, y, fit);
        return true;
    }

    @Override
    public ItemStack itemUnderMouse(RecipeDisplayId id, double contentX, double contentY,
                                    float ox, float oy, float fit) {
        // The live drawable knows which slot variant it painted last (its own
        // CycleTimer-driven cycling), so the tooltip matches the rendered item
        // exactly instead of BRBE's independent SlotSelectTime.
        IRecipeLayoutDrawable<?> drawable = LAYOUT_CACHE.get(id);
        if (drawable == null || fit <= 0) {
            return ItemStack.EMPTY;
        }
        try {
            // contentX/contentY are in content coordinates (cursor transformed
            // by the same ox/oy/fit the renderer drew at); JEI's slot lookup
            // works in its own local layout coordinates, so map back.
            double localX = (contentX - ox) / fit;
            double localY = (contentY - oy) / fit;
            return drawable.getItemStackUnderMouse((int) Math.floor(localX), (int) Math.floor(localY))
                    .orElse(ItemStack.EMPTY);
        } catch (Exception | LinkageError e) {
            return ItemStack.EMPTY;
        }
    }

    /**
     * 逐槽位的 display override 计数器：被冻结的槽位用它在候选表里定位当前变体。
     *  首次冻结时对齐到**当时正在显示**的那一个（按物品**值**比对——JEI 展示的
     *  {@code TypedIngredient} 实例与列表里的不是同一个，{@code indexOf} 永远
     *  失配），之后由锁定键+滚轮 ±1 步进 —— 每个候选都恰好轮到一次。
     */
    private static final Map<IRecipeSlotDrawable, Integer> SLOT_COUNTERS = new HashMap<>();

    /** 当前被 display override 钉住的槽位（松开/指针移开时清 override）。 */
    private static final java.util.Set<IRecipeSlotDrawable> FROZEN_SLOTS =
            java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());

    /** 逐槽位的折叠锁：只冻结指针下的那一个槽位。
     *
     *  <p>被指着的槽位：每帧把 override 重设到 {@link CycleLock} 记录的变体
     *  下标（首次冻结时 latched 到当时显示的变体，之后由滚轮步进）；没被指着的
     *  槽位：清掉 override，交回 JEI 自己的轮循器（drawable 照常 tick）。</p>
     */
    private static void applyCycleLock(IRecipeLayoutDrawable<?> drawable, int ox, int oy, float fit) {
        boolean down = CycleLock.isDown();
        try {
            for (IRecipeSlotView view : drawable.getRecipeSlotsView().getSlotViews()) {
                if (!(view instanceof IRecipeSlotDrawable slot)) continue;
                List<ITypedIngredient<?>> all = slot.getAllIngredientsList();
                if (all == null || all.size() <= 1) continue;
                if (!down) {
                    unfreeze(slot);
                    continue;
                }
                // 槽位的屏幕矩形：JEI 的布局坐标 × fit + 内容原点（与
                // itemUnderMouse 的映射同源）。
                Rect2i area = slot.getAreaIncludingBackground();
                int sx = Math.round(ox + area.getX() * fit);
                int sy = Math.round(oy + area.getY() * fit);
                int sw = Math.max(1, Math.round(area.getWidth() * fit));
                int sh = Math.max(1, Math.round(area.getHeight() * fit));
                if (!CycleLock.claim(slot, sx, sy, sw, sh)) {
                    unfreeze(slot);
                    continue;
                }
                // 首帧：把计数器对齐到当前显示的变体。
                Integer counter = SLOT_COUNTERS.get(slot);
                if (counter == null) {
                    counter = slot.getDisplayedIngredient()
                            .flatMap(ITypedIngredient::getItemStack)
                            .map(shown -> indexOfValue(all, shown))
                            .orElse(0);
                    SLOT_COUNTERS.put(slot, counter);
                }
                int idx = CycleLock.indexFor(slot, counter);
                ITypedIngredient<?> target = all.get(Math.floorMod(idx, all.size()));
                if (target == null) continue;
                target.getItemStack().ifPresent(stack -> {
                    slot.clearDisplayOverrides();
                    slot.createDisplayOverrides().addItemStack(stack);
                    FROZEN_SLOTS.add(slot);
                });
            }
        } catch (Exception | LinkageError ignored) {
            // one broken drawable must not break the whole quick-flip
        }
    }

    /** 解除一个槽位的冻结（JEI 原生轮循恢复）。 */
    private static void unfreeze(IRecipeSlotDrawable slot) {
        if (FROZEN_SLOTS.remove(slot)) {
            SLOT_COUNTERS.remove(slot);
            try {
                slot.clearDisplayOverrides();
            } catch (Throwable ignored) {
            }
        }
    }

    /** 重画被冻结槽位的候选角标（JEI 自己的角标在 override 生效时会消失，
     *  见 {@code render} 末尾的注释）。 */
    private static void drawFrozenBadges(GuiGraphics gui,
                                         IRecipeLayoutDrawable<?> drawable,
                                         int ox, int oy, float fit) {
        if (FROZEN_SLOTS.isEmpty() || fit <= 0) return;
        try {
            for (IRecipeSlotView view : drawable.getRecipeSlotsView().getSlotViews()) {
                if (!(view instanceof IRecipeSlotDrawable slot)) continue;
                if (!FROZEN_SLOTS.contains(slot)) continue;
                Rect2i area = slot.getAreaIncludingBackground();
                drawBadge(gui, slot,
                        ox + (area.getX() + area.getWidth() / 2.0) * fit,
                        oy + (area.getY() + area.getHeight() / 2.0) * fit,
                        ox, oy, fit);
            }
        } catch (Exception | LinkageError ignored) {
        }
    }

    /** 画一个槽位的候选角标（tag / list 二选一），位置与 JEI 原画一致。 */
    private static void drawBadge(GuiGraphics gui, IRecipeSlotDrawable slot,
                                 double contentX, double contentY, float ox, float oy, float fit) {
        try {
            // hasCandidates：只有轮循槽位带角标。判定取自槽位的**原始**候选列表
            // （display override 不影响它），所以冻结期间同样成立。
            if (slot.getAllIngredients().limit(2).count() <= 1) return;
            // getTagKey() 只存在于真实 JEI 运行时（30.24+）的 API，编译期参考的
            // headless-jei 里没有 → 反射取；缺失即该运行时没有角标功能。
            boolean isTag;
            try {
                isTag = ((Optional<?>) slot.getClass().getMethod("getTagKey").invoke(slot)).isPresent();
            } catch (NoSuchMethodException e) {
                return;
            }
            Object textures = mezz.jei.common.Internal.getTextures();
            IDrawable icon = (IDrawable) textures.getClass()
                    .getMethod(isTag ? "getTagBadgeIcon" : "getListBadgeIcon")
                    .invoke(textures);
            Rect2i area = slot.getAreaIncludingBackground();
            int bx = Math.round(ox + (area.getX() + area.getWidth() - icon.getWidth() + 1) * fit);
            int by = Math.round(oy + (area.getY() + area.getHeight() - icon.getHeight() + 1) * fit);
            gui.pose().pushMatrix();
            gui.pose().translate(bx, by);
            gui.pose().scale(fit, fit);
            icon.draw(gui, 0, 0);
            gui.pose().popMatrix();
        } catch (ReflectiveOperationException | LinkageError e) {
            // JEI build without the badge: it painted no badge either.
        } catch (RuntimeException ignored) {
            // a broken badge draw must never break the popup
        }
    }

    @Override
    public void drawSlotBadge(RecipeDisplayId id, GuiGraphics gui,
                              double contentX, double contentY, float ox, float oy, float fit) {
        // JEI's candidates badge (tag/list marker) is painted by the live
        // drawable's slot draw — i.e. BEFORE the caller's red ghost mask, so
        // the mask would cover it.  Redraw it from the live slot: the same
        // tag-key decision and icon JEI used, the same bottom-right corner
        // offset.  The tag/list badge API only exists in newer JEI runtimes
        // (30.24+); older builds draw no badge and this returns silently.
        IRecipeLayoutDrawable<?> drawable = LAYOUT_CACHE.get(id);
        if (drawable == null || fit <= 0) {
            return;
        }
        try {
            double localX = (contentX - ox) / fit;
            double localY = (contentY - oy) / fit;
            Optional<RecipeSlotUnderMouse> under = drawable.getSlotUnderMouse(localX, localY);
            if (under.isEmpty()) return;
            drawBadge(gui, under.get().slot(), contentX, contentY, ox, oy, fit);
        } catch (RuntimeException | LinkageError ignored) {
            // a broken badge draw must never break the popup
        }
    }

    /** Index of the candidate whose item value equals {@code shown}, or 0. */
    private static int indexOfValue(List<ITypedIngredient<?>> all, ItemStack shown) {
        for (int i = 0; i < all.size(); i++) {
            ITypedIngredient<?> candidate = all.get(i);
            if (candidate == null) continue;
            if (candidate.getItemStack().map(shown::equals).orElse(false)) {
                return i;
            }
        }
        return 0;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static IRecipeLayoutDrawable<?> createDrawable(RecipeDisplayId id,
                                                           java.lang.Object recipe,
                                                           RecipeViewerEngine.RecipeLayout layout) {
        try {
            // 独立化后：manager/category/focus 都来自 headless-jei 运行时（反射；
            // 其 jar-in-jar 加载后类在 classpath）。mezz.jei.api 接口类型仅作编译参考。
            java.lang.Object manager = com.alonie.brbe.cache.BrbeJeiBridge.reflectRecipeManager();
            java.lang.Object category = com.alonie.brbe.cache.BrbeJeiBridge.reflectCategory(id);
            java.lang.Object focusGroup = com.alonie.brbe.cache.BrbeJeiBridge.emptyFocusGroup();
            if (manager == null || category == null || focusGroup == null) {
                return null;
            }
            java.util.Optional<?> opt = (java.util.Optional<?>) manager.getClass()
                    .getMethod("createRecipeLayoutDrawable",
                            mezz.jei.api.recipe.category.IRecipeCategory.class,
                            Object.class,
                            mezz.jei.api.recipe.IFocusGroup.class)
                    .invoke(manager, category, recipe, focusGroup);
            return (IRecipeLayoutDrawable<?>) opt.orElse(null);
        } catch (Exception | LinkageError e) {
            BrbeLogger.log("BRBE-JEI-PLUGINS", "createRecipeLayoutDrawable failed: {}", e.toString());
            return null;
        }
    }

    /** Draw the vanilla alternative-recipe-group background around the popped-up
     *  recipe by 9-slicing the sprite: the four corners (with their radius) are
     *  stitched un-stretched and only the middle is stretched to span the panel.
     *  {@code w}/{@code h} are the content area; the sprite's own border and the
     *  shared {@link PopupGeometry#CONTAINER_PADDING} are added here, exactly as
     *  vanilla's OverlayRecipeComponent sizes its panel to the button grid. */
    private static void renderContainer(GuiGraphics gui, int x, int y, int w, int h) {
        TextureAtlasSprite sprite = Minecraft.getInstance().getAtlasManager()
                .getAtlasOrThrow(AtlasIds.GUI)
                .getSprite(OVERLAY_RECIPE_SPRITE);
        Identifier atlas = sprite.atlasLocation();
        int sw = sprite.contents().width();
        int sh = sprite.contents().height();
        int b = SPRITE_BORDER;
        int pad = PopupGeometry.CONTAINER_PADDING;

        int x1 = x - pad;
        int y1 = y - pad;
        int w1 = w + pad * 2;
        int h1 = h + pad * 2;
        int mw = w1 - 2 * b;
        int mh = h1 - 2 * b;

        // corners (un-stretched)
        blit9(gui, atlas, sprite, x1, y1, b, b, 0, 0, b, b, sw, sh);
        blit9(gui, atlas, sprite, x1 + w1 - b, y1, b, b, sw - b, 0, sw, b, sw, sh);
        blit9(gui, atlas, sprite, x1, y1 + h1 - b, b, b, 0, sh - b, b, sh, sw, sh);
        blit9(gui, atlas, sprite, x1 + w1 - b, y1 + h1 - b, b, b, sw - b, sh - b, sw, sh, sw, sh);
        // edges (stretch on one axis)
        blit9(gui, atlas, sprite, x1 + b, y1, mw, b, b, 0, sw - b, b, sw, sh);
        blit9(gui, atlas, sprite, x1 + b, y1 + h1 - b, mw, b, b, sh - b, sw - b, sh, sw, sh);
        blit9(gui, atlas, sprite, x1, y1 + b, b, mh, 0, b, b, sh - b, sw, sh);
        blit9(gui, atlas, sprite, x1 + w1 - b, y1 + b, b, mh, sw - b, b, sw, sh - b, sw, sh);
        // middle (stretch on both axes)
        blit9(gui, atlas, sprite, x1 + b, y1 + b, mw, mh, b, b, sw - b, sh - b, sw, sh);
    }

    /** One 9-slice segment: the sprite's pixel region (su0,sv0)-(su1,sv1) mapped to
     *  the destination rect (dx,dy,dw,dh) via atlas-normalised UV coordinates. */
    private static void blit9(GuiGraphics gui, Identifier atlas, TextureAtlasSprite sprite,
                              int dx, int dy, int dw, int dh,
                              int su0, int sv0, int su1, int sv1, int sw, int sh) {
        float u0 = sprite.getU(su0 / (float) sw);
        float u1 = sprite.getU(su1 / (float) sw);
        float v0 = sprite.getV(sv0 / (float) sh);
        float v1 = sprite.getV(sv1 / (float) sh);
        gui.blit(atlas, dx, dy, dx + dw, dy + dh, u0, u1, v0, v1);
    }
}
