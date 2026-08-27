package com.alonie.brbe.search;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 汉字 → 拼音音节展开。移植自 REI {@code PinyinInputMethod} +
 * {@code UniHanInputMethod}（MIT，shedaniel）。
 * <p>
 * 把字表里每个带声调的读音（如 {@code mù}）展开成「声母 / 韵母 / 声调数字」多段
 * phonemes（如 {@code [[m],[u],[4]]}），存入 {@code codePoint → List<ExpendedChar>}。
 * 生僻字 / ASCII 字符无读音，展开为原字符单段（支持中英混合名）。
 * <p>
 * 模糊音（z↔zh 等）结构保留但默认关闭，与 REI 默认行为一致（无配置界面）。
 */
public final class PinyinInputMethod {
    private static final Map<Integer, List<ExpendedChar>> DATA = new HashMap<>();

    private static final Map<Character, ToneEntry> TONE_MAP = new HashMap<>();
    private static final Map<String, String> FUZZY_MAP = new HashMap<>();

    private static volatile boolean loaded;

    private record ToneEntry(char base, int tone) {
    }

    static {
        addTone('ā', "a1");
        addTone('á', "a2");
        addTone('ǎ', "a3");
        addTone('à', "a4");
        addTone('ē', "e1");
        addTone('é', "e2");
        addTone('ě', "e3");
        addTone('è', "e4");
        addTone('ī', "i1");
        addTone('í', "i2");
        addTone('ǐ', "i3");
        addTone('ì', "i4");
        addTone('ō', "o1");
        addTone('ó', "o2");
        addTone('ǒ', "o3");
        addTone('ò', "o4");
        addTone('ū', "u1");
        addTone('ú', "u2");
        addTone('ǔ', "u3");
        addTone('ù', "u4");
        addTone('ǖ', "v1");
        addTone('ǘ', "v2");
        addTone('ǚ', "v3");
        addTone('ǜ', "v4");
        addFuzzy("z", "zh");
        addFuzzy("s", "sh");
        addFuzzy("c", "ch");
        addFuzzy("an", "ang");
        addFuzzy("en", "eng");
        addFuzzy("in", "ing");
        addFuzzy("ian", "iang");
        addFuzzy("uan", "uang");
        addFuzzy("n", "l");
        addFuzzy("r", "l");
        addFuzzy("h", "f");
    }

    private static void addTone(char c, String s) {
        TONE_MAP.put(c, new ToneEntry(s.charAt(0), Character.digit(s.charAt(1), 10)));
    }

    private static void addFuzzy(String from, String to) {
        FUZZY_MAP.put(from, to);
    }

    private PinyinInputMethod() {
    }

    /**
     * 返回某个 codepoint 的所有可能拼音展开；无读音时返回原字符单段。
     */
    static List<ExpendedChar> expendSourceChar(int codePoint) {
        ensureLoaded();
        List<ExpendedChar> expanded = DATA.get(codePoint);
        if (expanded != null && !expanded.isEmpty()) return expanded;
        return List.of(new ExpendedChar(List.of(new int[]{codePoint})));
    }

    private static void ensureLoaded() {
        if (loaded) return;
        synchronized (PinyinInputMethod.class) {
            if (loaded) return;
            PinyinData.map().forEach((codePoint, readings) -> {
                List<ExpendedChar> sequences = new ArrayList<>();
                for (String reading : readings) {
                    sequences.addAll(asExpendedChars(reading));
                }
                DATA.put(codePoint, List.copyOf(sequences));
            });
            loaded = true;
        }
    }

    /**
     * 把一个带声调的读音字符串展开成音节段组合（笛卡尔积）。
     */
    @SuppressWarnings("unchecked")
    private static List<ExpendedChar> asExpendedChars(String string) {
        List<int[]>[] codepoints = new List[3];
        int skip = 2;
        int tone = -1;
        char[] chars = string.toCharArray();
        if (chars.length >= 2 && chars[0] == 's' && chars[1] == 'h') {
            codepoints[0] = expendInitials("sh");
        } else if (chars.length >= 2 && chars[0] == 'c' && chars[1] == 'h') {
            codepoints[0] = expendInitials("ch");
        } else if (chars.length >= 2 && chars[0] == 'z' && chars[1] == 'h') {
            codepoints[0] = expendInitials("zh");
        } else {
            skip = 1;
            ToneEntry toneEntry = TONE_MAP.get(chars[0]);
            if (toneEntry == null) {
                codepoints[0] = expendInitials(String.valueOf(chars[0]));
            } else {
                codepoints[0] = expendInitials(String.valueOf(toneEntry.base));
                tone = toneEntry.tone;
            }
        }
        StringBuilder builder = new StringBuilder();
        for (int i = skip; i < chars.length; i++) {
            char c = chars[i];
            if (c == 'ü') {
                builder.append('v');
            } else {
                ToneEntry toneEntry = TONE_MAP.get(c);
                if (toneEntry == null) {
                    builder.append(c);
                } else {
                    builder.append(toneEntry.base);
                    tone = toneEntry.tone;
                }
            }
        }
        int length = 2;
        if (builder.isEmpty()) {
            // 仅声母（如单字读音），无声调时只有一段
            length = 1;
        } else {
            codepoints[1] = expendFinals(builder.toString());
        }
        if (tone != -1) {
            codepoints[length] = List.of(new int[]{'0' + tone});
            length++;
        }
        int combinations = 1;
        for (int i = 0; i < length; i++) {
            combinations *= codepoints[i].size();
        }
        List<ExpendedChar> results = new ArrayList<>(combinations);
        int[] current = new int[length];
        for (int i = 0; i < combinations; i++) {
            List<int[]> sequence = new ArrayList<>(length);
            for (int k = 0; k < length; k++) {
                sequence.add(codepoints[k].get(current[k]));
            }
            results.add(new ExpendedChar(sequence));
            for (int k = 0; k < length; k++) {
                if (current[k] + 1 < codepoints[k].size()) {
                    current[k]++;
                    break;
                } else {
                    current[k] = 0;
                }
            }
        }
        return results;
    }

    private static List<int[]> expendInitials(String string) {
        return expendSimple(string);
    }

    private static List<int[]> expendFinals(String string) {
        return expendSimple(string);
    }

    private static List<int[]> expendSimple(String string) {
        // 模糊音默认关闭（与 REI 默认一致），FUZZY_MAP 保留供后续启用
        return List.of(string.codePoints().toArray());
    }
}
