package com.alonie.recipebookispain_extended.access;

import net.minecraft.client.gui.screens.recipebook.RecipeBookTabButton;
import net.minecraft.world.item.CreativeModeTab;

/**
 * Mixin 公开桥接口（须放普通包——mixin 专包类不可被外部引用）：
 * {@link com.alonie.recipebookispain_extended.mixin.widget.RecipeBookWidgetMixin} 注入的
 * {@code rbip$buttonToTab} 映射经由本接口暴露给外部（Mixin 规则禁止
 * 非私有静态方法——接口方法注入为 public 合法）。
 */
public interface RbipTabBridge {

    /** 标签 → 创造模式标签映射（注入在目标 RecipeBookWidget 上）。 */
    CreativeModeTab rbip$tabToGroup(RecipeBookTabButton tab);
}
