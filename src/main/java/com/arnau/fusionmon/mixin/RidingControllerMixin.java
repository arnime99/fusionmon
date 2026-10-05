package com.arnau.fusionmon.mixin;

import com.arnau.fusionmon.fusion.FusionBodyForm;
import com.cobblemon.mod.common.api.riding.RidingProperties;
import com.cobblemon.mod.common.api.riding.behaviour.RidingController;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.FormData;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/** Al cambiar de estilo de montura (tierra, aire, agua...), los estilos posibles son los del cuerpo. */
@Mixin(value = RidingController.class, remap = false)
public abstract class RidingControllerMixin {

    @Shadow
    @Final
    private PokemonEntity entity;

    @WrapOperation(method = "changeBehaviour", at = @At(value = "INVOKE",
            target = "Lcom/cobblemon/mod/common/pokemon/FormData;getRiding()Lcom/cobblemon/mod/common/api/riding/RidingProperties;"))
    private RidingProperties fusionmon$bodyRiding(FormData form, Operation<RidingProperties> original) {
        return FusionBodyForm.riding(FusionBodyForm.of(entity), original.call(form));
    }
}
