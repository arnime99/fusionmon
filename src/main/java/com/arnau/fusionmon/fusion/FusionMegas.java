package com.arnau.fusionmon.fusion;

import com.cobblemon.mod.common.api.pokemon.feature.StringSpeciesFeature;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Megaevoluciones de Mega Showdown (mod opcional) al fusionar. Sin usar sus clases: solo su marca en persistentData y
 * su etiqueta de objetos, así que sin Mega Showdown nada de esto hace nada.
 *
 * La mega que se hace fuera de combate es solo de aspecto (Mega Showdown la deshace al empezar cada combate), pero
 * cambia la forma del Pokémon. Si se fusionaba así, la fusión guardaba la forma mega (tipos, stats, Fusion Album) y
 * Mega Showdown la deshacía un momento después al quitarle la piedra: la fusión quedaba a medias. Por eso las partes
 * se fusionan siempre en su forma normal, y la fusión se queda con la megapiedra.
 */
public final class FusionMegas {

    /** Marca que pone Mega Showdown en persistentData mientras dura una mega de fuera de combate. */
    private static final String IS_MEGA = "is_mega";
    /** La característica (species feature) con la que Mega Showdown cambia a la forma mega; "none" la deshace. */
    private static final String MEGA_FEATURE = "mega_evolution";
    private static final TagKey<Item> MEGA_STONES =
            TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("mega_showdown", "mega_stone"));

    private FusionMegas() {
    }

    /**
     * Si el Pokémon está megaevolucionado fuera de combate, lo devuelve a su forma normal en el acto (lo mismo que hace
     * Mega Showdown al deshacerla, sin su animación, que la retrasa). True si estaba en mega.
     */
    public static boolean revert(Pokemon pokemon) {
        CompoundTag data = pokemon.getPersistentData();
        if (data == null || !data.getBoolean(IS_MEGA)) {
            return false;
        }
        // La marca primero: si no, al quitarle la piedra Mega Showdown intentaría deshacerla otra vez
        data.remove(IS_MEGA);
        new StringSpeciesFeature(MEGA_FEATURE, "none").apply(pokemon);
        // Mega Showdown no deja intercambiar un Pokémon en mega
        pokemon.setTradeable(true);
        return true;
    }

    /** Le quita la megapiedra si lleva una (y la devuelve); si lleva otra cosa o nada, no toca nada y devuelve vacío. */
    public static ItemStack takeStone(Pokemon pokemon) {
        return pokemon.heldItem().is(MEGA_STONES) ? pokemon.removeHeldItem() : ItemStack.EMPTY;
    }
}
