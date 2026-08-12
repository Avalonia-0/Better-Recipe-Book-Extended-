package com.alonie.brbe.util;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.server.packs.resources.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.List;

public final class ClientCompat {
    public static final RenderPipeline GUI_TEXTURED = RenderPipelines.GUI_TEXTURED;

    private static final Logger LOGGER = LoggerFactory.getLogger(ClientCompat.class);
    private static boolean spriteDiagLogged = false;

    private ClientCompat() {
    }

    public static KeyEvent keyEvent(int keyCode, int scanCode, int modifiers) {
        return new KeyEvent(keyCode, scanCode, modifiers);
    }

    public static CharacterEvent characterEvent(char character, int modifiers) {
        return new CharacterEvent((int) character);
    }

    public static MouseButtonEvent mouseButtonEvent(double mouseX, double mouseY, int button) {
        return new MouseButtonEvent(mouseX, mouseY, new MouseButtonInfo(button, 0));
    }

    public static boolean matches(KeyMapping keyMapping, int keyCode, int scanCode, int modifiers) {
        return keyMapping.matches(keyEvent(keyCode, scanCode, modifiers));
    }

    public static boolean keyPressed(EditBox editBox, int keyCode, int scanCode, int modifiers) {
        return editBox.keyPressed(keyEvent(keyCode, scanCode, modifiers));
    }

    public static boolean charTyped(EditBox editBox, char character, int modifiers) {
        return editBox.charTyped(characterEvent(character, modifiers));
    }

    public static boolean mouseClicked(AbstractWidget widget, double mouseX, double mouseY, int button) {
        return widget.mouseClicked(mouseButtonEvent(mouseX, mouseY, button), false);
    }

    public static boolean isControlDown() {
        Minecraft minecraft = Minecraft.getInstance();
        return InputConstants.isKeyDown(minecraft.getWindow(), InputConstants.KEY_LCONTROL)
                || InputConstants.isKeyDown(minecraft.getWindow(), InputConstants.KEY_RCONTROL);
    }

    public static boolean isShiftDown() {
        Minecraft minecraft = Minecraft.getInstance();
        return InputConstants.isKeyDown(minecraft.getWindow(), InputConstants.KEY_LSHIFT)
                || InputConstants.isKeyDown(minecraft.getWindow(), InputConstants.KEY_RSHIFT);
    }

    public static void blitSprite(GuiGraphicsExtractor gui, Identifier sprite, int x, int y, int width, int height) {
        gui.blitSprite(GUI_TEXTURED, sprite, x, y, width, height);
    }

    public static void setComponentTooltipForNextFrame(GuiGraphicsExtractor gui, List<Component> tooltip, int mouseX, int mouseY) {
        gui.setComponentTooltipForNextFrame(Minecraft.getInstance().font, tooltip, mouseX, mouseY);
    }

    public static ItemStack[] ingredientItems(Ingredient ingredient) {
        return ingredient.items()
                .map(holder -> holder.value().getDefaultInstance())
                .toArray(ItemStack[]::new);
    }

    public static ItemStack firstIngredientItem(Ingredient ingredient) {
        ItemStack[] items = ingredientItems(ingredient);
        return items.length == 0 ? ItemStack.EMPTY : items[0];
    }

    /**
     * True if a GUI sprite exists in the current resource stack (compat packs included).
     * The sprite identifier is mapped to its actual file id ({@code textures/gui/sprites/... + ".png"})
     * because {@code getResourceStack} looks up pack files by their full id (the sprite
     * atlas convention adds the directory prefix and the file extension).
     */
    public static boolean hasSpriteResource(Identifier spriteId) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getResourceManager() == null) return false;
        Identifier fileId = spriteId.withPath("textures/gui/sprites/" + spriteId.getPath() + ".png");
        java.util.List<Resource> stack = minecraft.getResourceManager().getResourceStack(fileId);
        if (!spriteDiagLogged) {
            spriteDiagLogged = true;
            java.util.List<String> packs = new java.util.ArrayList<>();
            for (Resource r : stack) packs.add(r.sourcePackId());
            LOGGER.warn("[BRBE-DIAG] hasSpriteResource: sprite={} fileId={} stackSize={} packs={}",
                    spriteId, fileId, stack.size(), packs);
            // Control: a sprite shipped in the mod's OWN assets (not the built-in pack)
            Identifier pinFile = Identifier.fromNamespaceAndPath("brbe", "textures/gui/sprites/recipe_book/pin.png");
            java.util.List<Resource> pinStack = minecraft.getResourceManager().getResourceStack(pinFile);
            java.util.List<String> pinPacks = new java.util.ArrayList<>();
            for (Resource r : pinStack) pinPacks.add(r.sourcePackId());
            LOGGER.warn("[BRBE-DIAG] hasSpriteResource: control pin fileId={} stackSize={} packs={}",
                    pinFile, pinStack.size(), pinPacks);
        }
        return !stack.isEmpty();
    }
}
