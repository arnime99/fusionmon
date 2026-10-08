package com.arnau.fusionmon.client.mixin;

import com.arnau.fusionmon.client.SummaryTooltip;
import com.arnau.fusionmon.client.texture.FusionBody;
import com.arnau.fusionmon.fusion.FusionData;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.client.gui.summary.widgets.screens.info.InfoWidget;
import com.cobblemon.mod.common.pokemon.FormData;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Pestaña "Info" del resumen de Cobblemon: en una fusión, la fila "Especie" dice qué lleva dentro ("Pikachu + Eevee",
 * cabeza + cuerpo) y, al pasar el ratón por encima, cuál es cada parte.
 *
 * Es el único sitio donde InfoWidget pide el nombre de la especie, así que se cambia esa llamada. renderWidget es un
 * método de Minecraft: con remap = false ponemos también su nombre del juego publicado (method_48579).
 */
@Mixin(value = InfoWidget.class, remap = false)
public abstract class InfoWidgetMixin {

    // Medidas de InfoWidget/InfoOneLineWidget de Cobblemon 1.8.1 (javap): ancho del panel, alto de fila, dónde
    // empieza el valor; la fila "Especie" es la segunda
    private static final int PANEL_WIDTH = 134;
    private static final int ROW_HEIGHT = 15;
    private static final int VALUE_X = 53;
    private static final int SPECIES_ROW_Y = 15;
    private static final int VALUE_MAX_WIDTH = PANEL_WIDTH - VALUE_X - 6;

    @Shadow
    @Final
    private Pokemon pokemon;

    @WrapOperation(method = {"renderWidget", "method_48579"}, at = @At(value = "INVOKE",
            target = "Lcom/cobblemon/mod/common/pokemon/Species;getTranslatedName"))
    private MutableComponent fusionmon$fusionSpecies(Species species, Operation<MutableComponent> original) {
        MutableComponent head = original.call(species);
        Species body = fusionmon$bodySpecies();
        if (body == null) {
            return head;
        }
        MutableComponent both = head.copy().append(" + ").append(body.getTranslatedName());
        // Dos nombres largos no caben en la fila: se recorta y el texto entero sale al pasar el ratón
        Font font = Minecraft.getInstance().font;
        if (font.width(both) <= VALUE_MAX_WIDTH) {
            return both;
        }
        return Component.literal(font.plainSubstrByWidth(both.getString(), VALUE_MAX_WIDTH - font.width("…")) + "…");
    }

    @Inject(method = {"renderWidget", "method_48579"}, at = @At("TAIL"))
    private void fusionmon$partsTooltip(GuiGraphics graphics, int mouseX, int mouseY, float partialTick,
                                        CallbackInfo ci) {
        Species body = fusionmon$bodySpecies();
        if (body == null) {
            return;
        }
        InfoWidget self = (InfoWidget) (Object) this;
        int rowY = self.getY() + SPECIES_ROW_Y;
        if (mouseX < self.getX() || mouseX >= self.getX() + PANEL_WIDTH || mouseY < rowY || mouseY >= rowY + ROW_HEIGHT) {
            return;
        }
        // No se pinta aquí: Cobblemon pinta el resumen adelantado en profundidad y lo taparía (ver SummaryTooltip)
        SummaryTooltip.show(List.of(
                Component.translatable("gui.fusionmon.info.fusion").getVisualOrderText(),
                Component.translatable("gui.fusionmon.info.head", pokemon.getSpecies().getTranslatedName())
                        .getVisualOrderText(),
                Component.translatable("gui.fusionmon.info.body", body.getTranslatedName()).getVisualOrderText()),
                mouseX, mouseY);
    }

    /**
     * La especie del cuerpo, o null si no es una fusión. Con los datos guardados si han llegado al cliente (envío
     * completo del Pokémon); si no, por los aspects, que siempre llegan (ver FusionAspects).
     */
    private Species fusionmon$bodySpecies() {
        FormData form = FusionData.bodyForm(pokemon);
        if (form != null) {
            return form.getSpecies();
        }
        FusionBody body = FusionBody.of(pokemon.getAspects());
        if (body == null) {
            return null;
        }
        // El aspect solo lleva la ruta ("eevee"): casi todas son de Cobblemon; si no, la primera con esa ruta
        Species species = PokemonSpecies.getByIdentifier(ResourceLocation.fromNamespaceAndPath("cobblemon", body.species()));
        if (species != null) {
            return species;
        }
        for (Species candidate : PokemonSpecies.getSpecies()) {
            if (candidate.getResourceIdentifier().getPath().equals(body.species())) {
                return candidate;
            }
        }
        return null;
    }
}
