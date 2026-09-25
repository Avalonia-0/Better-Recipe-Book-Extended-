package com.alonie.brbe.util;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.config.KeybindingCodec;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.InputConstants;
import me.shedaniel.clothconfig2.api.Modifier;
import me.shedaniel.clothconfig2.api.ModifierKeyCode;
import net.minecraft.client.KeyMapping;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.List;

public final class ClientCompat {
    public static final RenderPipeline GUI_TEXTURED = RenderPipelines.GUI_TEXTURED;

    /** 查询系统（R/U viewer + pin）tooltip 的自定义背景样式：解析为
     *  {@code brbe:tooltip/viewer_background} / {@code viewer_frame}
     *  sprite（textures/gui/sprites/tooltip/ 下，背景 alpha 已调淡为 160）。
     *  传给原版 {@code renderTooltip(...)} 链路的最后一个 Identifier 参数
     *  即可，不影响配方书/原版 tooltip。 */
    public static final Identifier VIEWER_TOOLTIP_STYLE =
            Identifier.fromNamespaceAndPath("brbe", "viewer");

    private static boolean spriteDiagLogged = false;

    private ClientCompat() {
    }

    public static KeyEvent keyEvent(int keyCode, int scanCode, int modifiers) {
        return new KeyEvent(keyCode, scanCode, modifiers);
    }

    public static CharacterEvent characterEvent(char character, int modifiers) {
        return new CharacterEvent(character, modifiers);
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

    /** Whether EITHER Shift is held (BRBE's preview hotkey): both the left and
     *  the right Shift expand the preview UI, and the items keep cycling while
     *  it is held (pausing recipe cycling moved to Alt). */
    public static boolean isShiftDown() {
        Minecraft minecraft = Minecraft.getInstance();
        return InputConstants.isKeyDown(minecraft.getWindow(), InputConstants.KEY_LSHIFT)
                || InputConstants.isKeyDown(minecraft.getWindow(), InputConstants.KEY_RSHIFT);
    }

    /** Whether EITHER Alt is held: the pause-recipe-cycling key — while held,
     *  cycled variants freeze, and Alt+wheel steps through them manually. */
    public static boolean isAltDown() {
        Minecraft minecraft = Minecraft.getInstance();
        return InputConstants.isKeyDown(minecraft.getWindow(), InputConstants.KEY_LALT)
                || InputConstants.isKeyDown(minecraft.getWindow(), InputConstants.KEY_RALT);
    }

    /** 「锁定折叠物品」按键是否按住（配置项 {@code cycleLockKey}，默认左 Alt）。
     *  轮询物理键（不走 {@link KeyMapping} 的事件状态，与既有 Alt/Shift/Ctrl
     *  判定一致）；绑定的修饰键（ctrl/shift/alt 前缀）也必须按住。
     *
     *  <p>配置键是左/右 Alt 之一时**两边 Alt 都认** —— 保留历史上「按住 Alt
     *  锁定」在左右 Alt 上都生效的手感。 */
    public static boolean isCycleLockDown() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getWindow() == null) return false;
        String raw = BetterRecipeBook.config == null ? null : BetterRecipeBook.config.cycleLockKey;
        ModifierKeyCode mkc = KeybindingCodec.decode(raw);
        if (mkc == null || mkc.isUnknown()) return false;
        InputConstants.Key bound = mkc.getKeyCode();
        if (bound.getType() != InputConstants.Type.KEYSYM) return false;
        int code = bound.getValue();
        boolean down = InputConstants.isKeyDown(minecraft.getWindow(), code);
        if (!down && (code == InputConstants.KEY_LALT || code == InputConstants.KEY_RALT)) {
            down = isAltDown();
        }
        if (!down) return false;
        Modifier modifier = mkc.getModifier();
        if (modifier.hasControl() && !isControlDown()) return false;
        if (modifier.hasShift() && !isShiftDown()) return false;
        if (modifier.hasAlt() && !isAltDown()) return false;
        return true;
    }

    /** Play the shared page-flip UI click — gated by the "鼠标滚轮翻页音效"
     *  toggle and scaled by the page-flip volume setting (0.25 x volume, the
     *  same scaling the recipe book's scroll flips use).  Every paging surface
     *  (query viewer object area / tab strip / station column, RBIP tab area)
     *  goes through here, so the toggle, the volume slider and the configurable
     *  sound ID ({@link PageFlipSound}, set via the config screen or
     *  {@code /brbe set pagesound}) govern them all. */
    public static void playPageFlipSound(Minecraft mc) {
        if (!BetterRecipeBook.config.scrollPageSound) return;
        if (mc == null || mc.getSoundManager() == null) return;
        float volume = 0.25f * BetterRecipeBook.config.pageFlipVolume;
        if (volume > 0.0f) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(
                    PageFlipSound.resolve(), 1.0f, volume));
        }
    }

    /**
     * Matches the "pin" (固定) key binding — shared by the recipe-book
     * pinning, the query-object pin overlay and the RBIP tab pinning.  The
     * vanilla KeyMapping is the single source; the Cloth Config entry and
     * the config string are kept in sync with it. */
    public static boolean matchesPinKey(int keyCode, int scanCode, int modifiers) {
        return matches(BetterRecipeBook.PIN_MAPPING, keyCode, scanCode, modifiers);
    }

    private static boolean matchesBinding(ModifierKeyCode binding, int keyCode, int modifiers) {
        if (binding == null || binding.isUnknown()) return false;
        if (binding.getKeyCode().getType() != InputConstants.Type.KEYSYM) return false;
        if (binding.getKeyCode().getValue() != keyCode) return false;
        Modifier modifier = binding.getModifier();
        boolean needCtrl = modifier.hasControl();
        boolean hasCtrl = (modifiers & InputConstants.MOD_CONTROL) != 0;
        if (needCtrl != hasCtrl) return false;
        if (modifier.hasShift() && (modifiers & InputConstants.MOD_SHIFT) == 0) return false;
        if (modifier.hasAlt() && (modifiers & InputConstants.MOD_ALT) == 0) return false;
        return true;
    }

    public static void blitSprite(GuiGraphics gui, Identifier sprite, int x, int y, int width, int height) {
        gui.blitSprite(GUI_TEXTURED, sprite, x, y, width, height);
    }

    public static void setComponentTooltipForNextFrame(GuiGraphics gui, List<Component> tooltip, int mouseX, int mouseY) {
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
            BrbeLogger.log("BRBE-DIAG", "hasSpriteResource: sprite={} fileId={} stackSize={} packs={}",
                    spriteId, fileId, stack.size(), packs);
            // Control: a sprite shipped in the mod's OWN assets (not the built-in pack)
            Identifier pinFile = Identifier.fromNamespaceAndPath("brbe", "textures/gui/sprites/recipe_book/pin.png");
            java.util.List<Resource> pinStack = minecraft.getResourceManager().getResourceStack(pinFile);
            java.util.List<String> pinPacks = new java.util.ArrayList<>();
            for (Resource r : pinStack) pinPacks.add(r.sourcePackId());
            BrbeLogger.log("BRBE-DIAG", "hasSpriteResource: control pin fileId={} stackSize={} packs={}",
                    pinFile, pinStack.size(), pinPacks);
        }
        return !stack.isEmpty();
    }
}
