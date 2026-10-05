package com.arnau.fusionmon.mixin;

import com.arnau.fusionmon.fusion.FusionBodyForm;
import com.cobblemon.mod.common.api.riding.RidingProperties;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.FormData;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.EntityDimensions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Una fusión en el mundo tiene el tamaño y la montura de su CUERPO, que es el modelo que se pinta
 * (ver FusionBodyForm). Cobblemon los lee de la forma de la entidad, que en una fusión es la de la cabeza.
 *
 * getDimensions y onSyncedDataUpdated son métodos de Minecraft que PokemonEntity sobrescribe. Con remap = false
 * sus nombres no se traducen: en desarrollo se llaman así y en el juego publicado "method_18377" y "method_5674"
 * (intermediary). Ponemos los dos nombres; en cada entorno solo existe uno. Por lo mismo, las llamadas cuya firma
 * lleva clases de Minecraft (getHitbox devuelve EntityDimensions) se buscan solo por nombre, sin firma.
 */
@Mixin(value = PokemonEntity.class, remap = false)
public abstract class PokemonEntityMixin {

    /** Asientos, propiedades de montura y controlador de montura (refreshRiding): los del cuerpo. */
    @WrapOperation(method = {"getSeats", "getRideProp", "refreshRiding"}, at = @At(value = "INVOKE",
            target = "Lcom/cobblemon/mod/common/pokemon/FormData;getRiding()Lcom/cobblemon/mod/common/api/riding/RidingProperties;"))
    private RidingProperties fusionmon$bodyRiding(FormData form, Operation<RidingProperties> original) {
        return FusionBodyForm.riding(FusionBodyForm.of((PokemonEntity) (Object) this), original.call(form));
    }

    /** Caja de colisión: escala del cuerpo... */
    @ModifyExpressionValue(method = {"getDimensions", "method_18377"}, at = @At(value = "INVOKE",
            target = "Lcom/cobblemon/mod/common/pokemon/FormData;getBaseScale()F"))
    private float fusionmon$bodyScale(float scale) {
        FormData body = FusionBodyForm.of((PokemonEntity) (Object) this);
        return body == null ? scale : body.getBaseScale();
    }

    /** ...y su caja (ancho y alto). */
    @ModifyExpressionValue(method = {"getDimensions", "method_18377"}, at = @At(value = "INVOKE",
            target = "Lcom/cobblemon/mod/common/pokemon/FormData;getHitbox"))
    private EntityDimensions fusionmon$bodyHitbox(EntityDimensions hitbox) {
        FormData body = FusionBodyForm.of((PokemonEntity) (Object) this);
        return body == null ? hitbox : body.getHitbox();
    }

    /**
     * Minecraft guarda la caja y solo la recalcula cuando se le pide. En el cliente los aspects (de donde sale el
     * cuerpo) llegan después de crear la entidad, y en el servidor cambian al fusionar o separar con el Pokémon
     * fuera: al cambiar, la recalculamos.
     */
    @Inject(method = {"onSyncedDataUpdated", "method_5674"}, at = @At("TAIL"))
    private void fusionmon$refreshBodyDimensions(EntityDataAccessor<?> data, CallbackInfo ci) {
        if (PokemonEntity.Companion.getASPECTS().equals(data)) {
            ((PokemonEntity) (Object) this).refreshDimensions();
        }
    }
}
