/*
 * Decompiled with CFR 0.152.
 */
package mezz.jei.api.runtime.config;

import java.util.List;
import mezz.jei.api.runtime.config.IJeiConfigValueSerializer;

public interface IJeiConfigListValueSerializer<T>
extends IJeiConfigValueSerializer<List<T>> {
    public IJeiConfigValueSerializer<T> getListValueSerializer();
}

