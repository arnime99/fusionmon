package com.arnau.fusionmon.network;

import com.arnau.fusionmon.Fusionmon;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;

/**
 * Servidor → cliente: "abre el Fusion Album con estas fusiones descubiertas" (claves de FusionDiscovery, de la más
 * reciente a la más antigua). El cliente las convierte en especies y formas y las pinta él.
 */
public record OpenFusionAlbumPayload(List<String> discovered) implements CustomPacketPayload {

    public static final Type<OpenFusionAlbumPayload> TYPE = new Type<>(Fusionmon.id("open_fusion_album"));
    public static final StreamCodec<ByteBuf, OpenFusionAlbumPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), OpenFusionAlbumPayload::discovered,
            OpenFusionAlbumPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
