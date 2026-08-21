package com.alonie.brbe.search;

import java.util.List;

/**
 * 一个汉字读音展开成的一个音节多段表示（phonemes）。
 * <p>
 * 每段是一个拼音 codepoint 序列：如「木」→ {@code [['m'],['u'],['4']]}
 * （声母 / 韵母 / 声调数字段）。多音字的一个读音可能因模糊音有多种展开，
 * 但模糊音默认关闭，故通常一个读音对应一个 {@code ExpendedChar}。
 *
 * 移植自 REI {@code CharacterUnpackingInputMethod.ExpendedChar}（MIT）。
 */
public record ExpendedChar(List<int[]> phonemes) {
}
