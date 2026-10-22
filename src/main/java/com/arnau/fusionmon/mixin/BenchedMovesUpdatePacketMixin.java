package com.arnau.fusionmon.mixin;

import com.cobblemon.mod.common.api.moves.BenchedMoves;
import com.cobblemon.mod.common.net.messages.client.pokemon.update.BenchedMovesUpdatePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * El paquete de movimientos recordables de Cobblemon guarda la lista VIVA del Pokémon y la escribe más tarde, en el
 * hilo de red. Si mientras tanto se quita un movimiento (otro mod que los borra de uno en uno, como Mega Showdown al
 * megaevolucionar), el servidor escribe "hay N" pero manda N-1, y el cliente se desconecta al leerlo
 * ("Failed to decode packet", BenchedMove.loadFromBuffer). Aquí el paquete se lleva una copia hecha al crearlo, en el
 * hilo del servidor: cada cambio manda su propio paquete, así que el último sigue dejando el estado final.
 */
@Mixin(value = BenchedMovesUpdatePacket.class, remap = false)
public abstract class BenchedMovesUpdatePacketMixin {

    /** Cambia el valor que se pasa a super(...) (SingleUpdatePacket). Antes de super no hay "this": tiene que ser static. */
    // require = 0: es una protección de más; sin ella el juego funciona igual (CommonMixinPlugin avisa si falta)
    @ModifyArg(method = "<init>", index = 1, require = 0, at = @At(value = "INVOKE",
            target = "Lcom/cobblemon/mod/common/net/messages/client/pokemon/update/SingleUpdatePacket;<init>(Lkotlin/jvm/functions/Function0;Ljava/lang/Object;)V"))
    private static Object fusionmon$snapshot(Object value) {
        if (value instanceof BenchedMoves live) {
            BenchedMoves copy = new BenchedMoves();
            // La copia nueva no está enganchada a ningún Pokémon: su update() no manda nada
            copy.copyFrom(live);
            return copy;
        }
        return value;
    }
}
