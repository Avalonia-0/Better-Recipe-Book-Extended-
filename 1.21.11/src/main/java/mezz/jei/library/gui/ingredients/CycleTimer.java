package mezz.jei.library.gui.ingredients;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * [BRBE fork] Like {@link CycleTicker}, pause-recipe-cycling is bound to ALT
 * (either side): while Alt is held the time-driven index freezes, otherwise it
 * keeps rotating.  Alt+wheel steps the displayed variant manually through
 * {@link #manualIndexOverride} (>= 0 overrides the automatic index).
 */
public class CycleTimer implements ICycler {
	private static final CycleTimer ZERO_OFFSET = new CycleTimer(0);
	private static final int MAX_INDEX = 100_000;
	/* the amount of time in ms to display one thing before cycling to the next one */
	private static final int CYCLE_TIME_MS = 1_000;

	/** [BRBE fork] Alt+wheel stepping: >= 0 overrides the automatic index
	 *  (set/cleared by RecipeViewerOverlay while Alt is held). */
	public static int manualIndexOverride = -1;

	public static CycleTimer create(int offset) {
		if (offset == 0) {
			return ZERO_OFFSET;
		}
		return new CycleTimer(offset);
	}

	public static CycleTimer createWithRandomOffset() {
		int cycleOffset = (int) (Math.random() * MAX_INDEX);
		return new CycleTimer(cycleOffset);
	}

	private final int cycleOffset;
	private int index;

	private CycleTimer(int cycleOffset) {
		this.cycleOffset = cycleOffset;
		long now = System.currentTimeMillis();
		this.index = calculateIndex(now, cycleOffset);
	}

	private static int calculateIndex(long now, int cycleOffset) {
		long index = ((now / CYCLE_TIME_MS) % MAX_INDEX) + cycleOffset;
		return Math.toIntExact(index);
	}

	@Override
	public <T> Optional<T> getCycled(List<@Nullable T> list) {
		if (list.isEmpty()) {
			return Optional.empty();
		}
		if (manualIndexOverride < 0 && !isPauseKeyDown()) {
			long now = System.currentTimeMillis();
			index = calculateIndex(now, cycleOffset);
		}
		int index = (manualIndexOverride >= 0 ? manualIndexOverride : this.index) % list.size();
		T value = list.get(index);
		return Optional.ofNullable(value);
	}

	/** [BRBE fork] Whether the pause key (either ALT) is held. */
	private static boolean isPauseKeyDown() {
		Minecraft mc = Minecraft.getInstance();
		if (mc == null || mc.getWindow() == null) {
			return false;
		}
		return InputConstants.isKeyDown(mc.getWindow(), InputConstants.KEY_LALT)
				|| InputConstants.isKeyDown(mc.getWindow(), InputConstants.KEY_RALT);
	}
}
