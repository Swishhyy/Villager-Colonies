package io.github.swishhyy.wwmc;

import com.mojang.serialization.JsonOps;
import io.github.swishhyy.wwmc.core.StructureRole;
import io.github.swishhyy.wwmc.core.Upgrades;
import io.github.swishhyy.wwmc.settlement.*;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import static org.junit.jupiter.api.Assertions.*;

public final class ProductionChecks {
    private static int count(List<ItemStack> drops,net.minecraft.world.item.Item item) {
        return drops.stream().filter(s -> s.is(item)).mapToInt(ItemStack::getCount).sum();
    }
    @Test void yieldLevelsRoundTripAndOldStationsDefaultToZero() {
        for(var role:StructureRole.values()) {
            Station station=new Station(BlockPos.ZERO,role).withYield(7);
            assertEquals(Upgrades.yields(role) ? 3 : 0,station.yieldLevel());
            assertEquals(station,Station.CODEC.parse(JsonOps.INSTANCE,Station.CODEC.encodeStart(JsonOps.INSTANCE,station).getOrThrow()).getOrThrow());
            assertEquals(station.yieldLevel(),station.withRange(2).yieldLevel());
            var legacy=Station.CODEC.encodeStart(JsonOps.INSTANCE,station).getOrThrow().getAsJsonObject(); legacy.remove("yield");
            assertEquals(0,Station.CODEC.parse(JsonOps.INSTANCE,legacy).getOrThrow().yieldLevel());
            if(role!=StructureRole.QUARRY && role.providesWork()) assertEquals(1,SettlementService.workerLimit(station));
        }
        assertEquals(16,Upgrades.stationCost(16,0)); assertEquals(32,Upgrades.stationCost(16,1)); assertEquals(64,Upgrades.stationCost(16,2));
    }
    @Test @ExtendWith(EphemeralTestServerProvider.class)
    void bonusHasBoundedRatesAndPreservesCropSeedsAndItemComponents(MinecraftServer server) {
        ItemStack wheat=new ItemStack(Items.WHEAT,64); wheat.set(DataComponents.CUSTOM_NAME,Component.literal("Town crop"));
        List<ItemStack> drops=List.of(wheat,new ItemStack(Items.WHEAT_SEEDS,4),new ItemStack(Items.POISONOUS_POTATO));
        var farm=new Station(BlockPos.ZERO,StructureRole.FARM);
        assertSame(drops,ProductionYield.apply(farm,Blocks.WHEAT.defaultBlockState(),drops,RandomSource.create(7)));
        int previous=0;
        for(int level=1;level<=3;level++) {
            int extra=0; RandomSource random=RandomSource.create(7);
            for(int trip=0;trip<200;trip++) {
                var result=ProductionYield.apply(farm.withYield(level),Blocks.WHEAT.defaultBlockState(),drops,random);
                assertEquals(4,count(result,Items.WHEAT_SEEDS)); assertEquals(1,count(result,Items.POISONOUS_POTATO));
                for(ItemStack stack:result) if(stack.is(Items.WHEAT)) assertTrue(ItemStack.isSameItemSameComponents(stack,wheat));
                int n=count(result,Items.WHEAT); assertTrue(n>=64 && n<=128); extra+=n-64;
            }
            double rate=extra/12800.0; assertEquals(level/10.0,rate,0.015); assertTrue(extra>previous); previous=extra;
            assertEquals(64,wheat.getCount(),"Bonus generation must not mutate the vanilla loot or its components");
        }
        var mine=new Station(BlockPos.ZERO,StructureRole.MINE).withYield(3);
        var silk=List.of(new ItemStack(Items.DIAMOND_ORE));
        assertEquals(1,count(ProductionYield.apply(mine,Blocks.DIAMOND_ORE.defaultBlockState(),silk,RandomSource.create(7)),Items.DIAMOND_ORE));
        assertFalse(ProductionYield.eligible(StructureRole.MINE,Blocks.STONE.defaultBlockState(),new ItemStack(Items.COBBLESTONE)));
        assertTrue(ProductionYield.eligible(StructureRole.MINE,Blocks.IRON_ORE.defaultBlockState(),new ItemStack(Items.RAW_IRON)));
        assertTrue(ProductionYield.eligible(StructureRole.MINE,Blocks.DIAMOND_ORE.defaultBlockState(),new ItemStack(Items.DIAMOND)));
    }
    @Test @ExtendWith(EphemeralTestServerProvider.class)
    void pickaxeReplenishmentImprovesWithTiersButRetainsRarityAndCap(MinecraftServer server) {
        var ore=Blocks.COAL_ORE.defaultBlockState();
        int wood=OreVeins.interval(ore,15,new ItemStack(Items.WOODEN_PICKAXE));
        int stone=OreVeins.interval(ore,15,new ItemStack(Items.STONE_PICKAXE));
        int iron=OreVeins.interval(ore,15,new ItemStack(Items.IRON_PICKAXE));
        int diamond=OreVeins.interval(ore,15,new ItemStack(Items.DIAMOND_PICKAXE));
        int netherite=OreVeins.interval(ore,15,new ItemStack(Items.NETHERITE_PICKAXE));
        assertEquals(400,wood); assertEquals(300,stone); assertTrue(stone>iron && iron>diamond && diamond>netherite);
        assertEquals(200,netherite); assertEquals(200,OreVeins.interval(ore,15,new ItemStack(Items.GOLDEN_PICKAXE)));
        assertEquals(1200,OreVeins.interval(Blocks.DIAMOND_ORE.defaultBlockState(),15,new ItemStack(Items.NETHERITE_PICKAXE)));
        assertEquals(400,OreVeins.interval(Blocks.GOLD_ORE.defaultBlockState(),15,new ItemStack(Items.NETHERITE_PICKAXE)));
        assertEquals(1600,OreVeins.interval(Blocks.ANCIENT_DEBRIS.defaultBlockState(),15,new ItemStack(Items.NETHERITE_PICKAXE)));
        assertEquals(300,OreVeins.interval(ore,15,ItemStack.EMPTY),"UI fallback remains the stone baseline");
    }
}
