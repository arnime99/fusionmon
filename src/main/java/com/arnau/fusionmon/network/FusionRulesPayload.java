package com.arnau.fusionmon.network;

import com.arnau.fusionmon.Fusionmon;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Servidor → cliente, al conectarse: las opciones del servidor que cambian lo que hace el cliente por su cuenta. Los
 * stats del Fusion Album (si no, el Album enseñaría unos stats y el combate usaría otros) y si el servidor obliga a
 * todos a ver las fusiones en modo colores (interruptor de emergencia, ver FusionConfig).
 */
public record FusionRulesPayload(int statBonusPercent, boolean forceColorsMode) implements CustomPacketPayload {

    public static final Type<FusionRulesPayload> TYPE = new Type<>(Fusionmon.id("rules"));
    public static final StreamCodec<ByteBuf, FusionRulesPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, FusionRulesPayload::statBonusPercent,
            ByteBufCodecs.BOOL, FusionRulesPayload::forceColorsMode,
            FusionRulesPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
