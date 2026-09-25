package com.alonie.brbe.mixins.command;

import com.alonie.brbe.util.PageFlipSound;
import com.mojang.brigadier.suggestion.Suggestion;
import net.minecraft.client.gui.components.CommandSuggestions;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * 指令补全条目的**点击试听**：在聊天栏输入 {@code /brbe set pagesound <声音ID>} 时，
 * 点补全列表里的任一条目立刻就能听到那个声音（不必回车执行指令）。
 *
 * <p>挂点选在 {@code SuggestionsList.mouseClicked} 的 <b>RETURN</b>：</p>
 * <ul>
 *   <li>返回 {@code true} 才算点中条目（点在列表外返回 false，直接跳过）——
 *       <b>不用自己重算命中区</b>（列表内索引是 {@code (mouseY - rect.y) / 12 + offset}）；</li>
 *   <li>此时 {@code current} 已是被点条目（vanilla 先 {@code select(i)} 再
 *       {@code useSuggestion()}），{@code originalContents} 仍是**点击前**的输入文本
 *       ——正是判断"是否停在我们指令参数位"所需的那份文本；</li>
 *   <li>不触碰外层 {@code CommandSuggestions} 的合成字段（26.x 的 {@code this$0} /
 *       remap 分支的 {@code field_21615}）——合成字段没有映射名，跨分支写法不通用。</li>
 * </ul>
 *
 * <p>键盘选择（Tab 轮循）不在这里试听，只有鼠标点击会响。</p>
 */
@Mixin(CommandSuggestions.SuggestionsList.class)
public abstract class SuggestionsListMixin {

    @Shadow @Final private String originalContents;

    @Shadow @Final private List<Suggestion> suggestionList;

    @Shadow private int current;

    @Inject(method = "mouseClicked", at = @At("RETURN"))
    private void brbe$previewPageSound(int mouseX, int mouseY, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ()) return;
        if (this.suggestionList == null || this.current < 0 || this.current >= this.suggestionList.size()) {
            return;
        }
        PageFlipSound.previewSuggestion(this.originalContents,
                this.suggestionList.get(this.current).getText());
    }
}
