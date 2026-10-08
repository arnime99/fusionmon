package com.arnau.fusionmon.network;

import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.RenderablePokemon;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Lo que se enseña de uno de los dos Pokémon de una fusión (en las pantallas de fusionar y separar): nombre, nivel,
 * tipos y lo justo para pintarlo en 3D. El cliente no tiene el Pokémon entero, solo esto.
 */
public record FusionPartView(Component name, int level, List<Component> types, RenderablePokemon model) {

    /** Solo servidor. Sin objeto: al fusionar o separar, los objetos vuelven al jugador. */
    public static FusionPartView of(Pokemon pokemon) {
        List<Component> types = new ArrayList<>();
        for (ElementalType type : pokemon.getTypes()) {
            types.add(type.getDisplayName());
        }
        return new FusionPartView(pokemon.getDisplayName(false), pokemon.getLevel(), types,
                new RenderablePokemon(pokemon.getSpecies(), pokemon.getAspects(), ItemStack.EMPTY));
    }

    public void write(RegistryFriendlyByteBuf buf) {
        ComponentSerialization.STREAM_CODEC.encode(buf, name);
        buf.writeVarInt(level);
        buf.writeVarInt(types.size());
        for (Component type : types) {
            ComponentSerialization.STREAM_CODEC.encode(buf, type);
        }
        model.saveToBuffer(buf);
    }

    public static FusionPartView read(RegistryFriendlyByteBuf buf) {
        Component name = ComponentSerialization.STREAM_CODEC.decode(buf);
        int level = buf.readVarInt();
        int typeCount = buf.readVarInt();
        List<Component> types = new ArrayList<>(typeCount);
        for (int i = 0; i < typeCount; i++) {
            types.add(ComponentSerialization.STREAM_CODEC.decode(buf));
        }
        RenderablePokemon model = RenderablePokemon.Companion.loadFromBuffer(buf);
        return new FusionPartView(name, level, types, model);
    }
}
