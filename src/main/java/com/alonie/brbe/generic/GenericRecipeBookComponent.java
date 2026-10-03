package com.alonie.brbe.generic;

import com.google.common.collect.Lists;
import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.api.BRBBookCategories;
import com.alonie.brbe.api.BRBBookSettings;
import com.alonie.brbe.compat.ItemViewCompat;
import com.alonie.brbe.mixins.accessors.GenericRecipePageAccessor;
import com.alonie.brbe.mixins.accessors.RecipeBookComponentAccessor;
import com.alonie.brbe.util.HoverGhostRecipe;
import com.alonie.brbe.util.RecipeBookPositionMemory;
import com.alonie.brbe.util.RecipeExtraction;
import com.alonie.brbe.util.SortCategory;
import com.alonie.brbe.interfaces.IPinningComponent;
import com.alonie.brbe.interfaces.ISettingsButton;
import com.alonie.brbe.search.SearchCache;
import com.alonie.brbe.search.SearchQuery;
import com.alonie.brbe.util.ClientCompat;
import com.alonie.brbe.util.BRBHelper;
import com.alonie.brbe.util.BRBTextures;
import com.alonie.brbe.util.SearchPageJump;
import com.alonie.brbe.widget.StateSwitchingButton;
import net.minecraft.client.Minecraft;
import com.alonie.brbe.widget.StateSwitchingButton;
import net.minecraft.client.gui.GuiGraphics;
import com.alonie.brbe.widget.StateSwitchingButton;
import net.minecraft.client.gui.components.*;
import com.alonie.brbe.widget.StateSwitchingButton;
import net.minecraft.client.gui.components.events.GuiEventListener;
import com.alonie.brbe.widget.StateSwitchingButton;
import net.minecraft.client.input.CharacterEvent;
import com.alonie.brbe.widget.StateSwitchingButton;
import net.minecraft.client.input.KeyEvent;
import com.alonie.brbe.widget.StateSwitchingButton;
import net.minecraft.client.input.MouseButtonEvent;
import com.alonie.brbe.widget.StateSwitchingButton;
import net.minecraft.client.gui.narration.NarratableEntry;
import com.alonie.brbe.widget.StateSwitchingButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import com.alonie.brbe.widget.StateSwitchingButton;
import net.minecraft.client.resources.language.LanguageInfo;
import com.alonie.brbe.widget.StateSwitchingButton;
import net.minecraft.client.resources.language.LanguageManager;
import com.alonie.brbe.widget.StateSwitchingButton;
import net.minecraft.core.RegistryAccess;
import com.alonie.brbe.widget.StateSwitchingButton;
import net.minecraft.network.chat.Component;
import com.alonie.brbe.widget.StateSwitchingButton;
import net.minecraft.world.entity.player.StackedItemContents;
import com.alonie.brbe.widget.StateSwitchingButton;
import net.minecraft.world.inventory.AbstractContainerMenu;
import com.alonie.brbe.widget.StateSwitchingButton;
import net.minecraft.world.item.ItemStack;
import com.alonie.brbe.widget.StateSwitchingButton;
import net.minecraft.world.item.crafting.RecipeManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;

public abstract class GenericRecipeBookComponent<M extends AbstractContainerMenu, C extends GenericRecipeBookCollection<R, M>, R extends GenericRecipe> implements Renderable, NarratableEntry, GuiEventListener, ISettingsButton, IPinningComponent<C> {
    protected static final Component SEARCH_HINT = RecipeBookComponentAccessor.getSEARCH_HINT();
    protected static final Component ALL_RECIPES_TOOLTIP = Component.translatable("gui.recipebook.toggleRecipes.all");
    boolean visible;
    protected boolean ignoreTextInput;
    protected Minecraft minecraft;
    protected EditBox searchBox;
    /**
     * 上一次已应用的搜索词。
     *
     * <p>⚠️ 必须初始化成 {@code ""}（用户 2026-09-27 反馈：锻造台 / 酿造台"搜索要切标签才刷新"
     * ——实机日志实锤 {@code NullPointerException: this.lastSearch is null} 抛在
     * {@link #checkSearchStringUpdate()}，被原版 {@code KeyboardHandler} 吞掉并写进日志，
     * 于是输入框有字、配方区却一直停在旧结果，切标签时的 {@code updateCollections} 才把
     * 搜索词应用上）。判空兜底也一并留在 {@link #checkSearchStringUpdate()}。</p>
     */
    private String lastSearch = "";
    protected int xOffset;
    protected boolean widthTooNarrow;
    protected int width;
    protected int height;
    protected M menu;
    protected final StackedItemContents stackedContents = new StackedItemContents();
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
     * 悬停预览正在展示的配方（用户 2026-09-25 诉求）。与 {@code recipesPage.hoveredRecipe}
     * 比较：只有"指针换了一个配方"时才重写幽灵物品，因此点击（{@code handlePlaceRecipe}
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

    /** 交接保持：可合成配方点击放置后，幽灵留到真实物品到位（用户 2026-09-27 反馈的空窗期）。 */
    @Nullable
    private R brbe$handoverRecipe;
    /** 交接保持的起始时刻（TTL 兜底）。 */
    private long brbe$handoverAt;
    /** 交接保持上限：真实物品由服务端放置，本机一两个 tick 就到。 */
    private static final long HANDOVER_HOLD_MS = 600L;

    /**
     * 点击**可合成**配方 → 幽灵不立刻撤下，保持到真实物品真的进了工作区。
     *
     * <p>成因：真实物品是服务端放的（客户端只发点击包），本机也要一两个 tick 才出现在客户端
     * 槽位里；点击那一刻就清幽灵的话，这中间工作区**什么都没有**（用户 2026-09-27 反馈的
     * 视觉割裂）。保持期间真实物品继续被暂隐，于是物品到位时是"幽灵换成实物"、看不出接缝。</p>
     */
    protected final void brbe$beginPlacementHandover(R recipe) {
        this.brbe$placedGhostRecipe = null;
        this.brbe$handoverRecipe = recipe;
        this.brbe$handoverAt = System.currentTimeMillis();
        this.setupHoverGhost(recipe);
        HoverGhostRecipe.markHandedOver();
    }

    /** 交接保持是否在进行（界面 mixin 用它挡掉"槽位一变就结束引导"——放置本身就会改槽位）。 */
    public final boolean brbe$isHandoverHolding() {
        return this.brbe$handoverRecipe != null;
    }

    /**
     * 交接保持的逐帧推进：工作区真的摆好了这条配方（或超时）即结束。
     *
     * @return true = 本帧由交接保持接管（调用方跳过普通悬停逻辑）
     */
    private boolean brbe$tickPlacementHandover() {
        R recipe = this.brbe$handoverRecipe;
        if (recipe == null) {
            return false;
        }
        // 幽灵此刻就是这条配方（beginPlacementHandover 写的）→ 直接拿它判"工作区摆好了没"
        boolean laidOut = this.ghostRecipe != null && this.ghostRecipe.isLaidOutInWorkspace(this.menu);
        long now = System.currentTimeMillis();
        if (laidOut || now - this.brbe$handoverAt > HANDOVER_HOLD_MS) {
            this.brbe$handoverRecipe = null;
            this.brbe$endGhostGuide();
            return true;
        }
        // 保持：幽灵原样留着、真实物品继续暂隐
        HoverGhostRecipe.setGenericPreviewing(true);
        return true;
    }

    /**
     * 固定键的落点判定 —— 与工作台原版书**同一套规则**：
     * ① 组浮层打开时只固定浮层里悬停的那个**变体**（没悬停就吞键，防止穿透 pin 到下层组）；
     * ② 网格上只有**单配方格**能直接固定，多变体组吞键（组只能在打开后逐个固定变体）。
     *
     * @return true = 本次按键已处理（含"故意吞掉"）
     */
    private boolean brbe$togglePinUnderCursor() {
        if (this.recipesPage == null) {
            return false;
        }
        if (this.recipesPage.overlayIsVisible()) {
            this.brbe$togglePinRecipe(this.recipesPage.hoveredRecipe);
            return true;
        }
        for (GenericRecipeButton<C, R, M> button : this.recipesPage.buttons) {
            // 隐藏按钮的 isHovered 是陈旧值（不可见时不再刷新悬停），与原版书同一处理
            if (!button.visible || !button.isHoveredOrFocused()) continue;
            C collection = button.getCollection();
            if (collection != null && collection.getRecipes().size() == 1) {
                this.brbe$togglePinRecipe(collection.getRecipes().get(0));
            }
            return true;
        }
        return false;
    }

    /** 固定/取消固定**单条**配方（键 = 配方 id）；成功时刷页并播点击音效。 */
    private boolean brbe$togglePinRecipe(@Nullable R recipe) {
        if (recipe == null || recipe.id() == null) {
            return false;
        }
        BetterRecipeBook.pinnedRecipeManager.toggleFavourite(recipe);
        this.updateCollections(false);
        if (this.minecraft != null && this.minecraft.getSoundManager() != null) {
            net.minecraft.client.gui.components.AbstractWidget
                    .playButtonClickSound(this.minecraft.getSoundManager());
        }
        return true;
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
        // 交接保持一并结束（外部清空/放置完成都会走到这里）
        this.brbe$handoverRecipe = null;
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


//    private int timesInventoryChanged;

    protected GenericRecipeBookComponent() {
    }

    abstract public Component getRecipeFilterName();

    abstract public BRBHelper.Book getRecipeBookType();

    public void init(int parentWidth, int parentHeight, Minecraft client, boolean narrow, M menu, RegistryAccess registryAccess) {
        this.init(parentWidth, parentHeight, client, narrow, menu, null, registryAccess);
    }

    public void init(int width, int height, Minecraft minecraft, boolean widthNarrow, M menu, @Nullable Consumer<ItemStack> onGhostRecipeUpdate, RegistryAccess registryAccess) {
        BetterRecipeBook.ensureCategories();
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

//        this.timesInventoryChanged = minecraft.player.getInventory().getTimesChanged();
    }

    public void initVisuals() {
        if (BetterRecipeBook.config.keepCentered) {
            this.xOffset = this.widthTooNarrow ? 0 : 162;
        } else {
            this.xOffset = this.widthTooNarrow ? 0 : 86;
        }

        int i = (this.width - 147) / 2 - this.xOffset;
        int j = (this.height - 166) / 2;
        this.stackedContents.clear();
        if (this.minecraft.player == null) return;
        this.minecraft.player.getInventory().fillStackedContents(this.stackedContents);
        // TODO: menu.fillCraftSlotsStackedContents
//        this.menu.fillCraftSlotsStackedContents(this.stackedContents);
        String string = this.searchBox != null ? this.searchBox.getValue() : "";
        Objects.requireNonNull(this.minecraft.font);
        this.searchBox = new EditBox(this.minecraft.font, i + 25, j + 13, 81, this.minecraft.font.lineHeight + 5, Component.translatable("itemGroup.search"));
        this.searchBox.setMaxLength(50);
        this.searchBox.setVisible(true);
        this.searchBox.setTextColor(0xFFFFFFFF);
        this.searchBox.setValue(string);
        this.searchBox.setHint(SEARCH_HINT);
        this.settingsButton = createSettingsButton(i, j);
        this.recipesPage.initialize(this.minecraft, i, j, menu, xOffset);
        this.tabButtons.clear();
        this.filterButton = new StateSwitchingButton(i + 110, j + 12, 26, 16, false);
        this.filterButton.useStateTriggeredForTexture(true);
        this.filterButton.setStateTriggered(BRBBookSettings.isFiltering(this.getRecipeBookType()));
        this.filterButton.initTextureValues(BRBTextures.filterButtonFor(this.getRecipeBookType()));
        this.updateFilterButtonTooltip();
        // 「优化原版配方过滤器」（用户 2026-09-27 诉求）：与原版书同一套处理——按钮隐藏
        // （配方全显示，优先级交给排序），搜索栏加宽到 97 居中占位（左右各距书缘 25）。
        if (BRBBookSettings.partialFilterMode()) {
            this.filterButton.visible = false;
            this.filterButton.active = false;
            this.searchBox.setWidth(97);
        }

        List<BRBBookCategories.Category> categories = BRBBookCategories.getCategories(this.getRecipeBookType());

        if (categories == null || categories.isEmpty()) {
            // Categories not yet registered — silently degrade.  The next
            // screen open / initVisuals call will retry ensureCategories().
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

        gui.pose().pushMatrix();

        // blit recipe book background texture
        int blitX = (this.width - 147) / 2 - this.xOffset;
        int blitY = (this.height - 166) / 2;
        gui.blit(ClientCompat.GUI_TEXTURED, BRBTextures.RECIPE_BOOK_BACKGROUND_TEXTURE, blitX, blitY, 1.0F, 1.0F, 147, 166, 256, 256);

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

        // 悬停即预览：页面渲染后 hoveredRecipe 已是本帧光标下的配方
        this.brbe$updateHoverGhost();

        gui.pose().popMatrix();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        return this.keyPressed(event.key(), event.scancode(), event.modifiers());
    }

    public boolean keyPressed(int i, int j, int k) {
        this.ignoreTextInput = false;
        if (!this.isVisible() || this.minecraft.player != null && this.minecraft.player.isSpectator()) {
            return false;
        }
        /* causes escape needing to be pressed twice to exit menu.
        I don't think this was intentional? -Tau
        if (i == 256 && !this.isOffsetNextToMainGUI()) {
            this.setVisible(false);
            return true;
        }*/
        if (ClientCompat.keyPressed(this.searchBox, i, j, k)) {
            this.checkSearchStringUpdate();
            return true;
        }
        if (this.searchBox.isFocused() && this.searchBox.isVisible() && i != 256) {
            return true;
        }
        if (ClientCompat.matches(this.minecraft.options.keyChat, i, j, k) && !this.searchBox.isFocused()) {
            this.ignoreTextInput = true;
            this.searchBox.setFocused(true);
            return true;
        }

        if (ClientCompat.matchesPinKey(i, j, k)) {
            if (this.brbe$togglePinUnderCursor()) {
                return true;
            }
        }

        // JEI/REI integration: open recipe/usage views for hovered item.
        // Key matching delegates to each viewer's own configured key bindings.
        if (ItemViewCompat.isLoaded()) {
            // ── 1. Recipe buttons ──────────────────────────────────────
            if (this.recipesPage.hoveredButton != null && this.recipesPage.hoveredRecipe != null) {
                R hoveredRecipe = this.recipesPage.hoveredRecipe;
                if (hoveredRecipe != null) {
                    ItemStack hoveredStack = hoveredRecipe.getResult(registryAccess, this.recipesPage.hoveredCategory);
                    if (ItemViewCompat.matchesShowRecipe(i, j)) {
                        return ItemViewCompat.openRecipeView(hoveredStack);
                    }
                    if (ItemViewCompat.matchesShowUses(i, j)) {
                        return ItemViewCompat.openUsageView(hoveredStack);
                    }
                }
            }

            // ── 2. Ghost items ─────────────────────────────────────────
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
    public boolean keyReleased(KeyEvent event) {
        return this.keyReleased(event.key(), event.scancode(), event.modifiers());
    }

    public boolean keyReleased(int i, int j, int k) {
        this.ignoreTextInput = false;
        return GuiEventListener.super.keyReleased(ClientCompat.keyEvent(i, j, k));
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        return this.charTyped((char) event.codepoint(), 0);
    }

    public boolean charTyped(char c, int i) {
        if (this.ignoreTextInput) {
            return false;
        }
        if (!this.isVisible() || this.minecraft.player != null && this.minecraft.player.isSpectator()) {
            return false;
        }
        if (ClientCompat.charTyped(this.searchBox, c, i)) {
            this.checkSearchStringUpdate();
            return true;
        }
        return GuiEventListener.super.charTyped(ClientCompat.characterEvent(c, i));
    }

    private void checkSearchStringUpdate() {
        // 页码跳转命令（^N^ / ……N^ / ^N…… / ……N……）优先级最高：命中即跳页并清空搜索
        if (brbe$handlePageJumpCommand()) {
            return;
        }
        String string = this.searchBox.getValue().toLowerCase(Locale.ROOT);
        this.pirateSpeechForThePeople(string);
        // 判空兜底：lastSearch 理论上已初始化为 ""，旧版本遗留 null 时不再抛 NPE（见字段注释）
        String previous = this.lastSearch == null ? "" : this.lastSearch;
        if (!string.equals(previous)) {
            // 首次输入搜索词（空 → 非空）：回到第 1 页，从结果开头看
            boolean searchStarted = !string.isEmpty() && previous.isEmpty();
            // 清空搜索（非空 → 空）：恢复搜索前浏览的页码
            boolean searchCleared = string.isEmpty() && !previous.isEmpty();
            this.updateCollections(false);
            this.lastSearch = string;
            if (BetterRecipeBook.config.saveRecipeBookPosition) {
                if (searchCleared) {
                    this.restoreBrowsingPageAfterSearchClear();
                } else if (searchStarted) {
                    this.resetPageToFirst();
                }
            }
        }
    }

    /**
     * 首次输入搜索词后回到第 1 页：搜索从结果开头看，
     * 搜索前的位置由 basePage 记忆，清空搜索时再恢复。
     */
    private void resetPageToFirst() {
        if (this.recipesPage == null) return;
        ((GenericRecipePageAccessor) this.recipesPage).setCurrentPage(0);
        this.recipesPage.resetVisualPosition();
        this.recipesPage.updateButtonsForPage();
    }

    /**
     * 搜索词清空后恢复清空前的浏览页码：页码来自该标签记忆中的 basePage
     * （空搜索状态下持续更新的页码），钳制到当前列表范围。
     */
    private void restoreBrowsingPageAfterSearchClear() {
        if (this.selectedTab == null || this.recipesPage == null) return;
        int tabIndex = this.tabButtons.indexOf(this.selectedTab);
        if (tabIndex < 0) return;
        RecipeBookPositionMemory.Pos pos = RecipeBookPositionMemory.load(
                this.getRecipeBookType().Identifier.toString(), tabIndex);
        if (pos == null) return;
        int max = Math.max(0, ((GenericRecipePageAccessor) this.recipesPage).getTotalPages() - 1);
        ((GenericRecipePageAccessor) this.recipesPage).setCurrentPage(Math.min(pos.basePage(), max));
        this.recipesPage.resetVisualPosition();
        this.recipesPage.updateButtonsForPage();
    }

    /**
     * 搜索栏页码跳转命令：跳到第 N 页，清空搜索栏并取消聚焦。
     * 页码 1-indexed；格式不对（parse 返回 -1）或页码超出总页数时返回 false，
     * 由调用方走普通搜索（显示空页），保留输入和聚焦。
     */
    private boolean brbe$handlePageJumpCommand() {
        int page = SearchPageJump.parse(this.searchBox);
        if (page <= 0) return false;
        // 用完整类别列表的总页数判断页码合法性。不能用当前 recipesPage.totalPages：
        // 输入命令过程中间态的搜索词会把列表过滤空，合法页码会被误判为超范围。
        int fullTotalPages = (int) Math.ceil(this.getCollectionsForCategory().size() / 20.0D);
        if (page > fullTotalPages) {
            // 页码不存在：保留输入和聚焦，走普通搜索（无结果自然显示空页）
            return false;
        }
        // 先清空搜索（含 IME 组合残留）并恢复完整列表（页码重置到第 0 页），再跳转目标页
        this.searchBox.setValue("");
        this.searchBox.setFocused(false);
        this.lastSearch = "";
        this.updateCollections(true);
        this.recipesPage.flipTo(page - 1);
        return true;
    }

    /** 一个配方是否命中当前搜索（与 {@link #brbe$matchesSearch} 同一套判据）。 */
    private boolean brbe$recipeMatchesSearch(R recipe, SearchQuery query, SearchCache cache) {
        if (recipe == null) return false;
        ItemStack result = recipe.getResult(registryAccess, selectedTab.getCategory());
        return result != null && !result.isEmpty() && query.matches(result, cache);
    }

    /** 一个集合里是否有命中的配方（保留 = 至少一条命中）。 */
    private boolean brbe$matchesSearch(C collection, SearchQuery query, SearchCache cache) {
        for (R recipe : collection.getRecipes()) {
            if (this.brbe$recipeMatchesSearch(recipe, query, cache)) {
                return true;
            }
        }
        return false;
    }

    /**
     * **排序原因剥离**（用户 2026-09-27 诉求）：与原版书
     * （{@code CollectionPipeline.applySortExtraction}）共用 {@link RecipeExtraction} 通用核心。
     *
     * <p>组内"需要调整排序"的变体（pin / 可合成 / 残缺 / 搜索命中）从原组剥出来，**按类别各自
     * 成组**（同类别多个变体合成子组、单个则是单配方组），原组只保留基线（最低类别）变体且
     * 位置不变；子组再参与既有排序（pin 置顶 / 可合成 → 残缺 → 其余），并**多层递归**
     * （可合成子组里被 pin 的变体再剥出孙组——某个组可以同时是父组和子组）。</p>
     *
     * <p>搜索时未命中的变体不可见 → 原组可能被整体丢弃，界面上只留下命中的那部分
     * （用户选择："原组隐藏，只展示命中的子组"）。</p>
     */
    private List<C> brbe$extractBySortReason(List<C> results,
                                             @Nullable SearchQuery query,
                                             @Nullable SearchCache cache) {
        final SearchQuery fQuery = query;
        final SearchCache fCache = cache;
        return RecipeExtraction.extract(results, new RecipeExtraction.Plan<C, R>() {

            @Override
            public List<R> entries(C collection) {
                return collection.getRecipes();
            }

            @Override
            public SortCategory category(C collection, R recipe) {
                if (recipe == null) return SortCategory.NORMAL;
                if (BetterRecipeBook.pinnedRecipeManager.pinned.contains(recipe.id())) {
                    return SortCategory.PINNED;
                }
                // ⚠️ 残缺**先于**可合成判定（与 CollectionPipeline.applySortExtraction 及
                // Stage 4 的 partial-优先优先级一致）。自研书这两个谓词当前互斥
                // （isCraftable = hasMaterials，partial = !hasMaterials && hasPartialMaterials），
                // 但顺序统一后，将来任一侧引入"残缺也进 craftable"的注入都不会让
                // 残缺变体与真可合成变体并进同一个子组。
                // 两个判据都走集合的槽位状态缓存（本方法对每条配方问 2～3 次）
                if (collection.isPartiallyCraftable(recipe)) {
                    return SortCategory.PARTIAL;
                }
                if (collection.isCraftable(recipe, menu.slots)) {
                    return SortCategory.CRAFTABLE;
                }
                return SortCategory.NORMAL;
            }

            @Override
            public boolean visible(C collection, R recipe) {
                return fQuery == null || brbe$recipeMatchesSearch(recipe, fQuery, fCache);
            }

            @SuppressWarnings("unchecked")
            @Override
            public C subset(C parent, List<R> entries) {
                // 具体书集合的 subset 是协变覆写（返回自身类型），此转换恒安全
                return (C) parent.subset(entries);
            }

            /**
             * 剥离子组打标记（用户 2026-09-27 三次反馈）：折叠展示（锻造台纹饰组的模板 /
             * 升级组的锭）只属于原组那一格，子格要画自己那几条配方的产物——否则从纹饰组
             * pin 出来的一条画的是纹饰模板，看不出 pin 的是哪件装备。
             * 重打包的原组不走这里（它仍是原组）。
             */
            @Override
            public void onSubgroupPack(C parent, C pack) {
                pack.markExtractionSubgroup();
            }
        });
    }

    protected void updateCollections(boolean b) {
        if (this.selectedTab == null) return;
        if (this.searchBox == null) return;

        // Create a copy to not mess with the original list
        List<C> results = new ArrayList<>(this.getCollectionsForCategory());

        String string = this.searchBox.getValue();
        SearchQuery query = null;
        SearchCache cache = null;
        if (!string.isEmpty()) {
            // Parse search syntax (@mod $tag #tooltip r/regex/ "quotes" | OR -negation)
            // Checks all recipes in the collection, not just getFirst()
            query = SearchQuery.parse(string);
            cache = new SearchCache();
            // 「纯文本也匹配 tooltip 全文」——原版配方书搜索的语料就是产物物品的全部 tooltip 行
            // （SessionSearchTrees.recipes()），于是"海岸盔甲纹饰"这种只出现在 tooltip 里的文字
            // 在原版能查到锻造台纹饰组的配方；BRBE 先前只匹配物品名 → 查不到（用户 2026-09-27 诉求）。
            // 只在自研书打开：语料小（锻造/酿造几十个集合），tooltip 生成 + 缓存的开销可控。
            cache.setTooltipFallback(true);
            final SearchQuery fQuery = query;
            final SearchCache fCache = cache;
            results.removeIf(collection -> !this.brbe$matchesSearch(collection, fQuery, fCache));
        }

        // Stage：**排序原因剥离**（用户 2026-09-27 诉求）——与原版书共用 RecipeExtraction 通用核心：
        // 组内需要调序的变体（pin / 可合成 / 残缺 / 搜索命中）从原组剥出来按类别各自成组，
        // 原组保留基线变体且位置不变，多层递归。
        // ⚠️「拆散替代配方组」= 关闭（OFF）时不做任何拆散（用户 2026-10-03 指令）——
        // 自研书（酿造台/锻造台）与原版书走同一开关，组内变体不再按 pin / 可合成 / 残缺 /
        // 搜索命中被剥出去。
        if (BetterRecipeBook.config.alternativeRecipes.selectiveSplitEnabled()) {
            results = this.brbe$extractBySortReason(results, query, cache);
        }

        boolean filtering = BRBBookSettings.isFiltering(this.getRecipeBookType());
        if (filtering) {
            results.removeIf((result) -> !result.atleastOneCraftable(this.menu.slots)
                    && !result.atleastOnePartiallyCraftable(this.menu.slots));
        }

        this.brbe$sortByPinsInPlace(results);
        // 过滤开启 = 既有行为；「优化原版配方过滤器」开启 = 按钮隐藏但仍恒排序
        //（用户 2026-09-27 诉求：可合成物品始终置于首页，语义对齐原版书的 Stage 4）。
        if (filtering || BRBBookSettings.partialFilterMode()) {
            this.brbe$sortCraftableBeforePartial(results);
        }

        this.recipesPage.setResults(results, b, selectedTab.getCategory());
    }

    /**
     * 可合成 → 残缺 → 其余 的三段稳定排序（「优化原版配方过滤器」的核心表现：
     * 可合成物品始终置于首页）。与原版书 {@code CollectionPipeline.applyPartialSort}
     * 同一语义；pin 组由 {@link #brbe$sortByPinsInPlace} 先提到最前，本方法保持稳定
     * 分段、不再打乱 pin 组之间的相对次序。
     *
     * <p>残缺判据用 {@code atleastOnePartiallyCraftable}（不受 {@code partialMarkingEnabled}
     * 影响，见 {@code GenericRecipeBookCollection}），因此「残缺配方」标记关闭时排序仍然能用。</p>
     */
    private void brbe$sortCraftableBeforePartial(List<C> results) {
        List<C> craftableResults = new ArrayList<>();
        List<C> partialResults = new ArrayList<>();
        List<C> otherResults = new ArrayList<>();

        for (C result : results) {
            if (result.atleastOneCraftable(this.menu.slots)) {
                craftableResults.add(result);
            } else if (result.atleastOnePartiallyCraftable(this.menu.slots)) {
                partialResults.add(result);
            } else {
                otherResults.add(result);
            }
        }

        results.clear();
        results.addAll(craftableResults);
        results.addAll(partialResults);
        results.addAll(otherResults);
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
        return this.xOffset == 86;
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
            // 收起配方书 = 替代配方组浮层一并收起（原版走 setInvisible；自研书此前漏了）
            if (this.recipesPage != null) {
                this.recipesPage.hideOverlay();
            }
        }
    }

    public boolean isVisible() {
        return visible;
    }

    public void toggleVisibility() {
        this.setVisible(!this.isVisible());
    }

    public boolean hasClickedOutside(double d, double e, int i, int j, int k, int l, int m) {
        if (!this.isVisible()) {
            return true;
        }
        boolean bl = d < (double) i || e < (double) j || d >= (double) (i + k) || e >= (double) (j + l);
        boolean bl2 = (double) (i - 147) < d && d < (double) i && (double) j < e && e < (double) (j + l);
//        return bl && !bl2 && !this.selectedTab.isHoveredOrFocused();
        return bl && !bl2;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
        return this.mouseClicked(event.x(), event.y(), event.button());
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!this.isVisible()) return false;

        if (this.recipesPage.mouseClicked(mouseX, mouseY, button, (this.width - 147) / 2 - this.xOffset, (this.height - 166) / 2, 147, 166)) {
            this.handlePlaceRecipe();
            return true;
        }

        if (button == 1 && this.searchBox.isMouseOver(mouseX, mouseY)) {
            boolean hadSearch = !this.searchBox.getValue().isEmpty();
            searchBox.setValue("");
            searchBox.setFocused(false);
            // 非重置刷新：清空搜索不把页码打回第 1 页（与输入搜索时保留页码一致）
            this.updateCollections(false);
            // 搜索词清空：恢复清空前的浏览页码（"保存浏览记录"功能）
            if (hadSearch && BetterRecipeBook.config.saveRecipeBookPosition) {
                this.restoreBrowsingPageAfterSearchClear();
            }
            return true;
        }

        if (ClientCompat.mouseClicked(this.searchBox, mouseX, mouseY, button)) {
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
//                    this.sendUpdateSettings();
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

        return true;
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
//            ArrayList<AbstractWidget> list = Lists.newArrayList();
//            this.recipeBookPage.listButtons(abstractWidget -> {
//                if (abstractWidget.isActive()) {
//                    list.add((AbstractWidget)abstractWidget);
//                }
//            });
//            list.add(this.searchBox);
//            list.add(this.filterButton);
//            list.addAll(this.tabButtons);
//            Screen.NarratableSearchResult narratableSearchResult = Screen.findNarratableWidget(list, null);
//            if (narratableSearchResult != null) {
//                narratableSearchResult.entry.updateNarration(narrationElementOutput.nest());
//            }
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
        if (!this.isVisible()) {
            return false;
        }

        int left = (this.width - 147) / 2 - this.xOffset;
        int top = (this.height - 166) / 2;
        if (mouseX >= left && mouseX < left + 147 && mouseY >= top && mouseY < top + 166) {
            return true;
        }

        for (BRBGroupButtonWidget tabButton : this.tabButtons) {
            if (tabButton.visible && tabButton.isMouseOver(mouseX, mouseY)) {
                return true;
            }
        }

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

        // 标签页（tab）悬停标题：标签页图标无提示文字，悬停显示类别标题
        // （如锻造书"升级模板"/"纹饰模板"——用户 2026-09-28 起锻造书只有这两页）。
        for (BRBGroupButtonWidget tabButton : this.tabButtons) {
            if (tabButton.visible && tabButton.isMouseOver(mouseX, mouseY)) {
                net.minecraft.network.chat.Component title = tabButton.getCategory().getTitle();
                if (title != null) {
                    com.alonie.brbe.util.ClientCompat.setComponentTooltipForNextFrame(
                            gui, java.util.List.of(title), mouseX, mouseY);
                }
                return;
            }
        }

        // 替代配方组浮层打开时，普通配方格的 tooltip 会透过浮层冒出来，所以原样保留
        // 「浮层打开就不问页面」的判定；但**浮层里自己那一格**的 tooltip 要给出
        // （用户 2026-09-27 诉求：组内格子的 tooltip 与普通配方格同款）。
        java.util.List<net.minecraft.network.chat.Component> overlayTip = this.recipesPage.overlayTooltip();
        if (overlayTip != null && !overlayTip.isEmpty()) {
            // 组浮层会在帧末的 afterRender 顶层那一遍被再画一次（在帧末 tooltip 刷新之后）——
            // 那种屏幕上这里注册的延迟 tooltip 会被浮层压住，改由那一遍就地画
            // （TopLayerOverlayProvider#brbe$renderTopLayerTooltip）。用户 2026-09-28 反馈。
            if (!com.alonie.brbe.util.TopLayerOverlayRenderer
                    .redrawsOverlayOnTop(net.minecraft.client.Minecraft.getInstance().screen)) {
                com.alonie.brbe.util.ClientCompat.setComponentTooltipForNextFrame(gui, overlayTip, mouseX, mouseY);
            }
        } else if (!this.recipesPage.overlayIsVisible()) {
            this.recipesPage.drawTooltip(gui, mouseX, mouseY);

            ISettingsButton.super.renderSettingsButtonTooltip(this.settingsButton, gui, mouseX, mouseY);
        }

        this.ghostRecipe.drawTooltip(gui, x, y, mouseX, mouseY);
        this.brbe$lastHoveredGhostItem = this.ghostRecipe.getLastHoveredItem();
    }

    protected void refreshTabButtons() {
        int i = (this.width - 147) / 2 - this.xOffset - 30;
        int j = (this.height - 166) / 2 + 3;
        int l = 0;
        BRBGroupButtonWidget firstVisibleButton = null;
        BRBGroupButtonWidget lastVisibleButton = null;

        for (BRBGroupButtonWidget button : this.tabButtons) {
            BRBBookCategories.Category category = button.getCategory();
            if (category.getType() == BRBBookCategories.Category.Type.SEARCH) {
                button.visible = true;
            }
            button.setPosition(i, j + 27 * l++);
            button.setIconYOffset(0);
            if (button.visible) {
                if (firstVisibleButton == null) {
                    firstVisibleButton = button;
                }
                lastVisibleButton = button;
            }
        }

        if (firstVisibleButton != null && lastVisibleButton != null && firstVisibleButton != lastVisibleButton) {
            firstVisibleButton.setIconYOffset(-1);
            lastVisibleButton.setIconYOffset(1);
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
        // 点击放置的交接保持优先：幽灵留到真实物品到位为止（用户 2026-09-27 反馈的空窗期）
        if (this.brbe$tickPlacementHandover()) {
            return;
        }
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
}
