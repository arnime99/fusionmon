package com.arnau.fusionmon.client.screen;

import com.arnau.fusionmon.client.model.FusionGraft;
import com.arnau.fusionmon.network.FusionChoicePayload;
import com.arnau.fusionmon.network.FusionPreview;
import com.arnau.fusionmon.network.OpenFusionScreenPayload;
import com.cobblemon.mod.common.client.gui.PokemonGuiUtilsKt;
import com.cobblemon.mod.common.client.gui.ProfileTransformType;
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState;
import com.cobblemon.mod.common.entity.PoseType;
import com.cobblemon.mod.common.pokemon.RenderablePokemon;
import com.cobblemon.mod.common.util.math.QuaternionUtilsKt;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;

/**
 * Pantalla de confirmación de la fusión. Solo muestra y recoge la decisión:
 * la fusión la hace el servidor cuando recibe FusionChoicePayload.
 */
public class FusionConfirmScreen extends Screen {

    private static final int PANEL_HEIGHT = 258;
    private static final int OPTION_WIDTH = 110;
    private static final int DESCRIPTION_WIDTH = 2 * 110 + 8;
    private static final int DESCRIPTION_MAX_LINES = 3;
    private static final int GAP = 8;
    private static final int WHITE = 0xFFFFFF;
    private static final int GRAY = 0xAAAAAA;
    private static final int YELLOW = 0xFFFF55;
    private static final int GREEN = 0x55FF55;

    // Visor 3D (mismos valores que el modelo de la pantalla de resumen de Cobblemon, algo más grande)
    private static final int MODEL_BOX = 110;
    private static final float MODEL_SCALE = 2.4F;
    private static final double MODEL_OFFSET_Y = -10;
    private static final long MODEL_TURN_MILLIS = 8000;
    private static final int MODEL_LIGHT = 15;

    private static final String[] STAT_KEYS = {
            "gui.fusionmon.stat.hp", "gui.fusionmon.stat.attack", "gui.fusionmon.stat.defence",
            "gui.fusionmon.stat.special_attack", "gui.fusionmon.stat.special_defence", "gui.fusionmon.stat.speed"
    };

    private final OpenFusionScreenPayload data;
    private boolean swapped;
    private boolean natureFromB;
    private boolean abilityFromB;
    /** true cuando ya se ha mandado la respuesta (Aceptar o Cancelar), para no mandarla dos veces. */
    private boolean answered;
    /** Estado de animación del visor 3D (como los de los menús de Cobblemon: sin entidad detrás). */
    private final FloatingState previewState = new FloatingState();

    private Button natureAButton;
    private Button natureBButton;
    private Button abilityAButton;
    private Button abilityBButton;

    public FusionConfirmScreen(OpenFusionScreenPayload data) {
        super(Component.translatable("gui.fusionmon.confirm.title"));
        this.data = data;
    }

    @Override
    protected void init() {
        int centerX = width / 2;
        int top = top();

        addRenderableWidget(Button.builder(Component.translatable("gui.fusionmon.confirm.swap"), button -> swapped = !swapped)
                .bounds(centerX - 60, top + 86, 120, 20)
                .build());

        int leftX = centerX - OPTION_WIDTH - GAP / 2;
        int rightX = centerX + GAP / 2;

        // Al pasar el ratón por encima de una opción se ve su efecto o descripción (Tooltip)
        natureAButton = addRenderableWidget(Button.builder(Component.empty(), button -> {
            natureFromB = false;
            refreshOptionLabels();
        }).bounds(leftX, top + 126, OPTION_WIDTH, 20).tooltip(Tooltip.create(data.natureEffectA())).build());
        natureBButton = addRenderableWidget(Button.builder(Component.empty(), button -> {
            natureFromB = true;
            refreshOptionLabels();
        }).bounds(rightX, top + 126, OPTION_WIDTH, 20).tooltip(Tooltip.create(data.natureEffectB())).build());

        abilityAButton = addRenderableWidget(Button.builder(Component.empty(), button -> {
            abilityFromB = false;
            refreshOptionLabels();
        }).bounds(leftX, top + 178, OPTION_WIDTH, 20).tooltip(Tooltip.create(data.abilityDescriptionA())).build());
        abilityBButton = addRenderableWidget(Button.builder(Component.empty(), button -> {
            abilityFromB = true;
            refreshOptionLabels();
        }).bounds(rightX, top + 178, OPTION_WIDTH, 20).tooltip(Tooltip.create(data.abilityDescriptionB())).build());

        addRenderableWidget(Button.builder(Component.translatable("gui.fusionmon.confirm.accept"), button -> answer(true))
                .bounds(leftX, top + 236, OPTION_WIDTH, 20)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("gui.fusionmon.confirm.cancel"), button -> answer(false))
                .bounds(rightX, top + 236, OPTION_WIDTH, 20)
                .build());

        refreshOptionLabels();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        int centerX = width / 2;
        int top = top();
        FusionPreview preview = swapped ? data.swappedPreview() : data.preview();
        Component headName = swapped ? data.nameB() : data.nameA();
        Component bodyName = swapped ? data.nameA() : data.nameB();

        graphics.drawCenteredString(font, title, centerX, top, WHITE);
        graphics.drawCenteredString(font, Component.translatable("gui.fusionmon.confirm.parts", headName, bodyName),
                centerX, top + 16, GRAY);
        graphics.drawCenteredString(font, Component.literal("→ ").append(preview.name()), centerX, top + 30, YELLOW);
        graphics.drawCenteredString(font, Component.translatable("gui.fusionmon.confirm.types_level",
                joinTypes(preview.types()), preview.level()), centerX, top + 44, WHITE);

        graphics.drawCenteredString(font, statLine(preview.baseStats(), 0), centerX, top + 60, GRAY);
        graphics.drawCenteredString(font, statLine(preview.baseStats(), 3), centerX, top + 72, GRAY);

        graphics.drawCenteredString(font, Component.translatable("gui.fusionmon.confirm.nature"), centerX, top + 114, WHITE);
        graphics.drawCenteredString(font, natureFromB ? data.natureEffectB() : data.natureEffectA(),
                centerX, top + 150, GRAY);

        graphics.drawCenteredString(font, Component.translatable("gui.fusionmon.confirm.ability"), centerX, top + 166, WHITE);
        // Las descripciones de habilidad pueden ser largas: se parten en varias líneas
        List<FormattedCharSequence> lines = font.split(
                abilityFromB ? data.abilityDescriptionB() : data.abilityDescriptionA(), DESCRIPTION_WIDTH);
        for (int i = 0; i < Math.min(lines.size(), DESCRIPTION_MAX_LINES); i++) {
            graphics.drawCenteredString(font, lines.get(i), centerX, top + 202 + i * 10, GRAY);
        }

        // A la izquierda del panel (sin salirse de la pantalla si es estrecha)
        renderModel(graphics, preview, Math.max(4, centerX - DESCRIPTION_WIDTH / 2 - GAP - MODEL_BOX), top + 16,
                partialTick);
    }

    /**
     * Visor 3D de la fusión, a la izquierda del panel, girando despacio. Se pinta como cualquier Pokémon de un menú
     * de Cobblemon (mismos valores que su pantalla de resumen), así que sale como se verá en el juego según el modo
     * de /fusionvisual. Debajo, si el prototipo cabeza sobre cuerpo encuentra la cabeza de los dos modelos.
     */
    private void renderModel(GuiGraphics graphics, FusionPreview preview, int x, int y, float partialTick) {
        RenderablePokemon model = preview.model();
        previewState.setCurrentAspects(model.getAspects());

        graphics.fill(x - 1, y - 1, x + MODEL_BOX + 1, y + MODEL_BOX + 1, 0xFF555555);
        graphics.fill(x, y, x + MODEL_BOX, y + MODEL_BOX, 0xFF1E1E1E);

        // Lo que se salga de la caja no se pinta
        graphics.enableScissor(x, y, x + MODEL_BOX, y + MODEL_BOX);
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(x + MODEL_BOX / 2.0, y + MODEL_OFFSET_Y, 0);
        pose.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);
        float yaw = (System.currentTimeMillis() % MODEL_TURN_MILLIS) * 360F / MODEL_TURN_MILLIS;
        Quaternionf rotation = QuaternionUtilsKt.fromEulerXYZDegrees(new Quaternionf(), new Vector3f(13F, yaw, 0F));
        PokemonGuiUtilsKt.drawProfilePokemon(model, pose, rotation, PoseType.PROFILE, previewState, partialTick,
                20F, ProfileTransformType.SUMMARY, false, 1F, 1F, 1F, 1F, 0F, 0F, MODEL_LIGHT);
        pose.popPose();
        graphics.disableScissor();

        boolean graft = FusionGraft.canGraft(model.getSpecies().getResourceIdentifier(), previewState);
        Component label = Component.translatable(graft ? "gui.fusionmon.confirm.graft_yes" : "gui.fusionmon.confirm.graft_no");
        List<FormattedCharSequence> labelLines = font.split(label, MODEL_BOX + 16);
        for (int i = 0; i < labelLines.size(); i++) {
            graphics.drawCenteredString(font, labelLines.get(i), x + MODEL_BOX / 2, y + MODEL_BOX + 4 + i * 10,
                    graft ? GREEN : GRAY);
        }
    }

    /** removed() se llama siempre que la pantalla se cierra (Esc, otra pantalla...): cuenta como Cancelar. */
    @Override
    public void removed() {
        if (!answered && ClientPlayNetworking.canSend(FusionChoicePayload.TYPE)) {
            ClientPlayNetworking.send(FusionChoicePayload.cancel());
        }
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void answer(boolean accepted) {
        answered = true;
        ClientPlayNetworking.send(accepted
                ? new FusionChoicePayload(true, swapped, natureFromB, abilityFromB)
                : FusionChoicePayload.cancel());
        onClose();
    }

    /** Marca con ✔ la opción elegida de cada pareja. */
    private void refreshOptionLabels() {
        natureAButton.setMessage(option(data.natureA(), !natureFromB));
        natureBButton.setMessage(option(data.natureB(), natureFromB));
        abilityAButton.setMessage(option(data.abilityA(), !abilityFromB));
        abilityBButton.setMessage(option(data.abilityB(), abilityFromB));
    }

    private static Component option(Component label, boolean selected) {
        return selected ? Component.literal("✔ ").append(label) : label.copy();
    }

    private static Component joinTypes(List<Component> types) {
        MutableComponent result = Component.empty();
        for (int i = 0; i < types.size(); i++) {
            if (i > 0) {
                result.append(" / ");
            }
            result.append(types.get(i));
        }
        return result;
    }

    /** Tres stats a partir de "first": "PS 43   Ataque 51   Defensa 45". */
    private static Component statLine(List<Integer> stats, int first) {
        MutableComponent line = Component.empty();
        for (int i = first; i < first + 3 && i < stats.size(); i++) {
            if (i > first) {
                line.append("   ");
            }
            line.append(Component.translatable(STAT_KEYS[i])).append(" " + stats.get(i));
        }
        return line;
    }

    private int top() {
        return Math.max(4, height / 2 - PANEL_HEIGHT / 2);
    }
}
