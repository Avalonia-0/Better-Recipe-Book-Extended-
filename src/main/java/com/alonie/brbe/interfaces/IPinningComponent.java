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
                if (BetterRecipeBook.pinnedRecipeManager.has(result)) {
                    results.remove(result);
                    results.add(0, result);
                }
            }
        }
    }
}
