package com.alonie.brbe.generic;

import com.google.common.collect.Lists;
import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.api.BRBBookCategories;
import com.alonie.brbe.api.BRBBookSettings;
import com.alonie.brbe.compat.ItemViewCompat;
import com.alonie.brbe.compat.rei.ReiCompat;
import com.alonie.brbe.interfaces.IPinningComponent;
import com.alonie.brbe.interfaces.ISettingsButton;
import com.alonie.brbe.search.SearchCache;
import com.alonie.brbe.search.SearchQuery;
import com.alonie.brbe.util.ClientCompat;
import com.alonie.brbe.util.BRBHelper;
import com.alonie.brbe.util.BRBTextures;
import com.alonie.brbe.widget.StateSwitchingButton;
import com.alonie.brbe.layout.BookLayout;
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
    protected static final Component SEARCH_HINT = Component.translatable("gui.recipebook.search_hint");
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
//        this.menu.fillCraftSlotsStackedContents(this.stackedContents);
        String string = this.searchBox != null ? this.searchBox.getValue() : "";
        Objects.requireNonNull(this.minecraft.font);
        this.searchBox = new EditBox(this.minecraft.font, i + BookLayout.SEARCH_X_OFFSET, j + BookLayout.SEARCH_Y_OFFSET, BookLayout.SEARCH_WIDTH, this.minecraft.font.lineHeight + 5, Component.translatable("itemGroup.search"));
        this.searchBox.setMaxLength(50);
        this.searchBox.setVisible(true);
        this.searchBox.setTextColor(0xFFFFFF);
        this.searchBox.setValue(string);
        this.searchBox.setHint(SEARCH_HINT);
        this.settingsButton = createSettingsButton(i, j);
        this.recipesPage.initialize(this.minecraft, i, j, menu, xOffset);
        this.tabButtons.clear();
        this.filterButton = new StateSwitchingButton(i + BookLayout.TEXTURE_WIDTH - 37, j + BookLayout.FILTER_Y_OFFSET, BookLayout.FILTER_WIDTH, BookLayout.FILTER_HEIGHT, false);
        this.filterButton.useStateTriggeredForTexture(true);
        this.filterButton.setStateTriggered(BRBBookSettings.isFiltering(this.getRecipeBookType()));
        this.filterButton.initTextureValues(BRBTextures.RECIPE_BOOK_FILTER_BUTTON_SPRITES);
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
        int blitX = (this.width - BookLayout.TEXTURE_WIDTH) / 2 - this.xOffset;
        int blitY = (this.height - BookLayout.TEXTURE_HEIGHT) / 2;
        gui.blit(ClientCompat.GUI_TEXTURED, BRBTextures.RECIPE_BOOK_BACKGROUND_TEXTURE, blitX, blitY, 1.0F, 1.0F, BookLayout.TEXTURE_WIDTH, BookLayout.TEXTURE_HEIGHT, 256, 256);

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

        if (ClientCompat.matches(BetterRecipeBook.PIN_MAPPING, i, j, k) && BetterRecipeBook.ctx().config().enablePinning) {
            for (GenericRecipeButton<C, R, M> resultButton : this.recipesPage.buttons) {
                if (resultButton.isHoveredOrFocused()) {
                    BetterRecipeBook.pinnedRecipeManager.addOrRemoveFavourite(resultButton.getCollection());
                    this.updateCollections(false);
                    return true;
                }
            }
        }

        // JEI/REI integration: open recipe/usage views for hovered item
        if (ItemViewCompat.isLoaded()) {
            // ── 1. Recipe buttons ──────────────────────────────────────
            if (this.recipesPage.hoveredButton != null) {
                R hoveredRecipe = this.recipesPage.hoveredButton.getCurrentDisplayedRecipe();
                if (hoveredRecipe != null) {
                    ItemStack hoveredStack = hoveredRecipe.getResult(registryAccess, this.recipesPage.hoveredButton.category);
                    if (ClientCompat.matches(BetterRecipeBook.RECIPE_VIEW_MAPPING, i, j, k)) {
                        return ItemViewCompat.openRecipeView(hoveredStack);
                    }
                    if (ClientCompat.matches(BetterRecipeBook.USAGE_VIEW_MAPPING, i, j, k)) {
                        return ItemViewCompat.openUsageView(hoveredStack);
                    }
                }
            }

            // ── 2. Ghost items ─────────────────────────────────────────
            ItemStack ghostStack = this.brbe$lastHoveredGhostItem;
            if (ghostStack != null && !ghostStack.isEmpty()) {
                if (ClientCompat.matches(BetterRecipeBook.RECIPE_VIEW_MAPPING, i, j, k)) {
                    return ItemViewCompat.openRecipeView(ghostStack);
                }
                if (ClientCompat.matches(BetterRecipeBook.USAGE_VIEW_MAPPING, i, j, k)) {
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
        String string = this.searchBox.getValue().toLowerCase(Locale.ROOT);
        this.pirateSpeechForThePeople(string);
        if (!string.equals(this.lastSearch)) {
            this.updateCollections(false);
            this.lastSearch = string;
        }
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
        boolean bl2 = (double) (i - BookLayout.TEXTURE_WIDTH) < d && d < (double) i && (double) j < e && e < (double) (j + l);
//        return bl && !bl2 && !this.selectedTab.isHoveredOrFocused();
        return bl && !bl2;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
        return this.mouseClicked(event.x(), event.y(), event.button());
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!this.isVisible()) return false;

        if (this.recipesPage.mouseClicked(mouseX, mouseY, button, (this.width - BookLayout.TEXTURE_WIDTH) / 2 - this.xOffset, (this.height - BookLayout.TEXTURE_HEIGHT) / 2, BookLayout.TEXTURE_WIDTH, BookLayout.TEXTURE_HEIGHT)) {
            this.handlePlaceRecipe();
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

        int left = (this.width - BookLayout.TEXTURE_WIDTH) / 2 - this.xOffset;
        int top = (this.height - BookLayout.TEXTURE_HEIGHT) / 2;
        if (mouseX >= left && mouseX < left + BookLayout.TEXTURE_WIDTH && mouseY >= top && mouseY < top + BookLayout.TEXTURE_HEIGHT) {
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
        int i = (this.width - BookLayout.TEXTURE_WIDTH) / 2 - this.xOffset - BookLayout.TAB_BUTTON_WIDTH;
        int j = (this.height - BookLayout.TEXTURE_HEIGHT) / 2 + BookLayout.TAB_TOP_OFFSET;
        int l = 0;
        BRBGroupButtonWidget firstVisibleButton = null;
        BRBGroupButtonWidget lastVisibleButton = null;

        for (BRBGroupButtonWidget button : this.tabButtons) {
            BRBBookCategories.Category category = button.getCategory();
            if (category.getType() == BRBBookCategories.Category.Type.SEARCH) {
                button.visible = true;
            }
            button.setPosition(i, j + BookLayout.TAB_BUTTON_SPACING * l++);
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
