package com.arnau.fusionmon.network;

import com.cobblemon.mod.common.pokemon.RenderablePokemon;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;

import java.util.ArrayList;
import java.util.List;

/**
 * Lo que se enseña de una fusión en la pantalla de confirmación.
 * baseStats en orden: PS, Ataque, Defensa, At. Esp., Def. Esp., Velocidad.
 * model: lo justo para pintarla (especie de la cabeza + aspects de fusión, ver FusionAspects), como hace
 * Cobblemon con los Pokémon de los menús.
 */
public record FusionPreview(Component name, List<Component> types, int level, List<Integer> baseStats,
                            RenderablePokemon model) {

    public void write(RegistryFriendlyByteBuf buf) {
        ComponentSerialization.STREAM_CODEC.encode(buf, name);
        buf.writeVarInt(types.size());
        for (Component type : types) {
            ComponentSerialization.STREAM_CODEC.encode(buf, type);
        }
        buf.writeVarInt(level);
        buf.writeVarInt(baseStats.size());
        for (int stat : baseStats) {
            buf.writeVarInt(stat);
        }
        model.saveToBuffer(buf);
    }

    public static FusionPreview read(RegistryFriendlyByteBuf buf) {
        Component name = ComponentSerialization.STREAM_CODEC.decode(buf);
        int typeCount = buf.readVarInt();
        List<Component> types = new ArrayList<>(typeCount);
        for (int i = 0; i < typeCount; i++) {
            types.add(ComponentSerialization.STREAM_CODEC.decode(buf));
        }
        int level = buf.readVarInt();
        int statCount = buf.readVarInt();
        List<Integer> baseStats = new ArrayList<>(statCount);
        for (int i = 0; i < statCount; i++) {
            baseStats.add(buf.readVarInt());
        }
        RenderablePokemon model = RenderablePokemon.Companion.loadFromBuffer(buf);
        return new FusionPreview(name, types, level, baseStats, model);
    }
}
