package com.arnau.fusionmon.client.screen;

import com.arnau.fusionmon.client.model.FusionGraft;
import com.arnau.fusionmon.fusion.FusionAspects;
import com.arnau.fusionmon.fusion.FusionCalculator;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.api.pokemon.stats.Stat;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.client.gui.PokemonGuiUtilsKt;
import com.cobblemon.mod.common.client.gui.ProfileTransformType;
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState;
import com.cobblemon.mod.common.client.render.models.blockbench.repository.VaryingModelRepository;
import com.cobblemon.mod.common.entity.PoseType;
import com.cobblemon.mod.common.pokemon.FormData;
import com.cobblemon.mod.common.pokemon.Gender;
import com.cobblemon.mod.common.pokemon.RenderablePokemon;
import com.cobblemon.mod.common.pokemon.Species;
import com.cobblemon.mod.common.util.math.QuaternionUtilsKt;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

import java.text.Normalizer;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

/**
 * Visor de fusiones (/fusiondex): elige cabeza y cuerpo entre todas las especies con modelo y enseña la fusión en 3D,
 * con su nombre, tipos y stats. Todo en el cliente: no crea ningún Pokémon ni pasa por el servidor.
 *
 * La fusión se pinta como un Pokémon de un menú de Cobblemon (igual que la vista previa de FusionConfirmScreen), con
 * los aspects de fusión: pasa por FusionGraft y FusionTextures como cualquier fusión del juego, así que se ve como se
 * vería en el mundo con los modos de /fusionvisual (que aquí se cambian con botones).
 */
public class FusionDexScreen extends Screen {

    private static final int WHITE = 0xFFFFFF;
    private static final int GRAY = 0xAAAAAA;
    private static final int YELLOW = 0xFFFF55;
    private static final int GREEN = 0x55FF55;
    private static final int SMALL = 20;
    private static final int RANDOM_WIDTH = 40;
    private static final int GAP = 4;
    private static final int MARGIN = 8;
    private static final int ROW_Y = 20;
    private static final int TEXT_LINES = 4;
    private static final int LINE = 11;

    // Visor 3D: los valores de FusionConfirmScreen para una caja de 110 px, escalados al tamaño de la caja
    private static final int BASE_BOX = 110;
    private static final float BASE_SCALE = 2.4F;
    private static final double BASE_OFFSET_Y = -10;
    private static final int MODEL_LIGHT = 15;
    private static final float MIN_ZOOM = 0.25F;
    private static final float MAX_ZOOM = 5F;
    /** Mientras no se arrastre, el modelo gira solo (una vuelta en este tiempo). */
    private static final long TURN_MILLIS = 12000;

    private static final Stat[] STATS = {Stats.HP, Stats.ATTACK, Stats.DEFENCE, Stats.SPECIAL_ATTACK,
            Stats.SPECIAL_DEFENCE, Stats.SPEED};
    private static final String[] STAT_KEYS = {
            "gui.fusionmon.stat.hp", "gui.fusionmon.stat.attack", "gui.fusionmon.stat.defence",
            "gui.fusionmon.stat.special_attack", "gui.fusionmon.stat.special_defence", "gui.fusionmon.stat.speed"
    };

    // Lo elegido se recuerda al cerrar y volver a abrir (mientras el juego siga abierto)
    private static Species head;
    private static Species body;
    private static boolean shiny;
    private static boolean female;

    /** Estado de animación del visor (como los de los menús de Cobblemon: sin entidad detrás). */
    private final FloatingState previewState = new FloatingState();
    /** Especies con modelo, por número de la Pokédex. */
    private List<Species> species = List.of();

    private EditBox headBox;
    private EditBox bodyBox;
    private Button shinyButton;
    private Button genderButton;
    private Button modeButton;
    private Button tailButton;
    private Button decorButton;
    private Button topButton;

    // Cámara del visor: giro (grados), zoom y desplazamiento (píxeles)
    private float yaw = 30;
    private float pitch = 13;
    private float zoom = 1;
    private float panX;
    private float panY;
    /** Si se está arrastrando el modelo (con qué botón), y si ya se ha movido a mano (deja de girar solo). */
    private int dragButton = -1;
    private boolean manualCamera;

    public FusionDexScreen() {
        super(Component.translatable("gui.fusionmon.dex.title"));
    }

    @Override
    protected void init() {
        // Solo las especies con modelo en este cliente: las demás se pintarían con el muñeco sustituto
        species = PokemonSpecies.getImplemented().stream()
                .filter(s -> VaryingModelRepository.INSTANCE.getVariations().containsKey(s.getResourceIdentifier()))
                .sorted(Comparator.comparingInt(Species::getNationalPokedexNumber))
                .toList();
        if (species.isEmpty()) {
            return;
        }
        if (head == null || !species.contains(head)) {
            head = byNameOr("pikachu", 0);
        }
        if (body == null || !species.contains(body)) {
            body = byNameOr("charizard", 1);
        }

        // Fila de arriba: [cabeza][◀][▶][Azar]   [⇄]   [cuerpo][◀][▶][Azar], el cuadro de texto se encoge si no cabe
        int buttons = 2 * SMALL + RANDOM_WIDTH + 3 * GAP;
        int boxWidth = Mth.clamp((width - 2 * MARGIN - 2 * buttons - 30 - 2 * GAP) / 2, 50, 110);
        int x = MARGIN;
        headBox = addRenderableWidget(new EditBox(font, x, ROW_Y, boxWidth, SMALL,
                Component.translatable("gui.fusionmon.dex.head")));
        headBox.setHint(Component.translatable("gui.fusionmon.dex.head"));
        x += boxWidth + GAP;
        addRenderableWidget(Button.builder(Component.literal("◀"), b -> stepHead(-1)).bounds(x, ROW_Y, SMALL, SMALL).build());
        x += SMALL + GAP;
        addRenderableWidget(Button.builder(Component.literal("▶"), b -> stepHead(1)).bounds(x, ROW_Y, SMALL, SMALL).build());
        x += SMALL + GAP;
        addRenderableWidget(Button.builder(Component.translatable("gui.fusionmon.dex.random"), b -> setHead(random()))
                .bounds(x, ROW_Y, RANDOM_WIDTH, SMALL).build());

        addRenderableWidget(Button.builder(Component.literal("⇄"), b -> swap())
                .bounds(width / 2 - 15, ROW_Y, 30, SMALL).build());

        x = width - MARGIN - boxWidth - buttons;
        bodyBox = addRenderableWidget(new EditBox(font, x, ROW_Y, boxWidth, SMALL,
                Component.translatable("gui.fusionmon.dex.body")));
        bodyBox.setHint(Component.translatable("gui.fusionmon.dex.body"));
        x += boxWidth + GAP;
        addRenderableWidget(Button.builder(Component.literal("◀"), b -> stepBody(-1)).bounds(x, ROW_Y, SMALL, SMALL).build());
        x += SMALL + GAP;
        addRenderableWidget(Button.builder(Component.literal("▶"), b -> stepBody(1)).bounds(x, ROW_Y, SMALL, SMALL).build());
        x += SMALL + GAP;
        addRenderableWidget(Button.builder(Component.translatable("gui.fusionmon.dex.random"), b -> setBody(random()))
                .bounds(x, ROW_Y, RANDOM_WIDTH, SMALL).build());

        // Al escribir se elige la especie que mejor encaje; el texto se deja como está para seguir escribiendo
        headBox.setValue(displayName(head));
        bodyBox.setValue(displayName(body));
        headBox.setResponder(text -> {
            Species found = search(text);
            if (found != null) {
                head = found;
            }
        });
        bodyBox.setResponder(text -> {
            Species found = search(text);
            if (found != null) {
                body = found;
            }
        });

        // Fila de abajo: aspecto y modos de /fusionvisual
        int toggleWidth = Math.min(110, (width - 2 * MARGIN - 5 * GAP) / 6);
        x = width / 2 - (6 * toggleWidth + 5 * GAP) / 2;
        int y = height - SMALL - 4;
        shinyButton = addRenderableWidget(Button.builder(Component.empty(), b -> shiny = !shiny)
                .bounds(x, y, toggleWidth, SMALL).build());
        x += toggleWidth + GAP;
        genderButton = addRenderableWidget(Button.builder(Component.empty(), b -> female = !female)
                .bounds(x, y, toggleWidth, SMALL).build());
        x += toggleWidth + GAP;
        modeButton = addRenderableWidget(Button.builder(Component.empty(),
                b -> FusionGraft.setEnabled(!FusionGraft.isEnabled())).bounds(x, y, toggleWidth, SMALL).build());
        x += toggleWidth + GAP;
        tailButton = addRenderableWidget(Button.builder(Component.empty(),
                b -> FusionGraft.setTails(!FusionGraft.hasTails())).bounds(x, y, toggleWidth, SMALL).build());
        x += toggleWidth + GAP;
        decorButton = addRenderableWidget(Button.builder(Component.empty(),
                b -> FusionGraft.setDecorations(!FusionGraft.hasDecorations())).bounds(x, y, toggleWidth, SMALL).build());
        x += toggleWidth + GAP;
        topButton = addRenderableWidget(Button.builder(Component.empty(),
                b -> FusionGraft.setTops(!FusionGraft.hasTops())).bounds(x, y, toggleWidth, SMALL).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 6, WHITE);
        if (species.isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable("gui.fusionmon.dex.none"), width / 2, height / 2, GRAY);
            return;
        }
        refreshToggleLabels();

        // Qué especie ha elegido cada cuadro de texto
        int namesY = ROW_Y + SMALL + 3;
        graphics.drawString(font, numbered(head), MARGIN, namesY, GRAY);
        Component bodyLabel = numbered(body);
        graphics.drawString(font, bodyLabel, width - MARGIN - font.width(bodyLabel), namesY, GRAY);

        // El visor, cuadrado, entre las dos filas de botones y con sitio debajo para los textos
        int boxTop = namesY + LINE + 2;
        int boxBottom = height - SMALL - 8 - TEXT_LINES * LINE;
        int box = Math.max(40, Math.min(width - 2 * MARGIN, boxBottom - boxTop));
        int boxX = width / 2 - box / 2;
        RenderablePokemon model = new RenderablePokemon(head, aspects(), ItemStack.EMPTY);
        renderModel(graphics, model, boxX, boxTop, box, partialTick);

        // Debajo: nombre, tipos, stats y si se ha encontrado la cabeza
        FormData headForm = head.getStandardForm();
        FormData bodyForm = body.getStandardForm();
        int y = boxTop + box + 3;
        MutableComponent name = Component.literal(FusionCalculator.name(
                head.getTranslatedName().getString(), body.getTranslatedName().getString()));
        graphics.drawCenteredString(font, name.append(Component.literal("  ·  ").withColor(GRAY))
                .append(types(FusionCalculator.types(headForm, bodyForm)).copy().withColor(WHITE)), width / 2, y, YELLOW);
        y += LINE;
        int total = 0;
        MutableComponent stats = Component.empty();
        for (int i = 0; i < STATS.length; i++) {
            int value = FusionCalculator.baseStat(headForm, bodyForm, STATS[i]);
            total += value;
            if (i > 0) {
                stats.append("  ");
            }
            stats.append(Component.translatable(STAT_KEYS[i])).append(" " + value);
        }
        stats.append("  ").append(Component.translatable("gui.fusionmon.dex.total", total));
        graphics.drawCenteredString(font, stats, width / 2, y, GRAY);
        y += LINE;
        boolean graft = FusionGraft.canGraft(head.getResourceIdentifier(), previewState);
        Component status = Component.translatable(head == body ? "gui.fusionmon.dex.same"
                : graft ? "gui.fusionmon.confirm.graft_yes" : "gui.fusionmon.confirm.graft_no");
        graphics.drawCenteredString(font, status, width / 2, y, graft ? GREEN : GRAY);
        y += LINE;
        graphics.drawCenteredString(font, Component.translatable("gui.fusionmon.dex.help"), width / 2, y, GRAY);
    }

    /** Visor 3D: como el de FusionConfirmScreen, más grande, y se puede girar, mover y acercar. */
    private void renderModel(GuiGraphics graphics, RenderablePokemon model, int x, int y, int box, float partialTick) {
        previewState.setCurrentAspects(model.getAspects());
        graphics.fill(x - 1, y - 1, x + box + 1, y + box + 1, 0xFF555555);
        graphics.fill(x, y, x + box, y + box, 0xFF1E1E1E);

        float factor = (float) box / BASE_BOX;
        float shownYaw = manualCamera ? yaw : yaw + (System.currentTimeMillis() % TURN_MILLIS) * 360F / TURN_MILLIS;
        // Lo que se salga de la caja no se pinta
        graphics.enableScissor(x, y, x + box, y + box);
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(x + box / 2.0 + panX, y + BASE_OFFSET_Y * factor + panY, 0);
        float scale = BASE_SCALE * factor * zoom;
        pose.scale(scale, scale, scale);
        Quaternionf rotation = QuaternionUtilsKt.fromEulerXYZDegrees(new Quaternionf(), new Vector3f(pitch, shownYaw, 0F));
        PokemonGuiUtilsKt.drawProfilePokemon(model, pose, rotation, PoseType.PROFILE, previewState, partialTick,
                20F, ProfileTransformType.SUMMARY, false, 1F, 1F, 1F, 1F, 0F, 0F, MODEL_LIGHT);
        pose.popPose();
        graphics.disableScissor();
    }

    /**
     * Aspects de la fusión, como los tendría en el juego: los de la cabeza (sexo, shiny) más los de fusión con los del
     * cuerpo (ver FusionAspects).
     */
    private Set<String> aspects() {
        Set<String> aspects = partAspects(head);
        aspects.addAll(FusionAspects.fusionAspects(body, partAspects(body)));
        return aspects;
    }

    /** Sexo (el elegido si la especie lo tiene; si no, el que tenga) y shiny de una parte. */
    private static Set<String> partAspects(Species part) {
        Set<String> aspects = new HashSet<>();
        if (shiny) {
            aspects.add("shiny");
        }
        Set<Gender> genders = part.getPossibleGenders();
        if (female && genders.contains(Gender.FEMALE) || !genders.contains(Gender.MALE) && genders.contains(Gender.FEMALE)) {
            aspects.add("female");
        } else if (genders.contains(Gender.MALE)) {
            aspects.add("male");
        }
        return aspects;
    }

    // ---- Ratón y teclado ----

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        // Un clic fuera de los cuadros de texto les quita el foco: así las flechas vuelven a cambiar de especie
        setFocused(null);
        dragButton = button;
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragButton = -1;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragButton == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (!manualCamera) {
                // Seguir desde donde estaba girando solo
                yaw += (System.currentTimeMillis() % TURN_MILLIS) * 360F / TURN_MILLIS;
                manualCamera = true;
            }
            yaw += (float) dragX;
            pitch = Mth.clamp(pitch + (float) dragY, -90F, 90F);
            return true;
        }
        if (dragButton == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            panX += (float) dragX;
            panY += (float) dragY;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        zoom = Mth.clamp(zoom * (scrollY > 0 ? 1.1F : 1 / 1.1F), MIN_ZOOM, MAX_ZOOM);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Escribiendo en un cuadro de texto las teclas son suyas (salvo Esc, que lo gestiona Screen)
        boolean typing = getFocused() instanceof EditBox editBox && editBox.isFocused();
        if (!typing && !species.isEmpty()) {
            switch (keyCode) {
                case GLFW.GLFW_KEY_LEFT -> stepBody(-1);
                case GLFW.GLFW_KEY_RIGHT -> stepBody(1);
                case GLFW.GLFW_KEY_UP -> stepHead(-1);
                case GLFW.GLFW_KEY_DOWN -> stepHead(1);
                case GLFW.GLFW_KEY_R -> setBody(random());
                case GLFW.GLFW_KEY_S -> swap();
                case GLFW.GLFW_KEY_C -> resetCamera();
                default -> {
                    return super.keyPressed(keyCode, scanCode, modifiers);
                }
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        // Sin pausa, las animaciones del modelo siguen
        return false;
    }

    // ---- Elegir especies ----

    private void stepHead(int step) {
        setHead(species.get(Math.floorMod(species.indexOf(head) + step, species.size())));
    }

    private void stepBody(int step) {
        setBody(species.get(Math.floorMod(species.indexOf(body) + step, species.size())));
    }

    private void setHead(Species value) {
        head = value;
        headBox.setValue(displayName(value));
    }

    private void setBody(Species value) {
        body = value;
        bodyBox.setValue(displayName(value));
    }

    private void swap() {
        Species oldHead = head;
        setHead(body);
        setBody(oldHead);
    }

    private void resetCamera() {
        yaw = 30;
        pitch = 13;
        zoom = 1;
        panX = 0;
        panY = 0;
        manualCamera = false;
    }

    private Species random() {
        return species.get(ThreadLocalRandom.current().nextInt(species.size()));
    }

    private Species byNameOr(String name, int fallback) {
        Species found = search(name);
        return found != null ? found : species.get(Math.min(fallback, species.size() - 1));
    }

    /**
     * La especie que mejor encaja con lo escrito: el número de la Pokédex, o el nombre (en el idioma del juego o el
     * interno) igual, que empiece o que contenga el texto, en ese orden; sin mayúsculas ni tildes. null si ninguna.
     */
    private Species search(String text) {
        String query = normalize(text);
        if (query.isEmpty()) {
            return null;
        }
        if (query.chars().allMatch(Character::isDigit)) {
            int number = Integer.parseInt(query.length() > 6 ? query.substring(0, 6) : query);
            return first(s -> s.getNationalPokedexNumber() == number);
        }
        Species found = first(s -> names(s).anyMatch(query::equals));
        if (found == null) {
            found = first(s -> names(s).anyMatch(name -> name.startsWith(query)));
        }
        if (found == null) {
            found = first(s -> names(s).anyMatch(name -> name.contains(query)));
        }
        return found;
    }

    private Species first(Predicate<Species> condition) {
        for (Species candidate : species) {
            if (condition.test(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static java.util.stream.Stream<String> names(Species species) {
        return java.util.stream.Stream.of(normalize(species.getTranslatedName().getString()),
                normalize(species.getResourceIdentifier().getPath()));
    }

    private static String normalize(String text) {
        return Normalizer.normalize(text.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
    }

    // ---- Textos ----

    private void refreshToggleLabels() {
        shinyButton.setMessage(Component.translatable("gui.fusionmon.dex.shiny", onOff(shiny)));
        genderButton.setMessage(Component.translatable(female ? "gui.fusionmon.dex.female" : "gui.fusionmon.dex.male"));
        modeButton.setMessage(Component.translatable(FusionGraft.isEnabled()
                ? "gui.fusionmon.dex.mode.graft" : "gui.fusionmon.dex.mode.colors"));
        tailButton.setMessage(Component.translatable("gui.fusionmon.dex.tail", onOff(FusionGraft.hasTails())));
        decorButton.setMessage(Component.translatable("gui.fusionmon.dex.decor", onOff(FusionGraft.hasDecorations())));
        topButton.setMessage(Component.translatable("gui.fusionmon.dex.top", onOff(FusionGraft.hasTops())));
    }

    private static Component onOff(boolean value) {
        return Component.translatable(value ? "gui.fusionmon.dex.on" : "gui.fusionmon.dex.off");
    }

    private static String displayName(Species species) {
        return species.getTranslatedName().getString();
    }

    private static Component numbered(Species species) {
        return Component.literal(String.format("#%04d ", species.getNationalPokedexNumber()))
                .append(species.getTranslatedName());
    }

    private static Component types(List<ElementalType> types) {
        MutableComponent result = Component.empty();
        for (int i = 0; i < types.size(); i++) {
            if (i > 0) {
                result.append(" / ");
            }
            result.append(types.get(i).getDisplayName());
        }
        return result;
    }
}
