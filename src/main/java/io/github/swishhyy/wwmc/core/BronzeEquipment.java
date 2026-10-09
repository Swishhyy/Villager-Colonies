package io.github.swishhyy.wwmc.core;

import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;

/** Bronze lasts longer than stone but cannot replace iron when mining high-tier ores. */
public final class BronzeEquipment {
    private BronzeEquipment() {}
    public static final TagKey<Item> REPAIR=TagKey.create(Registries.ITEM,Identifier.fromNamespaceAndPath("c","ingots/bronze"));
    public static final ToolMaterial TOOLS=new ToolMaterial(BlockTags.INCORRECT_FOR_STONE_TOOL,220,5.0F,1.5F,14,REPAIR);
    public static final ResourceKey<EquipmentAsset> ASSET=ResourceKey.create(EquipmentAssets.ROOT_ID,Identifier.fromNamespaceAndPath("wwmc","bronze"));
    public static final ArmorMaterial ARMOR=new ArmorMaterial(13,Map.of(ArmorType.HELMET,2,ArmorType.CHESTPLATE,5,ArmorType.LEGGINGS,4,ArmorType.BOOTS,1),
            14,SoundEvents.ARMOR_EQUIP_IRON,0,0,REPAIR,ASSET);
}
