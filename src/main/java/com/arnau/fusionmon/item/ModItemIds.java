package com.arnau.fusionmon.item;

import com.arnau.fusionmon.Fusionmon;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

public class ModItemIds {

    public static ResourceKey<Item> create(String name) {
        return ResourceKey.create(
                Registries.ITEM,
                Fusionmon.id(name)
        );
    }

    public static final ResourceKey<Item> FUSION_CRYSTAL = create("fusion_crystal");
}