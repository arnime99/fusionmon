package com.arnau.fusionmon.network;

import com.arnau.fusionmon.Fusionmon;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Cliente → servidor: qué se hace con la fusión pendiente (separarla, invertirla o nada). */
public record UnfuseChoicePayload(Action action) implements CustomPacketPayload {

    public enum Action {
        CANCEL,
        SPLIT,
        /** Abre la pantalla de fusión con las partes al revés (ver FusionSelection). */
        REVERSE
    }

    public static final Type<UnfuseChoicePayload> TYPE = new Type<>(Fusionmon.id("unfuse_choice"));
    public static final StreamCodec<RegistryFriendlyByteBuf, UnfuseChoicePayload> CODEC =
            StreamCodec.ofMember(UnfuseChoicePayload::write, UnfuseChoicePayload::read);

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(action.ordinal());
    }

    private static UnfuseChoicePayload read(RegistryFriendlyByteBuf buf) {
        int index = buf.readVarInt();
        Action[] actions = Action.values();
        // Un valor que no existe (cliente trucado o de otra versión) cuenta como cancelar
        return new UnfuseChoicePayload(index >= 0 && index < actions.length ? actions[index] : Action.CANCEL);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
