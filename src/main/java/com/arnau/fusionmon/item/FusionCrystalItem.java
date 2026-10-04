package com.arnau.fusionmon.item;

import com.arnau.fusionmon.fusion.FusionSelection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
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

        // use() se ejecuta en el cliente y en el servidor; la lógica de Pokémon vive solo en el servidor
        if (player instanceof ServerPlayer serverPlayer) {
            FusionSelection.start(serverPlayer);
        }

        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}
