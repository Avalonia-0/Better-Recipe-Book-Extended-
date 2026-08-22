/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.blaze3d.platform.InputConstants$Key
 *  net.minecraft.client.KeyMapping
 *  net.minecraft.client.input.InputWithModifiers
 *  net.minecraft.client.input.InputWithModifiers$Modifiers
 */
package mezz.jei.api.gui.inputs;

import com.mojang.blaze3d.platform.InputConstants;
import mezz.jei.api.runtime.IJeiKeyMapping;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.input.InputWithModifiers;

public interface IJeiUserInput {
    public InputConstants.Key getKey();

    @InputWithModifiers.Modifiers
    public int getModifiers();

    public InputWithModifiers getInputWithModifiers();

    public boolean isSimulate();

    public boolean is(KeyMapping var1);

    public boolean is(IJeiKeyMapping var1);
}

