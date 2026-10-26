package com.arnau.fusionmon.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * El paquete con el que Mega Showdown abre su rueda de interacción. El botón de mega solo sale si la especie visible
 * (la cabeza) tiene megas (shouldMega), aunque canMega diga que sí: con una cabeza sin megas (Munchlax + Blaziken) la
 * mega del cuerpo era imposible. Si canMega es true (ver MegaShowdownMegaGimmickMixin), el botón sale.
 * Se construye en el servidor con los datos ya calculados; en el cliente, al leerlo, no cambia nada (ya viene bien).
 */
@Pseudo
@Mixin(targets = "com.github.yajatkaul.mega_showdown.networking.client.packet.InteractionWheelPacket", remap = false)
public abstract class MegaShowdownInteractionWheelMixin {

    @Shadow
    @Final
    @Mutable
    private boolean shouldMega;

    @Shadow
    @Final
    private boolean canMega;

    /** Lo mismo con la Ultraexplosión: su botón solo salía si la cabeza es Necrozma (ver FusionUltra). */
    @Shadow
    @Final
    @Mutable
    private boolean shouldUltra;

    @Shadow
    @Final
    private boolean canUltra;

    @Inject(method = "<init>", require = 0, at = @At("RETURN"))
    private void fusionmon$showMegaWhenPossible(CallbackInfo ci) {
        if (canMega) {
            shouldMega = true;
        }
        if (canUltra) {
            shouldUltra = true;
        }
    }
}
