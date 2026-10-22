package com.arnau.fusionmon;

import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Plugin de las dos configuraciones de mixins (fusionmon.mixins.json y la de cliente, que lo amplía):
 * - Salta los mixins cuya clase objetivo no existe (otra versión de Cobblemon, o un mod opcional como Mega Showdown
 *   que no está), en vez de impedir que arranque el juego.
 * - Apunta cada clase de Cobblemon que modifica, para que MixinHealth compruebe luego si los enganches opcionales
 *   (require = 0) se han aplicado: si no, Mixin no dice nada y la parte se pierde sin que nadie lo sepa.
 *
 * Mixin pregunta esto antes de cargar ninguna clase del juego: por eso se mira si existe el archivo .class (sin
 * cargarlo) en vez de usar Class.forName. Esta clase no puede estar en un paquete de mixins.
 */
public class CommonMixinPlugin implements IMixinConfigPlugin {

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        String file = targetClassName.replace('.', '/') + ".class";
        return CommonMixinPlugin.class.getClassLoader().getResource(file) != null;
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        List<String> handlers = new ArrayList<>();
        for (MethodNode method : mixinInfo.getClassNode(0).methods) {
            if (method.name.startsWith("fusionmon$") && isInjector(method)) {
                handlers.add(method.name);
            }
        }
        MixinHealth.applied(simpleName(mixinClassName), targetClass, handlers);
    }

    /** Un método del mixin con anotación de inyección (de Mixin o de MixinExtras), no un ayudante. */
    private static boolean isInjector(MethodNode method) {
        for (List<AnnotationNode> annotations : List.of(
                method.visibleAnnotations == null ? List.<AnnotationNode>of() : method.visibleAnnotations,
                method.invisibleAnnotations == null ? List.<AnnotationNode>of() : method.invisibleAnnotations)) {
            for (AnnotationNode annotation : annotations) {
                if (annotation.desc.contains("/injection/") || annotation.desc.contains("/injector/")) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String simpleName(String className) {
        return className.substring(className.lastIndexOf('.') + 1);
    }

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
