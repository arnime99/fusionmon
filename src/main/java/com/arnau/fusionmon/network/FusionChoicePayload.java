package com.arnau.fusionmon.network;

import com.arnau.fusionmon.Fusionmon;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Cliente → servidor: lo que el jugador ha decidido en la pantalla de confirmación.
 * "FromB" = la opción del segundo Pokémon elegido; si no, la del primero.
 * El servidor no se fía de esto a ciegas: vuelve a comprobar el equipo antes de fusionar.
 */
public record FusionChoicePayload(boolean accepted, boolean swapped, boolean natureFromB, boolean abilityFromB)
        implements CustomPacketPayload {

    public static final Type<FusionChoicePayload> TYPE = new Type<>(Fusionmon.id("fusion_choice"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FusionChoicePayload> CODEC =
            StreamCodec.ofMember(FusionChoicePayload::write, FusionChoicePayload::read);

    public static FusionChoicePayload cancel() {
        return new FusionChoicePayload(false, false, false, false);
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeBoolean(accepted);
        buf.writeBoolean(swapped);
        buf.writeBoolean(natureFromB);
        buf.writeBoolean(abilityFromB);
    }

    private static FusionChoicePayload read(RegistryFriendlyByteBuf buf) {
        return new FusionChoicePayload(buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
