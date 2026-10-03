package com.alonie.brbe.generic;

import com.google.common.collect.Lists;
import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.api.BRBBookCategories;
import com.alonie.brbe.api.BRBBookSettings;
import com.alonie.brbe.compat.ItemViewCompat;
import com.alonie.brbe.interfaces.IPinningComponent;
import com.alonie.brbe.interfaces.ISettingsButton;
import com.alonie.brbe.mixins.accessors.RecipeBookComponentAccessor;
import com.alonie.brbe.search.SearchCache;
import com.alonie.brbe.search.SearchQuery;
import com.alonie.brbe.util.BRBHelper;
import com.alonie.brbe.util.BRBTextures;
import com.alonie.brbe.util.CollectionPipeline;
import com.alonie.brbe.util.HoverGhostRecipe;
import com.alonie.brbe.layout.BookLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.recipebook.RecipeShownListener;
import net.minecraft.client.resources.language.LanguageInfo;
import net.minecraft.client.resources.language.LanguageManager;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;

public abstract class GenericRecipeBookComponent<M extends AbstractContainerMenu, C extends GenericRecipeBookCollection<R, M>, R extends GenericRecipe> implements Renderable, NarratableEntry, GuiEventListener, ISettingsButton, RecipeShownListener, IPinningComponent<C> {
    protected static final Component SEARCH_HINT = RecipeBookComponentAccessor.getSEARCH_HINT();
    protected static final Component ALL_RECIPES_TOOLTIP = RecipeBookComponentAccessor.getALL_RECIPES_TOOLTIP();

    /** Vanilla recipe book texture dimensions. */
    public static final int VANILLA_BOOK_WIDTH = 147;
    public static final int VANILLA_BOOK_HEIGHT = 166;

    boolean visible;
    protected boolean ignoreTextInput;
    protected Minecraft minecraft;
    protected EditBox searchBox;
    /**
     * 上一次已应用的搜索词。
     *
     * <p>本分支的 {@code checkSearchStringUpdate} 没有 26.x 那句 {@code lastSearch.isEmpty()}
     * （因此没有那个 {@code NullPointerException}），但字段仍然必须初始化——判等语义与
     * 26.x 各分支保持一致（用户 2026-09-27 反馈的"搜索要切标签才刷新"即该 NPE 所致）。</p>
     */
    private String lastSearch = "";
    protected int xOffset;
    protected boolean widthTooNarrow;
    protected int width;
    protected int height;
    protected M menu;
    protected final StackedContents stackedContents = new StackedContents();
    protected StateSwitchingButton filterButton;
    protected ImageButton settingsButton;
    public GenericRecipePage<M, C, R> recipesPage;
    protected final List<BRBGroupButtonWidget> tabButtons = Lists.newArrayList();
    @Nullable
    public BRBGroupButtonWidget selectedTab;
    protected GenericClientRecipeBook book;
    protected RecipeManager recipeManager;

    private boolean doubleRefresh = true;
    protected RegistryAccess registryAccess;
    @Nullable
    public GenericGhostRecipe<R> ghostRecipe;

    /** The ghost ingredient ItemStack the mouse was over during the last tooltip render. */
    @Nullable
    private ItemStack brbe$lastHoveredGhostItem;

    /**
     * 悬停预览正在展示的配方（用户 2026-09-25 诉求）。与页面当前悬停配方比较：
     * 只有"指针换了一个配方"时才重写幽灵物品，因此点击（{@code handlePlaceRecipe}
     * 自己写/清幽灵）不会被下一帧的悬停覆盖。
     */
    @Nullable
    private R brbe$hoverGhostRecipe;

    /**
     * **点击配方留下的缺料引导**（用户 2026-09-26）：点击是"持续引导"、悬停是"临时预览"，
     * 两者的幽灵内容可以相同，但生命周期不同——指针离开配方按钮后要**还原点击留下的引导**，
     * 而不是把工作区清空。
     *
     * <p>此前的缺陷：悬停逻辑在"指针下没有配方"时无条件 {@code ghostRecipe.clear()}，
     * 于是点击写入的引导在鼠标一移开就被抹掉 → 玩家观感"点击根本不填充幽灵配方，
     * 只有悬停时才临时出现"（用户反馈，酿造台与锻造台同病）。</p>
     *
     * <p>子类在点击放置的"材料不齐"分支调用 {@link #brbe$showPlacedGhost} 登记，
     * 在"材料齐、直接放置"分支调用 {@link #brbe$clearPlacedGhost} 撤销。</p>
     */
    @Nullable
    private R brbe$placedGhostRecipe;

    /**
     * 悬停的这条配方**工作区里已经摆好了** → 本次悬停不预览（用户 2026-09-27 收尾诉求）。
     * 判定见 {@link #brbe$recipeLaidOutInWorkspace}；解析见 {@link #brbe$updateHoverGhost}。
     */
    private boolean brbe$hoverSuppressed;

    /**
     * 「在别的配方上**停留**多久算'已经预览过它'」：达到它，悬停预览结束时**点击留下的缺料引导
     * 就此结束**（不再还原）。用户 2026-09-26 反馈：悬停展示结束后会留下一个持久幽灵，
     * 来源"无规律"——实为早先点击留下的引导被还原（26.3 实机日志 BRBE-GHOST 实证）。
     * 但鼠标从被点的格子移向工作区时会**掠过**别的格子，那种"路过"（< 300ms）必须保留引导，
     * 否则又回到"点击后引导被鼠标移开抹掉"的老问题。见 {@link #brbe$updateHoverGhost}。
     */
    private static final long GUIDE_SUPERSEDE_MS = 300L;
    /** 本次悬停预览的起始时刻（同一格内轮循换配方不重置）。 */
    private long brbe$hoverStartedAt;

    /** 点击配方、材料不齐：写入幽灵引导并登记为"点击留下的引导"（与悬停预览同一写入路径）。 */
    protected final void brbe$showPlacedGhost(R recipe) {
        this.brbe$placedGhostRecipe = recipe;
        // 点击即刷新"停留"计时：随后的离开不该被算成"在别的配方上停留看过"
        this.brbe$hoverStartedAt = System.currentTimeMillis();
        this.setupHoverGhost(recipe);
        // 幽灵的所有权交给原版（点击引导）→ 鼠标停在原格时不再叠加悬停预览，否则刚写好的引导
        // 会被预览的"暂隐工作区真实物品"盖成一片空白（用户 2026-09-27 实测）。
        HoverGhostRecipe.markHandedOver();
    }

    /** 材料齐、配方已放置：撤销登记（幽灵已被 {@code handlePlaceRecipe} 清空，不能再被还原）。 */
    protected final void brbe$clearPlacedGhost() {
        this.brbe$placedGhostRecipe = null;
        // 真实物品刚被放进工作区 → 本格上不再预览（同 {@link HoverGhostRecipe#invalidate()}）
        HoverGhostRecipe.markHandedOver();
    }

    /**
     * 幽灵被**外部**清空（玩家点空槽 / 槽位变化 / 服务端回包）：点击留下的缺料引导**随之结束**。
     *
     * <p>不结束的话，这份已被玩家处理掉的引导会在指针下一次离开悬停的配方时被"还原"回工作区——
     * 玩家看到的是"我明明没再点击，工作区自己冒出一份幽灵，而且配方跟我刚悬停的那个没关系"
     * （用户 2026-09-26 反馈；26.3 实机日志 BRBE-GHOST 实证：guide 注册 → slotClicked 清空 →
     * 下一次悬停结束时又把那份引导写回来）。与工作台那条路径的
     * {@code HoverGhostRecipe.invalidate()}（原版流程接管幽灵 → 放弃还原权）同义。</p>
     */
    public void brbe$endGhostGuide() {
        this.brbe$placedGhostRecipe = null;
        this.brbe$hoverGhostRecipe = null;
        this.brbe$hoverStartedAt = 0L;
        if (this.ghostRecipe != null) {
            this.ghostRecipe.clear();
        }
    }

    /** 两条配方是不是"同一个"（按 {@link GenericRecipe#id()} 比：同一组的各条路线 id 相同）。 */
    private boolean brbe$sameRecipeId(@Nullable R a, @Nullable R b) {
        if (a == null || b == null) {
            return false;
        }
        return a.id() != null && a.id().equals(b.id());
    }


    protected GenericRecipeBookComponent() {
    }

    abstract public Component getRecipeFilterName();

    abstract public BRBHelper.Book getRecipeBookType();

    public void init(int parentWidth, int parentHeight, Minecraft client, boolean narrow, M menu, RegistryAccess registryAccess) {
        this.init(parentWidth, parentHeight, client, narrow, menu, null, registryAccess);
    }

    public void init(int width, int height, Minecraft minecraft, boolean widthNarrow, M menu, @Nullable Consumer<ItemStack> onGhostRecipeUpdate, RegistryAccess registryAccess) {
        this.minecraft = minecraft;
        this.width = width;
        this.height = height;
        this.menu = menu;
        this.widthTooNarrow = widthNarrow;
        if (this.minecraft.player == null) return;
        this.minecraft.player.containerMenu = menu;

        this.setVisible(BRBBookSettings.isOpen(this.getRecipeBookType()));

        this.book = new GenericClientRecipeBook();
        this.registryAccess = registryAccess;

        this.ghostRecipe = new GenericGhostRecipe<>(onGhostRecipeUpdate, registryAccess);
    }

    public void initVisuals() {
        if (BetterRecipeBook.ctx().config().keepCentered) {
            this.xOffset = this.widthTooNarrow ? 0 : BookLayout.X_OFFSET_CENTERED;
        } else {
            this.xOffset = this.widthTooNarrow ? 0 : BookLayout.X_OFFSET_STANDARD;
        }

        int i = (this.width - BookLayout.TEXTURE_WIDTH) / 2 - this.xOffset;
        int j = (this.height - BookLayout.TEXTURE_HEIGHT) / 2;
        this.stackedContents.clear();
        if (this.minecraft.player == null) return;
        this.minecraft.player.getInventory().fillStackedContents(this.stackedContents);
        // TODO: menu.fillCraftSlotsStackedContents
        String string = this.searchBox != null ? this.searchBox.getValue() : "";
        Objects.requireNonNull(this.minecraft.font);
        this.searchBox = new EditBox(this.minecraft.font, i + BookLayout.SEARCH_X_OFFSET,
                j + BookLayout.SEARCH_Y_OFFSET, BookLayout.SEARCH_WIDTH,
                this.minecraft.font.lineHeight + 5, Component.translatable("itemGroup.search"));
        this.searchBox.setMaxLength(50);
        this.searchBox.setVisible(true);
        this.searchBox.setTextColor(0xFFFFFF);
        this.searchBox.setValue(string);
        this.searchBox.setHint(SEARCH_HINT);
        this.settingsButton = createSettingsButton(i, j);
        this.recipesPage.initialize(this.minecraft, i, j, menu, BookLayout.TEXTURE_WIDTH);
        this.tabButtons.clear();
        // filter button: right-aligned from the book's right edge
        this.filterButton = new StateSwitchingButton(i + BookLayout.TEXTURE_WIDTH - 37,
                j + BookLayout.FILTER_Y_OFFSET, BookLayout.FILTER_WIDTH, BookLayout.FILTER_HEIGHT,
                BRBBookSettings.isFiltering(this.getRecipeBookType()));
        this.updateFilterButtonTooltip();
        this.filterButton.initTextureValues(BRBTextures.filterButtonFor(this.getRecipeBookType()));
        // 「优化原版配方过滤器」（用户 2026-09-27 诉求）：与原版书同一套处理——按钮隐藏
        // （配方全显示，优先级交给「可合成置顶」排序），搜索栏加宽到 97 居中占位（左右各距书缘 25）。
        if (BRBBookSettings.partialFilterMode()) {
            this.filterButton.visible = false;
            this.filterButton.active = false;
            this.searchBox.setWidth(97);
        }

        List<BRBBookCategories.Category> categories = BRBBookCategories.getCategories(this.getRecipeBookType());

        if (categories == null || categories.isEmpty()) {
            // Categories not yet registered — silently degrade.
            return;
        }

        for (BRBBookCategories.Category category : categories) {
            this.tabButtons.add(new BRBGroupButtonWidget(category));
        }

        if (this.selectedTab != null) {
            this.selectedTab = this.tabButtons.stream().filter((button) -> button.getCategory().equals(this.selectedTab.getCategory())).findFirst().orElse(null);
        }

        if (this.selectedTab == null) {
            this.selectedTab = this.tabButtons.get(0);
        }

        this.selectedTab.setStateTriggered(true);
        this.updateCollections(false);
        this.refreshTabButtons();
    }

    public void render(GuiGraphics gui, int mouseX, int mouseY, float delta) {
        if (!this.isVisible()) return;


        if (this.doubleRefresh) {
            // Minecraft doesn't populate the inventory on initialization so this is the only solution I have
            updateCollections(true);
            this.doubleRefresh = false;
        }

        int blitX = (this.width - BookLayout.TEXTURE_WIDTH) / 2 - this.xOffset;
        int blitY = (this.height - BookLayout.TEXTURE_HEIGHT) / 2;

        // Render recipe book background
        gui.blit(BRBTextures.RECIPE_BOOK_BACKGROUND_TEXTURE, blitX, blitY, 0, 0,
                BookLayout.TEXTURE_WIDTH, BookLayout.TEXTURE_HEIGHT, 256, 256);

        // render search box
        this.searchBox.render(gui, mouseX, mouseY, delta);

        // render tab buttons
        for (BRBGroupButtonWidget widget : this.tabButtons) {
            widget.render(gui, mouseX, mouseY, delta);
        }

        // 过滤按钮隐藏时不画（「优化原版配方过滤器」开启 = 无按钮模式）
        if (this.filterButton.visible) {
            this.filterButton.render(gui, mouseX, mouseY, delta);
        }

        ISettingsButton.super.renderSettingsButton(this.settingsButton, gui, mouseX, mouseY, delta);

        // render the recipe book page contents
        this.recipesPage.render(gui, blitX, blitY, mouseX, mouseY, delta);

        // 悬停即预览：页面渲染后 hoveredButton 已是本帧光标下的按钮
        this.brbe$updateHoverGhost();
    }

    @Override
    public boolean keyPressed(int i, int j, int k) {
        this.ignoreTextInput = false;
        if (!this.isVisible() || this.minecraft.player != null && this.minecraft.player.isSpectator()) {
            return false;
        }
        if (this.searchBox.keyPressed(i, j, k)) {
            this.checkSearchStringUpdate();
            return true;
        }
        if (this.searchBox.isFocused() && this.searchBox.isVisible() && i != 256) {
            return true;
        }
        if (this.minecraft.options.keyChat.matches(i, j) && !this.searchBox.isFocused()) {
            this.ignoreTextInput = true;
            this.searchBox.setFocused(true);
            return true;
        }

        if (BetterRecipeBook.PIN_MAPPING.matches(i, j) && true) {
            for (GenericRecipeButton<C, R, M> resultButton : this.recipesPage.getButtons()) {
                if (resultButton.isHoveredOrFocused()) {
                    BetterRecipeBook.pinnedRecipeManager.addOrRemoveFavourite(resultButton.getCollection());
                    this.updateCollections(false);
                    return true;
                }
            }
        }

        // JEI/REI integration: open recipe/usage views for hovered item.
        if (ItemViewCompat.isLoaded()) {
            if (this.recipesPage.hoveredButton != null) {
                R hoveredRecipe = this.recipesPage.hoveredButton.getCurrentDisplayedRecipe();
                if (hoveredRecipe != null) {
                    ItemStack hoveredStack = hoveredRecipe.getResult(registryAccess, this.recipesPage.hoveredButton.category);
                    if (ItemViewCompat.matchesShowRecipe(i, j)) {
                        return ItemViewCompat.openRecipeView(hoveredStack);
                    }
                    if (ItemViewCompat.matchesShowUses(i, j)) {
                        return ItemViewCompat.openUsageView(hoveredStack);
                    }
                }
            }

            ItemStack ghostStack = this.brbe$lastHoveredGhostItem;
            if (ghostStack != null && !ghostStack.isEmpty()) {
                if (ItemViewCompat.matchesShowRecipe(i, j)) {
                    return ItemViewCompat.openRecipeView(ghostStack);
                }
                if (ItemViewCompat.matchesShowUses(i, j)) {
                    return ItemViewCompat.openUsageView(ghostStack);
                }
            }
        }

        return false;
    }

    public abstract void handlePlaceRecipe();

    @Override
    public boolean keyReleased(int i, int j, int k) {
        this.ignoreTextInput = false;
        return GuiEventListener.super.keyReleased(i, j, k);
    }

    @Override
    public boolean charTyped(char c, int i) {
        if (this.ignoreTextInput) {
            return false;
        }
        if (!this.isVisible() || this.minecraft.player != null && this.minecraft.player.isSpectator()) {
            return false;
        }
        if (this.searchBox.charTyped(c, i)) {
            this.checkSearchStringUpdate();
            return true;
        }
        return GuiEventListener.super.charTyped(c, i);
    }

    private void checkSearchStringUpdate() {
        String string = this.searchBox.getValue().toLowerCase(Locale.ROOT);
        this.pirateSpeechForThePeople(string);
        if (!string.equals(this.lastSearch)) {
            this.updateCollections(false);
            this.lastSearch = string;
        }
    }

    protected void updateCollections(boolean resetPageNumber) {
        if (this.selectedTab == null) return;
        if (this.searchBox == null) return;

        List<C> results = new ArrayList<>(this.getCollectionsForCategory());

        // Search filter
        String string = this.searchBox.getValue();
        if (!string.isEmpty()) {
            SearchQuery query = SearchQuery.parse(string);
            SearchCache cache = new SearchCache();
            // 「纯文本也匹配 tooltip 全文」——原版配方书搜索的语料就是产物物品的全部 tooltip 行
            // （SessionSearchTrees.recipes()），于是"海岸盔甲纹饰"这种只出现在 tooltip 里的文字
            // 在原版能查到锻造台纹饰组的配方；BRBE 先前只匹配物品名 → 查不到（用户 2026-09-27 诉求）。
            // 只在自研书打开：语料小（锻造/酿造几十个集合），tooltip 生成 + 缓存的开销可控。
            cache.setTooltipFallback(true);
            results.removeIf(collection -> !matchesSearch(collection, query, cache));
        }

        // Pipeline: pins → partial sort → filter toggle
        CollectionPipeline.applyPinsGeneric(results);

        boolean isFiltering = BRBBookSettings.isFiltering(this.getRecipeBookType());
        boolean shouldSort = BetterRecipeBook.ctx().config().partialCraftingEnabled || isFiltering;
        if (shouldSort) {
            results = CollectionPipeline.applyPartialSortGeneric(results);
        }
        if (isFiltering) {
            results = CollectionPipeline.applyFilterToggleGeneric(results, isFiltering);
        }

        this.recipesPage.setResults(results, resetPageNumber, selectedTab.getCategory());
    }

    private boolean matchesSearch(C collection, SearchQuery query, SearchCache cache) {
        for (R recipe : collection.getRecipes()) {
            ItemStack result = recipe.getResult(registryAccess, selectedTab.getCategory());
            if (result != null && !result.isEmpty() && query.matches(result, cache)) {
                return true;
            }
        }
        return false;
    }

    private void pirateSpeechForThePeople(String string) {
        if ("excitedze".equals(string)) {
            LanguageManager languageManager = this.minecraft.getLanguageManager();
            String string2 = "en_pt";
            LanguageInfo languageInfo = languageManager.getLanguage("en_pt");
            if (languageInfo == null || languageManager.getSelected().equals("en_pt")) {
                return;
            }
            languageManager.setSelected("en_pt");
            this.minecraft.options.languageCode = "en_pt";
            this.minecraft.reloadResourcePacks();
            this.minecraft.options.save();
        }
    }

    private boolean isOffsetNextToMainGUI() {
        return this.xOffset == BookLayout.X_OFFSET_STANDARD;
    }

    @Override
    @NotNull
    public NarratableEntry.NarrationPriority narrationPriority() {
        return this.isVisible() ? NarratableEntry.NarrationPriority.HOVERED : NarratableEntry.NarrationPriority.NONE;
    }

    protected void setVisible(boolean visible) {
        BRBBookSettings.setOpen(getRecipeBookType(), visible);
        this.visible = visible;
        if (!visible) {
            // 收起配方书 = 悬停结束（幽灵物品的渲染本就只在书可见时进行）
            this.brbe$hoverGhostRecipe = null;
            // 收起配方书 = 幽灵**不再显示** → 外部预览（锻造台界面的盔甲架）此刻复位。
            // 幽灵对象本身按原设计保留（点击留下的缺料引导不因收书而结束），只是不再占着外部预览。
            if (this.ghostRecipe != null) {
                this.ghostRecipe.releaseExternalPreview();
            }
        }
    }

    public boolean isVisible() {
        return visible;
    }

    public void toggleVisibility() {
        this.setVisible(!this.isVisible());
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return false;
    }

    public boolean hasClickedOutside(double d, double e, int i, int j, int k, int l, int m) {
        if (!this.isVisible()) {
            return true;
        }
        int bookLeft = (this.width - BookLayout.TEXTURE_WIDTH) / 2 - this.xOffset;
        boolean bl = d < (double) i || e < (double) j || d >= (double) (i + k) || e >= (double) (j + l);
        boolean bl2 = (double) (bookLeft) < d && d < (double) i && (double) j < e && e < (double) (j + l);
        return bl && !bl2;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!this.isVisible()) return false;
        int bookLeft = (this.width - BookLayout.TEXTURE_WIDTH) / 2 - this.xOffset;
        int bookTop = (this.height - BookLayout.TEXTURE_HEIGHT) / 2;
        boolean handled = this.recipesPage.mouseScrolled(mouseX, mouseY, scrollX, scrollY,
                bookLeft, bookTop,
                BookLayout.TEXTURE_WIDTH, BookLayout.TEXTURE_HEIGHT);
        if (handled) {
            BetterRecipeBook.setQueuedScroll(0);
        }
        return handled;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!this.isVisible()) return false;

        int bookLeft = (this.width - BookLayout.TEXTURE_WIDTH) / 2 - this.xOffset;
        int bookTop = (this.height - BookLayout.TEXTURE_HEIGHT) / 2;

        if (this.recipesPage.mouseClicked(mouseX, mouseY, button,
                bookLeft, bookTop,
                BookLayout.TEXTURE_WIDTH, BookLayout.TEXTURE_HEIGHT)) {
            this.handlePlaceRecipe();
            return true;
        }

        if (button == 1 && this.searchBox.isMouseOver(mouseX, mouseY)) {
            searchBox.setValue("");
            searchBox.setFocused(true);
            this.updateCollections(true);
            return true;
        }

        if (this.searchBox.mouseClicked(mouseX, mouseY, button)) {
            searchBox.setFocused(true);
            ignoreTextInput = true;
            return true;
        }

        searchBox.setFocused(false);
        ignoreTextInput = false;

        if (this.filterButton.visible && this.filterButton.mouseClicked(mouseX, mouseY, button)) {
            boolean bl = this.toggleFiltering();
            this.filterButton.setStateTriggered(bl);
            this.updateFilterButtonTooltip();
            this.updateCollections(false);
            return true;
        }

        if (ISettingsButton.super.settingsButtonMouseClicked(this.settingsButton, mouseX, mouseY, button)) {
            return true;
        }

        Iterator<BRBGroupButtonWidget> tabButtonsIter = this.tabButtons.iterator();

        BRBGroupButtonWidget widget;
        if (!tabButtonsIter.hasNext()) {
            return false;
        }

        widget = tabButtonsIter.next();
        while (!widget.mouseClicked(mouseX, mouseY, button)) {
            if (!tabButtonsIter.hasNext()) {
                return false;
            }

            widget = tabButtonsIter.next();
        }

        if (this.selectedTab != widget) {
            if (this.selectedTab != null) {
                this.selectedTab.setStateTriggered(false);
            }

            this.selectedTab = widget;
            this.selectedTab.setStateTriggered(true);
            this.updateCollections(true);
        }

        return false;
    }

    protected boolean toggleFiltering() {
        // 「优化原版配方过滤器」开启时按钮已隐藏：过滤状态恒关，不允许切回
        //（配方全显示，优先级交给「可合成置顶」排序）。
        if (BRBBookSettings.partialFilterMode()) {
            BRBBookSettings.setFiltering(this.getRecipeBookType(), false);
            return false;
        }
        boolean bl = !BRBBookSettings.isFiltering(this.getRecipeBookType());
        BRBBookSettings.setFiltering(this.getRecipeBookType(), bl);

        return bl;
    }

    @Override
    public void updateNarration(NarrationElementOutput narrationElementOutput) {
    }

    @Override
    public void setFocused(boolean bl) {
    }

    @Override
    public boolean isFocused() {
        return false;
    }

    protected void updateFilterButtonTooltip() {
        this.filterButton.setTooltip(this.filterButton.isStateTriggered() ? Tooltip.create(this.getRecipeFilterName()) : Tooltip.create(ALL_RECIPES_TOOLTIP));
    }

    public int findLeftEdge(int width, int backgroundWidth) {
        int j;
        if (this.isVisible() && !this.widthTooNarrow) {
            j = 177 + (width - backgroundWidth - 200) / 2;
        } else {
            j = (width - backgroundWidth) / 2;
        }

        return j;
    }

    public void drawTooltip(GuiGraphics gui, int x, int y, int mouseX, int mouseY) {
        if (!this.isVisible()) {
            return;
        }

        // 替代配方组浮层打开时，普通配方格的 tooltip 会透过浮层冒出来，所以原样保留
        // 「浮层打开就不问页面」的判定；但**浮层里自己那一格**的 tooltip 要给出
        // （用户 2026-09-27 诉求：组内格子的 tooltip 与普通配方格同款）。
        java.util.List<net.minecraft.network.chat.Component> overlayTip = this.recipesPage.overlayTooltip();
        if (overlayTip != null && !overlayTip.isEmpty()) {
            gui.renderComponentTooltip(Minecraft.getInstance().font, overlayTip, mouseX, mouseY);
        } else if (!this.recipesPage.overlayIsVisible()) {
            this.recipesPage.drawTooltip(gui, mouseX, mouseY);

            ISettingsButton.super.renderSettingsButtonTooltip(this.settingsButton, gui, mouseX, mouseY);
        }

        this.ghostRecipe.drawTooltip(gui, x, y, mouseX, mouseY);
        this.brbe$lastHoveredGhostItem = this.ghostRecipe.getLastHoveredItem();
    }

    protected void refreshTabButtons() {
        int i = (this.width - BookLayout.TEXTURE_WIDTH) / 2 - this.xOffset - BookLayout.TAB_BUTTON_WIDTH + 1;
        int j = (this.height - BookLayout.TEXTURE_HEIGHT) / 2 + BookLayout.TAB_TOP_OFFSET;
        int l = 0;

        for (BRBGroupButtonWidget button : this.tabButtons) {
            BRBBookCategories.Category category = button.getCategory();
            if (category.getType() == BRBBookCategories.Category.Type.SEARCH) {
                button.visible = true;
            }
            button.setPosition(i, j + BookLayout.TAB_BUTTON_SPACING * l++);
        }
    }

    public void renderGhostRecipe(GuiGraphics guiGraphics, int x, int y, boolean bl, float delta) {
        if (selectedTab == null || ghostRecipe == null) return;

        this.ghostRecipe.render(guiGraphics, this.minecraft, x, y, bl, delta, selectedTab.getCategory());
    }

    /**
     * 悬停即预览（用户 2026-09-25 诉求）：指针停在配方按钮上就把该配方的幽灵物品
     * 直接放进工作区（酿造台/锻造台槽位），移开立刻清掉。
     *
     * <p>与工作台（原版配方书）同语义：<b>可合成配方也显示</b>幽灵物品——酿造/锻造的
     * 幽灵渲染谓词只画空槽位，所以已经放好的材料不会被盖住。</p>
     *
     * <p>只在"光标下的配方换了"时重写：点击放置（{@code handlePlaceRecipe}）自己
     * 写/清幽灵之后，同一按钮上的悬停不再插手（否则会把点击留下的缺料引导顶掉）。</p>
     *
     * <p>指针**离开**按钮时不再一律清空：若工作区里还留着点击写入的缺料引导
     * （{@link #brbe$placedGhostRecipe}），把它还原回来——点击引导是持续的，
     * 悬停预览只是临时盖在它上面（用户 2026-09-26 反馈：点击后引导被鼠标移开抹掉，
     * 看起来像"点击不填充幽灵配方"）。</p>
     */
    private void brbe$updateHoverGhost() {
        if (this.recipesPage == null || this.ghostRecipe == null) return;
        if (!HoverGhostRecipe.enabled()) {
            // 配置「自动填充幽灵配方」关闭：撤下**悬停预览**（点击放置的引导属于原版语义，保留）。
            // 用 size()==0 判断"当前显示的是不是悬停预览"，避免每帧 clear+重写把轮循计时清零。
            this.brbe$hoverGhostRecipe = null;
            if (this.ghostRecipe.size() > 0) {
                this.ghostRecipe.clear();
            }
            if (this.brbe$placedGhostRecipe != null && this.ghostRecipe.size() == 0) {
                this.setupHoverGhost(this.brbe$placedGhostRecipe);
            }
            return;
        }

        // 幽灵预览源 = hoverGhostRecipe（而不是 hoveredRecipe）：不提供预览的格子在那里是 null
        R hovered = this.recipesPage.hoverGhostRecipe;
        // 悬停目标变了 → 清掉"本目标已交给原版"（点击放置后）并重算「工作区已经摆好这条配方」
        // （探针：写一次幽灵→逐格比对→清掉）；抑制期间**心跳也要关**，否则"暂隐工作区真实物品"
        // 会把摆好的材料整片藏掉。
        if (hovered != this.brbe$hoverGhostRecipe) {
            // 同一条配方的**等价对象**（页面因槽位变化重建、同格轮循换出新实例）不算换了目标：
            // 交接标记与工作区里的幽灵都保持原样——否则点击放置后的第一帧会把刚放好的真实物品
            // 又藏起来 / 把点击留下的缺料引导清掉（用户 2026-09-27 实测）。
            if (HoverGhostRecipe.isHandedOver() && this.brbe$sameRecipeId(hovered, this.brbe$hoverGhostRecipe)) {
                this.brbe$hoverGhostRecipe = hovered;
                return;
            }
            HoverGhostRecipe.clearHandedOver();
            this.brbe$hoverSuppressed = hovered != null && this.brbe$recipeLaidOutInWorkspace(hovered);
        }
        // 点击放置（材料齐 → 真实物品进工作区 / 材料不齐 → 原版缺料引导）后**鼠标还停在原格**时
        // 不再预览：否则"暂隐工作区真实物品"会把刚放好的真实物品当场藏起来（用户 2026-09-27 实测）。
        boolean handedOver = HoverGhostRecipe.isHandedOver();
        // 「暂隐工作区真实物品」心跳：本方法在书体可见时每帧跑，所以这里就是"预览是否在
        // 显示"的权威信号（界面关闭后不再有心跳，HoverGhostRecipe 侧超时自动失效）。
        // 预览显示期间**工作区真实物品一律隐藏**（幽灵覆盖不到的位置也藏——用户 2026-09-26：
        // 否则那些格子里的真实物品会和幽灵混在一起）。
        HoverGhostRecipe.setGenericPreviewing(hovered != null && !this.brbe$hoverSuppressed && !handedOver);

        // **真正的**无主幽灵：指针不在配方上、既没有点击引导、也没有正在显示的预览，幽灵却非空
        // （书收起再打开、外部清空后的残留）→ 清掉。
        if (hovered == null && this.brbe$placedGhostRecipe == null && this.brbe$hoverGhostRecipe == null
                && this.ghostRecipe.size() > 0) {
            this.ghostRecipe.clear();
            return;
        }

        if (hovered == this.brbe$hoverGhostRecipe) return;

        // 悬停预览**开始**（从"没有预览"进入）：记下起始时刻。同一格内轮循换配方不重置——
        // 酿造台一个格子就是"同产物的一组路线"，每 1.5s 换一条，重置的话"停留"永远算不出来。
        if (hovered != null && this.brbe$hoverGhostRecipe == null) {
            this.brbe$hoverStartedAt = System.currentTimeMillis();
        }
        long previewMs = this.brbe$hoverGhostRecipe == null
                ? 0L
                : System.currentTimeMillis() - this.brbe$hoverStartedAt;
        R leftRecipe = this.brbe$hoverGhostRecipe;

        this.brbe$hoverGhostRecipe = hovered;
        this.ghostRecipe.clear();
        if (hovered != null) {
            if (this.brbe$hoverSuppressed || handedOver) {
                // 工作区已经摆好这条配方 / 本目标已交给原版 → 不预览（用户 2026-09-27）：
                // 不写幽灵、不藏真实物品
                return;
            }
            this.setupHoverGhost(hovered);
            return;
        }

        // 指针离开了配方：点击留下的缺料引导怎么办？
        if (this.brbe$placedGhostRecipe == null) {
            return;
        }
        // ① 离开的就是引导自己那条（同一组的各条路线 id 相同）→ 照旧还原（内容本来一样）
        // ② 只是**掠过**别的格子（鼠标从被点的格子移向工作区，< 300ms）→ 还原，
        //    否则又回到"点击后引导被鼠标移开抹掉"的老问题
        // ③ 在别的配方上**停留**过（≥ 300ms）→ 玩家已经在浏览别的配方：引导就此结束，
        //    离开后工作区保持干净（用户 2026-09-26 反馈："来源无规律"的持久幽灵）
        boolean sameAsGuide = this.brbe$sameRecipeId(leftRecipe, this.brbe$placedGhostRecipe);
        if (!sameAsGuide && previewMs >= GUIDE_SUPERSEDE_MS) {
            this.brbe$placedGhostRecipe = null;
            return;
        }
        this.setupHoverGhost(this.brbe$placedGhostRecipe);
    }

    /** 把 {@code recipe} 的幽灵物品写进工作区（子类按各自槽位布局实现）。 */
    protected abstract void setupHoverGhost(R recipe);

    /**
     * 这条配方在**工作区里已经摆好**了吗（用户 2026-09-27 收尾诉求）。
     *
     * <p>做法是**探针**：把配方写进幽灵（{@link #setupHoverGhost}）→ 逐格与工作区实物比对
     * （{@link GenericGhostRecipe#isLaidOutInWorkspace}）→ 立刻 {@code clear()}；调用方随后
     * 照常 {@code setupHoverGhost} 或按抑制处理。只在悬停目标变化时调用，不是每帧开销。</p>
     */
    private boolean brbe$recipeLaidOutInWorkspace(R recipe) {
        if (this.ghostRecipe == null) return false;
        this.setupHoverGhost(recipe);
        boolean laidOut = this.ghostRecipe.isLaidOutInWorkspace(this.menu);
        this.ghostRecipe.clear();
        return laidOut;
    }

    protected abstract List<C> getCollectionsForCategory();

    @Override
    public void recipesShown(List<RecipeHolder<?>> list) {

    }
}
