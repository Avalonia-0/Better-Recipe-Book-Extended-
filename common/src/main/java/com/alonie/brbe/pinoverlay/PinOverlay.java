package com.alonie.brbe.pinoverlay;

import com.alonie.brbe.cache.RecipeViewerIndex;
import com.alonie.brbe.recipeviewer.engine.RecipeViewerEngine;
import com.alonie.brbe.render.PopupRenderer;
import com.alonie.brbe.util.ClientCompat;
import com.alonie.brbe.util.PartialCraftingUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * 1.21.1 版 pin 浮层（1.21.11 PinOverlay 移植；无 RecipeDisplayEntry → 条目用
 * {@link RecipeViewerOverlayDisplayEntry} 的原地版本：RecipeHolder 或 JEI 条目）。
 *
 * <p>与 1.21.11 的差异：无 SlotSelectTime/克隆 OverlayRecipeComponent——
 * 渲染直接用 {@link PopupRenderer}（viewer 同款弹窗），craftable/partial 每次
 * 渲染时按当前物品栏动态判定（refreshRecipeState 同语义）。</p>
 */
public final class PinOverlay {

    public static final int MODE_CRAFTING = 0;
    public static final int MODE_FURNACE = 1;
    public static final int MODE_STONECUTTING = 2;
    public static final int MODE_SMITHING = 3;
    public static final int MODE_ANVIL = 4;
    public static final int MODE_BREWING = 5;
    public static final int MODE_GRINDSTONE = 6;

    private static final int MIN_EDGE = 32;   // pin 图标足迹 / 窗口最小边
    private static final int CELL = 24;

    private final RecipeHolder<?> holder;
    private final RecipeViewerEngine.JeiEntry jei;
    private final int mode;
    private int z;
    private int cx;
    private int cy;
    private boolean tooltipArmed = true;

    private PinOverlay(RecipeHolder<?> holder, RecipeViewerEngine.JeiEntry jei,
                       int mode, int z, int anchorX, int anchorY) {
        this.holder = holder;
        this.jei = jei;
        this.mode = mode;
        this.z = z;
        setCenter(anchorX, anchorY);
    }

    public static PinOverlay create(RecipeHolder<?> holder, RecipeViewerEngine.JeiEntry jei,
                                    int mode, int z, int anchorX, int anchorY) {
        return new PinOverlay(holder, jei, mode, z, anchorX, anchorY);
    }

    // ── 窗口几何（48x48 弹窗盒；1.21.1 无 PopupGeometry 屏钳位→按盒钳位） ────

    public int boxW() {
        return Math.max(CELL * 2, MIN_EDGE);
    }

    public int boxH() {
        return Math.max(CELL * 2, MIN_EDGE);
    }

    public int boxX() {
        return cx - boxW() / 2;
    }

    public int boxY() {
        return cy - boxH() / 2;
    }

    public boolean contains(double mx, double my) {
        return mx >= boxX() && mx < boxX() + boxW()
                && my >= boxY() && my < boxY() + boxH();
    }

    public void setCenter(int nc, int nr) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null) {
            cx = nc;
            cy = nr;
            return;
        }
        int guiW = mc.getWindow().getGuiScaledWidth();
        int guiH = mc.getWindow().getGuiScaledHeight();
        int halfW = boxW() / 2;
        int halfH = boxH() / 2;
        this.cx = Math.max(halfW, Math.min(nc, guiW - halfW));
        this.cy = Math.max(halfH, Math.min(nr, guiH - halfH));
    }

    // ── 状态 ──────────────────────────────────────────────────────────────────

    public int z() { return z; }

    public void setZ(int value) { z = value; }

    public boolean tooltipArmed() { return tooltipArmed; }

    public void armTooltip() { tooltipArmed = true; }

    public int cx() { return cx; }

    public int cy() { return cy; }

    public RecipeHolder<?> holder() { return holder; }

    public int mode() { return mode; }

    public RecipeViewerEngine.JeiEntry jei() { return jei; }

    /** 点击放置用（pin 的配方）。 */
    private RecipeHolder<?> placeable() { return holder; }

    // ── 渲染 ──────────────────────────────────────────────────────────────────

    /** 渲染 pin：PopupRenderer 弹窗（viewer 同款布局）。 */
    public void render(GuiGraphics gui, int mouseX, int mouseY, float delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) return;
        int bx = boxX();
        int by = boxY();
        boolean hovered = contains(mouseX, mouseY);
        PinButtonRenderOverride.push(com.alonie.brbe.render.PopupGeometry.VANILLA_SCALE, mode);
        try {
            if (jei != null) {
                // JEI 条目：1:1 完整 JEI UI；失败回退固定布局
                int[] rect = PopupRenderer.renderJeiPopup1to1(gui, jei, cx, cy, 0, 0);
                if (rect == null) {
                    PopupRenderer.renderJeiPopup(gui, jei, bx, by, boxW(), boxH(), 2.0F);
                }
            } else if (holder != null) {
                // 阶段二 B P5：holder 条目带 JEI 布局（切石/锻造）→ 1:1 委托。
                RecipeViewerEngine.JeiEntry attached =
                        com.alonie.brbe.cache.BrbeJeiBridge.attachedJeiEntry(
                                RecipeViewerEngine.idFor(holder));
                if (attached != null) {
                    int[] rect = PopupRenderer.renderJeiPopup1to1(gui, attached, cx, cy, 0, 0);
                    if (rect == null) {
                        PopupRenderer.renderJeiPopup(gui, attached, bx, by, boxW(), boxH(), 2.0F);
                    }
                } else {
                    boolean craftable;
                    boolean partial = false;
                    try {
                        craftable = isCraftableNow(mc);
                        partial = isPartialNow(mc);
                    } catch (Exception e) {
                        craftable = false;
                    }
                    PopupRenderer.renderRecipePopup(gui, holder, mode, craftable, partial,
                            bx + 12, by + 12, CELL, CELL, true, 2.0F);
                }
            }
        } finally {
            PinButtonRenderOverride.pop();
        }
    }

    /** 当前可合成（pin 集合动态判定）。 */
    public boolean isCraftableNow(Minecraft mc) {
        if (holder == null) return false;
        try {
            RecipeViewerIndex.Family family = familyFor();
            int width = family == RecipeViewerIndex.Family.FURNACE ? 1 : 2;
            net.minecraft.client.gui.screens.recipebook.RecipeCollection collection =
                    RecipeViewerIndex.toCollection(List.of(holder),
                            partialStackedContents(), mc.player.getRecipeBook());
            return collection.isCraftable(holder);
        } catch (Exception e) {
            return false;
        }
    }

    private net.minecraft.world.entity.player.StackedContents partialStackedContents() {
        net.minecraft.world.entity.player.StackedContents stacked =
                new net.minecraft.world.entity.player.StackedContents();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.getInventory().fillStackedContents(stacked);
        }
        return stacked;
    }

    /** 当前残缺（旧模型判定）。 */
    public boolean isPartialNow(Minecraft mc) {
        if (holder == null) return false;
        try {
            RecipeViewerIndex.Family family = familyFor();
            if (family == RecipeViewerIndex.Family.FURNACE) return false;
            java.util.Set<net.minecraft.world.item.Item> items = new java.util.HashSet<>();
            if (mc.player != null) {
                for (ItemStack stack : mc.player.getInventory().items) {
                    if (!stack.isEmpty()) items.add(stack.getItem());
                }
            }
            net.minecraft.client.gui.screens.recipebook.RecipeCollection collection =
                    RecipeViewerIndex.toCollection(List.of(holder),
                            partialStackedContents(), mc.player.getRecipeBook());
            com.alonie.brbe.util.PartialCraftingUtil.beginFilteringUpdate(false);
            return PartialCraftingUtil.markPartialMaterials(collection, items, items);
        } catch (Exception e) {
            return false;
        }
    }

    private RecipeViewerIndex.Family familyFor() {
        return switch (mode) {
            case MODE_FURNACE -> RecipeViewerIndex.Family.FURNACE;
            case MODE_STONECUTTING -> RecipeViewerIndex.Family.STONECUTTING;
            case MODE_SMITHING -> RecipeViewerIndex.Family.SMITHING;
            case MODE_ANVIL -> RecipeViewerIndex.Family.ANVIL;
            case MODE_BREWING -> RecipeViewerIndex.Family.BREWING;
            case MODE_GRINDSTONE -> RecipeViewerIndex.Family.GRINDSTONE;
            default -> RecipeViewerIndex.Family.CRAFTING;
        };
    }

    // ── 持久化身份（1.21.11 PinSpec 语义） ────────────────────────────────────

    public record PinSpec(String resultItem, int resultCount, List<String> inputs,
                          int mode, int x, int y, int z) {}

    public PinSpec toSpec() {
        ItemStack result = resultOf();
        return new PinSpec(resultKey(result), result.getCount(),
                fingerprint(), mode, cx, cy, z);
    }

    public ItemStack resultOf() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (holder != null) {
                return holder.value().getResultItem(mc.level.registryAccess());
            }
            if (jei != null && jei.outputs() != null && !jei.outputs().isEmpty()) {
                return jei.outputs().get(0);
            }
        } catch (Exception e) {
            // broken — empty
        }
        return ItemStack.EMPTY;
    }

    public static String resultKey(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "";
        return BuiltInRegistries.ITEM.getKey(stack.getItem()) + ":" + stack.getCount();
    }

    public static ItemStack itemFromKey(String key) {
        if (key == null || key.isBlank()) return ItemStack.EMPTY;
        int idx = key.lastIndexOf(':');
        if (idx <= 0) return ItemStack.EMPTY;
        ResourceLocation id = ResourceLocation.parse(key.substring(0, idx));
        int count = Math.min(1, Integer.parseInt(key.substring(idx + 1)));
        return new ItemStack(BuiltInRegistries.ITEM.getOptional(id).orElse(net.minecraft.world.item.Items.AIR), count);
    }

    /** 槽位指纹（TreeSet 排序去重注册表 id）——恢复时区分同结果配方。 */
    public List<String> fingerprint() {
        TreeSet<String> ids = new TreeSet<>();
        if (holder != null) {
            try {
                for (Ingredient ingredient : holder.value().getIngredients()) {
                    for (ItemStack stack : ingredient.getItems()) {
                        ids.add(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
                        break;
                    }
                }
                ItemStack result = resultOf();
                if (!result.isEmpty()) {
                    ids.add(BuiltInRegistries.ITEM.getKey(result.getItem()).toString());
                }
            } catch (Exception ignore) {
            }
        } else if (jei != null) {
            for (ItemStack stack : jei.inputs() == null ? List.<ItemStack>of() : jei.inputs()) {
                if (stack != null && !stack.isEmpty()) {
                    ids.add(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
                }
            }
            for (ItemStack stack : jei.outputs() == null ? List.<ItemStack>of() : jei.outputs()) {
                if (stack != null && !stack.isEmpty()) {
                    ids.add(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
                }
            }
        }
        return new ArrayList<>(ids);
    }
}
