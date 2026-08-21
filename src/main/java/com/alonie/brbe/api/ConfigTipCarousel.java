package com.alonie.brbe.api;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * 一条配置界面轮循提示行。
 *
 * <p>每个轮循行绑定一个配置分类（页面）、一个可选前缀（如"提示："）、一个文案池。
 * 每次打开配置界面，该行显示一条（随机，且避免相邻重复）。多个轮循行可注册到不同
 * 页面或同一页面，各自独立不重复。</p>
 *
 * <p>通过 {@link com.alonie.brbe.util.ConfigTipsHelper#registerCarousel} 注册。</p>
 */
public final class ConfigTipCarousel {

    private final Component categoryTitle;
    private final Component prefix;
    private final List<String> tipKeys;
    private final ChatFormatting style;

    private int lastIndex = -1;

    private ConfigTipCarousel(Component categoryTitle, Component prefix,
                              List<String> tipKeys, ChatFormatting style) {
        this.categoryTitle = categoryTitle;
        this.prefix = prefix;
        this.tipKeys = tipKeys;
        this.style = style;
    }

    public Component categoryTitle() {
        return categoryTitle;
    }

    public Component prefix() {
        return prefix;
    }

    public boolean hasTips() {
        return !tipKeys.isEmpty();
    }

    /**
     * 选一条文案键（避开上次显示的）。返回 -1 表示无可用文案。
     */
    public int nextTipIndex() {
        if (tipKeys.size() == 1) {
            return 0;
        }
        int idx;
        do {
            idx = (int) (Math.random() * tipKeys.size());
        } while (idx == lastIndex);
        lastIndex = idx;
        return idx;
    }

    public Component tipAt(int index) {
        return Component.translatable(tipKeys.get(index));
    }

    public ChatFormatting style() {
        return style;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private Component categoryTitle;
        private Component prefix;
        private List<String> tipKeys = List.of();
        private ChatFormatting style = ChatFormatting.YELLOW;

        private Builder() {
        }

        public Builder category(Component categoryTitle) {
            this.categoryTitle = categoryTitle;
            return this;
        }

        public Builder prefix(Component prefix) {
            this.prefix = prefix;
            return this;
        }

        public Builder tipKeys(List<String> tipKeys) {
            this.tipKeys = List.copyOf(tipKeys);
            return this;
        }

        public Builder style(ChatFormatting style) {
            this.style = style;
            return this;
        }

        public ConfigTipCarousel build() {
            if (categoryTitle == null) {
                throw new IllegalStateException("category required");
            }
            return new ConfigTipCarousel(categoryTitle, prefix, tipKeys, style);
        }
    }
}
