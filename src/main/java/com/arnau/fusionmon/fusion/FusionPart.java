package com.arnau.fusionmon.fusion;

import java.util.Locale;

/** Las dos partes guardadas dentro de una fusión. */
public enum FusionPart {
    HEAD,
    BODY;

    /**
     * Id de una evolución de esta parte tal como la ve Cobblemon. Lleva la parte delante para que no choque
     * cuando cabeza y cuerpo son de la misma especie (Cobblemon identifica las evoluciones pendientes por id).
     */
    public String evolutionId(String originalId) {
        return "fusionmon_" + name().toLowerCase(Locale.ROOT) + "_" + originalId;
    }
}
