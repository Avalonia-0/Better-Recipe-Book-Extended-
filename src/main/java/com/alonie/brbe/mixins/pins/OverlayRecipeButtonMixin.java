package com.alonie.brbe.mixins.pins;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.mixins.accessors.OverlayRecipeButtonAccessor;
import com.alonie.brbe.util.BRBTextures;
import com.alonie.brbe.util.ClientCompat;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.recipebook.OverlayRecipeComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 替代配方浮层（overlay 变体按钮）的 pin 贴图：被 pin 的变体按钮左上角
 *  画图钉——替代配方组/副本替代配方组打开后，其 pin 变体带标记（与网格
 *  按钮的 {@link RecipeButtonMixin} 一致）。
 *
 *  <p>注意：不能 {@code @Shadow getX()/getY()}——该成员继承自
 *  {@link AbstractWidget}（target 未声明），Mixin 会报 "Shadow was not
 *  located in the target class" 使整个类变换失败（替代浮层就打不开/组为空）。
 *  坐标经运行时强转 {@link AbstractWidget} 获取。</p> */
@Mixin(targets = "net.minecraft.client.gui.screens.recipebook.OverlayRecipeComponent$OverlayRecipeButton")
public abstract class OverlayRecipeButtonMixin {

    @Inject(method = "extractWidgetRenderState", at = @At("RETURN"))
    public void brbe$pinMarker(GuiGraphicsExtractor gui, int x, int y, float delta, CallbackInfo ci) {
        AbstractWidget widget = (AbstractWidget) (Object) this;
        OverlayRecipeComponent outer = ((OverlayRecipeButtonAccessor) this).brbe$getOuterComponent();
        RecipeCollection collection = outer == null ? null : outer.getRecipeCollection();
        RecipeDisplayEntry entry = entryForId(collection,
                ((OverlayRecipeButtonAccessor) this).brbe$getRecipe());
        if (entry != null && BetterRecipeBook.pinnedRecipeManager.isPinnedEntry(entry)) {
            ClientCompat.blitSprite(gui, BRBTextures.RECIPE_BOOK_PIN_SPRITE,
                    widget.getX() - 4, widget.getY() - 4, 32, 32);
        }
    }

    /** The {@link RecipeDisplayEntry} of one overlay variant button inside the
     *  group collection ({@code recipeId} equals {@code entry.id()}), or null. */
    private static RecipeDisplayEntry entryForId(RecipeCollection collection, RecipeDisplayId recipeId) {
        if (collection == null || recipeId == null) return null;
        for (RecipeDisplayEntry entry : collection.getRecipes()) {
            if (entry != null && recipeId.equals(entry.id())) return entry;
        }
        return null;
    }
}
