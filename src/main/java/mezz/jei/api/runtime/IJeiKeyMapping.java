/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.blaze3d.platform.InputConstants$Key
 *  net.minecraft.network.chat.Component
 */
package mezz.jei.api.runtime;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.network.chat.Component;

public interface IJeiKeyMapping {
    public boolean isActiveAndMatches(InputConstants.Key var1);

    public boolean isUnbound();

    public Component getTranslatedKeyMessage();
}

