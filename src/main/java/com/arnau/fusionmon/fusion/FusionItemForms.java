package com.arnau.fusionmon.fusion;

import com.arnau.fusionmon.FusionSafety;
import com.arnau.fusionmon.Fusionmon;
import com.arnau.fusionmon.fusion.FusionMegas.MegaShowdown;
import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.pokemon.FormData;
import com.cobblemon.mod.common.pokemon.Pokemon;
import kotlin.Unit;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Formas por objeto equipado (Mega Showdown, opcional): placas de Arceus, Memorias de Silvally, Discos de Genesect, Orbe
 * Grisáceo de Giratina, máscaras de Ogerpon, Espada/Escudo Oxidados... Mega Showdown cambia la forma al dar o quitar el
 * objeto, pero solo mira la especie visible (la cabeza). Aquí, el mismo sistema que la mega del cuerpo (FusionMegas): si
 * el objeto que lleva la fusión no le sirve a la cabeza pero sí al cuerpo, una copia del cuerpo con esa forma va como
 * capa encima de él (FusionData.setBodyItemForm); al quitarle el objeto (o cambiarlo por otro) se quita. Modelo, tipos,
 * stats, tamaño, combate y Fusion Album salen de esa forma (la de la cabeza, de FusionData.activeHeadForm).
 *
 * Al dar o quitar el objeto a una fusión que ya existe se mira después de cada cambio (HELD_ITEM_POST de Cobblemon).
 * Al fusionar, separar o invertir, FusionService mueve los objetos en silencio (swapHeldItem sin eventos: si no, Mega
 * Showdown deshace y rehace las formas con animación y unos ticks tarde, cuando el Pokémon ya ha cambiado) y las formas
 * las pone y quita esta clase en el acto: revert antes de guardar las copias, applyToFusion / giveToPart después.
 */
public final class FusionItemForms {

    /** Sin Mega Showdown no hay objetos que cambien formas (Cobblemon no lo hace por su cuenta). */
    private static final boolean ENABLED = FabricLoader.getInstance().isModLoaded("mega_showdown");

    private FusionItemForms() {
    }

    public static void register() {
        if (!ENABLED) {
            return;
        }
        CobblemonEvents.HELD_ITEM_POST.subscribe(Priority.NORMAL, event -> {
            try {
                Pokemon fusion = event.getPokemon();
                ServerPlayer owner = FusionData.isFusion(fusion) ? fusion.getOwnerPlayer() : null;
                if (owner == null) {
                    return Unit.INSTANCE;
                }
                // Primero la mega del cuerpo si se le ha quitado su piedra: con el cuerpo en mega updateBody no lo toca
                boolean megaReverted = FusionMegas.revertBodyIfStoneRemoved(fusion, event.getReturned(),
                        event.getReceived());
                if (megaReverted) {
                    // persistentData solo llega al cliente con el Pokémon entero (el resumen calcula los stats con ella)
                    FusionService.resendToClient(owner, fusion);
                }
                // Lo mismo con el Ultranecrozium Z y la Ultraexplosión del cuerpo (ya avisa al cliente)
                FusionUltra.revertBodyIfCrystalRemoved(fusion, event.getReturned(), event.getReceived());
                // Como Mega Showdown con la cabeza: la animación y, en su destello, el cambio de forma del cuerpo
                float delay = animateBody(fusion, event.getReturned(), event.getReceived());
                Runnable change = () -> {
                    try {
                        if (FusionData.isFusion(fusion) && updateBody(owner, fusion)) {
                            FusionService.resendToClient(owner, fusion);
                        }
                    } catch (RuntimeException | LinkageError e) {
                        FusionSafety.failed("formas por objeto del cuerpo", e);
                    }
                };
                if (delay > 0) {
                    FusionMegas.afterOnServer(delay, change);
                } else {
                    change.run();
                }
            } catch (RuntimeException | LinkageError e) {
                // Solo se pierde la forma del cuerpo: el objeto se da o se quita igual
                FusionSafety.failed("formas por objeto del cuerpo", e);
            }
            return Unit.INSTANCE;
        });
    }

    /**
     * Antes de guardar la copia de una parte (al fusionar): si su objeto le da una forma, se deshace en el acto, como
     * las megas (FusionMegas.revert). Las copias se guardan siempre en forma normal; la forma vuelve con el objeto.
     */
    public static void revert(Pokemon part) {
        try {
            MegaShowdown.ItemForm form = formOf(part.heldItem());
            if (form != null && form.fits(part)) {
                FusionMegas.applyStone(part, form.revertAspects());
            }
        } catch (RuntimeException | LinkageError e) {
            // Nunca impide fusionar: como mucho la copia se guarda con la forma del objeto
            Fusionmon.LOGGER.warn("Fusionmon: no se ha podido deshacer la forma por objeto de {}",
                    part.getSpecies().getName(), e);
        }
    }

    /**
     * Después de dar un objeto en silencio a una fusión (al fusionar o invertir): la forma que le dé, a la cabeza si le
     * sirve (como haría Mega Showdown, sin su animación) y si no al cuerpo. No manda nada al cliente: quien llama manda
     * el Pokémon entero.
     */
    public static void applyToFusion(ServerPlayer owner, Pokemon fusion) {
        try {
            MegaShowdown.ItemForm form = formOf(fusion.heldItem());
            if (form != null && form.fits(fusion)) {
                FusionMegas.applyStone(fusion, form.applyAspects());
            } else {
                updateBody(owner, fusion);
            }
        } catch (RuntimeException | LinkageError e) {
            // La fusión ya está hecha: solo se queda sin la forma del objeto
            Fusionmon.LOGGER.warn("Fusionmon: no se ha podido poner la forma por objeto a la fusión", e);
        }
    }

    /**
     * Al separar: si el objeto de la fusión le da forma a una de las partes, esa parte se lo queda con su forma (como
     * keepMega con las megapiedras). Primero el cuerpo si lo llevaba puesto, luego la cabeza. False si no es para
     * ninguna: entonces vuelve al jugador.
     */
    public static boolean giveToPart(ItemStack item, Pokemon fused, Pokemon head, Pokemon body) {
        MegaShowdown.ItemForm form;
        try {
            form = formOf(item);
        } catch (RuntimeException | LinkageError e) {
            Fusionmon.LOGGER.warn("Fusionmon: no se ha podido leer la forma del objeto al separar", e);
            return false;
        }
        if (form == null) {
            return false;
        }
        Pokemon part = FusionData.hasBodyItemForm(fused) && form.fits(body) ? body
                : form.fits(head) ? head
                : form.fits(body) ? body
                : null;
        if (part == null) {
            return false;
        }
        part.swapHeldItem(item, false, true);
        FusionMegas.applyStone(part, form.applyAspects());
        return true;
    }

    /**
     * Para las pantallas de separar e invertir: las copias de las partes están en forma normal; si la fusión lleva un
     * objeto de forma, se le pone a la parte a la que le sirve, como quedará. Solo para la pantalla.
     */
    public static void preview(Pokemon fused, FusionService.Parts parts) {
        try {
            MegaShowdown.ItemForm form = formOf(fused.heldItem());
            if (form == null) {
                return;
            }
            Pokemon part = FusionData.hasBodyItemForm(fused) && form.fits(parts.body()) ? parts.body()
                    : form.fits(parts.head()) ? parts.head()
                    : form.fits(parts.body()) ? parts.body()
                    : null;
            if (part != null) {
                FusionMegas.applyStone(part, form.applyAspects());
            }
        } catch (RuntimeException | LinkageError e) {
            Fusionmon.LOGGER.warn("Fusionmon: no se ha podido enseñar la forma por objeto en la pantalla", e);
        }
    }

    /**
     * Al dar o quitar a mano el objeto de forma del cuerpo: la animación de Mega Showdown de ese objeto (la de ponerla
     * o la de quitarla). Devuelve los segundos hasta su destello, cuando hay que cambiar la forma (0: ya, sin esperar).
     */
    private static float animateBody(Pokemon fusion, ItemStack returned, ItemStack received) {
        if (FusionData.hasBodyMega(fusion) || FusionData.hasBodyUltra(fusion)) {
            return 0;
        }
        MegaShowdown.ItemForm next = formOf(received);
        if (next != null && fitsBodyOnly(fusion, next)) {
            return next.effect() == null ? 0 : MegaShowdown.playEffect(fusion, next.effect(), true);
        }
        MegaShowdown.ItemForm old = formOf(returned);
        if (old != null && FusionData.hasBodyItemForm(fusion) && fitsBodyOnly(fusion, old)) {
            return old.effect() == null ? 0 : MegaShowdown.playEffect(fusion, old.effect(), false);
        }
        return 0;
    }

    /** Si la forma es para el cuerpo: le sirve a él y no a la cabeza (si le sirve a la cabeza, gana la cabeza). */
    private static boolean fitsBodyOnly(Pokemon fusion, MegaShowdown.ItemForm form) {
        FormData body = FusionData.bodyForm(fusion);
        return body != null && !form.fits(fusion) && form.pokemons().contains(body.getSpecies().getName());
    }

    /** Pone o quita la forma del cuerpo según el objeto que lleva ahora la fusión. True si ha cambiado algo. */
    private static boolean updateBody(ServerPlayer owner, Pokemon fusion) {
        // La de antes se quita siempre: el objeto ha cambiado (si es el mismo tipo de objeto, se vuelve a poner)
        boolean changed = FusionData.clearBodyItemForm(fusion);
        MegaShowdown.ItemForm form = formOf(fusion.heldItem());
        FormData body = FusionData.bodyForm(fusion);
        // Si le sirve a la cabeza es para la cabeza (gana la cabeza, como con las megapiedras). Con el cuerpo en mega no
        // se toca: la megapiedra y este objeto no pueden ir a la vez, así que no debería pasar
        if (form == null || body == null || FusionData.hasBodyMega(fusion) || FusionData.hasBodyUltra(fusion)
                || form.fits(fusion)
                || !form.pokemons().contains(body.getSpecies().getName())) {
            return changed;
        }
        Pokemon formBody = FusionData.readBody(fusion, owner.registryAccess());
        if (!FusionMegas.applyStone(formBody, form.applyAspects())) {
            return changed;
        }
        FusionData.setBodyItemForm(fusion, formBody);
        Fusionmon.LOGGER.info("Fusionmon: el cuerpo de la fusión de {} ha cambiado de forma ({})",
                owner.getName().getString(), formBody.getForm().getName());
        return true;
    }

    /** La forma que da el objeto, o null (también sin Mega Showdown). */
    private static MegaShowdown.ItemForm formOf(ItemStack item) {
        return ENABLED && !item.isEmpty() ? MegaShowdown.itemForm(item) : null;
    }
}
