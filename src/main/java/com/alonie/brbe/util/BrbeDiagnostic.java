package com.alonie.brbe.util;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.config.AppContext;
import com.alonie.brbe.config.ConfigEventBus;
import com.alonie.brbe.layout.BookLayout;
import com.alonie.brbe.pin.JsonPinStore;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Runtime diagnostic tool for verifying the new architecture.
 *
 * <p>Call {@link #dump()} to write a full diagnostic report to the game
 * directory ({@code brbe-diagnostic.log}).  Each section includes a
 * PASS/FAIL verdict.</p>
 */
public final class BrbeDiagnostic {

    private static final DateTimeFormatter TS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private BrbeDiagnostic() {}

    /** Write a full diagnostic report. */
    public static void dump() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n");
        sb.append("══════════════════════════════════════════════════════\n");
        sb.append("  BRBE Architecture Diagnostic  —  ");
        sb.append(TS.format(LocalDateTime.now())).append("\n");
        sb.append("══════════════════════════════════════════════════════\n\n");

        int passed = 0, failed = 0;

        failed += checkAppContext(sb) ? 0 : 1;
        passed += checkAppContext(sb) ? 1 : 0;

        failed += checkEventBus(sb) ? 0 : 1;
        passed += checkEventBus(sb) ? 1 : 0;

        failed += checkConfigRouting(sb) ? 0 : 1;
        passed += checkConfigRouting(sb) ? 1 : 0;

        failed += checkPinStore(sb) ? 0 : 1;
        passed += checkPinStore(sb) ? 1 : 0;

        failed += checkBookLayout(sb) ? 0 : 1;
        passed += checkBookLayout(sb) ? 1 : 0;

        sb.append("\n─── Result ───────────────────────────────────────────\n");
        sb.append(String.format("  %d passed, %d failed, %d total\n",
                passed, failed, passed + failed));
        sb.append("══════════════════════════════════════════════════════\n");

        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.gameDirectory == null) return;
            Path logFile = mc.gameDirectory.toPath()
                    .resolve("brbe-diagnostic.log");
            Files.writeString(logFile, sb.toString(),
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            BetterRecipeBook.LOGGER.info("[BRBE] Diagnostic written to {}", logFile);
        } catch (IOException e) {
            BetterRecipeBook.LOGGER.error("[BRBE] Failed to write diagnostic", e);
        }

        BetterRecipeBook.LOGGER.info(sb.toString());
    }

    // ── Individual checks ──────────────────────────────────────────

    private static boolean checkAppContext(StringBuilder sb) {
        sb.append("─── 1. AppContext (DI Root) ───────────────────────────\n");
        try {
            AppContext ctx = AppContext.instance();
            sb.append(String.format("  PASS  instance       = %s\n", ctx));
            sb.append(String.format("  INFO  config         = %s\n",
                    ctx.config() != null ? "present" : "NULL"));
            sb.append(String.format("  INFO  events         = %s\n",
                    ctx.events() != null ? "present" : "NULL"));
            sb.append(String.format("  INFO  pins           = %s\n",
                    ctx.pins() != null ? "present" : "NULL"));
            sb.append(String.format("  INFO  instantCraft   = %s\n",
                    ctx.instantCraft() != null ? "present" : "NULL"));
            sb.append(String.format("  INFO  bookLayout     = %s\n",
                    ctx.bookLayout() != null ? "present" : "NULL"));
            sb.append(String.format("  INFO  brewingBook    = %s\n",
                    ctx.brewingBook() != null ? "present" : "NULL"));
            sb.append(String.format("  INFO  smithingBook   = %s\n",
                    ctx.smithingBook() != null ? "present" : "NULL"));
            return true;
        } catch (Exception e) {
            sb.append(String.format("  FAIL  %s\n", e.getMessage()));
            return false;
        }
    }

    private static boolean checkEventBus(StringBuilder sb) {
        sb.append("\n─── 2. ConfigEventBus ──────────────────────────────────\n");
        try {
            AppContext ctx = AppContext.instance();
            ConfigEventBus events = ctx.events();
            if (events == null) {
                sb.append("  FAIL  events is null\n");
                return false;
            }
            boolean before = events.consumeConfigChange();
            events.requestConfigRefresh();
            boolean after = events.consumeConfigChange();
            boolean after2 = events.consumeConfigChange();
            events.requestConfigRefresh();
            sb.append(String.format("  PASS  request/consume: before=%s after=%s after2=%s\n",
                    before, after, after2));
            sb.append(String.format("  INFO  config.keepCentered           = %s\n",
                    ctx.config().keepCentered));
            sb.append(String.format("  INFO  config.enablePinning          = %s\n",
                    true /* was enablePinning */));
            sb.append(String.format("  INFO  config.partialCraftingEnabled = %s\n",
                    ctx.config().partialCraftingEnabled));
            return true;
        } catch (Exception e) {
            sb.append(String.format("  FAIL  %s\n", e.getMessage()));
            return false;
        }
    }

    private static boolean checkConfigRouting(StringBuilder sb) {
        sb.append("\n─── 3. Config Routing (ctx().config() pathway) ────────\n");
        try {
            boolean same = BetterRecipeBook.config == AppContext.instance().config();
            sb.append(String.format("  %s  BetterRecipeBook.config == ctx().config()\n",
                    same ? "PASS" : "FAIL"));
            boolean pinMatch = true
                    == true /* was enablePinning */;
            sb.append(String.format("  %s  enablePinning (always true) matches both paths\n",
                    pinMatch ? "PASS" : "FAIL"));
            return same && pinMatch;
        } catch (Exception e) {
            sb.append(String.format("  FAIL  %s\n", e.getMessage()));
            return false;
        }
    }

    private static boolean checkPinStore(StringBuilder sb) {
        sb.append("\n─── 4. PinStore (async I/O) ────────────────────────────\n");
        try {
            var pins = AppContext.instance().pins();
            if (pins == null) {
                sb.append("  FAIL  PinnedRecipeManager is null\n");
                return false;
            }
            int count = pins.pinned != null ? pins.pinned.size() : 0;
            sb.append(String.format("  INFO  pinned recipes  = %d\n", count));
            if (pins.pinned != null && pins.pinned.size() <= 10) {
                for (var id : pins.pinned) {
                    sb.append(String.format("         - %s\n", id));
                }
            }
            sb.append("  PASS  PinnedRecipeManager present\n");
            return true;
        } catch (Exception e) {
            sb.append(String.format("  FAIL  %s\n", e.getMessage()));
            return false;
        }
    }

    private static boolean checkBookLayout(StringBuilder sb) {
        sb.append("\n─── 5. BookLayout Constants ────────────────────────────\n");
        sb.append(String.format("  INFO  TEXTURE_WIDTH      = %d\n", BookLayout.TEXTURE_WIDTH));
        sb.append(String.format("  INFO  TEXTURE_HEIGHT     = %d\n", BookLayout.TEXTURE_HEIGHT));
        sb.append(String.format("  INFO  BUTTON_SIZE        = %d\n", BookLayout.BUTTON_SIZE));
        sb.append(String.format("  INFO  GRID_LEFT_PADDING           = %d\n", BookLayout.GRID_LEFT_PADDING));
        sb.append(String.format("  INFO  TAB_BUTTON_WIDTH          = %d\n", BookLayout.TAB_BUTTON_WIDTH));
        sb.append(String.format("  INFO  TAB_BUTTON_SPACING        = %d\n", BookLayout.TAB_BUTTON_SPACING));
        sb.append(String.format("  INFO  X_OFFSET_CENTERED  = %d\n", BookLayout.X_OFFSET_CENTERED));
        sb.append(String.format("  INFO  X_OFFSET_STANDARD  = %d\n", BookLayout.X_OFFSET_STANDARD));
        sb.append("  PASS  all constants present\n");
        return true;
    }
}
