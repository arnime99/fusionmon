package com.arnau.fusionmon.network;

import com.arnau.fusionmon.Fusionmon;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Servidor → cliente: la fusión (o la inversión) que has aceptado se ha hecho. inWorld = se anima en el mundo (los dos
 * Pokémon estaban fuera y cerca, ver FusionWorldAnimation); si no, el cliente pone la animación de pantalla.
 * Si el servidor no fusiona (sin inyector, el equipo ha cambiado...) no manda nada y no hay animación.
 */
public record FusionResultPayload(boolean inWorld) implements CustomPacketPayload {

    public static final Type<FusionResultPayload> TYPE = new Type<>(Fusionmon.id("fusion_result"));
    public static final StreamCodec<ByteBuf, FusionResultPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, FusionResultPayload::inWorld,
            FusionResultPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
