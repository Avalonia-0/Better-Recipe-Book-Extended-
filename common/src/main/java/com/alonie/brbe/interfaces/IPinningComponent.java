package com.alonie.brbe.interfaces;

import com.google.common.collect.Lists;
import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.generic.pins.Pinnable;

import java.util.List;

public interface IPinningComponent<T extends Pinnable> {
    default void brbe$sortByPinsInPlace(List<T> results) {
        List<T> tempResults = Lists.newArrayList(results);

        if (true) {
            for (T result : tempResults) {
                // 泛型接口无稳定 isFullyPinned 重载（T 未绑定 RecipeCollection）：
                // 保持原有 has 语义（自研书集合尚未接入 Stage 6 剥离）。
                if (BetterRecipeBook.pinnedRecipeManager.has(result)) {
                    results.remove(result);
                    results.add(0, result);
                }
            }
        }
    }
}
