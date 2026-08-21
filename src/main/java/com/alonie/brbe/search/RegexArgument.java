package com.alonie.brbe.search;

import net.minecraft.world.item.ItemStack;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Searches by regex pattern on the item's hover name. Syntax: r/regex/
 * <p>
 * The pattern is compiled case-insensitively and uses {@link Pattern#matcher(CharSequence)}.{@link java.util.regex.Matcher#find() find()}.
 */
public class RegexArgument implements SearchArgument {
    private final Pattern pattern;

    public RegexArgument(String regex) {
        this.pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
    }

    @Override
    public boolean matches(ItemStack stack, SearchCache cache) {
        return pattern.matcher(stack.getHoverName().getString()).find();
    }

    @Override
    public boolean isAdvanced() {
        return true;
    }
}
