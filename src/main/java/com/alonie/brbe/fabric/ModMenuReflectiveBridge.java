package com.alonie.brbe.fabric;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.config.BrbeConfig;
import com.alonie.brbe.util.ConfigTipsHelper;
import net.minecraft.client.gui.screens.Screen;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import com.alonie.brbe.util.BrbeLogger;

/**
 * Bridges BRBE's Cloth Config screen into ModMenu using reflection.
 *
 * <p>This avoids a compile-time dependency on ModMenu, whose jar uses
 * Fabric intermediary mappings that conflict with loom-no-remap (Mojmap).
 *
 * <p>At runtime, when ModMenu is present, this class:
 * <ol>
 *   <li>Creates a dynamic proxy for {@code ModMenuApi}
 *   <li>Injects our {@code ConfigScreenFactory} into ModMenu's internal
 *       {@code configScreenFactories} map via reflection
 * </ol>
 */
public final class ModMenuReflectiveBridge {

    private static final String MODMENU_CLASS = "com.terraformersmc.modmenu.ModMenu";
    private static final String FACTORY_INTERFACE = "com.terraformersmc.modmenu.api.ConfigScreenFactory";
    private static final String API_INTERFACE = "com.terraformersmc.modmenu.api.ModMenuApi";

    private ModMenuReflectiveBridge() {
    }

    /**
     * Called during mod initialisation.  Safe to call even when ModMenu
     * is absent — does nothing in that case.
     */
    public static void register() {
        try {
            // 1. Check whether ModMenu is loaded
            Class<?> modMenuClass = Class.forName(MODMENU_CLASS);
            Class<?> factoryInterface = Class.forName(FACTORY_INTERFACE);

            // 2. Create a ConfigScreenFactory proxy whose create(Screen) method
            //    returns our Cloth Config screen.
            Object factory = Proxy.newProxyInstance(
                    factoryInterface.getClassLoader(),
                    new Class<?>[]{factoryInterface},
                    (Object proxy, Method method, Object[] args) -> {
                        if ("create".equals(method.getName()) && args.length == 1) {
                            // 走 ConfigTipsHelper：ModMenu 的配置按钮同样要拿到整理过的界面
                            // （直接调 AutoConfigClient 会得到未整理的字段声明顺序）
                            return ConfigTipsHelper.buildConfigScreen(BrbeConfig.class, (Screen) args[0]);
                        }
                        // Default method handling (equals, hashCode, toString)
                        if (method.getDeclaringClass() == Object.class) {
                            return method.invoke(proxy, args);
                        }
                        return null;
                    }
            );

            // 3. Inject into ModMenu.configScreenFactories
            java.lang.reflect.Field factoriesField = modMenuClass.getDeclaredField("configScreenFactories");
            factoriesField.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> factories = (java.util.Map<String, Object>) factoriesField.get(null);
            factories.put(BetterRecipeBook.MOD_ID, factory);

            BrbeLogger.log("BRBE-MODMENU", "Registered config screen via reflection bridge");
        } catch (ClassNotFoundException e) {
            // ModMenu not installed — nothing to do
            BrbeLogger.log("BRBE-MODMENU", "Not detected, skipping bridge");
        } catch (ReflectiveOperationException e) {
            BetterRecipeBook.LOGGER.warn("[ModMenu] Failed to register config screen", e);
        }
    }
}
