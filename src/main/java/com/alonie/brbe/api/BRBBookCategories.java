package com.alonie.brbe.api;

import com.google.common.collect.ImmutableList;
import com.alonie.brbe.util.BRBHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BRBBookCategories {
    private static final Map<BRBHelper.Book, List<Category>> categories = new HashMap<>();

    @Nullable
    public static List<Category> getCategories(BRBHelper.Book book) {
        return categories.get(book);
    }

    private static Category createCategory(BRBHelper.Book book, Category.Type type, ItemStack... entries) {
        Category category = new Category(type, entries);
        categories.putIfAbsent(book, new ArrayList<>());

        categories.get(book).add(category);

        return category;
    }

    public static Category createCategory(BRBHelper.Book book, @NotNull ItemStack... entries) {
        return createCategory(book, Category.Type.OTHER, entries);
    }

    public static Category createSearch(BRBHelper.Book book) {
        return createCategory(book, Category.Type.SEARCH, new ItemStack(Items.COMPASS));
    }

    /**
     * **不登记为标签页**的"搜索/全部"类别（用户 2026-09-28 诉求：锻造台去掉"搜索"页，
     * 只留「升级模板 / 纹饰模板」两页）。
     *
     * <p>类别对象本身仍然有用——锻造台的替代配方组浮层拿 {@code SMITHING_SEARCH} 当
     * {@code getResult(registryAccess, category)} 的类别参数（锻造配方的产物与类别无关，
     * 传哪个都一样），{@code shouldInclude(...)} 里也留着"搜索页 = 全部配方"的判定。
     * 只是**不进 {@link #getCategories} 的标签列表** → 界面上没有这一页。</p>
     */
    public static Category createUnlistedSearch() {
        return new Category(Category.Type.SEARCH, new ItemStack(Items.COMPASS));
    }

    public static class Category {
        private final List<ItemStack> itemIcons;
        private final Type type;
        private net.minecraft.network.chat.Component title;

        Category(Type type, ItemStack... entries) {
            this.itemIcons = ImmutableList.copyOf(entries);
            this.type = type;
        }

        public List<ItemStack> getItemIcons() {
            return this.itemIcons;
        }

        public Type getType() {
            return this.type;
        }

        /** 标签页悬停标题（tooltip）；null = 不显示。 */
        public void setTitle(net.minecraft.network.chat.Component title) {
            this.title = title;
        }

        @org.jetbrains.annotations.Nullable
        public net.minecraft.network.chat.Component getTitle() {
            return this.title;
        }

        public enum Type {
            SEARCH,
            OTHER
        }
    }
}
