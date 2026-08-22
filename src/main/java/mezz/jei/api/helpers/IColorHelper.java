/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.renderer.texture.TextureAtlasSprite
 *  net.minecraft.world.item.ItemStack
 */
package mezz.jei.api.helpers;

import java.util.List;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.item.ItemStack;

public interface IColorHelper {
    public List<Integer> getColors(TextureAtlasSprite var1, int var2, int var3);

    public List<Integer> getColors(ItemStack var1, int var2);

    public String getClosestColorName(int var1);
}

