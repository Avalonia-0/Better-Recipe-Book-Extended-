package com.alonie.brbe.api;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * 一条配置界面轮循提示行。
 *
 * <p>每个轮循行有一个文案池，每次打开配置界面显示一条（随机，且避免相邻重复）。
 * 多个轮循行各自独立不重复。</p>
 *
 * <p>显示位置有两种（{@link #screenWide()}）：</p>
 * <ul>
 *   <li><b>屏幕级</b>（{@code screenWide = true}，推荐）：插在配置界面**搜索栏之上**，
 *       切到任何类别页都可见 —— 位置与 {@code category(...)} 无关；</li>
 *   <li><b>类别内</b>（默认，{@code false}）：插在 {@code category(...)} 所绑定类别条目列表的
 *       第一条（老行为）。</li>
 * </ul>
 *
 * <p>通过 {@link com.alonie.brbe.util.ConfigTipsHelper#registerCarousel} 注册。</p>
 */
public final class ConfigTipCarousel {

    private final Component categoryTitle;
    private final List<String> tipKeys;
    private final ChatFormatting style;
    private final boolean screenWide;

    private int lastIndex = -1;

    private ConfigTipCarousel(Component categoryTitle,
                              List<String> tipKeys, ChatFormatting style, boolean screenWide) {
        this.categoryTitle = categoryTitle;
        this.tipKeys = tipKeys;
        this.style = style;
        this.screenWide = screenWide;
    }

    public Component categoryTitle() {
        return categoryTitle;
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

    /**
     * 是否显示在**屏幕级**：插在配置界面搜索栏之上，所有类别页都可见（{@code category(...)}
     * 此时仅作标识，不决定位置）。
     */
    public boolean screenWide() {
        return screenWide;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private Component categoryTitle;
        private List<String> tipKeys = List.of();
        private ChatFormatting style = ChatFormatting.YELLOW;
        private boolean screenWide = false;

        private Builder() {
        }

        public Builder category(Component categoryTitle) {
            this.categoryTitle = categoryTitle;
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

        /** 屏幕级显示：搜索栏之上、所有类别页可见（默认 {@code false} = 插在绑定类别内）。 */
        public Builder screenWide(boolean screenWide) {
            this.screenWide = screenWide;
            return this;
        }

        public ConfigTipCarousel build() {
            if (categoryTitle == null) {
                throw new IllegalStateException("category required");
            }
            return new ConfigTipCarousel(categoryTitle, tipKeys, style, screenWide);
        }
    }
}
