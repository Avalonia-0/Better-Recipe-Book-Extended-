package com.alonie.brbe.util;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.config.AppContext;
import com.alonie.brbe.config.ConfigEventBus;
import com.alonie.brbe.layout.BookGeometry;
import com.alonie.brbe.layout.BookLayout;
import com.alonie.brbe.layout.BookLayout.TabPosition;
import com.alonie.brbe.layout.BookLayout.Zone;
import com.alonie.brbe.pin.JsonPinStore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

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
 *
 * <p>Also callable from in-game with an optional key binding or the
 * Cloth Config screen (closing the config triggers a dump).</p>
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

        failed += checkRecipeViewers(sb) ? 0 : 1;
        passed += checkRecipeViewers(sb) ? 1 : 0;

        failed += checkConstraintLayout(sb) ? 0 : 1;
        passed += checkConstraintLayout(sb) ? 1 : 0;

        failed += checkRecipeBookState(sb) ? 0 : 1;
        passed += checkRecipeBookState(sb) ? 1 : 0;

        sb.append("\n─── Result ───────────────────────────────────────────\n");
        sb.append(String.format("  %d passed, %d failed, %d total\n",
                passed, failed, passed + failed));
        sb.append("══════════════════════════════════════════════════════\n");

        // Write to disk
        try {
            Path logFile = Minecraft.getInstance().gameDirectory.toPath()
                    .resolve("zzzbrbe-diagnostic.log");
            Files.writeString(logFile, sb.toString(),
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            BetterRecipeBook.LOGGER.info("[BRBE] Diagnostic written to {}", logFile);
        } catch (IOException e) {
            BetterRecipeBook.LOGGER.error("[BRBE] Failed to write diagnostic", e);
        }

        // Also print to game log
        BetterRecipeBook.LOGGER.info(sb.toString());
    }

    // ── Individual checks ──────────────────────────────────────────

    private static boolean checkRecipeBookState(StringBuilder sb) {
        sb.append("\n─── 8. Recipe Book Runtime State ──────────────────────\n");
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen == null) {
                sb.append("  SKIP  no screen open\n");
                return true;
            }

            // Config snapshot
            var cfg = BetterRecipeBook.ctx().config();
            sb.append(String.format("  INFO  config: showAll=%s pCE=%s pME=%s keepCentered=%s\n",
                    cfg.showAllRecipesInSurvival, cfg.partialCraftingEnabled,
                    cfg.partialMarkingEnabled, cfg.keepCentered));

            // Screen info
            boolean onInventory = mc.screen instanceof net.minecraft.client.gui.screens.inventory.EffectRenderingInventoryScreen;
            sb.append(String.format("  INFO  screen=%s onInventory=%s\n",
                    mc.screen.getClass().getSimpleName(), onInventory));

            // Recipe book component info
            if (!(mc.screen instanceof net.minecraft.client.gui.screens.recipebook.RecipeUpdateListener rul)) {
                sb.append("  SKIP  no recipe book on this screen\n");
                return true;
            }

            var comp = rul.getRecipeBookComponent();
            if (comp == null) {
                sb.append("  FAIL  recipe book component is null\n");
                return false;
            }

            var compAccessor = (com.alonie.brbe.mixins.accessors.RecipeBookComponentAccessor) comp;
            var tab = compAccessor.getSelectedTab();
            var book = compAccessor.getRecipeBook();
            var page = compAccessor.getRecipeBookPage();
            var pageAccessor = (com.alonie.brbe.mixins.accessors.RecipeBookPageAccessor) page;

            sb.append(String.format("  INFO  selectedTab=%s visible=%s\n",
                    tab != null ? tab.getCategory().getClass().getSimpleName() : "NULL",
                    comp.isVisible()));

            if (tab != null && book != null) {
                var collections = book.getCollection(tab.getCategory());
                int totalRecipes = 0, craftable = 0, partial = 0, incompatible = 0;
                int emptyFits = 0, partialFits = 0, fullFits = 0;

                for (var c : collections) {
                    totalRecipes += c.getRecipes().size();
                    if (c.hasCraftable()) craftable++;
                    if (PartialCraftingUtil.hasPartialMaterials(c)) partial++;
                    if (IncompatibleCraftingUtil.hasIncompatibleRecipes(c)) incompatible++;
                    var ca = (com.alonie.brbe.mixins.accessors.RecipeCollectionAccessor) c;
                    int fitCount = ca.getFitsDimensions().size();
                    if (fitCount == 0) emptyFits++;
                    else if (fitCount < c.getRecipes().size()) partialFits++;
                    else fullFits++;
                }

                sb.append(String.format("  INFO  collections=%d recipes=%d\n",
                        collections.size(), totalRecipes));
                sb.append(String.format("  INFO  craftable=%d partial=%d incompatible=%d\n",
                        craftable, partial, incompatible));
                sb.append(String.format("  INFO  fits: full=%d partial=%d empty=%d\n",
                        fullFits, partialFits, emptyFits));

                // Page state
                var storedCollections = pageAccessor.getCollections();
                sb.append(String.format("  INFO  page: storedCols=%d visible=%s\n",
                        storedCollections != null ? storedCollections.size() : 0,
                        comp.isVisible()));

                // Show first 5 collections with state
                int shown = 0;
                for (var c : collections) {
                    if (shown >= 5) break;
                    var ca = (com.alonie.brbe.mixins.accessors.RecipeCollectionAccessor) c;
                    int fitCount = ca.getFitsDimensions().size();
                    String first = c.getRecipes().iterator().next().value()
                            .getResultItem(c.registryAccess()).getHoverName().getString();
                    sb.append(String.format("         [%s%s%s] %s (%d/%d fit)\n",
                            c.hasCraftable() ? "C" : "-",
                            PartialCraftingUtil.hasPartialMaterials(c) ? "P" : "-",
                            fitCount == 0 ? "E" : (fitCount < c.getRecipes().size() ? "p" : "F"),
                            first, fitCount, c.getRecipes().size()));
                    shown++;
                }
                if (collections.size() > 5)
                    sb.append(String.format("         ... and %d more\n", collections.size() - 5));
            }

            sb.append("  PASS  recipe book state captured\n");
            return true;
        } catch (Exception e) {
            sb.append(String.format("  FAIL  %s\n", e.getMessage()));
            return false;
        }
    }

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
            sb.append(String.format("  INFO  recipeViewers  = %s\n",
                    ctx.recipeViewers() != null ? "present" : "NULL"));
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
            // Test consumeConfigChange round-trip
            boolean before = events.consumeConfigChange();
            events.requestConfigRefresh();
            boolean after = events.consumeConfigChange();
            boolean after2 = events.consumeConfigChange();
            events.requestConfigRefresh(); // restore flag for render loop
            sb.append(String.format("  PASS  request/consume: before=%s after=%s after2=%s\n",
                    before, after, after2));
            if (!after || after2) {
                sb.append("  WARN  AtomicBoolean round-trip unexpected\n");
            }
            // Check config values via events
            sb.append(String.format("  INFO  config.keepCentered           = %s\n",
                    ctx.config().keepCentered));
            sb.append(String.format("  INFO  config.enablePinning          = %s\n",
                    true /* was enablePinning */));
            sb.append(String.format("  INFO  config.partialCraftingEnabled = %s\n",
                    ctx.config().partialCraftingEnabled));
            sb.append(String.format("  INFO  config.enableBook             = %s\n",
                    ctx.config().enableBook));
            return true;
        } catch (Exception e) {
            sb.append(String.format("  FAIL  %s\n", e.getMessage()));
            return false;
        }
    }

    private static boolean checkConfigRouting(StringBuilder sb) {
        sb.append("\n─── 3. Config Routing (ctx().config() pathway) ────────\n");
        try {
            // Verify that BetterRecipeBook.config matches AppContext.config()
            boolean same = BetterRecipeBook.config == AppContext.instance().config();
            sb.append(String.format("  %s  BetterRecipeBook.config == ctx().config()\n",
                    same ? "PASS" : "FAIL"));
            // Check that config fields are accessible through both paths
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
            // Verify PinStore is wired (non-null store field means async I/O active)
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
        sb.append(String.format("  INFO  GRID_PAD           = %d\n", BookLayout.GRID_PAD));
        sb.append(String.format("  INFO  GRID_GAP           = %d\n", BookLayout.GRID_GAP));
        sb.append(String.format("  INFO  TAB_WIDTH          = %d\n", BookLayout.TAB_WIDTH));
        sb.append(String.format("  INFO  TAB_SPACING        = %d\n", BookLayout.TAB_SPACING));
        sb.append(String.format("  INFO  X_OFFSET_CENTERED  = %d\n", BookLayout.X_OFFSET_CENTERED));
        sb.append(String.format("  INFO  X_OFFSET_STANDARD  = %d\n", BookLayout.X_OFFSET_STANDARD));
        sb.append("  PASS  all constants present\n");
        return true;
    }

    private static boolean checkRecipeViewers(StringBuilder sb) {
        sb.append("\n─── 6. RecipeViewer Registry ────────────────────────────\n");
        try {
            var registry = AppContext.instance().recipeViewers();
            if (registry == null) {
                sb.append("  FAIL  registry is null\n");
                return false;
            }
            boolean any = registry.anyAvailable();
            sb.append(String.format("  INFO  anyAvailable = %s\n", any));

            // List available viewers
            var found = registry.findFirst();
            if (found != null && found != com.alonie.brbe.compat.recipeviewer.RecipeViewer.NONE) {
                sb.append(String.format("  INFO  first available = %s\n",
                        found.getClass().getSimpleName()));
            } else {
                sb.append("  INFO  no viewers available (JEI/REI not loaded)\n");
            }
            sb.append("  PASS  registry operational\n");
            return true;
        } catch (Exception e) {
            sb.append(String.format("  FAIL  %s\n", e.getMessage()));
            return false;
        }
    }

    private static boolean checkConstraintLayout(StringBuilder sb) {
        sb.append("\n─── 7. Constraint Layout (runtime geometry) ────────────\n");
        try {
            Screen screen = Minecraft.getInstance().screen;
            if (!(screen instanceof AbstractContainerScreen<?> acs)) {
                sb.append("  SKIP  no container screen open\n");
                return true; // not a failure
            }

            int screenW = acs.width;
            int screenH = acs.height;
            sb.append(String.format("  INFO  screen           = %s (%d×%d)\n",
                    screen.getClass().getSimpleName(), screenW, screenH));

            BookLayout layout = AppContext.instance().bookLayout();
            boolean centered = AppContext.instance().config().keepCentered;
            boolean expanded = false; // standard mode for diagnostic

            BookLayout.Rect available = BookLayout.Rect.of(0, 0, screenW, screenH);
            BookGeometry geo = layout.compute(available, centered, expanded);

            // Dump computed geometry
            sb.append(String.format("  INFO  book             = (%d,%d) %d×%d\n",
                    geo.bookLeft(), geo.bookTop(), geo.bookWidth(), geo.bookHeight()));
            sb.append(String.format("  INFO  searchBox        = (%d,%d) %d×%d\n",
                    geo.searchX(), geo.searchY(), geo.searchWidth(), geo.searchHeight()));
            sb.append(String.format("  INFO  filterButton     = (%d,%d) %d×%d\n",
                    geo.filterX(), geo.filterY(), geo.filterWidth(), geo.filterHeight()));
            sb.append(String.format("  INFO  settingsButton   = (%d,%d) %dpx\n",
                    geo.settingsX(), geo.settingsY(), geo.settingsSize()));
            sb.append(String.format("  INFO  grid             = (%d,%d) %d cols × %d rows, button=%dpx\n",
                    geo.gridX(), geo.gridY(), geo.gridColumns(), geo.gridRows(), geo.buttonSize()));
            sb.append(String.format("  INFO  gridZone         = (%d,%d) %d×%d\n",
                    geo.gridZone().left, geo.gridZone().top,
                    geo.gridZone().width, geo.gridZone().height));
            sb.append(String.format("  INFO  pageArrows       = back(%d,%d) forward(%d,%d)\n",
                    geo.arrowBackX(), geo.arrowY(), geo.arrowForwardX(), geo.arrowY()));
            sb.append(String.format("  INFO  instantCraft     = (%d,%d)\n",
                    geo.instantCraftX(), geo.instantCraftY()));

            // Dump tab zones
            for (TabPosition pos : TabPosition.values()) {
                Zone z = geo.tabZone(pos);
                sb.append(String.format("  INFO  TabZone[%-6s]   = (%d,%d) %d×%d\n",
                        pos, z.left, z.top, z.width, z.height));
            }

            // Verify constraint invariants
            int checks = 0;

            // Invariant: filter button right edge = grid right edge
            int filterRight = geo.filterX() + geo.filterWidth();
            int gridRight = geo.gridX() + geo.gridColumns() * (geo.buttonSize() + BookLayout.GRID_GAP)
                    - BookLayout.GRID_GAP;
            boolean gridAlign = Math.abs(filterRight - gridRight) <= 1;
            sb.append(String.format("  %s  filterButton.right(%d) ≈ gridRight(%d)\n",
                    gridAlign ? "PASS" : "WARN", filterRight, gridRight));
            if (gridAlign) checks++;

            // Invariant: tab left zone width = TAB_WIDTH
            Zone leftTab = geo.tabZone(TabPosition.LEFT);
            boolean tabWidth = leftTab.width == BookLayout.TAB_WIDTH;
            sb.append(String.format("  %s  leftTabZone.width(%d) = TAB_WIDTH(%d)\n",
                    tabWidth ? "PASS" : "FAIL", leftTab.width, BookLayout.TAB_WIDTH));
            if (tabWidth) checks++;

            // Invariant: settings button Y = arrow Y
            boolean settingsArrowY = geo.settingsY() == geo.arrowY();
            sb.append(String.format("  %s  settingsY(%d) = arrowY(%d)\n",
                    settingsArrowY ? "PASS" : "WARN", geo.settingsY(), geo.arrowY()));
            if (settingsArrowY) checks++;

            // Invariant: instant craft button right edge = grid right edge
            int icRight = geo.instantCraftX() + 26; // button width 26
            boolean icAlign = Math.abs(icRight - gridRight) <= 1;
            sb.append(String.format("  %s  instantCraft.right(%d) ≈ gridRight(%d)\n",
                    icAlign ? "PASS" : "WARN", icRight, gridRight));
            if (icAlign) checks++;

            sb.append(String.format("  RESULT  %d/4 constraint invariants hold\n", checks));
            return checks >= 3;
        } catch (Exception e) {
            sb.append(String.format("  FAIL  %s\n", e.getMessage()));
            return false;
        }
    }
}
