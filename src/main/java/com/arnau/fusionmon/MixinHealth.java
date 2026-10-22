package com.arnau.fusionmon;

import org.objectweb.asm.Handle;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Comprueba que los enganches de Fusionmon se han aplicado de verdad. Los opcionales (require = 0) no paran el juego
 * si no encuentran su sitio (otro mod que cambia ese método, otra versión de Cobblemon), pero Mixin tampoco lo dice:
 * aquí se mira, en cada clase modificada, si algún método llama a cada uno de nuestros métodos, y se avisa en el log
 * de los que no. Con el log de un jugador se sabe al momento qué parte de Fusionmon ha chocado con qué.
 *
 * Se comprueba más tarde (check, al arrancar el juego o el servidor y al entrar en un mundo), no al aplicar el mixin:
 * MixinExtras (WrapOperation...) inyecta después de que el plugin se entere. Las clases que Minecraft aún no ha cargado
 * se comprueban en la siguiente llamada.
 */
public final class MixinHealth {

    private record Applied(String mixin, ClassNode target, List<String> handlers) {
    }

    private static final List<Applied> PENDING = new ArrayList<>();

    private MixinHealth() {
    }

    static synchronized void applied(String mixin, ClassNode target, List<String> handlers) {
        if (!handlers.isEmpty()) {
            PENDING.add(new Applied(mixin, target, handlers));
        }
    }

    /** Avisa de los enganches que no se han aplicado en las clases ya cargadas, y las olvida (son pesadas). */
    public static synchronized void check() {
        if (PENDING.isEmpty()) {
            return;
        }
        int checked = 0;
        int missing = 0;
        for (Applied applied : PENDING) {
            for (String handler : applied.handlers()) {
                checked++;
                if (!isCalled(applied.target(), handler)) {
                    missing++;
                    Fusionmon.LOGGER.warn("Fusionmon: {}.{} no se ha podido enganchar en {} (¿otro mod u otra versión"
                                    + " de Cobblemon?); esa parte de Fusionmon queda desactivada",
                            applied.mixin(), handler, applied.target().name.replace('/', '.'));
                }
            }
        }
        // También cuando todo va bien: en el log de un jugador se ve que la comprobación ha mirado algo
        Fusionmon.LOGGER.info("Fusionmon: comprobados {} enganches en {} clases, {} sin aplicar", checked,
                PENDING.size(), missing);
        PENDING.clear();
    }

    /**
     * ¿Algún método de la clase (que no sea el propio) llama a este? Mixin puede renombrar el método al copiarlo
     * (le pone un prefijo), por eso se busca que el nombre lo contenga.
     */
    private static boolean isCalled(ClassNode target, String handler) {
        for (MethodNode method : target.methods) {
            if (method.name.contains(handler) || method.instructions == null) {
                continue;
            }
            for (AbstractInsnNode insn : method.instructions) {
                if (insn instanceof MethodInsnNode call && call.name.contains(handler)) {
                    return true;
                }
                if (insn instanceof InvokeDynamicInsnNode indy) {
                    for (Object arg : indy.bsmArgs) {
                        if (arg instanceof Handle handle && handle.getName().contains(handler)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }
}
