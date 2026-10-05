package com.arnau.fusionmon.client.mixin;

import com.arnau.fusionmon.fusion.FusionBodyForm;
import com.cobblemon.mod.common.api.riding.RidingProperties;
import com.cobblemon.mod.common.client.gui.summary.widgets.screens.stats.StatWidget;
import com.cobblemon.mod.common.pokemon.FormData;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Pestaña de estadísticas del resumen: la parte de montura (si sale la pestaña, sus estilos y sus estadísticas)
 * usa la montura del cuerpo, como el resto (ver FusionBodyForm.riding).
 *
 * renderWidget es un método de Minecraft: con remap = false ponemos también su nombre del juego publicado.
 */
@Mixin(value = StatWidget.class, remap = false)
public abstract class StatWidgetMixin {

    @Shadow
    @Final
    private Pokemon pokemon;

    @WrapOperation(method = {"<init>", "getRideBehaviourIndex", "setRideBehaviourIndex", "getTabIndexFromPos",
            "renderWidget", "method_48579"}, at = @At(value = "INVOKE",
            target = "Lcom/cobblemon/mod/common/pokemon/FormData;getRiding()Lcom/cobblemon/mod/common/api/riding/RidingProperties;"))
    private RidingProperties fusionmon$bodyRiding(FormData form, Operation<RidingProperties> original) {
        return FusionBodyForm.riding(FusionBodyForm.of(pokemon), original.call(form));
    }
}
