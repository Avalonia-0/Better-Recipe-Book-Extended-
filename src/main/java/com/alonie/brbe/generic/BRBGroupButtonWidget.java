package com.alonie.brbe.generic;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.api.BRBBookCategories;
import com.alonie.brbe.util.ClientCompat;
import com.alonie.brbe.util.BRBTextures;
import com.alonie.brbe.widget.StateSwitchingButton;
import com.alonie.recipebookispain_extended.access.RecipeGroupButtonPlacement;
import com.alonie.recipebookispain_extended.animation.TabFlipGeometry;
import com.alonie.recipebookispain_extended.animation.TabSelectFade;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public class BRBGroupButtonWidget extends StateSwitchingButton {
    protected BRBBookCategories.Category category;
    private int iconYOffset;

    /**
     * 选中态渐变系数（0 = 未选中外观，1 = 选中外观）。
     *
     * <p>锻造台 / 酿造台配方书的标签原先是一次性硬切（换贴图 + 左移 2px）。这里与合成台
     * （RBIP 标签）用**同一条曲线**：{@link TabSelectFade} 的指数减速，两张贴图在同一个按
     * 插值左移的位置上交叉淡化，图标只画一次、偏移同步插值 —— 没有 2px 重影。
     * 同样挂在「翻页动画」总开关下（没有新增配置项）。</p>
     */
    private float selectBlend;
    /** 首帧吸附标记：新建的按钮直接落到当前状态，不给开场补一段渐变。 */
    private boolean selectBlendInit;
    /** 渐变期间「未选中形态」的裁剪框暂存（每帧一次）。 */
    private final int[] selectClip = new int[4];

    public BRBGroupButtonWidget(BRBBookCategories.Category category) {
        super(0, 0, 35, 27, false);
        this.category = category;
        this.initTextureValues(BRBTextures.RECIPE_BOOK_TAB_SPRITES);
    }

    public void extractWidgetRenderState(GuiGraphicsExtractor gui, int mouseX, int mouseY, float delta) {
        Minecraft minecraftClient = Minecraft.getInstance();
        this.advanceSelectBlend(delta);

        // 选中态整体左移 2px，按渐变插值；两张贴图放在同一个插值位置上
        int shift = TabSelectFade.shift(this.selectBlend);
        int x = getX() - shift;
        int y = this.getY();
        float alpha = this.selectBlend;
        if (alpha < 1.0F) {
            // 未选中形态：配方书盖在它上面（渐变中按书体边缘裁住，和合成台标签一致）
            boolean clip = this.beginSelectClip(gui);
            gui.blitSprite(ClientCompat.GUI_TEXTURED, this.sprites.get(true, false), x, y, this.width, this.height,
                    TabSelectFade.color(1.0F - alpha));
            if (clip) {
                gui.disableScissor();
            }
        }
        if (alpha > 0.0F) {
            // 选中形态：压在配方书上 —— 那 4 列"贴着书皮"的贴图按 alpha 渐显出来
            gui.blitSprite(ClientCompat.GUI_TEXTURED, this.sprites.get(true, true), x, y, this.width, this.height,
                    TabSelectFade.color(alpha));
        }

        this.renderIcons(gui, minecraftClient.getItemModelResolver(), shift);
    }

    /** 推进选中态渐变（与合成台标签同一条指数曲线；总开关同为「翻页动画」）。 */
    private void advanceSelectBlend(float delta) {
        float target = this.isStateTriggered ? 1.0F : 0.0F;
        if (!this.selectBlendInit) {
            this.selectBlendInit = true;
            this.selectBlend = target;
            return;
        }
        if (!selectFadeEnabled()) {
            this.selectBlend = target;
            return;
        }
        this.selectBlend = TabSelectFade.advance(this.selectBlend, target, selectFadeRate(), delta);
    }

    private static boolean selectFadeEnabled() {
        return BetterRecipeBook.config == null
                || BetterRecipeBook.config.pageAnimation.pageAnimationEnabled;
    }

    private static float selectFadeRate() {
        float duration = 0.5F;
        if (BetterRecipeBook.config != null) {
            duration = BetterRecipeBook.config.pageAnimationDuration;
        }
        return TabSelectFade.rate(duration);
    }

    /** 渐变进行中：未选中形态按书体边缘裁住（配方书永远盖在标签上面）。 */
    private boolean beginSelectClip(GuiGraphicsExtractor gui) {
        if (this.selectBlend == (this.isStateTriggered ? 1.0F : 0.0F)) {
            return false;
        }
        TabFlipGeometry.clip(this.getX(), this.getY(), RecipeGroupButtonPlacement.NORMAL, this.selectClip);
        gui.enableScissor(this.selectClip[0], this.selectClip[1], this.selectClip[2], this.selectClip[3]);
        return true;
    }

    private void renderIcons(GuiGraphicsExtractor guiGraphics, ItemModelResolver itemModelResolver, int shift) {
        List<ItemStack> list = this.category.getItemIcons();
        int i = -shift;   // 原版：选中 -2；这里按渐变插值
        int iconY = getY() + 5 + this.iconYOffset;
        if (list.size() == 1) {
            guiGraphics.fakeItem(list.get(0), getX() + 9 + i, iconY);
        } else if (list.size() == 2) {
            guiGraphics.fakeItem(list.get(0), getX() + 3 + i, iconY);
            guiGraphics.fakeItem(list.get(1), getX() + 14 + i, iconY);
        }

    }

    public void setIconYOffset(int iconYOffset) {
        this.iconYOffset = iconYOffset;
    }

    public BRBBookCategories.Category getCategory() {
        return this.category;
    }
}
