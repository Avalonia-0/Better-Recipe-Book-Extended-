package mezz.jei.common.util;


public record ImmutableSize2i( int width,  int height) {
	public static final ImmutableSize2i EMPTY = new ImmutableSize2i(0, 0);

	public int getArea() {
		return width * height;
	}
}
