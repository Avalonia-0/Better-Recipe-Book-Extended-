package com.alonie.brbe.brewingstand;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.api.BRBBookCategories;
import com.alonie.brbe.api.BRBBookSettings;
import com.alonie.brbe.generic.GenericRecipeBookComponent;
import com.alonie.brbe.generic.GenericRecipePage;
import com.alonie.brbe.interfaces.IPinningComponent;
import com.alonie.brbe.loaders.PotionLoader;
import com.alonie.brbe.mixins.accessors.BrewingStandMenuAccessor;
import com.alonie.brbe.util.BRBHelper;
import com.alonie.brbe.util.ClientCompat;
import com.alonie.brbe.util.ClientInventoryUtil;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import static com.alonie.brbe.brewingstand.PlatformPotionUtil.getFrom;
import static com.alonie.brbe.brewingstand.PlatformPotionUtil.getIngredient;

@Environment(EnvType.CLIENT)
public class BrewingRecipeBookComponent extends GenericRecipeBookComponent<BrewingStandMenu, BrewingRecipeCollection, BrewableResult> implements IPinningComponent<BrewingRecipeCollection> {
    private static final Component ONLY_CRAFTABLES_TOOLTIP = Component.translatable("brbe.gui.togglePotions.brewable");

    @Override
    public void init(int parentWidth, int parentHeight, Minecraft client, boolean narrow, BrewingStandMenu menu, Consumer<ItemStack> onGhostRecipeUpdate, RegistryAccess registryAccess) {
        super.init(parentWidth, parentHeight, client, narrow, menu, onGhostRecipeUpdate, registryAccess);

        this.recipesPage = new GenericRecipePage<>(registryAccess, () -> new BrewableRecipeButton(registryAccess, () -> BRBBookSettings.isFiltering(this.getRecipeBookType())));
        // this.cachedInvChangeCount = client.player.getInventory().getChangeCount();

//        if (this.isVisible()) {
        this.initVisuals();
//        }

        ghostRecipe.setRenderingPredicate((type, ingredient) -> {
            ItemStack slot = menu.slots.get(ingredient.getContainerSlot()).getItem();
            switch (type) {
                case ITEM, BACKGROUND -> {
                    // slot 0 is the result so map it to 1
                    ItemStack ghost = ingredient.getContainerSlot() == BrewingStandMenuAccessor.getBOTTLE_SLOT_START() ? ingredient.getOwner().getBySlot(1).getItem() : ingredient.getItem();

                    // slot is result
                    if (ingredient.getContainerSlot() >= BrewingStandMenuAccessor.getBOTTLE_SLOT_START() && ingredient.getContainerSlot() <= BrewingStandMenuAccessor.getBOTTLE_SLOT_END()) {
                        if (!(slot.getItem() instanceof PotionItem)) return true;

                        var slotPotion = slot.get(DataComponents.POTION_CONTENTS);
                        var ghostPotion = ghost.get(DataComponents.POTION_CONTENTS);

                        return !Objects.equals(slotPotion, ghostPotion);
                    } else { // else it's the consumable item
                        return !slot.is(ghost.getItem());
                    }
                }
                case TOOLTIP -> {
                    // render tooltip only if slot is empty
                    return slot.isEmpty();
                }
            }
            return true;
        });

        // still required?
        //client.keyboardHandler.setSendRepeatsToGui(true);
    }

    public ItemStack getInputStack(BrewableResult result) {
        Potion inputPotion = getFrom(result.recipe);
        Ingredient ingredient = getIngredient(result.recipe);
        //Identifier identifier = BuiltInRegistries.POTION.getKey(inputPotion);
        ItemStack inputStack;
        if (this.selectedTab.getCategory() == BetterRecipeBook.BREWING_SPLASH_POTION) {
            inputStack = new ItemStack(Items.SPLASH_POTION);
        } else if (this.selectedTab.getCategory() == BetterRecipeBook.BREWING_LINGERING_POTION) {
            inputStack = new ItemStack(Items.LINGERING_POTION);
        } else {
            inputStack = new ItemStack(Items.POTION);
        }

        inputStack.set(DataComponents.POTION_CONTENTS, new PotionContents(BuiltInRegistries.POTION.wrapAsHolder(inputPotion)));
        return inputStack;
    }

    public void setupGhostRecipe(BrewableResult result, List<Slot> slots) {
        this.ghostRecipe.addIngredient(BrewingStandMenuAccessor.getINGREDIENT_SLOT(), ClientCompat.firstIngredientItem(getIngredient(result.recipe)), slots.get(BrewingStandMenuAccessor.getINGREDIENT_SLOT()).x, slots.get(BrewingStandMenuAccessor.getINGREDIENT_SLOT()).y);

        assert selectedTab != null;
        ItemStack inputStack = result.inputAsItemStack(selectedTab.getCategory());

        for (int i = BrewingStandMenuAccessor.getBOTTLE_SLOT_START(); i <= BrewingStandMenuAccessor.getBOTTLE_SLOT_END(); i++) {
            this.ghostRecipe.addIngredient(i, inputStack.copy(), slots.get(i).x, slots.get(i).y);
        }
    }

    @Override
    protected List<BrewingRecipeCollection> getCollectionsForCategory() {
        List<BrewingRecipeCollection> results = new ArrayList<>();
        BRBBookCategories.Category category = selectedTab.getCategory();

        for (BrewableResult potion : PotionLoader.POTIONS) {
            results.add(new BrewingRecipeCollection(List.of(potion), menu, registryAccess, category));
        }

        return results;
    }

    @Override
    public Component getRecipeFilterName() {
        return ONLY_CRAFTABLES_TOOLTIP;
    }

    @Override
    public BRBHelper.Book getRecipeBookType() {
        return BetterRecipeBook.BREWING;
    }

    @Override
    public void handlePlaceRecipe() {
        BrewableResult result = this.recipesPage.getCurrentClickedRecipe();

        if (result == null) return;

        this.ghostRecipe.clear();

        if (!result.hasMaterials(this.selectedTab.getCategory(), menu.slots)) {
            setupGhostRecipe(result, menu.slots);
            return;
        }

        ItemStack inputStack = getInputStack(result);
        Ingredient ingredient = getIngredient(result.recipe);

        int slotIndex = 0;
        int usedInputSlots = 0;
        for (Slot slot : menu.slots) {
            ItemStack itemStack = slot.getItem();

            if (ItemStack.isSameItemSameComponents(inputStack, itemStack)) {
                if (usedInputSlots <= 2) {
                    ClientInventoryUtil.moveItemToSlot(menu, slotIndex, menu.getSlot(usedInputSlots).index);
                    ++usedInputSlots;
                }
            } else if (ClientCompat.firstIngredientItem(ingredient).getItem().equals(slot.getItem().getItem())) {
                ClientInventoryUtil.moveItemToSlot(menu, slotIndex, menu.getSlot(3).index);
            }

            ++slotIndex;
        }

        this.updateCollections(false);
    }

    public void recipesUpdated() {
        updateCollections(false);
    }

}
