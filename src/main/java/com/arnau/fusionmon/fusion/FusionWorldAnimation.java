package com.arnau.fusionmon.fusion;

import com.arnau.fusionmon.Fusionmon;
import com.cobblemon.mod.common.CobblemonSounds;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/**
 * Animación de fusión en el mundo (solo al fusionar, no al separar ni invertir): si la cabeza y el cuerpo están fuera
 * de la Poké Ball y cerca del jugador, la fusión se ve en el mundo, como una evolución de Cobblemon, en vez de en la
 * pantalla. Pensada para que se vea bien en vídeo y para que la vean los demás jugadores.
 *
 * La lleva el servidor tick a tick (las partículas y sonidos se mandan a todos los que estén cerca):
 *  1. Carga (0 – CHARGE): los dos se quedan quietos mirándose, envueltos en espirales de partículas, rojas la cabeza
 *     y azules el cuerpo.
 *  2. Acercamiento (CHARGE – MEET): los dos se deslizan hasta juntarse en el punto medio, con un rastro entre ellos.
 *  3. Nube (CLOUD – FLASH): una nube roja, azul y blanca, apretada y cada vez más densa, los envuelve en el punto
 *     medio; dentro de ella se encogen (SHRINK – HIDE) y se vuelven invisibles (HIDE): las partículas solas no tapan
 *     un modelo grande.
 *  4. Destello (FLASH): estallido; justo aquí se hace la fusión (el cuerpo vuelve a su Poké Ball y la cabeza se
 *     convierte en la fusión, en el punto medio), todavía invisible y pequeña.
 *  5. Revelación (SHOW – END): la fusión aparece al abrirse la nube y crece con un rebote (SHOW – GROWN), grita, y
 *     quedan destellos que se apagan.
 * Mientras dura, los dos Pokémon no tienen IA ni gravedad (no se mueven solos, ni se caen los que vuelan). Si algo la
 * corta (uno vuelve a la Poké Ball, se desmaya, el jugador se va), la fusión se hace en ese momento: nunca se pierde.
 */
public final class FusionWorldAnimation {

    /** Distancia máxima (bloques) de cada Pokémon al jugador. */
    private static final double MAX_DISTANCE = 16;

    // Tiempos, en ticks (20 por segundo)
    private static final int CHARGE = 20;
    private static final int MEET = 58;
    private static final int CLOUD = 46;
    private static final int SHRINK = 46;
    private static final int HIDE = 66;
    private static final int FLASH = 74;
    private static final int SHOW = 80;
    private static final int GROWN = 96;
    private static final int CRY = 84;
    private static final int END = 120;

    /** Escala a la que se encogen antes de desaparecer, y de la que crece la fusión. */
    private static final double SMALL = 0.1;
    /** Modificador del atributo de escala de Minecraft: lo ven todos los clientes y se quita al acabar. */
    private static final ResourceLocation SCALE_ID = Fusionmon.id("fusion_animation");

    private static final Vector3f RED_COLOR = new Vector3f(1.0F, 0.22F, 0.22F);
    private static final Vector3f BLUE_COLOR = new Vector3f(0.25F, 0.55F, 1.0F);
    // El polvo de Minecraft dura más cuanto más grande es (a tamaño 4, hasta 8 s): tamaños pequeños para que se apague
    // pronto, y la nube se tapa con cantidad, no con tamaño
    private static final DustParticleOptions RED = new DustParticleOptions(RED_COLOR, 1.2F);
    private static final DustParticleOptions BLUE = new DustParticleOptions(BLUE_COLOR, 1.2F);
    private static final DustParticleOptions RED_CLOUD = new DustParticleOptions(RED_COLOR, 2.0F);
    private static final DustParticleOptions BLUE_CLOUD = new DustParticleOptions(BLUE_COLOR, 2.0F);
    private static final DustParticleOptions WHITE_CLOUD = new DustParticleOptions(new Vector3f(1.0F, 1.0F, 1.0F), 2.0F);

    private static final List<Animation> RUNNING = new ArrayList<>();

    private FusionWorldAnimation() {
    }

    public static void initialize() {
        ServerTickEvents.END_SERVER_TICK.register(server -> tickAll());
        // Si el servidor se cierra a mitad, la fusión se hace igual (si no, el inyector ya gastado se perdería)
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> finishAll());
    }

    /** Si la fusión de head + body se puede animar en el mundo: los dos fuera, vivos, en su mundo y cerca. */
    public static boolean canPlay(ServerPlayer player, Pokemon head, Pokemon body) {
        return isNear(player, head.getEntity()) && isNear(player, body.getEntity());
    }

    private static boolean isNear(ServerPlayer player, PokemonEntity entity) {
        return entity != null
                && entity.isAlive()
                && entity.level() == player.level()
                && entity.distanceTo(player) <= MAX_DISTANCE;
    }

    /** Si el jugador tiene una fusión animándose ahora (mientras, no puede empezar otra). */
    public static boolean isPlaying(ServerPlayer player) {
        return RUNNING.stream().anyMatch(animation -> animation.playerId.equals(player.getUUID()));
    }

    /**
     * Empieza la animación. fuse hace la fusión de verdad (vuelve a comprobar el equipo) y dice si se ha hecho: se
     * llama en el destello, o antes si la animación se corta.
     */
    public static void start(ServerPlayer player, Pokemon head, Pokemon body, BooleanSupplier fuse) {
        Animation animation = new Animation(player.getUUID(), player.serverLevel(), head.getEntity(),
                body.getEntity(), fuse);
        animation.freeze();
        RUNNING.add(animation);
        animation.sound(animation.meeting.add(0, 0.5, 0), CobblemonSounds.FOSSIL_MACHINE_ASSEMBLE);
    }

    /** El jugador se va: sus fusiones a medias se hacen ya. */
    public static void finishFor(ServerPlayer player) {
        Iterator<Animation> iterator = RUNNING.iterator();
        while (iterator.hasNext()) {
            Animation animation = iterator.next();
            if (animation.playerId.equals(player.getUUID())) {
                iterator.remove();
                animation.finishNow();
            }
        }
    }

    private static void finishAll() {
        List<Animation> running = new ArrayList<>(RUNNING);
        RUNNING.clear();
        running.forEach(Animation::finishNow);
    }

    private static void tickAll() {
        // Copia: al terminar una animación se quita de la lista mientras se recorre
        for (Animation animation : new ArrayList<>(RUNNING)) {
            if (!animation.tick()) {
                RUNNING.remove(animation);
            }
        }
    }

    private static final class Animation {
        private final UUID playerId;
        private final ServerLevel level;
        private final PokemonEntity head;
        private final PokemonEntity body;
        private final BooleanSupplier fuse;
        private final Vec3 headStart;
        private final Vec3 bodyStart;
        /** Donde se juntan: el punto medio entre los dos (a los pies). */
        private final Vec3 meeting;
        private final boolean headNoGravity;
        private final boolean bodyNoGravity;
        private int age;
        private boolean fused;

        private Animation(UUID playerId, ServerLevel level, PokemonEntity head, PokemonEntity body,
                          BooleanSupplier fuse) {
            this.playerId = playerId;
            this.level = level;
            this.head = head;
            this.body = body;
            this.fuse = fuse;
            this.headStart = head.position();
            this.bodyStart = body.position();
            this.meeting = headStart.lerp(bodyStart, 0.5);
            this.headNoGravity = head.isNoGravity();
            this.bodyNoGravity = body.isNoGravity();
        }

        /** Quietos y sin gravedad: así nada los mueve y se pueden deslizar a mano. */
        private void freeze() {
            for (PokemonEntity entity : List.of(head, body)) {
                entity.getNavigation().stop();
                entity.setNoAi(true);
                entity.setNoGravity(true);
                entity.setDeltaMovement(Vec3.ZERO);
            }
        }

        private void release(PokemonEntity entity, boolean noGravity) {
            entity.setNoAi(false);
            entity.setNoGravity(noGravity);
            entity.setInvisible(false);
            resetScale(entity);
        }

        private void releaseBoth() {
            release(head, headNoGravity);
            release(body, bodyNoGravity);
        }

        /** Un tick; false cuando ha terminado. */
        private boolean tick() {
            if (!fused && (!head.isAlive() || !body.isAlive() || head.level() != level || body.level() != level)) {
                // Cortada antes del destello (uno ha vuelto a su Poké Ball, se ha desmayado...): se fusiona ya
                finishNow();
                return false;
            }
            if (fused && !head.isAlive()) {
                return false;
            }

            age++;
            if (age < FLASH) {
                lookAtEachOther();
                if (age < HIDE) {
                    aura(head, RED);
                    aura(body, BLUE);
                }
                if (age >= CHARGE) {
                    // También después de juntarse: se tocan, y Minecraft empuja a las entidades que se solapan
                    approach();
                }
                if (age >= CLOUD) {
                    cloud();
                }
                if (age >= SHRINK && age <= HIDE) {
                    // Se encogen dentro de la nube (más deprisa al final): en los grandes la nube no los tapa enteros
                    float progress = (float) (age - SHRINK) / (HIDE - SHRINK);
                    double scale = 1 - (1 - SMALL) * progress * progress;
                    setScale(head, scale);
                    setScale(body, scale);
                }
                if (age == HIDE) {
                    // Ya dentro de la nube: invisibles, para que no se vea el cambio de modelo
                    head.setInvisible(true);
                    body.setInvisible(true);
                }
            } else if (age == FLASH) {
                flash();
            } else {
                // Sujeta en el punto medio hasta el final: el cuerpo tarda un poco en volver a su Poké Ball y la
                // empujaría (la fusión, además, ya tiene el tamaño del cuerpo)
                hold(head, meeting);
                if (age == SHOW) {
                    head.setInvisible(false);
                    Vec3 center = middle(head);
                    particle(ParticleTypes.POOF, center.x, center.y, center.z, 30, 0.5, 0.5, 0.5, 0.08);
                }
                if (age >= SHOW && age <= GROWN) {
                    // Crece desde pequeña, pasándose un poco y volviendo (como la animación de pantalla)
                    float progress = (float) (age - SHOW) / (GROWN - SHOW);
                    setScale(head, SMALL + (1 - SMALL) * easeOutBack(progress));
                }
                if (age == GROWN) {
                    resetScale(head);
                }
                reveal();
            }
            if (age == CRY && fused) {
                head.cry();
            }
            if (age >= END) {
                release(head, headNoGravity);
                return false;
            }
            return true;
        }

        /** Hace la fusión ahora mismo (animación cortada, el jugador se va o el servidor se cierra). */
        private void finishNow() {
            releaseBoth();
            if (!fused) {
                fused = true;
                fuse.getAsBoolean();
            }
        }

        private void lookAtEachOther() {
            face(head, body.position());
            face(body, head.position());
        }

        private static void face(PokemonEntity entity, Vec3 target) {
            Vec3 delta = target.subtract(entity.position());
            if (delta.horizontalDistanceSqr() < 1.0E-4) {
                return;
            }
            float yaw = (float) (Mth.atan2(delta.z, delta.x) * Mth.RAD_TO_DEG) - 90;
            entity.setYRot(yaw);
            entity.setYHeadRot(yaw);
            entity.setYBodyRot(yaw);
        }

        /** Dos espirales que suben alrededor del Pokémon, cuatro puntos cada una. */
        private void aura(PokemonEntity entity, ParticleOptions color) {
            double radius = Math.max(0.6, entity.getBbWidth() * 0.75);
            for (int i = 0; i < 4; i++) {
                double angle = age * 0.45 + i * Math.PI / 2;
                double height = entity.getBbHeight() * (((age + i * 5) % 20) / 20.0);
                particle(color, entity.getX() + Math.cos(angle) * radius, entity.getY() + height,
                        entity.getZ() + Math.sin(angle) * radius, 1, 0, 0, 0, 0);
            }
        }

        /**
         * Los dos se deslizan hacia el punto medio (cada vez más deprisa) hasta tocarse, con un rastro; después siguen
         * sujetos ahí.
         */
        private void approach() {
            float progress = Math.min(1, (float) (age - CHARGE) / (MEET - CHARGE));
            float eased = progress * progress * (3 - 2 * progress);
            slide(head, headStart, eased);
            slide(body, bodyStart, eased);
            if (age > MEET) {
                return;
            }

            Vec3 from = middle(head);
            Vec3 to = middle(body);
            for (int i = 0; i < 8; i++) {
                Vec3 point = from.lerp(to, level.random.nextDouble());
                particle(i % 2 == 0 ? RED : BLUE, point.x, point.y, point.z, 1, 0.08, 0.08, 0.08, 0);
            }
        }

        /** De su sitio a tocar el punto medio (el borde de su caja en el punto medio, para no meterse uno en otro). */
        private void slide(PokemonEntity entity, Vec3 start, float eased) {
            Vec3 away = start.subtract(meeting);
            Vec3 target = away.lengthSqr() < 1.0E-4
                    ? meeting
                    : meeting.add(away.normalize().scale(Math.min(entity.getBbWidth() * 0.4, away.length())));
            hold(entity, start.lerp(target, eased));
        }

        /** Escala del modelo (1 = la suya): el atributo de escala de Minecraft, con un modificador nuestro. */
        private static void setScale(PokemonEntity entity, double scale) {
            AttributeInstance attribute = entity.getAttribute(Attributes.SCALE);
            if (attribute != null) {
                attribute.addOrUpdateTransientModifier(new AttributeModifier(SCALE_ID, scale - 1,
                        AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            }
        }

        private static void resetScale(PokemonEntity entity) {
            AttributeInstance attribute = entity.getAttribute(Attributes.SCALE);
            if (attribute != null) {
                attribute.removeModifier(SCALE_ID);
            }
        }

        /** De 0 a 1 pasándose un poco (hasta ~1,1) antes de quedarse en 1. */
        private static double easeOutBack(float t) {
            double overshoot = 1.70158;
            double u = t - 1;
            return 1 + (overshoot + 1) * u * u * u + overshoot * u * u;
        }

        /** Lo pone ahí y le quita cualquier empuje que lleve (de otra entidad que lo toque). */
        private static void hold(PokemonEntity entity, Vec3 position) {
            entity.setDeltaMovement(Vec3.ZERO);
            entity.moveTo(position.x, position.y, position.z, entity.getYRot(), entity.getXRot());
        }

        /**
         * Nube roja, azul y blanca apretada donde se juntan, cada vez más densa y más cerrada, con chispas que entran:
         * tapa a los dos mientras se vuelven invisibles y se fusionan.
         */
        private void cloud() {
            float progress = (float) (age - CLOUD) / (FLASH - CLOUD);
            double width = Math.max(head.getBbWidth(), body.getBbWidth());
            double height = Math.max(head.getBbHeight(), body.getBbHeight());
            double spread = (width * 0.35 + 0.2) * (1.0 - 0.5 * progress);
            double spreadY = height * 0.3 * (1.0 - 0.4 * progress);
            Vec3 center = meeting.add(0, height / 2, 0);
            int count = 6 + (int) (progress * 14);
            particle(RED_CLOUD, center.x, center.y, center.z, count, spread, spreadY, spread, 0);
            particle(BLUE_CLOUD, center.x, center.y, center.z, count, spread, spreadY, spread, 0);
            particle(WHITE_CLOUD, center.x, center.y, center.z, count, spread * 0.7, spreadY * 0.7, spread * 0.7, 0);
            particle(ParticleTypes.FIREWORK, center.x, center.y, center.z, 2 + (int) (progress * 6),
                    spread * 0.5, spreadY * 0.5, spread * 0.5, 0.02);

            double radius = 2 + width;
            for (int i = 0; i < 10; i++) {
                double angle = level.random.nextDouble() * Mth.TWO_PI;
                double y = (level.random.nextDouble() - 0.5) * 2 * height;
                Vec3 from = center.add(Math.cos(angle) * radius, y, Math.sin(angle) * radius);
                Vec3 velocity = center.subtract(from).scale(0.12);
                // count 0: el desplazamiento es la velocidad de la partícula
                particle(ParticleTypes.END_ROD, from.x, from.y, from.z, 0, velocity.x, velocity.y, velocity.z, 1);
            }
        }

        private void flash() {
            double height = Math.max(head.getBbHeight(), body.getBbHeight());
            Vec3 center = meeting.add(0, height / 2, 0);
            particle(ParticleTypes.FLASH, center.x, center.y, center.z, 1, 0, 0, 0, 0);
            particle(ParticleTypes.END_ROD, center.x, center.y, center.z, 150, 0.2, 0.2, 0.2, 0.35);
            particle(ParticleTypes.FIREWORK, center.x, center.y, center.z, 80, 0.2, 0.2, 0.2, 0.25);
            particle(RED_CLOUD, center.x, center.y, center.z, 30, 0.5, height * 0.3, 0.5, 0);
            particle(BLUE_CLOUD, center.x, center.y, center.z, 30, 0.5, height * 0.3, 0.5, 0);
            particle(WHITE_CLOUD, center.x, center.y, center.z, 40, 0.4, height * 0.25, 0.4, 0);
            particle(ParticleTypes.CLOUD, center.x, center.y, center.z, 40, 0.4, height * 0.3, 0.4, 0.05);
            sound(center, CobblemonSounds.EVOLUTION_UI);

            // La fusión, invisible y pequeña dentro de la nube: el cuerpo vuelve a su Poké Ball y la cabeza, ya en el
            // punto medio, se transforma
            head.moveTo(meeting.x, meeting.y, meeting.z, head.getYRot(), head.getXRot());
            setScale(head, SMALL);
            release(body, bodyNoGravity);
            fused = true;
            if (!fuse.getAsBoolean()) {
                // No se ha podido (el equipo ha cambiado...): fuera la animación, quien fusiona ya ha avisado
                releaseBoth();
                age = END;
            }
        }

        /** Destellos rojos y azules alrededor de la fusión, cada vez menos. */
        private void reveal() {
            if (level.random.nextFloat() > 1 - (float) (age - FLASH) / (END - FLASH)) {
                return;
            }
            double radius = Math.max(0.8, head.getBbWidth());
            particle(level.random.nextBoolean() ? RED : BLUE, head.getX(), head.getY() + head.getBbHeight() / 2,
                    head.getZ(), 4, radius / 2, head.getBbHeight() / 2, radius / 2, 0);
            if (age % 2 == 0) {
                particle(ParticleTypes.END_ROD, head.getX(), head.getY() + head.getBbHeight() * 0.6, head.getZ(),
                        1, radius / 2, head.getBbHeight() / 3, radius / 2, 0.02);
            }
        }

        private static Vec3 middle(PokemonEntity entity) {
            return entity.position().add(0, entity.getBbHeight() / 2, 0);
        }

        private void particle(ParticleOptions particle, double x, double y, double z, int count,
                              double dx, double dy, double dz, double speed) {
            level.sendParticles(particle, x, y, z, count, dx, dy, dz, speed);
        }

        private void sound(Vec3 at, SoundEvent sound) {
            level.playSound(null, at.x, at.y, at.z, sound, SoundSource.NEUTRAL, 1.0F, 1.0F);
        }
    }
}
