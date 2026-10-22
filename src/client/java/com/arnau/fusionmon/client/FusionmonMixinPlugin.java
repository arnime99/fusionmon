package com.arnau.fusionmon.client;

import com.arnau.fusionmon.CommonMixinPlugin;

/**
 * Plugin de fusionmon.client.mixins.json: lo mismo que el común (saltar los mixins cuya clase de Cobblemon no existe,
 * y avisar de los enganches que no se aplican; ver CommonMixinPlugin). Hoy se salta en Cobblemon 1.7.3
 * ClientboundSeatAssignmentPacketHandlerMixin: ese paquete de montura no existe allí (y montar va bien sin él).
 *
 * Todos los mixins de cliente son opcionales (defaultRequire 0): son de pintado y pantallas, y si chocan con otro mod
 * es mejor perder ese detalle que no arrancar el juego.
 */
public final class FusionmonMixinPlugin extends CommonMixinPlugin {
}
