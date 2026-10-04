package com.arnau.fusionmon.network;

import com.arnau.fusionmon.Fusionmon;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Servidor → cliente: "abre la pantalla de confirmación para separar esta fusión". */
public record OpenUnfuseScreenPayload(
        Component fusedName,
        Component headName, int headLevel,
        Component bodyName, int bodyLevel,
        int experienceGained
) implements CustomPacketPayload {

    public static final Type<OpenUnfuseScreenPayload> TYPE = new Type<>(Fusionmon.id("open_unfuse_screen"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenUnfuseScreenPayload> CODEC =
            StreamCodec.ofMember(OpenUnfuseScreenPayload::write, OpenUnfuseScreenPayload::read);

    private void write(RegistryFriendlyByteBuf buf) {
        ComponentSerialization.STREAM_CODEC.encode(buf, fusedName);
        ComponentSerialization.STREAM_CODEC.encode(buf, headName);
        buf.writeVarInt(headLevel);
        ComponentSerialization.STREAM_CODEC.encode(buf, bodyName);
        buf.writeVarInt(bodyLevel);
        buf.writeVarInt(experienceGained);
    }

    private static OpenUnfuseScreenPayload read(RegistryFriendlyByteBuf buf) {
        return new OpenUnfuseScreenPayload(
                ComponentSerialization.STREAM_CODEC.decode(buf),
                ComponentSerialization.STREAM_CODEC.decode(buf), buf.readVarInt(),
                ComponentSerialization.STREAM_CODEC.decode(buf), buf.readVarInt(),
                buf.readVarInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
