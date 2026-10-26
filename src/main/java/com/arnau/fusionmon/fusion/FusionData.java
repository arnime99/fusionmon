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
 *      ├─ "bodyMega": forma temporal del cuerpo (Mega Showdown): "form" y "aspects" de esa forma, y "kind": "mega"
 *      │              (FusionMegas) o "item" (forma por objeto equipado: placas, máscaras..., FusionItemForms; sin
 *      │              "kind" es una mega, de antes de haber otras). Va encima de bodyForm/bodyAspects sin tocarlos ni
 *      │              tocar "body": al deshacerla se borra, y al separar el cuerpo sale en su forma normal
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
    /** La capa de forma temporal del cuerpo (se llama así porque al principio solo eran megas). */
    private static final String BODY_MEGA = "bodyMega";
    private static final String MEGA_FORM = "form";
    private static final String MEGA_ASPECTS = "aspects";
    private static final String LAYER_KIND = "kind";
    private static final String KIND_MEGA = "mega";
    private static final String KIND_ITEM = "item";
    private static final String KIND_ULTRA = "ultra";
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
        CompoundTag data = data(visible);
        putPart(data, part, pokemon, registryAccess);
        if (part == FusionPart.BODY) {
            // La mega era del cuerpo de antes (p. ej. antes de evolucionar)
            data.remove(BODY_MEGA);
        }
        visible.onChange(null);
        visible.updateAspects();
    }

    private static void putPart(CompoundTag data, FusionPart part, Pokemon pokemon, RegistryAccess registryAccess) {
        boolean head = part == FusionPart.HEAD;
        CompoundTag copy = pokemon.saveToNBT(registryAccess, new CompoundTag());
        // La copia nunca se anima: una animación de Mega Showdown a medias se quedaría en ella para siempre
        FusionMegas.clearFormChanging(copy.getCompound("PersistentData"));
        data.put(head ? HEAD : BODY, copy);
        data.putString(head ? HEAD_SPECIES : BODY_SPECIES, pokemon.getSpecies().getResourceIdentifier().toString());
        data.putString(head ? HEAD_FORM : BODY_FORM, pokemon.getForm().getName());
        if (!head) {
            // Los de la cabeza no hacen falta: el Pokémon visible ya es la cabeza y lleva los suyos
            data.put(BODY_ASPECTS, aspectList(pokemon));
        }
    }

    private static ListTag aspectList(Pokemon pokemon) {
        ListTag aspects = new ListTag();
        for (String aspect : pokemon.getAspects()) {
            aspects.add(StringTag.valueOf(aspect));
        }
        return aspects;
    }

    public static Pokemon readHead(Pokemon visible, RegistryAccess registryAccess) {
        return Pokemon.Companion.loadFromNBT(registryAccess, data(visible).getCompound(HEAD));
    }

    public static Pokemon readBody(Pokemon visible, RegistryAccess registryAccess) {
        return Pokemon.Companion.loadFromNBT(registryAccess, data(visible).getCompound(BODY));
    }

    /**
     * La especie guardada de una de las partes que ya no existe (p. ej. se ha quitado el mod que la añadía), o null.
     * Sin ella Cobblemon no puede volver a crear esa parte: readHead/readBody lanzarían una excepción.
     */
    public static String missingSpecies(Pokemon visible) {
        CompoundTag data = data(visible);
        for (String key : new String[]{HEAD_SPECIES, BODY_SPECIES}) {
            String id = data.getString(key);
            // Las fusiones de la versión 1 no guardaban la especie: no se puede comprobar sin leer la parte
            if (id.isEmpty()) {
                continue;
            }
            ResourceLocation speciesId = ResourceLocation.tryParse(id);
            if (speciesId == null || PokemonSpecies.getByIdentifier(speciesId) == null) {
                return id;
            }
        }
        return null;
    }

    /**
     * Forma guardada de la cabeza (la de cuando se fusionó), o null si no es una fusión (o es una fusión de la versión 1,
     * sin estos datos). Para tipos, stats y combate, activeHeadForm.
     */
    public static FormData headForm(Pokemon visible) {
        return form(visible, HEAD_SPECIES, HEAD_FORM);
    }

    /**
     * Forma de la cabeza tal como se ve ahora: la del Pokémon visible, que es la cabeza, si sigue siendo de su especie
     * (en mega o con una forma por objeto: placa de Arceus, Orbe Grisáceo...), y si no, la guardada. Así los tipos y los
     * stats siguen a la cabeza como bodyForm sigue al cuerpo (antes una placa en la cabeza no cambiaba el tipo).
     */
    public static FormData activeHeadForm(Pokemon visible) {
        FormData stored = headForm(visible);
        if (stored == null) {
            return null;
        }
        FormData current = visible.getForm();
        return current.getSpecies().getResourceIdentifier().equals(stored.getSpecies().getResourceIdentifier())
                ? current : stored;
    }

    /**
     * Forma del cuerpo tal como se ve ahora: la temporal si la tiene (mega o por objeto; así la usan stats, tipos,
     * modelo y tamaño).
     */
    public static FormData bodyForm(Pokemon visible) {
        FormData form = form(visible, BODY_SPECIES, BODY_FORM);
        if (form != null && isFusion(visible) && data(visible).contains(BODY_MEGA)) {
            return form.getSpecies().getFormByName(data(visible).getCompound(BODY_MEGA).getString(MEGA_FORM));
        }
        return form;
    }

    public static boolean hasBodyMega(Pokemon visible) {
        return KIND_MEGA.equals(layerKind(visible));
    }

    public static boolean hasBodyItemForm(Pokemon visible) {
        return KIND_ITEM.equals(layerKind(visible));
    }

    /** Si el cuerpo está en Ultraexplosión (Necrozma, ver FusionUltra). */
    public static boolean hasBodyUltra(Pokemon visible) {
        return KIND_ULTRA.equals(layerKind(visible));
    }

    /** Si el cuerpo tiene alguna forma temporal encima (mega, objeto, Ultraexplosión). */
    public static boolean hasBodyLayer(Pokemon visible) {
        return layerKind(visible) != null;
    }

    /** "mega", "item", "ultra" o null si el cuerpo no tiene forma temporal. */
    private static String layerKind(Pokemon visible) {
        if (!isFusion(visible) || !data(visible).contains(BODY_MEGA)) {
            return null;
        }
        String kind = data(visible).getCompound(BODY_MEGA).getString(LAYER_KIND);
        return kind.isEmpty() ? KIND_MEGA : kind;
    }

    /** Pone encima del cuerpo la forma y los aspects de megaBody (una copia del cuerpo ya megaevolucionada). */
    public static void setBodyMega(Pokemon visible, Pokemon megaBody) {
        setBodyLayer(visible, megaBody, KIND_MEGA);
    }

    /** Lo mismo con una forma por objeto equipado (una copia del cuerpo ya cambiada). */
    public static void setBodyItemForm(Pokemon visible, Pokemon formBody) {
        setBodyLayer(visible, formBody, KIND_ITEM);
    }

    /** Lo mismo con la Ultraexplosión de Necrozma (una copia del cuerpo ya en forma Ultra). */
    public static void setBodyUltra(Pokemon visible, Pokemon ultraBody) {
        setBodyLayer(visible, ultraBody, KIND_ULTRA);
    }

    private static void setBodyLayer(Pokemon visible, Pokemon formBody, String kind) {
        CompoundTag layer = new CompoundTag();
        layer.putString(MEGA_FORM, formBody.getForm().getName());
        layer.put(MEGA_ASPECTS, aspectList(formBody));
        layer.putString(LAYER_KIND, kind);
        data(visible).put(BODY_MEGA, layer);
        visible.onChange(null);
        visible.updateAspects();
    }

    /** Quita la mega del cuerpo. True si la tenía. */
    public static boolean clearBodyMega(Pokemon visible) {
        return hasBodyMega(visible) && clearBodyLayer(visible);
    }

    /** Quita la forma por objeto del cuerpo. True si la tenía. */
    public static boolean clearBodyItemForm(Pokemon visible) {
        return hasBodyItemForm(visible) && clearBodyLayer(visible);
    }

    /** Quita la Ultraexplosión del cuerpo. True si la tenía. */
    public static boolean clearBodyUltra(Pokemon visible) {
        return hasBodyUltra(visible) && clearBodyLayer(visible);
    }

    private static boolean clearBodyLayer(Pokemon visible) {
        data(visible).remove(BODY_MEGA);
        visible.onChange(null);
        visible.updateAspects();
        return true;
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

    /** Aspects del cuerpo, los de su forma temporal si la tiene (vacío en fusiones creadas antes de guardarlos). */
    public static Set<String> bodyAspects(Pokemon visible) {
        CompoundTag data = data(visible);
        ListTag list = data.contains(BODY_MEGA)
                ? data.getCompound(BODY_MEGA).getList(MEGA_ASPECTS, Tag.TAG_STRING)
                : data.getList(BODY_ASPECTS, Tag.TAG_STRING);
        Set<String> aspects = new HashSet<>();
        for (Tag tag : list) {
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
