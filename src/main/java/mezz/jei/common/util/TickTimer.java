// Forked from JustEnoughItems (https://github.com/mezz/JustEnoughItems), MIT License.
// Copyright (c) 2014-2015 mezz. See jei-plugins/LICENSE.txt for the full license text.
package mezz.jei.common.util;

import mezz.jei.api.gui.ITickTimer;

/**
 * A wall-clock tick timer that cycles between 0 and {@code maxValue} over
 * {@code ticksPerCycle * 50}ms, driving animated drawables.
 */
public class TickTimer implements ITickTimer {
	private final int msPerCycle;
	private final int maxValue;
	private final boolean countDown;
	private final long startTime;

	public TickTimer(int ticksPerCycle, int maxValue, boolean countDown) {
		if (ticksPerCycle <= 0) {
			throw new IllegalArgumentException("Must have at least 1 tick per cycle.");
		}
		if (maxValue <= 0) {
			throw new IllegalArgumentException("max value must be greater than 0");
		}
		this.msPerCycle = ticksPerCycle * 50;
		this.maxValue = maxValue;
		this.countDown = countDown;
		this.startTime = System.currentTimeMillis();
	}

	@Override
	public int getValue() {
		long currentTime = System.currentTimeMillis();
		return getValue(startTime, currentTime, maxValue, msPerCycle, countDown);
	}

	@Override
	public int getMaxValue() {
		return maxValue;
	}

	public static int getValue(long startTime, long currentTime, int maxValue, int msPerCycle, boolean countDown) {
		long msPassed = (currentTime - startTime) % msPerCycle;
		int value = (int) Math.floorDiv(msPassed * (maxValue + 1), msPerCycle);
		if (countDown) {
			return maxValue - value;
		} else {
			return value;
		}
	}
}
