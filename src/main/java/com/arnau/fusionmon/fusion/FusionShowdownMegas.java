package com.arnau.fusionmon.fusion;

import com.arnau.fusionmon.FusionSafety;
import com.arnau.fusionmon.Fusionmon;
import com.cobblemon.mod.common.battles.runner.ShowdownService;
import com.google.gson.JsonObject;

import java.lang.reflect.Method;
import java.util.Map;

/**
 * Megas de las fusiones en combate (Mega Showdown, opcional). En Showdown la mega la decide canMegaEvo mirando la
 * megapiedra (piedra.megaStone[nombre de la especie]), y una fusión es una especie propia ("fusionmoncharizardxpikachu")
 * que ninguna piedra conoce: el botón de mega no salía. Antes de cada combate FusionShowdown registra también la
 * fusión en mega (su especie destino, con los stats de la forma mega) y aquí se le pasa a Showdown una tabla "esta
 * fusión, con esta piedra, megaevoluciona en esta especie", calculada en Java con la misma regla que fuera de combate
 * (FusionMegas.plan): la regla está en un solo sitio.
 *
 * Para que Showdown la mire, se envuelve canMegaEvo una vez, en memoria (sin tocar los archivos de showdown/): si la
 * especie está en la tabla y lleva esa piedra, la fusión en mega; si no, lo de siempre. Showdown corre dentro de
 * Minecraft (GraalVM, GraalShowdownService), que deja ejecutar JavaScript en él, como hace Mega Showdown con sus
 * objetos y scripts. Sin Graal (el servidor de depuración por socket) o si algo falla, se avisa una vez en el log y
 * las fusiones pelean como hasta ahora, sin mega.
 */
public final class FusionShowdownMegas {

    /**
     * Envuelve canMegaEvo y canUltraBurst la primera vez (globalThis.fusionmonMegas/fusionmonUltras guardan las tablas
     * y marcan que ya están envueltas) y añade las entradas. require usa la caché de módulos: es la misma BattleActions
     * que usa Showdown. Una entrada vale si la fusión lleva su objeto (item) o sabe su movimiento (move: Rayquaza con
     * Ascenso Draco, con las mismas excepciones que el canMegaEvo original: dinamax, teracristal, cristal Z).
     *
     * No basta con cambiar la función del prototipo: al crear las acciones de cada combate Showdown copia encima las
     * de los scripts (Object.assign(this, Scripts.actions)), y Mega Showdown para Cobblemon 1.7.3 (Cobbleverse) mete
     * ahí su propio canMegaEvo, que tapaba el nuestro: en combate no salía el botón. Por eso es una propiedad con
     * get/set en el prototipo: Object.assign pasa por el set, que guarda la suya aparte, y el get devuelve la nuestra,
     * que mira la tabla y si no llama a la que haya (la de los scripts, o la original).
     */
    private static final String SCRIPT = """
            (function (megas, ultras) {
              const matches = function (pokemon, entry) {
                if (!entry) return false;
                if (entry.item) return pokemon.getItem().id === entry.item;
                return !!entry.move && pokemon.baseMoves.includes(entry.move) && !pokemon.volatiles['dynamax']
                  && !pokemon.getItem().zMove && !pokemon.terastallized;
              };
              const wrap = function (BattleActions, name, table) {
                const proto = BattleActions.prototype;
                const original = proto[name];
                if (typeof original !== 'function') return;
                const replaced = '__fusionmon_' + name;
                Object.defineProperty(proto, name, {
                  configurable: true,
                  get: function () {
                    const self = this;
                    const inner = Object.prototype.hasOwnProperty.call(self, replaced) ? self[replaced] : original;
                    return function (pokemon) {
                      try {
                        const entry = globalThis[table][pokemon.species.id];
                        if (matches(pokemon, entry)) return entry.target;
                      } catch (e) {
                      }
                      return inner.call(self, pokemon);
                    };
                  },
                  set: function (fn) {
                    Object.defineProperty(this, replaced, { value: fn, writable: true, configurable: true });
                  }
                });
              };
              if (!globalThis.fusionmonMegas) {
                globalThis.fusionmonMegas = {};
                globalThis.fusionmonUltras = {};
                const BattleActions = require('./sim/battle-actions').BattleActions;
                wrap(BattleActions, 'canMegaEvo', 'fusionmonMegas');
                wrap(BattleActions, 'canUltraBurst', 'fusionmonUltras');
              }
              Object.assign(globalThis.fusionmonMegas, megas);
              Object.assign(globalThis.fusionmonUltras, ultras);
              return true;
            })(%s, %s)
            """;

    private FusionShowdownMegas() {
    }

    /**
     * Pasa a Showdown las megas de las fusiones de un combate: id de la especie de fusión → {target, item}. Después de
     * registrar las especies (la de destino tiene que existir) y antes de empezar el combate (Showdown calcula
     * canMegaEvo al crear cada Pokémon del combate).
     */
    static boolean send(Map<String, JsonObject> megas, Map<String, JsonObject> ultras) {
        if (megas.isEmpty() && ultras.isEmpty()) {
            return true;
        }
        try {
            JsonObject megaEntries = new JsonObject();
            megas.forEach(megaEntries::add);
            JsonObject ultraEntries = new JsonObject();
            ultras.forEach(ultraEntries::add);
            evalJs(SCRIPT.formatted(megaEntries, ultraEntries));
            return true;
        } catch (Exception | LinkageError e) {
            // Sin mega en combate para las fusiones: pelean como siempre
            FusionSafety.failed("megas de las fusiones en combate (Showdown)", e);
            return false;
        }
    }

    /**
     * Ejecuta JavaScript en el Showdown de Cobblemon. Con reflexión: las clases de GraalVM (Context) vienen con
     * Cobblemon y no se compila contra ellas; GraalShowdownService.getContext() y Context.eval(String, CharSequence).
     */
    private static void evalJs(String code) throws ReflectiveOperationException {
        ShowdownService service = ShowdownService.Companion.getService();
        Method getContext = service.getClass().getMethod("getContext");
        Object context = getContext.invoke(service);
        Method eval = context.getClass().getMethod("eval", String.class, CharSequence.class);
        eval.invoke(context, "js", code);
        Fusionmon.LOGGER.debug("Fusionmon: megas de fusiones pasadas a Showdown");
    }
}
