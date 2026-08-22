/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.network.chat.Component
 */
package mezz.jei.api.runtime.config;

import mezz.jei.api.runtime.config.IJeiConfigValueSerializer;
import net.minecraft.network.chat.Component;

public interface IJeiConfigValue<T> {
    public String getName();

    public Component getLocalizedName();

    public Component getLocalizedDescription();

    public T getValue();

    public T getDefaultValue();

    public boolean set(T var1);

    public IJeiConfigValueSerializer<T> getSerializer();
}

