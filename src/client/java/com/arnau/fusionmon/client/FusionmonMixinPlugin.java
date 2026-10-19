package com.arnau.fusionmon.client;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Decide qué mixins de cliente se aplican: los que apuntan a una clase de Cobblemon que no existe en la versión
 * instalada se saltan, en vez de impedir que arranque el juego. Hoy solo ClientboundSeatAssignmentPacketHandlerMixin:
 * ese paquete de montura no existe en Cobblemon 1.7.3 (y montar va bien sin él allí).
 *
 * Mixin pregunta esto antes de cargar ninguna clase del juego: por eso se mira si existe el archivo .class (sin
 * cargarlo) en vez de usar Class.forName. Esta clase no puede estar en el paquete de los mixins
 * (com.arnau.fusionmon.client.mixin): ese paquete es solo para mixins.
 */
public final class FusionmonMixinPlugin implements IMixinConfigPlugin {

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        String file = targetClassName.replace('.', '/') + ".class";
        return FusionmonMixinPlugin.class.getClassLoader().getResource(file) != null;
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

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
