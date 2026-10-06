package io.github.swishhyy.wwmc;
import com.mojang.serialization.JsonOps;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import io.github.swishhyy.wwmc.core.*;
import io.github.swishhyy.wwmc.settlement.*;
import java.util.List;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.storage.loot.LootTable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;

public final class MinecraftChecks {
    @Test @ExtendWith(EphemeralTestServerProvider.class)
    void inventoryAndPersistence(MinecraftServer server) throws IOException {
        main(new String[0]);
        var ops=server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        List<String> markers=new java.util.ArrayList<>(List.of("settlement_banner"));
        for(StructureRole role:StructureRole.values()) markers.add(role.id()+"_station");
        List<String> recipes=new java.util.ArrayList<>(markers); recipes.add("surveyor");
        for(String id:recipes) {
            try(var input=MinecraftChecks.class.getResourceAsStream("/data/wwmc/recipe/"+id+".json")) {
                if(input==null) throw new AssertionError("Missing recipe: "+id);
                Recipe.CODEC.parse(ops,JsonParser.parseReader(new InputStreamReader(input,StandardCharsets.UTF_8))).getOrThrow();
            }
        }
        for(String id:markers) {
            try(var input=MinecraftChecks.class.getResourceAsStream("/data/wwmc/loot_table/blocks/"+id+".json")) {
                if(input==null) throw new AssertionError("Missing block drops: "+id);
                LootTable.DIRECT_CODEC.parse(ops,JsonParser.parseReader(new InputStreamReader(input,StandardCharsets.UTF_8))).getOrThrow();
            }
        }
    }
    private static int checks;
    private static void check(boolean result,String message) { checks++; if(!result) throw new AssertionError(message); }
    public static void main(String[] args) {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        SimpleContainer chest=new SimpleContainer(1);
        chest.setItem(0,new ItemStack(Items.BREAD,60));
        ItemStack offered=new ItemStack(Items.BREAD,10);
        ItemStack remainder=InventoryOps.insert(chest,offered);
        check(chest.getItem(0).getCount()==64 && remainder.getCount()==6,"Full storage preserves overflow");
        check(offered.getCount()==10,"Offers are copied; the caller commits its inventory once");
        ItemStack meal=InventoryOps.takeOne(List.of(chest),s -> s.is(Items.BREAD));
        check(meal.getCount()==1 && chest.getItem(0).getCount()==63,"Food consumption removes exactly one item");
        ItemStack named=new ItemStack(Items.BREAD,1); named.set(DataComponents.CUSTOM_NAME,Component.literal("Special bread"));
        check(InventoryOps.insert(chest,named).getCount()==1,"Different components never merge into an existing stack");
        SimpleContainer tools=new SimpleContainer(1); tools.setItem(0,new ItemStack(Items.STONE_PICKAXE));
        check(!InventoryOps.takeOne(List.of(tools),s -> s.is(Items.STONE_PICKAXE)).isEmpty(),"A worker takes an actual tool");
        check(InventoryOps.takeOne(List.of(tools),s -> s.is(Items.STONE_PICKAXE)).isEmpty(),"Another worker cannot receive the same tool");
        Settlement town=new Settlement(UUID.randomUUID(),UUID.randomUUID(),"Test town",new BlockPos(0,64,0),64,List.of(UUID.randomUUID()),
                List.of(new Station(new BlockPos(1,64,1),StructureRole.HOSPITAL)),"food");
        var json=Settlement.CODEC.encodeStart(JsonOps.INSTANCE,town).getOrThrow();
        Settlement restored=Settlement.CODEC.parse(JsonOps.INSTANCE,json).getOrThrow();
        check(restored.id.equals(town.id) && restored.owner.equals(town.owner) && restored.citizens.equals(town.citizens),"Settlement ownership and population survive saves");
        check(restored.stations.getFirst().role()==StructureRole.HOSPITAL && restored.stations.getFirst().area().equals(town.stations.getFirst().area()),"Station roles and automatic bounds survive saves");
        check(restored.priority.equals("food"),"Player direction survives a restart");
        check(town.contains(new BlockPos(64,90,64)) && !town.contains(new BlockPos(65,64,0)),"Claims have explicit inclusive boundaries");
        check(town.overlaps(new BlockPos(128,64,0),64) && !town.overlaps(new BlockPos(129,64,0),64),"New claims cannot share a block");
        var legacy=Station.CODEC.encodeStart(JsonOps.INSTANCE,town.stations.getFirst()).getOrThrow().getAsJsonObject();
        legacy.add("room",JsonParser.parseString("{\"min_x\":1,\"min_y\":64,\"min_z\":1,\"max_x\":20,\"max_y\":70,\"max_z\":20}"));
        Station migrated=Station.CODEC.parse(JsonOps.INSTANCE,legacy).getOrThrow();
        check(migrated.equals(town.stations.getFirst()) && !migrated.contains(new BlockPos(20,64,20)),"Old selected rooms decode and adopt the automatic range");

        Station home=new Station(new BlockPos(0,64,0),StructureRole.HOUSING);
        Station hospital=new Station(new BlockPos(2,64,0),StructureRole.HOSPITAL);
        Station warehouseA=new Station(new BlockPos(0,64,2),StructureRole.WAREHOUSE);
        Station warehouseB=new Station(new BlockPos(2,64,2),StructureRole.WAREHOUSE);
        Settlement overlapping=new Settlement(UUID.randomUUID(),UUID.randomUUID(),"Overlapping stations",BlockPos.ZERO,64,List.of(),
                List.of(hospital,home,warehouseB,warehouseA),"balanced");
        BlockPos headPos=new BlockPos(1,64,0);
        check(overlapping.nearestStation(headPos,s -> s.role().detectsBeds()).equals(home),"Bed ownership ties use coordinates rather than station registration order");
        check(overlapping.nearestStation(new BlockPos(2,64,0),s -> s.role().detectsBeds()).equals(hospital),"Nearest hospital reserves its beds away from recruiting");
        check(overlapping.nearestStation(new BlockPos(1,64,2),s -> s.role()==StructureRole.WAREHOUSE).equals(warehouseA),"Overlapping warehouses have a single deterministic inventory owner");
        check(overlapping.nearestStation(new BlockPos(6,64,2),s -> s.role()==StructureRole.WAREHOUSE)==null,"Storage outside every seven-block range is unassigned");
        BlockPos edgeHead=new BlockPos(3,64,0),edgeFoot=new BlockPos(4,64,0);
        check(overlapping.nearestStation(edgeHead,s -> s.role().detectsBeds() && s.contains(edgeFoot)).equals(hospital),"A whole bed must fit the owning station even when another station contains its head");
        check(overlapping.nearestStation(new BlockPos(-3,64,0),s -> s.role().detectsBeds() && s.contains(new BlockPos(-4,64,0)))==null,"A bed straddling the outer boundary supplies no capacity");

        var head=BuiltInRegistries.BLOCK.getValue(Identifier.fromNamespaceAndPath("minecraft","red_bed")).defaultBlockState().setValue(BedBlock.PART,BedPart.HEAD).setValue(BedBlock.FACING,Direction.NORTH);
        var foot=head.setValue(BedBlock.PART,BedPart.FOOT);
        check(StationDetection.completeBed(head,foot),"Matching complete bed contributes capacity");
        check(!StationDetection.completeBed(head,Blocks.AIR.defaultBlockState()),"Removed bed foot immediately invalidates capacity");
        check(!StationDetection.completeBed(head,head),"Two bed heads cannot stand in for a complete bed");
        check(!StationDetection.completeBed(head,foot.setValue(BedBlock.FACING,Direction.SOUTH)),"Misaligned bed halves are rejected");
        check(!StationDetection.completeBed(head,BuiltInRegistries.BLOCK.getValue(Identifier.fromNamespaceAndPath("minecraft","white_bed")).defaultBlockState().setValue(BedBlock.PART,BedPart.FOOT).setValue(BedBlock.FACING,Direction.NORTH)),"Different bed types cannot supply a phantom bed");
        check(StationDetection.storageBlock(Blocks.CHEST.defaultBlockState()) && StationDetection.storageBlock(Blocks.TRAPPED_CHEST.defaultBlockState())
                && StationDetection.storageBlock(Blocks.BARREL.defaultBlockState()),"Warehouses support chests, trapped chests, and barrels");
        check(!StationDetection.storageBlock(Blocks.FURNACE.defaultBlockState()) && !StationDetection.storageBlock(Blocks.HOPPER.defaultBlockState()),"Warehouse scans do not drain unrelated processing blocks");
        var wheat=Blocks.WHEAT.defaultBlockState();
        check(StationDetection.workBlock(StructureRole.FARM,((CropBlock)Blocks.WHEAT).getStateForAge(7))
                && !StationDetection.workBlock(StructureRole.FARM,wheat),"Farm inspection distinguishes mature crops from seedlings");
        BlockPos.MutableBlockPos mutable=new BlockPos.MutableBlockPos(0,64,0);
        Station immutable=new Station(mutable,StructureRole.FARM); mutable.set(100,100,100);
        check(immutable.position().equals(new BlockPos(0,64,0)),"Station records retain immutable coordinates");
        System.out.println("Passed "+checks+" Minecraft adapter checks.");
    }
}
