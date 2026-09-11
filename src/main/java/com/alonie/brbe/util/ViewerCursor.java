package com.alonie.brbe.util;

import com.mojang.blaze3d.platform.cursor.CursorType;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWImage;
import org.lwjgl.system.MemoryUtil;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * The system's closed-fist ("grabbing") cursor — the one the desktop shows
 * while dragging a native window title bar — for the BRBE query window and
 * pin drags.
 *
 * <p>Minecraft's standard cursor set (GLFW standard shapes) resolves on Linux
 * only to theme names such as {@code default / text / crosshair / pointer /
 * size_* / not_allowed} — none of them is {@code grabbing}, so a drag can
 * never show the fist through {@code CursorTypes}.  This helper mirrors the
 * compositor's own resolution instead:
 * <ol>
 *   <li>theme = XCURSOR_THEME → KDE {@code kdeglobals} {@code cursorTheme} →
 *       GTK {@code gtk-cursor-theme-name} → <b>Breeze</b> (KWin's default when
 *       unset — the desktop's actual drag cursor source);</li>
 *   <li>prefer the theme's <b>scalable SVG art</b> ({@code cursors_scalable/
 *       {grabbing|closedhand}/*.svg} — what KWin Wayland renders), rasterised
 *       at the desktop's cursor size via {@code rsvg-convert};</li>
 *   <li>fall back to the classic raster Xcursor {@code grabbing} file;</li>
 *   <li>the frame is wrapped in a {@link CursorType} via the private
 *       constructor (reflection — the class offers no image-based factory).</li>
 * </ol>
 *
 * <p>Any failure (non-Linux, theme missing, no renderer, parse error) returns
 * {@code null} and the callers fall back to {@code CursorTypes.RESIZE_ALL} —
 * the previous behaviour.  The cursor is loaded once and cached.
 */
public final class ViewerCursor {

    /** KDE theme names → icon directory names (Kwin's cursor themes). */
    private static final java.util.Map<String, String> DIR_ALIASES =
            java.util.Map.of(
                    "Breeze", "breeze_cursors",
                    "breeze", "breeze_cursors",
                    "Breeze Light", "Breeze_Light",
                    "Breeze-Light", "Breeze_Light",
                    "Default", "default");

    private static CursorType fistCursor;
    private static boolean attempted;

    /** The system's closed-fist cursor, or {@code null} when unavailable
     *  (callers fall back to the standard resize-all cursor). */
    public static synchronized CursorType fist() {
        if (!attempted) {
            attempted = true;
            try {
                fistCursor = loadSystemGrabbing();
            } catch (Throwable t) {
                fistCursor = null;
            }
        }
        return fistCursor;
    }

    private static CursorType loadSystemGrabbing() {
        String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
        if (!os.contains("linux")) return null;
        String theme = activeTheme();
        int size = desktopCursorSize() > 0 ? desktopCursorSize() : 32;
        // 1) The theme's scalable SVG art (the compositor's rendering source —
        //    e.g. Breeze's solid closed fist), rasterised at the desktop size.
        Image svg = rasterizeScalable(theme, size);
        if (svg != null) {
            long handle = createGlfwCursor(svg);
            if (handle != 0L) {
                CursorType t = wrap(handle);
                if (t != null) return t;
                GLFW.glfwDestroyCursor(handle);
            }
        }
        // 2) Classic raster Xcursor fallback.
        Path file = findGrabbing(theme);
        Image img = file == null ? null : decodeXcursor(file, size);
        if (img == null) return null;
        long handle = createGlfwCursor(img);
        if (handle == 0L) return null;
        return wrap(handle);
    }

    private static CursorType wrap(long handle) {
        try {
            Constructor<?> ctor = CursorType.class.getDeclaredConstructor(String.class, long.class);
            ctor.setAccessible(true);
            return (CursorType) ctor.newInstance("brbe_grabbing", handle);
        } catch (Throwable t) {
            return null;
        }
    }

    /** The desktop's active cursor theme name — XCURSOR_THEME, KDE
     *  {@code kdeglobals} {@code cursorTheme}, GTK {@code gtk-cursor-theme-name},
     *  or <b>Breeze</b> (the KWin default when nothing is set — and the only
     *  theme the desktop's own drag cursor can come from then). */
    private static String activeTheme() {
        String env = System.getenv("XCURSOR_THEME");
        if (env != null && !env.isBlank()) return env.trim();
        String[] keys = {"cursorTheme", "gtk-cursor-theme-name"};
        for (String[] cand : new String[][] {
                {System.getProperty("user.home", ""), ".config/kdeglobals"},
                {System.getProperty("user.home", ""), ".config/gtk-3.0/settings.ini"},
                {System.getProperty("user.home", ""), ".config/gtk-4.0/settings.ini"}}) {
            try {
                if (Files.isRegularFile(Path.of(cand[0], cand[1]))) {
                    String text = Files.readString(Path.of(cand[0], cand[1]));
                    for (String key : keys) {
                        String v = extractSetting(text, key);
                        if (v != null && !v.isBlank()) return v.trim();
                    }
                }
            } catch (IOException ignored) {
            }
        }
        return "Breeze";
    }

    /** The desktop's cursor render size (KDE {@code CursorSize} / GTK
     *  {@code gtk-cursor-theme-size}), or 0 when unknown. */
    private static int desktopCursorSize() {
        String[] keys = {"CursorSize", "gtk-cursor-theme-size"};
        for (String[] cand : new String[][] {
                {System.getProperty("user.home", ""), ".config/kdeglobals"},
                {System.getProperty("user.home", ""), ".config/gtk-3.0/settings.ini"},
                {System.getProperty("user.home", ""), ".config/gtk-4.0/settings.ini"}}) {
            try {
                if (Files.isRegularFile(Path.of(cand[0], cand[1]))) {
                    String text = Files.readString(Path.of(cand[0], cand[1]));
                    for (String key : keys) {
                        String v = extractSetting(text, key);
                        if (v != null) {
                            try {
                                int size = Integer.parseInt(v.trim());
                                if (size >= 16 && size <= 96) return size;
                            } catch (NumberFormatException ignored) {
                            }
                        }
                    }
                }
            } catch (IOException ignored) {
            }
        }
        return 0;
    }

    private static String extractSetting(String text, String key) {
        for (String line : text.split("\\R")) {
            int i = line.indexOf(key + "=");
            if (i >= 0) {
                return line.substring(i + key.length() + 1).split("[; ]")[0].replace("\"", "");
            }
        }
        return null;
    }

    /** Install dirs of cursor themes (env + standard XCURSOR search path). */
    private static List<Path> themeDirs() {
        List<Path> dirs = new ArrayList<>();
        String xcursorPath = System.getenv("XCURSOR_PATH");
        if (xcursorPath != null && !xcursorPath.isBlank()) {
            for (String d : xcursorPath.split(":")) {
                if (!d.isBlank()) dirs.add(Path.of(d));
            }
        }
        String home = System.getProperty("user.home", "");
        dirs.add(Path.of(home, ".local/share/icons"));
        dirs.add(Path.of(home, ".icons"));
        dirs.add(Path.of("/usr/share/icons"));
        dirs.add(Path.of("/usr/share/pixmaps"));
        return dirs;
    }

    /** Candidate theme NAMES to try, in order: the detected one (with its
     *  dir aliases), then the common desktops' defaults (Breeze for KDE,
     *  Adwaita for GNOME) — an all-failed detection must not lock a user of
     *  another desktop onto the wrong theme. */
    private static List<String> themeCandidates(String theme) {
        List<String> out = new ArrayList<>();
        if (theme != null && !theme.isBlank() && !"Breeze".equals(theme)) {
            out.add(theme);
            String alias = DIR_ALIASES.get(theme);
            if (alias != null) out.add(alias);
        }
        out.add("Breeze");
        out.add("breeze_cursors");
        out.add("Adwaita");
        return out;
    }

    /** Rasterise the theme's scalable closed-fist SVG art, or {@code null}
     *  when the theme has none (or {@code rsvg-convert} is unavailable). */
    private static Image rasterizeScalable(String theme, int size) {
        Path svg = findScalableSvg(theme);
        if (svg == null) return null;
        Path out;
        try {
            out = Files.createTempFile("brbe-cursor", ".png");
        } catch (IOException e) {
            return null;
        }
        try {
            int s = Math.max(24, size);
            Process p = new ProcessBuilder("rsvg-convert",
                    "-w", String.valueOf(s), "-h", String.valueOf(s),
                    "-b", "none", svg.toString(), "-o", out.toString())
                    .redirectErrorStream(true)
                    .start();
            if (!p.waitFor(10, TimeUnit.SECONDS) || p.exitValue() != 0) {
                p.destroyForcibly();
                return null;
            }
            BufferedImage bi = ImageIO.read(out.toFile());
            if (bi == null) return null;
            int w = bi.getWidth();
            int h = bi.getHeight();
            int[] argb = new int[w * h];
            bi.getRGB(0, 0, w, h, argb, 0, w);
            // KDE metadata hotspot: nominal 24 → scale with the render size.
            int hot = Math.max(1, Math.min(w - 1, Math.round(16f * s / 24f)));
            return new Image(w, h, hot, hot, argb);
        } catch (IOException | InterruptedException e) {
            return null;
        } finally {
            try {
                Files.deleteIfExists(out);
            } catch (IOException ignored) {
            }
        }
    }

    /** Locate the theme's scalable closed-fist SVG, or {@code null}. */
    private static Path findScalableSvg(String theme) {
        for (String dirName : themeCandidates(theme)) {
            for (Path dir : themeDirs()) {
                Path base = dir.resolve(dirName).resolve("cursors_scalable");
                if (!Files.isDirectory(base)) continue;
                for (String artifact : new String[] {"grabbing", "closedhand"}) {
                    Path cand = base.resolve(artifact);
                    if (Files.isDirectory(cand)) {
                        // KDE layout: a dir holding <name>.svg / dnd-move.svg.
                        try (Stream<Path> files = Files.list(cand)) {
                            List<Path> svgs = files
                                    .filter(f -> f.getFileName().toString().endsWith(".svg"))
                                    .sorted(java.util.Comparator.comparing(
                                            f -> !f.getFileName().toString().equals("dnd-move.svg")))
                                    .toList();
                            if (!svgs.isEmpty()) return svgs.get(0);
                        } catch (IOException ignored) {
                        }
                    } else if (Files.isRegularFile(cand) && cand.toString().endsWith(".svg")) {
                        return cand;
                    }
                }
                // Direct file variant: cursors_scalable/grabbing.svg.
                Path direct = base.resolve("grabbing.svg");
                if (Files.isRegularFile(direct)) return direct;
            }
        }
        return null;
    }

    /** Locate a raster {@code grabbing} Xcursor file: the active theme across
     *  ALL install dirs first, then (theme missing) any installed theme in
     *  the standard XCURSOR search path (user dirs before system dirs). */
    private static Path findGrabbing(String theme) {
        List<Path> dirs = themeDirs();
        // Pass 1: the active theme (if known) in every install dir.
        for (String dirName : themeCandidates(theme)) {
            for (Path dir : dirs) {
                Path themed = dir.resolve(dirName).resolve("cursors").resolve("grabbing");
                if (Files.isRegularFile(themed)) return themed;
            }
        }
        // Pass 2: theme-agnostic scan.
        for (Path dir : dirs) {
            try (Stream<Path> themes = Files.list(dir)) {
                List<Path> hits = themes
                        .filter(Files::isDirectory)
                        .map(t -> t.resolve("cursors").resolve("grabbing"))
                        .filter(Files::isRegularFile)
                        .toList();
                if (!hits.isEmpty()) return hits.get(0);
            } catch (IOException ignored) {
            }
        }
        return null;
    }

    private record Image(int width, int height, int xhot, int yhot, int[] argb) {
    }

    /** Parse an Xcursor file (TOC + image chunks) and return the frame that
     *  matches the desktop cursor size when present, else 32px, else the
     *  largest ≤48px frame. */
    private static Image decodeXcursor(Path file, int preferredSize) {
        byte[] data;
        try {
            data = Files.readAllBytes(file);
        } catch (IOException e) {
            return null;
        }
        if (data.length < 16 || data[0] != 'X' || data[1] != 'c' || data[2] != 'u' || data[3] != 'r') {
            return null;
        }
        ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        int ntypes = buf.getInt(12);
        Image best = null;
        long bestRank = -1;
        for (int i = 0; i < ntypes; i++) {
            int pos = 16 + i * 12;
            if (pos + 12 > data.length) break;
            int type = buf.getInt(pos);
            long size = buf.getInt(pos + 4) & 0xFFFFFFFFL;
            int off = buf.getInt(pos + 8);
            if (type != 0xFFFD0002 || off + 24 > data.length) continue;
            int w = buf.getInt(off + 16);
            int h = buf.getInt(off + 20);
            int xhot = buf.getInt(off + 24);
            int yhot = buf.getInt(off + 28);
            int pxStart = off + 36;
            if (w <= 0 || h <= 0 || pxStart + w * h * 4 > data.length) continue;
            if (size > 48) continue;
            // Rank: exact desktop size > 32px > largest.
            long rank;
            if (preferredSize > 0 && size == preferredSize) {
                rank = 1000;
            } else if (size == 32) {
                rank = 500;
            } else {
                rank = size;
            }
            if (best != null && rank <= bestRank) continue;
            bestRank = rank;
            int[] argb = new int[w * h];
            int n = 0;
            for (int p = pxStart; p < pxStart + w * h * 4; p += 4) {
                int a = data[p] & 0xFF;
                int r = data[p + 1] & 0xFF;
                int g = data[p + 2] & 0xFF;
                int b = data[p + 3] & 0xFF;
                argb[n++] = (a << 24) | (r << 16) | (g << 8) | b;
            }
            best = new Image(w, h,
                    Math.max(0, Math.min(xhot, w - 1)), Math.max(0, Math.min(yhot, h - 1)),
                    argb);
        }
        return best;
    }

    private static long createGlfwCursor(Image img) {
        ByteBuffer pixels = MemoryUtil.memAlloc(img.width() * img.height() * 4);
        try {
            for (int argb : img.argb()) {
                pixels.put((byte) ((argb >> 16) & 0xFF));  // R
                pixels.put((byte) ((argb >> 8) & 0xFF));   // G
                pixels.put((byte) (argb & 0xFF));          // B
                pixels.put((byte) ((argb >> 24) & 0xFF));  // A
            }
            pixels.flip();
            GLFWImage image = GLFWImage.malloc();
            try {
                image.set(img.width(), img.height(), pixels);
                return GLFW.glfwCreateCursor(image, img.xhot(), img.yhot());
            } finally {
                image.free();
            }
        } finally {
            MemoryUtil.memFree(pixels);
        }
    }

    private ViewerCursor() {
    }
}
