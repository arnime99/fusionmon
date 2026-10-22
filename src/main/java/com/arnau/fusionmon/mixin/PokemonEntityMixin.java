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
import org.spongepowered.asm.mixin.Unique;
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
 *
 * Todos con require = 0: si otro mod o una versión de Cobblemon cambia uno de estos métodos, esa parte se queda como
 * la de la cabeza (tamaño, montura, hombro) en vez de no arrancar el juego; CommonMixinPlugin lo avisa en el log.
 */
@Mixin(value = PokemonEntity.class, remap = false)
public abstract class PokemonEntityMixin {

    /** Asientos, propiedades de montura y controlador de montura (refreshRiding): los del cuerpo. */
    @WrapOperation(method = {"getSeats", "getRideProp", "refreshRiding"}, require = 0, at = @At(value = "INVOKE",
            target = "Lcom/cobblemon/mod/common/pokemon/FormData;getRiding()Lcom/cobblemon/mod/common/api/riding/RidingProperties;"))
    private RidingProperties fusionmon$bodyRiding(FormData form, Operation<RidingProperties> original) {
        return FusionBodyForm.riding(FusionBodyForm.of((PokemonEntity) (Object) this), original.call(form));
    }

    /** Caja de colisión: escala del cuerpo... */
    @ModifyExpressionValue(method = {"getDimensions", "method_18377"}, require = 0, at = @At(value = "INVOKE",
            target = "Lcom/cobblemon/mod/common/pokemon/FormData;getBaseScale()F"))
    private float fusionmon$bodyScale(float scale) {
        FormData body = FusionBodyForm.of((PokemonEntity) (Object) this);
        return body == null ? scale : body.getBaseScale();
    }

    /** ...y su caja (ancho y alto). */
    @ModifyExpressionValue(method = {"getDimensions", "method_18377"}, require = 0, at = @At(value = "INVOKE",
            target = "Lcom/cobblemon/mod/common/pokemon/FormData;getHitbox"))
    private EntityDimensions fusionmon$bodyHitbox(EntityDimensions hitbox) {
        FormData body = FusionBodyForm.of((PokemonEntity) (Object) this);
        return body == null ? hitbox : body.getHitbox();
    }

    /**
     * Hombro: se puede subir si se puede su CUERPO, que es el modelo y el tamaño que se ven ahí (decisión del usuario,
     * 2026-10-21; antes decidía la cabeza y se subía un Charizard con cabeza de Pikachu). canSitOnShoulder es de
     * Minecraft: "method_6626" en el juego publicado.
     */
    @ModifyExpressionValue(method = {"canSitOnShoulder", "method_6626"}, require = 0, at = @At(value = "INVOKE",
            target = "Lcom/cobblemon/mod/common/pokemon/FormData;getShoulderMountable()Z"))
    private boolean fusionmon$bodyShoulder(boolean mountable) {
        FormData body = FusionBodyForm.of((PokemonEntity) (Object) this);
        return body == null ? mountable : body.getShoulderMountable();
    }

    /** El cuerpo con el que se preparó la montura (ver fusionmon$refreshBody); null si no es una fusión. */
    @Unique
    private FormData fusionmon$ridingBody;

    /**
     * Minecraft guarda la caja y solo la recalcula cuando se le pide. En el cliente los aspects (de donde sale el
     * cuerpo) llegan después de crear la entidad, y en el servidor cambian al fusionar o separar con el Pokémon
     * fuera: al cambiar, la recalculamos.
     *
     * Con la montura pasa lo mismo: Cobblemon prepara su controlador (estilo, velocidades) una sola vez, al crear la
     * entidad (refreshRiding). En el cliente aún no se sabía que era una fusión y salía la montura de la CABEZA: si
     * la cabeza no se podía montar no había montura, y si se podía, el cliente la movía con la de la cabeza y el
     * servidor con la del cuerpo (velocidades distintas: el modelo se iba sin el jinete). Cuando cambia el cuerpo, se
     * vuelve a preparar. Solo entonces: refreshRiding baja a los jinetes.
     */
    @Inject(method = {"onSyncedDataUpdated", "method_5674"}, at = @At("TAIL"), require = 0)
    private void fusionmon$refreshBody(EntityDataAccessor<?> data, CallbackInfo ci) {
        if (!PokemonEntity.Companion.getASPECTS().equals(data)) {
            return;
        }
        PokemonEntity self = (PokemonEntity) (Object) this;
        self.refreshDimensions();
        // Mientras se construye la entidad aún no tiene Pokémon (y refreshRiding lo necesita)
        if (self.getPokemon() == null) {
            return;
        }
        FormData body = FusionBodyForm.of(self);
        if (body != fusionmon$ridingBody) {
            fusionmon$ridingBody = body;
            self.refreshRiding();
        }
    }
}
