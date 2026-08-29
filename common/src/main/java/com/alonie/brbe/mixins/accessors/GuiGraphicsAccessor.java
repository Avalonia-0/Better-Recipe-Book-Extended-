package com.alonie.brbe.mixins.accessors;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

/**
 * 富 tooltip 入口（1.21.1 的 {@code GuiGraphics.renderTooltipInternal} 在
 * 编译器可见的 merged jar 中为 private——经 Invoker 调用）。1.21.11 是 public，
 * 1.21.1 是 private，这是移植 5 个 tooltip 组件类的必需注入。
 */
@Mixin(GuiGraphics.class)
public interface GuiGraphicsAccessor {
    @Invoker("renderTooltipInternal")
    void brbe$renderTooltipInternal(Font font, List<ClientTooltipComponent> components,
                                    int mouseX, int mouseY, ClientTooltipPositioner positioner);
}
