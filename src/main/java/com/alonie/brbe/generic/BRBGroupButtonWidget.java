package com.alonie.brbe.generic;

import com.alonie.brbe.api.BRBBookCategories;
import com.alonie.brbe.util.ClientCompat;
import com.alonie.brbe.util.BRBTextures;
import com.alonie.brbe.widget.StateSwitchingButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public class BRBGroupButtonWidget extends StateSwitchingButton {
    protected BRBBookCategories.Category category;
    private int iconYOffset;

    public BRBGroupButtonWidget(BRBBookCategories.Category category) {
        super(0, 0, 35, 27, false);
        this.category = category;
        this.initTextureValues(BRBTextures.RECIPE_BOOK_TAB_SPRITES);
    }

    public void extractWidgetRenderState(GuiGraphicsExtractor gui, int mouseX, int mouseY, float delta) {
        Minecraft minecraftClient = Minecraft.getInstance();

        Identifier sprite = this.sprites.get(true, this.isStateTriggered);
        int x = getX();
        if (this.isStateTriggered) {
            x -= 2;
        }

        ClientCompat.blitSprite(gui, sprite, x, this.getY(), this.width, this.height);

        this.renderIcons(gui, minecraftClient.getItemModelResolver());
    }

    private void renderIcons(GuiGraphicsExtractor guiGraphics, ItemModelResolver itemModelResolver) {
        List<ItemStack> list = this.category.getItemIcons();
        int i = this.isStateTriggered ? -2 : 0;
        int iconY = getY() + 5 + this.iconYOffset;
        if (list.size() == 1) {
            guiGraphics.fakeItem(list.get(0), getX() + 9 + i, iconY);
        } else if (list.size() == 2) {
            guiGraphics.fakeItem(list.get(0), getX() + 3 + i, iconY);
            guiGraphics.fakeItem(list.get(1), getX() + 14 + i, iconY);
        }

    }

    public void setIconYOffset(int iconYOffset) {
        this.iconYOffset = iconYOffset;
    }

    public BRBBookCategories.Category getCategory() {
        return this.category;
    }
}
