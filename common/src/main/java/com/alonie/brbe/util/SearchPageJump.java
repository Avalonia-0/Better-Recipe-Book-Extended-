package com.alonie.brbe.util;

import com.alonie.brbe.mixins.accessors.EditBoxPreeditAccessor;
import com.alonie.brbe.mixins.accessors.IMEPreeditOverlayAccessor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.IMEPreeditOverlay;
import net.minecraft.network.chat.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 搜索栏页码跳转命令解析。
 *
 * <p>格式为 {@code <分隔符><页码><分隔符>}，分隔符是 {@code ^} 或省略号
 * （一个或多个 U+2026），前后分隔符可以不同。合法示例：
 * {@code ^50^}、{@code ^50……}、{@code ……50^}、{@code ……50……}。
 * 用户依次输入开分隔符、页码、闭分隔符，闭合时由搜索刷新点检测并触发跳转。</p>
 *
 * <p>省略号经 IME 输入时常停留在组合（preedit）状态、尚未 commit 进
 * {@link EditBox#getValue()}，因此检测需拼接 preedit 文本（见 {@link #fullText}）。</p>
 */
public final class SearchPageJump {

    // 闭合分隔符必须是 "^" 或至少 2 个省略号：省略号逐字上屏时，第 1 个闭合
    // "…" 是中间态（用户还会输第 2 个），收紧后避免提前触发；"^" 是单字符
    // ASCII 直接上屏，一次就绪。
    private static final Pattern PATTERN = Pattern.compile("^(?:\\^|…+)(\\d+)(?:\\^|…{2,})$");

    private SearchPageJump() {
    }

    /** 从搜索框读取完整文本（value + IME 组合文本），再做命令解析。 */
    public static int parse(EditBox searchBox) {
        return parse(fullText(searchBox));
    }

    /**
     * {@link EditBox#getValue()} 不含 IME 组合（preedit）文本——省略号作为
     * 最后输入时可能停留在组合状态，拼接上才是用户实际看到的完整内容。
     * 光标在末尾（命令逐字输入场景）时 preedit 即追加在 value 之后。
     */
    public static String fullText(EditBox searchBox) {
        String value = searchBox.getValue();
        IMEPreeditOverlay overlay = ((EditBoxPreeditAccessor) searchBox).getPreeditOverlay();
        if (overlay != null) {
            Component preedit = ((IMEPreeditOverlayAccessor) overlay).getPreEditText();
            if (preedit != null) {
                return value + preedit.getString();
            }
        }
        return value;
    }

    /** 返回 1-indexed 页码；不匹配或页码为 0 时返回 -1。 */
    public static int parse(String text) {
        if (text == null) return -1;
        Matcher matcher = PATTERN.matcher(text);
        if (!matcher.matches()) return -1;
        try {
            int page = Integer.parseInt(matcher.group(1));
            return page > 0 ? page : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
