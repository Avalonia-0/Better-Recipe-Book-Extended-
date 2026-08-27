package com.alonie.brbe.jei.plugins;

import mezz.jei.common.input.keys.IJeiKeyMappingBuilder;
import mezz.jei.common.input.keys.IJeiKeyMappingCategoryBuilder;
import mezz.jei.common.input.keys.IJeiKeyMappingInternal;
import mezz.jei.common.input.keys.JeiKeyConflictContext;
import mezz.jei.common.input.keys.JeiKeyModifier;

/**
 * 1.21.1 无头键位 stub：headless 模式不注册任何 JEI 键位，平台侧
 * IPlatformInputHelper#createKeyMappingCategoryBuilder 返回此 stub
 * （键位分类器从未被使用——嵌入式核心不注册键位，对应官方 JEI 的
 * FabricJeiKeyMappingCategoryBuilder / ForgeJeiKeyMappingCategoryBuilder）。
 */
public final class BrbeHeadlessKeyMappingStubs {

    private BrbeHeadlessKeyMappingStubs() {}

    public static IJeiKeyMappingCategoryBuilder categoryBuilder() {
        return description -> new IJeiKeyMappingBuilder() {
            @Override
            public IJeiKeyMappingBuilder setContext(JeiKeyConflictContext context) {
                return this;
            }

            @Override
            public IJeiKeyMappingBuilder setModifier(JeiKeyModifier modifier) {
                return this;
            }

            @Override
            public IJeiKeyMappingInternal buildMouseLeft() {
                return UNBOUND;
            }

            @Override
            public IJeiKeyMappingInternal buildMouseRight() {
                return UNBOUND;
            }

            @Override
            public IJeiKeyMappingInternal buildMouseMiddle() {
                return UNBOUND;
            }

            @Override
            public IJeiKeyMappingInternal buildKeyboardKey(int key) {
                return UNBOUND;
            }

            @Override
            public IJeiKeyMappingInternal buildUnbound() {
                return UNBOUND;
            }
        };
    }

    private static final IJeiKeyMappingInternal UNBOUND = new IJeiKeyMappingInternal() {
        @Override
        public boolean isActiveAndMatches(com.mojang.blaze3d.platform.InputConstants.Key key) {
            return false;
        }

        @Override
        public boolean isUnbound() {
            return true;
        }

        @Override
        public net.minecraft.network.chat.Component getTranslatedKeyMessage() {
            return net.minecraft.network.chat.Component.literal("");
        }

        @Override
        public boolean isDown() {
            return false;
        }

        @Override
        public IJeiKeyMappingInternal register(java.util.function.Consumer<net.minecraft.client.KeyMapping> registerMethod) {
            return this;
        }
    };
}
