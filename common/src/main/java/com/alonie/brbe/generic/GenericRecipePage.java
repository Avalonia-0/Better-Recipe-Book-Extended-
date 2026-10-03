package com.alonie.brbe.generic;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.api.BRBBookCategories;
import com.alonie.brbe.layout.BookLayout;
import com.alonie.brbe.layout.GridSpec;
import com.alonie.brbe.util.BRBTextures;
import com.alonie.brbe.util.CycleLock;
import com.alonie.brbe.util.PageTurnArrows;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.StateSwitchingButton;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
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
    protected int bookWidth = GenericRecipeBookComponent.VANILLA_BOOK_WIDTH;
    protected StateSwitchingButton forwardButton;
    protected StateSwitchingButton backButton;
    protected List<C> recipeCollections = ImmutableList.of();
    protected C lastClickedRecipeCollection;
    protected R lastClickedRecipe;
    protected BRBBookCategories.Category category;
    protected int totalPages;
    protected int currentPage;
    private final List<GenericRecipeButton<C, R, M>> buttons = Lists.newArrayListWithCapacity(80);

    public List<GenericRecipeButton<C, R, M>> getButtons() {
        return buttons;
    }
    protected GenericRecipeButton<C, R, M> hoveredButton;
    /** 本帧光标下的配方变体（网格按钮，或**替代配方组浮层**内的按钮——见
     *  {@code SmithingRecipeBookPage}）：tooltip / R-U 查询读它。 */
    protected R hoveredRecipe;
    /**
     * **幽灵预览**专用的悬停配方（可与 {@link #hoveredRecipe} 不同）：只喂
     * 「自动填充幽灵配方」。不提供预览的格子（{@link GenericRecipeButton#providesHoverPreview()}
     * = false，例如锻造台纹饰组）在这里是 {@code null} → 不写幽灵（用户 2026-09-26 诉求）。
     */
    protected R hoverGhostRecipe;

    public GenericRecipePage(RegistryAccess registryAccess, Supplier<GenericRecipeButton<C, R, M>> recipeButtonSupplier) {
        this.registryAccess = registryAccess;

        // 20 buttons for vanilla-size book (5 columns × 4 rows).
        // The expanded recipe book will add more buttons on demand via
        // its own mixin, matching the vanilla-path pattern.
        for (int i = 0; i < 20; ++i) {
            this.buttons.add(recipeButtonSupplier.get());
        }
    }

    /** Number of button columns — 5 normally, dynamic when expanded. */
    public int getColumns() {
        if (!BetterRecipeBook.ctx().config().expandedRecipeBook) return 5;
        int availableWidth = bookWidth - BookLayout.GRID_LEFT_PADDING * 2;
        return Math.max(5, availableWidth / BookLayout.BUTTON_SIZE);
    }

    /** Buttons per page — 20 normally, dynamic when expanded. */
    public int getButtonsPerPage() {
        if (!BetterRecipeBook.ctx().config().expandedRecipeBook) return GridSpec.standard().totalButtons();
        return getColumns() * 4;
    }

    protected void initialize(Minecraft client, int parentLeft, int parentTop, M menu, int bookWidth) {
        this.minecraft = client;
        this.menu = menu;

        this.parentLeft = parentLeft;
        this.parentTop = parentTop;
        this.bookWidth = bookWidth;

        int cols;
        int gridLeft;
        int forwardX;
        int backX;

        if (BetterRecipeBook.ctx().config().expandedRecipeBook) {
            cols = getColumns();
            int gridWidth = cols * BookLayout.BUTTON_SIZE;
            gridLeft = parentLeft + (bookWidth - gridWidth) / 2;
            int pageCenterX = parentLeft + bookWidth / 2;
            forwardX = pageCenterX + 3;
            backX = pageCenterX - 15;
        } else {
            // Standard layout — use BookLayout positioning
            cols = 5;
            gridLeft = parentLeft + BookLayout.GRID_LEFT_PADDING;
            forwardX = parentLeft + BookLayout.ARROW_FORWARD_X;
            backX = parentLeft + BookLayout.ARROW_BACK_X;
        }

        this.forwardButton = new StateSwitchingButton(forwardX, parentTop + BookLayout.ARROW_Y_OFFSET, 12, 17, false);
        this.forwardButton.initTextureValues(BRBTextures.RECIPE_BOOK_PAGE_FORWARD_SPRITES);
        this.backButton = new StateSwitchingButton(backX, parentTop + BookLayout.ARROW_Y_OFFSET, 12, 17, true);
        this.backButton.initTextureValues(BRBTextures.RECIPE_BOOK_PAGE_BACKWARD_SPRITES);
        // 登记翻页箭头：按下时播「翻页音效」而不是原版点击声（PageTurnArrowSoundMixin）。
        // 酿造台/锻造台书的翻页音效本已由 flipTo 播放——不登记的话这里会再叠一声原版点击声。
        PageTurnArrows.register(this.forwardButton, this.backButton);

        for (int k = 0; k < this.buttons.size(); ++k) {
            this.buttons.get(k).setPosition(
                    gridLeft + BookLayout.BUTTON_SIZE * (k % cols),
                    parentTop + BookLayout.GRID_TOP_PADDING + BookLayout.BUTTON_SIZE * (k / cols));
            this.buttons.get(k).visible = false;
        }
    }

    protected boolean overlayMouseClicked(double mouseX, double mouseY, int button, int j, int k, int l, int m) {
        return false;
    }

    /**
     * 展开替代配方组浮层（默认无浮层）。
     *
     * <p>{@code anchorX/anchorY} = **被右键点击的那个组按钮**的屏幕坐标：浮层贴着它展开
     * （原版合成书的落位规则，见 {@link com.alonie.brbe.util.AlternativeOverlayLayout#placeBox}）。</p>
     */
    protected void initOverlay(C recipeCollection, int anchorX, int anchorY,
                               int x, int y, RegistryAccess registryAccess) {
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button, int j, int k, int l, int m) {
        this.lastClickedRecipe = null;
        this.lastClickedRecipeCollection = null;

        if (overlayIsVisible() && overlayMouseClicked(mouseX, mouseY, button, j, k, l, m)) {
            return true;
        }

        if (this.forwardButton.mouseClicked(mouseX, mouseY, button)) {
            if (++currentPage >= totalPages) {
                currentPage = BetterRecipeBook.config.scrolling.scrollAround ? 0 : totalPages - 1;
            }
            this.updateButtonsForPage();
            return true;
        } else if (this.backButton.mouseClicked(mouseX, mouseY, button)) {
            if (--currentPage < 0) {
                currentPage = BetterRecipeBook.config.scrolling.scrollAround ? totalPages - 1 : 0;
            }
            this.updateButtonsForPage();
            return true;
        } else {
            for (GenericRecipeButton<C, R, M> recipeButton : this.buttons) {
                if (!recipeButton.mouseClicked(mouseX, mouseY, button)) continue;
                if (button == 0) {
                    this.lastClickedRecipe = recipeButton.getCurrentDisplayedRecipe();
                    this.lastClickedRecipeCollection = recipeButton.getCollection();
                } else if (button == 1 && !overlayIsVisible() && !recipeButton.isOnlyOption()) {
                    this.initOverlay(recipeButton.getCollection(), recipeButton.getX(), recipeButton.getY(),
                            this.parentLeft, this.parentTop, registryAccess);
                }
                return true;
            }
        }
        return false;
    }

    public void updateButtonsForPage() {
        int bpp = getButtonsPerPage();
        int i = bpp * this.currentPage;

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

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY, int left, int top, int width, int height) {
        if (this.backButton == null || this.forwardButton == null) return false;
        if (!isMouseOverRecipeBookPage((int) mouseX, (int) mouseY, left, top) || scrollY == 0) return false;

        if (scrollY < 0 && currentPage < totalPages - 1) {
            currentPage++;
            this.updateButtonsForPage();
            return true;
        } else if (scrollY > 0 && currentPage > 0) {
            currentPage--;
            this.updateButtonsForPage();
            return true;
        }
        return false;
    }

    protected boolean overlayIsVisible() {
        return false;
    }

    /**
     * 替代配方组浮层里**被悬停的那一格**的 tooltip（{@code null} = 没有悬停任何格子）。
     *
     * <p>浮层打开时格子由页面自己画（{@code suppressGridHover()} 把 {@code hoveredButton}
     * 压成 null），tooltip 就得由浮层自己给：锻造台/酿造台页面覆写本方法，行内容与该书的
     * 普通配方格同款（用户 2026-09-27 诉求）。通用页面没有浮层，默认 {@code null}。</p>
     */
    @Nullable
    public List<Component> overlayTooltip() {
        return null;
    }

    /**
     * 替代配方组浮层打开时，**网格不再参与悬停判定**（原版合成书就是这个语义：
     * {@code hoverghost/RecipeBookPageMixin} 在 {@code overlay.isVisible()} 时直接 return）。
     *
     * <p>不抑制的话，鼠标落在浮层的面板/间隙上时命中判定会**透过浮层**命中底下的网格按钮 →
     * 触发那格的幽灵预览与 tooltip（用户 2026-09-26 反馈的"透过替代配方组界面触发幽灵配方"）。</p>
     */
    protected boolean suppressGridHover() {
        return false;
    }

    protected void render(GuiGraphics gui, int blitX, int blitY, int mouseX, int mouseY, float delta) {
        // Guard: if initialize() was never called (e.g. book closed on screen
        // init), every field is null — bail out cleanly.
        if (this.backButton == null || this.forwardButton == null || this.buttons == null) return;

        // Process queued scroll — captured by MouseScrollHandler at the
        // GLFW level and stored in BetterRecipeBook.queuedScroll.  Processed
        // at render time (same as scrollablepages/RecipeBookPageMixin).
        // 「锁定折叠物品」键 + 滚轮：先试**逐格翻动指针下那一件折叠物品**（功能方块
        // 里的幽灵物品 / 网格按钮），翻到了就不翻页（用户 2026-09-26 诉求：幽灵物品
        // 也能锁定 + 滚轮翻动）。BRBE 自研配方书（酿造台/锻造台）的幽灵物品走
        // GenericGhostRecipe 自己的轮循，不经过原版 SlotSelectTime，所以这里用
        // CycleLock 的统一判定。
        int queued = BetterRecipeBook.getQueuedScroll();
        if (queued != 0 && !CycleLock.consumeQueuedScroll()
                && totalPages > 1 && isMouseOverRecipeBookPage(mouseX, mouseY, blitX, blitY)) {
            currentPage += queued;
            if (currentPage >= totalPages) {
                currentPage = BetterRecipeBook.config.scrolling.scrollAround ? currentPage % totalPages : totalPages - 1;
            } else if (currentPage < 0) {
                currentPage = BetterRecipeBook.config.scrolling.scrollAround ? (currentPage % totalPages) + totalPages : 0;
            }
            updateButtonsForPage();
        }
        BetterRecipeBook.setQueuedScroll(0);

        if (this.totalPages > 1) {
            String string = this.currentPage + 1 + "/" + this.totalPages;
            int stringWidth = this.minecraft.font.width(string);
            gui.drawString(this.minecraft.font, string, blitX + bookWidth / 2 - stringWidth / 2, blitY + 141, -1, false);
        }

        this.hoveredButton = null;
        this.hoveredRecipe = null;
        this.hoverGhostRecipe = null;

        for (var button : this.buttons) {
            button.render(gui, mouseX, mouseY, delta);
            if (!this.suppressGridHover() && button.visible && button.isHoveredOrFocused()) {
                this.hoveredButton = button;
                this.hoveredRecipe = button.getCurrentDisplayedRecipe();
                // 幽灵预览：格子可以声明"不提供"（纹饰组）——悬停它不写幽灵、也不暂隐工作区
                this.hoverGhostRecipe = button.providesHoverPreview() ? this.hoveredRecipe : null;
            }
        }

        if (this.backButton != null) this.backButton.render(gui, mouseX, mouseY, delta);
        if (this.forwardButton != null) this.forwardButton.render(gui, mouseX, mouseY, delta);
    }

    private boolean isMouseOverRecipeBookPage(int mouseX, int mouseY, int left, int top) {
        return mouseX >= left && mouseX < left + bookWidth && mouseY >= top && mouseY < top + GenericRecipeBookComponent.VANILLA_BOOK_HEIGHT;
    }

    public void setResults(List<C> recipeCollection, boolean resetCurrentPage, BRBBookCategories.Category category) {
        this.recipeCollections = recipeCollection;
        this.category = category;

        int bpp = getButtonsPerPage();
        this.totalPages = (int) Math.ceil((double) recipeCollection.size() / (double) bpp);
        if (this.totalPages <= this.currentPage || resetCurrentPage) {
            this.currentPage = 0;
        }

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

    public void drawTooltip(GuiGraphics gui, int x, int y) {
        if (this.minecraft != null && this.minecraft.screen != null && hoveredButton != null) {
            gui.renderComponentTooltip(Minecraft.getInstance().font, this.hoveredButton.getTooltipText(), x, y);
        }
    }
}
