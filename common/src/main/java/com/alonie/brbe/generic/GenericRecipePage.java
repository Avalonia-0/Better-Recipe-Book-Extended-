package com.alonie.brbe.generic;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.api.BRBBookCategories;
import com.alonie.brbe.util.ClientCompat;
import com.alonie.brbe.util.BRBTextures;
import com.alonie.brbe.widget.StateSwitchingButton;
import net.minecraft.client.Minecraft;
import com.alonie.brbe.widget.StateSwitchingButton;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import com.alonie.brbe.widget.StateSwitchingButton;
import net.minecraft.core.RegistryAccess;
import com.alonie.brbe.widget.StateSwitchingButton;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Supplier;

public class GenericRecipePage<M extends AbstractContainerMenu, C extends GenericRecipeBookCollection<R, M>, R extends GenericRecipe> {
    protected final RegistryAccess registryAccess;
    protected M menu;
    protected Minecraft minecraft;
    protected int parentLeft;
    protected int parentTop;
    protected StateSwitchingButton forwardButton;
    protected StateSwitchingButton backButton;
    protected List<C> recipeCollections = ImmutableList.of();
    protected C lastClickedRecipeCollection;
    protected R lastClickedRecipe;
    protected BRBBookCategories.Category category;
    protected int totalPages;
    protected int currentPage;
    public final List<GenericRecipeButton<C, R, M>> buttons = Lists.newArrayListWithCapacity(20);
    protected GenericRecipeButton<C, R, M> hoveredButton;

    // 翻页动画：整页平滑滑动 + 内容区 scissor 视窗。
    // visualPage（浮点视觉页）朝 currentPage 平滑逼近：单页动画由配置时长控制，
    // 连续翻页（连点）进入追逐延展模式——滑动连贯不逐页独立、越远越快、末端指数停靠。
    private static final float PAGE_ANIM_DURATION_FALLBACK = 0.1F;
    private static final float TICKS_PER_SECOND = 20.0F;
    private static final int PAGE_SLIDE_DISTANCE = 125;
    /** 连续翻页判定窗口：距上次翻页不足该帧数视为连点（进入追逐延展）。 */
    private static final int CHASE_WINDOW_FRAMES = 10;
    /** 追逐模式下每页响应时长（秒），独立于配置时长，保证连点滑动连贯。 */
    private static final float CHASE_PAGE_DURATION = 0.25F;
    private static final float SNAP_THRESHOLD = 0.002F;
    private float visualPage;
    private boolean animActive;
    private boolean animChase;
    private int frameCounter;
    private int lastFlipFrame = -100;

    public GenericRecipePage(RegistryAccess registryAccess, Supplier<GenericRecipeButton<C, R, M>> recipeButtonSupplier) {
        this.registryAccess = registryAccess;

        for (int i = 0; i < 20; ++i) {
            this.buttons.add(recipeButtonSupplier.get());
        }
    }

    protected void initialize(Minecraft client, int parentLeft, int parentTop, M menu, int leftOffset) {
        // 窗口尺寸变化/重新打开触发重新布局：重置动画，避免配方区渲染到旧位置
        this.animActive = false;
        this.animChase = false;
        this.visualPage = this.currentPage;
        this.lastFlipFrame = -100;
        this.frameCounter = 0;

        this.minecraft = client;
        this.menu = menu;

        this.parentLeft = parentLeft;
        this.parentTop = parentTop;

        this.forwardButton = new StateSwitchingButton(parentLeft + 93, parentTop + 137, 12, 17, false);
        this.forwardButton.initTextureValues(BRBTextures.RECIPE_BOOK_PAGE_FORWARD_SPRITES);
        this.forwardButton.active = true;
        this.backButton = new StateSwitchingButton(parentLeft + 38, parentTop + 137, 12, 17, false);
        this.backButton.initTextureValues(BRBTextures.RECIPE_BOOK_PAGE_BACKWARD_SPRITES);
        this.backButton.active = true;

        for (int k = 0; k < this.buttons.size(); ++k) {
            this.buttons.get(k).setPosition(parentLeft + 11 + 25 * (k % 5), parentTop + 31 + 25 * (k / 5));
        }
    }

    protected boolean overlayMouseClicked(double mouseX, double mouseY, int button, int j, int k, int l, int m) {
        return false;
    }

    protected void initOverlay(C recipeCollection, int x, int y, RegistryAccess registryAccess) {
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button, int j, int k, int l, int m) {
        this.lastClickedRecipe = null;
        this.lastClickedRecipeCollection = null;

        if (overlayIsVisible() && overlayMouseClicked(mouseX, mouseY, button, j, k, l, m)) {
            return true;
        }

        if (this.forwardButton.mouseClicked(mouseX, mouseY, button)) {
            int target = currentPage + 1;
            if (target >= totalPages) {
                target = BetterRecipeBook.config.scrolling.scrollAround ? 0 : totalPages - 1;
            }
            this.flipTo(target);
            return true;
        } else if (this.backButton.mouseClicked(mouseX, mouseY, button)) {
            int target = currentPage - 1;
            if (target < 0) {
                target = BetterRecipeBook.config.scrolling.scrollAround ? totalPages - 1 : 0;
            }
            this.flipTo(target);
            return true;
        } else {
            for (GenericRecipeButton<C, R, M> recipeButton : this.buttons) {
                if (!ClientCompat.mouseClicked(recipeButton, mouseX, mouseY, button)) continue;
                if (button == 0) {
                    this.lastClickedRecipe = recipeButton.getCurrentDisplayedRecipe();
                    this.lastClickedRecipeCollection = recipeButton.getCollection();
                } else if (button == 1 && !overlayIsVisible() && !recipeButton.isOnlyOption()) {
                    this.initOverlay(recipeButton.getCollection(), this.parentLeft, this.parentTop, registryAccess);
                }
                return true;
            }
        }
        return false;
    }

    public void updateButtonsForPage() {
        int i = 20 * this.currentPage;

        for (int j = 0; j < this.buttons.size(); ++j) {
            var button = this.buttons.get(j);
            if (i + j < this.recipeCollections.size()) {
                C output = this.recipeCollections.get(i + j);
                button.showCollection(output, menu, this.category);
                button.visible = true;
            } else {
                button.visible = false;
            }
        }

        this.updateArrowButtons();
    }

    protected boolean overlayIsVisible() {
        return false;
    }

    protected void render(GuiGraphicsExtractor gui, int blitX, int blitY, int mouseX, int mouseY, float delta) {
        // Guard: if initialize() was never called, all fields are null
        if (this.backButton == null || this.forwardButton == null || this.buttons == null) return;

        this.frameCounter++;

        if (BetterRecipeBook.queuedScroll != 0) {
            if (isMouseOverRecipeBookPage(mouseX, mouseY, blitX, blitY) && totalPages > 1) {
                int target = currentPage + BetterRecipeBook.queuedScroll;
                if (target >= totalPages) {
                    target = BetterRecipeBook.config.scrolling.scrollAround ? target % totalPages : totalPages - 1;
                } else if (target < 0) {
                    // required as % is not modulus, it is remainder. we need to force output positive by((target % totalPages) + totalPages)
                    target = BetterRecipeBook.config.scrolling.scrollAround ? (target % totalPages) + totalPages : 0;
                }

                this.flipTo(target);
            }
            BetterRecipeBook.queuedScroll = 0;
        }

        if (this.totalPages > 1) {
            String string = this.currentPage + 1 + "/" + this.totalPages;
            int width = this.minecraft.font.width(string);
            gui.text(this.minecraft.font, string, blitX - width / 2 + 73, blitY + 141, -1, false);
        }

        this.hoveredButton = null;

        boolean animating = this.animActive;
        if (animating) {
            float deltaS = delta / TICKS_PER_SECOND;
            float remaining = this.currentPage - this.visualPage;
            float absd = Math.abs(remaining);
            if (absd < SNAP_THRESHOLD) {
                this.visualPage = this.currentPage;
                this.animActive = false;
                this.animChase = false;
                animating = false;
            } else {
                // 统一速度曲线：追逐只在远离目标（absd≥1）时用快 rate 追赶；一旦进入
                // 最后一页（absd<1）即与单页共用同一条指数减速曲线，减速弧线完全一致。
                float base = (this.animChase && absd >= 1.0F) ? 6.2F / CHASE_PAGE_DURATION : 6.2F / this.pageAnimDuration();
                float fraction = 1.0F - (float) Math.exp(-base * deltaS);
                float cap = 0.45F + (float) Math.sqrt(absd) * 0.12F;
                float move = Math.min(Math.min(absd * fraction, cap), absd);
                this.visualPage += Math.signum(remaining) * move;
            }
        }

        if (animating) {
            int basePage = (int) Math.floor(this.visualPage);
            float frac = this.visualPage - basePage;
            int interactPage = Math.round(this.visualPage);
            // 方案三：滑动内容全宽渲染（11..136），配方滑到边缘时纹理延伸至边界并被整齐切边
            gui.enableScissor(blitX + 11, blitY + 31, blitX + 136, blitY + 156);
            this.renderButtonGrid(gui, mouseX, mouseY, delta, basePage, Math.round(-frac * PAGE_SLIDE_DISTANCE), interactPage == basePage);
            this.renderButtonGrid(gui, mouseX, mouseY, delta, basePage + 1, Math.round((1.0F - frac) * PAGE_SLIDE_DISTANCE), interactPage == basePage + 1);
            gui.disableScissor();
        } else {
            this.renderButtonGrid(gui, mouseX, mouseY, delta, this.currentPage, 0, true);
        }

        this.backButton.extractRenderState(gui, mouseX, mouseY, delta);
        this.forwardButton.extractRenderState(gui, mouseX, mouseY, delta);
    }

    private void renderButtonGrid(GuiGraphicsExtractor gui, int mouseX, int mouseY, float delta, int page, int dx, boolean interactive) {
        int baseX = parentLeft + 11 + dx;
        int baseY = parentTop + 31;
        int leftBound = parentLeft + 11;
        int rightBound = parentLeft + 136;
        for (int k = 0; k < this.buttons.size(); ++k) {
            GenericRecipeButton<C, R, M> button = this.buttons.get(k);
            button.setPosition(baseX + 25 * (k % 5), baseY + 25 * (k / 5));
            int index = page * 20 + k;
            boolean valid = index < this.recipeCollections.size();
            button.visible = valid;
            if (!valid) {
                continue;
            }
            button.showCollection(this.recipeCollections.get(index), menu, this.category);
            // 挤压离场：配方滑出视窗边界时，边缘钳制在边界、宽度收窄，直到压成一条线消失
            int bx = button.getX();
            int effX = bx;
            int effW = 25;
            if (bx < leftBound) {
                effX = leftBound;
                effW = bx + 25 - leftBound;
            } else if (bx + 25 > rightBound) {
                effX = bx;
                effW = rightBound - bx;
            }
            if (effW <= 0) {
                button.visible = false;
                continue;
            }
            if (effW < 25) {
                button.renderSquashed(gui, effX, effW, bx, button.getY());
            } else {
                button.extractRenderState(gui, mouseX, mouseY, delta);
            }
            if (interactive && button.visible && button.isHoveredOrFocused()) {
                this.hoveredButton = button;
            }
        }
    }

    protected void flipTo(int targetPage) {
        if (targetPage == this.currentPage) {
            return;
        }
        boolean animEnabled = BetterRecipeBook.config != null && BetterRecipeBook.config.pageAnimation.pageAnimationEnabled;
        if (!animEnabled || Math.abs(targetPage - this.currentPage) != 1) {
            // 配置禁用或跨多页跳转（如滚轮快速连滚）：直接切换，不做滑动动画
            this.currentPage = targetPage;
            this.visualPage = targetPage;
            this.animActive = false;
            this.animChase = false;
            this.updateButtonsForPage();
            return;
        }
        // 连续翻页检测：距上次翻页较近（连点）→ 进入追逐延展模式，滑动连贯不逐页独立
        if (this.frameCounter - this.lastFlipFrame < CHASE_WINDOW_FRAMES) {
            this.animChase = true;
        }
        this.lastFlipFrame = this.frameCounter;
        // 目标追逐：只更新逻辑页，visualPage 会在渲染时持续追赶
        this.currentPage = targetPage;
        this.animActive = true;
        this.updateButtonsForPage();
    }

    private float pageAnimDuration() {
        if (BetterRecipeBook.config != null) {
            return BetterRecipeBook.config.pageAnimation.pageAnimationDuration;
        }
        return PAGE_ANIM_DURATION_FALLBACK;
    }

    /**
     * 程序设置页码（如恢复上一次浏览记录）后调用，使视觉页与逻辑页同步，
     * 避免下次翻页动画从错误位置开始滑动。
     */
    public void resetVisualPosition() {
        this.visualPage = this.currentPage;
        this.animActive = false;
        this.animChase = false;
    }

    private static boolean isMouseOverRecipeBookPage(int mouseX, int mouseY, int left, int top) {
        return mouseX >= left && mouseX < left + 147 && mouseY >= top && mouseY < top + 166;
    }

    public void setResults(List<C> recipeCollection, boolean resetCurrentPage, BRBBookCategories.Category category) {
        this.recipeCollections = recipeCollection;
        this.category = category;

        this.totalPages = (int) Math.ceil((double) recipeCollection.size() / 20.0D);
        if (this.totalPages <= this.currentPage || resetCurrentPage) {
            this.currentPage = 0;
        }

        // 搜索/过滤结果变化：直接显示，不保留上一轮翻页动画
        this.animActive = false;
        this.animChase = false;
        this.visualPage = this.currentPage;
        this.lastFlipFrame = -100;

        this.updateButtonsForPage();
    }

    @Nullable
    public R getCurrentClickedRecipe() {
        return this.lastClickedRecipe;
    }

    @Nullable
    public C getLastClickedRecipeCollection() {
        return this.lastClickedRecipeCollection;
    }

    protected void updateArrowButtons() {
        if (forwardButton == null || backButton == null) return;
        if (BetterRecipeBook.config.scrolling.scrollAround && totalPages > 1) {
            forwardButton.visible = true;
            backButton.visible = true;
        } else {
            forwardButton.visible = totalPages > 1 && currentPage < totalPages - 1;
            backButton.visible = totalPages > 1 && currentPage > 0;
        }
    }

    public void drawTooltip(GuiGraphicsExtractor gui, int x, int y) {
        if (this.minecraft != null && this.minecraft.gui.screen() != null && hoveredButton != null) {
            ClientCompat.setComponentTooltipForNextFrame(gui, this.hoveredButton.getTooltipText(), x, y);
        }
    }
}
