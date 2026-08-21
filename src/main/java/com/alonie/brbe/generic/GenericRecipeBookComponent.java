package com.alonie.brbe.generic;

import com.google.common.collect.Lists;
import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.api.BRBBookCategories;
import com.alonie.brbe.api.BRBBookSettings;
import com.alonie.brbe.compat.ItemViewCompat;
import com.alonie.brbe.mixins.accessors.GenericRecipePageAccessor;
import com.alonie.brbe.mixins.accessors.RecipeBookComponentAccessor;
import com.alonie.brbe.util.RecipeBookPositionMemory;
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
import net.minecraft.client.gui.GuiGraphicsExtractor;
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
    private String lastSearch;
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

    public void extractRenderState(GuiGraphicsExtractor gui, int mouseX, int mouseY, float delta) {
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
        this.searchBox.extractRenderState(gui, mouseX, mouseY, delta);

        // render tab buttons
        for (BRBGroupButtonWidget widget : this.tabButtons) {
            widget.extractRenderState(gui, mouseX, mouseY, delta);
        }

        this.filterButton.extractRenderState(gui, mouseX, mouseY, delta);

        ISettingsButton.super.renderSettingsButton(this.settingsButton, gui, mouseX, mouseY, delta);

        // render the recipe book page contents
        this.recipesPage.render(gui, blitX, blitY, mouseX, mouseY, delta);

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
            for (GenericRecipeButton<C, R, M> resultButton : this.recipesPage.buttons) {
                if (resultButton.isHoveredOrFocused()) {
                    BetterRecipeBook.pinnedRecipeManager.addOrRemoveFavourite(resultButton.getCollection());
                    this.updateCollections(false);
                    // 固定/取消固定配方：播放点击音效。
                    if (this.minecraft != null && this.minecraft.getSoundManager() != null) {
                        net.minecraft.client.gui.components.AbstractWidget
                                .playButtonClickSound(this.minecraft.getSoundManager());
                    }
                    return true;
                }
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
        if (!string.equals(this.lastSearch)) {
            // 首次输入搜索词（空 → 非空）：回到第 1 页，从结果开头看
            boolean searchStarted = !string.isEmpty() && this.lastSearch.isEmpty();
            // 清空搜索（非空 → 空）：恢复搜索前浏览的页码
            boolean searchCleared = string.isEmpty() && !this.lastSearch.isEmpty();
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
        this.searchBox.preeditUpdated(null);
        this.searchBox.setValue("");
        this.searchBox.setFocused(false);
        this.lastSearch = "";
        this.updateCollections(true);
        this.recipesPage.flipTo(page - 1);
        return true;
    }

    protected void updateCollections(boolean b) {
        if (this.selectedTab == null) return;
        if (this.searchBox == null) return;

        // Create a copy to not mess with the original list
        List<C> results = new ArrayList<>(this.getCollectionsForCategory());

        String string = this.searchBox.getValue();
        if (!string.isEmpty()) {
            // Parse search syntax (@mod $tag #tooltip r/regex/ "quotes" | OR -negation)
            // Checks all recipes in the collection, not just getFirst()
            SearchQuery query = SearchQuery.parse(string);
            SearchCache cache = new SearchCache();
            results.removeIf(collection -> {
                for (R recipe : collection.getRecipes()) {
                    ItemStack result = recipe.getResult(registryAccess, selectedTab.getCategory());
                    if (result != null && !result.isEmpty() && query.matches(result, cache)) {
                        return false; // Keep collection — at least one recipe matches
                    }
                }
                return true; // Remove collection — no recipes match
            });
        }

        if (BRBBookSettings.isFiltering(this.getRecipeBookType())) {
            results.removeIf((result) -> !result.atleastOneCraftable(this.menu.slots)
                    && !result.atleastOnePartiallyCraftable(this.menu.slots));
        }

        this.brbe$sortByPinsInPlace(results);
        if (BRBBookSettings.isFiltering(this.getRecipeBookType())) {
            this.brbe$sortCraftableBeforePartial(results);
        }

        this.recipesPage.setResults(results, b, selectedTab.getCategory());
    }

    private void brbe$sortCraftableBeforePartial(List<C> results) {
        List<C> craftableResults = new ArrayList<>();
        List<C> partialResults = new ArrayList<>();

        for (C result : results) {
            if (result.atleastOneCraftable(this.menu.slots)) {
                craftableResults.add(result);
            } else {
                partialResults.add(result);
            }
        }

        results.clear();
        results.addAll(craftableResults);
        results.addAll(partialResults);
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

        if (this.filterButton.mouseClicked(mouseX, mouseY, button)) {
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

    public void drawTooltip(GuiGraphicsExtractor gui, int x, int y, int mouseX, int mouseY) {
        if (!this.isVisible()) {
            return;
        }

        if (!this.recipesPage.overlayIsVisible()) {
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

    public void renderGhostRecipe(GuiGraphicsExtractor guiGraphics, int x, int y, boolean bl, float delta) {
        if (selectedTab == null || ghostRecipe == null) return;

        this.ghostRecipe.render(guiGraphics, this.minecraft, x, y, bl, delta, selectedTab.getCategory());
    }

    protected abstract List<C> getCollectionsForCategory();
}
