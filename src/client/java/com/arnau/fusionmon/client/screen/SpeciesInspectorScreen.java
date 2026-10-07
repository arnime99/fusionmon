package com.arnau.fusionmon.client.screen;

import com.arnau.fusionmon.client.model.FusionGraft;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState;
import com.cobblemon.mod.common.client.render.models.blockbench.PosableModel;
import com.cobblemon.mod.common.client.render.models.blockbench.repository.VaryingModelRepository;
import com.cobblemon.mod.common.pokemon.Gender;
import com.cobblemon.mod.common.pokemon.RenderablePokemon;
import com.cobblemon.mod.common.pokemon.Species;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Inspector de especies (/fusioninspect): una especie sola, con lo que detectan las reglas de FusionGraft marcado
 * (cabeza, cráneo, punto de pegado, tronco, cola, adornos), para revisar las ~1000 especies una a una en vez de las
 * fusiones (ver docs/visual-bugs.md, "revisión por especies"). Cada especie se marca bien o con fallo y una nota, y se
 * guarda (SpeciesReview). Si está la tabla del proyecto (docs/species, SpeciesTable) enseña su forma y sus avisos, y
 * se puede filtrar por ellos.
 *
 * Usa el mismo código que las fusiones: FusionGraft pinta la especie en modo inspección (ver InspectView) y devuelve
 * unos marcadores que se dibujan aquí encima, con líneas que se ven a través del modelo.
 */
public class SpeciesInspectorScreen extends Screen {

    private static final int WHITE = 0xFFFFFF;
    private static final int GRAY = 0xAAAAAA;
    private static final int YELLOW = 0xFFFF55;
    private static final int GREEN = 0x55FF55;
    private static final int RED = 0xFF7777;
    private static final int SMALL = 20;
    private static final int GAP = 4;
    private static final int MARGIN = 6;
    private static final int LINE = 10;

    /** Qué especies se recorren con las flechas. */
    private enum Filter { ALL, WARNINGS, UNREVIEWED, WRONG }

    // Se recuerda al cerrar y volver a abrir (mientras el juego siga abierto)
    private static Species current;
    private static Filter filter = Filter.ALL;
    /** Forma de la tabla por la que se filtra ("bípedo"...), o null para todas. */
    private static String shape;
    private static FusionGraft.InspectView view = FusionGraft.InspectView.PARTS;
    private static boolean female;

    private final FloatingState state = new FloatingState();
    private final ModelViewport viewport = new ModelViewport();
    /** Especies con modelo, por número de la Pokédex. */
    private List<Species> species = List.of();
    private SpeciesTable table;
    private SpeciesReview review;

    private EditBox searchBox;
    private EditBox noteBox;
    private Button filterButton;
    private Button shapeButton;
    private Button viewButton;
    private Button genderButton;
    /** Si hay que tirar la próxima letra que llegue (ver la tecla X en keyPressed). */
    private boolean skipNextChar;

    public SpeciesInspectorScreen() {
        super(Component.translatable("gui.fusionmon.inspect.title"));
    }

    @Override
    protected void init() {
        species = PokemonSpecies.getSpecies().stream()
                .filter(s -> VaryingModelRepository.INSTANCE.getVariations().containsKey(s.getResourceIdentifier()))
                .sorted(Comparator.comparingInt(Species::getNationalPokedexNumber))
                .toList();
        table = SpeciesTable.load();
        review = SpeciesReview.load();
        if (species.isEmpty()) {
            return;
        }
        if (current == null || !species.contains(current)) {
            current = species.get(0);
        }

        // Arriba: [buscar][◀][▶] [Filtro] [Forma]
        int x = MARGIN;
        searchBox = addRenderableWidget(new EditBox(font, x, MARGIN, 80, SMALL,
                Component.translatable("gui.fusionmon.inspect.search")));
        searchBox.setHint(Component.translatable("gui.fusionmon.inspect.search"));
        searchBox.setResponder(text -> {
            Species found = search(text);
            if (found != null && found != current) {
                select(found);
            }
        });
        x += 80 + GAP;
        addRenderableWidget(Button.builder(Component.literal("◀"), b -> step(-1)).bounds(x, MARGIN, SMALL, SMALL).build());
        x += SMALL + GAP;
        addRenderableWidget(Button.builder(Component.literal("▶"), b -> step(1)).bounds(x, MARGIN, SMALL, SMALL).build());
        x += SMALL + GAP;
        filterButton = addRenderableWidget(Button.builder(Component.empty(), b -> cycleFilter())
                .bounds(x, MARGIN, 100, SMALL).build());
        x += 100 + GAP;
        shapeButton = addRenderableWidget(Button.builder(Component.empty(), b -> cycleShape())
                .bounds(x, MARGIN, 100, SMALL).build());
        shapeButton.active = !table.isEmpty();

        // Abajo: [Vista][Sexo] [Bien][Fallo][nota...]
        int y = height - SMALL - MARGIN;
        x = MARGIN;
        viewButton = addRenderableWidget(Button.builder(Component.empty(), b -> cycleView())
                .bounds(x, y, 80, SMALL).build());
        x += 80 + GAP;
        genderButton = addRenderableWidget(Button.builder(Component.empty(), b -> toggleGender())
                .bounds(x, y, 50, SMALL).build());
        x += 50 + GAP * 3;
        addRenderableWidget(Button.builder(Component.translatable("gui.fusionmon.inspect.ok"), b -> markOk())
                .bounds(x, y, 60, SMALL).build());
        x += 60 + GAP;
        addRenderableWidget(Button.builder(Component.translatable("gui.fusionmon.inspect.wrong"), b -> markWrong())
                .bounds(x, y, 60, SMALL).build());
        x += 60 + GAP;
        noteBox = addRenderableWidget(new EditBox(font, x, y, Math.max(60, width - MARGIN - x), SMALL,
                Component.translatable("gui.fusionmon.inspect.note")));
        noteBox.setHint(Component.translatable("gui.fusionmon.inspect.note"));
        noteBox.setMaxLength(300);
        loadNote();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        if (species.isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable("gui.fusionmon.dex.none"), width / 2, height / 2, GRAY);
            return;
        }
        refreshLabels();

        // El modelo, a la izquierda, entre las dos filas de botones
        int boxTop = MARGIN + SMALL + GAP;
        int boxBottom = height - SMALL - MARGIN - GAP;
        int box = Math.max(40, Math.min(boxBottom - boxTop, width / 2));
        RenderablePokemon model = new RenderablePokemon(current, aspects(), ItemStack.EMPTY);
        viewport.render(graphics, model, state, view.name(), MARGIN, boxTop, box, partialTick,
                () -> FusionGraft.startInspection(view,
                        VaryingModelRepository.INSTANCE.getTexture(current.getResourceIdentifier(), state)),
                () -> {
                    List<FusionGraft.Marker> markers = FusionGraft.stopInspection();
                    // Lo pintado se manda ya: si no, el modelo se pintaría después, tapando las líneas
                    graphics.flush();
                    drawMarkers(markers);
                });

        // A la derecha, lo que se sabe de la especie
        int x = MARGIN + box + 8;
        int panel = width - x - MARGIN;
        int y = boxTop;
        y = text(graphics, Component.literal(String.format("#%04d ", current.getNationalPokedexNumber()))
                .append(current.getTranslatedName()).append("  (" + key(current) + ")"), x, y, panel, YELLOW);
        y = reviewLine(graphics, x, y, panel);
        y += 4;

        SpeciesTable.Row row = table.get(current.getResourceIdentifier().getPath());
        if (table.isEmpty()) {
            y = text(graphics, Component.translatable("gui.fusionmon.inspect.no_table"), x, y, panel, GRAY);
        } else if (row == null) {
            y = text(graphics, Component.translatable("gui.fusionmon.inspect.not_in_table"), x, y, panel, GRAY);
        } else {
            y = text(graphics, Component.translatable("gui.fusionmon.inspect.table", row.shape(),
                    row.tags().isEmpty() ? "-" : row.tags()), x, y, panel, WHITE);
            for (String warning : row.warnings()) {
                y = text(graphics, Component.literal("• " + warning), x, y, panel, RED);
            }
        }
        y += 4;

        PosableModel poser = VaryingModelRepository.INSTANCE.getPoser(current.getResourceIdentifier(), state);
        FusionGraft.Anatomy anatomy = FusionGraft.describe(poser);
        y = text(graphics, Component.translatable("gui.fusionmon.inspect.kind",
                Component.translatable("gui.fusionmon.inspect.kind." + anatomy.kind())), x, y, panel, WHITE);
        if (anatomy.kind().equals("headless")) {
            y = text(graphics, Component.translatable("gui.fusionmon.inspect.top", or(anatomy.top())), x, y, panel, GRAY);
        } else {
            y = text(graphics, Component.translatable("gui.fusionmon.inspect.head", anatomy.head(),
                    Component.translatable("gui.fusionmon.inspect.attach." + anatomy.attach()), or(anatomy.skull())),
                    x, y, panel, GRAY);
            y = text(graphics, Component.translatable("gui.fusionmon.inspect.trunk", or(anatomy.trunk()),
                    or(anatomy.spine())), x, y, panel, GRAY);
        }
        y = text(graphics, Component.translatable("gui.fusionmon.inspect.tail", or(anatomy.tail())), x, y, panel,
                FusionGraft.INSPECT_TAIL & 0xFFFFFF);
        y = text(graphics, Component.translatable("gui.fusionmon.inspect.decor",
                or(String.join(", ", anatomy.decorations()))), x, y, panel, FusionGraft.INSPECT_TRUNK_DECOR & 0xFFFFFF);
        y = text(graphics, Component.translatable("gui.fusionmon.inspect.neck_decor",
                or(String.join(", ", anatomy.neckDecorations()))), x, y, panel, FusionGraft.INSPECT_NECK_DECOR & 0xFFFFFF);
        y += 4;

        // Leyenda de colores y ayuda
        y = text(graphics, Component.translatable("gui.fusionmon.inspect.view_help." + view.name().toLowerCase(Locale.ROOT)),
                x, y, panel, GRAY);
        y = legend(graphics, x, y, panel);
        y += 4;
        y = text(graphics, Component.translatable("gui.fusionmon.inspect.progress", review.count(SpeciesReview.Status.OK)
                + review.count(SpeciesReview.Status.WRONG), species.size(), review.count(SpeciesReview.Status.WRONG),
                countMatching()), x, y, panel, GRAY);
        text(graphics, Component.translatable("gui.fusionmon.inspect.help"), x, y, panel, GRAY);
    }

    /** Escribe un texto partido en líneas que caben en el ancho; devuelve la y de debajo. */
    private int text(GuiGraphics graphics, Component text, int x, int y, int width, int color) {
        for (FormattedCharSequence line : font.split(text, width)) {
            graphics.drawString(font, line, x, y, color);
            y += LINE;
        }
        return y;
    }

    private int reviewLine(GuiGraphics graphics, int x, int y, int width) {
        SpeciesReview.Entry entry = review.get(key(current));
        if (entry == null) {
            return text(graphics, Component.translatable("gui.fusionmon.inspect.status.none"), x, y, width, GRAY);
        }
        boolean ok = entry.status() == SpeciesReview.Status.OK;
        Component status = Component.translatable(ok ? "gui.fusionmon.inspect.status.ok" : "gui.fusionmon.inspect.status.wrong");
        if (!entry.note().isEmpty()) {
            status = status.copy().append(": " + entry.note());
        }
        return text(graphics, status, x, y, width, ok ? GREEN : RED);
    }

    private int legend(GuiGraphics graphics, int x, int y, int width) {
        int[] colors = {FusionGraft.INSPECT_PIVOT, FusionGraft.INSPECT_BASE, FusionGraft.INSPECT_SKULL,
                FusionGraft.INSPECT_TRUNK, FusionGraft.INSPECT_SPINE, FusionGraft.INSPECT_TRUNK_DECOR,
                FusionGraft.INSPECT_NECK_DECOR, FusionGraft.INSPECT_ARM, FusionGraft.INSPECT_TAIL};
        String[] keys = {"pivot", "base", "skull", "trunk", "spine", "decor", "neck_decor", "arm", "tail"};
        for (int i = 0; i < colors.length; i++) {
            y = text(graphics, Component.literal("■ ").append(Component.translatable("gui.fusionmon.inspect.legend." + keys[i])),
                    x, y, width, colors[i] & 0xFFFFFF);
        }
        return y;
    }

    private static String or(String value) {
        return value == null || value.isEmpty() ? "-" : value;
    }

    /**
     * Las cajas de FusionGraft, con líneas por encima de todo (sin prueba de profundidad): el pivote y el cráneo de la
     * cabeza suelen quedar dentro del modelo.
     */
    private static void drawMarkers(List<FusionGraft.Marker> markers) {
        if (markers.isEmpty()) {
            return;
        }
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.DEBUG_LINES,
                DefaultVertexFormat.POSITION_COLOR);
        for (FusionGraft.Marker marker : markers) {
            Matrix4f pose = marker.pose();
            float[] b = marker.box();
            // Las 12 aristas: entre cada par de esquinas que solo se diferencian en un eje
            for (int corner = 0; corner < 8; corner++) {
                for (int axis = 1; axis <= 4; axis <<= 1) {
                    if ((corner & axis) != 0) {
                        continue;
                    }
                    int other = corner | axis;
                    buffer.addVertex(pose, b[(corner & 1) == 0 ? 0 : 3], b[(corner & 2) == 0 ? 1 : 4],
                            b[(corner & 4) == 0 ? 2 : 5]).setColor(marker.color());
                    buffer.addVertex(pose, b[(other & 1) == 0 ? 0 : 3], b[(other & 2) == 0 ? 1 : 4],
                            b[(other & 4) == 0 ? 2 : 5]).setColor(marker.color());
                }
            }
        }
        BufferUploader.drawWithShader(buffer.buildOrThrow());
        RenderSystem.enableDepthTest();
    }

    // ---- Revisión ----

    /**
     * Clave de la especie en la revisión: su nombre interno, y "[female]" si se mira la hembra de una especie con los
     * dos sexos (pueden tener modelos distintos: Pyroar).
     */
    private static String key(Species species) {
        Set<Gender> genders = species.getPossibleGenders();
        boolean both = genders.contains(Gender.MALE) && genders.contains(Gender.FEMALE);
        return species.getResourceIdentifier().getPath() + (female && both ? "[female]" : "");
    }

    private void markOk() {
        review.set(key(current), SpeciesReview.Status.OK, noteBox.getValue());
        step(1);
    }

    /** Fallo: se guarda ya y se pasa a la nota (Intro la guarda y pasa a la siguiente). */
    private void markWrong() {
        review.set(key(current), SpeciesReview.Status.WRONG, noteBox.getValue());
        setFocused(noteBox);
        noteBox.setFocused(true);
    }

    private void loadNote() {
        SpeciesReview.Entry entry = review.get(key(current));
        noteBox.setValue(entry == null ? "" : entry.note());
    }

    // ---- Recorrer especies ----

    private boolean matches(Species candidate) {
        SpeciesTable.Row row = table.get(candidate.getResourceIdentifier().getPath());
        if (shape != null && (row == null || !row.shape().equals(shape))) {
            return false;
        }
        SpeciesReview.Entry entry = review.get(key(candidate));
        return switch (filter) {
            case ALL -> true;
            case WARNINGS -> row != null && !row.warnings().isEmpty();
            case UNREVIEWED -> entry == null;
            case WRONG -> entry != null && entry.status() == SpeciesReview.Status.WRONG;
        };
    }

    private int countMatching() {
        int count = 0;
        for (Species candidate : species) {
            if (matches(candidate)) {
                count++;
            }
        }
        return count;
    }

    /** La siguiente (o anterior) especie que pasa el filtro, dando la vuelta; si ninguna, se queda donde está. */
    private void step(int direction) {
        int start = species.indexOf(current);
        for (int i = 1; i <= species.size(); i++) {
            Species candidate = species.get(Math.floorMod(start + direction * i, species.size()));
            if (matches(candidate)) {
                select(candidate);
                return;
            }
        }
    }

    private void select(Species value) {
        current = value;
        loadNote();
    }

    private void cycleFilter() {
        filter = Filter.values()[(filter.ordinal() + 1) % Filter.values().length];
        if (!matches(current)) {
            step(1);
        }
    }

    private void cycleShape() {
        List<String> shapes = new ArrayList<>();
        shapes.add(null);
        shapes.addAll(table.shapes());
        shape = shapes.get((shapes.indexOf(shape) + 1) % shapes.size());
        if (!matches(current)) {
            step(1);
        }
    }

    private void cycleView() {
        view = FusionGraft.InspectView.values()[(view.ordinal() + 1) % FusionGraft.InspectView.values().length];
    }

    private void toggleGender() {
        female = !female;
        loadNote();
    }

    /** Aspects de la especie sola: el sexo elegido si lo tiene (si no, el que tenga). */
    private Set<String> aspects() {
        Set<String> aspects = new HashSet<>();
        Set<Gender> genders = current.getPossibleGenders();
        if (female && genders.contains(Gender.FEMALE) || !genders.contains(Gender.MALE) && genders.contains(Gender.FEMALE)) {
            aspects.add("female");
        } else if (genders.contains(Gender.MALE)) {
            aspects.add("male");
        }
        return aspects;
    }

    /** Como en el visor de fusiones: número, o nombre igual, que empiece o que contenga el texto. */
    private Species search(String text) {
        String query = normalize(text);
        if (query.isEmpty()) {
            return null;
        }
        if (query.chars().allMatch(Character::isDigit)) {
            int number = Integer.parseInt(query.length() > 6 ? query.substring(0, 6) : query);
            return species.stream().filter(s -> s.getNationalPokedexNumber() == number).findFirst().orElse(null);
        }
        for (int pass = 0; pass < 3; pass++) {
            for (Species candidate : species) {
                for (String name : List.of(normalize(candidate.getTranslatedName().getString()),
                        normalize(candidate.getResourceIdentifier().getPath()))) {
                    if (pass == 0 ? name.equals(query) : pass == 1 ? name.startsWith(query) : name.contains(query)) {
                        return candidate;
                    }
                }
            }
        }
        return null;
    }

    private static String normalize(String text) {
        return Normalizer.normalize(text.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }

    private void refreshLabels() {
        filterButton.setMessage(Component.translatable("gui.fusionmon.inspect.filter." + filter.name().toLowerCase(Locale.ROOT)));
        shapeButton.setMessage(shape == null ? Component.translatable("gui.fusionmon.inspect.shape.all")
                : Component.translatable("gui.fusionmon.inspect.shape", shape));
        viewButton.setMessage(Component.translatable("gui.fusionmon.inspect.view." + view.name().toLowerCase(Locale.ROOT)));
        genderButton.setMessage(Component.translatable(female ? "gui.fusionmon.dex.female" : "gui.fusionmon.dex.male"));
    }

    // ---- Ratón y teclado ----

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        // Un clic fuera de los cuadros de texto les quita el foco: así las teclas vuelven a ser atajos
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
        boolean enter = keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER;
        if (getFocused() == noteBox && noteBox.isFocused()) {
            // Intro en la nota: se guarda con el fallo y se pasa a la siguiente
            if (enter) {
                review.set(key(current), SpeciesReview.Status.WRONG, noteBox.getValue());
                setFocused(null);
                step(1);
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (getFocused() == searchBox && searchBox.isFocused()) {
            if (enter) {
                setFocused(null);
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (!species.isEmpty()) {
            switch (keyCode) {
                case GLFW.GLFW_KEY_LEFT -> step(-1);
                case GLFW.GLFW_KEY_RIGHT -> step(1);
                case GLFW.GLFW_KEY_V -> cycleView();
                case GLFW.GLFW_KEY_G -> toggleGender();
                case GLFW.GLFW_KEY_F -> cycleFilter();
                case GLFW.GLFW_KEY_B -> markOk();
                case GLFW.GLFW_KEY_X -> {
                    markWrong();
                    // Minecraft manda después la letra de esta misma tecla, y acabaría escrita en la nota
                    skipNextChar = true;
                }
                case GLFW.GLFW_KEY_U -> {
                    review.clear(key(current));
                    loadNote();
                }
                case GLFW.GLFW_KEY_C -> viewport.reset();
                default -> {
                    return super.keyPressed(keyCode, scanCode, modifiers);
                }
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (skipNextChar) {
            skipNextChar = false;
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        // Sin pausa, las animaciones del modelo siguen
        return false;
    }
}
