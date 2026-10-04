package com.arnau.fusionmon.network;

import com.arnau.fusionmon.Fusionmon;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Cliente → servidor: ¿se separa la fusión pendiente o no? */
public record UnfuseChoicePayload(boolean accepted) implements CustomPacketPayload {

    public static final Type<UnfuseChoicePayload> TYPE = new Type<>(Fusionmon.id("unfuse_choice"));
    public static final StreamCodec<RegistryFriendlyByteBuf, UnfuseChoicePayload> CODEC =
            StreamCodec.ofMember(UnfuseChoicePayload::write, UnfuseChoicePayload::read);

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeBoolean(accepted);
    }

    private static UnfuseChoicePayload read(RegistryFriendlyByteBuf buf) {
        return new UnfuseChoicePayload(buf.readBoolean());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
