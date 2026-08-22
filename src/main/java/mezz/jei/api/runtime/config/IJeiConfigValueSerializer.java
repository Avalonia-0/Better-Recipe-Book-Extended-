/*
 * Decompiled with CFR 0.152.
 */
package mezz.jei.api.runtime.config;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface IJeiConfigValueSerializer<T> {
    public String serialize(T var1);

    public IDeserializeResult<T> deserialize(String var1);

    public boolean isValid(T var1);

    public Optional<Collection<T>> getAllValidValues();

    public String getValidValuesDescription();

    public static interface IDeserializeResult<T> {
        public Optional<T> getResult();

        public List<String> getErrors();
    }
}

