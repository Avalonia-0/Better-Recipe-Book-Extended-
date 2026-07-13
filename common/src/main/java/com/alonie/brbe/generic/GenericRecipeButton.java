package com.alonie.brbe.generic;

import com.google.common.collect.Lists;
import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.api.BRBBookCategories;
import com.alonie.brbe.mixins.accessors.KeyMappingAccessor;
import com.alonie.brbe.util.ClientCompat;
import com.alonie.brbe.util.BRBTextures;
import com.alonie.brbe.util.ModNameUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;
import java.util.function.Supplier;

public class GenericRecipeButton<C extends GenericRecipeBookCollection<R, M>, R extends GenericRecipe, M extends AbstractContainerMenu> extends AbstractWidget {
    private final Supplier<Boolean> filteringSupplier;
    protected C collection;
    protected M menu;
    protected float time;
    protected int currentIndex;
    protected RegistryAccess registryAccess;
    protected BRBBookCategories.Category category;

    public GenericRecipeButton(RegistryAccess registryAccess, Supplier<Boolean> filteringSupplier) {
        super(0, 0, 25, 25, CommonComponents.EMPTY);
        this.registryAccess = registryAccess;
        this.filteringSupplier = filteringSupplier;
    }

    public void showCollection(C collection, M smithingMenu, BRBBookCategories.Category category) {
        this.collection = collection;
        this.menu = smithingMenu;
        this.category = category;
    }

    public void extractWidgetRenderState(GuiGraphicsExtractor gui, int mouseX, int mouseY, float delta) {
        if (this.collection == null) return;

        if (!ClientCompat.isControlDown()) {
            this.time += delta;
        }

        List<R> list = getOrderedRecipes();

        if (list.isEmpty()) {
            return;
        }

        this.currentIndex = Mth.floor(this.time / 30.0F) % list.size();

        R current = getCurrentDisplayedRecipe();
        boolean isPartial = current != null
                && this.collection.getPartiallyCraftableRecipes().stream()
                        .anyMatch(r -> r.id().equals(current.id()));

        // blit outline texture — use craftable sprite for partial recipes
        // so they get the light-coloured border (red fill is drawn below)
        boolean effectiveCraftable = current != null
                && (collection.isCraftable(current, menu.slots) || isPartial);
        Identifier outlineTexture = effectiveCraftable ?
                BRBTextures.RECIPE_BOOK_BUTTON_SLOT_CRAFTABLE_SPRITE : BRBTextures.RECIPE_BOOK_BUTTON_SLOT_UNCRAFTABLE_SPRITE;
        ClientCompat.blitSprite(gui, outlineTexture, getX(), getY(), this.width, this.height);

        // red overlay for partially craftable recipes (drawn before item so item shows on top)
        if (isPartial) {
            gui.fill(getX() + 1, getY() + 1, getX() + this.width - 1, getY() + this.height - 1, 0x60FF3333);
        }

        ItemStack result = getCurrentDisplayedRecipe().getResult(registryAccess, category);

        // render ingredient item (on top of red overlay)
        int offset = 4;
        gui.fakeItem(result, getX() + offset, getY() + offset);

        // if pinned recipe, blit the pin texture over it
        if (BetterRecipeBook.pinnedRecipeManager.has(collection)) {
            ClientCompat.blitSprite(gui, BRBTextures.RECIPE_BOOK_PIN_SPRITE, getX() - 4, getY() - 4, 32, 32);
        }
    }

    public R getCurrentDisplayedRecipe() {
        List<R> list = getOrderedRecipes();
        if (list.isEmpty()) {
            return null;
        }

        return list.get(currentIndex);
    }

    public boolean isOnlyOption() {
        return this.getOrderedRecipes().size() == 1;
    }

    public List<R> getOrderedRecipes() {
        List<R> list = this.getCollection().getDisplayRecipes(true);

        if (!this.filteringSupplier.get()) {
            list.addAll(this.collection.getDisplayRecipes(false));
        } else {
            list.addAll(this.collection.getPartiallyCraftableRecipes());
        }

        return list;
    }

    public C getCollection() {
        return this.collection;
    }

    @Override
    public void updateWidgetNarration(NarrationElementOutput builder) {
//        ItemStack inputStack = this.getCollection().getFirst().inputAsItemStack(group);
//
//        builder.add(NarratedElementType.TITLE, Component.translatable("narration.recipe", inputStack.getHoverName()));
//        builder.add(NarratedElementType.USAGE, Component.translatable("narration.button.usage.hovered"));
    }

    public int getWidth() {
        return 25;
    }

    protected boolean isValidClickButton(int i) {
        return i == 0 || i == 1;
    }

    @Override
    protected boolean isValidClickButton(MouseButtonInfo button) {
        return this.isValidClickButton(button.button());
    }

    public List<Component> getTooltipText() {
        List<Component> list = Lists.newArrayList();
        R recipe = getCurrentDisplayedRecipe();
        if (recipe == null) {
            return list;
        }

        var tipCtx = Item.TooltipContext.of(registryAccess);
        ItemStack result = recipe.getResult(registryAccess, category);
        list.addAll(result.getTooltipLines(tipCtx, Minecraft.getInstance().player, TooltipFlag.NORMAL));

        // Add source mod name (Jade-compatible format: jade.modName.<MOD_ID>)
        if (BetterRecipeBook.config.showModName) {
            Component modName = ModNameUtil.getFormattedModName(result);
            if (modName != null && !modName.getString().isEmpty()) {
                list.add(Component.empty());
                list.add(modName);
            }
        }

        this.addPinTooltip(list);

        return list;
    }

    public void addPinTooltip(List<Component> list) {
        list.add(Component.empty());

        if (true) {
            String keyName = ((KeyMappingAccessor) BetterRecipeBook.PIN_MAPPING).getKey().getDisplayName().getString();
            if (BetterRecipeBook.pinnedRecipeManager.has(collection)) {
                list.add(Component.translatable("brbe.gui.pin.remove", keyName));
            } else {
                list.add(Component.translatable("brbe.gui.pin.add", keyName));
            }
        }
    }
}
