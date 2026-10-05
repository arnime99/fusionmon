package com.arnau.fusionmon.fusion;

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.pokemon.FormData;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.util.HashSet;
import java.util.Set;

/**
 * Lee y escribe los datos de fusión dentro del Pokémon visible.
 *
 * Cobblemon da a cada Pokémon un CompoundTag libre para mods (persistentData) que se guarda
 * con él en el equipo, el PC, los intercambios... y que también llega al cliente.
 *
 * persistentData
 *  └─ "fusionmon"
 *      ├─ "version": 2
 *      ├─ "head": Pokémon A completo, tal como era antes de fusionar (salvo si ha evolucionado como fusión)
 *      ├─ "body": Pokémon B completo, ídem
 *      ├─ "headSpecies" / "headForm": especie y forma de A (para calcular rápido, sin cargar A entero)
 *      ├─ "bodySpecies" / "bodyForm": especie y forma de B
 *      ├─ "bodyAspects": aspects de B (shiny, female, alolan...), para pintar la fusión con los colores
 *      │                 del cuerpo sin tener que cargarlo entero (ver FusionAspects)
 *      └─ "startExperience": experiencia de la fusión al crearse (la ganada después se reparte al separar)
 *
 * Cada vez que cambian estos datos se llama a updateAspects(): Cobblemon vuelve a preguntar a FusionAspects
 * y envía los aspects nuevos al cliente.
 */
public final class FusionData {

    private static final String KEY = "fusionmon";
    private static final String VERSION = "version";
    private static final String HEAD = "head";
    private static final String BODY = "body";
    private static final String HEAD_SPECIES = "headSpecies";
    private static final String HEAD_FORM = "headForm";
    private static final String BODY_SPECIES = "bodySpecies";
    private static final String BODY_FORM = "bodyForm";
    private static final String BODY_ASPECTS = "bodyAspects";
    private static final String START_EXPERIENCE = "startExperience";
    private static final int CURRENT_VERSION = 2;

    private FusionData() {
    }

    public static boolean isFusion(Pokemon pokemon) {
        // Cobblemon calcula stats mientras construye el Pokémon, antes de crear persistentData
        CompoundTag persistentData = pokemon.getPersistentData();
        return persistentData != null && persistentData.contains(KEY);
    }

    public static void write(Pokemon visible, Pokemon head, Pokemon body, RegistryAccess registryAccess) {
        CompoundTag data = new CompoundTag();
        data.putInt(VERSION, CURRENT_VERSION);
        putPart(data, FusionPart.HEAD, head, registryAccess);
        putPart(data, FusionPart.BODY, body, registryAccess);

        visible.getPersistentData().put(KEY, data);
        // Avisa a Cobblemon de que el Pokémon ha cambiado para que lo guarde
        visible.onChange(null);
        visible.updateAspects();
    }

    /** Sustituye una de las partes guardadas (p. ej. después de evolucionarla). */
    public static void writePart(Pokemon visible, FusionPart part, Pokemon pokemon, RegistryAccess registryAccess) {
        putPart(data(visible), part, pokemon, registryAccess);
        visible.onChange(null);
        visible.updateAspects();
    }

    private static void putPart(CompoundTag data, FusionPart part, Pokemon pokemon, RegistryAccess registryAccess) {
        boolean head = part == FusionPart.HEAD;
        data.put(head ? HEAD : BODY, pokemon.saveToNBT(registryAccess, new CompoundTag()));
        data.putString(head ? HEAD_SPECIES : BODY_SPECIES, pokemon.getSpecies().getResourceIdentifier().toString());
        data.putString(head ? HEAD_FORM : BODY_FORM, pokemon.getForm().getName());
        if (!head) {
            // Los de la cabeza no hacen falta: el Pokémon visible ya es la cabeza y lleva los suyos
            ListTag aspects = new ListTag();
            for (String aspect : pokemon.getAspects()) {
                aspects.add(StringTag.valueOf(aspect));
            }
            data.put(BODY_ASPECTS, aspects);
        }
    }

    public static Pokemon readHead(Pokemon visible, RegistryAccess registryAccess) {
        return Pokemon.Companion.loadFromNBT(registryAccess, data(visible).getCompound(HEAD));
    }

    public static Pokemon readBody(Pokemon visible, RegistryAccess registryAccess) {
        return Pokemon.Companion.loadFromNBT(registryAccess, data(visible).getCompound(BODY));
    }

    /** Forma de la cabeza, o null si no es una fusión (o es una fusión de la versión 1, sin estos datos). */
    public static FormData headForm(Pokemon visible) {
        return form(visible, HEAD_SPECIES, HEAD_FORM);
    }

    public static FormData bodyForm(Pokemon visible) {
        return form(visible, BODY_SPECIES, BODY_FORM);
    }

    /** Guarda la experiencia con la que nace la fusión, para saber al separarla cuánta ha ganado. */
    public static void markStartExperience(Pokemon visible) {
        data(visible).putInt(START_EXPERIENCE, visible.getExperience());
        visible.onChange(null);
    }

    /** Experiencia ganada como fusión (0 en fusiones antiguas que no guardaban el dato). */
    public static int experienceGained(Pokemon visible) {
        CompoundTag data = data(visible);
        if (!data.contains(START_EXPERIENCE)) {
            return 0;
        }
        return Math.max(0, visible.getExperience() - data.getInt(START_EXPERIENCE));
    }

    /** Aspects del cuerpo (vacío en fusiones creadas antes de guardarlos). */
    public static Set<String> bodyAspects(Pokemon visible) {
        Set<String> aspects = new HashSet<>();
        for (Tag tag : data(visible).getList(BODY_ASPECTS, Tag.TAG_STRING)) {
            aspects.add(tag.getAsString());
        }
        return aspects;
    }

    public static void clear(Pokemon visible) {
        visible.getPersistentData().remove(KEY);
        visible.onChange(null);
        visible.updateAspects();
    }

    private static FormData form(Pokemon visible, String speciesKey, String formKey) {
        if (!isFusion(visible)) {
            return null;
        }

        CompoundTag data = data(visible);
        ResourceLocation speciesId = ResourceLocation.tryParse(data.getString(speciesKey));
        if (speciesId == null) {
            return null;
        }

        Species species = PokemonSpecies.getByIdentifier(speciesId);
        if (species == null) {
            return null;
        }
        return species.getFormByName(data.getString(formKey));
    }

    private static CompoundTag data(Pokemon visible) {
        return visible.getPersistentData().getCompound(KEY);
    }
}
