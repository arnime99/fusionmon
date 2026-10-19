package com.arnau.fusionmon.client.screen;

import com.arnau.fusionmon.Fusionmon;
import com.arnau.fusionmon.client.model.FusionGraft;
import com.arnau.fusionmon.fusion.FusionAspects;
import com.arnau.fusionmon.fusion.FusionCalculator;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.api.pokemon.stats.Stat;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState;
import com.cobblemon.mod.common.client.render.models.blockbench.repository.VaryingModelRepository;
import com.cobblemon.mod.common.pokemon.FormData;
import com.cobblemon.mod.common.pokemon.Gender;
import com.cobblemon.mod.common.pokemon.RenderablePokemon;
import com.cobblemon.mod.common.pokemon.Species;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.text.Normalizer;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

/**
 * Visor de todas las fusiones (/fusionviewer, solo en desarrollo): elige cabeza y cuerpo entre todas las especies con modelo y enseña la fusión en 3D,
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
    /** Pintar la fusión en negro, como sin descubrir (tecla B): para portadas "X + Y = ???". */
    private static boolean silhouette;
    /** Qué se ve en el visor (tecla V): la fusión, o una de las partes sin fusionar (para sacar su imagen). */
    private static View view = View.FUSION;

    private enum View { FUSION, HEAD, BODY }

    /** Lado de las imágenes que se guardan con la tecla P, en píxeles: de sobra para una portada. */
    private static final int RENDER_SIZE = 2048;
    private static final long MESSAGE_MILLIS = 4000;
    private static final DateTimeFormatter RENDER_TIME = DateTimeFormatter.ofPattern("HHmmss");

    /** Se ha pulsado P: la imagen se guarda en el siguiente render (ver render). */
    private boolean exportRequested;
    /** Aviso de la última imagen guardada (o del error), y hasta cuándo se enseña. */
    private Component exportMessage;
    private long exportMessageUntil;

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
    private Button alignButton;
    private Button sizeButton;

    /** El visor 3D (cámara y encuadre). */
    private final ModelViewport viewport = new ModelViewport();

    public FusionDexScreen() {
        super(Component.translatable("gui.fusionmon.dex.title"));
    }

    @Override
    protected void init() {
        // Todas las especies con modelo en este cliente (las demás se pintarían con el muñeco sustituto), aunque no
        // estén marcadas como "implementadas": Gulpin tiene modelo en Cobblemon y Darkrai en AllTheMons, y no salían
        species = PokemonSpecies.getSpecies().stream()
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
        int toggleWidth = Math.min(110, (width - 2 * MARGIN - 7 * GAP) / 8);
        x = width / 2 - (8 * toggleWidth + 7 * GAP) / 2;
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
        x += toggleWidth + GAP;
        alignButton = addRenderableWidget(Button.builder(Component.empty(),
                b -> FusionGraft.setAlign(FusionGraft.Align.values()[(FusionGraft.getAlign().ordinal() + 1)
                        % FusionGraft.Align.values().length])).bounds(x, y, toggleWidth, SMALL).build());
        x += toggleWidth + GAP;
        sizeButton = addRenderableWidget(Button.builder(Component.empty(),
                b -> FusionGraft.setSizing(FusionGraft.Sizing.values()[(FusionGraft.getSizing().ordinal() + 1)
                        % FusionGraft.Sizing.values().length])).bounds(x, y, toggleWidth, SMALL).build());
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
        // Una parte sola se pinta como un Pokémon normal: sin los aspects de fusión no pasa por el graft
        RenderablePokemon model = switch (view) {
            case FUSION -> new RenderablePokemon(head, aspects(), ItemStack.EMPTY);
            case HEAD -> new RenderablePokemon(head, partAspects(head), ItemStack.EMPTY);
            case BODY -> new RenderablePokemon(body, partAspects(body), ItemStack.EMPTY);
        };
        // Los modos de /fusionvisual cambian lo que se pinta: hay que volver a encuadrar
        String modes = "" + FusionGraft.isEnabled() + FusionGraft.hasTails() + FusionGraft.hasDecorations()
                + FusionGraft.hasTops() + FusionGraft.getAlign() + FusionGraft.getSizing();
        viewport.render(graphics, model, previewState, modes, boxX, boxTop, box, partialTick, null, null, silhouette);
        // Esquina del visor: qué se está viendo, si no es la fusión normal (lo que saldrá en la imagen con P)
        Component viewLabel = viewLabel();
        if (viewLabel != null) {
            graphics.drawString(font, viewLabel, boxX + 4, boxTop + 4, YELLOW);
        }
        if (exportRequested) {
            // Aquí y no al pulsar la tecla: hace falta la matriz de la interfaz que se pone para pintar la pantalla
            exportRequested = false;
            export(model, modes, box, partialTick);
        }

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
        if (exportMessage != null && System.currentTimeMillis() < exportMessageUntil) {
            // La última imagen guardada tapa un momento la línea de si encuentra la cabeza
            graphics.drawCenteredString(font, exportMessage, width / 2, y, YELLOW);
        } else {
            boolean graft = FusionGraft.canGraft(head.getResourceIdentifier(), previewState);
            Component status = Component.translatable(head == body ? "gui.fusionmon.dex.same"
                    : graft ? "gui.fusionmon.confirm.graft_yes" : "gui.fusionmon.confirm.graft_no");
            graphics.drawCenteredString(font, status, width / 2, y, graft ? GREEN : GRAY);
        }
        y += LINE;
        graphics.drawCenteredString(font, Component.translatable("gui.fusionmon.dex.help"), width / 2, y, GRAY);
    }

    /**
     * Guarda lo que se ve en el visor como PNG con el fondo transparente en fusionmon/renders (en runClient,
     * run/fusionmon/renders), y lo avisa aquí y en el chat con un enlace al archivo.
     */
    private void export(RenderablePokemon model, String modes, int box, float partialTick) {
        String name = switch (view) {
            case FUSION -> head.getResourceIdentifier().getPath()
                    + (head == body ? "" : "+" + body.getResourceIdentifier().getPath());
            case HEAD -> head.getResourceIdentifier().getPath();
            case BODY -> body.getResourceIdentifier().getPath();
        } + (shiny ? "_shiny" : "") + (female ? "_female" : "") + (silhouette ? "_silhouette" : "")
                + "_" + LocalTime.now().format(RENDER_TIME) + ".png";
        File file = new File(new File(Minecraft.getInstance().gameDirectory, "fusionmon/renders"), name);
        try {
            Files.createDirectories(file.getParentFile().toPath());
            viewport.export(model, previewState, modes, box, RENDER_SIZE, silhouette, partialTick, file);
            exportMessage = Component.translatable("gui.fusionmon.dex.saved", name);
            Component link = Component.literal(name).withStyle(style -> style.withUnderlined(true)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, file.getAbsolutePath())));
            Minecraft.getInstance().gui.getChat().addMessage(Component.translatable("gui.fusionmon.dex.saved", link));
        } catch (IOException | RuntimeException e) {
            Fusionmon.LOGGER.error("No se ha podido guardar la imagen {}", file, e);
            exportMessage = Component.translatable("gui.fusionmon.dex.save_failed").withColor(0xFF5555);
        }
        exportMessageUntil = System.currentTimeMillis() + MESSAGE_MILLIS;
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
        viewport.press(button);
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        viewport.release();
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return viewport.drag(dragX, dragY) || super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        viewport.scroll(scrollY);
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
                case GLFW.GLFW_KEY_C -> viewport.reset();
                case GLFW.GLFW_KEY_B -> silhouette = !silhouette;
                case GLFW.GLFW_KEY_V -> view = View.values()[(view.ordinal() + 1) % View.values().length];
                case GLFW.GLFW_KEY_P -> exportRequested = true;
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
        alignButton.setMessage(Component.translatable("gui.fusionmon.dex.align."
                + FusionGraft.getAlign().name().toLowerCase(Locale.ROOT)));
        sizeButton.setMessage(Component.translatable("gui.fusionmon.dex.size."
                + FusionGraft.getSizing().name().toLowerCase(Locale.ROOT)));
    }

    /** "Solo cabeza: Pikachu", "Silueta"... o null si se ve la fusión normal. */
    private static Component viewLabel() {
        MutableComponent label = switch (view) {
            case FUSION -> null;
            case HEAD -> Component.translatable("gui.fusionmon.dex.view.head", head.getTranslatedName());
            case BODY -> Component.translatable("gui.fusionmon.dex.view.body", body.getTranslatedName());
        };
        if (!silhouette) {
            return label;
        }
        Component black = Component.translatable("gui.fusionmon.dex.view.silhouette");
        return label == null ? black : label.append(" · ").append(black);
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
