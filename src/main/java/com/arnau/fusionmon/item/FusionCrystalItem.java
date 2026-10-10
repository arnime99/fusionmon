package com.arnau.fusionmon.item;

import com.arnau.fusionmon.fusion.FusionDiscovery;
import com.arnau.fusionmon.fusion.FusionSelection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class FusionCrystalItem extends Item {

    public FusionCrystalItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        // use() se ejecuta en el cliente y en el servidor; la lógica de Pokémon vive solo en el servidor.
        // El cristal no se gasta aquí: el jugador aún puede cancelar en las pantallas
        if (player instanceof ServerPlayer serverPlayer) {
            // Agachado: el Fusion Album (las fusiones descubiertas); si no, elegir Pokémon para fusionar o separar
            if (serverPlayer.isShiftKeyDown()) {
                FusionDiscovery.open(serverPlayer);
            } else {
                FusionSelection.start(serverPlayer);
            }
        }

        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    /**
     * Gasta un cristal del jugador; false si ya no tiene ninguno.
     * Se llama al confirmar la fusión/separación: mientras las pantallas estaban abiertas
     * pudo soltarlo o guardarlo, así que se busca primero en las manos y luego en todo el inventario.
     * En creativo no se resta (ItemStack.consume ya lo comprueba).
     */
    public static boolean consumeOne(ServerPlayer player) {
        ItemStack crystal = findCrystal(player);
        if (crystal == null) {
            return false;
        }
        crystal.consume(1, player);
        return true;
    }

    private static ItemStack findCrystal(Player player) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.is(ModItems.FUSION_CRYSTAL)) {
                return stack;
            }
        }
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(ModItems.FUSION_CRYSTAL)) {
                return stack;
            }
        }
        return null;
    }
}
