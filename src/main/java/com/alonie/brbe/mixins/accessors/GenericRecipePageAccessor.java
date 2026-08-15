package com.alonie.brbe.mixins.accessors;

import com.alonie.brbe.generic.GenericRecipePage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(GenericRecipePage.class)
public interface GenericRecipePageAccessor {

    @Accessor("currentPage")
    int getCurrentPage();

    @Accessor("currentPage")
    void setCurrentPage(int currentPage);

    @Accessor("totalPages")
    int getTotalPages();
}
