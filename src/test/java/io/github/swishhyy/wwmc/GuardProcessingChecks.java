package io.github.swishhyy.wwmc;

import io.github.swishhyy.wwmc.core.AlarmState;
import io.github.swishhyy.wwmc.core.GuardDuty;
import io.github.swishhyy.wwmc.core.StructureRole;
import io.github.swishhyy.wwmc.core.WorkforceBook;
import io.github.swishhyy.wwmc.settlement.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import static org.junit.jupiter.api.Assertions.*;

public final class GuardProcessingChecks {
    @Test void eachStationHasItsOwnShiftAndAlarmRoster() {
        WorkforceBook<String> book=new WorkforceBook<>();
        UUID a=new UUID(0,1),b=new UUID(0,2),c=new UUID(0,3),d=new UUID(0,4);
        assertTrue(book.claim("west",b,0,200,2)); assertTrue(book.claim("west",a,0,200,2));
        assertTrue(book.claim("east",d,0,200,2)); assertTrue(book.claim("east",c,0,200,2));
        for(boolean night:List.of(false,true)) {
            assertEquals(1,book.members("west",0).stream().filter(id -> GuardDuty.active(book.members("west",0),id,night,false)).count());
            assertEquals(1,book.members("east",0).stream().filter(id -> GuardDuty.active(book.members("east",0),id,night,false)).count());
        }
        assertTrue(GuardDuty.active(book.members("west",0),a,false,false));
        assertTrue(GuardDuty.active(book.members("west",0),b,true,false));
        assertFalse(GuardDuty.active(book.members("west",0),c,false,true),"The alarm does not borrow another station's crew");
        AlarmState alarm=new AlarmState(); alarm.ring();
        for(String station:List.of("west","east")) for(UUID id:book.members(station,0))
            assertTrue(GuardDuty.active(book.members(station,0),id,true,alarm.ringing()),"The bell activates every assigned guard");
        alarm.calm();
        assertFalse(GuardDuty.active(book.members("west",0),a,true,alarm.ringing()),"All-clear restores the night shift");
        book.release("west",a);
        assertTrue(GuardDuty.active(book.members("west",0),b,false,false));
        assertTrue(GuardDuty.active(book.members("west",0),b,true,false),"A lone guard covers both shifts");
        assertTrue(book.claim("west",b,190,200,2));
        assertEquals(List.of(b),book.members("west",205),"A sleeping guard's renewed lease survives the old expiry");
        assertTrue(book.members("east",205).isEmpty(),"Absent guards cannot keep a station falsely staffed");
    }
    @Test @ExtendWith(EphemeralTestServerProvider.class)
    void ingredientsUseTheAppliancesLoadedRecipes(MinecraftServer server) {
        var level=server.overworld();
        for(var item:List.of(Items.RAW_IRON,Items.RAW_COPPER,Items.RAW_GOLD,Items.IRON_ORE,Items.DEEPSLATE_GOLD_ORE)) {
            assertTrue(ProcessingService.input(level,StructureRole.SMELTERY,RecipeType.SMELTING,new ItemStack(item)));
            assertTrue(ProcessingService.input(level,StructureRole.SMELTERY,RecipeType.BLASTING,new ItemStack(item)));
        }
        assertTrue(ProcessingService.input(level,StructureRole.SMELTERY,RecipeType.SMELTING,new ItemStack(Items.DIAMOND_ORE)));
        assertFalse(ProcessingService.input(level,StructureRole.SMELTERY,RecipeType.SMELTING,new ItemStack(Items.COBBLESTONE)),"Smelters only process raw metals and ores");
        for(var item:List.of(Items.BEEF,Items.CHICKEN,Items.PORKCHOP,Items.COD,Items.POTATO,Items.KELP)) {
            assertTrue(ProcessingService.input(level,StructureRole.COOK,RecipeType.SMOKING,new ItemStack(item)));
            assertTrue(ProcessingService.input(level,StructureRole.COOK,RecipeType.CAMPFIRE_COOKING,new ItemStack(item)));
        }
        assertFalse(ProcessingService.input(level,StructureRole.COOK,RecipeType.SMOKING,new ItemStack(Items.COOKED_BEEF)));
        assertFalse(ProcessingService.input(level,StructureRole.COOK,RecipeType.SMOKING,new ItemStack(Items.WHEAT)),"Bread uses three real wheat instead of an invented one-wheat smelting recipe");
        assertTrue(ProcessingService.fuel(level,new ItemStack(Items.COAL)));
        assertTrue(ProcessingService.fuel(level,new ItemStack(Items.CHARCOAL)));
        assertFalse(ProcessingService.fuel(level,new ItemStack(Items.STONE)));
        assertFalse(ProcessingService.fuel(level,new ItemStack(Items.LAVA_BUCKET)),"Container fuels are left for manual loading");
        assertTrue(ProcessingService.supply(level,StructureRole.COOK,new ItemStack(Items.BEEF)));
        assertTrue(ProcessingService.supply(level,StructureRole.COOK,new ItemStack(Items.WHEAT)));
        assertFalse(ProcessingService.supply(level,StructureRole.COOK,new ItemStack(Items.COOKED_BEEF)),"Finished food is delivered instead of retained as an ingredient");
    }
    @Test @ExtendWith(EphemeralTestServerProvider.class)
    void furnaceSlotTransfersConserveItemsAndComponents(MinecraftServer server) {
        SimpleContainer furnace=new SimpleContainer(3) {
            @Override public boolean canPlaceItem(int slot,ItemStack stack) {
                return slot==0 && stack.is(Items.RAW_IRON) || slot==1 && stack.is(Items.COAL);
            }
        };
        SimpleContainer bag=new SimpleContainer(6);
        bag.setItem(0,new ItemStack(Items.RAW_IRON,20)); bag.setItem(1,new ItemStack(Items.COAL,8));
        assertEquals(16,InventoryOps.moveToSlot(List.of(bag),furnace,0,ProcessingService::rawMetal,16));
        assertEquals(4,bag.getItem(0).getCount()); assertEquals(16,furnace.getItem(0).getCount());
        assertTrue(furnace.getItem(1).isEmpty()); assertTrue(furnace.getItem(2).isEmpty());
        assertEquals(4,InventoryOps.moveToSlot(List.of(bag),furnace,1,s -> s.is(Items.COAL),4));
        assertEquals(4,bag.getItem(1).getCount()); assertEquals(4,furnace.getItem(1).getCount());
        assertEquals(0,InventoryOps.moveToSlot(List.of(bag),furnace,2,s -> true,64),"No transfer inserts into the output slot");
        ItemStack named=new ItemStack(Items.RAW_IRON,3); named.set(DataComponents.CUSTOM_NAME,Component.literal("Kept apart")); bag.setItem(2,named);
        assertEquals(4,InventoryOps.moveToSlot(List.of(bag),furnace,0,ProcessingService::rawMetal,64));
        assertEquals(3,bag.getItem(2).getCount(),"Different components do not silently merge");
        furnace.setItem(0,new ItemStack(Items.RAW_IRON,63)); bag.setItem(0,new ItemStack(Items.RAW_IRON,5));
        assertEquals(1,InventoryOps.moveToSlot(List.of(bag),furnace,0,ProcessingService::rawMetal,16));
        assertEquals(64,furnace.getItem(0).getCount()); assertEquals(4,bag.getItem(0).getCount());
        assertEquals(0,InventoryOps.moveToSlot(List.of(furnace),furnace,0,s -> true,64),"An appliance cannot transfer into itself");
    }
    @Test @ExtendWith(EphemeralTestServerProvider.class)
    void hardwareMustBeTheRightKindAndInTheLocalRange(MinecraftServer server) {
        assertTrue(StationDetection.processingBlock(StructureRole.SMELTERY,Blocks.FURNACE.defaultBlockState()));
        assertTrue(StationDetection.processingBlock(StructureRole.SMELTERY,Blocks.BLAST_FURNACE.defaultBlockState()));
        assertFalse(StationDetection.processingBlock(StructureRole.SMELTERY,Blocks.SMOKER.defaultBlockState()));
        assertTrue(StationDetection.processingBlock(StructureRole.COOK,Blocks.SMOKER.defaultBlockState()));
        assertFalse(StationDetection.processingBlock(StructureRole.COOK,Blocks.FURNACE.defaultBlockState()));
        assertTrue(StationDetection.processingBlock(StructureRole.COOK,Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT,true)));
        assertTrue(StationDetection.processingBlock(StructureRole.COOK,Blocks.SOUL_CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT,true)));
        assertFalse(StationDetection.processingBlock(StructureRole.COOK,Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT,false)));
        Station a=new Station(new BlockPos(0,64,0),StructureRole.SMELTERY),b=new Station(new BlockPos(2,64,0),StructureRole.SMELTERY);
        Settlement town=new Settlement(UUID.randomUUID(),UUID.randomUUID(),"Town",BlockPos.ZERO,240,List.of(),List.of(b,a),"balanced");
        assertEquals(a,town.nearestStation(new BlockPos(1,64,0),s -> s.role()==StructureRole.SMELTERY));
        assertTrue(a.contains(new BlockPos(-3,67,3))); assertFalse(a.contains(new BlockPos(-4,64,0)));
    }
}
