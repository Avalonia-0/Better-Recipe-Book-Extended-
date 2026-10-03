package com.alonie.brbe.mixins.localcache;

import com.alonie.brbe.cache.RecipeViewerIndex;
import com.alonie.brbe.cache.VanillaRecipeCache;
import com.alonie.brbe.mixins.accessors.ClientRecipeBookAccessor;
import com.alonie.brbe.util.RecipeBookState;
import com.alonie.brbe.util.RecipeCraftingIndex;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.world.item.crafting.ExtendedRecipeBookCategory;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.alonie.brbe.util.BrbeLogger;

/**
 * Injects locally-cached vanilla recipe entries into ClientRecipeBook at
 * two strategic points — constructor and rebuildCollections — with different
 * strategies at each point.
 *
 * <h3>Constructor: inject all</h3>
 * During client init, no server recipe data exists yet.  We inject ALL
 * valid cached entries unconditionally.  This covers servers that never
 * send recipe packets (Hypixel).
 *
 * <h3>rebuildCollections: complement</h3>
 * When the server sends recipe packets (singleplayer, most servers),
 * rebuildCollections is called from refreshRecipeBook after server entries
 * have been added.  At this point we only inject cache entries whose
 * result items are NOT already covered by the server.
 */
@Mixin(ClientRecipeBook.class)
public abstract class ClientRecipeBookMixin {

    @Shadow
    private Map<RecipeDisplayId, RecipeDisplayEntry> known;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void brbe$onConstruct(CallbackInfo ci) {
        if (!VanillaRecipeCache.hasEntries()) return;
        ClientRecipeBook self = (ClientRecipeBook) (Object) this;
        VanillaRecipeCache.detectAndInject(self, known);
        self.rebuildCollections();
    }

    @Inject(method = "rebuildCollections", at = @At("HEAD"))
    private void brbe$preRebuildInjectCache(CallbackInfo ci) {
        RecipeBookState.beginCycle((ClientRecipeBook) (Object) this, known);
    }

    @Inject(method = "rebuildCollections", at = @At("RETURN"))
    private void brbe$postRebuildEndCycle(CallbackInfo ci) {
        BrbeLogger.log("BRBE-CACHE", "rebuild RETURN — known={}", known.size());
        RecipeBookState.endCycle();
        RecipeViewerIndex.rebuildEngine();
        // Collection objects were recreated: rebuild the incremental-canCraft
        // index so the next inventory pass can skip unaffected collections.
        ClientRecipeBook self = (ClientRecipeBook) (Object) this;
        RecipeCraftingIndex.rebuild(brbe$displayedCollections(self));
    }

    /**
     * 索引的输入必须是"配方书实际会遍历到的集合"。
     *
     * <p>vanilla {@code getCollections()} 返回的是 {@code allCollections}
     * （vanilla 自己重建时生成的扁平表），而 RBIP 会把 {@code collectionsByTab} 换成
     * <b>新建的</b> {@code RecipeCollection} 对象（每个创造标签一个；紧凑模式下原版
     * 标签页用的也是新对象）—— 这些对象不在 {@code allCollections} 里。只索引
     * {@code allCollections} 会让 {@link RecipeCraftingIndex#shouldSkip} 把它们误判为
     * "不受库存变化影响" → 取消 {@code selectRecipes} → 可合成状态冻结
     * （2026-09-30 用户反馈：选中 RBIP 标签页时用最后一个铁锭合成铁粒后，该配方仍显示
     * 可合成、材料却全部标记缺失；搜索页用的是 vanilla 集合，所以看不出问题）。</p>
     *
     * <p>RBIP 侧（{@code rbip$refreshCreativeGroups}）在改写完成后也会重建一次索引，
     * 两者互为兜底：无论两个 mixin 的注入顺序如何，索引都与最终显示用的集合一致；
     * 万一仍有遗漏，{@code shouldSkip} 对"索引不认识"的集合一律不跳过。</p>
     */
    @Unique
    private static List<RecipeCollection> brbe$displayedCollections(ClientRecipeBook book) {
        Set<RecipeCollection> seen = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        List<RecipeCollection> out = new ArrayList<>();
        for (RecipeCollection collection : book.getCollections()) {
            if (collection != null && seen.add(collection)) out.add(collection);
        }
        Map<ExtendedRecipeBookCategory, List<RecipeCollection>> byTab =
                ((ClientRecipeBookAccessor) book).brbe$getCollectionsByTab();
        if (byTab != null) {
            for (List<RecipeCollection> collections : byTab.values()) {
                if (collections == null) continue;
                for (RecipeCollection collection : collections) {
                    if (collection != null && seen.add(collection)) out.add(collection);
                }
            }
        }
        return out;
    }
}
