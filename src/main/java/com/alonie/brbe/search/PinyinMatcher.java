package com.alonie.brbe.search;

import java.util.List;

/**
 * 拼音匹配器：对源串（物品名 codepoints）逐字符展开为拼音音节，与查询串做
 * DP 回溯匹配。
 * <p>
 * 移植自 REI {@code InputMethodMatcher}（MIT）。
 * <pre>
 * MIT License
 * Copyright (c) 2019 Juntong Liu
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * </pre>
 * <p>
 * 核心机制：每个源字符展开成若干「声母/韵母/声调数字」段，查询串按段前缀匹配
 * 逐段消耗。{@code matchM} 的 {@code partial && start + size == source.length()}
 * 使落在查询串末尾的声调数字段可跳过——即无声调输入 {@code mutou} 也能匹配
 * 带声调数据展开的 {@code mù tóu}。
 */
public final class PinyinMatcher {
    private PinyinMatcher() {
    }

    /**
     * 查询串是否为源串的「拼音化子串」（partial 模式）。
     */
    public static boolean contains(int[] source, int[] query) {
        if (source.length == 0) return false;
        for (int i = 0; i < source.length; i++) {
            if (check(source, i, query, 0, true)) return true;
        }
        return false;
    }

    /**
     * 查询串是否完整匹配整个源串（整串模式）。
     */
    public static boolean matches(int[] source, int[] query) {
        if (source.length == 0) return query.length == 0;
        return check(source, 0, query, 0, false);
    }

    private static IndexSet match(int self, int[] query, int start, boolean partial) {
        List<ExpendedChar> expanded = PinyinInputMethod.expendSourceChar(self);
        IndexSet ret = (start < query.length && query[start] == self ? IndexSet.ONE : IndexSet.NONE).copy();
        for (ExpendedChar phonemes : expanded) {
            ret.merge(match(phonemes, query, start, partial));
        }
        return ret;
    }

    private static IndexSet match(ExpendedChar phonemes, int[] query, int start, boolean partial) {
        IndexSet active = IndexSet.ZERO;
        IndexSet ret = new IndexSet();
        for (int[] phoneme : phonemes.phonemes()) {
            active = matchPhoneme(phoneme, query, active, start, partial);
            if (active.isEmpty()) return ret;
            ret.merge(active);
        }
        return ret;
    }

    private static IndexSet matchPhoneme(int[] str, int[] source, IndexSet idx, int start, boolean partial) {
        if (str.length == 0) return new IndexSet(idx);
        IndexSet ret = new IndexSet();
        idx.foreach(i -> {
            IndexSet is = matchM(str, source, start + i, partial);
            is.offset(i);
            ret.merge(is);
        });
        return ret;
    }

    private static IndexSet matchM(int[] str, int[] source, int start, boolean partial) {
        IndexSet ret = new IndexSet();
        if (str.length == 0) return ret;
        int size = strCmp(source, str, start);
        if (partial && start + size == source.length) ret.set(size);
        else if (size == str.length) ret.set(size);
        return ret;
    }

    private static int strCmp(int[] a, int[] b, int aStart) {
        int len = Math.min(a.length - aStart, b.length);
        for (int i = 0; i < len; i++) {
            if (a[i + aStart] != b[i]) return i;
        }
        return len;
    }

    private static boolean check(int[] source, int start1, int[] query, int start2, boolean partial) {
        if (start2 == query.length) return partial || start1 == source.length;

        int ch = source[start1];
        IndexSet s = match(ch, query, start2, partial);

        if (start1 == source.length - 1) {
            int i = query.length - start2;
            return s.get(i);
        } else {
            return !s.traverse(i -> !check(source, start1 + 1, query, start2 + i, partial));
        }
    }
}
