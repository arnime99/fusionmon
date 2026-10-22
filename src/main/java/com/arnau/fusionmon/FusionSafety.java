package com.arnau.fusionmon;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Red de seguridad para lo que Fusionmon engancha en Cobblemon. Fusionmon convive con modpacks de cientos de mods
 * (otros modelos, resource packs, mods de megas...): si algo nuestro falla con uno de ellos, ese detalle se pierde
 * (la fusión se pinta como su cabeza, el resumen sale sin el añadido...) pero el juego sigue. Un crash le cuesta al
 * jugador la partida; un detalle de menos, no.
 *
 * Avisa una sola vez por sitio: muchos de estos fallos pasarían en cada fotograma y llenarían el log.
 */
public final class FusionSafety {

    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    private FusionSafety() {
    }

    /** Apunta en el log (una vez por "where") que algo de Fusionmon ha fallado y se ha seguido sin ello. */
    public static void failed(String where, Throwable error) {
        if (WARNED.add(where)) {
            Fusionmon.LOGGER.warn("Fusionmon: ha fallado {} (¿otro mod?); se sigue sin esa parte. Solo se avisa una"
                    + " vez", where, error);
        }
    }
}
