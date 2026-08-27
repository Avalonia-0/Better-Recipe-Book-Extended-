package com.alonie.brbe.search;

import com.alonie.brbe.BetterRecipeBook;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;

/**
 * Plain substring match on the item's hover name.
 * This is equivalent to the original simple search behavior.
 */
public class TextArgument implements SearchArgument {
    private final String searchText;

    public TextArgument(String searchText) {
        this.searchText = searchText.toLowerCase(Locale.ROOT);
    }

    @Override
    public boolean matches(ItemStack stack, SearchCache cache) {
        String name = stack.getHoverName().getString().toLowerCase(Locale.ROOT);
        // 配置开启且查询词为纯 ASCII（拼音/英文）且名称含汉字时走拼音匹配；
        // 否则保持原 substring 行为（纯英文名零开销）。
        if (pinyinEnabled() && isAscii(searchText) && containsCjk(name)) {
            return PinyinMatcher.contains(name.codePoints().toArray(), searchText.codePoints().toArray());
        }
        return name.contains(searchText);
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
