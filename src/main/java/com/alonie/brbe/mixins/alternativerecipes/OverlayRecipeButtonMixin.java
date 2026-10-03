package com.alonie.brbe.mixins.alternativerecipes;

import com.alonie.brbe.cache.RecipeViewerIndex;
import com.alonie.brbe.interfaces.IOverlayCellTooltip;
import com.alonie.brbe.mixins.accessors.ClientRecipeBookAccessor;
import com.alonie.brbe.mixins.accessors.OverlayRecipeButtonAccessor;
import com.alonie.brbe.mixins.accessors.OverlayRecipeComponentAccessor;
import com.alonie.brbe.pinoverlay.PinButtonRenderOverride;
import com.alonie.brbe.pinoverlay.PinOverlay;
import com.alonie.brbe.recipeviewer.engine.RecipeViewerEngine;
import com.alonie.brbe.render.PopupRenderer;
import com.alonie.brbe.util.CycleLock;
import com.alonie.brbe.util.PartialCraftingUtil;
import com.alonie.brbe.util.RecipeBookKind;
import com.alonie.brbe.util.RecipeCellTooltips;
import com.alonie.brbe.util.RecipePopupLayer;
import com.alonie.brbe.util.RecipeViewerOverlay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.recipebook.OverlayRecipeComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import com.alonie.brbe.util.BrbeLogger;


@Mixin(targets = "net.minecraft.client.gui.screens.recipebook.OverlayRecipeComponent$OverlayRecipeButton")
public abstract class OverlayRecipeButtonMixin extends AbstractWidget implements IOverlayCellTooltip {

    @Final
    @Shadow
    private boolean isCraftable;
    @Final
    @Shadow
    private RecipeDisplayId recipe;

    @Shadow
    @Final
    private List<Object> slots;

    public OverlayRecipeButtonMixin(int x, int y, int width, int height, Component message) {
        super(x, y, width, height, message);
    }

    /** Frozen pin mode while a pin renders, else the live viewer mode.  A pin
     *  must keep rendering in its creation mode even if the query viewer
     *  switches category underneath. */
    private boolean isFurnaceMode() {
        return PinButtonRenderOverride.active()
                ? PinButtonRenderOverride.isFurnace() : RecipeViewerOverlay.isFurnaceMode();
    }

    private boolean isStonecuttingMode() {
        return PinButtonRenderOverride.active()
                ? PinButtonRenderOverride.isStonecutting() : RecipeViewerOverlay.isStonecuttingMode();
    }

    private boolean isSmithingMode() {
        return PinButtonRenderOverride.active()
                ? PinButtonRenderOverride.isSmithing() : RecipeViewerOverlay.isSmithingMode();
    }

    @Inject(at = @At("HEAD"), method = "extractWidgetRenderState", cancellable = true)
    public void extractWidgetRenderState(GuiGraphicsExtractor gui, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        OverlayRecipeComponent outer = ((OverlayRecipeButtonAccessor) this).brbe$getOuterComponent();
        RecipeCollection collection = outer.getRecipeCollection();
        boolean furnaceBook = ((OverlayRecipeComponentAccessor) outer).isFurnaceMenu();
        boolean partial = computePartial(outer, collection, furnaceBook);

        boolean pin = PinButtonRenderOverride.active();
        boolean viewer = RecipeViewerIndex.isViewerCollection(collection);
        // 只有**配方书浮层**这一条渲染路径会重新填它（见方法末尾）：查询窗口 / pin 的浮层
        // 渲染过后它保持空 → 那里的格子不出 tooltip（各自有 tooltip 通路）。
        this.brbe$drawnProduct = ItemStack.EMPTY;
        boolean hover = isHoveredOrFocused() || pin;
        // Window-scoped mode / variant index: a bottom window's buttons must
        // render with ITS OWN window's category mode and Alt-pause state (the
        // old topmost-based lookups made a bottom window degrade to the
        // FOCUSED window's mode / furnace state — losing its partial red
        // overlays).  A PIN branch bypasses the window lookups entirely: the
        // pin renders with its FROZEN creation mode and its own Alt state
        // (the window-based fallback degrades to MODE_CRAFTING / raw
        // auto-index once the query viewer is closed).
        int mode;
        int selIdx;
        if (pin) {
            // The pin's raw frozen mode (anvil / brewing / grindstone included
            // — the mode() helper below only maps the three classic modes).
            mode = PinButtonRenderOverride.mode();
            selIdx = PinButtonRenderOverride.selIdx();
        } else {
            mode = viewer ? RecipeViewerOverlay.windowMode(outer) : mode();
            // 逐物品折叠锁（用户 2026-09-13 诉求 2）：只有**指针下这一个按钮**
            // 会冻结，锁定键+滚轮也只翻动它；同一界面上其余按钮照常轮换。按钮
            // 整块当作一件折叠物品（它就这么大：查询界面 24px 对象按钮 / 配方书
            // 替代配方按钮），面板里的槽位另有 PopupRenderer 逐槽位处理。配方书
            // 自己的替代配方按钮走屏幕层判定（LEI 浮层挡住指针时不判定）。
            int auto = ((OverlayRecipeComponentAccessor) outer).getSlotSelectTime().currentIndex();
            boolean onViewer = viewer || RecipeViewerIndex.isViewerCollection(collection);
            if (onViewer ? CycleLock.claim(this, getX(), getY(), width, height)
                    : CycleLock.claimScreen(this, getX(), getY(), width, height)) {
                selIdx = CycleLock.indexFor(this, auto);
            } else {
                CycleLock.release(this);
                selIdx = auto;
            }
        }
        int x = getX();
        int y = getY();
        int w = width;
        int h = height;

        // Pin: frozen hover rendering of the full recipe popup (its geometry is
        // pinned at creation; the query viewer's category can switch beneath).
        // The craftable state is read dynamically from the pin's collection
        // (refreshed on inventory change), not from the cloned button's final
        // isCraftable field, which is frozen at creation.
        if (pin) {
            PopupRenderer.renderRecipePopup(gui, this.recipe, recipeEntry(), mode,
                    collection.isCraftable(this.recipe), partial, this.slots, selIdx, x, y, w, h,
                    true, PinButtonRenderOverride.current(),
                    // 缺料遮罩只由实时库存逐槽判定（见 RecipePopupLayer 同款注释）。
                    PartialCraftingUtil.searchSpaceItemCounts(), false);
            ci.cancel();
            return;
        }

        // The popup layer is modal: a button lying under it must not stay
        // hovered (its own popup / tooltip would show through).
        AbstractWidget topPopup = RecipePopupLayer.button();
        if (hover && topPopup != null && topPopup != this
                && RecipePopupLayer.contains(mouseX, mouseY)) {
            hover = false;
        }

        // Query viewer: the button stays at its base size — hovering (without
        // Shift) swaps the backdrop to the _highlighted sprite (per craftable
        // state); the enlarged preview (Shift) is the only zoom feedback.  The
        // popup is drawn by the independent popup layer, which is triggered and
        // kept alive by RecipeViewerOverlay.  lockReveal=true: the query viewer
        // LOCKS the "只在悬停时显示微缩配方" hover-reveal design (product icon
        // until hovered, full layout on hover) — the toggle's current value
        // governs the recipe book's buttons only.
        if (viewer) {
            PopupRenderer.renderBaseButton(gui, this.recipe, recipeEntry(), mode,
                    this.isCraftable, partial, this.slots, selIdx, x, y, w, h, hover, true);
            ci.cancel();
            return;
        }

        // Recipe book（**替代配方组**浮层，用户 2026-09-25 诉求 2）：
        //  * 悬停 → 就地显示**完整配方预览**（3×3 布局 + 产物，1:1，不再弹放大界面），
        //    底板 = 原版 crafting_overlay_highlighted / _disabled_highlighted；
        //  * 未悬停 + 开启「只在悬停时显示微缩配方」→ 只画产物图标，底板 = BRBE
        //    crafting_overlay(_disabled)；
        //  * 未悬停 + 关闭该配置 → 仍显示完整配方，底板 = 原版 crafting_overlay(_disabled)。
        // 配方内容改由工作区的**幽灵物品**另外呈现（hoverghost/OverlayRecipeComponentMixin）。
        // 旧行为：悬停做 2x 放大预览；Shift 的 4x 放大已在 2026-09-13 移除。
        //
        // 2026-09-27（用户诉求）：上面这套只留给**合成类**书。格子内容按配方书种类分派
        // （util/RecipeBookKind）：熔炉类书的格子改画**材料**当展示物品（代替原版的微缩
        // 配方），熔炉类/合成类以外的书（模组自建配方书，含调用原版 API 拿到本浮层的那种）
        // 统一不画微缩配方，只画展示物品——与酿造台 / 锻造台已有行为一致。
        // 种类取自**本界面实际的配方书组件**，不再拿查询窗口的全局熔炉状态顶替
        // （旧写法在熔炉书里 mode 恒为 MODE_CRAFTING → 底面板与内容都按合成书走）。
        //
        // 顺手缓存"这一格画出来的产物"：格子 tooltip（用户 2026-09-27 诉求）在
        // RecipeBookPage.extractTooltip 里取它——那才是原版配方书 tooltip 的正规出口
        // （槽位 tooltip 早于它注册，会被它盖掉；见 RecipeBookPageOverlayTooltipMixin）。
        RecipeBookKind kind = RecipeBookKind.current(furnaceBook);
        int bookMode = kind == RecipeBookKind.FURNACE ? PinOverlay.MODE_FURNACE : PinOverlay.MODE_CRAFTING;
        this.brbe$drawnProduct = PopupRenderer.alternativesCellItem(recipeEntry(), selIdx, kind);
        PopupRenderer.renderAlternativesButton(gui, this.recipe, recipeEntry(), bookMode,
                this.isCraftable, partial, this.slots, selIdx, x, y, w, h, hover, false, kind);
        ci.cancel();
    }

    /**
     * 本帧这一格**画出来的**展示物品（{@link IOverlayCellTooltip}）：配方书浮层格子渲染时逐帧刷新
     * ——合成类 = 产物，熔炉类 = 材料，其余书 = 产物（见 {@code RecipeBookKind}）；
     * 查询窗口 / pin 的浮层里恒为空（那些通路各自有 tooltip）。
     */
    @Unique
    private ItemStack brbe$drawnProduct = ItemStack.EMPTY;

    @Nullable
    @Override
    public List<Component> brbe$cellTooltip() {
        return this.brbe$drawnProduct.isEmpty()
                ? null
                : RecipeCellTooltips.forStack(null, this.brbe$drawnProduct, false);
    }

    private boolean computePartial(OverlayRecipeComponent outer, RecipeCollection collection,
                                   boolean furnaceBook) {
        if (RecipeViewerIndex.isViewerCollection(collection)) {
            // Window-scoped furnace check: only the OWNING window's furnace
            // category suppresses partials (the old topmost-based check broke
            // a bottom window whenever the focused window was furnace — the
            // partial red overlays disappeared until the bottom window was
            // focused again).  NOTE: partial is NOT gated on !isCraftable —
            // the viewer's prepareForViewer adds partial recipes to the
            // collection's craftable set, so partial buttons report
            // isCraftable=true.
            if (RecipeViewerOverlay.windowMode(outer) == PinOverlay.MODE_FURNACE) {
                return false;
            }
            boolean snap = RecipeViewerIndex.isViewerPartial(collection, this.recipe);
            boolean stale = PartialCraftingUtil.isPartiallyCraftableEvenIfStale(collection, this.recipe);
            // [BRBE-DIAG] 一次性：真实按钮状态分解（每个 id 一次）
            if (VBTN_DIAG.add(this.recipe)) {
                RecipeDisplayEntry entry = recipeEntry();
                BrbeLogger.log("BRBE-DIAG-PARTIAL", "vbtn id=" + this.recipe
                        + " disp=" + (entry == null ? "null" : entry.display().getClass().getSimpleName())
                        + " layout=" + (entry != null && com.alonie.brbe.recipeviewer.engine.RecipeViewerEngine.getLayout(this.recipe) != null)
                        + " isCraftable=" + this.isCraftable
                        + " colC=" + collection.isCraftable(this.recipe)
                        + " partial=" + (snap || stale)
                        + " snap=" + snap + " stale=" + stale
                        + " tag=" + PartialCraftingUtil.isPartiallyCraftable(collection, this.recipe)
                        + " canCraftNow=" + PartialCraftingUtil.canCraftByRequirements(entry)
                        + " coll=" + System.identityHashCode(collection));
            }
            return snap || stale;
        }
        if (furnaceBook) return false;
        return PartialCraftingUtil.isPartiallyCraftable(collection, this.recipe);
    }

    /** [BRBE-DIAG] 真实按钮一次性日志（每个 id 一次）。 */
    private static final java.util.Set<RecipeDisplayId> VBTN_DIAG = new java.util.HashSet<>();

    private int mode() {
        if (isFurnaceMode()) return PinOverlay.MODE_FURNACE;
        if (isStonecuttingMode()) return PinOverlay.MODE_STONECUTTING;
        if (isSmithingMode()) return PinOverlay.MODE_SMITHING;
        return PinOverlay.MODE_CRAFTING;
    }

    /** The RecipeDisplayEntry backing this button (or null). */
    private RecipeDisplayEntry recipeEntry() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return null;
        RecipeDisplayEntry entry = RecipeViewerEngine.entryFor(this.recipe);
        if (entry != null) return entry;
        return ((ClientRecipeBookAccessor) mc.player.getRecipeBook())
                .brbe$getKnown().get(this.recipe);
    }
}
