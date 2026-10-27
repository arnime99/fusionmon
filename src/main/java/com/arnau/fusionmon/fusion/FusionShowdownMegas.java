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
     * Envuelve canMegaEvo la primera vez (globalThis.fusionmonMegas guarda la tabla y marca que ya está envuelta) y
     * añade las entradas. require usa la caché de módulos: es la misma BattleActions que usa Showdown.
     */
    private static final String SCRIPT = """
            (function (entries) {
              if (!globalThis.fusionmonMegas) {
                globalThis.fusionmonMegas = {};
                const BattleActions = require('./sim/battle-actions').BattleActions;
                const original = BattleActions.prototype.canMegaEvo;
                BattleActions.prototype.canMegaEvo = function (pokemon) {
                  try {
                    const mega = globalThis.fusionmonMegas[pokemon.species.id];
                    if (mega && pokemon.getItem().id === mega.item) return mega.target;
                  } catch (e) {
                  }
                  return original.call(this, pokemon);
                };
              }
              Object.assign(globalThis.fusionmonMegas, entries);
              return true;
            })(%s)
            """;

    private FusionShowdownMegas() {
    }

    /**
     * Pasa a Showdown las megas de las fusiones de un combate: id de la especie de fusión → {target, item}. Después de
     * registrar las especies (la de destino tiene que existir) y antes de empezar el combate (Showdown calcula
     * canMegaEvo al crear cada Pokémon del combate).
     */
    static boolean send(Map<String, JsonObject> megas) {
        if (megas.isEmpty()) {
            return true;
        }
        try {
            JsonObject entries = new JsonObject();
            megas.forEach(entries::add);
            evalJs(SCRIPT.formatted(entries));
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
