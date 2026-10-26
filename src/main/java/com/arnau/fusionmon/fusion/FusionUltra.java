package com.arnau.fusionmon.fusion;

import com.arnau.fusionmon.FusionSafety;
import com.arnau.fusionmon.Fusionmon;
import com.arnau.fusionmon.fusion.FusionMegas.MegaShowdown;
import com.cobblemon.mod.common.pokemon.FormData;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Set;

/**
 * Ultraexplosión de Necrozma (Mega Showdown, opcional) en las fusiones, con el mismo sistema que la mega del cuerpo
 * (FusionMegas). Mega Showdown la permite fuera de combate a Necrozma Melena Crepuscular o Alas del Alba con el
 * Ultranecrozium Z y el Aro Z, pero solo mira la especie visible (la cabeza), y su botón de la rueda solo sale si la
 * cabeza es Necrozma: con Necrozma de cuerpo no se podía. MegaShowdownUltraGimmickMixin le dice que sí puede
 * (canUltraBody) y, al hacerla, cambiamos la forma del cuerpo: una capa encima de él (FusionData.setBodyUltra), que se
 * quita al deshacerla, al quitarle el cristal o al empezar un combate (como hace Mega Showdown con la cabeza).
 *
 * Como las megas, se conserva al fusionar, invertir y separar: las copias de las partes se guardan en su forma de
 * antes (Melena Crepuscular / Alas del Alba) y la fusión, que se queda el cristal, vuelve a hacerla en silencio.
 */
public final class FusionUltra {

    private static final String NECROZMA = "Necrozma";
    private static final String CRYSTAL = "ultranecroziumz";
    private static final String EFFECT = "mega_showdown:ultra_burst";
    /** Aspects de las formas de Necrozma con las que se puede (las fusiones con Solgaleo y Lunala) y la Ultra. */
    private static final String DAWN = "dawn-fusion";
    private static final String DUSK = "dusk-fusion";
    private static final String ULTRA = "ultra-fusion";
    private static final List<String> ULTRA_FEATURE = List.of("prism_fusion=ultra");
    /** Lo que guarda Mega Showdown en persistentData para deshacerla: la forma de antes y sus datos de efecto. */
    private static final String NECROZMA_FORM = "necrozma_form";
    private static final String REVERT_ASPECTS = "revert_aspects";

    private FusionUltra() {
    }

    /** Para isUltra de Mega Showdown: una fusión con el cuerpo en Ultraexplosión también lo está. */
    public static boolean isBodyUltra(Pokemon pokemon) {
        return FusionData.isFusion(pokemon) && FusionData.hasBodyUltra(pokemon);
    }

    /**
     * Para canUltraBurst de Mega Showdown, cuando él ha dicho que no: si la fusión puede hacerla por el cuerpo. Sus
     * mismas condiciones (dueño con el Aro Z, el Ultranecrozium Z, Necrozma en una de sus formas de fusión), mirando
     * el cuerpo.
     */
    public static boolean canUltraBody(Pokemon fusion) {
        if (!FusionData.isFusion(fusion) || FusionData.hasBodyLayer(fusion)) {
            return false;
        }
        ServerPlayer owner = fusion.getOwnerPlayer();
        return owner != null && holdsCrystal(fusion) && bodyCanUltra(fusion) && MegaShowdown.hasZRing(owner);
    }

    /**
     * En lugar de UltraGimmick.ultraBurst: si la que toca es la del cuerpo, la hace (marca de no intercambiable,
     * animación de Mega Showdown y, en su destello, la forma). True si lo ha hecho.
     */
    public static boolean ultraBurstBody(Pokemon fusion) {
        if (!canUltraBody(fusion) || headCanUltra(fusion)) {
            return false;
        }
        ServerPlayer owner = fusion.getOwnerPlayer();
        fusion.setTradeable(false);
        float delay = MegaShowdown.playEffect(fusion, EFFECT, true);
        Runnable change = () -> {
            try {
                // Mientras, pudo empezar un combate o quitársele el cristal
                if (!FusionData.isFusion(fusion) || FusionData.hasBodyLayer(fusion) || !holdsCrystal(fusion)) {
                    fusion.setTradeable(true);
                    return;
                }
                if (ultraBody(owner, fusion)) {
                    FusionService.resendToClient(owner, fusion);
                    Fusionmon.LOGGER.info("Fusionmon: el cuerpo de la fusión de {} ha hecho la Ultraexplosión",
                            owner.getName().getString());
                }
            } catch (RuntimeException | LinkageError e) {
                FusionSafety.failed("Ultraexplosión del cuerpo", e);
            }
        };
        if (delay > 0) {
            FusionMegas.afterOnServer(delay, change);
        } else {
            change.run();
        }
        return true;
    }

    /** En lugar de UltraGimmick.unUltraBurst: si la Ultraexplosión es del cuerpo, la deshace. True si lo ha hecho. */
    public static boolean unUltraBurstBody(Pokemon fusion) {
        if (!isBodyUltra(fusion)) {
            return false;
        }
        MegaShowdown.playEffect(fusion, EFFECT, false);
        clearBody(fusion);
        return true;
    }

    /**
     * Después de cambiarle el objeto a una fusión: si se le ha quitado el cristal con el cuerpo en Ultraexplosión, se
     * deshace (Mega Showdown solo lo hace si la cabeza es Necrozma). True si ha cambiado algo.
     */
    public static boolean revertBodyIfCrystalRemoved(Pokemon fusion, ItemStack returned, ItemStack received) {
        if (!isBodyUltra(fusion) || !CRYSTAL.equals(MegaShowdown.zCrystal(returned))
                || ItemStack.isSameItem(returned, received)) {
            return false;
        }
        clearBody(fusion);
        return true;
    }

    /** Al principio de AspectUtils.revertPokemonsIfRequired (al empezar un combate...): fuera la del cuerpo. */
    public static void revertBodyForBattle(Pokemon pokemon) {
        if (isBodyUltra(pokemon)) {
            clearBody(pokemon);
        }
    }

    /**
     * Antes de fusionar, invertir o separar: deshace la Ultraexplosión en el acto, sin animación (como
     * FusionMegas.revert): la del cuerpo de una fusión, o la del propio Pokémon (una parte, o la cabeza de una
     * fusión). True si la tenía.
     */
    public static boolean revert(Pokemon pokemon) {
        if (isBodyUltra(pokemon)) {
            FusionData.clearBodyUltra(pokemon);
            pokemon.setTradeable(true);
            return true;
        }
        if (!pokemon.getAspects().contains(ULTRA)) {
            return false;
        }
        CompoundTag data = pokemon.getPersistentData();
        String before = data.getString(NECROZMA_FORM);
        // Sin el dato de Mega Showdown no se sabe cuál era: Alas del Alba
        FusionMegas.applyStone(pokemon, List.of(before.isEmpty() ? "prism_fusion=dawn" : before));
        data.remove(NECROZMA_FORM);
        data.remove(REVERT_ASPECTS);
        pokemon.setTradeable(true);
        return true;
    }

    /**
     * Después de fusionar o invertir partes que estaban en Ultraexplosión: la fusión la vuelve a hacer en silencio si
     * lleva el cristal (la cabeza si es Necrozma en forma de fusión, si no el cuerpo). Antes de mandarla al cliente.
     */
    public static void ultraAgain(ServerPlayer player, Pokemon fusion) {
        try {
            if (!holdsCrystal(fusion)) {
                return;
            }
            if (headCanUltra(fusion)) {
                ultraPart(fusion);
            } else if (bodyCanUltra(fusion)) {
                ultraBody(player, fusion);
                fusion.setTradeable(false);
            }
        } catch (RuntimeException | LinkageError e) {
            Fusionmon.LOGGER.warn("Fusionmon: la fusión no ha podido volver a hacer la Ultraexplosión", e);
        }
    }

    /** Al separar una fusión en Ultraexplosión: la parte que lo estaba sale con el cristal y en Ultra. */
    public static void keepUltra(Pokemon part, ItemStack crystal) {
        try {
            if (!crystal.isEmpty()) {
                part.swapHeldItem(crystal, false, true);
            }
            ultraPart(part);
        } catch (RuntimeException | LinkageError e) {
            Fusionmon.LOGGER.warn("Fusionmon: la parte separada no ha podido seguir en Ultraexplosión", e);
        }
    }

    /** Para las pantallas de separar e invertir: la copia de la parte que está en Ultraexplosión, así. */
    public static void preview(Pokemon fused, FusionService.Parts parts) {
        try {
            if (isBodyUltra(fused)) {
                ultraPart(parts.body());
            } else if (fused.getAspects().contains(ULTRA)) {
                ultraPart(parts.head());
            }
        } catch (RuntimeException | LinkageError e) {
            Fusionmon.LOGGER.warn("Fusionmon: no se ha podido enseñar la Ultraexplosión en la pantalla", e);
        }
    }

    /** La Ultraexplosión de un Pokémon (una parte, o la cabeza de una fusión), en silencio, como la hace Mega Showdown. */
    private static void ultraPart(Pokemon pokemon) {
        String before = ultraAspect(pokemon.getAspects());
        if (before == null || !NECROZMA.equals(pokemon.getSpecies().getName())) {
            return;
        }
        pokemon.getPersistentData().putString(NECROZMA_FORM, before);
        MegaShowdown.appendRevertData(pokemon, EFFECT, List.of(before), REVERT_ASPECTS);
        FusionMegas.applyStone(pokemon, ULTRA_FEATURE);
        pokemon.setTradeable(false);
    }

    /** La forma Ultra del cuerpo: una copia suya cambiada, como capa encima (FusionData.setBodyUltra). */
    private static boolean ultraBody(ServerPlayer owner, Pokemon fusion) {
        Pokemon body = FusionData.readBody(fusion, owner.registryAccess());
        if (!FusionMegas.applyStone(body, ULTRA_FEATURE)) {
            Fusionmon.LOGGER.warn("Fusionmon: {} no cambia la forma de {}; no se hace la Ultraexplosión del cuerpo",
                    ULTRA_FEATURE, body.getSpecies().getName());
            return false;
        }
        FusionData.setBodyUltra(fusion, body);
        return true;
    }

    private static void clearBody(Pokemon fusion) {
        FusionData.clearBodyUltra(fusion);
        fusion.setTradeable(true);
        ServerPlayer owner = fusion.getOwnerPlayer();
        if (owner != null) {
            FusionService.resendToClient(owner, fusion);
        }
    }

    private static boolean holdsCrystal(Pokemon pokemon) {
        return CRYSTAL.equals(MegaShowdown.zCrystal(pokemon.heldItem()));
    }

    /** Si la cabeza (la especie visible) puede hacerla: entonces es de Mega Showdown, como siempre. */
    private static boolean headCanUltra(Pokemon fusion) {
        return NECROZMA.equals(fusion.getSpecies().getName()) && ultraAspect(fusion.getAspects()) != null;
    }

    private static boolean bodyCanUltra(Pokemon fusion) {
        FormData body = FusionData.bodyForm(fusion);
        return body != null && NECROZMA.equals(body.getSpecies().getName())
                && ultraAspect(FusionData.bodyAspects(fusion)) != null;
    }

    /** La característica de la forma de fusión de Necrozma ("prism_fusion=dawn"), o null si no está en una. */
    private static String ultraAspect(Set<String> aspects) {
        if (aspects.contains(DAWN)) {
            return "prism_fusion=dawn";
        }
        if (aspects.contains(DUSK)) {
            return "prism_fusion=dusk";
        }
        return null;
    }
}
