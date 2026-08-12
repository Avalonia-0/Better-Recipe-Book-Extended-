package com.alonie.brbe.smithingtable;

import com.google.common.collect.Lists;
import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.api.BRBBookSettings;
import com.alonie.brbe.recipe.BRBSmithingRecipe;
import com.alonie.brbe.util.AlternativeOverlayLayout;
import com.alonie.brbe.util.BRBTextures;
import com.alonie.brbe.util.ClientCompat;
import net.minecraft.client.gui.GuiGraphics;
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

public class SmithingOverlayRecipeComponent implements Renderable, GuiEventListener {
    private static final Identifier OVERLAY_RECIPE_SPRITE = Identifier.withDefaultNamespace("recipe_book/overlay_recipe");

    private final List<OverlayRecipeButton> recipeButtons = Lists.newArrayList();
    private BRBSmithingRecipe lastRecipeClicked;
    private SmithingRecipeCollection collection;
    private boolean visible;
    private float time;
    private int x;
    private int y;

    public void init(SmithingRecipeCollection recipeCollection, int x, int y, RegistryAccess registryAccess) {
        this.collection = recipeCollection;

        List<BRBSmithingRecipe> lockedRecipes = recipeCollection.getDisplayRecipes(true);
        List<BRBSmithingRecipe> unlockedRecipes;
        if (!BRBBookSettings.isFiltering(BetterRecipeBook.SMITHING)) {
            unlockedRecipes = recipeCollection.getDisplayRecipes(false);
        } else {
            unlockedRecipes = recipeCollection.getPartiallyCraftableRecipes();
        }
        int lockedRecipeCount = lockedRecipes.size();
        int totalRecipeCount = lockedRecipeCount + unlockedRecipes.size();
        int columns = AlternativeOverlayLayout.columnsFor(totalRecipeCount);

        this.x = x + 7;
        this.y = y + 26;
        this.visible = true;
        this.recipeButtons.clear();

        List<BRBSmithingRecipe> partialRecipes = recipeCollection.getPartiallyCraftableRecipes();

        for (int index = 0; index < totalRecipeCount; ++index) {
            boolean isCraftable = index < lockedRecipeCount;
            BRBSmithingRecipe recipe = isCraftable ? lockedRecipes.get(index) : unlockedRecipes.get(index - lockedRecipeCount);
            boolean partial = partialRecipes.stream().anyMatch(r -> r.id().equals(recipe.id()));
            int buttonX = this.x + 4 + 25 * (index % columns);
            int buttonY = this.y + 5 + 25 * (index / columns);
            this.recipeButtons.add(new OverlayRecipeButton(buttonX, buttonY, recipe, isCraftable, registryAccess, partial));
        }

        this.lastRecipeClicked = null;
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return false;
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

    @Nullable
    public ScreenRectangle getBounds() {
        if (!this.visible || this.recipeButtons.isEmpty()) {
            return null;
        }

        int columns = AlternativeOverlayLayout.columnsFor(this.recipeButtons.size());
        int visibleColumns = Math.min(this.recipeButtons.size(), columns);
        int rows = Mth.ceil((float) this.recipeButtons.size() / (float) columns);
        return new ScreenRectangle(this.x, this.y, visibleColumns * 25 + 8, rows * 25 + 8);
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
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        if (!this.visible) {
            return;
        }

        this.time += delta;
        guiGraphics.pose().pushMatrix();
        int columns = AlternativeOverlayLayout.columnsFor(this.recipeButtons.size());
        int visibleColumns = Math.min(this.recipeButtons.size(), columns);
        int rows = Mth.ceil((float) this.recipeButtons.size() / (float) columns);
        ClientCompat.blitSprite(guiGraphics, OVERLAY_RECIPE_SPRITE, this.x, this.y, visibleColumns * 25 + 8, rows * 25 + 8);

        for (OverlayRecipeButton overlayRecipeButton : this.recipeButtons) {
            overlayRecipeButton.render(guiGraphics, mouseX, mouseY, delta);
        }

        guiGraphics.pose().popMatrix();
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

        @Override
        protected boolean isValidClickButton(MouseButtonInfo button) {
            return button.button() == 0;
        }

        @Override
        public void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
            this.defaultButtonNarrationText(narrationElementOutput);
        }

        @Override
        public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
            // Partial recipes keep the craftable sprite (light border); the red overlay marks them.
            Identifier sprite = BRBTextures.RECIPE_BOOK_PLAIN_OVERLAY_SPRITE.get(this.craftable || this.partial, this.isHoveredOrFocused());
            ClientCompat.blitSprite(guiGraphics, sprite, this.getX(), this.getY(), this.width, this.height);
            if (this.partial) {
                guiGraphics.fill(this.getX() + 1, this.getY() + 1, this.getX() + this.width - 1, this.getY() + this.height - 1, 0x60FF3333);
            }
            guiGraphics.renderFakeItem(this.recipe.getResult(this.registryAccess, BetterRecipeBook.SMITHING_SEARCH), this.getX() + 4, this.getY() + 4);
        }
    }
}
