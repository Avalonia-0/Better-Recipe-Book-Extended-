package com.alonie.brbe.generic;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.api.BRBBookCategories;
import com.alonie.brbe.layout.BookLayout;
import com.alonie.brbe.layout.GridSpec;
import com.alonie.brbe.util.BRBTextures;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.StateSwitchingButton;
import net.minecraft.core.RegistryAccess;
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
        if (!BetterRecipeBook.config.expandedRecipeBook) return 5;
        int availableWidth = bookWidth - BookLayout.GRID_LEFT_PADDING * 2;
        return Math.max(5, availableWidth / BookLayout.BUTTON_SIZE);
    }

    /** Buttons per page — 20 normally, dynamic when expanded. */
    public int getButtonsPerPage() {
        if (!BetterRecipeBook.config.expandedRecipeBook) return GridSpec.standard().totalButtons();
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

        if (BetterRecipeBook.config.expandedRecipeBook) {
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

    protected void initOverlay(C recipeCollection, int x, int y, RegistryAccess registryAccess) {
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
                    this.initOverlay(recipeButton.getCollection(), this.parentLeft, this.parentTop, registryAccess);
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

    protected boolean overlayIsVisible() {
        return false;
    }

    protected void render(GuiGraphics gui, int blitX, int blitY, int mouseX, int mouseY, float delta) {
        if (BetterRecipeBook.getQueuedScroll() != 0 && BetterRecipeBook.config.scrolling.enableScrolling) {
            if (isMouseOverRecipeBookPage(mouseX, mouseY, blitX, blitY) && totalPages > 1) {
                currentPage += BetterRecipeBook.getQueuedScroll();
                if (currentPage >= totalPages) {
                    currentPage = BetterRecipeBook.config.scrolling.scrollAround ? currentPage % totalPages : totalPages - 1;
                } else if (currentPage < 0) {
                    currentPage = BetterRecipeBook.config.scrolling.scrollAround ? (currentPage % totalPages) + totalPages : 0;
                }

                updateButtonsForPage();
            }
            BetterRecipeBook.setQueuedScroll(0);
        }

        if (this.totalPages > 1) {
            String string = this.currentPage + 1 + "/" + this.totalPages;
            int stringWidth = this.minecraft.font.width(string);
            gui.drawString(this.minecraft.font, string, blitX + bookWidth / 2 - stringWidth / 2, blitY + 141, -1, false);
        }

        this.hoveredButton = null;

        for (var button : this.buttons) {
            button.render(gui, mouseX, mouseY, delta);
            if (button.visible && button.isHoveredOrFocused()) {
                this.hoveredButton = button;
            }
        }

        this.backButton.render(gui, mouseX, mouseY, delta);
        this.forwardButton.render(gui, mouseX, mouseY, delta);
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
        if (BetterRecipeBook.config.scrolling.scrollAround && totalPages > 1) {
            forwardButton.visible = true;
            backButton.visible = true;
        } else {
            forwardButton.visible = totalPages > 1 && currentPage < totalPages - 1;
            backButton.visible = totalPages > 1 && currentPage > 0;
        }
    }

    public void drawTooltip(GuiGraphics gui, int x, int y) {
        if (this.minecraft.screen != null && hoveredButton != null) {
            gui.renderComponentTooltip(Minecraft.getInstance().font, this.hoveredButton.getTooltipText(), x, y);
        }
    }
}
