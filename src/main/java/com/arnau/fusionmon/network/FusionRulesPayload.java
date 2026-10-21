package com.arnau.fusionmon.network;

import com.arnau.fusionmon.Fusionmon;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Servidor → cliente, al conectarse: las opciones del servidor que cambian lo que el cliente calcula por su cuenta
 * (los stats del Fusion Album). Si no, el Album enseñaría unos stats y el combate usaría otros.
 */
public record FusionRulesPayload(int statBonusPercent) implements CustomPacketPayload {

    public static final Type<FusionRulesPayload> TYPE = new Type<>(Fusionmon.id("rules"));
    public static final StreamCodec<ByteBuf, FusionRulesPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, FusionRulesPayload::statBonusPercent,
            FusionRulesPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
