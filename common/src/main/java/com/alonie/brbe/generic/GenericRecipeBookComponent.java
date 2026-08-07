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
    private String lastSearch;
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

    /** Last carried item, to detect pick-up/drop and refresh partial visibility. */
    private ItemStack brbe$lastCarried = ItemStack.EMPTY;

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

        this.filterButton.render(gui, mouseX, mouseY, delta);

        ISettingsButton.super.renderSettingsButton(this.settingsButton, gui, mouseX, mouseY, delta);

        // render the recipe book page contents
        this.recipesPage.render(gui, blitX, blitY, mouseX, mouseY, delta);
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
            results.removeIf(collection -> !matchesSearch(collection, query, cache));
        }

        // Pipeline: pins → partial sort → filter toggle
        CollectionPipeline.applyPinsGeneric(results);

        boolean isFiltering = BRBBookSettings.isFiltering(this.getRecipeBookType());
        boolean shouldSort = BetterRecipeBook.ctx().config().partialCraftingEnabled || isFiltering;
        if (shouldSort) {
            results = CollectionPipeline.applyPartialSortGeneric(results);
        }
        // partialOnlyWhenCarrying applies regardless of the "craftable only"
        // filter toggle: partial recipes are hidden by default and only shown
        // while the player is holding one of their materials.
        boolean partialOnlyWhenCarrying = BetterRecipeBook.ctx().config().partialOnlyWhenCarrying;
        ItemStack carried = this.menu.getCarried();
        results.removeIf((result) -> {
            if (result.atleastOneCraftable(this.menu.slots)) {
                return false; // craftable → always keep
            }
            if (partialOnlyWhenCarrying) {
                // Nothing held → hide all partial recipes.
                if (carried == null || carried.isEmpty()) {
                    return true;
                }
                // Only keep partial recipes that actually use the held item.
                for (R recipe : result.getRecipes()) {
                    if (recipe.usesItem(carried)) {
                        return false; // uses held item → keep
                    }
                }
                return true; // unrelated partial → hide
            }
            return isFiltering && !result.atleastOnePartiallyCraftable(this.menu.slots);
        });

        this.recipesPage.setResults(results, resetPageNumber, selectedTab.getCategory());
    }

    /**
     * Detects a change in the carried item (pick-up/drop) and refreshes the
     * collection list, so {@code partialOnlyWhenCarrying} visibility updates
     * live. Called from the host screen's {@code containerTick}.
     */
    public void tick() {
        if (!this.isVisible() || this.menu == null) {
            return;
        }
        ItemStack carried = this.menu.getCarried();
        if (!ItemStack.matches(carried, this.brbe$lastCarried)) {
            this.brbe$lastCarried = carried.copy();
            this.updateCollections(false);
        }
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

        if (this.filterButton.mouseClicked(mouseX, mouseY, button)) {
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

        if (!this.recipesPage.overlayIsVisible()) {
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

    protected abstract List<C> getCollectionsForCategory();

    @Override
    public void recipesShown(List<RecipeHolder<?>> list) {

    }
}
