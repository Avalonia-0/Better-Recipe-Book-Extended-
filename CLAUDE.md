# Better Recipe Book Extended — Claude Guide

## Multi-branch architecture

Each git branch targets a **different Minecraft version** and is built independently:

| Branch    | Minecraft | Java | Loom                   | Mod Loaders      |
|-----------|-----------|------|------------------------|-----------------|
| `1.21.1`  | 1.21.1    | 21   | Architectury Loom      | Fabric + NeoForge |
| `1.21.11` | 1.21.11   | 21   | Architectury Loom      | Fabric + NeoForge |
| `26.1.2`  | 26.1.2    | 25   | Architectury Loom (loom-no-remap) | Fabric + NeoForge |

**Never copy gradle.properties between branches.** Each branch has a unique `minecraft_version`.
The root `build.gradle` validates this at configure time — you'll see a clear error if they differ.

## Critical 26.1.2 differences from 1.21.11

The 26.1.2 Minecraft version renamed the entire rendering pipeline.
When porting code from the `1.21.11` branch, **every** file using `GuiGraphics` must be updated:

| 1.21.11 (old)                    | 26.1.2 (new)                        |
|----------------------------------|-------------------------------------|
| `GuiGraphics`                    | `GuiGraphicsExtractor`              |
| `render(GuiGraphics, …)`         | `extractRenderState(GuiGraphicsExtractor, …)` |
| `renderWidget(GuiGraphics, …)`   | `extractWidgetRenderState(GuiGraphicsExtractor, …)` |
| `renderFakeItem(ItemStack, x, y)`| `fakeItem(ItemStack, x, y)`         |
| `renderItem(ItemStack, x, y)`    | `item(ItemStack, x, y)`            |
| `drawString(Font, str, x, y, c)` | `text(Font, str, x, y, c)`         |
| `CharacterEvent(char, int)`      | `CharacterEvent(int)` — no modifiers |
| `AbstractWidget.render()`        | `AbstractWidget.extractRenderState()` |
| `ScreenEvents.afterRender()`     | `ScreenEvents.afterExtract()`       |
| `PotionBrewing.Mix`              | `PotionBrewing.Mix` is **package-private** — use reflection |
| `Ingredient.EMPTY`               | removed — use `null` instead       |

## Build commands

```bash
# Full build (common + fabric + neoforge)
./gradlew build

# Re-download mappings after branch switch if cache is corrupted
./gradlew cleanLoomCache
rm -rf .gradle
./gradlew build

# Build only one module
./gradlew :common:compileJava
./gradlew :fabric:build
./gradlew :neoforge:build
```

## When loom cache corrupts

If you see `Namespace mismatch, expected named got official`:
1. Verify `gradle.properties` has the correct `minecraft_version` for this branch
2. Run `./gradlew cleanLoomCache && rm -rf .gradle`
3. Rebuild with `./gradlew build`

## Cloth Config notes

- `enableRecipeBookIsPain` is **hidden from GUI** (`@ConfigEntry.Gui.Excluded`)
  but still works via `config/brbe.toml`.
- `enableTabPage` controls how many recipe tabs show (16 vs 6).
- Hot-reload: no restart needed — toggle `enableRecipeBookIsPain` and the
  recipe book updates immediately via `reloadIfChanged()`.

## CI

GitHub Actions builds both `1.21.11` and `26.1.2` on every push and PR.
`fail-fast: false` ensures one branch failing doesn't cancel the other.
