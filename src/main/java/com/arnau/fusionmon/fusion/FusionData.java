package com.arnau.fusionmon.fusion;

import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;

/**
 * Lee y escribe los datos de fusión dentro del Pokémon visible.
 *
 * Cobblemon da a cada Pokémon un CompoundTag libre para mods (persistentData) que se guarda
 * con él en el equipo, el PC, los intercambios... Ahí guardamos los dos originales completos:
 *
 * persistentData
 *  └─ "fusionmon"
 *      ├─ "version": 1
 *      ├─ "head": Pokémon A tal como era antes de fusionar
 *      └─ "body": Pokémon B tal como era antes de fusionar
 */
public final class FusionData {

    private static final String KEY = "fusionmon";
    private static final String VERSION = "version";
    private static final String HEAD = "head";
    private static final String BODY = "body";
    private static final int CURRENT_VERSION = 1;

    private FusionData() {
    }

    public static boolean isFusion(Pokemon pokemon) {
        return pokemon.getPersistentData().contains(KEY);
    }

    public static void write(Pokemon visible, Pokemon head, Pokemon body, RegistryAccess registryAccess) {
        CompoundTag data = new CompoundTag();
        data.putInt(VERSION, CURRENT_VERSION);
        data.put(HEAD, head.saveToNBT(registryAccess, new CompoundTag()));
        data.put(BODY, body.saveToNBT(registryAccess, new CompoundTag()));

        visible.getPersistentData().put(KEY, data);
        // Avisa a Cobblemon de que el Pokémon ha cambiado para que lo guarde
        visible.onChange(null);
    }

    public static Pokemon readHead(Pokemon visible, RegistryAccess registryAccess) {
        return Pokemon.Companion.loadFromNBT(registryAccess, data(visible).getCompound(HEAD));
    }

    public static Pokemon readBody(Pokemon visible, RegistryAccess registryAccess) {
        return Pokemon.Companion.loadFromNBT(registryAccess, data(visible).getCompound(BODY));
    }

    public static void clear(Pokemon visible) {
        visible.getPersistentData().remove(KEY);
        visible.onChange(null);
    }

    private static CompoundTag data(Pokemon visible) {
        return visible.getPersistentData().getCompound(KEY);
    }
}
