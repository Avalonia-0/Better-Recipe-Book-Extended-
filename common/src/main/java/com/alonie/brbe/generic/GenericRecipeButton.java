package com.alonie.brbe.generic;

import com.google.common.collect.Lists;
import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.api.BRBBookCategories;
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

        // partial recipes: use the red-check sprite from the compat pack when available,
        // otherwise fall back to the vanilla sprite + red overlay
        boolean redCheck = isPartial && ClientCompat.hasSpriteResource(BRBTextures.RECIPE_BOOK_BUTTON_SLOT_PARTIAL_SPRITE);
        ClientCompat.blitSprite(gui, redCheck ? BRBTextures.RECIPE_BOOK_BUTTON_SLOT_PARTIAL_SPRITE : outlineTexture,
                getX(), getY(), this.width, this.height);

        // red overlay for partially craftable recipes (drawn before item so item shows on top)
        if (isPartial && !redCheck) {
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

    /**
     * 挤压离场渲染：配方格子以给定宽度渲染（blit 贴图被横向压缩），用于配方
     * 滑出视窗边界时被挤压消失的效果。右/左边缘由 slotX/width 决定（边缘钳制在
     * 视窗边界），图标在格子宽度不足以容纳时（&lt;=20）消失。
     */
    public void renderSquashed(GuiGraphicsExtractor gui, int slotX, int width, int bx, int slotY) {
        if (this.collection == null || width <= 0) {
            return;
        }
        List<R> list = getOrderedRecipes();
        if (list.isEmpty()) {
            return;
        }
        R current = getCurrentDisplayedRecipe();
        if (current == null) {
            return;
        }
        boolean isPartial = this.collection.getPartiallyCraftableRecipes().stream()
                .anyMatch(r -> r.id().equals(current.id()));
        boolean effectiveCraftable = collection.isCraftable(current, menu.slots) || isPartial;
        Identifier outlineTexture = effectiveCraftable ?
                BRBTextures.RECIPE_BOOK_BUTTON_SLOT_CRAFTABLE_SPRITE : BRBTextures.RECIPE_BOOK_BUTTON_SLOT_UNCRAFTABLE_SPRITE;
        boolean redCheck = isPartial && ClientCompat.hasSpriteResource(BRBTextures.RECIPE_BOOK_BUTTON_SLOT_PARTIAL_SPRITE);
        Identifier sprite = redCheck ? BRBTextures.RECIPE_BOOK_BUTTON_SLOT_PARTIAL_SPRITE : outlineTexture;
        int rightBound = slotX + width;
        if (width < 25) {
            // 伪压缩：内容在 [slotX, rightBound] 内裁剪（中间随滑动变短）。图标完整跟随
            // 配方位置滑出视窗；左右边界 2px 最后渲染（上层），盖住经过边界的图标。
            gui.enableScissor(slotX, slotY, rightBound, slotY + this.height);
            ClientCompat.blitSprite(gui, sprite, bx, slotY, this.width, this.height);
            if (isPartial && !redCheck) {
                gui.fill(slotX + 1, slotY + 1, rightBound - 1, slotY + this.height - 1, 0x60FF3333);
            }
            gui.disableScissor();
            ItemStack result = current.getResult(registryAccess, category);
            gui.fakeItem(result, bx + 4, slotY + 4);
            // 左边界 2px（上层，盖住图标）
            gui.enableScissor(slotX, slotY, Math.min(slotX + 2, rightBound), slotY + this.height);
            ClientCompat.blitSprite(gui, sprite, slotX, slotY, this.width, this.height);
            gui.disableScissor();
            // 右边界 2px（上层，盖住图标）
            gui.enableScissor(Math.max(rightBound - 2, slotX), slotY, rightBound, slotY + this.height);
            ClientCompat.blitSprite(gui, sprite, rightBound - this.width, slotY, this.width, this.height);
            gui.disableScissor();
        } else {
            ClientCompat.blitSprite(gui, sprite, slotX, slotY, this.width, this.height);
            if (isPartial && !redCheck) {
                gui.fill(slotX + 1, slotY + 1, slotX + this.width - 1, slotY + this.height - 1, 0x60FF3333);
            }
            ItemStack result = current.getResult(registryAccess, category);
            gui.fakeItem(result, bx + 4, slotY + 4);
        }
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

        return list;
    }
}
