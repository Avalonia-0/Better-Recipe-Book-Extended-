package com.alonie.brbe.mixins.settings;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.config.BrbeConfig;
import com.alonie.brbe.layout.BookLayout;
import com.alonie.brbe.util.BRBTextures;
import com.alonie.brbe.util.ClientCompat;
import com.alonie.brbe.util.ConfigTipsHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * NOTE: Does NOT implement ISettingsButton. BRB already injects its own
 * ISettingsButton into RecipeBookComponent (via marsh.town.brb). Adding a
 * second ISettingsButton from BRBE causes an IncompatibleClassChangeError.
 * Settings button logic is inlined here.
 */
@Mixin(RecipeBookComponent.class)
public abstract class RecipeBookComponentMixin {

    @Shadow
    protected Minecraft minecraft;

    @Shadow
    private int height;
    @Shadow
    private int width;
    @Shadow
    private int xOffset;

    @Shadow
    public abstract boolean isVisible();

    @Unique
    protected ImageButton _$settingsButton;

    @Inject(method = "initVisuals", at = @At("RETURN"))
    public void reset(CallbackInfo ci) {
        if (BetterRecipeBook.ctx().config().settingsButton) {
            int i = (this.width - BookLayout.TEXTURE_WIDTH) / 2 - this.xOffset;
            int j = (this.height - BookLayout.TEXTURE_HEIGHT) / 2 + BookLayout.settingsY();
            this._$settingsButton = new ImageButton(i + BookLayout.SETTINGS_X_OFFSET, j,
                    BookLayout.settingsSize(), BookLayout.settingsSize(),
                    BRBTextures.SETTINGS_BUTTON_SPRITES, this::brbe$openConfigFromSettings);
        }
    }

    /**
     * 设置按钮回调。
     *
     * <p><b>为什么拆成方法 + 方法引用</b>：mixin 类里的 lambda 会被编译成合成方法，
     * Mixin 必须重命名它们（否则与目标类同名合成方法冲突）并在 latest.log 打一行
     * {@code Renaming synthetic method ...}；方法引用走 invokedynamic 的
     * {@code MethodHandle}，与直接调用走同一套重映射（{@code transformMethodRef}），
     * 不产生合成方法、不刷日志。</p>
     */
    @Unique
    private void brbe$openConfigFromSettings(Button button) {
        ConfigTipsHelper.openConfigScreen(BrbeConfig.class, Minecraft.getInstance().screen);
    }

    @Inject(method = "mouseClicked", at = @At("RETURN"), cancellable = true)
    public void mouseClicked(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        if (!this.isVisible()) return;
        if (this._$settingsButton != null && BetterRecipeBook.ctx().config().settingsButton
                && ClientCompat.mouseClicked(this._$settingsButton, event.x(), event.y(), event.button())) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "render", at = @At("RETURN"))
    public void render(GuiGraphics gui, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (this._$settingsButton != null) {
            this._$settingsButton.visible = this.isVisible() && BetterRecipeBook.ctx().config().settingsButton;
        }
        if (!this.isVisible()) return;
        if (this._$settingsButton != null && BetterRecipeBook.ctx().config().settingsButton) {
            this._$settingsButton.render(gui, mouseX, mouseY, delta);
        }
    }

    @Inject(method = "renderTooltip", at = @At("RETURN"))
    public void drawTooltip(GuiGraphics gui, int mouseX, int mouseY, Slot hoveredSlot, CallbackInfo ci) {
        if (!this.isVisible()) return;
        if (this._$settingsButton != null && this._$settingsButton.isHoveredOrFocused()
                && BetterRecipeBook.ctx().config().settingsButton && Minecraft.getInstance().screen != null) {
            ClientCompat.setComponentTooltipForNextFrame(gui,
                    java.util.List.of(Component.translatable("brbe.gui.settings.open")), mouseX, mouseY);
        }
    }
}
