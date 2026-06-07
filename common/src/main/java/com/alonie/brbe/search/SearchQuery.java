package com.alonie.brbe.search;

import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Parses a search query string into a tree of {@link SearchArgument} conditions
 * and provides matching against ItemStacks.
 * <p>
 * Syntax:
 * <ul>
 *   <li><code>|</code> — OR separator between groups</li>
 *   <li><code> </code> (space) — AND within a group</li>
 *   <li><code>"..."</code> — quoted text preserving spaces</li>
 *   <li><code>-</code> prefix — negation (e.g., <code>-@minecraft</code>)</li>
 *   <li><code>@text</code> — mod search (by namespace or display name)</li>
 *   <li><code>$text</code> — tag search (by item tag identifier)</li>
 *   <li><code>#text</code> — tooltip search</li>
 *   <li><code>r/regex/</code> — regex search on item hover name</li>
 * </ul>
 */
public class SearchQuery {
    private static final Pattern OR_SPLIT = Pattern.compile("\\|");
    private static final Pattern TOKEN_SPLIT = Pattern.compile("\"([^\"]*)\"|([^\\s]+)");

    private final AlternativeArgument root;
    private final boolean advanced;

    /**
     * Parses the given search input into a query tree.
     * Returns an empty-matching query if the input is null or blank.
     */
    public static SearchQuery parse(String input) {
        if (input == null || input.isBlank()) {
            return new SearchQuery(new AlternativeArgument(List.of()), false);
        }

        String trimmed = input.trim();
        String[] orParts = OR_SPLIT.split(trimmed, -1);

        List<SearchArgument> compoundArgs = new ArrayList<>();

        for (String part : orParts) {
            part = part.trim();
            if (part.isEmpty()) continue;

            List<SearchArgument> tokens = new ArrayList<>();
            var matcher = TOKEN_SPLIT.matcher(part);

            while (matcher.find()) {
                String token;
                if (matcher.group(1) != null) {
                    // Quoted group: capture inner text as-is
                    token = matcher.group(1);
                } else {
                    // Bare token
                    token = matcher.group(2);
                }
                if (token == null || token.isEmpty()) continue;

                SearchArgument arg = parseToken(token);
                tokens.add(arg);
            }

            if (!tokens.isEmpty()) {
                if (tokens.size() == 1) {
                    compoundArgs.add(tokens.get(0));
                } else {
                    compoundArgs.add(new CompoundArgument(tokens));
                }
            }
        }

        if (compoundArgs.isEmpty()) {
            return new SearchQuery(new AlternativeArgument(List.of()), false);
        }

        boolean advanced = compoundArgs.stream().anyMatch(SearchArgument::isAdvanced);
        AlternativeArgument root;
        if (compoundArgs.size() == 1) {
            root = new AlternativeArgument(compoundArgs);
        } else {
            root = new AlternativeArgument(compoundArgs);
        }

        return new SearchQuery(root, advanced);
    }

    private static SearchArgument parseToken(String token) {
        boolean negated = false;
        String text = token;

        // Check for negation prefix
        if (text.startsWith("-") && text.length() > 1) {
            // But "--" might be an edge case — treat double-dash as literal
            if (!text.startsWith("--")) {
                negated = true;
                text = text.substring(1);
            }
        }

        SearchArgument arg;

        if (text.startsWith("@") && text.length() > 1) {
            arg = new ModArgument(text.substring(1));
        } else if (text.startsWith("$") && text.length() > 1) {
            arg = new TagArgument(text.substring(1));
        } else if (text.startsWith("#") && text.length() > 1) {
            arg = new TooltipArgument(text.substring(1));
        } else if (text.startsWith("r/") && text.length() > 3) {
            int closingSlash = text.lastIndexOf('/');
            if (closingSlash > 2) {
                String regex = text.substring(2, closingSlash);
                try {
                    arg = new RegexArgument(regex);
                } catch (Exception e) {
                    // Invalid regex — fall back to text match of the entire token
                    arg = new TextArgument(token);
                }
            } else {
                arg = new TextArgument(token);
            }
        } else {
            arg = new TextArgument(text);
        }

        if (negated) {
            return new NegatedArgument(arg);
        }
        return arg;
    }

    private SearchQuery(AlternativeArgument root, boolean advanced) {
        this.root = root;
        this.advanced = advanced;
    }

    /**
     * Returns true if the given ItemStack matches this query.
     */
    public boolean matches(ItemStack stack, SearchCache cache) {
        return root.matches(stack, cache);
    }

    /**
     * Returns true if the query contains any advanced syntax
     * (beyond plain substring matching).
     */
    public boolean isAdvanced() {
        return advanced;
    }

    /**
     * A query that matches everything (empty search).
     */
    public static final SearchQuery EMPTY = new SearchQuery(
            new AlternativeArgument(List.of()), false);
}
