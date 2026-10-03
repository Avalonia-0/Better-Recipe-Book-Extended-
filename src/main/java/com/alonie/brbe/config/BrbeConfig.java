package com.alonie.brbe.config;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;

/**
 * Root configuration for Better Recipe Book Extended.
 *
 * <p>Sub-configs ({@code InstantCraft},
 * {@code AlternativeRecipes}, {@code Scrolling}) are standalone classes in the
 * same package — same structure as the 1.21.1 branch.  Only
 * {@code RecipeBookIsPain} is nested here (matches 1.21.1's {@code Config}).</p>
 */
@Config(name = "brbe")
public class BrbeConfig implements ConfigData {

    // -- 配方书设置（general 标签）--------------------------------------------

    /** 「自动填充幽灵配方」：鼠标指向配方书中的某个配方时，直接在工作区填充该配方的幽灵
     *  物品（等价于点击该配方所展示的幽灵物品），鼠标移开则立刻消失。默认开。 */
    @ConfigEntry.Gui.Tooltip
    public boolean autoFillGhostRecipe = true;

    /** 拼音搜索：在搜索栏输入拼音匹配中文物品名。仅中文语言（zh_*）下显示配置项并默认开启；其他语言强制关闭。 */
    @ConfigEntry.Gui.Tooltip
    public boolean pinyinSearch = false;

    /** 保存配方书上一次的浏览记录（标签 + 页码），下次打开恢复。 */
    @ConfigEntry.Gui.Tooltip
    public boolean saveRecipeBookPosition = true;

    @ConfigEntry.Gui.Tooltip
    public boolean showModName = false;

    @ConfigEntry.Gui.TransitiveObject
    public Scrolling scrolling = new Scrolling();

    @ConfigEntry.Gui.Tooltip
    @ConfigEntry.Gui.PrefixText
    public boolean recipeViewerEnabled = true;

    /** 「查询范围」（用户 2026-10-03：显示名由「配方书模式」改来，类型由布尔改为枚举）：
     *  {@link QueryScope#RECIPE_BOOK_ONLY} = 仅限配方书（原 {@code true}）；
     *  {@link QueryScope#ALL_CATEGORIES} = 全类别（原 {@code false}，默认）。
     *  语义与旧布尔完全一致：仅限配方书时，配方书体系的工作站（合成/烧炼/锻造/酿造——BRBE 自带
     *  酿造配方书）与配方书驱动的模组类别保留，无配方书体系的工作站（切石/铁砧/研磨）与信息行
     *  类别（燃料/堆肥/信息）整体隐藏，对象的 tooltip 也过滤非法工作站图标。
     *  旧 TOML 布尔键 {@code hideNoRecipeBookStationObjects} 由配置加载前的
     *  {@code migrateLegacyConfigValuesInToml()} 迁移。 */
    @ConfigEntry.Gui.Tooltip(count = 2)
    // 两行说明（排版对齐「标签模式」）：…@Tooltip[0] / [1]。
    // ⚠️ 必需（理由同 pageFlipDirection）：AutoConfig 对**没有这个注解**的枚举字段生成的是
    // 可搜索下拉框（DropdownBoxEntry，实测候选只剩当前档位）；带上才走枚举切换按钮
    // （SelectionListEntry，与「标签模式」同一条 provider 路径）。
    @ConfigEntry.Gui.EnumHandler(option = ConfigEntry.Gui.EnumHandler.EnumDisplayOption.BUTTON)
    public QueryScope queryScope = QueryScope.ALL_CATEGORIES;

    /** 「查询范围」的**两档**。档位名走 {@code text.autoconfig.brbe.option.queryScope.<常量名>}。 */
    public enum QueryScope implements me.shedaniel.clothconfig2.gui.entries.SelectionListEntry.Translatable {
        /** 仅限配方书：只显示配方书体系内的对象（原布尔 {@code hideNoRecipeBookStationObjects=true}）。 */
        RECIPE_BOOK_ONLY,
        /** 全类别：不按配方书体系过滤（原布尔 {@code =false}，默认）。 */
        ALL_CATEGORIES;

        @Override
        public String getKey() {
            return "text.autoconfig.brbe.option.queryScope." + name();
        }

        /** 便捷判断（核心代码用它，避免直接耦合枚举）。 */
        public boolean recipeBookOnly() {
            return this == RECIPE_BOOK_ONLY;
        }
    }

    /** 旧布尔 {@code hideNoRecipeBookStationObjects} 的读取点统一改用它
     *  （字段缺失 / 为 null 时按默认的「全类别」）。 */
    public boolean recipeBookOnly() {
        return queryScope == QueryScope.RECIPE_BOOK_ONLY;
    }

    /** 「窗口模式」（用户 2026-10-03：由布尔项改为枚举，并改名）：
     *  {@link WindowMode#PERSISTENT} = 持久（原 {@code false}，默认）：重新开启界面时查询窗口恢复；
     *  {@link WindowMode#PREVIEW} = 预览（原 {@code true}）：重新开启界面时查询窗口不再恢复，
     *  与其他元素交互时也会关闭查询窗口。
     *  旧 TOML 布尔键 {@code previewMode} 由配置加载前的
     *  {@code migrateLegacyConfigValuesInToml()} 迁移。 */
    @ConfigEntry.Gui.Tooltip(count = 2)
    // 两行说明（排版对齐「标签模式」）：…@Tooltip[0] / [1]。
    // ⚠️ 必需（理由同 queryScope）：没有它 AutoConfig 会生成候选只剩当前档位的下拉框。
    @ConfigEntry.Gui.EnumHandler(option = ConfigEntry.Gui.EnumHandler.EnumDisplayOption.BUTTON)
    public WindowMode windowMode = WindowMode.PERSISTENT;

    /** 「窗口模式」的**两档**。档位名走 {@code text.autoconfig.brbe.option.windowMode.<常量名>}。 */
    public enum WindowMode implements me.shedaniel.clothconfig2.gui.entries.SelectionListEntry.Translatable {
        /** 持久：查询窗口在重新开启界面时恢复（原布尔 {@code previewMode=false}，默认）。 */
        PERSISTENT,
        /** 预览：不恢复，且与其他元素交互时关闭（原布尔 {@code previewMode=true}）。 */
        PREVIEW;

        @Override
        public String getKey() {
            return "text.autoconfig.brbe.option.windowMode." + name();
        }

        /** 便捷判断（核心代码用它，避免直接耦合枚举）。 */
        public boolean preview() {
            return this == PREVIEW;
        }
    }

    /** 旧布尔 {@code previewMode} 的读取点统一改用它（字段缺失 / 为 null 时按默认的「持久」）。 */
    public boolean previewMode() {
        return windowMode == WindowMode.PREVIEW;
    }

    /** 「配方区翻页方向」（用户 2026-10-03：由布尔项改为枚举，并改名）：
     *  {@link PageFlipDirection#NATURAL} = 自然（鼠标滚轮向前 / 上滚＝往后翻页，原 {@code true}）；
     *  {@link PageFlipDirection#REGULAR} = 常规（旧方向：上滚＝往前翻页，原 {@code false}，默认）。
     *  只作用于**查询窗口配方区**的翻页——标签条翻页、Alt+滚轮轮循、配方书自身的翻页都不受影响。
     *  旧 TOML 布尔键 {@code naturalPageDirection} 由配置加载前的
     *  {@code migrateLegacyConfigValuesInToml()} 迁移成 {@code pageFlipDirection = "NATURAL"|"REGULAR"}。 */
    @ConfigEntry.Gui.Tooltip(count = 2)
    // 两行说明（排版对齐「标签模式」）：Cloth 的 @Tooltip(count = N) 读 …@Tooltip[0] / [1]。
    // ⚠️ 这个注解是**必需**的（用户 2026-10-03 实测："这一项只有一个『常规』可选"）：
    // AutoConfig 的 DefaultGuiProviders 对枚举字段注册了**两条**路径 ——
    //   ① 带本注解 → startSelector(...) → SelectionListEntry＝**枚举切换按钮**（点一下切下一档）；
    //   ② 不带    → startDropdownMenu(...) → DropdownBoxEntry＝可搜索下拉框（候选只剩当前档位）。
    // 注册顺序 ① 在前、GuiRegistry 取 findFirst → 带注解才走切换按钮（「标签模式」走的正是 ①）。
    @ConfigEntry.Gui.EnumHandler(option = ConfigEntry.Gui.EnumHandler.EnumDisplayOption.BUTTON)
    public PageFlipDirection pageFlipDirection = PageFlipDirection.REGULAR;

    /** 「配方区翻页方向」的**两档**。AutoConfig 的枚举选择器按
     *  {@link me.shedaniel.clothconfig2.gui.entries.SelectionListEntry.Translatable#getKey()} 取显示名，
     *  即 {@code text.autoconfig.brbe.option.pageFlipDirection.<常量名>}。 */
    public enum PageFlipDirection implements me.shedaniel.clothconfig2.gui.entries.SelectionListEntry.Translatable {
        /** 自然：鼠标滚轮向前（上滚）＝往后翻页（原布尔项 {@code true}）。 */
        NATURAL,
        /** 常规：旧方向，上滚＝往前翻页（原布尔项 {@code false}，默认）。 */
        REGULAR;

        @Override
        public String getKey() {
            return "text.autoconfig.brbe.option.pageFlipDirection." + name();
        }

        /** 便捷判断（核心代码用它，避免直接耦合枚举）。 */
        public boolean natural() {
            return this == NATURAL;
        }
    }

    @ConfigEntry.Gui.TransitiveObject
    public RecipeBookIsPain rbip = new RecipeBookIsPain();

    @ConfigEntry.Gui.TransitiveObject
    public InstantCraft instantCraft = new InstantCraft();

    // -- 界面设置（ui 标签）----------------------------------------------------

    /** 隐藏生存模式配方书中的3x3配方标记。 */
    @ConfigEntry.Category("ui")
    public boolean hideIncompatibleMark = false;

    /** 解锁新物品时启用小弹跳动画。 */
    @ConfigEntry.Category("ui")
    @ConfigEntry.Gui.Tooltip
    public boolean enableBounce = false;

    @ConfigEntry.Category("ui")
    @ConfigEntry.Gui.Tooltip
    public boolean keepCentered = false;

    @ConfigEntry.Category("ui")
    @ConfigEntry.Gui.Excluded
    @ConfigEntry.Gui.Tooltip
    public boolean expandedRecipeBook = false;

    /** 鼠标滚轮翻页音效：滚轮翻页（配方区/配方书标签/查询浮层）时播放点击音。 */
    @ConfigEntry.Category("ui")
    @ConfigEntry.Gui.PrefixText
    public boolean scrollPageSound = true;

    @ConfigEntry.Category("ui")
    @ConfigEntry.Gui.TransitiveObject
    public PageAnimation pageAnimation = new PageAnimation();

    /** 「显示设置按钮」原先带一条 {@code @PrefixText} 黄字提示行
     *  （"如果你禁用了以下两个选项，需要通过模组菜单来重新打开它们"），
     *  按用户要求已移除该文字行 —— 注解一并删除，AutoConfig 不再生成该条目。 */
    @ConfigEntry.Category("ui")
    @ConfigEntry.Gui.Tooltip
    public boolean settingsButton = true;

    @ConfigEntry.Category("ui")
    @ConfigEntry.Gui.Tooltip
    public boolean enableBook = true;

    // -- 配方设置（recipeSettings 标签）----------------------------------------

    /** 启用后自动解锁所有配方，无需先发现即可在配方书中查看。重新进入游戏生效。 */
    @ConfigEntry.Category("recipeSettings")
    @ConfigEntry.Gui.PrefixText
    @ConfigEntry.Gui.Tooltip
    public boolean unlockAll = true;

    /** 默认关（2026-09-23 用户要求）：开启时才在生存模式配方书里放行 3×3 / 环境不兼容配方。 */
    @ConfigEntry.Category("recipeSettings")
    public boolean showAllRecipesInSurvival = false;

    /** 默认开（2026-09-25 用户要求；2026-09-23 曾按要求改为关）：开启 = 移除「仅显示可合成」按钮并让可合成始终置顶。 */
    @ConfigEntry.Category("recipeSettings")
    @ConfigEntry.Gui.Tooltip
    public boolean partialCraftingEnabled = true;

    @ConfigEntry.Category("recipeSettings")
    @ConfigEntry.Gui.PrefixText
    @ConfigEntry.Gui.Tooltip
    public boolean partialMarkingEnabled = true;

    @ConfigEntry.Category("recipeSettings")
    @ConfigEntry.Gui.Tooltip
    public boolean partialOnlyWhenCarrying = false;

    @ConfigEntry.Category("recipeSettings")
    @ConfigEntry.Gui.PrefixText
    @ConfigEntry.Gui.TransitiveObject
    public AlternativeRecipes alternativeRecipes = new AlternativeRecipes();

    // -- 快捷键（keybindings 标签；位于「配方」右侧）----------------------------

    /** 「固定」快捷键（GUI 渲染为键位输入框，存为字符串）。默认 A。 */
    @ConfigEntry.Category("keybindings")
    @ConfigEntry.Gui.Tooltip
    public String pinKey = KeybindingCodec.PIN_DEFAULT_RAW;

    /** 「锁定」快捷键（GUI 渲染为键位输入框，存为字符串）。默认 Alt。
     *  按住 = 冻结**指针下那一个**折叠物品的自动轮换（配方书内的配方、功能方块内的
     *  幽灵物品、查询界面/预览里的对象），配合滚轮逐格翻动；松开恢复自动轮换。
     *  GUI 标题「锁定」，tooltip「锁定循环中的折叠物品。」（用户 2026-09-13）；
     *  位置在「固定」下方。 */
    @ConfigEntry.Gui.Tooltip
    @ConfigEntry.Category("keybindings")
    public String cycleLockKey = KeybindingCodec.cycleLockDefaultRaw();

    /** 「查询合成」快捷键（GUI 渲染为键位输入框，存为字符串）。默认 R。
     *  @PrefixText 复制自 recipeViewerEnabled 的信息行（原信息行保留在
     *  「功能」类别，这里为同一行文案的副本）。 */
    @ConfigEntry.Category("keybindings")
    @ConfigEntry.Gui.PrefixText
    @ConfigEntry.Gui.Tooltip
    public String recipeViewKey = KeybindingCodec.recipeViewDefaultRaw();

    /** 「查询用途」快捷键（GUI 渲染为键位输入框，存为字符串）。默认 U。 */
    @ConfigEntry.Category("keybindings")
    @ConfigEntry.Gui.Tooltip
    public String usageViewKey = KeybindingCodec.usageViewDefaultRaw();

    /** 查询界面「配方区行上限」：对象区**一页最多显示的行数**，默认 3。
     *
     *  <p>行上限同时就是**工作站列的对象数量上限**：查询窗口左侧工作站列的行数
     *  由框体高度推导（{@code RecipeViewerOverlay.stationViewRows()} =
     *  {@code (boxH - 8) / 25}），而框体高度 = 本页行数 × 25 + 8，本页行数恒
     *  ≤ 行上限 —— 所以列里最多只会出现"行上限"个对象，无需额外钳制。
     *
     *  <p>GUI：无 tooltip，只接受整型（运行时会夹紧到 1–64）；位置在「查询用途」
     *  下方、「配方区列上限」上方。 */
    @ConfigEntry.Category("keybindings")
    public int recipeViewerRowLimit = 3;

    /** 查询界面「配方区列上限」：对象区**一页最多显示的列数**，默认 7。
     *
     *  <p>列上限同时就是**底部标签的数量上限**（标签条一次最多显示"列上限"个标签；
     *  改前还额外写死了 10 这个硬上限，已按用户 2026-09-13 的要求去掉）。
     *  唯一的例外是**顶部元素**：标题栏
     *  整行（标题文字 + 旁边翻页键的占位）的加列优先级高于本上限，放不下时会继续
     *  创建列把窗口撑宽 —— 标签条随后也能用上这些多出来的列（即标签数**临时突破**
     *  列上限）。
     *
     *  <p>GUI：无 tooltip，只接受整型（运行时会夹紧到 1–64）；位置在「配方区行
     *  上限」下方。 */
    @ConfigEntry.Category("keybindings")
    public int recipeViewerColumnLimit = 7;

    // -- 「快捷键&数值」页底部的数值项（分节标题「音效与动画」由 ConfigTipsHelper 注入）----

    /** 翻页音效音量（0.0–1.0，默认 1.0 = 原生音量），可在「音乐与声音」界面调节。 */
    @ConfigEntry.Category("keybindings")
    public float pageFlipVolume = 1.0f;

    /** 配方书翻页动画时长（秒）。 */
    @ConfigEntry.Category("keybindings")
    public float pageAnimationDuration = 0.5f;

    /**
     * 翻页音效：BRBE 全部界面翻页时播放的声音资源 ID（如
     * {@code minecraft:ui.button.click}）。
     *
     * <p>这里是字符串，填任意 ID 都不会报错——未注册/非法的 ID 在播放时回退默认
     * 音效（见 {@link com.alonie.brbe.util.PageFlipSound}）；带校验的设置途径是
     * {@code /brbe set pagesound <声音ID>}。</p>
     */
    @ConfigEntry.Category("keybindings")
    public String pageFlipSound = com.alonie.brbe.util.PageFlipSound.DEFAULT_ID;

    // -- 杂项（miscellaneous 标签）--------------------------------------------

    /** 隐藏配置界面的Tips：打开时隐藏「功能」页面顶部的轮循提示行。 */
    @ConfigEntry.Category("miscellaneous")
    @ConfigEntry.Gui.Tooltip
    public boolean hideConfigTips = false;

    /** 隐藏暂停菜单的配置界面入口：打开时暂停菜单图标行的 BRBE 配置按钮不再显示。默认关。 */
    @ConfigEntry.Category("miscellaneous")
    public boolean hidePauseMenuConfigEntry = false;

    /** 隐藏配置界面顶部的标题区域：**默认开** —— 关掉它就恢复 Cloth 原来的标题带
     *  （y=18 的界面标题文字 + 上方那 41px 留白）。无 tooltip。 */
    @ConfigEntry.Category("miscellaneous")
    public boolean hideConfigTitleBand = true;

    /** 隐藏配置界面两侧的文字：打开时左右两条竖排装饰文字（{@code ConfigScreenSideText}）
     *  不再绘制。无 tooltip，默认关。 */
    @ConfigEntry.Category("miscellaneous")
    public boolean hideConfigSideText = false;

    // -- Inner config class ---------------------------------------------------

    public static class PageAnimation {
        public boolean pageAnimationEnabled = true;
    }

    public static class RecipeBookIsPain implements ConfigData {
        /** 主开关（留在「功能」页）。原 `@PrefixText`「§eRecipe Book Is Pain」黄字行
         *  已改为「界面」页里的独立纯文字行，与下面两个子开关一起搬过去 —— 见
         *  {@code ConfigTipsHelper.relocateRbipEntries}。字段仍留在本子对象里，
         *  故 TOML 路径保持 {@code [rbip]} 不变（玩家配置不失效）。 */
        @ConfigEntry.Gui.Tooltip
        public boolean enableRecipeBookIsPain = true;

        /** 「标签模式」（默认「创造模式物品栏」）：用 Cloth 的**枚举切换按钮**（点一下切到下一档、
         *  到底回绕）代替原来的布尔开关 —— 三档各一套实现，见下。
         *
         *  <ul>
         *    <li>{@link TabMode#CREATIVE_TABS}：每个创造标签一个配方书标签（原 RBIP 行为）；</li>
         *    <li>{@link TabMode#NAMESPACE}（界面显示名「命名空间」）：搜索标签 + **配方 id 的
         *        命名空间**各一个标签（模组 / 数据包各按自己的命名空间归组，图标 = 该标签内
         *        某个配方的产物（稳定伪随机），原版命名空间是草方块），tooltip = 命名空间对应的
         *        模组名 / 命名空间本身。**兼容性最好**：数据包沿用 {@code minecraft} 命名空间时
         *        自然并入原版标签，不会多出一堆零散标签。</li>
         *    <li>{@link TabMode#DATAPACK}（界面显示名「数据包」）：搜索标签 + 每个**来源数据包**
         *        一个标签 —— 按配方来自哪个数据包归组（原版 / 每个模组自带数据包 / 每个启用的
         *        数据包），图标与 tooltip 同上但 tooltip 是数据包名字。**只在单机 / 局域网主机
         *        可用**：联机时客户端拿不到服务端的包列表，该档自动按「命名空间」跑，配置界面
         *        也把它藏起来，离开服务器恢复（用户 2026-10-01 定）。</li>
         *  </ul>
         *
         *  <p>历史：2026-09-30 引入第二档（当时叫「紧凑型标签」、枚举常量 {@code COMPACT}）→
         *  2026-10-01 改名「命名空间」→ 同日转向「数据包」（按来源包归组）→ 同日又拆回三档。
         *  旧 TOML 值 {@code tabMode = "COMPACT"} 在配置加载前由 {@code BetterRecipeBook}
         *  的预处理改写成 {@code "NAMESPACE"}。</p>
         *
         *  <p>按钮上的档位名来自 {@code text.autoconfig.brbe.option.rbip.tabMode.<常量名>}。</p> */
        // 四行说明：Cloth 的 @Tooltip(count = N) 会读 …@Tooltip[0] / [1] / [2] / [3]（不是换行符）。
        // ⚠️ 这一项的 GUI 由 {@code TabModeGuiRegistrar} 的 transformer 接管（联机时隐藏数据包档），
        // 那里的 tooltip 也是按这 4 个键拼的；count 只影响"provider 被绕过"时的默认条目。
        @ConfigEntry.Gui.Tooltip(count = 4)
        @ConfigEntry.Gui.EnumHandler(option = ConfigEntry.Gui.EnumHandler.EnumDisplayOption.BUTTON)
        public TabMode tabMode = TabMode.CREATIVE_TABS;

        /** 旧字段（2026-09-30 起由 {@link #tabMode} 取代）：**只用于迁移** ——
         *  老配置里的 {@code compactTabs = true} 会在启动时被 {@link #migrateLegacyTabMode()}
         *  搬进 {@code tabMode = NAMESPACE} 并复位。界面上不显示，也没有别的读取点。 */
        @ConfigEntry.Gui.Excluded
        public boolean compactTabs = false;

        /** 标签模式的三档（实现 Cloth 的
         *  {@code SelectionListEntry.Translatable} 让切换按钮显示**本地化档位名**，
         *  否则按钮上会直接显示枚举常量名）。 */
        public enum TabMode implements me.shedaniel.clothconfig2.gui.entries.SelectionListEntry.Translatable {
            CREATIVE_TABS,
            /** 按**配方 id 的命名空间**归组（旧名 {@code COMPACT}「紧凑型标签」）。 */
            NAMESPACE,
            /** 按配方所属的**数据包**归组（需要服务端包列表 → 联机不可用）。 */
            DATAPACK;

            @Override
            public String getKey() {
                return "text.autoconfig.brbe.option.rbip.tabMode." + name();
            }
        }

        /**
         * **实际生效**的档位：联机（真正的服务器）时数据包档降级为命名空间档 ——
         * 客户端拿不到服务端的包列表，数据包档在联机下必然只有一个「服务器」标签，
         * 不如按命名空间正常分类（用户 2026-10-01 定）。存储值不变，离开服务器自动恢复。
         */
        public TabMode effectiveTabMode() {
            if (tabMode == TabMode.DATAPACK
                    && com.alonie.brbe.util.WorldScopedStore.onRemoteServer()) {
                return TabMode.NAMESPACE;
            }
            return tabMode;
        }

        /** 便捷判断：当前是不是**命名空间**标签模式（核心代码如 {@code TabPinManager} 用它，
         *  避免直接耦合枚举）。 */
        public boolean namespaceModeEnabled() {
            return effectiveTabMode() == TabMode.NAMESPACE;
        }

        /** 便捷判断：当前是不是**数据包**标签模式（联机时恒 false，见 {@link #effectiveTabMode()}）。 */
        public boolean datapackModeEnabled() {
            return effectiveTabMode() == TabMode.DATAPACK;
        }

        /** 联机时数据包档被隐藏（配置界面据此收窄可选档位）。 */
        public boolean datapackModeHidden() {
            return tabMode == TabMode.DATAPACK
                    && com.alonie.brbe.util.WorldScopedStore.onRemoteServer();
        }

        /** 老配置迁移（只搬一次）：布尔 {@code compactTabs = true} → {@code tabMode = NAMESPACE}
         *  （那个布尔开关当年的语义就是"按命名空间收标签"，与数据包档无关）。
         *  枚举字符串的旧值（{@code "COMPACT"}）在配置加载前就被改写了，见
         *  {@code BetterRecipeBook} 的 TOML 预处理。
         *  @return 是否发生了改动（true 时调用方需要保存配置） */
        public boolean migrateLegacyTabMode() {
            if (!compactTabs) return false;
            if (tabMode == TabMode.CREATIVE_TABS) {
                tabMode = TabMode.NAMESPACE;
            }
            compactTabs = false;
            return true;
        }

        public boolean enableTabPage = true;

        /** 隐藏翻页按钮：打开时 RBIP 标签栏的翻页按钮（书左侧的两个箭头）不再显示，
         *  其位置也不再吞掉点击；标签区域的滚轮翻页不受影响。默认关。 */
        public boolean hideTabPageButtons = false;
    }

    @Override
    public void validatePostLoad() {
        this.expandedRecipeBook = false;
    }
}
