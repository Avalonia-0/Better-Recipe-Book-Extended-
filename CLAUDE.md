# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Multi-branch architecture

Each git branch targets a **different Minecraft version** and is built independently:

| Branch    | Minecraft | Java | Loom                         | Mod Loaders      |
|-----------|-----------|------|------------------------------|-------------------|
| `1.21.1`  | 1.21.1    | 21   | Architectury Loom            | Fabric + NeoForge |
| `1.21.11` | 1.21.11   | 21   | Architectury Loom            | Fabric + NeoForge |
| `26.1.2`  | 26.1.2    | 25   | Loom (`loom-no-remap`)       | Fabric + NeoForge |

**The root `build.gradle` validates `minecraft_version` against the branch name at configure time** — it will fail with a clear error if they differ.

## Module layout

```
common/          ← shared across Fabric + NeoForge
  api/           ←   public interfaces (HudHider, ConfigScreenProvider)
  impl/          ←   implementations (JeiHudHider, ReiHudHider)
  compat/        ←   cross-mod bridges (OverlayHider → thin registry, ReiCompat)
  mixins/        ←   grouped by feature, one directory per concern
  recipebookispain_extended/  ← RBIP — merged source code, own mixin configs
fabric/          ← Fabric-specific entry points + JEI plugin
neoforge/        ← NeoForge-specific entry points + platform init
```

## Critical 26.1.2 API differences from 1.21.x

| Old (1.21.x)                     | New (26.1.2)                        |
|----------------------------------|-------------------------------------|
| `GuiGraphics`                    | `GuiGraphicsExtractor`              |
| `render(GuiGraphics,…)`          | `extractRenderState(GuiGraphicsExtractor,…)` |
| `renderWidget(GuiGraphics,…)`    | `extractWidgetRenderState(GuiGraphicsExtractor,…)` |
| `renderFakeItem(stack, x, y)`    | `fakeItem(stack, x, y)`             |
| `renderItem(stack, x, y)`        | `item(stack, x, y)`                 |
| `drawString(font, str, x, y, c)` | `text(font, str, x, y, c)`          |
| `CharacterEvent(char, int)`      | `CharacterEvent(int)`               |
| `ScreenEvents.afterRender()`     | `ScreenEvents.afterExtract()`       |
| `PotionBrewing.Mix`              | package-private — reflection needed |
| `Ingredient.EMPTY`               | removed — use null                  |

When porting from `1.21.11` → `26.1.2`, grep every Mixin `@Inject`/`@Redirect` annotation for `method` and `target` strings referencing old names.

## Build commands

```bash
./gradlew build                    # full build (common + fabric + neoforge)
./gradlew :common:compileJava      # compile-only check

# Cache corruption recovery (after branch switches)
./gradlew cleanLoomCache && rm -rf .gradle && ./gradlew build

# Deploy (build JAR → copy to test instance)
cp fabric/build/libs/BetterRecipeBookExtended-fabric-26.1.2-2.0.1.jar /media/…/26.1.2-Fabric/mods/
cp neoforge/build/libs/BetterRecipeBookExtended-neoforge-26.1.2-2.0.1.jar /media/…/26.1.2-NeoForge/mods/
```

Test instance paths follow the pattern `/media/avalonia/data/MinecraftLib/versions/<version>-<loader>/mods/`.

## Config features and their gates

| Config field | Effect | Gate location |
|-------------|--------|---------------|
| `hideReiJeiOverlay` | Hides JEI/REI overlays | `OverlayHider.setOverlaysHidden()` → iterates `HudHider` registry |
| `showAllRecipesInSurvival` | When **false**, skips ALL partial-material injection (vanilla-only) | `RecipeBookComponentMixin.keepPartiallyCraftable` |
| `enableRecipeBookIsPain` | Enables RBIP creative-mode tabs in recipe book | Hidden from GUI (`@ConfigEntry.Gui.Excluded`), edited in `brbe.toml`, hot-reloaded via `reloadIfChanged()` |
| `enablePinning` | Pin recipes | `PinnedRecipeManager` |
| `instantCraft.enabled` | Shift-click instant craft | `InstantCraftingManager` |
| `alternativeRecipes.noGrouped` | Ungroup recipe variants | `ungroup/RecipeBookComponentMixin` |

## RBIP (Recipe Book is Pain) module

- Source-merged into `common/.../recipebookispain_extended/` — **not** a jar-in-jar dependency
- Own mixin configs: `recipe-book-is-pain-extended.mixins.json` (Fabric), `rbip-neoforge.mixins.json` (NeoForge)
- Platform init: NeoForge → `BetterRecipeBookClientNeoForge.init()`, Fabric → `RBIPFabricEntrypoint`
- Config bridged through `RecipeBookIsPainExtendedConfig.enabled()` → reads `brbe.toml [rbip]`
- If `enableRecipeBookIsPain` is off, RBIP is a no-op (constructor saves `vanillaTabInfos`, all methods guard on `enabled()`)

## HudHider API (refactored from OverlayHider)

`OverlayHider` is now a thin registry. New implementations implement `api/hud/HudHider`:

```java
OverlayHider.register(new JeiHudHider());  // JEI IClientToggleState bridge
OverlayHider.register(new ReiHudHider());  // REI ConfigObject bridge
```

Each hider owns its own state (snapshot, guard flags). Adding a new HUD mod only requires implementing the interface + one registration call.
