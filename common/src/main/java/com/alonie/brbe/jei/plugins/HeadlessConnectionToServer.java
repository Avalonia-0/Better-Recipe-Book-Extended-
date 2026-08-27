package com.alonie.brbe.jei.plugins;

import mezz.jei.common.network.IConnectionToServer;
import mezz.jei.common.network.packets.PlayToServerPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 1.21.1 无头连接：headless 模式下无真实服务器 JEI 通道——所有查询返回默认
 * （服务器有 JEI=true 免警告、无法发包、不同加载器）。对应 1.21.11 的
 * HeadlessConnectionToServer（其依赖 27.4 fabric network 包，1.21.1 自研）。
 */
public final class HeadlessConnectionToServer implements IConnectionToServer {

    @Override
    public boolean isJeiOnServer() {
        // headless 核心本身就是"服务器侧 JEI"（配方来自同一客户端同步）
        return true;
    }

    @Override
    public boolean isSameModLoader() {
        return true;
    }

    @Override
    public boolean canSendPacket(CustomPacketPayload.Type<?> packetType) {
        return false;
    }

    @Override
    public <T extends PlayToServerPacket<T>> void sendPacketToServer(T packet) {
        // no-op：headless 无服务器通道
    }
}
