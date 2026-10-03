package com.alonie.brbe.search;

import com.alonie.brbe.BetterRecipeBook;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;

/**
 * Plain substring match on the item's hover name.
 *
 * <p>{@link SearchCache#tooltipFallback()} 打开时（自研书）额外匹配 **tooltip 全文**——
 * 与原版配方书搜索的语料一致（原版索引产物物品的全部 tooltip 行），见
 * {@code SearchCache#tooltipFallback} 的说明。</p>
 */
public class TextArgument implements SearchArgument {
    private final String searchText;

    public TextArgument(String searchText) {
        this.searchText = searchText.toLowerCase(Locale.ROOT);
    }

    @Override
    public boolean matches(ItemStack stack, SearchCache cache) {
        String name = stack.getHoverName().getString().toLowerCase(Locale.ROOT);
        if (matchesText(name, searchText)) {
            return true;
        }
        // 名字没命中才去看 tooltip（tooltip 生成有开销，按 stack 缓存）。
        if (!cache.tooltipFallback()) {
            return false;
        }
        String tooltip = cache.getTooltipText(stack);
        return !tooltip.isEmpty() && matchesText(tooltip.toLowerCase(Locale.ROOT), searchText);
    }

    /**
     * 一段文本里找查询词：配置开启且查询词为纯 ASCII（拼音/英文）且文本含汉字时走拼音匹配，
     * 否则保持原 substring 行为（纯英文零开销）。
     */
    private static boolean matchesText(String haystack, String needle) {
        if (pinyinEnabled() && isAscii(needle) && containsCjk(haystack)) {
            return PinyinMatcher.contains(haystack.codePoints().toArray(), needle.codePoints().toArray());
        }
        return haystack.contains(needle);
    }

    private static boolean pinyinEnabled() {
        return BetterRecipeBook.config != null && BetterRecipeBook.config.pinyinSearch;
    }

    private static boolean isAscii(String s) {
        if (s.isEmpty()) return false;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) >= 128) return false;
        }
        return true;
    }

    private static boolean containsCjk(String s) {
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            if ((cp >= 0x4E00 && cp <= 0x9FFF) || (cp >= 0x3400 && cp <= 0x4DBF)) return true;
            i += Character.charCount(cp);
        }
        return false;
    }

    @Override
    public boolean isAdvanced() {
        return false;
    }

    public String getSearchText() {
        return searchText;
    }
}
