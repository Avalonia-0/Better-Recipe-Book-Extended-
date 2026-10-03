package com.alonie.brbe.smithingtable;

import com.google.common.collect.Lists;
import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.api.BRBBookSettings;
import com.alonie.brbe.recipe.BRBSmithingRecipe;
import com.alonie.brbe.util.AlternativeOverlayLayout;
import com.alonie.brbe.util.AlternativesPaging;
import com.alonie.brbe.util.BRBTextures;
import com.alonie.brbe.util.ClientCompat;
import com.alonie.brbe.util.LeiPageButtons;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class SmithingOverlayRecipeComponent implements Renderable, GuiEventListener, AlternativesPaging.Target {
    private static final Identifier OVERLAY_RECIPE_SPRITE = Identifier.withDefaultNamespace("recipe_book/overlay_recipe");

    /** 分页上限：列 4 × 行 4（用户 2026-09-25 指定），每页 ≤16 条。 */
    private static final int PAGE_COLUMNS = 4;
    private static final int PAGE_ROWS = 4;
    private static final int PER_PAGE = PAGE_COLUMNS * PAGE_ROWS;
    /** 配方格步距（既有布局：25px）。 */
    private static final int CELL = 25;

    private final List<OverlayRecipeButton> recipeButtons = Lists.newArrayList();
    private BRBSmithingRecipe lastRecipeClicked;
    private SmithingRecipeCollection collection;
    private boolean visible;
    private float time;
    private int x;
    private int y;

    /** 全量条目 + 各自的"可合成/残缺"标记（{@code init} 时算一次，翻页时按页重建按钮）。 */
    private final List<BRBSmithingRecipe> allRecipes = Lists.newArrayList();
    private final List<Boolean> allCraftable = Lists.newArrayList();
    private final List<Boolean> allPartial = Lists.newArrayList();
    private RegistryAccess registryAccess;
    /** 当前页（0 基）与总页数（≤16 条时 pageCount = 1，不画翻页键）。 */
    private int page;
    private int pageCount = 1;

    public void init(SmithingRecipeCollection recipeCollection, int anchorX, int anchorY,
                     int panelLeft, int panelTop, RegistryAccess registryAccess) {
        this.collection = recipeCollection;

        List<BRBSmithingRecipe> lockedRecipes = recipeCollection.getDisplayRecipes(true);
        List<BRBSmithingRecipe> unlockedRecipes;
        BetterRecipeBook.ensureCategories();
        if (!BRBBookSettings.isFiltering(BetterRecipeBook.SMITHING)) {
            unlockedRecipes = recipeCollection.getDisplayRecipes(false);
        } else {
            unlockedRecipes = recipeCollection.getPartiallyCraftableRecipes();
        }
        int lockedRecipeCount = lockedRecipes.size();
        int totalRecipeCount = lockedRecipeCount + unlockedRecipes.size();

        this.visible = true;
        this.registryAccess = registryAccess;

        // 残缺标记走集合的槽位状态缓存（O(1)，且与「残缺配方标记」开关同语义）
        this.allRecipes.clear();
        this.allCraftable.clear();
        this.allPartial.clear();
        for (int index = 0; index < totalRecipeCount; ++index) {
            boolean isCraftable = index < lockedRecipeCount;
            BRBSmithingRecipe recipe = isCraftable ? lockedRecipes.get(index) : unlockedRecipes.get(index - lockedRecipeCount);
            this.allRecipes.add(recipe);
            this.allCraftable.add(isCraftable);
            this.allPartial.add(recipeCollection.isPartiallyMarked(recipe));
        }

        this.page = 0;
        this.pageCount = Math.max(1, (totalRecipeCount + PER_PAGE - 1) / PER_PAGE);
        // 先按当前页建一次按钮只为拿到盒尺寸，位置算好后按新 x/y 重排（见下）
        this.brbe$rebuildPage();
        this.brbe$placeBox(anchorX, anchorY, panelLeft, panelTop);
        this.brbe$rebuildPage();

        this.lastRecipeClicked = null;
    }

    /**
     * **就地刷新**已经打开的浮层（用户 2026-09-27 三次反馈）：pin 切换会重建结果集，浮层
     * 内容得跟着变（剥走的变体不能留一份镜像），但**位置与页码必须保持**——初版直接重新
     * {@code init} 会把页码打回第 1 页、并让浮层跟着被重排的格子跑，观感就是"界面整体乱动"。
     *
     * <p>锚点沿用调用方给的**打开时那个落点**（{@code GenericRecipePage} 存着），所以盒位置
     * 与原开法一致；页码按新页数钳制（变体被剥走可能少一页）。</p>
     */
    public void refresh(SmithingRecipeCollection recipeCollection, int anchorX, int anchorY,
                        int panelLeft, int panelTop, RegistryAccess registryAccess) {
        int keepPage = this.page;
        this.init(recipeCollection, anchorX, anchorY, panelLeft, panelTop, registryAccess);
        int clamped = Mth.clamp(keepPage, 0, this.pageCount - 1);
        if (clamped != this.page) {
            this.page = clamped;
            this.brbe$rebuildPage();
        }
    }

    /**
     * 浮层位置：**贴着被右键点击的那个组按钮**展开（用户 2026-09-26 要求与合成书统一）。
     *
     * <p>此前这里写死 {@code 面板 +7,+26}（永远同一个位置，点哪一行都一样）。现在走
     * {@link AlternativeOverlayLayout#placeBox}——与原版合成书**同一套**公式（盒右缘不超过
     * 页面中心右侧、盒底留在配方格内、盒顶不低于页面上方 100，再夹进屏幕），条目数/列数用
     * **本页**的（分页时 = 4 列 × ≤4 行）。</p>
     */
    private void brbe$placeBox(int anchorX, int anchorY, int panelLeft, int panelTop) {
        Minecraft minecraft = Minecraft.getInstance();
        int[] box = AlternativeOverlayLayout.placeBox(anchorX, anchorY,
                Math.min(PER_PAGE, this.allRecipes.size()), PAGE_COLUMNS,
                this.brbe$boxWidth(), this.brbe$boxHeight(),
                AlternativeOverlayLayout.pageCenterX(panelLeft), AlternativeOverlayLayout.pageCenterY(panelTop),
                CELL, minecraft.getWindow().getGuiScaledWidth(), minecraft.getWindow().getGuiScaledHeight());
        this.x = box[0];
        this.y = box[1];
    }

    /** 按当前页重建按钮：恒 4 列、行 ≤4（第 index 条落在本页第 {@code index - page*16} 格）。 */
    private void brbe$rebuildPage() {
        this.recipeButtons.clear();
        int start = this.page * PER_PAGE;
        int end = Math.min(this.allRecipes.size(), start + PER_PAGE);
        for (int index = start; index < end; ++index) {
            int slot = index - start;
            int buttonX = this.x + 4 + CELL * (slot % PAGE_COLUMNS);
            int buttonY = this.y + 5 + CELL * (slot / PAGE_COLUMNS);
            this.recipeButtons.add(new OverlayRecipeButton(buttonX, buttonY, this.allRecipes.get(index),
                    this.allCraftable.get(index), this.registryAccess, this.allPartial.get(index)));
        }
    }

    /** 浮层盒宽：分页时固定 4 列（翻页键右对齐才不会随每页条目数左右跳）。 */
    private int brbe$boxWidth() {
        int columns = this.pageCount > 1
                ? PAGE_COLUMNS
                : Math.max(1, Math.min(PAGE_COLUMNS, this.recipeButtons.size()));
        return columns * CELL + 8;
    }

    /** 浮层盒高：本页行数 × 25 + 8。 */
    private int brbe$boxHeight() {
        int rows = Math.max(1, Mth.ceil((float) this.recipeButtons.size() / (float) PAGE_COLUMNS));
        return Math.min(rows, PAGE_ROWS) * CELL + 8;
    }

    /**
     * 翻页键点击：翻页 + 翻页音效；Ctrl+点击跳首页/末页（与 LEI 查询窗口同语义）。
     *
     * @return true = 点在一对翻页键区域内（含禁用键）——调用方应吞掉这次点击，浮层不关
     */
    private boolean brbe$clickPageButtons(double mouseX, double mouseY) {
        int leftX = LeiPageButtons.leftX(this.x, this.brbe$boxWidth());
        int topY = LeiPageButtons.topY(this.y);
        int mx = Mth.floor(mouseX);
        int my = Mth.floor(mouseY);
        boolean overPrev = LeiPageButtons.overPrev(leftX, topY, mx, my);
        boolean overNext = LeiPageButtons.overNext(leftX, topY, mx, my);
        if (!overPrev && !overNext) {
            return false;
        }
        // 命中翻页键 = "不是点配方"：清掉上次点击的配方——调用方把 mouseClicked 的
        // true 当成"有配方要放置"（overlayMouseClicked → lastClickedRecipe → 放置），
        // 不清就会拿旧配方再放一次。
        this.lastRecipeClicked = null;
        boolean prev = overPrev && this.page > 0;
        boolean next = overNext && this.page < this.pageCount - 1;
        if (!prev && !next) {
            return true; // 禁用键：吞点击但不翻页
        }
        this.page = ClientCompat.isControlDown()
                ? (prev ? 0 : this.pageCount - 1)
                : (prev ? this.page - 1 : this.page + 1);
        ClientCompat.playPageFlipSound(Minecraft.getInstance());
        this.brbe$rebuildPage();
        return true;
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != ClientCompat.MOUSE_LEFT) {
            return false;
        }

        if (this.pageCount > 1 && this.brbe$clickPageButtons(mouseX, mouseY)) {
            return true;
        }

        for (OverlayRecipeButton overlayRecipeButton : this.recipeButtons) {
            if (!overlayRecipeButton.mouseClicked(mouseX, mouseY, button)) {
                continue;
            }
            this.lastRecipeClicked = overlayRecipeButton.recipe;
            return true;
        }

        return false;
    }

    @Nullable
    public BRBSmithingRecipe getLastRecipeClicked() {
        return this.lastRecipeClicked;
    }

    public SmithingRecipeCollection getRecipeCollection() {
        return this.collection;
    }

    public boolean isVisible() {
        return this.visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    // ── 独立滚轮翻页区（AlternativesPaging.Target，用户 2026-09-26）──────────────────

    @Override
    public boolean paged() {
        return this.visible && this.pageCount > 1;
    }

    @Override
    public boolean inScrollRegion(int mouseX, int mouseY) {
        // 滚轮区 = 浮层盒 + 上方悬浮的一对翻页键（与点击区一致）
        if (mouseX >= this.x && mouseX < this.x + this.brbe$boxWidth()
                && mouseY >= this.y && mouseY < this.y + this.brbe$boxHeight()) {
            return true;
        }
        int leftX = LeiPageButtons.leftX(this.x, this.brbe$boxWidth());
        int topY = LeiPageButtons.topY(this.y);
        return LeiPageButtons.overPrev(leftX, topY, mouseX, mouseY)
                || LeiPageButtons.overNext(leftX, topY, mouseX, mouseY);
    }

    @Override
    public void flipPage(double verticalAmount) {
        int next = AlternativesPaging.stepPage(this.page, this.pageCount, verticalAmount);
        if (next == this.page) {
            return;
        }
        this.page = next;
        this.lastRecipeClicked = null;
        ClientCompat.playPageFlipSound(Minecraft.getInstance());
        this.brbe$rebuildPage();
    }

    @Nullable
    public ScreenRectangle getBounds() {
        if (!this.visible || this.recipeButtons.isEmpty()) {
            return null;
        }

        // 盒高按本页条目数（分页时盒宽固定 4 列）；翻页键悬浮在面板上方，一并算进命中区
        int floating = this.pageCount > 1 ? LeiPageButtons.ABOVE : 0;
        return new ScreenRectangle(this.x, this.y - floating, this.brbe$boxWidth(), this.brbe$boxHeight() + floating);
    }

    @Override
    public void setFocused(boolean bl) {
    }

    @Override
    public boolean isFocused() {
        return false;
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return false;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float delta) {
        if (!this.visible) {
            return;
        }
        // 独立滚轮翻页区（用户 2026-09-26）：渲染时自登记，滚轮判定在 RecipeBookGesture 的最前面
        AlternativesPaging.track(this);

        this.time += delta;
        guiGraphics.pose().pushMatrix();
        int boxWidth = this.brbe$boxWidth();
        int boxHeight = this.brbe$boxHeight();
        // 面板背景不向上拓展（用户 2026-09-26）：翻页键直接**悬浮**在面板上方
        ClientCompat.blitSprite(guiGraphics, OVERLAY_RECIPE_SPRITE, this.x, this.y, boxWidth, boxHeight);

        for (OverlayRecipeButton overlayRecipeButton : this.recipeButtons) {
            // ⚠️ 必须走**外层** extractRenderState（原版 {@code OverlayRecipeComponent} 也是
            // 这样）：26.x 的悬停标记只在它里面刷新（isMouseOver 只做判定、不写缓存）。
            // 此前直接调内层 extractWidgetRenderState，isHovered 永不更新 → 组内按钮的
            // 悬停高亮失效，且「组内悬停 → 自动填充幽灵配方」读不到悬停（用户 2026-09-25
            // 反馈：锻造台替代配方组里的配方无法触发自动填充幽灵配方）。
            overlayRecipeButton.extractRenderState(guiGraphics, mouseX, mouseY, delta);
        }

        if (this.pageCount > 1) {
            LeiPageButtons.draw(guiGraphics, LeiPageButtons.leftX(this.x, boxWidth), LeiPageButtons.topY(this.y),
                    mouseX, mouseY, this.page > 0, this.page < this.pageCount - 1, this.page, this.pageCount);
        }

        guiGraphics.pose().popMatrix();
    }

    /** 组浮层内指针下的按钮（null = 没有）：{@code SmithingRecipeBookPage} 用它把
     *  悬停变体补进页面的 {@code hoveredRecipe}，悬停预览便能在组内配方上生效。 */
    @Nullable
    public OverlayRecipeButton hoveredButton() {
        if (!this.visible) {
            return null;
        }
        for (OverlayRecipeButton button : this.recipeButtons) {
            if (button.isHovered()) {
                return button;
            }
        }
        return null;
    }

    public static class OverlayRecipeButton extends AbstractWidget {
        final BRBSmithingRecipe recipe;
        private final boolean craftable;
        private final boolean partial;
        private final RegistryAccess registryAccess;

        public OverlayRecipeButton(int x, int y, BRBSmithingRecipe recipe, boolean craftable, RegistryAccess registryAccess, boolean partial) {
            super(x, y, 24, 24, CommonComponents.EMPTY);
            this.recipe = recipe;
            this.craftable = craftable;
            this.registryAccess = registryAccess;
            this.partial = partial;
        }

        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            return ClientCompat.mouseClicked(this, mouseX, mouseY, button);
        }

        public BRBSmithingRecipe getRecipe() {
            return this.recipe;
        }

        /**
         * 这一格的 tooltip（用户 2026-09-27 诉求：替代配方组里的配方要像普通配方那样有 tooltip）。
         *
         * <p>行内容 = 该格**画出来的那件产物**的 tooltip（+ 空行 + 模组名），与该书普通配方格
         * 完全同款。没有「单击鼠标右键获取更多信息」那行：格子本身就是展开后的单个变体，
         * 右键不再展开任何东西。</p>
         */
        public java.util.List<net.minecraft.network.chat.Component> getTooltipText() {
            return com.alonie.brbe.util.RecipeCellTooltips.forStack(this.registryAccess,
                    this.recipe.getResult(this.registryAccess, BetterRecipeBook.SMITHING_SEARCH), false);
        }

        @Override
        protected boolean isValidClickButton(MouseButtonInfo button) {
            return button.button() == ClientCompat.MOUSE_LEFT;
        }

        @Override
        public void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
            this.defaultButtonNarrationText(narrationElementOutput);
        }

        @Override
        protected void extractWidgetRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float delta) {
            BetterRecipeBook.ensureCategories();
            // Partial recipes keep the craftable sprite (light border); the red overlay marks them.
            Identifier sprite = BRBTextures.RECIPE_BOOK_PLAIN_OVERLAY_SPRITE.get(this.craftable || this.partial, this.isHoveredOrFocused());
            ClientCompat.blitSprite(guiGraphics, sprite, this.getX(), this.getY(), this.width, this.height);
            if (this.partial) {
                guiGraphics.fill(this.getX() + 1, this.getY() + 1, this.getX() + this.width - 1, this.getY() + this.height - 1, 0x60FF3333);
            }
            guiGraphics.fakeItem(this.recipe.getResult(this.registryAccess, BetterRecipeBook.SMITHING_SEARCH), this.getX() + 4, this.getY() + 4);
            // 已固定的变体画图钉（与网格按钮 / 原版书的组浮层同款）——组浮层里 pin 的反馈
            // （用户 2026-09-27 反馈：自研书此前只有"组能 pin"，变体 pin 没有任何标记）
            if (this.recipe.id() != null && BetterRecipeBook.pinnedRecipeManager.pinned.contains(this.recipe.id())) {
                ClientCompat.blitSprite(guiGraphics, BRBTextures.RECIPE_BOOK_PIN_SPRITE,
                        this.getX() - 4, this.getY() - 4, 32, 32);
            }
        }
    }
}
