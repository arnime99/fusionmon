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
        Component natureEffectA, Component natureEffectB,
        Component abilityA, Component abilityB,
        Component abilityDescriptionA, Component abilityDescriptionB
) implements CustomPacketPayload {

    public static final Type<OpenFusionScreenPayload> TYPE = new Type<>(Fusionmon.id("open_fusion_screen"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenFusionScreenPayload> CODEC =
            StreamCodec.ofMember(OpenFusionScreenPayload::write, OpenFusionScreenPayload::read);

    private void write(RegistryFriendlyByteBuf buf) {
        writeComponent(buf, nameA);
        writeComponent(buf, nameB);
        preview.write(buf);
        swappedPreview.write(buf);
        writeComponent(buf, natureA);
        writeComponent(buf, natureB);
        writeComponent(buf, natureEffectA);
        writeComponent(buf, natureEffectB);
        writeComponent(buf, abilityA);
        writeComponent(buf, abilityB);
        writeComponent(buf, abilityDescriptionA);
        writeComponent(buf, abilityDescriptionB);
    }

    // Java evalúa los argumentos de izquierda a derecha, así que se leen en el mismo orden en que se escribieron
    private static OpenFusionScreenPayload read(RegistryFriendlyByteBuf buf) {
        return new OpenFusionScreenPayload(
                readComponent(buf), readComponent(buf),
                FusionPreview.read(buf), FusionPreview.read(buf),
                readComponent(buf), readComponent(buf),
                readComponent(buf), readComponent(buf),
                readComponent(buf), readComponent(buf),
                readComponent(buf), readComponent(buf));
    }

    private static void writeComponent(RegistryFriendlyByteBuf buf, Component component) {
        ComponentSerialization.STREAM_CODEC.encode(buf, component);
    }

    private static Component readComponent(RegistryFriendlyByteBuf buf) {
        return ComponentSerialization.STREAM_CODEC.decode(buf);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
