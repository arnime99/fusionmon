package com.arnau.fusionmon.network;

import com.arnau.fusionmon.Fusionmon;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Servidor → cliente: "abre la pantalla de confirmación con estas opciones".
 * A = primer Pokémon elegido, B = segundo. Se mandan las dos vistas previas
 * (A cabeza + B cuerpo, y al revés) para que el botón Intercambiar no necesite preguntar al servidor.
 */
public record OpenFusionScreenPayload(
        Component nameA, Component nameB,
        FusionPreview preview, FusionPreview swappedPreview,
        Component natureA, Component natureB,
        Component abilityA, Component abilityB
) implements CustomPacketPayload {

    public static final Type<OpenFusionScreenPayload> TYPE = new Type<>(Fusionmon.id("open_fusion_screen"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenFusionScreenPayload> CODEC =
            StreamCodec.ofMember(OpenFusionScreenPayload::write, OpenFusionScreenPayload::read);

    private void write(RegistryFriendlyByteBuf buf) {
        ComponentSerialization.STREAM_CODEC.encode(buf, nameA);
        ComponentSerialization.STREAM_CODEC.encode(buf, nameB);
        preview.write(buf);
        swappedPreview.write(buf);
        ComponentSerialization.STREAM_CODEC.encode(buf, natureA);
        ComponentSerialization.STREAM_CODEC.encode(buf, natureB);
        ComponentSerialization.STREAM_CODEC.encode(buf, abilityA);
        ComponentSerialization.STREAM_CODEC.encode(buf, abilityB);
    }

    private static OpenFusionScreenPayload read(RegistryFriendlyByteBuf buf) {
        return new OpenFusionScreenPayload(
                ComponentSerialization.STREAM_CODEC.decode(buf),
                ComponentSerialization.STREAM_CODEC.decode(buf),
                FusionPreview.read(buf),
                FusionPreview.read(buf),
                ComponentSerialization.STREAM_CODEC.decode(buf),
                ComponentSerialization.STREAM_CODEC.decode(buf),
                ComponentSerialization.STREAM_CODEC.decode(buf),
                ComponentSerialization.STREAM_CODEC.decode(buf));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
