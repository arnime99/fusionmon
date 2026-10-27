package com.arnau.fusionmon.fusion;

import com.arnau.fusionmon.FusionConfig;
import com.arnau.fusionmon.FusionSafety;
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
import com.cobblemon.mod.common.util.DistributionUtilsKt;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.RegistryAccess;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
            "requiredMove", "requiredItem", "requiredItems", "maxHP", "battleOnly", "changesFrom");

    private FusionShowdown() {
    }

    /**
     * Especies de fusión que no se han podido registrar en Showdown. Para ellas showdownId() devuelve la especie de
     * la cabeza (ver PokemonMixin): Showdown no conocería la de la fusión y el combate no podría empezar.
     */
    private static final Set<String> FAILED = new HashSet<>();

    public static String speciesId(FormData head, FormData body) {
        return "fusionmon" + head.showdownId() + "x" + body.showdownId();
    }

    /** Si se puede decir a Showdown que esta fusión es su propia especie (si no, peleará como su cabeza). */
    public static boolean isUsable(String speciesId) {
        return !FAILED.contains(speciesId);
    }

    /** Se llama justo antes de que Cobblemon arranque el combate en Showdown. */
    public static void registerFusions(PokemonBattle battle) {
        Map<String, String> species = new LinkedHashMap<>();
        // Fusiones que pueden megaevolucionar en este combate: su especie → {target: la fusión en mega, item: piedra o
        // move: Ascenso Draco}; y las que pueden hacer la Ultraexplosión (Showdown la mira aparte, canUltraBurst)
        Map<String, JsonObject> megas = new LinkedHashMap<>();
        Map<String, JsonObject> ultras = new LinkedHashMap<>();
        for (BattleActor actor : battle.getActors()) {
            for (BattlePokemon battlePokemon : actor.getPokemonList()) {
                Pokemon pokemon = battlePokemon.getEffectedPokemon();
                FormData head = FusionData.activeHeadForm(pokemon);
                FormData body = FusionData.bodyForm(pokemon);
                if (head == null || body == null) {
                    continue;
                }
                String id = speciesId(head, body);
                if (species.containsKey(id)) {
                    continue;
                }
                // Cada fusión por separado: una que falle no deja sin registrar a las demás
                try {
                    species.put(id, speciesJson(head, body, id, null));
                    FAILED.remove(id);
                } catch (RuntimeException e) {
                    FAILED.add(id);
                    Fusionmon.LOGGER.error("No se ha podido preparar la fusión {} para Showdown: pelea como su cabeza",
                            id, e);
                    continue;
                }
                addMega(pokemon, id, species, megas, ultras);
            }
        }

        if (species.isEmpty()) {
            return;
        }

        try {
            ShowdownService.Companion.getService().sendRegistryData(species, "species");
        } catch (Exception e) {
            // Si falla, el combate sigue (las fusiones pelean como su cabeza) en vez de romperse
            FAILED.addAll(species.keySet());
            Fusionmon.LOGGER.error("No se han podido registrar las fusiones en Showdown: {}", species.keySet(), e);
            return;
        }
        FusionShowdownMegas.send(megas, ultras);
    }

    /**
     * Si la fusión puede megaevolucionar o hacer la Ultraexplosión en combate (FusionMegas.battleMega,
     * FusionUltra.battleUltra), registra también esa especie y su entrada para FusionShowdownMegas. Si falla, solo se
     * queda sin ella.
     */
    private static void addMega(Pokemon pokemon, String id, Map<String, String> species,
                                Map<String, JsonObject> megas, Map<String, JsonObject> ultras) {
        try {
            RegistryAccess registryAccess = DistributionUtilsKt.server().registryAccess();
            addEntry(FusionMegas.battleMega(pokemon, registryAccess), id, species, megas);
            addEntry(FusionUltra.battleUltra(pokemon, registryAccess), id, species, ultras);
        } catch (RuntimeException | LinkageError e) {
            FusionSafety.failed("megas de las fusiones en combate", e);
        }
    }

    private static void addEntry(FusionMegas.BattleMega mega, String id, Map<String, String> species,
                                 Map<String, JsonObject> entries) {
        if (mega == null) {
            return;
        }
        String targetId = speciesId(mega.head(), mega.body());
        if (!species.containsKey(targetId)) {
            species.put(targetId, speciesJson(mega.head(), mega.body(), targetId, mega.part(),
                    mega.ultra() ? "Ultra" : "Mega"));
        }
        JsonObject entry = new JsonObject();
        entry.addProperty("target", targetId);
        if (mega.item() != null) {
            entry.addProperty("item", mega.item());
        }
        if (mega.move() != null) {
            entry.addProperty("move", mega.move());
        }
        entries.put(id, entry);
    }

    /**
     * Datos de especie para Showdown: los de la cabeza, con nombre, tipos y stats base de la fusión.
     *
     * @param megaPart si es la fusión ya megaevolucionada (la especie a la que cambia en combate), la parte que lo
     *                 está; null si no. Lleva su forma ("Mega", "Ultra"), para que Showdown la trate como tal (no se
     *                 puede teracristalizar...), y de la forma mega de esa parte las habilidades (al megaevolucionar,
     *                 Showdown pone la primera: Sequía, Impulso...) y la piedra (requiredItem, el mensaje)
     */
    private static String speciesJson(FormData head, FormData body, String id, FusionPart megaPart) {
        return speciesJson(head, body, id, megaPart, "Mega");
    }

    private static String speciesJson(FormData head, FormData body, String id, FusionPart megaPart, String forme) {
        JsonObject headJson = showdownJson(head);
        JsonObject bodyJson = showdownJson(body);

        JsonObject fused = headJson.deepCopy();
        JsonObject megaJson = megaPart == FusionPart.BODY ? bodyJson : headJson;
        REMOVED_FIELDS.forEach(fused::remove);
        fused.addProperty("name", id);
        fused.addProperty("baseSpecies", id);
        fused.addProperty("forme", megaPart != null ? forme : "");
        fused.addProperty("nfe", false);
        if (megaPart != null) {
            if (megaJson.has("abilities")) {
                fused.add("abilities", megaJson.get("abilities").deepCopy());
            }
            JsonElement requiredItem = megaJson.get("requiredItem");
            if (requiredItem != null) {
                fused.add("requiredItem", requiredItem.deepCopy());
            }
        }

        JsonArray types = new JsonArray();
        for (ElementalType type : FusionCalculator.types(head, body)) {
            types.add(showdownTypeName(type, head, headJson, body, bodyJson));
        }
        fused.add("types", types);

        JsonObject baseStats = new JsonObject();
        for (Stat stat : FusionService.PERMANENT_STATS) {
            baseStats.addProperty(stat.getShowdownId(),
                    FusionCalculator.baseStat(head, body, stat, FusionConfig.statBonusPercent()));
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
