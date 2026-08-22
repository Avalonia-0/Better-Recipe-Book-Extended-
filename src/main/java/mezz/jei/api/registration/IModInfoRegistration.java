/*
 * Decompiled with CFR 0.152.
 */
package mezz.jei.api.registration;

import java.util.Collection;
import java.util.Set;

public interface IModInfoRegistration {
    public void addModAliases(String var1, Collection<String> var2);

    default public void addModAliases(String modId, String ... aliases) {
        this.addModAliases(modId, Set.of(aliases));
    }
}

