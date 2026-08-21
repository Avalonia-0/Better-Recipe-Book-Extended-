package com.alonie.brbe.util;

import java.util.Random;

/**
 * 翻页动画方向判定：向左（页号减小/绕回）还是向右（页号增大）。
 *
 * <p>循环滚动（scrollAround）开启时取绕行距离较短的方向，左右等距时完全随机；
 * 关闭时按目标页与当前页的大小关系定方向。</p>
 */
public final class PageFlipDirection {

    private static final Random RANDOM = new Random();

    private PageFlipDirection() {
    }

    /** @return true = 向左翻页（页号减小 / 绕回），false = 向右翻页（页号增大）。 */
    public static boolean backward(int from, int target, int totalPages, boolean scrollAround) {
        if (scrollAround && totalPages > 1) {
            int forward = (target - from + totalPages) % totalPages;
            int backward = (from - target + totalPages) % totalPages;
            if (backward < forward) return true;
            if (forward < backward) return false;
            return RANDOM.nextBoolean();
        }
        return target < from;
    }
}
