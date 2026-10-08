package com.arnau.fusionmon.network;

import com.arnau.fusionmon.Fusionmon;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Servidor → cliente: "abre la pantalla de confirmación para separar esta fusión": la fusión, las dos partes tal como
 * saldrán (con su nivel de antes de recibir la experiencia) y la experiencia ganada como fusión, que recibe cada una.
 * roomForBody: si hay sitio para el cuerpo en el equipo o el PC; si no, se puede invertir pero no separar.
 */
public record OpenUnfuseScreenPayload(
        FusionPartView fusion, FusionPartView head, FusionPartView body, int experienceGained, boolean roomForBody
) implements CustomPacketPayload {

    public static final Type<OpenUnfuseScreenPayload> TYPE = new Type<>(Fusionmon.id("open_unfuse_screen"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenUnfuseScreenPayload> CODEC =
            StreamCodec.ofMember(OpenUnfuseScreenPayload::write, OpenUnfuseScreenPayload::read);

    private void write(RegistryFriendlyByteBuf buf) {
        fusion.write(buf);
        head.write(buf);
        body.write(buf);
        buf.writeVarInt(experienceGained);
        buf.writeBoolean(roomForBody);
    }

    // Java evalúa los argumentos de izquierda a derecha, así que se leen en el mismo orden en que se escribieron
    private static OpenUnfuseScreenPayload read(RegistryFriendlyByteBuf buf) {
        return new OpenUnfuseScreenPayload(
                FusionPartView.read(buf), FusionPartView.read(buf), FusionPartView.read(buf), buf.readVarInt(),
                buf.readBoolean());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
