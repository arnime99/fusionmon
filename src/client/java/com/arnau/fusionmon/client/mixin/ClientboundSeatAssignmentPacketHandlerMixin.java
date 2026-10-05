package com.arnau.fusionmon.client.mixin;

import com.arnau.fusionmon.fusion.FusionBodyForm;
import com.cobblemon.mod.common.api.riding.RidingProperties;
import com.cobblemon.mod.common.client.net.pokemon.update.ClientboundSeatAssignmentPacketHandler;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.FormData;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** El servidor dice en qué asiento va cada jinete: el cliente busca ese asiento en la montura del cuerpo. */
@Mixin(value = ClientboundSeatAssignmentPacketHandler.class, remap = false)
public abstract class ClientboundSeatAssignmentPacketHandlerMixin {

    @WrapOperation(method = "handle", at = @At(value = "INVOKE",
            target = "Lcom/cobblemon/mod/common/pokemon/FormData;getRiding()Lcom/cobblemon/mod/common/api/riding/RidingProperties;"))
    private RidingProperties fusionmon$bodyRiding(FormData form, Operation<RidingProperties> original,
                                                  @Local PokemonEntity vehicle) {
        return FusionBodyForm.riding(FusionBodyForm.of(vehicle), original.call(form));
    }
}
