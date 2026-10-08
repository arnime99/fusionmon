package com.arnau.fusionmon.client.mixin;

import com.arnau.fusionmon.client.SummaryTooltip;
import com.cobblemon.mod.common.client.gui.summary.Summary;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Pantalla de resumen de Cobblemon: pinta al final los tooltips de Fusionmon, por delante de sus paneles (ver
 * SummaryTooltip). render es un método de Minecraft: con remap = false ponemos también su nombre del juego publicado.
 */
@Mixin(value = Summary.class, remap = false)
public abstract class SummaryMixin {

    @Inject(method = {"render", "method_25394"}, at = @At("TAIL"))
    private void fusionmon$renderTooltip(GuiGraphics graphics, int mouseX, int mouseY, float partialTick,
                                         CallbackInfo ci) {
        SummaryTooltip.render(graphics);
    }
}
