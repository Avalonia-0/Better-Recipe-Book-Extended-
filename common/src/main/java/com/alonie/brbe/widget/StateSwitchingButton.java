package com.alonie.brbe.widget;

import com.alonie.brbe.util.ClientCompat;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.resources.Identifier;

public class StateSwitchingButton extends AbstractWidget {
    protected WidgetSprites sprites = new WidgetSprites(Identifier.withDefaultNamespace("widget/button"));
    protected boolean isStateTriggered;
    private final boolean mirrored;
    private boolean useStateTriggeredForTexture;

    public StateSwitchingButton(int x, int y, int width, int height, boolean mirrored) {
        super(x, y, width, height, CommonComponents.EMPTY);
        this.mirrored = mirrored;
    }

    public void initTextureValues(WidgetSprites sprites) {
        this.sprites = sprites;
    }

    public void setStateTriggered(boolean stateTriggered) {
        this.isStateTriggered = stateTriggered;
    }

    public boolean isStateTriggered() {
        return this.isStateTriggered;
    }

    public void useStateTriggeredForTexture(boolean useStateTriggeredForTexture) {
        this.useStateTriggeredForTexture = useStateTriggeredForTexture;
    }

    public void setPosition(int x, int y) {
        this.setX(x);
        this.setY(y);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return ClientCompat.mouseClicked(this, mouseX, mouseY, button);
    }

    @Override
    protected boolean isValidClickButton(MouseButtonInfo button) {
        return button.button() == 0;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor gui, int mouseX, int mouseY, float delta) {
        boolean enabledState = this.useStateTriggeredForTexture ? this.isStateTriggered : this.active;
        boolean focusedState = this.useStateTriggeredForTexture ? this.isHoveredOrFocused() : this.isHoveredOrFocused();
        Identifier sprite = this.sprites.get(enabledState, focusedState);
        gui.pose().pushMatrix();
        if (this.mirrored) {
            gui.pose().translate(this.getX() + this.width, this.getY());
            gui.pose().scale(-1.0F, 1.0F);
            ClientCompat.blitSprite(gui, sprite, 0, 0, this.width, this.height);
        } else {
            ClientCompat.blitSprite(gui, sprite, this.getX(), this.getY(), this.width, this.height);
        }
        gui.pose().popMatrix();
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
        this.defaultButtonNarrationText(narrationElementOutput);
    }
}
