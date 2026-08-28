package com.alonie.brbe.util;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.config.KeybindingCodec;
import com.mojang.blaze3d.platform.InputConstants;
import me.shedaniel.clothconfig2.api.Modifier;
import me.shedaniel.clothconfig2.api.ModifierKeyCode;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.List;

/**
 * 1.21.1 版 ClientCompat：与 1.21.11 同名的调用接口（供后续移植代码使用），
 * 实现体按 1.21.1 的 Minecraft API 适配（无 RenderPipeline/KeyEvent/CharacterEvent
 * modifiers 参数等 1.21.5+ 重构）。
 */
public final class ClientCompat {

    /** 查询系统（R/U viewer + pin）tooltip 的自定义背景样式：解析为
     *  {@code brbe:tooltip/viewer_background} / {@code viewer_frame}
     *  sprite（textures/gui/sprites/tooltip/ 下，背景 alpha 已调淡为 160）。
     *  传给原版 {@code renderTooltip(...)} 链路的最后一个 ResourceLocation 参数
     *  即可，不影响配方书/原版 tooltip。 */
    public static final ResourceLocation VIEWER_TOOLTIP_STYLE =
            ResourceLocation.fromNamespaceAndPath("brbe", "viewer");

    private ClientCompat() {
    }

    /* -- Input helpers (1.21.1: KeyMapping.matches(int,int) / EditBox 旧签名) -- */

    /** 1.21.1 的 KeyMapping.matches(keyCode, scanCode) 不含 modifiers——
     *  键位匹配放开给 mod 自身语义（1.21.11 的 modifiers 匹配此处降级为
     *  keyCode+scanCode 匹配，BRBE 键位无修饰键组合）。 */
    public static boolean matches(KeyMapping keyMapping, int keyCode, int scanCode, int modifiers) {
        return keyMapping.matches(keyCode, scanCode);
    }

    public static boolean keyPressed(EditBox editBox, int keyCode, int scanCode, int modifiers) {
        return editBox.keyPressed(keyCode, scanCode, modifiers);
    }

    public static boolean charTyped(EditBox editBox, char character, int modifiers) {
        return editBox.charTyped(character, modifiers);
    }

    public static boolean mouseClicked(AbstractWidget widget, double mouseX, double mouseY, int button) {
        return widget.mouseClicked(mouseX, mouseY, button);
    }

    public static boolean isControlDown() {
        Minecraft minecraft = Minecraft.getInstance();
        return InputConstants.isKeyDown(minecraft.getWindow().getWindow(), InputConstants.KEY_LCONTROL)
                || InputConstants.isKeyDown(minecraft.getWindow().getWindow(), InputConstants.KEY_RCONTROL);
    }

    /** Whether EITHER Shift is held (BRBE's preview hotkey): both the left and
     *  the right Shift expand the preview UI, and the items keep cycling while
     *  it is held (pausing recipe cycling moved to Alt). */
    public static boolean isShiftDown() {
        Minecraft minecraft = Minecraft.getInstance();
        return InputConstants.isKeyDown(minecraft.getWindow().getWindow(), InputConstants.KEY_LSHIFT)
                || InputConstants.isKeyDown(minecraft.getWindow().getWindow(), InputConstants.KEY_RSHIFT);
    }

    /** Whether EITHER Alt is held: the pause-recipe-cycling key — while held,
     *  cycled variants freeze, and Alt+wheel steps through them manually. */
    public static boolean isAltDown() {
        Minecraft minecraft = Minecraft.getInstance();
        return InputConstants.isKeyDown(minecraft.getWindow().getWindow(), InputConstants.KEY_LALT)
                || InputConstants.isKeyDown(minecraft.getWindow().getWindow(), InputConstants.KEY_RALT);
    }

    /** Play the shared page-flip UI click — gated by the "鼠标滚轮翻页音效"
     *  toggle and scaled by the page-flip volume setting (0.25 x volume, the
     *  same scaling the recipe book's scroll flips use).  Every paging surface
     *  (query viewer object area / tab strip / station column, RBIP tab area)
     *  goes through here, so the toggle and the volume slider govern them all. */
    /** 翻页音效上次播放时间（毫秒），0.01s 节流——快速滚动不叠音（1.21.11 语义）。 */
    private static long brbe$lastPageFlipSoundTime;

    public static void playPageFlipSound(Minecraft mc) {
        if (BetterRecipeBook.config == null || !BetterRecipeBook.config.scrollPageSound) return;
        if (mc == null) return;
        long now = net.minecraft.Util.getMillis();
        if (now - brbe$lastPageFlipSoundTime < 10) return;
        brbe$lastPageFlipSoundTime = now;
        float volume = 0.25f * BetterRecipeBook.config.pageFlipVolume;
        if (volume > 0.0f && mc.getSoundManager() != null) {
            // 3 参重载 (sound, pitch, volume)：pitch=1.0 与按钮点击原声一致。
            // ⚠️ 2 参 forUI(sound, p) 的 p 是 **pitch** 不是音量——此前把 volume
            // 当 pitch 传入 → 点击音变成低频闷响（"音效源用错了"根因）。
            mc.getSoundManager().play(SimpleSoundInstance.forUI(
                    SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, volume));
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

    /** Keep for API parity with 1.21.11 (Cloth key binding checks). */
    private static boolean matchesBinding(ModifierKeyCode binding, int keyCode, int modifiers) {
        if (binding == null || binding.isUnknown()) return false;
        if (binding.getKeyCode().getType() != InputConstants.Type.KEYSYM) return false;
        if (binding.getKeyCode().getValue() != keyCode) return false;
        Modifier modifier = binding.getModifier();
        boolean needCtrl = modifier.hasControl();
        boolean hasCtrl = (modifiers & InputConstants.MOD_CONTROL) != 0;
        if (needCtrl != hasCtrl) return false;
        // 1.21.1 无 MOD_SHIFT/MOD_ALT 常量：用 GLFW 键态实时检测（与 1.21.11 的
        // modifiers 位语义等效——shift/alt 修饰键在按键时必然被按下）。
        Minecraft mc = Minecraft.getInstance();
        if (modifier.hasShift() && mc != null
                && !InputConstants.isKeyDown(mc.getWindow().getWindow(), InputConstants.KEY_LSHIFT)
                && !InputConstants.isKeyDown(mc.getWindow().getWindow(), InputConstants.KEY_RSHIFT)) return false;
        if (modifier.hasAlt() && mc != null
                && !InputConstants.isKeyDown(mc.getWindow().getWindow(), InputConstants.KEY_LALT)
                && !InputConstants.isKeyDown(mc.getWindow().getWindow(), InputConstants.KEY_RALT)) return false;
        return true;
    }

    /* -- Render helpers (1.21.1: GuiGraphics.blitSprite(ResourceLocation,…)) - */

    public static void blitSprite(GuiGraphics gui, ResourceLocation sprite, int x, int y, int width, int height) {
        gui.blitSprite(sprite, x, y, width, height);
    }


    /* -- Ingredient helpers (1.21.1: getItems() returns ItemStack[]) ---------- */

    public static ItemStack[] ingredientItems(Ingredient ingredient) {
        return ingredient.getItems();
    }

    public static ItemStack firstIngredientItem(Ingredient ingredient) {
        ItemStack[] items = ingredientItems(ingredient);
        return items.length == 0 ? ItemStack.EMPTY : items[0];
    }
}
