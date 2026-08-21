package com.alonie.brbe.search;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 打包的拼音字表（Unihan kMandarin 裁剪版）的懒加载容器。
 * <p>
 * 数据文件 {@code assets/zzzbrbe/search/pinyin.txt} 由
 * {@code tools/generate_pinyin_data.py} 生成，行格式：
 * <pre>{@code 汉字=读音1 读音2}</pre>
 * 保留原始声调符号（如 {@code 木=mù}），多音字以空格分隔（如 {@code 行=xíng háng}）。
 * 通过 classloader 直接读取，不经过 MC 资源系统，避免资源包加载时机问题。
 */
public final class PinyinData {
    private static final String RESOURCE = "/assets/zzzbrbe/search/pinyin.txt";

    private static volatile Map<Integer, List<String>> data;
    private static volatile boolean loaded;

    private PinyinData() {
    }

    /**
     * 返回只读的 {@code codePoint → 读音字符串列表} 映射，懒加载。
     */
    public static Map<Integer, List<String>> map() {
        if (!loaded) {
            synchronized (PinyinData.class) {
                if (!loaded) {
                    data = load();
                    loaded = true;
                }
            }
        }
        return data;
    }

    private static Map<Integer, List<String>> load() {
        Map<Integer, List<String>> map = new HashMap<>();
        try (InputStream in = PinyinData.class.getResourceAsStream(RESOURCE)) {
            if (in == null) return map;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isEmpty() || line.charAt(0) == '#') continue;
                    int eq = line.indexOf('=');
                    if (eq != 1 || line.length() <= 2) continue;
                    int codePoint = line.codePointAt(0);
                    if (eq == 1) { // 单 BMP 汉字 + 读音
                        map.put(codePoint, List.of(line.substring(2).split(" ")));
                    }
                }
            }
        } catch (Exception e) {
            // 字表加载失败则拼音搜索静默降级为普通 contains
        }
        return map;
    }
}
