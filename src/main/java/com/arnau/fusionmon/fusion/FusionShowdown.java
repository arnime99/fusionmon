package com.arnau.fusionmon.fusion;

import com.arnau.fusionmon.Fusionmon;
import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.api.pokemon.stats.Stat;
import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.cobblemon.mod.common.battles.runner.ShowdownService;
import com.cobblemon.mod.common.pokemon.FormData;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Hace que Showdown (el motor de combate de Cobblemon) conozca las fusiones.
 *
 * Showdown saca tipos y stats base de la especie que le dice Cobblemon (Pokemon.showdownId()).
 * Para cada fusión registramos una especie propia, p. ej. "fusionmonbulbasaurxcharmander",
 * y PokemonMixin hace que showdownId() devuelva ese identificador.
 */
public final class FusionShowdown {

    private static final Gson GSON = new Gson();

    /** Campos de la especie de la cabeza que no tienen sentido en una fusión (formas, evoluciones, objetos requeridos...). */
    private static final List<String> REMOVED_FIELDS = List.of(
            "num", "otherFormes", "formeOrder", "preevo", "evos", "canGigantamax",
            "requiredMove", "requiredItem", "requiredItems", "maxHP");

    private FusionShowdown() {
    }

    public static String speciesId(FormData head, FormData body) {
        return "fusionmon" + head.showdownId() + "x" + body.showdownId();
    }

    /** Se llama justo antes de que Cobblemon arranque el combate en Showdown. */
    public static void registerFusions(PokemonBattle battle) {
        Map<String, String> species = new LinkedHashMap<>();
        for (BattleActor actor : battle.getActors()) {
            for (BattlePokemon battlePokemon : actor.getPokemonList()) {
                Pokemon pokemon = battlePokemon.getEffectedPokemon();
                FormData head = FusionData.headForm(pokemon);
                FormData body = FusionData.bodyForm(pokemon);
                if (head != null && body != null) {
                    String id = speciesId(head, body);
                    species.computeIfAbsent(id, key -> speciesJson(head, body, key));
                }
            }
        }

        if (species.isEmpty()) {
            return;
        }

        try {
            ShowdownService.Companion.getService().sendRegistryData(species, "species");
        } catch (Exception e) {
            // Si falla, el combate sigue (la fusión pelearía como su cabeza) en vez de romperse
            Fusionmon.LOGGER.error("No se han podido registrar las fusiones en Showdown: {}", species.keySet(), e);
        }
    }

    /** Datos de especie para Showdown: los de la cabeza, con nombre, tipos y stats base de la fusión. */
    private static String speciesJson(FormData head, FormData body, String id) {
        JsonObject headJson = showdownJson(head);
        JsonObject bodyJson = showdownJson(body);

        JsonObject fused = headJson.deepCopy();
        REMOVED_FIELDS.forEach(fused::remove);
        fused.addProperty("name", id);
        fused.addProperty("baseSpecies", id);
        fused.addProperty("forme", "");
        fused.addProperty("nfe", false);

        JsonArray types = new JsonArray();
        for (ElementalType type : FusionCalculator.types(head, body)) {
            types.add(showdownTypeName(type, head, headJson, body, bodyJson));
        }
        fused.add("types", types);

        JsonObject baseStats = new JsonObject();
        for (Stat stat : FusionService.PERMANENT_STATS) {
            baseStats.addProperty(stat.getShowdownId(), FusionCalculator.baseStat(head, body, stat));
        }
        fused.add("baseStats", baseStats);

        return fused.toString();
    }

    private static JsonObject showdownJson(FormData form) {
        return GSON.toJsonTree(new PokemonSpecies.ShowdownSpecies(form.getSpecies(), form)).getAsJsonObject();
    }

    /**
     * El nombre del tipo tal como lo escribe Cobblemon para Showdown ("Grass", "Fire"...).
     * Se copia de los datos de la cabeza o del cuerpo, que son de donde sale cada tipo de la fusión.
     */
    private static String showdownTypeName(ElementalType type, FormData head, JsonObject headJson,
                                           FormData body, JsonObject bodyJson) {
        String name = findTypeName(type, head, headJson);
        if (name == null) {
            name = findTypeName(type, body, bodyJson);
        }
        return name != null ? name : type.getName();
    }

    private static String findTypeName(ElementalType type, FormData form, JsonObject json) {
        JsonArray names = json.getAsJsonArray("types");
        int index = 0;
        for (ElementalType formType : form.getTypes()) {
            if (formType == type && names != null && index < names.size()) {
                return names.get(index).getAsString();
            }
            index++;
        }
        return null;
    }
}
