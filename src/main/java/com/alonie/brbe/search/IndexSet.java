package com.alonie.brbe.search;

import java.util.function.IntPredicate;

/**
 * 与 REI 完全一致的 int 位图集合，表示「当前匹配游标相对起点的所有可能消耗量」。
 * <p>
 * 移植自 REI {@code IndexSet}（MIT）。
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
 */
class IndexSet {
    static final IndexSet ZERO = new IndexSet(0x1);
    static final IndexSet ONE = new IndexSet(0x2);
    static final IndexSet NONE = new IndexSet(0x0);

    int value = 0x0;

    IndexSet() {
    }

    IndexSet(IndexSet set) {
        value = set.value;
    }

    IndexSet(int value) {
        this.value = value;
    }

    void set(int index) {
        value |= 0x1 << index;
    }

    boolean get(int index) {
        return (value & (0x1 << index)) != 0;
    }

    void merge(IndexSet s) {
        value = value == 0x1 ? s.value : (value |= s.value);
    }

    boolean traverse(IntPredicate p) {
        int v = value;
        for (int i = 0; i < 7; i++) {
            if ((v & 0x1) == 0x1 && !p.test(i)) return false;
            else if (v == 0) return true;
            v >>= 1;
        }
        return true;
    }

    void foreach(java.util.function.IntConsumer c) {
        int v = value;
        for (int i = 0; i < 7; i++) {
            if ((v & 0x1) == 0x1) c.accept(i);
            else if (v == 0) return;
            v >>= 1;
        }
    }

    void offset(int i) {
        value <<= i;
    }

    boolean isEmpty() {
        return value == 0x0;
    }

    IndexSet copy() {
        return new IndexSet(value);
    }
}
