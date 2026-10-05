package com.arnau.fusionmon.mixin;

import com.arnau.fusionmon.fusion.FusionCalculator;
import com.arnau.fusionmon.fusion.FusionData;
import com.arnau.fusionmon.fusion.FusionEvolutions;
import com.arnau.fusionmon.fusion.FusionShowdown;
import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.pokemon.evolution.Evolution;
import com.cobblemon.mod.common.api.pokemon.moves.Learnset;
import com.cobblemon.mod.common.api.pokemon.moves.LearnsetQuery;
import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.pokemon.FormData;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Inyecta código al principio de varios métodos de Pokemon de Cobblemon: si el Pokémon es una fusión,
 * devolvemos nuestro valor y el método original no llega a ejecutarse. Si no lo es, no tocamos nada.
 * (@WrapOperation, de MixinExtras, que viene con Fabric Loader: envuelve una sola llamada dentro de un método.)
 *
 * remap = false: Pokemon es una clase de Cobblemon, no de Minecraft, así que sus nombres no se traducen.
 */
@Mixin(value = Pokemon.class, remap = false)
public abstract class PokemonMixin {

    @Inject(method = "getPrimaryType", at = @At("HEAD"), cancellable = true)
    private void fusionmon$getPrimaryType(CallbackInfoReturnable<ElementalType> cir) {
        List<ElementalType> types = fusionmon$fusedTypes();
        if (types != null) {
            cir.setReturnValue(types.get(0));
        }
    }

    @Inject(method = "getSecondaryType", at = @At("HEAD"), cancellable = true)
    private void fusionmon$getSecondaryType(CallbackInfoReturnable<ElementalType> cir) {
        List<ElementalType> types = fusionmon$fusedTypes();
        if (types != null) {
            cir.setReturnValue(types.size() > 1 ? types.get(1) : null);
        }
    }

    @Inject(method = "getTypes", at = @At("HEAD"), cancellable = true)
    private void fusionmon$getTypes(CallbackInfoReturnable<Iterable<ElementalType>> cir) {
        List<ElementalType> types = fusionmon$fusedTypes();
        if (types != null) {
            cir.setReturnValue(types);
        }
    }

    @Inject(method = "getDisplayName", at = @At("HEAD"), cancellable = true)
    private void fusionmon$getDisplayName(boolean showTitle, CallbackInfoReturnable<MutableComponent> cir) {
        Pokemon self = (Pokemon) (Object) this;
        // Un mote puesto por el jugador tiene prioridad sobre el nombre de fusión
        if (self.getNickname() != null) {
            return;
        }

        FormData head = FusionData.headForm(self);
        FormData body = FusionData.bodyForm(self);
        if (head != null && body != null) {
            cir.setReturnValue(Component.literal(
                    FusionCalculator.name(head.getSpecies().getName(), body.getSpecies().getName())));
        }
    }

    /** La especie que se le dice a Showdown en combate: la de la fusión (registrada por FusionShowdown). */
    @Inject(method = "showdownId", at = @At("HEAD"), cancellable = true)
    private void fusionmon$showdownId(CallbackInfoReturnable<String> cir) {
        Pokemon self = (Pokemon) (Object) this;
        FormData head = FusionData.headForm(self);
        FormData body = FusionData.bodyForm(self);
        if (head != null && body != null) {
            cir.setReturnValue(FusionShowdown.speciesId(head, body));
        }
    }

    /** Las evoluciones de una fusión son las de su cabeza y su cuerpo guardados (ver FusionEvolutions). */
    @Inject(method = "getEvolutions", at = @At("HEAD"), cancellable = true)
    private void fusionmon$getEvolutions(CallbackInfoReturnable<Iterable<Evolution>> cir) {
        List<Evolution> evolutions = FusionEvolutions.evolutionsOf((Pokemon) (Object) this);
        if (evolutions != null) {
            cir.setReturnValue(evolutions);
        }
    }

    /**
     * Al cambiar de forma (setForm, también al evolucionar), Cobblemon borra los movimientos recordables que la
     * nueva especie no puede aprender. En una fusión eso se llevaba los del cuerpo al evolucionar la cabeza:
     * aquí, si es una fusión, todo recordable cuenta como aprendible y no se borra ninguno.
     */
    @WrapOperation(method = "updateMovesOnFormChange", at = @At(value = "INVOKE",
            target = "Lcom/cobblemon/mod/common/api/pokemon/moves/LearnsetQuery;canLearn(Lcom/cobblemon/mod/common/api/moves/MoveTemplate;Lcom/cobblemon/mod/common/api/pokemon/moves/Learnset;)Z"))
    private boolean fusionmon$keepBenchedMoves(LearnsetQuery query, MoveTemplate move, Learnset learnset,
                                               Operation<Boolean> original) {
        if (FusionData.isFusion((Pokemon) (Object) this)) {
            return true;
        }
        return original.call(query, move, learnset);
    }

    private List<ElementalType> fusionmon$fusedTypes() {
        Pokemon self = (Pokemon) (Object) this;
        FormData head = FusionData.headForm(self);
        FormData body = FusionData.bodyForm(self);
        return head != null && body != null ? FusionCalculator.types(head, body) : null;
    }
}
