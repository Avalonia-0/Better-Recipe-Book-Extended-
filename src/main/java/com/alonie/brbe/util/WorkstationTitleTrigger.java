package com.alonie.brbe.util;

import com.alonie.brbe.mixins.accessors.AbstractContainerScreenAccessor;
import com.alonie.brbe.mixins.accessors.ScreenAccessor;
import com.alonie.brbe.pinoverlay.PinOverlayManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.BlastFurnaceMenu;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.FurnaceMenu;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.inventory.SmokerMenu;
import net.minecraft.world.inventory.StonecutterMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * 工作站标题触发：点击（或悬停）工作站界面<b>标题</b>（合成/升级装备/厨锅…）
 * 打开 BRBE 查询界面，查询<b>该工作站所属的对象</b>（U 语义——该工作站作为
 * 工作站的配方，而非作为材料）——与 JEI 点击界面箭头查看同类别的逻辑等价，
 * 但锚点选标题（每个容器屏幕都有 {@code titleLabelX/Y} 标签框，跨原版/mod、
 * 跨语言/资源包通用，mod 无需注册任何区域）。
 *
 * <p>菜单 → 工作站物品：原版 7 菜单硬编码映射；mod 屏幕（如厨锅）反射调用
 * {@code menu.getBlockEntity()} 取方块（best-effort）。<b>解析失败的屏幕
 * 静默无响应并隐藏 tooltip</b>（预加载：悬停/点击前先解析，失败即整个入口
 * 关闭——光标/提示都不出现）。已打开的查询窗口拥有该点时，标题触发让位。
 */
public final class WorkstationTitleTrigger {

    private WorkstationTitleTrigger() {
    }

    /** 标题命中框（屏幕绝对坐标，含 2px 内边距）。 */
    private static int[] titleRect(AbstractContainerScreen<?> screen) {
        AbstractContainerScreenAccessor acc = (AbstractContainerScreenAccessor) screen;
        int w = Minecraft.getInstance().font.width(
                ((ScreenAccessor) screen).brbe$getTitle());
        // extractLabels 在 pose.translate(leftPos, topPos) 内绘制标题——绝对
        // 屏幕位置 = leftPos + titleLabelX。
        int x = acc.brbe$getLeftPos() + acc.brbe$getTitleLabelX() - 2;
        int y = acc.brbe$getTopPos() + acc.brbe$getTitleLabelY() - 2;
        return new int[] {x, y, w + 4, 12};
    }

    /** 是否命中标题区：在标题框内、工作站可解析、且不被查询窗口/pin 覆盖。 */
    public static boolean titleHover(AbstractContainerScreen<?> screen, int mx, int my) {
        int[] r = titleRect(screen);
        if (mx < r[0] || mx >= r[0] + r[2] || my < r[1] || my >= r[1] + r[3]) return false;
        if (RecipeViewerOverlay.ownsPoint(mx, my)
                || PinOverlayManager.topInteractivePin(mx, my) != null) return false;
        return !stationItem(screen).isEmpty();
    }

    /** 左键点击标题：打开<b>新</b>查询窗口（多窗口，已开窗口不受影响）。
     *  工作站无法解析时静默（返回 false，点击交给原版）。 */
    public static boolean clickTitle(MouseButtonEvent event, AbstractContainerScreen<?> screen) {
        if (!ClientCompat.isLeftClick(event)) return false;
        int mx = net.minecraft.util.Mth.floor(event.x());
        int my = net.minecraft.util.Mth.floor(event.y());
        int[] r = titleRect(screen);
        if (mx < r[0] || mx >= r[0] + r[2] || my < r[1] || my >= r[1] + r[3]) return false;
        if (RecipeViewerOverlay.ownsPoint(mx, my)
                || PinOverlayManager.topInteractivePin(mx, my) != null) return false;
        ItemStack station = stationItem(screen);
        if (station.isEmpty()) return false;   // 静默（tooltip 也已隐藏）
        Minecraft mc = Minecraft.getInstance();
        if (mc.getSoundManager() != null) {
            net.minecraft.client.gui.components.AbstractWidget.playButtonClickSound(mc.getSoundManager());
        }
        return RecipeViewerOverlay.openForStation(screen, station);
    }

    /** 每屏幕缓存的"由谁打开"（打开瞬间的准星方块优先）。 */
    private static final java.util.WeakHashMap<AbstractContainerScreen<?>, ItemStack>
            STATION_CACHE = new java.util.WeakHashMap<>();

    /** GUI 打开瞬间由 mixin 的 {@code init} 钩子调用：立即捕获"谁打开了
     *  这个界面"并缓存（此刻玩家右键的准星仍指向打开的方块——这是最可信的
     *  原始事实；此后玩家转视角不影响）。init 未触发时（异常屏幕）回退到
     *  {@link #stationItem} 的惰性求值。 */
    public static void capture(AbstractContainerScreen<?> screen) {
        STATION_CACHE.put(screen, resolveStation(screen));
    }

    /** 该界面的"主人"（哪个工作站打开了它）：<b>打开瞬间准星命中的方块</b>
     *  优先（开 GUI 必右键指向该方块——Better End 终末合金砧用的是原版铁砧
     *  界面，以"界面类型"查就会落到原版铁砧的用途页；以准星方块查才是它本身）；
     *  命中方块经引擎校验可查询（{@code defaultFor != null}），否则退回菜单
     *  映射链。首次求值时捕获并缓存（此后玩家移动视角不影响）。
     */
    public static ItemStack stationItem(AbstractContainerScreen<?> screen) {
        ItemStack cached = STATION_CACHE.get(screen);
        if (cached != null) return cached;
        ItemStack station = resolveStation(screen);
        STATION_CACHE.put(screen, station);
        return station;
    }

    private static ItemStack resolveStation(AbstractContainerScreen<?> screen) {
        AbstractContainerMenu menu = screen.getMenu();
        ItemStack menuStation = byMenu(menu);
        ItemStack hit = hitBlockStation();
        if (!hit.isEmpty() && !hit.is(menuStation.getItem())
                && com.alonie.brbe.recipeviewer.RecipeViewerCategories
                        .isRegisteredStationTarget(hit)) {
            return hit;
        }
        return menuStation;
    }

    /** 打开瞬间准星命中的方块物品（best-effort；缓存已锁定打开瞬间的值）。
     *  <b>距离约束</b>：非交互距离（>5 格，原版强交互约 4.5 格）的命中不采纳
     *  ——mod 远程打开 GUI 而玩家未在交互距离内时，远处方块被过滤，回退菜单
     *  映射链（防"看着远处另一台工作站误开错误窗口"）。 */
    private static ItemStack hitBlockStation() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || mc.player == null
                    || !(mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult bhr)) {
                return ItemStack.EMPTY;
            }
            // 距离约束：非交互距离（>5 格，原版强交互约 4.5 格）的命中不采纳。
            if (bhr.getLocation().distanceToSqr(mc.player.getEyePosition()) > 25.0) {
                return ItemStack.EMPTY;
            }
            net.minecraft.world.level.block.state.BlockState state = mc.level.getBlockState(bhr.getBlockPos());
            return state.isAir() ? ItemStack.EMPTY : new ItemStack(state.getBlock());
        } catch (Exception | LinkageError ignored) {
            return ItemStack.EMPTY;
        }
    }

    /** The workstation item of the open menu (vanilla table + mod block-entity
     *  reflection), or EMPTY when unresolvable. */
    public static ItemStack byMenu(AbstractContainerMenu menu) {
        if (menu == null) return ItemStack.EMPTY;
        if (menu instanceof CraftingMenu) return new ItemStack(Items.CRAFTING_TABLE);
        if (menu instanceof FurnaceMenu) return new ItemStack(Items.FURNACE);
        if (menu instanceof SmokerMenu) return new ItemStack(Items.SMOKER);
        if (menu instanceof BlastFurnaceMenu) return new ItemStack(Items.BLAST_FURNACE);
        if (menu instanceof StonecutterMenu) return new ItemStack(Items.STONECUTTER);
        if (menu instanceof SmithingMenu) return new ItemStack(Items.SMITHING_TABLE);
        if (menu instanceof GrindstoneMenu) return new ItemStack(Items.GRINDSTONE);
        if (menu instanceof AnvilMenu) return new ItemStack(Items.ANVIL);
        if (menu instanceof BrewingStandMenu) return new ItemStack(Items.BREWING_STAND);
        // 熔炉系基类兜底：mod 熔炉菜单（如 aerialhell StellarFurnaceMenu 继承
        // AbstractFurnaceMenu）→ 开"烧炼"类别的查询（引擎的 smelting 类别含
        // 其注册的工作站）。
        if (menu instanceof AbstractFurnaceMenu) return new ItemStack(Items.FURNACE);
        return blockEntityStation(menu);
    }

    /** Best-effort: a mod menu exposing its workstation resolves the station
     *  item through a chain of common shapes (nothing is hard-coded per mod):
     *  <ol>
     *    <li>{@code getBlockEntity()} method (many mod menus);</li>
     *    <li>any public {@link BlockEntity} field (Farmer's Delight's
     *        {@code CookingPotMenu.blockEntity});</li>
     *    <li>a {@code getPos()} / {@code getBlockPos()} method or public
     *        {@link net.minecraft.core.BlockPos} field → level lookup;</li>
     *    <li>any slot whose {@code container} is a {@link BlockEntity}.</li>
     *  </ol> */
    private static ItemStack blockEntityStation(AbstractContainerMenu menu) {
        try {
            Object be = menu.getClass().getMethod("getBlockEntity").invoke(menu);
            if (be instanceof BlockEntity blockEntity) {
                return new ItemStack(blockEntity.getBlockState().getBlock());
            }
        } catch (Exception | LinkageError ignored) {
            // fall through
        }
        try {
            for (java.lang.reflect.Field field : menu.getClass().getFields()) {
                try {
                    Object value = field.get(menu);
                    if (value instanceof BlockEntity blockEntity) {
                        return new ItemStack(blockEntity.getBlockState().getBlock());
                    }
                    if (value instanceof net.minecraft.core.BlockPos pos) {
                        net.minecraft.world.item.ItemStack fromPos = blockAt(pos);
                        if (!fromPos.isEmpty()) return fromPos;
                    }
                } catch (Exception | LinkageError ignored) {
                    // try next field
                }
            }
        } catch (Exception | LinkageError ignored) {
            // fall through
        }
        for (String name : new String[] {"getPos", "getBlockPos"}) {
            try {
                Object pos = menu.getClass().getMethod(name).invoke(menu);
                if (pos instanceof net.minecraft.core.BlockPos blockPos) {
                    net.minecraft.world.item.ItemStack fromPos = blockAt(blockPos);
                    if (!fromPos.isEmpty()) return fromPos;
                }
            } catch (Exception | LinkageError ignored) {
                // try next accessor
            }
        }
        try {
            for (net.minecraft.world.inventory.Slot slot : menu.slots) {
                if (slot != null && slot.container instanceof BlockEntity blockEntity) {
                    return new ItemStack(blockEntity.getBlockState().getBlock());
                }
            }
        } catch (Exception | LinkageError ignored) {
            // fall through
        }
        return ItemStack.EMPTY;
    }

    /** 世界内按坐标取方块物品（best-effort）。 */
    private static ItemStack blockAt(net.minecraft.core.BlockPos pos) {
        try {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.level == null) return ItemStack.EMPTY;
            net.minecraft.world.level.block.state.BlockState state = mc.level.getBlockState(pos);
            return state.isAir() ? ItemStack.EMPTY : new ItemStack(state.getBlock());
        } catch (Exception | LinkageError ignored) {
            return ItemStack.EMPTY;
        }
    }

    /** 标题 tooltip 文本（"显示配方"）。 */
    public static Component tooltip() {
        return Component.translatable("brbe.gui.title.showRecipes");
    }
}
