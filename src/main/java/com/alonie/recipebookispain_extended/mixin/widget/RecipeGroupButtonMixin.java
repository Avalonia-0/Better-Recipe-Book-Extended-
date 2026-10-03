package com.alonie.recipebookispain_extended.mixin.widget;

import com.alonie.brbe.interfaces.RecipeBookTabButtonIconOffset;
import com.alonie.brbe.pin.TabPinManager;
import com.alonie.brbe.util.BRBTextures;
import com.alonie.brbe.util.ClientCompat;
import com.alonie.recipebookispain_extended.RecipeBookIsPain;
import com.alonie.recipebookispain_extended.access.RecipeGroupButtonFlipAccess;
import com.alonie.recipebookispain_extended.access.RecipeGroupButtonPlacement;
import com.alonie.recipebookispain_extended.access.RecipeGroupButtonPlacementAccess;
import com.alonie.recipebookispain_extended.animation.TabFlipGeometry;
import com.alonie.recipebookispain_extended.animation.TabFlipState;
import com.alonie.recipebookispain_extended.animation.TabSelectFade;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookTabButton;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.crafting.ExtendedRecipeBookCategory;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RecipeBookTabButton.class)
public abstract class RecipeGroupButtonMixin extends ImageButton implements RecipeGroupButtonPlacementAccess, RecipeGroupButtonFlipAccess {
    @Unique private static final int RBIP_TAB_WIDTH = 35;
    @Unique private static final int RBIP_TAB_HEIGHT = 27;
    @Unique private static final int RBIP_ROTATED_TAB_WIDTH = 27;
    @Unique private static final int RBIP_ROTATED_TAB_HEIGHT = 35;
    @Unique private static final Identifier RBIP_BOTTOM_TAB = Identifier.fromNamespaceAndPath("brbe", "textures/rbip/bottom_tab.png");
    @Unique private static final Identifier RBIP_BOTTOM_TAB_SELECTED = Identifier.fromNamespaceAndPath("brbe", "textures/rbip/bottom_tab_selected.png");
    @Unique private static final Identifier RBIP_TOP_TAB = Identifier.fromNamespaceAndPath("brbe", "textures/rbip/top_tab.png");
    @Unique private static final Identifier RBIP_TOP_TAB_SELECTED = Identifier.fromNamespaceAndPath("brbe", "textures/rbip/top_tab_selected.png");

    @Unique private RecipeGroupButtonPlacement rbip$placement = RecipeGroupButtonPlacement.NORMAL;
    /** 标签栏翻页动画的单标签状态（伸展度 / 静止位 / 裁剪轴向）。 */
    @Unique private final TabFlipState rbip$flip = new TabFlipState();
    /** 选中态渐变的当前系数：0 = 未选中外观、1 = 选中外观（见 {@link TabSelectFade}）。 */
    @Unique private float rbip$selectBlend;
    /** 首帧对齐标志：按钮刚建出来时直接吸附到当前选中态，不给开场补一段渐变。 */
    @Unique private boolean rbip$selectBlendInit;
    /** 选中态渐变的裁剪框暂存（每帧每标签一次）。 */
    @Unique private final int[] rbip$selectClip = new int[4];

    @Shadow @Final private RecipeBookComponent.TabInfo tabInfo;
    @Shadow private float animationTime;
    @Shadow private boolean selected;

    @Shadow public abstract ExtendedRecipeBookCategory getCategory();

    public RecipeGroupButtonMixin(int x, int y, int width, int height, WidgetSprites sprites, Button.OnPress onPress) {
        super(x, y, width, height, sprites, onPress);
    }

    @Override
    public void rbip$setPlacement(RecipeGroupButtonPlacement placement) {
        this.rbip$placement = placement;
    }

    @Override
    public RecipeGroupButtonPlacement rbip$getPlacement() {
        return this.rbip$placement;
    }

    @Override
    public TabFlipState rbip$flipState() {
        return this.rbip$flip;
    }

    @Override
    public void rbip$beginFlipIn() {
        // 登场标签**吸附到自身当前形态**（选中标签就是选中形态）：翻页期间它被书皮裁住，
        // 看上去就是"被配方书压住"，落地后再让贴合带渐显 —— 不做整块交叉淡化
        // （交叉淡化会看到标签短暂变成未选中、还带重影，用户 2026-10-04 反馈"比较破碎"）。
        this.rbip$selectBlend = rbip$targetBlend();
        this.rbip$selectBlendInit = true;
    }

    @Inject(at = @At("HEAD"), method = "startAnimation", cancellable = true)
    private void rbip$skipCreativeTabUnlockBounce(ClientRecipeBook recipeBook, boolean filteringCraftable, CallbackInfo ci) {
        // RBIP 造的标签都不播"新配方解锁"弹跳动画：创造标签映射的标签 + 扩展档
        // （命名空间 / 数据包）的全部标签（后者没有代表创造标签，toItemGroup 为 null，必须单独判）。
        if (RecipeBookIsPain.isExtendedTabGroup(this.getCategory())
                || RecipeBookIsPain.toItemGroup(this.getCategory()) != null) {
            ci.cancel();
        }
    }

    /**
     * 标签本体的自绘入口（取代原版 {@code renderContents}）：
     *
     * <ul>
     *   <li><b>旋转条带（上/下侧）</b>：始终自绘（90° 旋转矩阵 + BRBE 自己的贴图）；</li>
     *   <li><b>正常朝向</b>：只在<b>选中态渐变进行中</b>接管 —— 交叉渐变两张贴图 + 图标按插值
     *       偏移绘制；渐变停在端点时原样交回原版（逐像素一致，不改变静止观感）。</li>
     * </ul>
     *
     * <p>渐变的推进也放在这里：只有"正在渲染的标签"才需要推进，而端点状态无需推进。</p>
     */
    @Inject(at = @At("HEAD"), method = "renderContents", cancellable = true)
    private void rbip$drawTabContents(GuiGraphics context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!this.rbip$selectBlendInit) {
            this.rbip$selectBlendInit = true;
            this.rbip$selectBlend = this.selected ? 1.0F : 0.0F;
        }

        if (this.rbip$placement == RecipeGroupButtonPlacement.NORMAL) {
            // 弹跳动画留在原版路径（自绘会把那段挤压姿态丢掉），其余按需接管。
            // ⚠️ 停靠渐显窗口必须放行：那一刻 blend 已经等于 target，若在此提前返回，
            //    固定的停靠窗口就永远显不出来 —— 表现就是"静止瞬间闪到书皮上"。
            if (this.animationTime > 0.0F
                    || (this.rbip$selectBlend == this.rbip$targetBlend() && !rbip$revealsDock())) {
                return;
            }
            this.rbip$advanceSelectBlend(delta);
            this.rbip$drawNormalTabContents(context);
            ci.cancel();
            return;
        }

        this.rbip$advanceSelectBlend(delta);

        boolean pushed = false;
        if (this.animationTime > 0.0F) {
            // ⚠️ 这一段会给 pose 叠一个缩放：而 enableScissor 会按当前 pose 变换裁剪框。
            //    RBIP 标签的「新配方解锁弹跳」已被 rbip$skipCreativeTabUnlockBounce 取消，
            //    所以 animationTime 恒为 0、这里不会执行；若将来放开弹跳，必须同时把
            //    旋转条带的裁剪改成"在单位矩阵下设置"（见 rbip$drawRotatedBackground）。
            float scale = 1.0F + 0.1F * (float) Math.sin(this.animationTime / 15.0F * (float) Math.PI);
            context.pose().pushMatrix();
            context.pose().translate(this.getX() + 8, this.getY() + 12);
            context.pose().scale(1.0F, scale);
            context.pose().translate(-(this.getX() + 8), -(this.getY() + 12));
            pushed = true;
        }

        this.rbip$drawRotatedBackground(context);
        this.rbip$renderIconsAt(context, this.rbip$getRotatedIconX(), this.rbip$getRotatedIconY());
        this.rbip$drawTabPin(context);

        if (pushed) {
            context.pose().popMatrix();
            this.animationTime -= delta;
        }

        ci.cancel();
    }

    @Inject(at = @At("HEAD"), method = "renderContents", cancellable = true)
    private void rbip$render(GuiGraphics context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        CreativeModeTab group = RecipeBookIsPain.toItemGroup(this.getCategory());
        if (group == null) return;

        int i = -TabSelectFade.shift(this.rbip$selectBlend);   // 原版是 selected ? -2 : 0

        // 原版（vanilla 包 / minecraft 命名空间）标签固定用草方块图标，不走 owo 的创造标签渲染。
        if (RecipeBookIsPain.isOwOLoaded
                && !RecipeBookIsPain.isVanillaTabGroup(this.getCategory())) {
            if (RecipeBookIsPain.rbip$renderOwo(context, i, (RecipeBookTabButton) (Object) this, group)) {
                ci.cancel();
            }
        }
    }

    /** 固定标签标记：固定了的创造标签画 pin 图标（最上层，图标之后，
     *  32x32 与配方按钮的 pin 一致，图形悬出标签边缘）。正常朝向与上侧标签
     *  的 pin 悬在标签左上角，下侧标签的 pin 悬在左下角；旋转条带在 90°
     *  旋转矩阵之外按最终屏幕坐标绘制，位置即最终呈现位置。正常朝向的标签
     *  借 renderContents RETURN 绘制；旋转条带上的标签由 renderContents
     *  HEAD 注入接管（正常体被取消），在 rbip$drawTabContents 内补画。 */
    @Inject(at = @At("RETURN"), method = "renderContents")
    private void rbip$drawPinMarker(GuiGraphics context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (this.rbip$placement != RecipeGroupButtonPlacement.NORMAL) return;
        this.rbip$drawTabPin(context);
    }

    @Unique
    private void rbip$drawTabPin(GuiGraphics context) {
        // pin 键与「固定/取消固定」用同一套（见 RecipeBookIsPain.extendedPinKey）：
        // 扩展档（命名空间 / 数据包）标签没有创造标签，键是命名空间 / 包 id。
        String pinKey = RecipeBookIsPain.extendedPinKey(this.getCategory());
        if (pinKey == null) {
            CreativeModeTab group = RecipeBookIsPain.toItemGroup(this.getCategory());
            Identifier tabId = group == null ? null
                    : BuiltInRegistries.CREATIVE_MODE_TAB.getKey(group);
            pinKey = tabId == null ? null : tabId.toString();
        }
        if (pinKey == null || !TabPinManager.isPinnedKey(pinKey)) return;
        // pin 图形位于 32x32 精灵图左上 (6,2)-(12,7)。上侧 pin 锚点 (x-1, y-4)
        // （左上角，悬出标签顶边 2px，x-4 基础上右移 3px）；下侧 pin 同 x 但
        // y+6（左上角下移 6px，用户要求）；正常朝向锚点 (x-4, y-4)。
        // 均为最终屏幕位置（不随旋转矩阵变换）。
        // 选中时 pin 随标签选中偏移移动：左/上/下侧分别向左/上/下移动 1px。
        int pinX = this.getX() - 4;
        int pinY = this.getY() - 4;
        if (this.rbip$placement == RecipeGroupButtonPlacement.TOP
                || this.rbip$placement == RecipeGroupButtonPlacement.BOTTOM) {
            pinX += 3;
        }
        if (this.rbip$placement == RecipeGroupButtonPlacement.BOTTOM) {
            pinY += 6;
        }
        if (this.selected) {
            if (this.rbip$placement == RecipeGroupButtonPlacement.NORMAL) {
                pinX--;
            } else if (this.rbip$placement == RecipeGroupButtonPlacement.TOP) {
                pinY--;
            } else {
                pinY++;
            }
        }
        ClientCompat.blitSprite(context, BRBTextures.RECIPE_BOOK_PIN_SPRITE,
                pinX, pinY, 32, 32);
    }

    /**
     * 裁剪基准 x：翻页动画期间标签被临时挪到动画位置，而**书皮边缘固定在静止位**
     * （{@code baseX + bookEdgeOffset}）—— 此时必须用静止位做基准，否则停靠渐显里
     * "哪一块被书皮盖着"会跟着标签一起漂。
     */
    @Unique
    private int rbip$clipBaseX() {
        return this.rbip$flip.tracked ? this.rbip$flip.baseX : this.getX();
    }

    /** 裁剪基准 y（旋转条带同理，见 {@link #rbip$clipBaseX()}）。 */
    @Unique
    private int rbip$clipBaseY() {
        return this.rbip$flip.tracked ? this.rbip$flip.baseY : this.getY();
    }

    /**
     * 本体裁剪：按"书皮那条边"裁住标签（= 被配方书压住）。停靠渐显窗口内也必须裁。
     *
     * <p>⚠️ 必须在**单位矩阵**下调用：{@code enableScissor} 会按当前 pose 变换裁剪框，
     * 在旋转/缩放的 pose 里设裁剪会把裁剪框转到错位置（见 {@link #rbip$drawRotatedBackground}）。</p>
     */
    @Unique
    private void rbip$beginBodyClip(GuiGraphics context) {
        TabFlipGeometry.clip(this.rbip$clipBaseX(), this.rbip$clipBaseY(), this.rbip$placement, this.rbip$selectClip);
        context.enableScissor(this.rbip$selectClip[0], this.rbip$selectClip[1],
                this.rbip$selectClip[2], this.rbip$selectClip[3]);
    }

    /**
     * 停靠窗口裁剪：**固定在最终停靠位置**的那一条（正常朝向 = 右端 3×27，上/下侧 = 对应的
     * 几行）—— 从书皮那条边到标签**静止位**的外缘，全程不动。
     *
     * <p>⚠️ 必须用静止位算，不能跟着停靠中的标签走（用户 2026-10-04 指出）：窗口固定，标签从
     * 它下面滑过去，窗口里显示的就是标签此刻在这几个像素上的真实内容；滑到静止位时窗口里恰好
     * 就是最终的贴合条，天然无接缝。"渐显"= 书皮边在停靠过程中逐渐透出标签的最终停靠区。</p>
     */
    @Unique
    private void rbip$beginMergeStripClip(GuiGraphics context) {
        int bx = this.rbip$clipBaseX();
        int by = this.rbip$clipBaseY();
        TabFlipGeometry.clip(bx, by, this.rbip$placement, this.rbip$selectClip);
        int w = TabFlipGeometry.width(this.rbip$placement);
        int h = TabFlipGeometry.height(this.rbip$placement);
        switch (this.rbip$placement) {
            case NORMAL -> context.enableScissor(this.rbip$selectClip[2], by, bx + w, by + h);
            case TOP -> context.enableScissor(bx, this.rbip$selectClip[3], bx + w, by + h);
            case BOTTOM -> context.enableScissor(bx, by, bx + w, this.rbip$selectClip[1]);
        }
    }

    /** 按 90° 旋转把标签贴图摆到 (x, y)（正常朝向不走这里）。 */
    @Unique
    private void rbip$pushRotatedMatrix(GuiGraphics context, int x, int y) {
        context.pose().pushMatrix();
        if (this.rbip$placement == RecipeGroupButtonPlacement.BOTTOM) {
            context.pose().translate(x, y + RBIP_ROTATED_TAB_HEIGHT);
            context.pose().rotate(-(float) Math.PI / 2.0F);
        } else {
            context.pose().translate(x + RBIP_ROTATED_TAB_WIDTH, y);
            context.pose().rotate((float) Math.PI / 2.0F);
        }
    }

    /** 旋转条带贴图：选中/取消选中同样是交叉渐变（位置取按插值左移后的 localX）。 */
    @Unique
    private void rbip$drawRotatedBackground(GuiGraphics context) {
        // ⚠️ **裁剪必须在摆旋转矩阵之前设好**（即在单位矩阵下调用 enableScissor）：
        //    26.3 的 `GuiGraphics.enableScissor` 会用**当前 pose 变换裁剪框**
        //    （`ScreenRectangle.transformAxisAligned(this.pose)`，绘制时不再变换），
        //    在 90° 旋转矩阵里设裁剪会把裁剪框整个转到不相干的位置 —— 上下侧标签在
        //    "取消选中"渐变中会因为裁剪框错位而整块消失（用户 2026-10-04 实测）。
        int localX = -TabSelectFade.shift(this.rbip$selectBlend);
        float mergeFade = rbip$mergeFade();
        if (mergeFade < 1.0F) {
            // 停靠渐显：本体（动画位置，裁在书皮边缘；含图标、pin）+ 固定窗口（静止位贴图）
            rbip$beginBodyClip(context);
            rbip$pushRotatedMatrix(context, this.getX(), this.getY());
            this.rbip$blitRotated(context, this.rbip$getRotatedTexture(true), localX, TabSelectFade.color(1.0F));
            this.rbip$renderIconsAt(context, this.rbip$getRotatedIconX(), this.rbip$getRotatedIconY());
            this.rbip$drawTabPin(context);
            context.pose().popMatrix();
            context.disableScissor();

            rbip$beginMergeStripClip(context);
            rbip$pushRotatedMatrix(context, this.rbip$clipBaseX(), this.rbip$clipBaseY());
            this.rbip$blitRotated(context, this.rbip$getRotatedTexture(true), localX, TabSelectFade.color(mergeFade));
            context.pose().popMatrix();
            context.disableScissor();
            return;
        }

        float alpha = this.rbip$selectBlend;
        if (alpha < 1.0F) {
            // 未选中形态：配方书盖在它上面
            boolean clip = rbip$beginSelectClip(context);
            rbip$pushRotatedMatrix(context, this.getX(), this.getY());
            this.rbip$blitRotated(context, this.rbip$getRotatedTexture(false), localX, TabSelectFade.color(1.0F - alpha));
            context.pose().popMatrix();
            if (clip) {
                context.disableScissor();
            }
        }
        if (alpha > 0.0F) {
            // 选中形态：压在配方书上（理由同正常朝向）
            rbip$pushRotatedMatrix(context, this.getX(), this.getY());
            this.rbip$blitRotated(context, this.rbip$getRotatedTexture(true), localX, TabSelectFade.color(alpha));
            context.pose().popMatrix();
        }
    }

    /** BRBE 的 rbip 贴图走直连纹理（不在图集里），原版贴图走 sprite —— 两种都带 alpha 着色。 */
    @Unique
    private void rbip$blitRotated(GuiGraphics context, Identifier texture, int localX, int color) {
        if (texture.getPath().startsWith("textures/")) {
            context.blit(RenderPipelines.GUI_TEXTURED, texture, localX, 0, 0.0F, 0.0F,
                    RBIP_TAB_WIDTH, RBIP_TAB_HEIGHT, RBIP_TAB_WIDTH, RBIP_TAB_HEIGHT, color);
        } else {
            context.blitSprite(RenderPipelines.GUI_TEXTURED, texture, localX, 0,
                    RBIP_TAB_WIDTH, RBIP_TAB_HEIGHT, color);
        }
    }

    @Unique
    private Identifier rbip$getRotatedTexture(boolean selectedState) {
        if (this.rbip$placement == RecipeGroupButtonPlacement.TOP) {
            return selectedState ? RBIP_TOP_TAB_SELECTED : RBIP_TOP_TAB;
        }
        if (this.rbip$placement == RecipeGroupButtonPlacement.BOTTOM) {
            return selectedState ? RBIP_BOTTOM_TAB_SELECTED : RBIP_BOTTOM_TAB;
        }
        return this.sprites.get(true, selectedState);
    }

    @Unique
    private void rbip$renderIconsAt(GuiGraphics context, int x, int y) {
        CreativeModeTab group = RecipeBookIsPain.toItemGroup(this.getCategory());
        if (group != null && RecipeBookIsPain.isOwOLoaded
                && !RecipeBookIsPain.isVanillaTabGroup(this.getCategory())
                && RecipeBookIsPain.rbip$renderOwo(context, x, y, group)) {
            return;
        }

        if (this.tabInfo.secondaryIcon().isPresent()) {
            context.renderFakeItem(this.tabInfo.primaryIcon(), this.getX(), y);
            context.renderFakeItem(this.tabInfo.secondaryIcon().get(), this.getX() + 11, y);
        } else {
            context.renderFakeItem(this.tabInfo.primaryIcon(), x, y);
        }
    }

    @Unique
    private int rbip$getRotatedIconX() {
        boolean top = this.rbip$placement == RecipeGroupButtonPlacement.TOP;
        int offset = top ? 1 : 0;
        if (top && ClientCompat.hasSpriteResource(BRBTextures.RECIPE_BOOK_BUTTON_SLOT_PARTIAL_SPRITE)) {
            offset -= 1; // unique dark 兼容包：顶部 tab 图标左移 1px
        }
        return this.getX() + (RBIP_ROTATED_TAB_WIDTH - 16) / 2 + offset;
    }

    @Unique
    private int rbip$getRotatedIconY() {
        int y = this.getY() + (RBIP_ROTATED_TAB_HEIGHT - 16) / 2;
        int shift = TabSelectFade.shift(this.rbip$selectBlend);   // 原版是 selected ? 2 : 0
        if (this.rbip$placement == RecipeGroupButtonPlacement.TOP) {
            return y - 1 - shift;
        }
        if (this.rbip$placement == RecipeGroupButtonPlacement.BOTTOM) {
            return y + 1 + shift;
        }
        return y;
    }

    // ── 选中 / 取消选中的渐变过渡 ────────────────────────────────

    /** 渐变的速率（1/秒）；总开关就是「翻页动画」。 */
    @Unique
    private static float rbip$selectFadeRate() {
        float duration = 0.5F;
        if (com.alonie.brbe.BetterRecipeBook.config != null) {
            duration = com.alonie.brbe.BetterRecipeBook.config.pageAnimationDuration;
        }
        return TabSelectFade.rate(duration);
    }

    @Unique
    private static boolean rbip$selectFadeEnabled() {
        return com.alonie.brbe.BetterRecipeBook.config == null
                || com.alonie.brbe.BetterRecipeBook.config.pageAnimation.pageAnimationEnabled;
    }

    /** 当前该朝向的端点：选中 = 1、未选中 = 0。 */
    @Unique
    private float rbip$targetBlend() {
        return this.selected ? 1.0F : 0.0F;
    }

    /**
     * 推进选中态渐变 / 翻页落地后的贴合带渐显。
     *
     * <ul>
     *   <li><b>翻页期间</b>（{@code tracked}）：<b>保持自身形态</b> —— 选中标签全程是选中形态，
     *       只是整块被书皮裁住（"配方书始终盖在移动的标签上面"）。早先用
     *       {@code min(blend, extend)} 让选中形态随伸出涨满，落地时已经是 1，**没有任何渐变可放**，
     *       表现就是静止瞬间"啪"地贴回书皮（用户第五~八轮反复反馈）。</li>
     *   <li><b>停靠窗口</b>（{@link #rbip$revealsDock()}）：<b>绝不改动 blend</b> —— 本体、
     *       图标一帧都不变；固定在最终停靠位置的那条裁切窗口按剩余位移
     *       （{@link TabSelectFade#dockReveal}）渐显，于是"缓慢停靠"与"渐变过渡"同时进行。</li>
     *   <li><b>其余</b>：点击切换那条跟手的指数曲线。</li>
     * </ul>
     *
     * <p>⚠️ 试过让移动中的标签放开裁剪（选中形态直接压到书皮上）：副作用是本体与图标
     * 会以半透明闪现在书皮上 —— 已回退，<b>不要在翻页期间放开移动标签的裁剪</b>。</p>
     */
    /**
     * 是否处于「停靠渐显」窗口：**登场**（target = 1）且**选中**、且离静止位 ≤
     * {@link TabSelectFade#DOCK_REVEAL_DISTANCE} 像素。
     *
     * <p>只有这个标签需要那最后一段过渡：翻页中它全程是选中形态、被书皮压住，进入窗口后
     * **固定在最终停靠位置**的那条裁切窗口按剩余位移渐显 —— 缓慢停靠与渐变过渡同时进行
     * （用户 2026-10-04 提议）。其余标签（退场的、未选中的）照旧整块被书皮压住。</p>
     */
    @Override
    public boolean rbip$revealsDock() {
        return this.rbip$flip.tracked && this.selected && this.rbip$flip.target > 0.5F
                && TabFlipGeometry.shift(this.rbip$flip) <= TabSelectFade.DOCK_REVEAL_DISTANCE;
    }

    /** 停靠窗口的渐显系数（窗口外恒为 1 = 照常绘制）。 */
    @Unique
    private float rbip$mergeFade() {
        if (!rbip$revealsDock()) {
            return 1.0F;
        }
        return TabSelectFade.dockReveal(TabFlipGeometry.shift(this.rbip$flip));
    }

    @Unique
    private void rbip$advanceSelectBlend(float delta) {
        if (this.rbip$flip.tracked) {
            // 翻页期间：保持自身形态（选中标签全程是选中形态，只是被书皮裁住）。
            // 不再用 extend 去夹 blend —— 那会让选中形态跟着伸出一起涨满，落地时就没有渐变可放了。
            this.rbip$selectBlend = rbip$targetBlend();
            return;
        }
        float target = this.rbip$targetBlend();
        if (!rbip$selectFadeEnabled()) {
            this.rbip$selectBlend = target;
            return;
        }
        this.rbip$selectBlend = TabSelectFade.advance(this.rbip$selectBlend, target, rbip$selectFadeRate(), delta);
    }

    /**
     * 正常朝向标签的渐变绘制：两张贴图在<b>同一个</b>（按插值左移的）位置上各按
     * {@code 1-blend} / {@code blend} 的透明度叠画，图标只画一次、偏移同步插值 ——
     * 所以 2px 的选中位移不会留下重影。
     */
    @Unique
    private void rbip$drawNormalTabContents(GuiGraphics context) {
        int shift = TabSelectFade.shift(this.rbip$selectBlend);
        int x = this.getX() - shift;
        int y = this.getY();
        float mergeFade = rbip$mergeFade();
        if (mergeFade < 1.0F) {
            // 停靠渐显窗口：选中形态整块画 —— ① 本体（含图标、pin）按书皮边缘裁住
            // （= 仍被配方书压住；此刻标签还在移动，图标右缘可能越过书皮边，必须一起裁），
            // ② 只有越过书皮的那一条按剩余位移渐显（"缓慢停靠 + 渐变过渡"同时进行）。
            rbip$beginBodyClip(context);
            context.blitSprite(RenderPipelines.GUI_TEXTURED, this.sprites.get(true, true), x, y, this.width, this.height,
                    TabSelectFade.color(1.0F));
            this.rbip$renderNormalIcons(context, shift);
            this.rbip$drawTabPin(context);
            context.disableScissor();
            // 停靠窗口里画的是**标签静止位的这一段贴图本身**（内容与窗口都不动）：
            // 标签从它下面滑过去，滑到位时窗口里的内容与本体严丝合缝拼成完整的选中形态。
            rbip$beginMergeStripClip(context);
            context.blitSprite(RenderPipelines.GUI_TEXTURED, this.sprites.get(true, true),
                    this.rbip$clipBaseX() - shift, this.rbip$clipBaseY(), this.width, this.height,
                    TabSelectFade.color(mergeFade));
            context.disableScissor();
            return;
        } else {
            float alpha = this.rbip$selectBlend;
            if (alpha < 1.0F) {
                // 未选中形态：配方书盖在它上面
                boolean clip = rbip$beginSelectClip(context);
                context.blitSprite(RenderPipelines.GUI_TEXTURED, this.sprites.get(true, false), x, y, this.width, this.height,
                        TabSelectFade.color(1.0F - alpha));
                if (clip) {
                    context.disableScissor();
                }
            }
            if (alpha > 0.0F) {
                // 选中形态：**压在配方书上**（那 5 列"贴着书皮"的贴图本就在书体范围内），
                // 按插值 alpha 淡入淡出 —— 书皮上出现的永远是「选中形态 × 当前 alpha」。
                context.blitSprite(RenderPipelines.GUI_TEXTURED, this.sprites.get(true, true), x, y, this.width, this.height,
                        TabSelectFade.color(alpha));
            }
        }
        this.rbip$renderNormalIcons(context, shift);
        this.rbip$drawTabPin(context);
    }

    /**
     * 选中态渐变期间的裁剪：**配方书盖住「未选中」那张贴图**。
     *
     * <p>两条要求其实不冲突 —— 两张贴图在书体那一侧并不占同一批像素：未选中贴图的
     * 第 30–34 列是全透明的，只有选中贴图在那几列不透明（那段"贴着书皮"的形态）。
     * 所以这里只裁<b>未选中形态</b>：书皮上出现的永远是「选中形态 × 当前 alpha」，
     * 未选中形态一个像素也上不去书体；两者各自淡入淡出，互不打架。</p>
     *
     * <p>图标与固定标记不裁：它们完全落在标签本体之内，裁了反而会让下侧标签的 pin
     * 缺掉压进书体的那 1px。</p>
     *
     * <p>翻页动画期间由 {@code RecipeBookWidgetMixin} 在外面套了整块裁剪（标签在平移、
     * 位置不是静止位），这里也就不会走到。</p>
     *
     * @return 是否套了裁剪（true 时调用方负责 {@code disableScissor}）
     */
    @Unique
    private boolean rbip$beginSelectClip(GuiGraphics context) {
        // ⚠️ 同样必须在单位矩阵下调用（enableScissor 会按 pose 变换裁剪框）。
        // 翻页期间由 RecipeBookWidgetMixin 在外面套了整块裁剪，这里不重复套；
        // 但**停靠渐显窗口**里外层不套（要留出压书皮的那一条），这里的裁剪就必须生效；
        // 静止端点则原样交回原版观感。
        boolean outerClip = this.rbip$flip.tracked && !rbip$revealsDock();
        if (outerClip || this.rbip$selectBlend == this.rbip$targetBlend()) {
            return false;
        }
        TabFlipGeometry.clip(this.rbip$clipBaseX(), this.rbip$clipBaseY(), this.rbip$placement, this.rbip$selectClip);
        context.enableScissor(this.rbip$selectClip[0], this.rbip$selectClip[1],
                this.rbip$selectClip[2], this.rbip$selectClip[3]);
        return true;
    }

    /**
     * 正常朝向的图标：位置与原版 {@code renderIcon} 一致（单图标 +9、双图标 +3/+14，
     * y 都再带 {@code RecipeBookTabButtonIconOffset} 的 ±1px 微调），X 偏移按插值。
     *
     * <p>那个 Y 微调是 BRBE 另一个 mixin 通过 {@code @ModifyArg} 加在原版 {@code renderIcon}
     * 里的；渐变接管取消了原方法、自己画图标，所以必须自己补上 —— 否则首个可见标签
     * （搜索标签）会在过渡期间相对静止态下沉 1px（用户 2026-10-03 反馈）。</p>
     */
    @Unique
    private void rbip$renderNormalIcons(GuiGraphics context, int shift) {
        int move = -shift;
        int iconY = this.getY() + 5 + ((RecipeBookTabButtonIconOffset) (Object) this).brbe$getIconYOffset();
        CreativeModeTab group = RecipeBookIsPain.toItemGroup(this.getCategory());
        if (group != null && RecipeBookIsPain.isOwOLoaded
                && !RecipeBookIsPain.isVanillaTabGroup(this.getCategory())
                && RecipeBookIsPain.rbip$renderOwo(context, move, (RecipeBookTabButton) (Object) this, group)) {
            return;
        }
        if (this.tabInfo.secondaryIcon().isPresent()) {
            context.renderFakeItem(this.tabInfo.primaryIcon(), this.getX() + 3 + move, iconY);
            context.renderFakeItem(this.tabInfo.secondaryIcon().get(), this.getX() + 14 + move, iconY);
        } else {
            context.renderFakeItem(this.tabInfo.primaryIcon(), this.getX() + 9 + move, iconY);
        }
    }
}
